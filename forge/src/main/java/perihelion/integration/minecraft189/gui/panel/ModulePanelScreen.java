package perihelion.integration.minecraft189.gui.panel;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import perihelion.core.diagnostics.Failures;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.*;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.gui.*;
import perihelion.integration.minecraft189.gui.font.GuiFonts;
import perihelion.integration.minecraft189.gui.widget.*;
import perihelion.integration.minecraft189.render.*;

/** Native module-panel state, measurement and input shared by independent frontend renderers. */
public abstract class ModulePanelScreen extends GuiScreen implements SettingControl.Actions {
  protected abstract float rowHeight();

  protected String moduleLabel(ModuleEntry entry) {
    return GuiValues.readable(entry.info().name());
  }

  protected float settingsHeight(ModuleView view) {
    float result = rowHeight();
    for (SettingControl control : view.controls)
      if (visible(view, control)) result += control.height();
    return result;
  }

  /** Shared displayed expansion used by content measurement, rendering and input. */
  protected float expansionHeight(ModuleView view) {
    return view.expanded ? settingsHeight(view) : 0;
  }

  protected float searchX() {
    return 7;
  }

  protected float searchY() {
    return 7;
  }

  protected float searchTextX() {
    return searchX() + 4;
  }

  protected GuiFonts.Face editorFont() {
    return fonts.get(GuiTheme.font(preferences), GuiTheme.bold(preferences));
  }

  protected abstract void configureCanvas();

  protected abstract ClickGuiModule.ClientStyle frontendStyle();

  protected boolean modernFrontend() {
    return false;
  }

  protected boolean searchEnabled() {
    return preferences.search.get();
  }

  protected Setting<String> layoutSetting() {
    return preferences.layoutFor(frontendStyle());
  }

  protected Setting<String> utilitySetting() {
    return preferences.utilityLayoutFor(frontendStyle());
  }

  protected float requestedScale() {
    return preferences.scale.get().floatValue();
  }

  public final ClickGuiModule.ClientStyle style() {
    return frontendStyle();
  }

  protected float headerHeight() {
    return PanelLayout.HEADER;
  }

  protected float minimumPanelWidth() {
    return PanelLayout.WIDTH;
  }

  protected final boolean switchFrontend() {
    if (preferences.modern() == modernFrontend()
        && (modernFrontend() || preferences.clientStyle.get() == frontendStyle())) return false;
    if (preferences.modern()) {
      perihelion.integration.minecraft189.gui.modern.ModernScreen screen =
          new perihelion.integration.minecraft189.gui.modern.ModernScreen(
              runtime, preferences, fonts);
      screen.parent(parent);
      mc.displayGuiScreen(screen);
      return true;
    }
    ModulePanelScreen next =
        preferences.clientStyle.get() == ClickGuiModule.ClientStyle.RAVEN
            ? new perihelion.integration.minecraft189.gui.raven.RavenScreen(
                runtime, preferences, fonts)
            : new PanelScreen(runtime, preferences, fonts);
    next.parent(parent);
    mc.displayGuiScreen(next);
    return true;
  }

  protected static final float TAB_GAP = 4;
  protected static final float TAB_HEIGHT = 16, TAB_TOP = 7;
  protected final ClientRuntime runtime;
  protected GuiScreen parent;

  public void parent(GuiScreen parent) {
    this.parent = parent;
  }

