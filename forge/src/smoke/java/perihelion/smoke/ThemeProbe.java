package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleCategory;
import perihelion.core.module.ModuleEntry;
import perihelion.core.setting.ColorValue;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.gui.ClickGuiModule;
import perihelion.integration.minecraft189.gui.modern.ModernTheme;
import perihelion.integration.minecraft189.gui.panel.PanelLayout;
import perihelion.integration.minecraft189.gui.theme.ThemeModule;
import perihelion.integration.minecraft189.gui.widget.SettingControl;
import perihelion.integration.minecraft189.gui.widget.UiCanvas;

/** Native selection, theme propagation and compact picker flows through production input. */
public final class ThemeProbe {
  private static int checks;

  private ThemeProbe() {}

  public static void title(ClientRuntime runtime, Object modern) throws Exception {
    checks = 0;
    ThemeModule themes = (ThemeModule) runtime.modules().require("themes").module();
    ClickGuiModule gui = (ClickGuiModule) runtime.modules().require("clickgui").module();
    Map<Setting<?>, Object> savedGui = snapshot(runtime, "clickgui"),
        savedTheme = snapshot(runtime, "themes");
    Map<Object, Boolean> expansions = new IdentityHashMap<>();
    Map<ModuleCategory, float[]> positions = new LinkedHashMap<>();
    Map<?, ?> groups = (Map<?, ?>) field(modern.getClass(), "views").get(modern);
    for (Object group : groups.values())
      for (Object view : (List<?>) group) {
        expansions.put(view, field(view.getClass(), "expanded").getBoolean(view));
        for (Object control : (List<?>) field(view.getClass(), "controls").get(view))
          expansions.put(control, field(control.getClass(), "expanded").getBoolean(control));
      }
    PanelLayout model = (PanelLayout) method(modern.getClass(), "model").invoke(modern);
    for (PanelLayout.Panel panel : model.panels())
      positions.put(panel.category, new float[] {panel.x, panel.y, panel.scroll});
    String savedQuery = (String) field(modern.getClass(), "query").get(modern);
    Object search = field(modern.getClass(), "searchField").get(modern);
    String savedSearch =
        (String) method(search.getClass(), "getText", "func_146179_b").invoke(search);
    boolean savedFocus =
        (Boolean) method(search.getClass(), "isFocused", "func_146206_l").invoke(search);
    try {
      query(modern, "clickgui");
      PanelLayout.Panel panel = model.find(ModuleCategory.MISC);
      panel.x = 12;
      panel.y = 12;
      panel.collapsed = false;
      model.front(panel);
      field(view(modern, "clickgui").getClass(), "expanded")
          .setBoolean(view(modern, "clickgui"), true);
      draw(modern);
      SettingControl font = control(modern, "clickgui", "modern-font");
      field(font.getClass(), "expanded").setBoolean(font, false);
      draw(modern);
      header(modern, panel, font, 1);
      check(expanded(font), "Right-click opens the native font selection list");
      choose(modern, panel, font, ClickGuiModule.Font.INTER.name(), 0);
      draw(modern);
      check(
          gui.modernFont.get() == ClickGuiModule.Font.INTER,
          "Native option click changes the font family");
      check(expanded(font), "Changing font and remeasuring preserves the open list");
      header(modern, panel, font, 0);
      check(expanded(font), "Left-clicking an open selection header leaves it open");
      choose(modern, panel, font, ClickGuiModule.Font.ROBOTO.name(), 0);
      draw(modern);
      check(
          gui.modernFont.get() == ClickGuiModule.Font.ROBOTO && expanded(font),
          "A second font selection stays open across redraws");
      choose(modern, panel, font, ClickGuiModule.Font.SERIF.name(), 1);
      check(!expanded(font), "Right-clicking an option dismisses the selection list");
      check(
          gui.modernFont.get() == ClickGuiModule.Font.ROBOTO,
          "Dismissal does not choose the pointed option");
      header(modern, panel, font, 1);
      header(modern, panel, font, 1);
      check(!expanded(font), "Right-clicking the selection header closes it explicitly");
      runtime.modules().setSetting("clickgui", gui.modernFont, ClickGuiModule.Font.ROBOTO);
      query(modern, "themes");
      panel = model.find(ModuleCategory.MISC);
      Object themeView = view(modern, "themes");
      field(themeView.getClass(), "expanded").setBoolean(themeView, false);
      draw(modern);
      tap(modern, panel.x + 10, panel.y + panel.headerHeight + 8, 0);
      draw(modern);
      check(
          field(themeView.getClass(), "expanded").getBoolean(themeView),
          "Left-click opens Themes configuration");
      check(
          !runtime.modules().require("themes").requestedEnabled(),
          "Opening Themes does not activate a gameplay scope");
      SettingControl preset = control(modern, "themes", "preset");
      header(modern, panel, preset, 1);
      choose(modern, panel, preset, ThemeModule.Preset.OCEAN.name(), 0);
      draw(modern);
      check(
          themes.preset.get() == ThemeModule.Preset.OCEAN && expanded(preset),
          "Theme selection applies while its list stays open");
      ProductionHudProbe.capture("themes-preset-list.png");
      header(modern, panel, preset, 1);
      check(!expanded(preset), "Theme list closes on an explicit right-click");
      int presets = 0;
      for (ThemeModule.Preset choice : ThemeModule.Preset.values()) {
        if (choice == ThemeModule.Preset.CUSTOM) continue;
        presets++;
        runtime.modules().setSetting("themes", themes.preset, choice);
        draw(modern);
        UiCanvas canvas = canvas(modern);
        check(
            canvas.theme == themes.palette(),
            "Live GUI shares the selected cached palette: " + choice);
        check(
            canvas.accent == themes.palette().accent(0),
            "GUI controls use the selected theme accent: " + choice);
        check(
            canvas.panel == ModernTheme.PANEL
                && canvas.header == ModernTheme.HEADER
                && canvas.text == ModernTheme.TEXT,
            "Theme keeps graphite surfaces and readable neutral labels: " + choice);
        check(
            GL11.glGetError() == GL11.GL_NO_ERROR, "Theme renders with clean GL state: " + choice);
        if (choice == ThemeModule.Preset.OCEAN
            || choice == ThemeModule.Preset.AURORA
            || choice == ThemeModule.Preset.CHERRY) {
          ProductionHudProbe.capture(
              "themes-preset-" + choice.name().toLowerCase(java.util.Locale.ROOT) + ".png");
        }
      }
      check(presets == 20, "Twenty ready-made palettes are available");
      header(modern, panel, preset, 1);
      choose(modern, panel, preset, ThemeModule.Preset.CUSTOM.name(), 0);
      draw(modern);
      check(
          themes.preset.get() == ThemeModule.Preset.CUSTOM && expanded(preset),
          "Scrolling to the final Custom option selects it and leaves the list open");
      header(modern, panel, preset, 1);
      check(!expanded(preset), "The scrolled preset list closes on an explicit right-click");
      for (ThemeModule.ColorCount count : ThemeModule.ColorCount.values()) {
        runtime.modules().setSetting("themes", themes.colors, count);
        draw(modern);
        check(
            themes.palette().size() == count.count(),
            "Custom palette uses the selected number of stops");
        check(
            themes.color1.isVisible()
                && themes.color2.isVisible() == (count.count() >= 2)
                && themes.color3.isVisible() == (count == ThemeModule.ColorCount.THREE),
            "Custom exposes only the relevant color pickers");
        check(
            canvas(modern).theme == themes.palette(),
            "Custom colors propagate on the current frame");
      }
      colorPicker(runtime, modern, panel, themes);
      check(GL11.glGetError() == GL11.GL_NO_ERROR, "Theme and picker flows leave GL clean");
      System.out.println(
          "[PERIHELION_SMOKE] THEMES_TITLE PASS checks="
              + checks
              + " presets=20 customStops=1,2,3");
    } finally {
      method(modern.getClass(), "release").invoke(modern);
      method(modern.getClass(), "cancelEditor").invoke(modern);
      restore(runtime, "themes", savedTheme);
      restore(runtime, "clickgui", savedGui);
      for (Map.Entry<Object, Boolean> entry : expansions.entrySet())
        field(entry.getKey().getClass(), "expanded").setBoolean(entry.getKey(), entry.getValue());
      query(modern, savedQuery);
      method(search.getClass(), new Class<?>[] {String.class}, "setText", "func_146180_a")
          .invoke(search, savedSearch);
      method(search.getClass(), new Class<?>[] {boolean.class}, "setFocused", "func_146195_b")
          .invoke(search, savedFocus);
      draw(modern);
      for (Map.Entry<ModuleCategory, float[]> entry : positions.entrySet()) {
        PanelLayout.Panel panel = model.find(entry.getKey());
        panel.x = entry.getValue()[0];
        panel.y = entry.getValue()[1];
        panel.scroll = entry.getValue()[2];
        Object motion =
            method(modern.getClass(), new Class<?>[] {PanelLayout.Panel.class}, "panelMotion")
                .invoke(modern, panel);
        field(motion.getClass(), "targetScroll").setFloat(motion, panel.scroll);
      }
      draw(modern);
    }
  }

