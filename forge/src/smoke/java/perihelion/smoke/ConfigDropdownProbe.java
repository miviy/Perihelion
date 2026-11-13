package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import perihelion.core.input.Keybind;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleCategory;
import perihelion.integration.minecraft189.gui.ClickGuiModule;
import perihelion.integration.minecraft189.gui.panel.PanelLayout;
import perihelion.integration.minecraft189.gui.theme.ThemeModule;
import perihelion.integration.minecraft189.gui.widget.UiCanvas;

/** Real inline config input, scrolling, theme changes, and asynchronous preset CRUD. */
public final class ConfigDropdownProbe {
  private static int checks;

  public static void run(ClientRuntime runtime, Object game, Object screen) throws Exception {
    checks = 0;
    int originalWidth = field(screen.getClass(), "width", "field_146294_l").getInt(screen);
    int originalHeight = field(screen.getClass(), "height", "field_146295_m").getInt(screen);
    Object configs = field(screen.getClass(), "configs").get(screen);
    ClickGuiModule p = (ClickGuiModule) runtime.modules().require("clickgui").module();
    ThemeModule themes = (ThemeModule) runtime.modules().require("themes").module();
    ThemeModule.Preset palette = themes.preset.get();
    double background = p.background.get();
    Keybind binding = runtime.modules().require("clickgui").binding();
    ClickGuiModule.Font font = p.modernFont.get();
    String preset = "dropdown-smoke";
    Path file = runtime.config().store().profilePath(preset);
    try {
      pump(runtime, screen, configs);
      entry(screen, "@new", 0);
      check(flag(configs, "creating"), "New config expands inline");
      check(n(configs, "w") == 110, "Inline name editor retains category width");
      ProductionHudProbe.capture("config-inline-new.png");
      Object name = field(configs.getClass(), "name").get(configs);
      text(name, "bad name");
      action(screen, configs, "save");
      check(flag(configs, "error") && flag(configs, "creating"), "Invalid name retains draft");
      text(name, "");
      for (char letter : preset.toCharArray())
        method(screen.getClass(), new Class<?>[] {char.class, int.class}, "type")
            .invoke(screen, letter, 0);
      key(screen, Keyboard.KEY_RETURN);
      pump(runtime, screen, configs);
      check(Files.isRegularFile(file), "Keyboard creation saves asynchronously");
      byte[] original = Files.readAllBytes(file);
      entry(screen, "@new", 0);
      text(name, preset);
      action(screen, configs, "save");
      pump(runtime, screen, configs);
      check(
          flag(configs, "error") && java.util.Arrays.equals(original, Files.readAllBytes(file)),
          "Duplicate creation cannot overwrite a preset");
      key(screen, Keyboard.KEY_ESCAPE);
      check(!flag(configs, "creating"), "Escape cancels the focused name draft");
      check(profiles(configs).contains(preset), "New preset appears in the same list");

      runtime.modules().setSetting("clickgui", p.background, background == .55 ? .45 : .55);
      double liveBackground = p.background.get();
      Keybind changed = new Keybind(Keybind.Device.KEYBOARD, 43);
      for (boolean visuals : new boolean[] {false, true}) {
        for (boolean binds : new boolean[] {false, true}) {
          runtime.modules().setSetting("clickgui", p.background, liveBackground);
          runtime.modules().setBinding("clickgui", changed);
          open(screen, configs, preset);
          if (flag(configs, "loadVisuals") != visuals) action(screen, configs, "visuals");
          if (flag(configs, "loadKeybinds") != binds) action(screen, configs, "bindings");
          action(screen, configs, "load");
          pump(runtime, screen, configs);
          check(
              p.background.get() == (visuals ? background : liveBackground),
              "Visual choice independently controls ClickGUI settings");
          check(
              runtime.modules().require("clickgui").binding().equals(binds ? binding : changed),
              "Keybind choice independently controls bindings");
          check(
              java.util.Arrays.equals(original, Files.readAllBytes(file)),
              "Load preserves source preset");
          check(flag(configs, "expanded"), "Loading leaves settings expanded like a module");
        }
      }
      runtime.modules().setSetting("clickgui", p.background, liveBackground);
      PanelLayout model = (PanelLayout) method(screen.getClass(), "model").invoke(screen);
      PanelLayout.Panel combat = model.find(ModuleCategory.COMBAT);
      boolean collapsed = combat.collapsed;
      click(screen, combat.x + 8, combat.y + 8, 1);
      check(
          combat.collapsed != collapsed && flag(configs, "expanded"),
          "Expanded config leaves other module panels interactive");
      click(screen, combat.x + 8, combat.y + 8, 1);
      check(
          !(Boolean)
              method(configs.getClass(), new Class<?>[] {char.class, int.class}, "type")
                  .invoke(configs, '\0', Keyboard.KEY_ESCAPE),
          "Regular config expansion does not capture Escape");
      action(screen, configs, "update");
      check(
          java.util.Arrays.equals(original, Files.readAllBytes(file)),
          "Save over requires confirmation");
      action(screen, configs, "update");
      pump(runtime, screen, configs);
      check(
          !java.util.Arrays.equals(original, Files.readAllBytes(file)),
          "Save current updates preset");
      check(
          java.util.Arrays.equals(
              original, Files.readAllBytes(file.resolveSibling(preset + ".json.bak"))),
          "Save over keeps previous version");
      // A shorter regular face can fit all actions in a tall viewport. Force overflow
      // before asserting scrolling, then cancel the confirmation without deleting.
      resize(screen, game, 320, 240);
      draw(screen);
      action(screen, configs, "delete");
      check(
          n(screen, "configScroll") > 0
              && Files.exists(file)
              && field(configs.getClass(), "confirm").get(configs).equals("delete"),
          "Lower inline actions are reachable by scrolling in a compact viewport");
      entry(screen, preset, 1);
      open(screen, configs, preset);
      resize(screen, game, originalWidth, originalHeight);

      for (ClickGuiModule.Font choice : ClickGuiModule.Font.values()) {
        runtime.modules().setSetting("clickgui", p.modernFont, choice);
        draw(screen);
        bounds(screen, configs);
        UiCanvas c = (UiCanvas) field(screen.getClass(), "canvas").get(screen);
        for (Object action : actions(configs)) {
          boolean toggle = field(action.getClass(), "toggle").getBoolean(action);
          for (Object line : (List<?>) field(action.getClass(), "lines").get(action))
            check(
                c.font.width((String) line) <= 110 - 8 - (toggle ? 20 : 6) + .01f,
                "Inline action wraps within native setting insets");
        }
      }
      runtime.modules().setSetting("clickgui", p.modernFont, font);
      for (int[] size : new int[][] {{320, 240}, {427, 240}, {854, 480}}) {
        resize(screen, game, size[0], size[1]);
        draw(screen);
        bounds(screen, configs);
        action(screen, configs, "bindings");
        action(screen, configs, "bindings");
      }
      resize(screen, game, originalWidth, originalHeight);
      for (ThemeModule.Preset theme :
          new ThemeModule.Preset[] {ThemeModule.Preset.OCEAN, ThemeModule.Preset.ROSE}) {
        runtime.modules().setSetting("themes", themes.preset, theme);
        revealEntry(screen, preset);
        draw(screen);
        UiCanvas c = (UiCanvas) field(screen.getClass(), "canvas").get(screen);
        check(c.accent == themes.palette().accent(0), "Inline config follows the active theme");
        check(
            c.rowAccentAt(0) == themes.palette().fill(0),
            "Loaded config uses native theme row fill");
        ProductionHudProbe.capture("config-inline-" + theme.name().toLowerCase() + ".png");
      }
      action(screen, configs, "delete");
      check(Files.exists(file), "Delete requires confirmation");
      entry(screen, preset, 1);
      check(
          !flag(configs, "expanded") && Files.exists(file),
          "Right-click collapses and cancels confirmation");
      open(screen, configs, preset);
      action(screen, configs, "delete");
      action(screen, configs, "delete");
      pump(runtime, screen, configs);
      check(
          !Files.exists(file) && !profiles(configs).contains(preset),
          "Confirmed delete refreshes inline list");
      entry(screen, "@tools", 0);
      check(
          flag(configs, "expanded") && n(configs, "w") == 110, "File tools expand at module width");
      revealEntry(screen, "@tools");
      ProductionHudProbe.capture("config-inline-tools.png");
      action(screen, configs, "refresh");
      pump(runtime, screen, configs);
      draw(screen);
      check(GL11.glGetError() == GL11.GL_NO_ERROR, "Inline config leaves no GL errors");
      System.out.println("[PERIHELION_SMOKE] CONFIG_INLINE PASS checks=" + checks);
    } finally {
      method(configs.getClass(), "close").invoke(configs);
      runtime.modules().setSetting("themes", themes.preset, palette);
      runtime.modules().setSetting("clickgui", p.background, background);
      runtime.modules().setSetting("clickgui", p.modernFont, font);
      runtime.modules().setBinding("clickgui", binding);
      resize(screen, game, originalWidth, originalHeight);
    }
  }

