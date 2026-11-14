package perihelion.integration.minecraft189.gui.modern;

import java.util.*;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.*;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.gui.*;
import perihelion.integration.minecraft189.gui.font.GuiFonts;
import perihelion.integration.minecraft189.gui.menu.MenuFontScale;
import perihelion.integration.minecraft189.gui.panel.PanelLayout;
import perihelion.integration.minecraft189.gui.panel.PanelScreen;
import perihelion.integration.minecraft189.gui.widget.*;
import perihelion.integration.minecraft189.render.*;

/** Rounded dropdowns over the native setting/input controller. Animation state is screen-owned. */
public final class ModernScreen extends PanelScreen {
  private static final float WIDTH = 110, GAP = 8, HEADER = 22, ROW = 17;
  private final IdentityHashMap<ModuleView, RowMotion> motions = new IdentityHashMap<>();
  private final IdentityHashMap<PanelLayout.Panel, PanelMotion> panels = new IdentityHashMap<>();
  private final ModernAssets assets = new ModernAssets();
  private final ModernBackdrop backdrop = new ModernBackdrop();
  private final ModernControls controls = new ModernControls();
  private final RoundedClip roundedClip = new RoundedClip();
  private final DragSway dragSway = new DragSway();
  private PanelLayout.Panel swayPanel;
  private float swayGrabX, swayGrabY;
  private boolean rotatedPanel;
  private GuiFonts.Face bodyFace, titleFace;
  private ClickGuiModule.Font fontSeen;
  private boolean boldSeen;
  private float fontPixelsSeen;
  private long previousFrame;
  private float opening, configX, configY, configScroll, configOpen = 1;
  private final ConfigActions configs;
  private boolean configCollapsed;
  private PanelLayout.Panel focusedHud;
  private float hudRestingY;

  private static final class RowMotion {
    float height, enabled, hover, arrow;

    RowMotion(boolean on) {
      enabled = on ? 1 : 0;
    }
  }

  private static final class PanelMotion {
    float open = 1, targetScroll;
  }

  public ModernScreen(ClientRuntime runtime, ClickGuiModule preferences, GuiFonts fonts) {
    super(runtime, preferences, fonts);
    configs = new ConfigActions(runtime);
    for (List<ModuleView> group : views.values())
      for (ModuleView v : group) motions.put(v, new RowMotion(v.entry.requestedEnabled()));
  }

  @Override
  protected boolean modernFrontend() {
    return true;
  }

  @Override
  protected boolean searchEnabled() {
    return true;
  }

  @Override
  protected float rowHeight() {
    return ROW;
  }

  @Override
  protected float headerHeight() {
    return HEADER;
  }

  @Override
  protected float top() {
    return 9;
  }

  @Override
  protected float columnWidth() {
    return WIDTH;
  }

  @Override
  protected float minimumPanelWidth() {
    return WIDTH;
  }

  @Override
  protected Setting<String> layoutSetting() {
    return preferences.modernLayout;
  }

  @Override
  protected Setting<String> utilitySetting() {
    return preferences.modernUtilityLayout;
  }

  @Override
  protected float searchX() {
    return 9;
  }

  @Override
  protected float searchY() {
    return viewHeight - 24;
  }

  @Override
  protected String moduleLabel(ModuleEntry entry) {
    return GuiValues.moduleLabel(entry.info().name());
  }

  @Override
  protected GuiFonts.Face editorFont() {
    faces();
    return bodyFace;
  }

  private void faces() {
    ClickGuiModule.Font choice = GuiTheme.font(preferences);
    boolean weight = GuiTheme.bold(preferences);
    float effectivePixels = Math.max(.5f, pixels);
    if (bodyFace == null
        || fontSeen != choice
        || boldSeen != weight
        || fontPixelsSeen != effectivePixels) {
      if (bodyFace != null) {
        cancelEditor();
        clearCapture();
        dragging = null;
        binding.cancel();
      }
      fontSeen = choice;
      boldSeen = weight;
      fontPixelsSeen = effectivePixels;
      if (choice == ClickGuiModule.Font.MINECRAFT) {
        bodyFace = new MenuFontScale(fonts.get(choice, weight), 1f);
        titleFace = new MenuFontScale(fonts.get(choice, weight), 1f);
      } else {
        bodyFace = fonts.pixelFace(choice, weight, GuiTheme.MODERN_BODY_SCALE, effectivePixels);
        float titleFactor = GuiTheme.MODERN_TITLE_SCALE;
        titleFace = fonts.pixelFace(choice, weight, titleFactor, effectivePixels);
        while (titleFace.width("Movement") > WIDTH - 27 && titleFactor > .8f) {
          titleFactor -= .05f;
          titleFace = fonts.pixelFace(choice, weight, titleFactor, effectivePixels);
        }
      }
    }
  }

