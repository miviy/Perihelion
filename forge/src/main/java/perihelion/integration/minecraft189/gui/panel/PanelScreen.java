package perihelion.integration.minecraft189.gui.panel;

import java.util.*;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.*;
import perihelion.integration.minecraft189.gui.*;
import perihelion.integration.minecraft189.gui.font.GuiFonts;
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
      String[] names = {"Edit hud", "Toggle hud", "Reset hud layout"};
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
          if (view.entry.requestedEnabled())
            canvas.rect(panel.x, rowY, panel.width, rowHeight(), GuiTheme.enabledRow(canvas));
          else if (view.expanded)
            canvas.rect(panel.x, rowY, panel.width, rowHeight(), GuiTheme.alpha(.04, 0xffffff));
          if (hit)
            canvas.rect(panel.x, rowY, panel.width, rowHeight(), GuiTheme.alpha(.07, 0xffffff));
          int color =
              view.entry.state() == ModuleState.FAULTED
                  ? 0xffdc8580
                  : view.expanded
                      ? canvas.selectedText
                      : view.entry.requestedEnabled() ? canvas.enabled : canvas.text;
          if (view.entry.state() == ModuleState.FAULTED)
            canvas.rect(panel.x, rowY + 5, 1, 6, 0xffd86e65);
          canvas.label(
              view.label,
              panel.x + (canvas.modern ? 6 : 3),
              canvas.font.centeredY(rowY, rowHeight()),
              panel.width - (canvas.modern ? 20 : 12),
              color);
          if (!view.entry.module().settings().isEmpty())
            canvas.text(
                view.expanded ? "-" : "+",
                panel.x + panel.width - (canvas.modern ? 10 : 6),
                canvas.font.centeredY(rowY, rowHeight()),
                canvas.muted);
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
      float top = Math.max(editY, panel.y + headerHeight());
      float bottom = Math.min(editY + 16, panel.y + headerHeight() + height);
      if (bottom > top) {
        clip(editX, top, editWidth, bottom - top);
        inlineEditor.draw(
            canvas, editor, editX, canvas.font.centeredY(editY, 12) - 1, editWidth, caretEpoch);
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
    float x = Math.max(4, (viewWidth - 244) / 2),
        y = viewHeight / 2 - 23,
        w = Math.min(244, viewWidth - 8);
    canvas.rect(x, y, w, 72, 0xfa191e25);
    canvas.rect(x, y, w, 1, canvas.accent);
    canvas.label(binding.title(), x + 8, y + 5, w - 16, canvas.text);
    canvas.text("Press a key or mouse button", x + 8, y + 25, canvas.text);
    canvas.text("Delete: clear   Esc: cancel", x + 8, y + 45, 0xff9ca9b7);
  }
}