  protected final ClickGuiModule preferences;
  protected final GuiFonts fonts;
  protected final PanelLayout layout = new PanelLayout();
  protected boolean hudMenu;
  protected final UtilityLayout utilities = new UtilityLayout();
  protected UtilityLayout.Box hudTab, configTab, utilityDrag;
  protected String utilitySeen = "";
  protected float utilityOffsetX, utilityOffsetY, pressX, pressY;
  protected boolean utilityMoved;
  protected int utilityAction;
  protected float configTabWidth = 40,
      moduleTabWidth = 54,
      moduleTabX,
      configAnchor = 48,
      hudAnchor = 150;
  protected float hudTabWidth = 24;
  protected ClickGuiModule.Font toolbarFont;
  protected ClickGuiModule.ClientStyle styleSeen;
  protected ClickGuiModule.TextWeight toolbarWeight;
  protected final EnumMap<ModuleCategory, List<ModuleView>> views =
      new EnumMap<>(ModuleCategory.class);
  protected final RenderState renderState = new RenderState();
  protected final UiCanvas canvas = new UiCanvas();
  protected final InlineTextFieldView inlineEditor = new InlineTextFieldView();
  protected final InlineTextFieldView searchEditor = new InlineTextFieldView();
  protected long searchEpoch;
  protected final Tooltip tooltip = new Tooltip();
  protected final Set<Setting<?>> brokenVisibility =
      Collections.newSetFromMap(new IdentityHashMap<Setting<?>, Boolean>());
  protected float scale = 1, viewWidth, viewHeight, pixels;
  protected boolean initialized, previousRepeat, searchShown;
  protected String query = "", layoutSeen = "", status = "";
  protected GuiTextField searchField, editor;
  protected Consumer<String> submit;
  protected PanelLayout.Panel editorPanel, pressedPanel;
  protected SettingControl editorControl, pressedControl;
  protected float editX, editY, editWidth;
  protected long caretEpoch;
  protected boolean replaceOnType;
  protected final KeybindEditor binding;
  protected PanelLayout.Panel dragging;
  protected float dragX, dragY;
  protected SettingControl captured;

  protected final class ModuleView {
    public final ModuleEntry entry;
    public final String search, label;
    public final List<SettingControl> controls = new ArrayList<>();
    public boolean expanded, matches = true;

    ModuleView(ModuleEntry entry) {
      this.entry = entry;
      search = GuiValues.searchable(entry);
      label = moduleLabel(entry);
      if (entry.module() instanceof perihelion.integration.minecraft189.hud.HudModule) {
        controls.addAll(
            perihelion.integration.minecraft189.hud.HudSettingsControls.create(
                (perihelion.integration.minecraft189.hud.HudModule) entry.module(),
                ModulePanelScreen.this,
                ModulePanelScreen.this::arrangeHud,
                rowHeight()));
        return;
      }
      if (entry.module() instanceof perihelion.integration.minecraft189.hud.TargetHudModule) {
        controls.addAll(
            ((perihelion.integration.minecraft189.hud.TargetHudModule) entry.module())
                .controls(ModulePanelScreen.this, ModulePanelScreen.this::arrangeHud, rowHeight()));
        return;
      }
      for (Setting<?> setting : entry.module().settings().values())
        controls.add(
            SettingControl.create(
                runtime.modules(), entry.info().id(), setting, ModulePanelScreen.this));
    }

    public float height() {
      return rowHeight() + expansionHeight(this);
    }
  }

  protected ModulePanelScreen(ClientRuntime runtime, ClickGuiModule preferences, GuiFonts fonts) {
    this.runtime = runtime;
    this.preferences = preferences;
    this.fonts = fonts;
    this.binding = new KeybindEditor(runtime.modules());
    this.searchShown = searchEnabled();
    for (ModuleCategory category : ModuleCategory.values()) views.put(category, new ArrayList<>());
    for (ModuleEntry entry : runtime.modules().entries())
      views.get(entry.info().category()).add(new ModuleView(entry));
    for (List<ModuleView> group : views.values())
      group.sort(Comparator.comparing(view -> view.label, String.CASE_INSENSITIVE_ORDER));
  }

  @Override
  public void initGui() {
    if (!initialized) {
      previousRepeat = Keyboard.areRepeatEventsEnabled();
      initialized = true;
    }
    Keyboard.enableRepeatEvents(true);
    runtime.input().setAllowed(false, Collections.emptyList());
    geometry(true);
  }

  protected float top() {
    return searchEnabled() ? 27 : 2;
  }

