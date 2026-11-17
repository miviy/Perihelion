package perihelion.integration.minecraft189.gui.panel;

import java.util.*;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.*;
import perihelion.integration.minecraft189.gui.*;
import perihelion.integration.minecraft189.gui.font.GuiFonts;
import perihelion.integration.minecraft189.gui.modern.ModernTheme;
import perihelion.integration.minecraft189.gui.widget.*;
import perihelion.integration.minecraft189.render.*;

/** Adjust presentation over the shared native panel controller. */
public class PanelScreen extends ModulePanelScreen {
  public PanelScreen(ClientRuntime runtime, ClickGuiModule preferences, GuiFonts fonts) {
    super(runtime, preferences, fonts);
  }

  @Override
  protected float rowHeight() {
    return 13;
  }

  @Override
  protected ClickGuiModule.ClientStyle frontendStyle() {
    return ClickGuiModule.ClientStyle.ADJUST;
  }

  @Override
  protected void configureCanvas() {
    GuiTheme.configure(canvas, fonts, preferences, Math.max(1, pixels));
  }

  @Override
  public void drawScreen(int mouseX, int mouseY, float partialTicks) {
    if (switchFrontend()) return;
    if (!runtime.isRunning()) {
      mc.displayGuiScreen(null);
      return;
    }
    geometry(false);
    validateEditor();
    float mx = mouseX / scale, my = mouseY / scale;
    try (RenderState ignored = renderState.open()) {
      RenderStyle.text();
      GlStateManager.scale(scale, scale, 1);
      configureCanvas();
      canvas.rect(
          0,
          0,
          viewWidth,
          viewHeight,
          alpha(canvas.modern ? .18 : preferences.background.get(), 0x090b0e));
      if (searchEnabled()) {
        canvas.rect(7, 7, searchField.width + 2, 16, 0x99141414);
        RenderStyle.text();
        float searchY = canvas.font.centeredY(7, 16);
        if (searchField.isFocused())
          searchEditor.draw(
              canvas, searchField, 11, searchY - 1, searchField.width - 6, searchEpoch);
        else canvas.label(searchField.getText(), 11, searchY, searchField.width - 6, canvas.text);
        if (searchField.getText().isEmpty() && !searchField.isFocused())
          canvas.label("Search", 11, searchY, searchField.width - 6, 0xff949ba4);
      }
      String hover = null;
      PanelLayout.Panel front = panelAt(mx, my);
      for (PanelLayout.Panel panel : layout.panels())
        if (shown(panel)) {
          String candidate = drawPanel(panel, mx, my, front == panel);
          if (candidate != null) hover = candidate;
        }
      GL11.glDisable(GL11.GL_SCISSOR_TEST);
      if (searching() && matchingModules() == 0)
        canvas.label(
            "No matches. Esc closes the menu.", 12, viewHeight / 2, viewWidth - 24, 0xffa2adb7);
      if (!status.isEmpty()) canvas.label(status, 8, viewHeight - 15, viewWidth - 16, 0xffde8d85);
      drawUtilities(mx, my);
      if (binding.active()) {
        tooltip.clear();
        drawModal();
      } else if (editor != null || dragging != null || captured != null || utilityDrag != null)
        tooltip.clear();
      else if (preferences.tooltips.get())
        tooltip.draw(canvas, hover, mx, my, viewWidth, viewHeight);
      else tooltip.clear();
    }
  }

