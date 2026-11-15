package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;

import java.util.*;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleCategory;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.gui.panel.PanelLayout;
import perihelion.integration.minecraft189.gui.widget.SettingControl;
import perihelion.integration.minecraft189.gui.widget.SettingSection;

/** Native clicks and inline input through the real grouped HUD module. */
public final class HudDropdownProbe {
  private static int checks;

  @SuppressWarnings({"rawtypes", "unchecked"})
  public static void run(ClientRuntime runtime, Object game, Object screen) throws Exception {
    checks = 0;
    Map<String, Setting<?>> settings = runtime.modules().require("hud").module().settings();
    Map<Setting<?>, Object> saved = new LinkedHashMap<>();
    for (Setting<?> setting : settings.values()) saved.put(setting, setting.get());
    List<SettingControl> controls = controls(screen);
    int width = field(screen.getClass(), "width", "field_146294_l").getInt(screen);
    int height = field(screen.getClass(), "height", "field_146295_m").getInt(screen);
    try {
      draw(screen);
      PanelLayout.Panel focused = panel(screen);
      float restingY = n(screen, "hudRestingY");
      check(focused.y == 9, "HUD focus uses the full available height");
      method(screen.getClass(), "persist").invoke(screen);
      String encoded =
          ((Setting<?>) method(screen.getClass(), "layoutSetting").invoke(screen)).get().toString();
      com.google.gson.JsonArray layout =
          new com.google.gson.JsonParser().parse(encoded).getAsJsonArray();
      for (com.google.gson.JsonElement entry : layout)
        if (entry.getAsJsonObject().get("category").getAsString().equals("RENDER"))
          check(
              entry.getAsJsonObject().get("y").getAsFloat() == restingY,
              "Temporary HUD focus preserves the saved category position");
      check(
          controls.stream().filter(c -> c instanceof SettingSection && c.isVisible()).count() == 10,
          "Nine collapsed elements and one General section replace the flat settings list");
      ProductionHudProbe.capture("hud-dropdowns-overview.png");
      for (String[] element :
          new String[][] {
            {"Logo", "watermark"},
            {"Tab GUI", "tab_gui"},
            {"Array list", "modules"},
            {"Coordinates", "info"},
            {"Armor", "armor"},
            {"Potion effects", "effects"},
            {"Keystrokes", "keys"},
            {"Nearby players", "players"},
            {"Notifications", "notifications"}
          }) {
        SettingSection section = section(controls, element[0]);
        boolean originalEnabled = (Boolean) settings.get(element[1]).get();
        hit(screen, section, 0);
        check(
            settings.get(element[1]).get().equals(!originalEnabled) && !section.isOpen(),
            "Left-click toggles without expanding, like other modules");
        hit(screen, section, 0);
        hit(screen, section, 1);
        check(section.isOpen(), "Right-click opens element settings: " + element[0]);
        check(
            controls.stream().noneMatch(c -> c.setting == settings.get(element[1])),
            "Element settings contain no duplicate enable control: " + element[0]);
        check(
            settings.get(element[1]).get().equals(originalEnabled),
            "Opening settings preserves the enabled state: " + element[0]);
        hit(screen, section, 1);
        check(
            !section.isOpen() && settings.get(element[1]).get().equals(originalEnabled),
            "Collapse preserves values");
      }
      SettingSection array = section(controls, "Array list");
      hit(screen, array, 1);
      SettingControl modes = control(controls, settings.get("suffixes"));
      if ((Boolean) modes.setting.get()) hit(screen, modes, 0);
      check(
          !control(controls, settings.get("module-suffix")).isVisible(),
          "Hidden modes hide separator");
      hit(screen, modes, 0);
      check(
          control(controls, settings.get("module-suffix")).isVisible(),
          "Mode separator returns when useful");
      SettingSection text = child(controls, array, "Text");
      hit(screen, text, 0);
      SettingControl size = control(controls, settings.get("modules-scale"));
      hit(screen, size, 1);
      Object editor = field(screen.getClass(), "editor").get(screen);
      check(editor != null, "Size supports native inline input in the dropdown");
      method(editor.getClass(), new Class<?>[] {String.class}, "setText", "func_146180_a")
          .invoke(editor, "1.25");
      method(screen.getClass(), new Class<?>[] {char.class, int.class}, "type")
          .invoke(screen, '\0', Keyboard.KEY_RETURN);
      check(
          settings.get("modules-scale").get().equals(1.25),
          "Inline size changes the actual element setting");
      show(screen, size);
      ProductionHudProbe.capture("hud-dropdowns-array-text.png");
      hit(screen, size, 1);
      text.open(false);
      draw(screen);
      check(
          field(screen.getClass(), "editor").get(screen) == null,
          "Collapsing an ancestor cancels hidden inline input");
      SettingSection background = child(controls, array, "Background");
      hit(screen, background, 0);
      SettingControl blur = control(controls, settings.get("modules-blur"));
      if (!(Boolean) blur.setting.get()) hit(screen, blur, 0);
      check(
          control(controls, settings.get("modules-blur-strength")).isVisible(),
          "Blur strength follows Blur");
      show(screen, blur);
      ProductionHudProbe.capture("hud-dropdowns-background.png");
      hit(screen, background, 0);
      SettingSection position = child(controls, array, "Position");
      hit(screen, position, 0);
      SettingControl horizontal = control(controls, settings.get("modules-x"));
      hit(screen, horizontal, 0);
      check(
          ((Number) horizontal.setting.get()).doubleValue() < 1,
          "Position slider changes saved coordinates");
      show(screen, horizontal);
      ProductionHudProbe.capture("hud-dropdowns-position.png");
      for (int[] dimensions : new int[][] {{320, 240}, {427, 240}, {854, 480}}) {
        resize(screen, game, dimensions[0], dimensions[1]);
        show(screen, horizontal);
        PanelLayout.Panel panel = panel(screen);
        float body =
            (Float)
                method(screen.getClass(), new Class<?>[] {PanelLayout.Panel.class}, "body")
                    .invoke(screen, panel);
        check(
            panel.x >= 0 && panel.x + panel.width <= n(screen, "viewWidth") + 1,
            "Grouped settings remain within resized width");
        check(
            panel.y + panel.headerHeight + body <= n(screen, "viewHeight") + 1,
            "Grouped settings clip within resized height");
      }
      // Restore the real screen projection before capturing and clicking the design selector.
      resize(screen, game, width, height);
      Map<?, ?> views = (Map<?, ?>) field(screen.getClass(), "views").get(screen);
      for (Object view : (List<?>) views.get(ModuleCategory.RENDER)) {
        perihelion.core.module.ModuleEntry entry =
            (perihelion.core.module.ModuleEntry) field(view.getClass(), "entry").get(view);
        if (!entry.info().id().equals("targethud")) continue;
        boolean expanded = field(view.getClass(), "expanded").getBoolean(view);
        Setting<?> delay = entry.module().settings().get("hide-delay");
        Object oldDelay = delay.get();
        Setting<?> design = entry.module().settings().get("design");
        Object oldDesign = design.get();
        Setting<?> theme = entry.module().settings().get("theme");
        Object oldTheme = theme.get();
        perihelion.integration.minecraft189.gui.ClickGuiModule preferences =
            (perihelion.integration.minecraft189.gui.ClickGuiModule)
                runtime.modules().require("clickgui").module();
        boolean oldSearch = preferences.search.get();
        try {
          runtime.modules().setSetting("clickgui", preferences.search, true);
          field(view.getClass(), "expanded").setBoolean(view, true);
          method(screen.getClass(), new Class<?>[] {String.class}, "setQuery")
              .invoke(screen, "targethud");
          panel(screen).scroll = 0;
          draw(screen);
          List<SettingControl> targetControls =
              (List<SettingControl>) field(view.getClass(), "controls").get(view);
          check(
              targetControls.stream().filter(SettingControl::isVisible).count() == 4,
              "Target HUD starts with design, scale, hide delay and Arrange");
          SettingControl delayControl = control(targetControls, delay);
          hit(screen, delayControl, 1);
          Object delayEditor = field(screen.getClass(), "editor").get(screen);
          method(delayEditor.getClass(), new Class<?>[] {String.class}, "setText", "func_146180_a")
              .invoke(delayEditor, "1.7");
          method(screen.getClass(), new Class<?>[] {char.class, int.class}, "type")
              .invoke(screen, '\0', Keyboard.KEY_RETURN);
          check(
              delay.get().equals(1.7), "Target HUD delay edits through the native module controls");
          SettingControl scaleControl = control(targetControls, settings.get("target-scale"));
          hit(screen, scaleControl, 1);
          Object scaleEditor = field(screen.getClass(), "editor").get(screen);
          method(scaleEditor.getClass(), new Class<?>[] {String.class}, "setText", "func_146180_a")
              .invoke(scaleEditor, "1.25");
          method(screen.getClass(), new Class<?>[] {char.class, int.class}, "type")
              .invoke(screen, '\0', Keyboard.KEY_RETURN);
          check(settings.get("target-scale").get().equals(1.25),
              "Direct scale control edits the existing saved HUD scale");
          panel(screen).scroll = 0;
          draw(screen);
          ProductionHudProbe.capture("target-hud-module.png");
          SettingControl designControl = control(targetControls, design);
          hit(screen, designControl, 0);
          check(design.metadata().options().size() == 33, "All Target HUD designs are selectable");
          float last = lastOptionY(designControl);
          PanelLayout.Panel targetPanel = panel(screen);
          float top = targetPanel.y + targetPanel.headerHeight;
          float body =
              (Float)
                  method(screen.getClass(), new Class<?>[] {PanelLayout.Panel.class}, "body")
                      .invoke(screen, targetPanel);
          method(
                  screen.getClass(),
                  new Class<?>[] {float.class, float.class, float.class},
                  "scroll")
              .invoke(screen, targetPanel.x + 20, top + 5, last - (top + body - 12));
          draw(screen);
          last = lastOptionY(designControl);
          check(
              last > top && last < top + body, "Last design scrolls within the panel's hit bounds");
          ProductionHudProbe.capture("target-hud-design-selector.png");
          method(screen.getClass(), new Class<?>[] {float.class, float.class, int.class}, "click")
              .invoke(screen, n(designControl, "x") + 20, last, 0);
          method(screen.getClass(), "release").invoke(screen);
          check(
              design.get().toString().equals("MYAU_TENACITY"),
              "Last design selects through native mouse input");
          field(designControl.getClass(), "expanded").setBoolean(designControl, false);
          runtime
              .modules()
              .setSetting(
                  "targethud",
                  (Setting) theme,
                  perihelion.integration.minecraft189.hud.TargetHudModule.Theme.CUSTOM);
          panel(screen).scroll = 0;
          draw(screen);
          SettingControl accent = control(targetControls, entry.module().settings().get("primary"));
          hit(screen, accent, 0);
          check(
              (Boolean) method(accent.getClass(), "compact").invoke(accent),
              "Custom Target HUD uses the client theme's compact color picker");
          check(
              accent.height() - n(accent, "headerHeight") < 115,
              "Target color picker uses the same compact geometry as client themes");
          ProductionHudProbe.capture("target-hud-custom-colors.png");
          field(accent.getClass(), "expanded").setBoolean(accent, false);
        } finally {
          runtime.modules().setSetting("clickgui", preferences.search, oldSearch);
          runtime.modules().setSetting("targethud", (Setting) delay, oldDelay);
          runtime.modules().setSetting("targethud", (Setting) design, oldDesign);
          runtime.modules().setSetting("targethud", (Setting) theme, oldTheme);
          field(view.getClass(), "expanded").setBoolean(view, expanded);
          method(screen.getClass(), new Class<?>[] {String.class}, "setQuery").invoke(screen, "");
        }
      }
      check(GL11.glGetError() == GL11.GL_NO_ERROR, "Grouped settings preserve native GL state");
      System.out.println("[PERIHELION_SMOKE] HUD_DROPDOWNS PASS checks=" + checks);
    } finally {
      for (Map.Entry<Setting<?>, Object> entry : saved.entrySet())
        runtime.modules().setSetting("hud", (Setting) entry.getKey(), entry.getValue());
      for (SettingControl control : controls)
        if (control instanceof SettingSection) ((SettingSection) control).open(false);
      resize(screen, game, width, height);
      method(screen.getClass(), "openHud").invoke(screen);
      draw(screen);
    }
  }