  private static void open(Object screen, Object configs, String preset) throws Exception {
    if (!flag(configs, "expanded")
        || !field(configs.getClass(), "target").get(configs).equals(preset))
      entry(screen, preset, 1);
    draw(screen);
    check(flag(configs, "expanded"), "Right-click expands preset settings inline");
  }

  private static void entry(Object screen, String id, int button) throws Exception {
    revealEntry(screen, id);
    click(screen, n(screen, "configX") + 8, rowTop(screen, id) + 8, button);
    draw(screen);
  }

  private static float rowTop(Object screen, String id) throws Exception {
    return (Float)
        method(screen.getClass(), new Class<?>[] {String.class}, "configRowTop").invoke(screen, id);
  }

  private static void revealEntry(Object screen, String id) throws Exception {
    draw(screen);
    reveal(screen, rowTop(screen, id), 17);
    draw(screen);
  }

  private static void reveal(Object screen, float y, float h) throws Exception {
    float top = n(screen, "configY") + 22;
    float bottom = top + (Float) method(screen.getClass(), "configFullHeight").invoke(screen) - 5;
    float delta = y < top ? y - top : y + h > bottom ? y + h - bottom : 0;
    method(screen.getClass(), new Class<?>[] {float.class, float.class, float.class}, "scroll")
        .invoke(screen, n(screen, "configX") + 5, top + 5, delta);
  }