  private static void colorPicker(
      ClientRuntime runtime, Object modern, PanelLayout.Panel panel, ThemeModule themes)
      throws Exception {
    SettingControl color = control(modern, "themes", "color-1");
    header(modern, panel, color, 1);
    draw(modern);
    check(expanded(color), "Right-click opens the compact custom color picker");
    float header = number(color, "headerHeight");
    check(
        color.height() - header < 115,
        "Theme picker omits unrelated alpha, rainbow and speed controls");
    float sv = expose(modern, panel, color, header + 3 + 52 * .3f);
    float left = number(color, "x") + 3, width = number(color, "w") - 6;
    ColorValue before = themes.color1.get();
    click(modern, left + width * .65f, sv, 0);
    check(
        field(modern.getClass(), "captured").get(modern) == color,
        "SV click captures the actual native setting control");
    drag(modern, left + width * .8f, number(color, "y") + header + 3 + 52 * .2f);
    method(modern.getClass(), "release").invoke(modern);
    check(!themes.color1.get().equals(before), "SV drag updates the saved custom color");
    check(
        themes.color1.get().alpha() == 255 && !themes.color1.get().rainbow(),
        "Custom color dragging stores an opaque static accent");
    ColorValue released = themes.color1.get();
    drag(modern, left, number(color, "y") + header + 50);
    check(themes.color1.get().equals(released), "Mouse release prevents continued color dragging");
    draw(modern);
    float hueY = expose(modern, panel, color, header + 63);
    before = themes.color1.get();
    tap(modern, left + width * .62f, hueY, 0);
    draw(modern);
    check(!themes.color1.get().equals(before), "Hue strip changes the selected color");
    float hexY = expose(modern, panel, color, header + 79);
    tap(modern, number(color, "x") + 8, hexY, 0);
    check(
        field(modern.getClass(), "editor").get(modern) != null,
        "Native hex field opens the owned inline editor");
    for (char c : "39A7D2".toCharArray()) type(modern, c, 0);
    type(modern, '\n', Keyboard.KEY_RETURN);
    check(
        field(modern.getClass(), "editor").get(modern) == null,
        "Valid hex color commits and closes the inline editor");
    check(
        themes.color1.get().equals(ColorValue.rgb(57, 167, 210)),
        "Hex edit stores the exact opaque RGB value");
    draw(modern);
    check(
        canvas(modern).font.width("#39A7D2") <= number(color, "w") - 12,
        "Complete hex label fits the selected font");
    check(
        canvas(modern).theme == themes.palette(),
        "Picker edits immediately update the client palette");
    ProductionHudProbe.capture("themes-custom-picker.png");
    header(modern, panel, color, 1);
    check(!expanded(color), "Compact picker closes on an explicit right-click");
  }

