package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;

import java.lang.reflect.*;
import java.util.*;
import org.lwjgl.input.Keyboard;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.*;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.gui.ClickGuiModule;
import perihelion.integration.minecraft189.gui.panel.PanelLayout;

/** Regressions for the reported menu transitions, expansion and Array list filtering. */
public final class GuiRegressionProbe {
  private static int checks;

  public static void run(ClientRuntime runtime, Object game, Object hud) throws Exception {
    checks = 0;
    Class<?> screenType = Class.forName("net.minecraft.client.gui.GuiScreen");
    Field current = field(game.getClass(), "currentScreen", "field_71462_r");
    Method display =
        method(game.getClass(), new Class<?>[] {screenType}, "displayGuiScreen", "func_147108_a");
    // Invoke the game's virtual callback, so a missing/remapping-broken override fails too.
    Method escape =
        method(screenType, new Class<?>[] {char.class, int.class}, "keyTyped", "func_73869_a");
    Object original = current.get(game);
    ClickGuiModule preferences = (ClickGuiModule) runtime.modules().require("clickgui").module();
    Map<Setting<?>, Object> saved = new LinkedHashMap<>();
    for (String id : new String[] {"clickgui", "hud"})
      for (Setting<?> setting : runtime.modules().require(id).module().settings().values())
        saved.put(setting, setting.get());
    boolean oldHud = runtime.modules().require("hud").requestedEnabled();
    boolean oldBright = runtime.modules().require("fullbright").requestedEnabled();
    boolean oldSprint = runtime.modules().require("sprint").requestedEnabled();
    try {
      runtime.modules().setSetting("clickgui", preferences.utilityLayout, "{}");
      runtime.modules().setSetting("clickgui", preferences.layout, "[]");
      runtime.modules().setSetting("clickgui", preferences.search, false);
      runtime.commands().execute("gui", line -> {});
      Object menu = current.get(game);
      for (ClickGuiModule.Font font : ClickGuiModule.Font.values()) {
        runtime.modules().setSetting("clickgui", preferences.font, font);
        for (double scale : new double[] {1, 1.05, 1.5}) {
          runtime.modules().setSetting("clickgui", preferences.scale, scale);
          draw(menu);
          float[] before = toolbar(menu);
          clickTab(menu, "configTab");
          Object config = current.get(game);
          check(
              config != menu && config.getClass().getName().endsWith("ConfigScreen"),
              "config tab opens: " + font + " / " + scale);
          draw(config);
          escape.invoke(config, '\0', Keyboard.KEY_ESCAPE);
          check(current.get(game) == menu, "one Escape returns from Configs");
          draw(menu);
          check(Arrays.equals(before, toolbar(menu)), "Configs preserves every toolbar coordinate");
          clickTab(menu, "hudTab");
          check(current.get(game) == menu, "HUD shortcut keeps settings in Modules");
          method(menu.getClass(), "arrangeHud").invoke(menu);
          Object editor = current.get(game);
          check(editor.getClass().getName().endsWith("HudEditor"), "HUD tab opens editor");
          draw(editor);
          check(
              ((List<?>) field(screenType, "buttonList", "field_146292_n").get(editor)).isEmpty(),
              "Arrange view has no editor controls");
          escape.invoke(editor, '\0', Keyboard.KEY_ESCAPE);
          check(current.get(game) == menu, "one Escape returns from the clean arrange view");
          draw(menu);
          check(Arrays.equals(before, toolbar(menu)), "HUD preserves every toolbar coordinate");
        }
      }
      PanelLayout layout = (PanelLayout) method(menu.getClass(), "model").invoke(menu);
      for (PanelLayout.Panel panel : layout.panels())
        check(panel.width >= 132, "wider category panel: " + panel.category);
      method(menu.getClass(), new Class<?>[] {String.class}, "setQuery").invoke(menu, "clickgui");
      PanelLayout.Panel misc = layout.find(ModuleCategory.MISC);
      float initial =
          (Float)
              method(menu.getClass(), new Class<?>[] {PanelLayout.Panel.class}, "body")
                  .invoke(menu, misc);
      // Enable search so geometry keeps this one-module filter during the native click.
      runtime.modules().setSetting("clickgui", preferences.search, true);
      draw(menu);
      method(menu.getClass(), new Class<?>[] {float.class, float.class, int.class}, "click")
          .invoke(menu, misc.x + 5, misc.y + PanelLayout.HEADER + 5, 1);
      draw(menu);
      float expanded =
          (Float)
              method(menu.getClass(), new Class<?>[] {PanelLayout.Panel.class}, "body")
                  .invoke(menu, misc);
      float height = field(menu.getClass(), "viewHeight").getFloat(menu);
      check(expanded > initial, "right-click settings grow the panel downward");
      check(
          expanded <= height * .64f + .01f
              && misc.y + PanelLayout.HEADER + expanded <= height - 6 + .01f,
          "expanded settings stop at shared cap and screen bottom");
      HudChatProbe.captureWorld(game, "gui-expanded-settings.png");
      escape.invoke(menu, '\0', Keyboard.KEY_ESCAPE);
      check(current.get(game) == null, "Escape exits ClickGUI with a nonempty search");
      runtime.commands().execute("gui", line -> {});
      menu = current.get(game);
      Object tab = field(menu.getClass(), "hudTab").get(menu);
      method(menu.getClass(), new Class<?>[] {float.class, float.class, int.class}, "click")
          .invoke(
              menu,
              field(tab.getClass(), "x").getFloat(tab) + 3,
              field(tab.getClass(), "y").getFloat(tab) + 3,
              1);
      escape.invoke(menu, '\0', Keyboard.KEY_ESCAPE);
      check(current.get(game) == null, "Escape exits ClickGUI with the HUD menu open");

      runtime.modules().setEnabled("hud", true);
      runtime.modules().setEnabled("fullbright", true);
      runtime.modules().setEnabled("sprint", true);
      set(runtime, "suffixes", false);
      set(runtime, "hide-visuals", false);
      set(runtime, "modules", true);
      method(hud.getClass(), new Class<?>[] {screenType}, "openEditor")
          .invoke(hud, new Object[] {null});
      Object editor = current.get(game);
      draw(editor);
      Class<?> element = Class.forName("perihelion.integration.minecraft189.hud.HudModule$Element");
      Object modules = Enum.valueOf((Class) element, "MODULES");
      method(editor.getClass(), new Class<?>[] {element}, "openCustomization")
          .invoke(editor, modules);
      draw(editor);
      Object popup = field(editor.getClass(), "customization").get(editor);
      field(popup.getClass(), "section").setInt(popup, 2);
      field(popup.getClass(), "scroll").setFloat(popup, 10000);
      draw(editor);
      Object toggle = null;
      for (Object control : (List<?>) field(popup.getClass(), "controls").get(popup))
        if (((Setting<?>) field(control.getClass(), "setting").get(control))
            .id()
            .equals("hide-visuals")) toggle = control;
      check(toggle != null, "Hide visuals control is reachable");
      Object preview = field(editor.getClass(), "preview").get(editor);
      check(
          hasRow(preview, "Fullbright") && hasRow(preview, "Sprint"),
          "unfiltered preview contains visual and gameplay modules");
      method(
              editor.getClass(),
              new Class<?>[] {int.class, int.class, int.class},
              "mouseClicked",
              "func_73864_a")
          .invoke(
              editor,
              (int) field(toggle.getClass(), "x").getFloat(toggle) + 4,
              (int) field(toggle.getClass(), "y").getFloat(toggle) + 4,
              0);
      draw(editor);
      check(
          !hasRow(preview, "Fullbright") && hasRow(preview, "Sprint"),
          "Hide visuals immediately removes Render modules from editor Array list");
      Object renderer = field(hud.getClass(), "renderer").get(hud);
      method(
              renderer.getClass(),
              new Class<?>[] {int.class, int.class, boolean.class, element, int.class, int.class},
              "render")
          .invoke(renderer, 427, 240, false, null, 0, 0);
      check(
          !hasRow(renderer, "Fullbright") && hasRow(renderer, "Sprint"),
          "Hide visuals applies to production Array list and preserves gameplay modules");
      HudChatProbe.captureWorld(game, "gui-hide-visuals.png");
      escape.invoke(editor, '\0', Keyboard.KEY_ESCAPE);
      check(current.get(game) == null, "Escape exits HUD directly with Options open");
      System.out.println("[PERIHELION_SMOKE] GUI_REGRESSIONS PASS checks=" + checks);
    } finally {
      display.invoke(game, new Object[] {null});
      for (Map.Entry<Setting<?>, Object> entry : saved.entrySet()) {
        String id =
            runtime
                    .modules()
                    .require("clickgui")
                    .module()
                    .settings()
                    .values()
                    .contains(entry.getKey())
                ? "clickgui"
                : "hud";
        runtime.modules().setSetting(id, (Setting) entry.getKey(), entry.getValue());
      }
      runtime.modules().setEnabled("hud", oldHud);
      runtime.modules().setEnabled("fullbright", oldBright);
      runtime.modules().setEnabled("sprint", oldSprint);
      display.invoke(game, original);
    }
  }

