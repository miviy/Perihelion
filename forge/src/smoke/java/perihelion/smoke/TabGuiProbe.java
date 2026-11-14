package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;

import java.lang.reflect.*;
import java.util.*;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleEntry;
import perihelion.core.setting.Setting;

/** Native keyboard focus, setting writes and saved-anchor regressions for the Tab GUI. */
public final class TabGuiProbe {
  private static int checks;

  public static void run(ClientRuntime runtime, Object game, Object hud) throws Exception {
    checks = 0;
    Class<?> rendererType = Class.forName("perihelion.integration.minecraft189.hud.HudRenderer");
    Constructor<?> constructor = rendererType.getDeclaredConstructor(hud.getClass());
    constructor.setAccessible(true);
    Object renderer = constructor.newInstance(hud);
    Object nav = field(rendererType, "navigation").get(renderer);
    Class<?> elementType =
        Class.forName("perihelion.integration.minecraft189.hud.HudModule$Element");
    Object tab = Enum.valueOf((Class) elementType, "TAB_GUI");
    Method render =
        method(
            rendererType,
            new Class<?>[] {int.class, int.class, boolean.class, elementType, int.class, int.class},
            "render");
    Method key = method(nav.getClass(), new Class<?>[] {int.class}, "key");
    Method navigate = method(rendererType, new Class<?>[] {int.class, char.class}, "navigate");
    Field screen = field(game.getClass(), "currentScreen", "field_71462_r");
    Method display =
        method(
            game.getClass(),
            new Class<?>[] {Class.forName("net.minecraft.client.gui.GuiScreen")},
            "displayGuiScreen",
            "func_147108_a");
    Object oldScreen = screen.get(game);
    boolean oldInput = runtime.input().isAllowed();
    Map<Setting<?>, Object> originals = new LinkedHashMap<>();
    String[] ids = {
      "tab_gui",
      "tab_gui-x",
      "tab_gui-y",
      "watermark",
      "modules",
      "info",
      "target",
      "armor",
      "effects",
      "keys",
      "players",
      "notifications"
    };
    for (String id : ids) {
      Setting<?> setting = runtime.modules().require("hud").module().settings().get(id);
      originals.put(setting, setting.get());
    }
    Setting<?> sprint = runtime.modules().require("sprint").module().settings().get("cancel-invis");
    Object oldSprint = sprint.get();
    Setting<?> names = runtime.modules().require("friends").module().settings().get("names");
    Object oldNames = names.get();
    Setting<?> visuals = runtime.modules().require("clickgui").module().settings().get("visuals");
    Setting<?> clientStyle =
        runtime.modules().require("clickgui").module().settings().get("client-style");
    Object oldVisuals = visuals.get(), oldStyle = clientStyle.get();
    try {
      display.invoke(game, new Object[] {null});
      put(
          runtime,
          "hud",
          runtime.modules().require("hud").module().settings().get("tab_gui"),
          true);
      for (int width : new int[] {320, 427, 1920}) {
        for (double fraction : new double[] {0, .5, 1}) {
          put(
              runtime,
              "hud",
              runtime.modules().require("hud").module().settings().get("tab_gui-x"),
              fraction);
          put(
              runtime,
              "hud",
              runtime.modules().require("hud").module().settings().get("tab_gui-y"),
              fraction);
          field(nav.getClass(), "expanded").setBoolean(nav, false);
          field(nav.getClass(), "settingsOpen").setBoolean(nav, false);
          render.invoke(renderer, width, 240, true, null, 0, 0);
          Object box = ((Map<?, ?>) field(rendererType, "boxes").get(renderer)).get(tab);
          int x = field(box.getClass(), "anchorX").getInt(box);
          int y = field(box.getClass(), "anchorY").getInt(box);
          select(nav, "sprint");
          key.invoke(nav, Keyboard.KEY_RIGHT);
          render.invoke(renderer, width, 240, true, null, 0, 0);
          anchor(box, x, y, width);
          key.invoke(nav, Keyboard.KEY_RIGHT);
          render.invoke(renderer, width, 240, true, null, 0, 0);
          anchor(box, x, y, width);
          key.invoke(nav, Keyboard.KEY_LEFT);
          key.invoke(nav, Keyboard.KEY_LEFT);
          render.invoke(renderer, width, 240, true, null, 0, 0);
          anchor(box, x, y, width);
        }
      }
      select(nav, "sprint");
      key.invoke(nav, Keyboard.KEY_RIGHT);
      key.invoke(nav, Keyboard.KEY_RIGHT);
      key.invoke(nav, Keyboard.KEY_RETURN);
      check(!sprint.get().equals(oldSprint), "Tab GUI edits the native Boolean setting");
      render.invoke(renderer, 427, 240, true, null, 0, 0);
      Object compactRows = field(rendererType, "navigationSettings").get(renderer);
      check(
          ((int[]) field(compactRows.getClass(), "heights").get(compactRows))[0] == 11,
          "Boolean settings share the module-list row height");
      put(runtime, "sprint", sprint, oldSprint);
      render.invoke(renderer, 427, 240, true, null, 0, 0);
      compactRows = field(rendererType, "navigationSettings").get(renderer);
      check(
          ((List<?>) field(compactRows.getClass(), "values").get(compactRows))
              .get(0)
              .equals(Boolean.TRUE.equals(oldSprint) ? "+" : "-"),
          "Tab GUI refreshes a setting changed through the shared module manager");
      key.invoke(nav, Keyboard.KEY_LEFT);
      key.invoke(nav, Keyboard.KEY_LEFT);
      select(nav, "friends");
      key.invoke(nav, Keyboard.KEY_RIGHT);
      key.invoke(nav, Keyboard.KEY_RIGHT);
      field(nav.getClass(), "settingSelected").setInt(nav, 1);
      boolean oldRepeat = Keyboard.areRepeatEventsEnabled();
      navigate.invoke(renderer, Keyboard.KEY_RETURN, '\0');
      Object input = screen.get(game);
      check(
          input != null && input.getClass().getSimpleName().equals("NavigationInput"),
          "string editor owns native GUI focus");
      check(Keyboard.areRepeatEventsEnabled(), "native settings enable keyboard repeat");
      Method type =
          method(
              input.getClass(), new Class<?>[] {char.class, int.class}, "keyTyped", "func_73869_a");
      type.invoke(input, '\0', Keyboard.KEY_DELETE);
      for (char character : "TabProbe".toCharArray()) type.invoke(input, character, Keyboard.KEY_A);
      check(names.get().equals(oldNames), "typing leaves the committed setting unchanged");
      type.invoke(input, '\0', Keyboard.KEY_RETURN);
      check(names.get().equals("TabProbe"), "Enter commits through the native setting boundary");
      check(screen.get(game) == input, "text commit retains settings focus for safe navigation");
      type.invoke(input, '\0', Keyboard.KEY_LEFT);
      check(screen.get(game) == null, "Left back releases native settings focus");
      check(
          Keyboard.areRepeatEventsEnabled() == oldRepeat,
          "closing settings restores keyboard repeat");
      key.invoke(nav, Keyboard.KEY_RIGHT);
      put(
          runtime,
          "hud",
          runtime.modules().require("hud").module().settings().get("tab_gui-x"),
          0.0);
      put(
          runtime,
          "hud",
          runtime.modules().require("hud").module().settings().get("tab_gui-y"),
          0.0);
      // Isolate this control in the captures so other preview cards do not show through its tint.
      for (String id :
          new String[] {
            "modules", "info", "target", "armor", "effects", "keys", "players", "notifications"
          })
        put(runtime, "hud", runtime.modules().require("hud").module().settings().get(id), false);
      for (String variant : new String[] {"new", "adjust", "raven"}) {
        put(runtime, "clickgui", visuals, visuals.parse(variant.equals("new") ? "NEW" : "OLD"));
        put(
            runtime,
            "clickgui",
            clientStyle,
            clientStyle.parse(variant.equals("raven") ? "RAVEN" : "ADJUST"));
        select(nav, "antibot");
        field(nav.getClass(), "expanded").setBoolean(nav, true);
        field(nav.getClass(), "settingsOpen").setBoolean(nav, false);
        GL11.glClearColor(.07f, .075f, .09f, 1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        int clearBackground = pixel(0, 0);
        render.invoke(renderer, 427, 240, true, null, 0, 0);
        Object pane = field(rendererType, "navigationLayout").get(renderer);
        Object box = ((Map<?, ?>) field(rendererType, "boxes").get(renderer)).get(tab);
        int paneX = field(pane.getClass(), "paneX").getInt(pane);
        int paneWidth = field(pane.getClass(), "paneWidth").getInt(pane);
        int moduleBackground = backgroundPixel(game, box, paneX, paneWidth);
        check(
            moduleBackground != clearBackground,
            "Background sample is inside the rendered pane: " + variant);
        ProductionHudProbe.capture("tab-gui-" + variant + "-modules.png");
        if (variant.equals("new")) ProductionHudProbe.capture("tab-gui-modules.png");
        key.invoke(nav, Keyboard.KEY_RIGHT);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        render.invoke(renderer, 427, 240, true, null, 0, 0);
        Object settingsPane = field(rendererType, "navigationLayout").get(renderer);
        check(
            field(settingsPane.getClass(), "paneX").getInt(settingsPane) == paneX
                && field(settingsPane.getClass(), "paneWidth").getInt(settingsPane) == paneWidth,
            "Module and settings panes share their position and width: " + variant);
        check(
            backgroundPixel(game, box, paneX, paneWidth) == moduleBackground,
            "Module and settings panes use identical background pixels: " + variant);
        Object settingRows = field(rendererType, "navigationSettings").get(renderer);
        for (int height : (int[]) field(settingRows.getClass(), "heights").get(settingRows))
          check(height == 11 || height == 22, "Settings follow the common row grid: " + variant);
        ProductionHudProbe.capture("tab-gui-" + variant + "-settings.png");
        if (variant.equals("new")) ProductionHudProbe.capture("tab-gui-settings.png");
      }
      check(GL11.glGetError() == GL11.GL_NO_ERROR, "Tab GUI setting panes leave GL clean");
    } finally {
      ((AutoCloseable) renderer).close();
      put(runtime, "clickgui", visuals, oldVisuals);
      put(runtime, "clickgui", clientStyle, oldStyle);
      put(runtime, "sprint", sprint, oldSprint);
      put(runtime, "friends", names, oldNames);
      for (Map.Entry<Setting<?>, Object> original : originals.entrySet())
        put(runtime, "hud", original.getKey(), original.getValue());
      display.invoke(game, new Object[] {oldScreen});
      runtime.input().setAllowed(oldInput, Collections.emptyList());
    }
    System.out.println("[PERIHELION_SMOKE] TAB_GUI PASS checks=" + checks);
  }

  private static int backgroundPixel(Object game, Object box, int paneX, int paneWidth)
      throws Exception {
    float scale = field(box.getClass(), "scale").getFloat(box);
    int x = field(box.getClass(), "anchorX").getInt(box),
        y = field(box.getClass(), "anchorY").getInt(box);
    int displayWidth = field(game.getClass(), "displayWidth", "field_71443_c").getInt(game);
    int displayHeight = field(game.getClass(), "displayHeight", "field_71440_d").getInt(game);
    int px = Math.round((x + (paneX + paneWidth - 4) * scale) * displayWidth / 427f);
    int py = displayHeight - 1 - Math.round((y + 5 * scale) * displayHeight / 240f);
    return pixel(px, py);
  }

  private static int pixel(int x, int y) {
    java.nio.ByteBuffer pixel = org.lwjgl.BufferUtils.createByteBuffer(4);
    GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
    return (pixel.get(0) & 255) << 16 | (pixel.get(1) & 255) << 8 | pixel.get(2) & 255;
  }

  private static void anchor(Object box, int x, int y, int width) throws Exception {
    check(
        field(box.getClass(), "anchorX").getInt(box) == x
            && field(box.getClass(), "anchorY").getInt(box) == y,
        "opening, editing and closing preserve the saved category anchor");
    check(
        field(box.getClass(), "anchorW").getInt(box) == 65
            && field(box.getClass(), "anchorH").getInt(box) == 66,
        "drag persistence uses permanent category dimensions");
    int left = field(box.getClass(), "x").getInt(box), top = field(box.getClass(), "y").getInt(box);
    check(
        left >= 0
            && top >= 0
            && left + field(box.getClass(), "w").getInt(box) <= width
            && top + field(box.getClass(), "h").getInt(box) <= 240,
        "expanded drawing bounds fit near every edge");
  }

  private static void select(Object nav, String id) throws Exception {
    List<List<ModuleEntry>> groups =
        (List<List<ModuleEntry>>) field(nav.getClass(), "groups").get(nav);
    for (int category = 0; category < groups.size(); category++)
      for (int selected = 0; selected < groups.get(category).size(); selected++)
        if (groups.get(category).get(selected).info().id().equals(id)) {
          field(nav.getClass(), "category").setInt(nav, category);
          field(nav.getClass(), "selected").setInt(nav, selected);
          return;
        }
    throw new AssertionError("Missing Tab GUI module: " + id);
  }

  @SuppressWarnings("unchecked")
  private static void put(ClientRuntime runtime, String module, Setting<?> setting, Object value) {
    runtime.modules().setSetting(module, (Setting<Object>) setting, value);
  }

  private static void check(boolean success, String message) {
    if (!success) throw new AssertionError(message);
    checks++;
  }
}