  @Override
  protected void configureCanvas() {
    faces();
    GuiTheme.configure(canvas, fonts, preferences, Math.max(.5f, pixels));
    if (!roundedClip.available()) canvas.cornerRadius = 0;
    canvas.font = bodyFace;
    canvas.sectionFont = titleFace;
    canvas.text = canvas.enabled = canvas.selectedText = 0xfff2f2f2;
    canvas.panel = 0xfa242322;
    canvas.header = 0xff191817;
    canvas.muted = 0xffc6c1bb;
    canvas.settingPainter = controls;
    canvas.textShadow = false;
  }

  @Override
  protected float requestedScale() {
    float desired = preferences.scale.get().floatValue() * .8f;
    if (width >= 600) desired = Math.min(desired, width / (7 * (WIDTH + GAP) + 16));
    return desired;
  }

  @Override
  protected boolean updatePanelWidths() {
    boolean changed = false;
    try (RenderState ignored = renderState.open()) {
      configureCanvas();
      for (PanelLayout.Panel p : layout.panels()) {
        changed |= p.width != WIDTH;
        p.width = WIDTH;
        p.headerHeight = HEADER;
        for (ModuleView v : views.get(p.category))
          for (SettingControl c : v.controls) c.measure(bodyFace, WIDTH - 8, true);
      }
    }
    return changed;
  }

  @Override
  protected void geometry(boolean resize) {
    float previousScale = scale;
    PanelLayout.Panel previousDrag = dragging;
    super.geometry(resize);
    if (swayPanel != null
        && (resize
            || scale != previousScale
            || previousDrag != null && dragging == null
            || !layout.panels().contains(swayPanel))) clearSway();
    // The base controller computes framebuffer scale after its first measurement pass.
    if (fontPixelsSeen != Math.max(.5f, pixels)) updatePanelWidths();
    // Loading/resetting a layout replaces every Panel; do not retain the discarded identities.
    List<PanelLayout.Panel> currentPanels = layout.panels();
    if (focusedHud != null) {
      if (currentPanels.contains(focusedHud)) focusedHud.y = top();
      else focusedHud = null;
    }
    if (!panels.isEmpty() && (currentPanels.isEmpty() || !panels.containsKey(currentPanels.get(0))))
      panels.clear();
    // A single fixed dock keeps the category row identical to the reference.
    hudTabWidth = Math.max(38, (float) Math.ceil(bodyFace.width("Hud")) + 12);
    moduleTabWidth = Math.max(48, (float) Math.ceil(bodyFace.width("Modules")) + 12);
    configTabWidth = Math.max(40, (float) Math.ceil(bodyFace.width("Configs")) + 12);
    configTab.x = viewWidth - 9 - configTabWidth;
    configTab.y = searchY();
    moduleTabX = viewWidth - 9 - moduleTabWidth;
    hudTab.x = moduleTabX - 4 - hudTabWidth;
    hudTab.y = searchY();
    searchField.width = (int) Math.max(64, Math.min(150, hudTab.x - searchX() - 10));
    placeConfig();
    if (configs != null) configs.measure(bodyFace, WIDTH);
    if (resize) {
      for (PanelLayout.Panel p : layout.panels()) panelMotion(p).targetScroll = p.scroll;
      previousFrame = 0;
    }
  }

  private int count() {
    int count = 1;
    for (List<ModuleView> group : views.values()) if (!group.isEmpty()) count++;
    return count;
  }

  private int columns() {
    return Math.max(1, Math.min(count(), (int) ((viewWidth - 12 + GAP) / (WIDTH + GAP))));
  }

  private float left() {
    int cols = columns();
    return Math.max(6, (viewWidth - (cols * (WIDTH + GAP) - GAP)) / 2);
  }

  private float band() {
    return Math.max(
        HEADER + ROW, (viewHeight - top() - 36) / ((count() + columns() - 1) / columns()));
  }