  protected void geometry(boolean resize) {
    styleSeen = frontendStyle();
    if (searchShown != searchEnabled()) {
      cancelEditor();
      boolean next = searchEnabled();
      searchShown = next;
      if (searchField != null && !next) {
        searchField.setText("");
        searchField.setFocused(false);
        setQuery("");
      }
    }
    boolean widthsChanged = updatePanelWidths();
    float nextScale = requestedScale();
    nextScale = Math.min(nextScale, width / (columnWidth() + 8));
    int columns = Math.max(1, (int) ((width / nextScale - 10) / (columnWidth() + 2)));
    int rows = (ModuleCategory.values().length + columns - 1) / columns;
    float requiredHeight = top() + 6 + rows * (headerHeight() + 16) + (rows - 1) * 3;
    nextScale = Math.min(nextScale, height / requiredHeight);
    // Keep search separate from all three toolbar actions in small, enlarged menus.
    if (searchEnabled()) nextScale = Math.min(nextScale, width / 270f);
    boolean changed =
        resize
            || widthsChanged
            || nextScale != scale
            || viewWidth != width / nextScale
            || viewHeight != height / nextScale;
    scale = nextScale;
    viewWidth = width / scale;
    viewHeight = height / scale;
    if (changed) {
      clearCapture();
      dragging = null;
      utilityDrag = null;
      cancelEditor();
      pixels = new ScaledResolution(mc).getScaleFactor() * scale;
      if (searchField == null) {
        searchField = new GuiTextField(1, mc.fontRendererObj, 8, 8, 180, 14);
        searchField.setMaxStringLength(128);
      }
      searchField.width = (int) Math.max(64, Math.min(260, viewWidth - 226));
      if (layout.panels().isEmpty() || !layoutSeen.equals(layoutSetting().get())) loadLayout();
      else if (defaultPositions()) placeDefaults();
      else
        for (PanelLayout.Panel panel : layout.panels()) panel.recover(viewWidth, viewHeight, top());
    } else if (!layoutSeen.equals(layoutSetting().get())
        && dragging == null
        && captured == null
        && editor == null) loadLayout();
    if (!searchEnabled() && !query.isEmpty()) {
      searchField.setText("");
      setQuery("");
      searchField.setFocused(false);
    }
    if (utilityDrag == null && !utilitySeen.equals(utilitySetting().get())) {
      utilitySeen = utilitySetting().get();
      utilities.load(utilitySeen);
    }
    float oldHudAnchor = hudAnchor, oldConfigAnchor = configAnchor;
    ClickGuiModule.TextWeight effectiveWeight =
        GuiTheme.bold(preferences)
            ? ClickGuiModule.TextWeight.BOLD
            : ClickGuiModule.TextWeight.REGULAR;
    if (toolbarFont != GuiTheme.font(preferences) || toolbarWeight != effectiveWeight) {
      toolbarFont = GuiTheme.font(preferences);
      toolbarWeight = effectiveWeight;
      try (RenderState ignored = renderState.open()) {
        GuiFonts.Face face =
            fonts.get(toolbarFont, toolbarWeight == ClickGuiModule.TextWeight.BOLD);
        float hudWidth = Math.max(24, (float) Math.ceil(face.width("Hud")) + 8);
        float configWidth = Math.max(34, (float) Math.ceil(face.width("Configs")) + 8);
        float moduleWidth = Math.max(44, (float) Math.ceil(face.width("Modules")) + 8);
        // Recognize the previous default positions while preserving independently dragged tabs.
        if (oldHudAnchor == 150 && oldConfigAnchor == 48) {
          oldConfigAnchor = configWidth + 8;
          oldHudAnchor = oldConfigAnchor + moduleWidth + hudWidth + 8;
        }
        hudTabWidth =
            configTabWidth =
                moduleTabWidth = Math.max(moduleWidth, Math.max(hudWidth, configWidth));
      }
      configAnchor = configTabWidth + 8;
      hudAnchor = configAnchor + moduleTabWidth + hudTabWidth + TAB_GAP * 2;
    }
    moduleTabX = viewWidth - configAnchor - moduleTabWidth - TAB_GAP;
    if (utilityDrag == null) utilities.resize(viewWidth, viewHeight, oldHudAnchor, oldConfigAnchor);
    hudTab = utilities.box("hud", viewWidth - hudAnchor, TAB_TOP);
    configTab = utilities.box("configs", viewWidth - configAnchor, TAB_TOP);
    if (utilityDrag == null) {
      if (Math.abs(hudTab.x - (viewWidth - oldHudAnchor)) < .01f) hudTab.x = viewWidth - hudAnchor;
      if (Math.abs(configTab.x - (viewWidth - oldConfigAnchor)) < .01f)
        configTab.x = viewWidth - configAnchor;
    }
    if (utilityDrag == null) {
      // Repair old resize drift only for tabs that are still docked at their default anchor.
      alignDockedTab(hudTab, viewWidth - hudAnchor);
      alignDockedTab(configTab, viewWidth - configAnchor);
      hudTab.recover(viewWidth, viewHeight, hudTabWidth, TAB_HEIGHT);
      configTab.recover(viewWidth, viewHeight, configTabWidth, TAB_HEIGHT);
    }
    // Search removal gives its space back; resize/recovery never runs during a panel drag.
    if (dragging == null)
      for (PanelLayout.Panel panel : layout.panels())
        if (panel.y < top()) panel.recover(viewWidth, viewHeight, top());
  }