  public static void hud(ClientRuntime runtime, Object editor, Object preview) throws Exception {
    checks = 0;
    ThemeModule themes = (ThemeModule) runtime.modules().require("themes").module();
    Map<Setting<?>, Object> saved = snapshot(runtime, "themes");
    try {
      for (ThemeModule.Preset preset :
          new ThemeModule.Preset[] {
            ThemeModule.Preset.OCEAN, ThemeModule.Preset.SUNSET, ThemeModule.Preset.CUSTOM
          }) {
        runtime.modules().setSetting("themes", themes.preset, preset);
        if (preset == ThemeModule.Preset.CUSTOM) {
          runtime.modules().setSetting("themes", themes.colors, ThemeModule.ColorCount.THREE);
          runtime.modules().setSetting("themes", themes.color1, ColorValue.rgb(80, 180, 220));
          runtime.modules().setSetting("themes", themes.color2, ColorValue.rgb(230, 140, 190));
          runtime.modules().setSetting("themes", themes.color3, ColorValue.rgb(150, 210, 140));
        }
        draw(editor);
        UiCanvas canvas = canvas(preview);
        check(
            canvas.theme == themes.palette(), "HUD preview shares the current palette: " + preset);
        Class<?> element =
            Class.forName("perihelion.integration.minecraft189.hud.HudModule$Element");
        Object[] elements = element.getEnumConstants();
        for (int index : new int[] {0, elements.length - 1}) {
          method(preview.getClass(), new Class<?>[] {element, long.class}, "appearance")
              .invoke(preview, elements[index], System.currentTimeMillis());
          check(
              field(preview.getClass(), "primary").getInt(preview)
                  == themes.palette().accent(index / (float) Math.max(1, elements.length - 1)),
              "HUD accents use the selected palette's complete range");
          check(
              field(preview.getClass(), "background").getInt(preview)
                      == perihelion.integration.minecraft189.gui.GuiTheme.alpha(
                          ((Number)
                                  runtime
                                      .modules()
                                      .require("hud")
                                      .module()
                                      .settings()
                                      .get(
                                          elements[index]
                                                  .toString()
                                                  .toLowerCase(java.util.Locale.ROOT)
                                              + "-opacity")
                                      .get())
                              .doubleValue(),
                          0x191817)
                  && field(preview.getClass(), "secondary").getInt(preview) == ModernTheme.TEXT,
              "HUD theme keeps readable neutral surfaces and text");
        }
        check(GL11.glGetError() == GL11.GL_NO_ERROR, "Themed HUD draws without GL errors");
        ProductionHudProbe.capture(
            "themes-hud-" + preset.name().toLowerCase(java.util.Locale.ROOT) + ".png");
      }
      System.out.println("[PERIHELION_SMOKE] THEMES_HUD PASS checks=" + checks);
    } finally {
      restore(runtime, "themes", saved);
      draw(editor);
    }
  }