  private static void action(Object screen, Object configs, String id) throws Exception {
    draw(screen);
    for (Object action : actions(configs)) {
      if (!field(action.getClass(), "id").get(action).equals(id)) continue;
      reveal(screen, n(action, "y"), n(action, "h"));
      draw(screen);
      for (Object placed : actions(configs)) {
        if (field(placed.getClass(), "id").get(placed).equals(id)) {
          click(screen, n(configs, "x") + 10, n(placed, "y") + n(placed, "h") / 2, 0);
          return;
        }
      }
    }
    throw new AssertionError("Missing inline action " + id);
  }

  private static List<?> actions(Object configs) throws Exception {
    return (List<?>) field(configs.getClass(), "actions").get(configs);
  }

  private static void pump(ClientRuntime runtime, Object screen, Object configs) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    do {
      runtime.tasks().drain();
      method(screen.getClass(), "updateScreen", "func_73876_c").invoke(screen);
      if (!(Boolean) method(configs.getClass(), "busy").invoke(configs)) return;
      Thread.sleep(2);
    } while (System.nanoTime() < deadline);
    throw new AssertionError("Config operation did not complete");
  }

  private static void bounds(Object screen, Object configs) throws Exception {
    check(n(configs, "w") == 110, "Inline settings keep the module panel width");
    check(
        n(screen, "configX") >= 0 && n(screen, "configX") + 110 <= n(screen, "viewWidth") + 1,
        "Config panel fits viewport width");
    float visible = (Float) method(screen.getClass(), "configFullHeight").invoke(screen);
    check(
        n(screen, "configY") + 22 + visible <= n(screen, "viewHeight") + 1,
        "Expanded content clips to category viewport");
  }

  private static void resize(Object screen, Object game, int w, int h) throws Exception {
    method(
            screen.getClass(),
            new Class<?>[] {game.getClass(), int.class, int.class},
            "setWorldAndResolution",
            "func_146280_a")
        .invoke(screen, game, w, h);
  }

  private static void draw(Object screen) throws Exception {
    method(NewVisualsProbe.class, new Class<?>[] {Object.class}, "draw").invoke(null, screen);
  }

  private static void click(Object screen, float x, float y, int button) throws Exception {
    method(screen.getClass(), new Class<?>[] {float.class, float.class, int.class}, "click")
        .invoke(screen, x, y, button);
  }

  private static void key(Object screen, int code) throws Exception {
    method(screen.getClass(), new Class<?>[] {char.class, int.class}, "type")
        .invoke(screen, '\0', code);
  }

  private static void text(Object input, String value) throws Exception {
    method(input.getClass(), new Class<?>[] {String.class}, "setText", "func_146180_a")
        .invoke(input, value);
  }

  private static List<?> profiles(Object configs) throws Exception {
    return (List<?>) field(configs.getClass(), "profiles").get(configs);
  }

  private static float n(Object owner, String id) throws Exception {
    return field(owner.getClass(), id).getFloat(owner);
  }

  private static boolean flag(Object owner, String id) throws Exception {
    return field(owner.getClass(), id).getBoolean(owner);
  }

  private static void check(boolean ok, String message) {
    checks++;
    if (!ok) throw new AssertionError(message);
  }
}