  protected void loadLayout() {
    layoutSeen = layoutSetting().get();
    layout.load(layoutSeen, viewWidth, viewHeight, top());
    restoreExpansions();
    updatePanelWidths();
    if ("[]".equals(layoutSeen.trim())) placeDefaults();
  }

  protected void alignDockedTab(UtilityLayout.Box tab, float defaultX) {
    if (Math.abs(tab.x - defaultX) < .01f && Math.abs(tab.y - TAB_TOP) <= 2) tab.y = TAB_TOP;
  }

  protected boolean defaultPositions() {
    if (layout.panels().isEmpty()) return false;
    for (PanelLayout.Panel panel : layout.panels())
      if (shown(panel) && !panel.limited()) return false;
    return true;
  }

  protected float columnWidth() {
    float width = PanelLayout.WIDTH;
    for (PanelLayout.Panel panel : layout.panels())
      if (shown(panel)) width = Math.max(width, panel.width);
    return width;
  }

  protected boolean updatePanelWidths() {
    boolean changed = false;
    try (RenderState ignored = renderState.open()) {
      configureCanvas();
      for (PanelLayout.Panel panel : layout.panels()) {
        panel.headerHeight = headerHeight();
        float wanted =
            Math.max(
                minimumPanelWidth(), canvas.sectionFont.width(panel.category.displayName()) + 6);
        for (ModuleView view : views.get(panel.category)) {
          wanted = Math.max(wanted, canvas.font.width(view.label) + (modernFrontend() ? 20 : 12));
        }
        wanted = (float) Math.ceil(wanted);
        changed |= wanted != panel.width;
        panel.width = wanted;
        // Settings flow inside the category's normal width. Opening a module or changing a
        // value must never resize/reposition the panel or change the menu scale.
        for (ModuleView view : views.get(panel.category))
          for (SettingControl control : view.controls)
            control.measure(canvas.font, panel.width - 8, modernFrontend());
      }
    }
    return changed;
  }

  protected void placeDefaults() {
    List<PanelLayout.Panel> panels = new ArrayList<>(layout.panels());
    panels.removeIf(p -> views.get(p.category).isEmpty());
    PanelLayout.Panel render = layout.find(ModuleCategory.RENDER);
    if (panels.remove(render)) panels.add(0, render);
    float availableWidth = viewWidth;
    int columns =
        Math.max(1, Math.min(panels.size(), (int) ((availableWidth - 10) / (columnWidth() + 2))));
    float left = Math.max(4, (availableWidth - (columns * (columnWidth() + 2) - 2)) / 2);
    float start = Math.max(top(), viewHeight * .115f);
    List<List<PanelLayout.Panel>> stacks = new ArrayList<>();
    float[] stackHeights = new float[columns];
    Map<PanelLayout.Panel, Float> wanted = new IdentityHashMap<>();
    for (int i = 0; i < columns; i++) stacks.add(new ArrayList<>());
    for (PanelLayout.Panel panel : panels) {
      float content = 0;
      for (ModuleView view : views.get(panel.category)) content += view.height();
      float body = Math.max(16, Math.min(content, viewHeight * .64f));
      wanted.put(panel, body);
      int shortest = 0;
      for (int i = 1; i < columns; i++) if (stackHeights[i] < stackHeights[shortest]) shortest = i;
      stacks.get(shortest).add(panel);
      stackHeights[shortest] += headerHeight() + body + 3;
    }
    for (int column = 0; column < columns; column++) {
      List<PanelLayout.Panel> stack = stacks.get(column);
      int count = stack.size();
      float desired = 0;
      for (PanelLayout.Panel panel : stack) desired += wanted.get(panel);
      float available = viewHeight - start - 6 - count * headerHeight() - (count - 1) * 3;
      float factor =
          desired <= available || desired <= count * 16
              ? 1
              : Math.max(0, (available - count * 16) / (desired - count * 16));
      float y = start;
      for (PanelLayout.Panel panel : stack) {
        float body = 16 + (wanted.get(panel) - 16) * factor;
        panel.x = left + column * (columnWidth() + 2);
        panel.y = y;
        panel.recover(viewWidth, viewHeight, top());
        panel.limitBody(body, viewHeight);
        layout.front(panel);
        y += headerHeight() + body + 3;
      }
    }
  }