  private static void header(
      Object screen, PanelLayout.Panel panel, SettingControl control, int button) throws Exception {
    float y = expose(screen, panel, control, number(control, "headerHeight") / 2);
    tap(screen, number(control, "x") + 8, y, button);
    draw(screen);
  }

  private static void choose(
      Object screen, PanelLayout.Panel panel, SettingControl control, String name, int button)
      throws Exception {
    List<String> options = control.setting.metadata().options();
    int index = 0;
    while (index < options.size() && !options.get(index).equalsIgnoreCase(name)) index++;
    check(index < options.size(), "Native option exists: " + name);
    float offset = number(control, "headerHeight");
    for (int i = 0; i < index; i++) offset += optionHeight(control, i);
    offset += optionHeight(control, index) / 2;
    float y = expose(screen, panel, control, offset);
    tap(screen, number(control, "x") + 8, y, button);
  }

  private static float expose(
      Object screen, PanelLayout.Panel panel, SettingControl control, float offset)
      throws Exception {
    for (int tries = 0; tries < 16; tries++) {
      draw(screen);
      float y = number(control, "y") + offset, top = panel.y + panel.headerHeight;
      float body =
          (Float)
              method(screen.getClass(), new Class<?>[] {PanelLayout.Panel.class}, "body")
                  .invoke(screen, panel);
      float end = top + body;
      if (y >= top + 2 && y < end - 2) return y;
      float delta = y < top + 2 ? y - top - 8 : y - end + 8;
      method(screen.getClass(), new Class<?>[] {float.class, float.class, float.class}, "scroll")
          .invoke(screen, panel.x + 8, top + Math.min(8, body / 2), delta);
    }
    throw new AssertionError(
        "Unable to expose "
            + control.setting.id()
            + " offset="
            + offset
            + " y="
            + number(control, "y")
            + " scroll="
            + panel.scroll);
  }