  private void placeConfig() {
    int index = count() - 1;
    configX = left() + index % columns() * (WIDTH + GAP);
    configY = top() + index / columns() * band();
  }

  @Override
  protected void placeDefaults() {
    int index = 0;
    for (ModuleCategory category : ModuleCategory.values()) {
      PanelLayout.Panel p = layout.find(category);
      if (views.get(category).isEmpty()) continue;
      p.headerHeight = HEADER;
      p.x = left() + index % columns() * (WIDTH + GAP);
      p.y = top() + index / columns() * band();
      p.limitBody(Math.max(ROW, band() - HEADER - 8), viewHeight);
      p.recover(viewWidth, viewHeight, top());
      index++;
    }
    placeConfig();
  }

  private PanelMotion panelMotion(PanelLayout.Panel p) {
    PanelMotion m = panels.get(p);
    if (m == null) {
      m = new PanelMotion();
      m.open = p.collapsed ? 0 : 1;
      m.targetScroll = p.scroll;
      panels.put(p, m);
    }
    return m;
  }

  @Override
  protected float expansionHeight(ModuleView view) {
    RowMotion motion = motions == null ? null : motions.get(view);
    return motion == null ? (view.expanded ? settingsHeight(view) : 0) : motion.height;
  }

  private float viewport(PanelLayout.Panel p) {
    float available = Math.max(0, viewHeight - p.y - HEADER - 34);
    if (p != focusedHud && columns() < count() && p.limited())
      available = Math.min(available, band() - HEADER - 8);
    return Math.min(content(p), available);
  }

  @Override
  protected float body(PanelLayout.Panel p) {
    return viewport(p) * panelMotion(p).open * opening;
  }

  /** Advance once per rendered frame. All later geometry and input read these same values. */
  private void advance(float mx, float my) {
    long now = System.nanoTime();
    float dt = previousFrame == 0 ? 1 / 60f : (now - previousFrame) / 1_000_000_000f;
    previousFrame = now;
    float normal = GuiMotion.weight(dt, 18), fast = GuiMotion.weight(dt, 24);
    if (swayPanel != null) {
      dragSway.advance(dt, dragging == swayPanel);
      if (dragging == null && dragSway.angle == 0) swayPanel = null;
    }
    opening = GuiMotion.approach(opening, 1, normal);
    configOpen = GuiMotion.approach(configOpen, configCollapsed ? 0 : 1, normal);
    configs.advance(normal);
    boolean held = editor != null || captured != null || dragging != null || binding.active();
    PanelLayout.Panel front = panelAt(mx, my);
    for (PanelLayout.Panel p : layout.panels()) {
      PanelMotion pm = panelMotion(p);
      pm.open = GuiMotion.approach(pm.open, p.collapsed && !searching() ? 0 : 1, normal);
      float rowY = p.y + HEADER - p.scroll;
      for (ModuleView v : views.get(p.category)) {
        RowMotion m = motions.get(v);
        float wanted = v.expanded ? settingsHeight(v) : 0;
        m.height = held ? m.height : GuiMotion.height(m.height, wanted, normal);
        m.arrow = GuiMotion.approach(m.arrow, v.expanded ? 1 : 0, normal);
        m.enabled = GuiMotion.approach(m.enabled, v.entry.requestedEnabled() ? 1 : 0, fast);
        boolean hit =
            dragging == null
                && v.matches
                && front == p
                && my >= p.y + HEADER
                && my < p.y + HEADER + body(p)
                && UiCanvas.inside(mx, my, p.x, rowY, WIDTH, ROW);
        m.hover = GuiMotion.approach(m.hover, hit ? 1 : 0, fast);
        if (v.matches) rowY += ROW + m.height;
      }
      float limit = Math.max(0, content(p) - viewport(p));
      pm.targetScroll = Math.max(0, Math.min(limit, pm.targetScroll));
      if (held) pm.targetScroll = p.scroll;
      p.scroll =
          held ? Math.min(p.scroll, limit) : GuiMotion.height(p.scroll, pm.targetScroll, normal);
      p.scroll = Math.min(p.scroll, limit);
    }
  }