  protected void persist() {
    for (PanelLayout.Panel panel : layout.panels()) {
      panel.expandedRows.clear();
      for (ModuleView view : views.get(panel.category)) {
        String id = view.entry.info().id();
        if (view.expanded) panel.expandedRows.add(id);
        for (SettingControl control : view.controls)
          if (control.expanded()) panel.expandedRows.add(id + "/" + control.expansionKey());
      }
    }
    String encoded = layout.encode();
    layoutSeen = encoded;
    runtime.modules().setSetting("clickgui", layoutSetting(), encoded);
    utilitySeen = utilities.encode();
    runtime.modules().setSetting("clickgui", utilitySetting(), utilitySeen);
  }

  private void restoreExpansions() {
    for (PanelLayout.Panel panel : layout.panels())
      for (ModuleView view : views.get(panel.category)) {
        String id = view.entry.info().id();
        view.expanded = panel.expandedRows.contains(id);
        for (SettingControl control : view.controls)
          control.expanded(panel.expandedRows.contains(id + "/" + control.expansionKey()));
      }
  }

  public PanelLayout model() {
    return layout;
  }

  public void resetLayout() {
    cancelEditor();
    utilities.load("{}");
    utilitySeen = "{}";
    runtime.modules().setSetting("clickgui", utilitySetting(), "{}");
    geometry(false);
    layout.reset(viewWidth, viewHeight, top());
    restoreExpansions();
    placeDefaults();
    clearCapture();
    dragging = null;
    persist();
  }

  public void setQuery(String text) {
    query = text.trim().toLowerCase(Locale.ROOT);
    for (List<ModuleView> group : views.values())
      for (ModuleView view : group) view.matches = GuiValues.matches(view.search, query);
    for (PanelLayout.Panel panel : layout.panels()) panel.scroll = 0;
    validateEditor();
  }

  public int matchingModules() {
    int count = 0;
    for (List<ModuleView> group : views.values())
      for (ModuleView view : group) if (view.matches) count++;
    return count;
  }

  protected boolean searching() {
    return !query.isEmpty();
  }

  protected boolean shown(PanelLayout.Panel panel) {
    if (views.get(panel.category).isEmpty()) return false;
    if (!searching()) return true;
    for (ModuleView view : views.get(panel.category)) if (view.matches) return true;
    return false;
  }

  protected boolean visible(ModuleView view, SettingControl control) {
    if (brokenVisibility.contains(control.setting)) return false;
    try {
      return control.isVisible();
    } catch (Throwable failure) {
      Failures.rethrowFatal(failure);
      brokenVisibility.add(control.setting);
      runtime.modules().quarantine(view.entry.info().id(), failure);
      return false;
    }
  }

  protected void validateEditor() {
    if (editor == null || editorControl == null) return;
    for (List<ModuleView> group : views.values())
      for (ModuleView view : group)
        if (view.controls.contains(editorControl)) {
          if (!view.matches || !view.expanded || !visible(view, editorControl)) cancelEditor();
          return;
        }
    cancelEditor();
  }

  protected float content(PanelLayout.Panel panel) {
    float h = 0;
    for (ModuleView view : views.get(panel.category)) if (view.matches) h += view.height();
    return h;
  }

  protected float body(PanelLayout.Panel panel) {
    boolean expanded = false;
    for (ModuleView view : views.get(panel.category))
      if (view.matches && view.expanded) expanded = true;
    return panel.bodyHeight(viewHeight, content(panel), searching(), expanded);
  }

  public PanelLayout.Panel panelAt(float x, float y) {
    List<PanelLayout.Panel> panels = layout.panels();
    for (int i = panels.size() - 1; i >= 0; i--) {
      PanelLayout.Panel p = panels.get(i);
      if (shown(p) && UiCanvas.inside(x, y, p.x, p.y, p.width, headerHeight() + body(p))) return p;
    }
    return null;
  }

  protected void beginUtility(UtilityLayout.Box box, float mx, float my, int action) {
    cancelEditor();
    clearCapture();
    dragging = null;
    utilityDrag = box;
    utilityOffsetX = mx - box.x;
    utilityOffsetY = my - box.y;
    pressX = mx;
    pressY = my;
    utilityMoved = false;
    utilityAction = action;
  }