  protected void drawUtilities(float mx, float my) {
    GuiTheme.button(
        canvas,
        "Hud",
        hudTab.x,
        hudTab.y,
        hudTabWidth,
        TAB_HEIGHT,
        hudMenu,
        true,
        UiCanvas.inside(mx, my, hudTab.x, hudTab.y, hudTabWidth, TAB_HEIGHT));
    GuiTheme.button(
        canvas,
        "Modules",
        moduleTabX,
        TAB_TOP,
        moduleTabWidth,
        TAB_HEIGHT,
        true,
        true,
        UiCanvas.inside(mx, my, moduleTabX, TAB_TOP, moduleTabWidth, TAB_HEIGHT));
    GuiTheme.button(
        canvas,
        "Configs",
        configTab.x,
        configTab.y,
        configTabWidth,
        TAB_HEIGHT,
        false,
        true,
        UiCanvas.inside(mx, my, configTab.x, configTab.y, configTabWidth, TAB_HEIGHT));
    if (hudMenu) {
      float x = Math.min(hudTab.x, viewWidth - 100),
          y = Math.min(hudTab.y + TAB_HEIGHT + TAB_GAP, viewHeight - 50);
      canvas.rect(x, y, 100, 48, GuiTheme.overlay(canvas));
      String[] names = {"HUD settings", "Toggle HUD", "Reset positions"};
      for (int i = 0; i < names.length; i++)
        GuiTheme.button(
            canvas,
            names[i],
            x,
            y + i * 16,
            100,
            16,
            false,
            true,
            UiCanvas.inside(mx, my, x, y + i * 16, 100, 16));
    }
  }

  protected void drawHeader(PanelLayout.Panel panel) {
    GuiTheme.header(canvas, panel.category.displayName(), panel.x, panel.y, panel.width);
  }

  protected String drawPanel(PanelLayout.Panel panel, float mx, float my, boolean hovered) {
    float height = body(panel), total = content(panel);
    panel.scroll(0, total, height);
    drawHeader(panel);
    if (height <= 0) return null;
    canvas.rect(panel.x, panel.y + headerHeight(), panel.width, height, canvas.panel);
    clip(panel.x, panel.y + headerHeight(), panel.width, height);
    float y = panel.y + headerHeight() - panel.scroll;
    String hover = null;
    for (ModuleView view : views.get(panel.category))
      if (view.matches) {
        float rowY = y;
        y += rowHeight();
        if (rowY + rowHeight() > panel.y + headerHeight()
            && rowY < panel.y + headerHeight() + height) {
          boolean hit =
              hovered
                  && my >= panel.y + headerHeight()
                  && UiCanvas.inside(mx, my, panel.x, rowY, panel.width, rowHeight());
          ModuleRowPainter.draw(
              canvas,
              view.label,
              panel.x,
              rowY,
              panel.width,
              rowHeight(),
              view.entry.requestedEnabled() ? 1 : 0,
              view.expanded ? 1 : 0,
              hit ? 1 : 0,
              view.entry.state() == ModuleState.FAULTED,
              !view.entry.module().settings().isEmpty(),
              1);
          if (hit)
            hover =
                view.entry.state() == ModuleState.FAULTED
                    ? GuiValues.fault(view.entry)
                    : view.entry.info().description();
        }
        if (view.expanded) {
          float insetHeight = view.height() - rowHeight();
          if (y + insetHeight > panel.y + headerHeight() && y < panel.y + headerHeight() + height) {
            // The reference uses one flat translucent section, with only a top separator.
            float insetX = panel.x + 3, insetWidth = panel.width - 6;
            double opacity = canvas.modern ? .8 : preferences.panelOpacity.get();
            canvas.rect(insetX, y, insetWidth, insetHeight, alpha(opacity * 0.50, 0x090b0d));
            canvas.hairline(insetX, y, insetWidth, alpha(opacity * 0.35, 0x747b80));
          }
          if (y + rowHeight() > panel.y + headerHeight() && y < panel.y + headerHeight() + height) {
            String key = KeybindEditor.name(view.entry.binding());
            String bindLabel = "Bind: " + key;
            if (canvas.font.width(bindLabel) > panel.width - 14) bindLabel = key;
            canvas.label(
                bindLabel,
                panel.x + 7,
                canvas.font.centeredY(y, rowHeight()),
                panel.width - 14,
                canvas.muted);
            if (hovered && UiCanvas.inside(mx, my, panel.x, y, panel.width, rowHeight()))
              hover = "Click to change bind.";
          }
          y += rowHeight();
          for (SettingControl control : view.controls)
            if (visible(view, control)) {
              float h = control.height();
              // Keep control geometry current even off-screen, but do not render it there.
              control.place(panel.x + 4, y, panel.width - 8);
              if (y + h > panel.y + headerHeight() && y < panel.y + headerHeight() + height) {
                control.hovered(
                    hovered
                        && my >= panel.y + headerHeight()
                        && my < panel.y + headerHeight() + height
                        && UiCanvas.inside(mx, my, panel.x + 4, y, panel.width - 8, h));
                control.render(
                    canvas,
                    panel.x + 4,
                    y,
                    panel.width - 8,
                    editorControl == control ? editY : Float.NaN,
                    editX);
                if (hovered
                    && my >= panel.y + headerHeight()
                    && UiCanvas.inside(mx, my, panel.x + 4, y, panel.width - 8, h))
                  hover = control.setting.description();
              }
              y += h;
            }
        }
      }
    if (editor != null && editorPanel == panel) {
      float top = Math.max(editTextY, panel.y + headerHeight());
      float bottom =
          Math.min(
              editTextY + Math.max(12, canvas.font.height()), panel.y + headerHeight() + height);
      if (bottom > top) {
        clip(editX, top, editWidth, bottom - top);
        inlineEditor.draw(canvas, editor, editX, editTextY - 1, editWidth, caretEpoch);
      }
    }
    GL11.glDisable(GL11.GL_SCISSOR_TEST);
    if (total > height) {
      float track = height * height / total;
      canvas.verticalHairline(
          panel.x + panel.width - 1 / pixels,
          panel.y + headerHeight() + panel.scroll / (total - height) * (height - track),
          track,
          canvas.accent);
    }
    return hover;
  }