  private static void set(ClientRuntime runtime, String id, Object value) {
    runtime
        .modules()
        .setSetting(
            "hud", (Setting) runtime.modules().require("hud").module().settings().get(id), value);
  }

  private static boolean hasRow(Object renderer, String label) throws Exception {
    for (Object row : (List<?>) field(renderer.getClass(), "modules").get(renderer))
      if (label.equals(field(row.getClass(), "label").get(row))) return true;
    return false;
  }

  private static void draw(Object screen) throws Exception {
    method(
            screen.getClass(),
            new Class<?>[] {int.class, int.class, float.class},
            "drawScreen",
            "func_73863_a")
        .invoke(screen, 0, 0, 0f);
  }

  private static float[] toolbar(Object menu) throws Exception {
    Object hud = field(menu.getClass(), "hudTab").get(menu);
    Object configs = field(menu.getClass(), "configTab").get(menu);
    return new float[] {
      field(hud.getClass(), "x").getFloat(hud),
      field(hud.getClass(), "y").getFloat(hud),
      field(configs.getClass(), "x").getFloat(configs),
      field(configs.getClass(), "y").getFloat(configs),
      field(menu.getClass(), "moduleTabX").getFloat(menu)
    };
  }

  private static void clickTab(Object menu, String id) throws Exception {
    Object tab = field(menu.getClass(), id).get(menu);
    method(menu.getClass(), new Class<?>[] {float.class, float.class, int.class}, "click")
        .invoke(
            menu,
            field(tab.getClass(), "x").getFloat(tab) + 3,
            field(tab.getClass(), "y").getFloat(tab) + 3,
            0);
    method(menu.getClass(), "release").invoke(menu);
  }

  private static void check(boolean pass, String message) {
    checks++;
    if (!pass) throw new IllegalStateException("GUI regression: " + message);
  }
}