  protected void openHud() {
    cancelEditor();
    clearCapture();
    binding.cancel();
    hudMenu = false;
    searchField.setText("");
    searchField.setFocused(false);
    setQuery("");
    PanelLayout.Panel panel = layout.find(ModuleCategory.RENDER);
    panel.collapsed = false;
    panel.recover(viewWidth, viewHeight, top());
    layout.front(panel);
    float offset = 0;
    for (ModuleView view : views.get(ModuleCategory.RENDER)) {
      if (view.entry.info().id().equals("hud")) {
        view.expanded = true;
        panel.scroll = offset;
        break;
      }
      offset += view.height();
    }
    persist();
  }

  protected void arrangeHud() {
    cancelEditor();
    binding.cancel();
    hudMenu = false;
    persist();
    perihelion.integration.minecraft189.hud.HudModule hud =
        (perihelion.integration.minecraft189.hud.HudModule)
            runtime.modules().require("hud").module();
    if (!hud.openArrangement(this)) status = "Join a world to arrange the HUD.";
  }

  protected boolean utilityClick(float mx, float my, int button) {
    if (hudMenu) {
      float x = Math.min(hudTab.x, viewWidth - 100),
          y = Math.min(hudTab.y + TAB_HEIGHT + TAB_GAP, viewHeight - 50);
      if (UiCanvas.inside(mx, my, x, y, 100, 48)) {
        if (button == 0) {
          int row = (int) ((my - y) / 16);
          hudMenu = false;
          if (row == 0) openHud();
          else if (row == 1) runtime.modules().toggle("hud");
          else
            ((perihelion.integration.minecraft189.hud.HudModule)
                    runtime.modules().require("hud").module())
                .resetLayout();
        }
        return true;
      }
    }
    if (UiCanvas.inside(mx, my, hudTab.x, hudTab.y, hudTabWidth, TAB_HEIGHT)) {
      if (button == 1) hudMenu = !hudMenu;
      else if (button == 0) beginUtility(hudTab, mx, my, 1);
      return true;
    }
    if (UiCanvas.inside(mx, my, configTab.x, configTab.y, configTabWidth, TAB_HEIGHT)) {
      if (button == 1) openConfigs();
      else if (button == 0) beginUtility(configTab, mx, my, 2);
      return true;
    }
    if (UiCanvas.inside(mx, my, moduleTabX, TAB_TOP, moduleTabWidth, TAB_HEIGHT)) {
      if (defaultPositions()) placeDefaults();
      hudMenu = false;
      cancelEditor();
      return true;
    }
    hudMenu = false;
    return false;
  }

  protected void openConfigs() {
    cancelEditor();
    binding.cancel();
    hudMenu = false;
    persist();
    mc.displayGuiScreen(new ConfigScreen(runtime, this));
  }

  protected static int alpha(double opacity, int rgb) {
    return ((int) Math.round(opacity * 255) << 24) | rgb;
  }

  protected void clip(float x, float y, float w, float h) {
    GL11.glEnable(GL11.GL_SCISSOR_TEST);
    int left = (int) Math.floor(x * pixels),
        bottom = (int) Math.floor(mc.displayHeight - (y + h) * pixels);
    int right = (int) Math.ceil((x + w) * pixels),
        upper = (int) Math.ceil(mc.displayHeight - y * pixels);
    left = Math.max(0, left);
    bottom = Math.max(0, bottom);
    GL11.glScissor(
        left,
        bottom,
        Math.max(0, Math.min(mc.displayWidth, right) - left),
        Math.max(0, Math.min(mc.displayHeight, upper) - bottom));
  }

  protected void cancelEditor() {
    editor = null;
    editorPanel = null;
    editorControl = null;
    submit = null;
    replaceOnType = false;
    inlineEditor.reset();
  }

  @Override
  public void edit(
      String label, String initial, float x, float y, float width, Consumer<String> submit) {
    this.submit = submit;
    editorPanel = pressedPanel;
    editorControl = pressedControl;
    editY = y;
    editX = x + Math.min(editorFont().width(label) + 9, width - 64);
    editWidth = width - (editX - x) - 2;
    editor =
        new GuiTextField(
            2, mc.fontRendererObj, (int) editX, (int) (y + 2), Math.max(12, (int) editWidth), 12);
    editor.setEnableBackgroundDrawing(false);
    editor.setMaxStringLength(Math.max(2048, initial.length()));
    editor.setText(initial);
    editor.setFocused(true);
    editor.setCursorPositionEnd();
    editor.setSelectionPos(initial.length());
    replaceOnType = true;
    inlineEditor.reset();
    caretEpoch = System.currentTimeMillis();
    clearCapture();
    binding.cancel();
  }