  protected void drawModal() {
    canvas.rect(0, 0, viewWidth, viewHeight, 0x88000000);
    GuiFonts.Face titleFont = canvas.sectionFont == null ? canvas.font : canvas.sectionFont;
    String prompt = "Press a key or mouse button", hints = "Delete: clear   Esc: cancel";
    float padding = 12;
    float w =
        Math.min(
            Math.max(
                220,
                Math.max(
                        titleFont.width(binding.title()),
                        Math.max(canvas.font.width(prompt), canvas.font.width(hints)))
                    + padding * 2),
            viewWidth - 16);
    float contentWidth = w - padding * 2;
    List<String> titles = UiCanvas.wrap(titleFont, binding.title(), contentWidth);
    List<String> prompts = UiCanvas.wrap(canvas.font, prompt, contentWidth);
    List<String> footers = UiCanvas.wrap(canvas.font, hints, contentWidth);
    float titleLine = titleFont.height() + 3, line = canvas.font.height() + 3;
    float titleHeight = titles.size() * titleLine, promptHeight = prompts.size() * line;
    float footerHeight = footers.size() * line;
    float h = padding * 2 + titleHeight + 8 + promptHeight + 12 + footerHeight;
    float x = (viewWidth - w) / 2, y = Math.max(8, (viewHeight - h) / 2);
    if (canvas.modern) ModernTheme.surface(canvas, x, y, w, h, GuiTheme.overlay(canvas));
    else canvas.rect(x, y, w, h, GuiTheme.overlay(canvas));
    float textY = y + padding;
    GuiFonts.Face bodyFont = canvas.font;
    try {
      canvas.font = titleFont;
      for (String title : titles) {
        canvas.text(title, x + padding, titleFont.centeredY(textY, titleLine), canvas.sectionText);
        textY += titleLine;
      }
    } finally {
      canvas.font = bodyFont;
    }
    textY += 8;
    for (String text : prompts) {
      canvas.text(text, x + padding, bodyFont.centeredY(textY, line), canvas.text);
      textY += line;
    }
    canvas.hairline(x + padding, textY + 5, contentWidth, GuiTheme.alpha(.08, canvas.text));
    textY += 12;
    for (String text : footers) {
      canvas.text(text, x + padding, bodyFont.centeredY(textY, line), canvas.muted);
      textY += line;
    }
  }
}