  private static float optionHeight(SettingControl control, int index) throws Exception {
    return (Float)
        method(control.getClass(), new Class<?>[] {int.class}, "optionHeight")
            .invoke(control, index);
  }

  private static SettingControl control(Object screen, String module, String setting)
      throws Exception {
    return (SettingControl)
        method(
                GuiHookProbe.class,
                new Class<?>[] {Object.class, String.class, String.class},
                "findControl")
            .invoke(null, screen, module, setting);
  }

  private static Object view(Object screen, String module) throws Exception {
    Map<?, ?> groups = (Map<?, ?>) field(screen.getClass(), "views").get(screen);
    for (Object group : groups.values())
      for (Object view : (List<?>) group)
        if (((ModuleEntry) field(view.getClass(), "entry").get(view)).info().id().equals(module))
          return view;
    throw new AssertionError("Missing module view: " + module);
  }

  private static boolean expanded(Object control) throws Exception {
    return field(control.getClass(), "expanded").getBoolean(control);
  }

  private static UiCanvas canvas(Object owner) throws Exception {
    return (UiCanvas) field(owner.getClass(), "canvas").get(owner);
  }

  private static void query(Object screen, String query) throws Exception {
    method(screen.getClass(), new Class<?>[] {String.class}, "setQuery").invoke(screen, query);
    Object search = field(screen.getClass(), "searchField").get(screen);
    method(search.getClass(), new Class<?>[] {String.class}, "setText", "func_146180_a")
        .invoke(search, query);
    method(search.getClass(), new Class<?>[] {boolean.class}, "setFocused", "func_146195_b")
        .invoke(search, false);
  }

  private static void draw(Object screen) throws Exception {
    method(NewVisualsProbe.class, new Class<?>[] {Object.class}, "draw").invoke(null, screen);
  }

  private static void click(Object screen, float x, float y, int button) throws Exception {
    method(screen.getClass(), new Class<?>[] {float.class, float.class, int.class}, "click")
        .invoke(screen, x, y, button);
  }

  private static void tap(Object screen, float x, float y, int button) throws Exception {
    click(screen, x, y, button);
    method(screen.getClass(), "release").invoke(screen);
  }

  private static void drag(Object screen, float x, float y) throws Exception {
    method(screen.getClass(), new Class<?>[] {float.class, float.class}, "drag")
        .invoke(screen, x, y);
  }

  private static void type(Object screen, char character, int code) throws Exception {
    method(screen.getClass(), new Class<?>[] {char.class, int.class}, "type")
        .invoke(screen, character, code);
  }

  private static float number(Object object, String name) throws Exception {
    return field(object.getClass(), name).getFloat(object);
  }

  private static Map<Setting<?>, Object> snapshot(ClientRuntime runtime, String module) {
    Map<Setting<?>, Object> saved = new LinkedHashMap<>();
    for (Setting<?> setting : runtime.modules().require(module).module().settings().values())
      saved.put(setting, setting.get());
    return saved;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void restore(ClientRuntime runtime, String module, Map<Setting<?>, Object> saved) {
    for (Map.Entry<Setting<?>, Object> entry : saved.entrySet())
      runtime.modules().setSetting(module, (Setting) entry.getKey(), entry.getValue());
  }

  private static void check(boolean value, String description) {
    checks++;
    if (!value) throw new AssertionError(description);
  }
}