  private static float lastOptionY(SettingControl control) throws Exception {
    float y = n(control, "y") + n(control, "headerHeight");
    int count = control.setting.metadata().options().size();
    for (int i = 0; i < count; i++) {
      float height =
          (Float)
              method(control.getClass(), new Class<?>[] {int.class}, "optionHeight")
                  .invoke(control, i);
      y += i == count - 1 ? height / 2 : height;
    }
    return y;
  }

  private static List<SettingControl> controls(Object screen) throws Exception {
    Map<?, ?> views = (Map<?, ?>) field(screen.getClass(), "views").get(screen);
    for (Object view : (List<?>) views.get(ModuleCategory.RENDER)) {
      Object entry = field(view.getClass(), "entry").get(view);
      Object info = method(entry.getClass(), "info").invoke(entry);
      if (method(info.getClass(), "id").invoke(info).equals("hud"))
        return (List<SettingControl>) field(view.getClass(), "controls").get(view);
    }
    throw new AssertionError("HUD view missing");
  }

  private static SettingControl control(List<SettingControl> controls, Setting<?> setting) {
    return controls.stream().filter(c -> c.setting == setting).findFirst().get();
  }

  private static SettingSection section(List<SettingControl> controls, String title) {
    return (SettingSection)
        controls.stream()
            .filter(c -> c instanceof SettingSection && c.setting.displayName().equals(title))
            .findFirst()
            .get();
  }