  @Override
  protected void mouseClicked(int x, int y, int button) throws IOException {
    click(x / scale, y / scale, button);
  }

  /** Logical-coordinate input, shared by native callbacks and behavior smoke fixtures. */
  public void click(float mx, float my, int button) {
    tooltip.clear();
    geometry(false);
    status = "";
    if (binding.active()) {
      binding.mouse(button);
      return;
    }
    if (utilityClick(mx, my, button)) return;
    if (dragging != null || captured != null) return;
    if (binding.active()) {
      binding.mouse(button);
      return;
    }
    if (editor != null) {
      if (UiCanvas.inside(mx, my, editX, editY, editWidth, 16)) {
        if (button == 0) {
          canvas.font = editorFont();
          canvas.pixelScale = pixels;
          inlineEditor.placeCursor(canvas, editor, editX, editWidth, mx);
          replaceOnType = false;
          caretEpoch = System.currentTimeMillis();
        }
      } else cancelEditor();
      return;
    }
    if (searchEnabled()) {
      boolean hit = UiCanvas.inside(mx, my, searchX(), searchY(), searchField.width + 2, 16);
      searchField.setFocused(hit);
      if (hit) {
        if (button == 0) {
          canvas.font = editorFont();
          searchEditor.placeCursor(canvas, searchField, searchTextX(), searchField.width - 6, mx);
          searchEpoch = System.currentTimeMillis();
        }
        return;
      }
    }
    PanelLayout.Panel panel = panelAt(mx, my);
    if (panel == null) return;
    layout.front(panel);
    if (my < panel.y + headerHeight()) {
      if (button == 1) {
        panel.collapsed = !panel.collapsed;
        persist();
      } else if (button == 0) {
        dragging = panel;
        dragX = mx - panel.x;
        dragY = my - panel.y;
      }
      return;
    }
    float y = panel.y + headerHeight() - panel.scroll;
    for (ModuleView view : views.get(panel.category))
      if (view.matches) {
        if (my >= y && my < y + rowHeight()) {
          if (button == 0
              && view.entry.module()
                  instanceof perihelion.integration.minecraft189.gui.theme.ThemeModule)
            view.expanded = !view.expanded;
          else if (button == 0) runtime.modules().toggle(view.entry.info().id());
          else if (button == 1) view.expanded = !view.expanded;
          else if (button == 2) binding.begin(view.entry);
          persist();
          return;
        }
        y += rowHeight();
        float blockTop = y, blockHeight = expansionHeight(view);
        if (view.expanded && my >= blockTop && my < blockTop + blockHeight) {
          if (my < y + rowHeight()) {
            binding.begin(view.entry);
            return;
          }
          y += rowHeight();
          for (SettingControl control : view.controls)
            if (visible(view, control)) {
              float h = control.height();
              if (my >= y && my < y + h) {
                control.place(panel.x + 4, y, panel.width - 8);
                pressedPanel = panel;
                pressedControl = control;
                try {
                  if (control.press(mx, my, button)) captured = control;
                } catch (IllegalArgumentException invalid) {
                  status = invalid.getMessage();
                } finally {
                  pressedPanel = null;
                  pressedControl = null;
                }
                return;
              }
              y += h;
            }
        }
        y = blockTop + blockHeight;
      }
    persist();
  }

  @Override
  protected void mouseClickMove(int x, int y, int button, long elapsed) {
    if (button == 0) drag(x / scale, y / scale);
  }

  public void drag(float mx, float my) {
    if (utilityDrag != null) {
      utilityMoved |= Math.abs(mx - pressX) > 3 || Math.abs(my - pressY) > 3;
      if (utilityMoved) {
        utilityDrag.x = mx - utilityOffsetX;
        utilityDrag.y = my - utilityOffsetY;
      }
    } else if (dragging != null) {
      dragging.x = mx - dragX;
      dragging.y = my - dragY;
    } else if (captured != null) {
      try {
        if (captured.isVisible()) captured.drag(mx, my);
        else clearCapture();
      } catch (Throwable failure) {
        Failures.rethrowFatal(failure);
        runtime.modules().quarantine(captured.moduleId(), failure);
        clearCapture();
      }
    }
  }