  @Override
  public void scroll(float mx, float my, float delta) {
    tooltip.clear();
    if (dragging == null) clearSway();
    if (editor != null || binding.active() || captured != null || dragging != null) return;
    if (!searching() && UiCanvas.inside(mx, my, configX, configY + HEADER, WIDTH, configHeight())) {
      configScroll =
          Math.max(
              0,
              Math.min(
                  Math.max(0, configContentHeight() - configFullHeight()), configScroll + delta));
      return;
    }
    PanelLayout.Panel p = panelAt(mx, my);
    if (p != null && my >= p.y + HEADER) {
      PanelMotion m = panelMotion(p);
      m.targetScroll =
          Math.max(0, Math.min(Math.max(0, content(p) - viewport(p)), m.targetScroll + delta));
    }
  }

  @Override
  public void setQuery(String text) {
    if (configs != null) configs.close();
    super.setQuery(text);
    if (panels != null) for (PanelMotion p : panels.values()) p.targetScroll = 0;
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
    advance(mx, my);
    configs.update();
    configScroll =
        Math.max(
            0, Math.min(configScroll, Math.max(0, configContentHeight() - configFullHeight())));
    try (RenderState ignored = renderState.open()) {
      RenderStyle.text();
      GlStateManager.scale(scale, scale, 1);
      configureCanvas();
      assets.initialize();
      backdrop.render(canvas, viewWidth, viewHeight, opening);
      canvas.rect(0, 0, viewWidth, viewHeight, alpha(.18 * opening, 0x171a18));
      PanelLayout.Panel front = panelAt(mx, my);
      String hover = null;
      for (PanelLayout.Panel p : layout.panels())
        if (shown(p)) {
          boolean lean = p == swayPanel && dragSway.angle != 0 && roundedClip.available();
          if (lean) {
            rotatedPanel = true;
            GlStateManager.pushMatrix();
            float cx = p.x + swayGrabX, cy = p.y + swayGrabY;
            GlStateManager.translate(cx, cy, 0);
            GlStateManager.rotate(dragSway.angle, 0, 0, 1);
            GlStateManager.translate(-cx, -cy, 0);
          }
          try {
            String candidate = drawPanel(p, mx, my, !lean && front == p);
            if (candidate != null) hover = candidate;
          } finally {
            if (lean) {
              rotatedPanel = false;
              GlStateManager.popMatrix();
            }
          }
        }
      if (!searching()) {
        String configHover = drawConfigs(mx, my);
        if (configHover != null) hover = configHover;
      }
      drawUtilities(mx, my);
      if (searching() && matchingModules() == 0)
        canvas.label("No matches", 12, viewHeight / 2, viewWidth - 24, canvas.muted);
      if (!status.isEmpty()) canvas.label(status, 9, viewHeight - 39, viewWidth - 18, 0xffe3aaaa);
      else if (!configs.message().isEmpty())
        canvas.label(
            configs.message(),
            9,
            viewHeight - 39,
            viewWidth - 18,
            configs.error() ? 0xffe3aaaa : canvas.muted);
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

  private int ink(int color) {
    return (Math.round((color >>> 24) * opening) << 24) | (color & 0xffffff);
  }

  private String title(ModuleCategory c) {
    return c == ModuleCategory.RENDER ? "Visual" : c.displayName();
  }

  private int icon(ModuleCategory c) {
    switch (c) {
      case COMBAT:
        return 0;
      case MOVEMENT:
        return 1;
      case PLAYER:
        return 2;
      case WORLD:
        return 3;
      case RENDER:
        return 4;
      default:
        return 5;
    }
  }

  @Override
  protected void drawHeader(PanelLayout.Panel p) {
    header(title(p.category), icon(p.category), p.x, p.y, body(p) <= 0);
  }

  private void header(String text, int icon, float x, float y, boolean roundedBottom) {
    ModernAssets.rounded(
        canvas, x, y, WIDTH, HEADER, canvas.cornerRadius, ink(canvas.header), true, roundedBottom);
    assets.icon(canvas, icon, x + 3, y + 4, 14, ink(canvas.text));
    titleFace.draw(
        text,
        x + WIDTH - 4 - titleFace.width(text),
        titleFace.centeredY(y, HEADER),
        ink(canvas.text));
  }

  @Override
  protected String drawPanel(PanelLayout.Panel p, float mx, float my, boolean hovered) {
    float height = body(p), total = content(p), bottom = p.y + HEADER + height;
    assets.shadow(canvas, p.x, p.y, WIDTH, HEADER + height, canvas.cornerRadius, opening * .7f);
    ModernAssets.rounded(
        canvas,
        p.x,
        p.y,
        WIDTH,
        HEADER + height,
        canvas.cornerRadius,
        ink(canvas.panel),
        true,
        true);
    boolean masked = beginContent(p.x, p.y, WIDTH, HEADER + height);
    float contentBottom = bottom - (masked ? 0 : Math.min(canvas.cornerRadius, height));
    try {
      drawHeader(p);
      if (height <= 0) return null;
      clip(p.x, p.y + HEADER, WIDTH, Math.max(0, contentBottom - p.y - HEADER));
      float y = p.y + HEADER - p.scroll;
      String hover = null;
      for (ModuleView v : views.get(p.category))
        if (v.matches) {
          RowMotion m = motions.get(v);
          boolean hit =
              hovered
                  && my >= p.y + HEADER
                  && my < bottom
                  && UiCanvas.inside(mx, my, p.x, y, WIDTH, ROW);
          if (y + ROW > p.y + HEADER && y < bottom) {
            ModuleRowPainter.draw(
                canvas,
                v.label,
                p.x,
                y,
                WIDTH,
                ROW,
                m.enabled,
                m.arrow,
                m.hover,
                v.entry.state() == ModuleState.FAULTED,
                !v.controls.isEmpty(),
                opening);
            if (hit)
              hover =
                  v.entry.state() == ModuleState.FAULTED
                      ? GuiValues.fault(v.entry)
                      : v.entry.info().description();
          }
          y += ROW;
          float blockTop = y, blockBottom = y + m.height;
          if (m.height > 0 && blockBottom > p.y + HEADER && y < bottom) {
            clip(
                p.x,
                Math.max(y, p.y + HEADER),
                WIDTH,
                Math.max(0, Math.min(blockBottom, contentBottom) - Math.max(y, p.y + HEADER)));
            canvas.rect(p.x, y, WIDTH, m.height, ink(ModernTheme.SETTINGS));
            canvas.label(
                "Bind", p.x + 7, canvas.font.centeredY(y, ROW), WIDTH - 40, ink(canvas.muted));
            String key = KeybindEditor.name(v.entry.binding());
            canvas.label(
                key,
                p.x + WIDTH - 7 - canvas.font.width(key),
                canvas.font.centeredY(y, ROW),
                WIDTH - 14,
                ink(canvas.muted));
            if (hit) hover = "Middle-click to bind.";
            y += ROW;
            for (SettingControl control : v.controls)
              if (visible(v, control)) {
                float h = control.height();
                control.place(p.x + 4, y, WIDTH - 8);
                if (y + h > p.y + HEADER && y < bottom && y < blockBottom) {
                  control.hovered(
                      v.expanded
                          && hovered
                          && my >= p.y + HEADER
                          && my < Math.min(bottom, blockBottom)
                          && UiCanvas.inside(mx, my, p.x + 4, y, WIDTH - 8, h));
                  control.render(
                      canvas,
                      p.x + 4,
                      y,
                      WIDTH - 8,
                      editorControl == control ? editY : Float.NaN,
                      editX);
                  if (v.expanded
                      && hovered
                      && my >= p.y + HEADER
                      && my < Math.min(bottom, blockBottom)
                      && UiCanvas.inside(mx, my, p.x + 4, y, WIDTH - 8, h))
                    hover = control.setting.description();
                }
                y += h;
              }
            clip(p.x, p.y + HEADER, WIDTH, Math.max(0, contentBottom - p.y - HEADER));
          } else {
            // Placement must remain current for inline editors and smoke/native hit testing.
            float cy = y + ROW;
            for (SettingControl c : v.controls)
              if (visible(v, c)) {
                c.place(p.x + 4, cy, WIDTH - 8);
                cy += c.height();
              }
          }
          y = blockTop + m.height;
        }
      if (editor != null && editorPanel == p) {
        float top = Math.max(editY, p.y + HEADER), end = Math.min(editY + 16, contentBottom);
        if (end > top) {
          clip(editX, top, editWidth, end - top);
          inlineEditor.draw(
              canvas, editor, editX, canvas.font.centeredY(editY, 12) - 1, editWidth, caretEpoch);
        }
      }
      clip(p.x, p.y + HEADER, WIDTH, Math.max(0, contentBottom - p.y - HEADER));
      if (total > height + 1 && height > 8) {
        float thumb = Math.max(8, height * height / total);
        canvas.rect(
            p.x + WIDTH - 1.2f,
            p.y + HEADER + p.scroll / Math.max(1, total - height) * (height - thumb),
            1,
            thumb,
            ink(0x80757575));
      }
      return hover;
    } finally {
      endContent();
    }
  }

  private boolean beginContent(float x, float y, float w, float h) {
    boolean active = roundedClip.begin(x, y, w, h, canvas.cornerRadius, pixels, mc.displayHeight);
    if (active && rotatedPanel && swayPanel != null)
      roundedClip.rotation(swayPanel.x + swayGrabX, swayPanel.y + swayGrabY, dragSway.angle);
    canvas.clip = active ? roundedClip : null;
    return active;
  }

  @Override
  protected void clip(float x, float y, float w, float h) {
    if (rotatedPanel) {
      GL11.glDisable(GL11.GL_SCISSOR_TEST);
      roundedClip.rectangle(x, y, w, h);
    } else super.clip(x, y, w, h);
  }

  private void endContent() {
    canvas.clip = null;
    roundedClip.end();
    GL11.glDisable(GL11.GL_SCISSOR_TEST);
  }

  @Override
  protected void drawUtilities(float mx, float my) {
    float y = searchY();
    ModernAssets.rounded(
        canvas,
        searchX(),
        y,
        searchField.width + 2,
        16,
        Math.min(canvas.cornerRadius, 8),
        ink(0xde161616),
        true,
        true);
    float sy = canvas.font.centeredY(y, 16);
    if (searchField.isFocused())
      searchEditor.draw(
          canvas, searchField, searchTextX(), sy - 1, searchField.width - 6, searchEpoch);
    else
      canvas.label(
          searchField.getText().isEmpty() ? "Search" : searchField.getText(),
          searchTextX(),
          sy,
          searchField.width - 6,
          ink(searchField.getText().isEmpty() ? canvas.muted : canvas.text));
    dockButton("Hud", hudTab.x, y, hudTabWidth, mx, my);
    dockButton("Modules", moduleTabX, y, moduleTabWidth, mx, my);
  }

  private void dockButton(String name, float x, float y, float w, float mx, float my) {
    ModernAssets.rounded(
        canvas,
        x,
        y,
        w,
        16,
        Math.min(canvas.cornerRadius, 8),
        ink(UiCanvas.inside(mx, my, x, y, w, 16) ? 0xf0323232 : 0xde161616),
        true,
        true);
    canvas.center(name, x, canvas.font.centeredY(y, 16), w, ink(canvas.text));
  }

  @Override
  protected void openHud() {
    clearSway();
    super.openHud();
    PanelLayout.Panel panel = layout.find(ModuleCategory.RENDER);
    if (focusedHud != panel) {
      focusedHud = panel;
      hudRestingY = panel.y;
    }
    // Give HUD settings the available height without changing the user's saved panel position.
    panel.y = top();
    for (ModuleView view : views.get(ModuleCategory.RENDER))
      if (view.entry.info().id().equals("hud")) motions.get(view).height = settingsHeight(view);
    panelMotion(panel).open = 1;
    panelMotion(panel).targetScroll = panel.scroll;
  }

  private void restoreHudPosition() {
    if (focusedHud != null && layout.panels().contains(focusedHud)) {
      focusedHud.y = hudRestingY;
      focusedHud.recover(viewWidth, viewHeight, top());
    }
    focusedHud = null;
  }

  @Override
  protected void persist() {
    if (focusedHud == null || !layout.panels().contains(focusedHud)) {
      super.persist();
      return;
    }
    float visibleY = focusedHud.y;
    try {
      focusedHud.y = hudRestingY;
      super.persist();
    } finally {
      focusedHud.y = visibleY;
    }
  }

  @Override
  protected boolean utilityClick(float mx, float my, int button) {
    if (UiCanvas.inside(mx, my, hudTab.x, hudTab.y, hudTabWidth, 16)) {
      if (button == 0) openHud();
      return true;
    }
    if (UiCanvas.inside(mx, my, moduleTabX, searchY(), moduleTabWidth, 16)) {
      if (button == 0) {
        restoreHudPosition();
        cancelEditor();
        hudMenu = false;
      }
      return true;
    }
    return false;
  }

  @Override
  public void click(float mx, float my, int button) {
    tooltip.clear();
    float pointerX = mx, pointerY = my;
    geometry(false);
    if (editor == null
        && !binding.active()
        && !searching()
        && UiCanvas.inside(mx, my, configX, configY, WIDTH, HEADER + configHeight())) {
      clearSway();
      searchField.setFocused(false);
      status = "";
      if (my < configY + HEADER) {
        if (button == 1) configCollapsed = !configCollapsed;
        configs.blur();
        return;
      }
      if (configCollapsed || configOpen < .99f || opening < .99f) return;
      float y = configY + HEADER - configScroll;
      for (int i = 0; i < configRowCount(); i++) {
        String id = configRow(i);
        if (my >= y && my < y + ROW) {
          configs.blur();
          if (id.equals(ConfigActions.NEW) && (button == 0 || button == 1)) configs.create();
          else if (id.equals(ConfigActions.TOOLS) && (button == 0 || button == 1)) configs.open(id);
          else if (!id.isEmpty()) {
            if (button == 1) configs.open(id);
            else if (button == 0) configs.load(id);
          }
          return;
        }
        y += ROW;
        float block = configs.height(id);
        if (block > 0 && my >= y && my < y + block) {
          configs.place(configX, y);
          configs.click(canvas, mx, my, button);
          return;
        }
        y += block;
      }
      return;
    }
    configs.blur();
    if (swayPanel != null && dragging == null) {
      // Configs, search and toolbar are drawn above panels and keep their raw pointer input.
      boolean overlay =
          editor != null
              || binding.active()
              || UiCanvas.inside(mx, my, searchX(), searchY(), searchField.width + 2, 16)
              || UiCanvas.inside(mx, my, hudTab.x, hudTab.y, hudTabWidth, 16)
              || UiCanvas.inside(mx, my, moduleTabX, searchY(), moduleTabWidth, 16);
      if (!overlay && roundedClip.available()) {
        float cx = swayPanel.x + swayGrabX, cy = swayPanel.y + swayGrabY;
        double radians = Math.toRadians(dragSway.angle);
        float co = (float) Math.cos(radians), si = (float) Math.sin(radians);
        float x = cx + (mx - cx) * co + (my - cy) * si;
        float y = cy - (mx - cx) * si + (my - cy) * co;
        if (UiCanvas.inside(x, y, swayPanel.x, swayPanel.y, WIDTH, HEADER + body(swayPanel))) {
          mx = x;
          my = y;
        } else if (super.panelAt(mx, my) == swayPanel) {
          // The panel has visibly leaned away from this edge; do not click its vacant wedge.
          clearSway();
          return;
        }
      }
      clearSway();
    }
    PanelLayout.Panel p = panelAt(mx, my);
    if (p != null && p.collapsed && !searching() && my >= p.y + HEADER) return;
    super.click(mx, my, button);
    // A deliberate drag becomes the new saved location, including while HUD is focused.
    if (dragging != null && dragging == focusedHud) focusedHud = null;
    if (dragging != null && button == 0) {
      clearSway();
      swayPanel = dragging;
      // Re-grabbing a tilted header keeps the real pointer anchored, with no positional easing.
      dragX = swayGrabX = pointerX - dragging.x;
      dragY = swayGrabY = pointerY - dragging.y;
    }
  }

  @Override
  public void drag(float mx, float my) {
    PanelLayout.Panel panel = dragging;
    float x = panel == null ? 0 : panel.x, y = panel == null ? 0 : panel.y;
    super.drag(mx, my);
    if (panel != null && panel == swayPanel) dragSway.move(panel.x - x, panel.y - y);
  }

  private void clearSway() {
    swayPanel = null;
    if (dragSway != null) dragSway.reset();
  }

  @Override
  public void resetLayout() {
    clearSway();
    restoreHudPosition();
    super.resetLayout();
  }

  private float configHeight() {
    return configFullHeight() * configOpen * opening;
  }

  private int configRowCount() {
    return Math.max(1, configs.profiles().size()) + 2;
  }

  private String configRow(int index) {
    int presets = Math.max(1, configs.profiles().size());
    if (index < presets) return configs.profiles().isEmpty() ? "" : configs.profiles().get(index);
    return index == presets ? ConfigActions.NEW : ConfigActions.TOOLS;
  }

  private float configContentHeight() {
    return configRowCount() * ROW + configs.height();
  }

  private float configRowTop(String id) {
    float y = configY + HEADER - configScroll;
    for (int i = 0; i < configRowCount(); i++) {
      String row = configRow(i);
      if (row.equals(id)) return y;
      y += ROW + configs.height(row);
    }
    return Float.NaN;
  }

  private float configFullHeight() {
    return Math.min(
        configContentHeight(),
        Math.max(ROW * 2, Math.min(band() - HEADER - 8, viewHeight - configY - HEADER - 34)));
  }

  private String drawConfigs(float mx, float my) {
    float h = configHeight(), total = configContentHeight();
    assets.shadow(canvas, configX, configY, WIDTH, HEADER + h, canvas.cornerRadius, opening * .7f);
    ModernAssets.rounded(
        canvas,
        configX,
        configY,
        WIDTH,
        HEADER + h,
        canvas.cornerRadius,
        ink(canvas.panel),
        true,
        true);
    boolean masked = beginContent(configX, configY, WIDTH, HEADER + h);
    float top = configY + HEADER;
    float bottom = top + Math.max(0, h - (masked ? 0 : Math.min(canvas.cornerRadius, h)));
    String hover = null;
    try {
      header("Configs", 6, configX, configY, h <= 0);
      if (h <= 0) return null;
      clip(configX, top, WIDTH, Math.max(0, bottom - top));
      float y = top - configScroll;
      for (int i = 0; i < configRowCount(); i++) {
        String id = configRow(i);
        String label =
            id.equals(ConfigActions.NEW)
                ? "New config"
                : id.equals(ConfigActions.TOOLS)
                    ? "File tools"
                    : id.isEmpty() ? (configs.reading() ? "Loading..." : "No configs") : id;
        boolean hit = my >= top && my < bottom && UiCanvas.inside(mx, my, configX, y, WIDTH, ROW);
        if (y + ROW > top && y < bottom) {
          if (!id.isEmpty() && id.equals(configs.selected()))
            ModernTheme.band(canvas, configX, y, WIDTH, ROW, opening);
          if (hit) canvas.rect(configX, y, WIDTH, ROW, ink(canvas.hover));
          canvas.label(
              label,
              configX + 4,
              canvas.font.centeredY(y, ROW),
              WIDTH - 17,
              ink(id.isEmpty() ? canvas.muted : canvas.text));
          if (!id.isEmpty())
            ModernAssets.arrow(
                canvas, configX + WIDTH - 7, y + ROW / 2, configs.arrow(id), ink(canvas.text));
          if (hit)
            hover =
                id.equals(ConfigActions.NEW)
                    ? "Save the current settings as a new config."
                    : id.equals(ConfigActions.TOOLS)
                        ? "Open the config folder or refresh the list."
                        : "Left-click to load. Right-click to expand settings.";
        }
        y += ROW;
        float block = configs.height(id);
        if (block > 0) configs.place(configX, y);
        if (block > 0 && y + block > top && y < bottom) {
          clip(
              configX,
              Math.max(y, top),
              WIDTH,
              Math.max(0, Math.min(y + block, bottom) - Math.max(y, top)));
          configs.draw(canvas, mx, my);
          clip(configX, top, WIDTH, Math.max(0, bottom - top));
        }
        y += block;
      }
      if (total > h + 1 && h > 8) {
        float thumb = Math.max(8, h * h / total);
        canvas.rect(
            configX + WIDTH - 1.2f,
            top + configScroll / Math.max(1, total - h) * (h - thumb),
            1,
            thumb,
            ink(0x80757575));
      }
      return hover;
    } finally {
      endContent();
    }
  }

  @Override
  public void initGui() {
    super.initGui();
    configs.refresh();
  }

  @Override
  public void type(char character, int code) {
    if (dragging == null) clearSway();
    if (!configs.type(character, code)) super.type(character, code);
  }

  @Override
  public void updateScreen() {
    super.updateScreen();
    configs.update();
  }

  @Override
  public void onGuiClosed() {
    clearSway();
    restoreHudPosition();
    configs.close();
    try {
      super.onGuiClosed();
    } finally {
      endContent();
      roundedClip.close();
      assets.close();
      backdrop.close();
      previousFrame = 0;
      opening = 0;
    }
  }
}