  private static SettingSection child(
      List<SettingControl> controls, SettingSection parent, String title) {
    for (int i = controls.indexOf(parent) + 1; i < controls.size(); i++)
      if (controls.get(i) instanceof SettingSection
          && controls.get(i).setting.displayName().equals(title))
        return (SettingSection) controls.get(i);
    throw new AssertionError("Section missing: " + title);
  }

  private static PanelLayout.Panel panel(Object screen) throws Exception {
    return ((PanelLayout) method(screen.getClass(), "model").invoke(screen))
        .find(ModuleCategory.RENDER);
  }

  private static void show(Object screen, SettingControl control) throws Exception {
    check(control.isVisible(), "Requested control is visible: " + control.setting.id());
    for (int attempt = 0; attempt < 5; attempt++) {
      draw(screen);
      PanelLayout.Panel panel = panel(screen);
      float y = n(control, "y"), top = panel.y + panel.headerHeight;
      float bottom =
          top
              + (Float)
                  method(screen.getClass(), new Class<?>[] {PanelLayout.Panel.class}, "body")
                      .invoke(screen, panel);
      float delta =
          y < top
              ? y - top - 2
              : y + control.height() > bottom ? y + control.height() - bottom + 2 : 0;
      if (Math.abs(delta) < 1) return;
      method(screen.getClass(), new Class<?>[] {float.class, float.class, float.class}, "scroll")
          .invoke(screen, panel.x + 20, top + 3, delta);
    }
    throw new AssertionError(
        "Could not scroll control into view: " + control.setting.displayName());
  }

  private static void hit(Object screen, SettingControl control, int button) throws Exception {
    show(screen, control);
    method(screen.getClass(), new Class<?>[] {float.class, float.class, int.class}, "click")
        .invoke(screen, n(control, "x") + n(control, "w") / 2, n(control, "y") + 6, button);
    method(screen.getClass(), "release").invoke(screen);
    draw(screen);
  }

  private static void draw(Object screen) throws Exception {
    method(NewVisualsProbe.class, new Class<?>[] {Object.class}, "draw").invoke(null, screen);
  }

  private static float n(Object object, String name) throws Exception {
    return field(object.getClass(), name).getFloat(object);
  }

  private static void resize(Object screen, Object game, int width, int height) throws Exception {
    method(
            screen.getClass(),
            new Class<?>[] {game.getClass(), int.class, int.class},
            "setWorldAndResolution",
            "func_146280_a")
        .invoke(screen, game, width, height);
  }

  private static void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }
}
