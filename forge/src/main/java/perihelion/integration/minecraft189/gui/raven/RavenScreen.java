package perihelion.integration.minecraft189.gui.raven;

import java.util.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.*;
import perihelion.integration.minecraft189.gui.*;
import perihelion.integration.minecraft189.gui.font.GuiFonts;
import perihelion.integration.minecraft189.gui.panel.*;
import perihelion.integration.minecraft189.gui.widget.*;
import perihelion.integration.minecraft189.render.*;

/**
 * Raven presentation. Only input, persistence and measured setting state are shared with Adjust.
 */
public final class RavenScreen extends ModulePanelScreen {
  private final EnumMap<ModuleCategory, ItemStack> icons = new EnumMap<>(ModuleCategory.class);

  public RavenScreen(ClientRuntime runtime, ClickGuiModule preferences, GuiFonts fonts) {
    super(runtime, preferences, fonts);
    icons.put(ModuleCategory.COMBAT, new ItemStack(Items.diamond_sword));
    icons.put(ModuleCategory.MOVEMENT, new ItemStack(Items.diamond_boots));
    icons.put(ModuleCategory.PLAYER, new ItemStack(Items.golden_apple));
    icons.put(ModuleCategory.WORLD, new ItemStack(Items.filled_map));
    icons.put(ModuleCategory.RENDER, new ItemStack(Items.ender_eye));
    icons.put(ModuleCategory.MISC, new ItemStack(Items.compass));
  }

  @Override
  protected float rowHeight() {
    return 16;
  }

  @Override
  protected float headerHeight() {
    return 16;
  }

  @Override
  protected float minimumPanelWidth() {
    return 112;
  }

  @Override
  protected ClickGuiModule.ClientStyle frontendStyle() {
    return ClickGuiModule.ClientStyle.RAVEN;
  }

  @Override
  protected void configureCanvas() {
    RavenTheme.configure(canvas, fonts, preferences, Math.max(1, pixels));
  }

  @Override
  protected void placeDefaults() {
    if (searching()) {
      super.placeDefaults();
      return;
    }
    float y = Math.max(34, top() + 7);
    boolean reset = "[]".equals(preferences.ravenLayout.get().trim());
    for (PanelLayout.Panel panel : layout.panels()) {
      if (!shown(panel)) continue;
      panel.x = 8;
      panel.y = y;
      if (reset) panel.collapsed = true;
      panel.scroll = 0;
      panel.recover(viewWidth, viewHeight, top());
      panel.limitBody(16, viewHeight);
      y += 21;
    }
  }