  @Override
  protected void mouseReleased(int x, int y, int state) {
    if (state == 0) release();
  }

  protected final void clearCapture() {
    if (captured != null) captured.release();
    captured = null;
  }

  public void release() {
    if (utilityDrag != null) {
      boolean open = !utilityMoved && utilityAction == 1;
      boolean configs = !utilityMoved && utilityAction == 2;
      utilityDrag = null;
      if (runtime.isRunning()) {
        geometry(false);
        persist();
      }
      if (open) openHud();
      else if (configs) openConfigs();
    }
    if (dragging != null) {
      dragging.recover(viewWidth, viewHeight, top());
      dragging = null;
      if (runtime.isRunning()) persist();
    }
    clearCapture();
  }

  @Override
  public void handleMouseInput() throws IOException {
    super.handleMouseInput();
    int wheel = Mouse.getEventDWheel();
    if (wheel != 0)
      scroll(
          Mouse.getEventX() * (float) width / mc.displayWidth / scale,
          (height - Mouse.getEventY() * (float) height / mc.displayHeight - 1) / scale,
          wheel > 0 ? -24 : 24);
  }

  public void scroll(float mx, float my, float delta) {
    tooltip.clear();
    if (editor != null || binding.active() || captured != null || dragging != null) return;
    PanelLayout.Panel panel = panelAt(mx, my);
    if (panel != null && my >= panel.y + headerHeight())
      panel.scroll(delta, content(panel), body(panel));
  }

  @Override
  protected void keyTyped(char character, int code) throws IOException {
    type(character, code);
  }

  public void type(char character, int code) {
    tooltip.clear();
    validateEditor();
    if (binding.active()) {
      binding.key(code);
      return;
    }
    if (editor != null) {
      if (code == Keyboard.KEY_ESCAPE) {
        cancelEditor();
        status = "";
        return;
      }
      if (code == Keyboard.KEY_RETURN || code == Keyboard.KEY_NUMPADENTER) {
        try {
          submit.accept(editor.getText());
          cancelEditor();
          status = "";
        } catch (IllegalArgumentException invalid) {
          status = invalid.getMessage() == null ? "Invalid value" : invalid.getMessage();
        }
        return;
      }
      boolean replacing =
          (character >= ' ' && character != 127)
              || code == Keyboard.KEY_BACK
              || code == Keyboard.KEY_DELETE
              || (code == Keyboard.KEY_V && isCtrlKeyDown());
      if (replaceOnType && replacing) {
        editor.setCursorPositionEnd();
        editor.setSelectionPos(0);
      }
      if (replacing
          || code == Keyboard.KEY_LEFT
          || code == Keyboard.KEY_RIGHT
          || code == Keyboard.KEY_HOME
          || code == Keyboard.KEY_END
          || (code == Keyboard.KEY_A && isCtrlKeyDown())) replaceOnType = false;
      if (editor.textboxKeyTyped(character, code)) {
        status = "";
        caretEpoch = System.currentTimeMillis();
      }
      return;
    }
    if (code == Keyboard.KEY_ESCAPE) {
      mc.displayGuiScreen(parent);
      return;
    }
    if (code == Keyboard.KEY_R && isCtrlKeyDown()) {
      resetLayout();
      return;
    }
    if (code == Keyboard.KEY_F && isCtrlKeyDown()) {
      if (!modernFrontend()) runtime.modules().setSetting("clickgui", preferences.search, true);
      geometry(false);
      searchField.setFocused(true);
      return;
    }
    if (searchEnabled() && searchField.textboxKeyTyped(character, code)) {
      setQuery(searchField.getText());
      searchEpoch = System.currentTimeMillis();
    }
  }

  @Override
  public void updateScreen() {
    if (searchField != null) searchField.updateCursorCounter();
    if (editor != null) editor.updateCursorCounter();
  }

  @Override
  public void onGuiClosed() {
    tooltip.clear();
    // Closing a screen must cancel a held toolbar click, never activate it.
    utilityDrag = null;
    hudMenu = false;
    if (searchField != null) searchField.setFocused(false);
    release();
    if (runtime.isRunning()) persist();
    cancelEditor();
    binding.cancel();
    Keyboard.enableRepeatEvents(previousRepeat);
    initialized = false;
  }

  @Override
  public boolean doesGuiPauseGame() {
    return false;
  }
}