  @Override
  protected float body(PanelLayout.Panel panel) {
    if (panel.collapsed && !searching()) return 0;
    return Math.min(
        content(panel),
        Math.max(0, Math.min(viewHeight * .80f, viewHeight - panel.y - headerHeight() - 6)));
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
          0, 0, viewWidth, viewHeight, GuiTheme.alpha(preferences.background.get(), 0x000000));
      wordmark();
      if (preferences.search.get()) {
        RavenTheme.frame(canvas, 7, 7, searchField.width + 2, 16, searchField.isFocused());
        float sy = canvas.font.centeredY(7, 16);
        if (searchField.isFocused())
          searchEditor.draw(canvas, searchField, 11, sy - 1, searchField.width - 6, searchEpoch);
        else
          canvas.label(
              searchField.getText().isEmpty() ? "Search modules" : searchField.getText(),
              11,
              sy,
              searchField.width - 6,
              searchField.getText().isEmpty() ? canvas.muted : canvas.text);
      }
      String hover = null;
      PanelLayout.Panel front = panelAt(mx, my);
      for (PanelLayout.Panel panel : layout.panels())
        if (shown(panel)) {
          String next = drawCategory(panel, mx, my, front == panel);
          if (next != null) hover = next;
        }
      GL11.glDisable(GL11.GL_SCISSOR_TEST);
      footer();
      drawToolbar(mx, my);
      if (!status.isEmpty()) canvas.label(status, 7, viewHeight - 15, viewWidth - 14, 0xffff6055);
      if (searching() && matchingModules() == 0)
        canvas.center("No matching modules", 8, viewHeight / 2, viewWidth - 16, canvas.muted);
      if (binding.active()) {
        tooltip.clear();
        drawBinding();
      } else if (editor == null
          && dragging == null
          && captured == null
          && utilityDrag == null
          && preferences.tooltips.get()) tooltip.draw(canvas, hover, mx, my, viewWidth, viewHeight);
      else tooltip.clear();
    }
  }

  private void wordmark() {
    float zoom = Math.min(2.4f, viewWidth / 150f);
    String name = "Perihelion";
    float full = canvas.font.width(name) * zoom;
    float x = Math.max(154, (viewWidth - full) / 2), y = viewHeight * .26f;
    if (x + full > viewWidth - 10) x = (viewWidth - full) / 2;
    GlStateManager.pushMatrix();
    try {
      GlStateManager.translate(x, y, 0);
      GlStateManager.scale(zoom, zoom, 1);
      float cursor = 0;
      for (int i = 0; i < name.length(); i++) {
        String glyph = name.substring(i, i + 1);
        int ink =
            java.awt.Color.HSBtoRGB(
                (System.currentTimeMillis() % 6000L) / 6000f + i * .035f, .62f, 1);
        canvas.text(glyph, cursor, 0, ink);
        cursor += canvas.font.width(glyph);
      }
    } finally {
      GlStateManager.popMatrix();
    }
    canvas.text("Raven", x + full - canvas.font.width("Raven"), y + 12 * zoom, 0xffbfbfbf);
  }

  private void footer() {
    String value = "Perihelion  /  Raven";
    float w = canvas.font.width(value), y = viewHeight - 12, x = 5;
    for (PanelLayout.Panel panel : layout.panels())
      if (shown(panel)
          && panel.x < x + w
          && panel.x + panel.width > x
          && panel.y < y + 10
          && panel.y + headerHeight() + body(panel) > y) {
        x = viewWidth - w - 5;
        break;
      }
    for (PanelLayout.Panel panel : layout.panels())
      if (shown(panel)
          && panel.x < x + w
          && panel.x + panel.width > x
          && panel.y < y + 10
          && panel.y + headerHeight() + body(panel) > y) return;
    canvas.label(value, x, y, w, 0xffbfbfbf);
  }

  private String drawCategory(PanelLayout.Panel panel, float mx, float my, boolean hovered) {
    float h = body(panel), total = content(panel);
    panel.scroll(0, total, h);
    RavenTheme.frame(
        canvas,
        panel.x,
        panel.y,
        panel.width,
        headerHeight() + h,
        !panel.collapsed || hovered || searching());
    drawIcon(panel);
    String title = panel.category.displayName();
    canvas.section(
        title,
        panel.x + 16,
        canvas.sectionFont.centeredY(panel.y, headerHeight()),
        panel.width - 23,
        true);
    canvas.text(
        panel.collapsed && !searching() ? "+" : "-",
        panel.x + panel.width - 8,
        canvas.font.centeredY(panel.y, headerHeight()),
        canvas.muted);
    if (h <= 0)
      return hovered
          ? "Left-click the icon or right-click the title to open. Drag the title to move."
          : null;
    clip(panel.x + 1, panel.y + headerHeight(), panel.width - 2, h);
    float y = panel.y + headerHeight() - panel.scroll;
    String hint = null;
    for (ModuleView view : views.get(panel.category))
      if (view.matches) {
        float row = y;
        y += rowHeight();
        boolean hit =
            hovered
                && my >= panel.y + headerHeight()
                && UiCanvas.inside(mx, my, panel.x, row, panel.width, rowHeight());
        if (row + rowHeight() > panel.y + headerHeight() && row < panel.y + headerHeight() + h) {
          ModuleRowPainter.draw(
              canvas,
              view.label,
              panel.x,
              row,
              panel.width,
              rowHeight(),
              view.entry.requestedEnabled() ? 1 : 0,
              view.expanded ? 1 : 0,
              hit ? 1 : 0,
              view.entry.state() == ModuleState.FAULTED,
              !view.entry.module().settings().isEmpty(),
              1);
          if (hit)
            hint =
                view.entry.state() == ModuleState.FAULTED
                    ? GuiValues.fault(view.entry)
                    : view.entry.info().description();
        }
        if (!view.expanded) continue;
        float inset = view.height() - rowHeight();
        canvas.rect(panel.x + 2, y, panel.width - 4, inset, 0x35000000);
        String bind = "Bind: " + KeybindEditor.name(view.entry.binding());
        if (y + rowHeight() > panel.y + headerHeight() && y < panel.y + headerHeight() + h) {
          canvas.label(
              bind,
              panel.x + 7,
              canvas.font.centeredY(y, rowHeight()),
              panel.width - 14,
              canvas.muted);
          if (hovered && UiCanvas.inside(mx, my, panel.x, y, panel.width, rowHeight()))
            hint = "Click to change bind.";
        }
        y += rowHeight();
        for (SettingControl control : view.controls)
          if (visible(view, control)) {
            float height = control.height();
            control.place(panel.x + 4, y, panel.width - 8);
            if (y + height > panel.y + headerHeight() && y < panel.y + headerHeight() + h) {
              control.hovered(
                  hovered
                      && my >= panel.y + headerHeight()
                      && my < panel.y + headerHeight() + h
                      && UiCanvas.inside(mx, my, panel.x + 4, y, panel.width - 8, height));
              control.render(
                  canvas,
                  panel.x + 4,
                  y,
                  panel.width - 8,
                  editorControl == control ? editY : Float.NaN,
                  editX);
              if (hovered
                  && my >= panel.y + headerHeight()
                  && UiCanvas.inside(mx, my, panel.x + 4, y, panel.width - 8, height))
                hint = control.setting.description();
            }
            y += height;
          }
      }
    if (editor != null && editorPanel == panel) {
      float top = Math.max(editY, panel.y + headerHeight());
      float bottom = Math.min(editY + 16, panel.y + headerHeight() + h);
      if (bottom > top) {
        clip(editX, top, editWidth, bottom - top);
        inlineEditor.draw(
            canvas, editor, editX, canvas.font.centeredY(editY, 12) - 1, editWidth, caretEpoch);
      }
    }
    GL11.glDisable(GL11.GL_SCISSOR_TEST);
    if (total > h) {
      float track = Math.max(5, h * h / total);
      canvas.rect(
          panel.x + panel.width - 2,
          panel.y + headerHeight() + panel.scroll / (total - h) * (h - track),
          1,
          track,
          0xff189aff);
    }
    return hint;
  }

  private void drawIcon(PanelLayout.Panel panel) {
    ItemStack icon = icons.get(panel.category);
    if (icon == null) return;
    try (RenderState ignored = new RenderState().open()) {
      RenderStyle.text();
      GlStateManager.pushMatrix();
      try {
        GlStateManager.translate(panel.x + 3, panel.y + 4, 0);
        GlStateManager.scale(.5f, .5f, 1);
        RenderHelper.enableGUIStandardItemLighting();
        mc.getRenderItem().renderItemAndEffectIntoGUI(icon, 0, 0);
      } finally {
        GlStateManager.popMatrix();
      }
    }
    RenderStyle.text();
  }

  private void drawToolbar(float mx, float my) {
    RavenTheme.button(
        canvas,
        "HUD",
        hudTab.x,
        hudTab.y,
        hudTabWidth,
        TAB_HEIGHT,
        hudMenu,
        true,
        UiCanvas.inside(mx, my, hudTab.x, hudTab.y, hudTabWidth, TAB_HEIGHT));
    RavenTheme.button(
        canvas,
        "Modules",
        moduleTabX,
        TAB_TOP,
        moduleTabWidth,
        TAB_HEIGHT,
        true,
        true,
        UiCanvas.inside(mx, my, moduleTabX, TAB_TOP, moduleTabWidth, TAB_HEIGHT));
    RavenTheme.button(
        canvas,
        "Configs",
        configTab.x,
        configTab.y,
        configTabWidth,
        TAB_HEIGHT,
        false,
        true,
        UiCanvas.inside(mx, my, configTab.x, configTab.y, configTabWidth, TAB_HEIGHT));
    if (!hudMenu) return;
    float x = Math.min(hudTab.x, viewWidth - 100),
        y = Math.min(hudTab.y + TAB_HEIGHT + TAB_GAP, viewHeight - 50);
    RavenTheme.frame(canvas, x, y, 100, 48, true);
    String[] titles = {"HUD settings", "Toggle HUD", "Reset positions"};
    for (int i = 0; i < titles.length; i++)
      RavenTheme.button(
          canvas,
          titles[i],
          x,
          y + i * 16,
          100,
          16,
          false,
          true,
          UiCanvas.inside(mx, my, x, y + i * 16, 100, 16));
  }

  private void drawBinding() {
    canvas.rect(0, 0, viewWidth, viewHeight, 0x99000000);
    float w = Math.min(244, viewWidth - 12), x = (viewWidth - w) / 2, y = viewHeight / 2 - 28;
    RavenTheme.frame(canvas, x, y, w, 64, true);
    canvas.center(binding.title(), x + 5, y + 8, w - 10, canvas.enabled);
    canvas.center("Press a key or mouse button", x + 5, y + 27, w - 10, canvas.text);
    canvas.center("Delete: clear   Esc: cancel", x + 5, y + 46, w - 10, canvas.muted);
  }

  @Override
  public void click(float mx, float my, int button) {
    tooltip.clear();
    geometry(false);
    PanelLayout.Panel panel = panelAt(mx, my);
    if (button == 0
        && !binding.active()
        && editor == null
        && panel != null
        && mx < panel.x + 15
        && my < panel.y + headerHeight()) {
      layout.front(panel);
      panel.collapsed = !panel.collapsed;
      persist();
      return;
    }
    super.click(mx, my, button);
  }
}
