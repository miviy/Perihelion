package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;

import java.util.*;
import org.lwjgl.input.Keyboard;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleCategory;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.gui.ClickGuiModule;
import perihelion.integration.minecraft189.gui.panel.PanelLayout;
import perihelion.integration.minecraft189.gui.widget.SettingControl;
import perihelion.integration.minecraft189.gui.widget.SettingSection;

/** Exercise close/reopen with fresh controls in all three shipped frontends. */
public final class GuiExpansionProbe {
  public static void run(ClientRuntime runtime, Object game) throws Exception {
    ClickGuiModule gui = (ClickGuiModule) runtime.modules().require("clickgui").module();
    Map<Setting<?>, Object> saved = new LinkedHashMap<>();
    for (Setting<?> setting : gui.settings().values()) saved.put(setting, setting.get());
    Object original = field(game.getClass(), "currentScreen", "field_71462_r").get(game);
    int checks = 0;
    try {
      display(game, null);
      runtime.modules().setSetting("clickgui", gui.search, true);
      for (int frontend = 0; frontend < 3; frontend++) {
        runtime
            .modules()
            .setSetting(
                "clickgui",
                gui.visuals,
                frontend == 0 ? ClickGuiModule.Visuals.NEW : ClickGuiModule.Visuals.OLD);
        runtime
            .modules()
            .setSetting(
                "clickgui",
                gui.clientStyle,
                frontend == 2
                    ? ClickGuiModule.ClientStyle.RAVEN
                    : ClickGuiModule.ClientStyle.ADJUST);
        runtime.modules().setSetting("clickgui", gui.panelLayout(), "[]");
        Object screen = open(runtime, game);
        query(screen, "expand each hud element");
        draw(screen);
        PanelLayout layout = (PanelLayout) method(screen.getClass(), "model").invoke(screen);
        PanelLayout.Panel panel = layout.find(ModuleCategory.RENDER);
        ProductionHudProbe.capture("gui-expansion-before-" + frontend + ".png");
        click(screen, panel.x + 8, panel.y + panel.headerHeight + 5);
        draw(screen);
        check(
            field(hudView(screen).getClass(), "expanded").getBoolean(hudView(screen)),
            "Right-click opens the HUD module in frontend "
                + frontend
                + " panel="
                + panel.x
                + ","
                + panel.y
                + " header="
                + panel.headerHeight
                + " query="
                + field(screen.getClass(), "query").get(screen)
                + " matches="
                + method(screen.getClass(), "matchingModules").invoke(screen));
        List<SettingControl> controls = controls(screen);
        SettingSection root = section(controls, "section:Array list");
        SettingSection text = section(controls, "section:Array list/section:Text");
        SettingSection other = section(controls, "section:Logo/section:Text");
        root.press(0, 0, 1);
        text.press(0, 0, 1);
        SettingControl font =
            controls.stream().filter(c -> c.setting.id().equals("modules-font")).findFirst().get();
        Object value = font.setting.get();
        font.press(0, -1, 1);
        check(
            root.isOpen() && text.isOpen() && font.expanded(), "Native clicks expand nested rows");
        checks++;
        method(screen.getClass(), new Class<?>[] {char.class, int.class}, "type")
            .invoke(screen, '\0', Keyboard.KEY_ESCAPE);
        Object reopened = open(runtime, game);
        check(reopened != screen, "Reopen uses a fresh screen");
        query(reopened, "expand each hud element");
        draw(reopened);
        controls = controls(reopened);
        check(
            field(hudView(reopened).getClass(), "expanded").getBoolean(hudView(reopened))
                && section(controls, root.expansionKey()).isOpen(),
            "Module and element stay open in frontend "
                + frontend
                + "; saved="
                + gui.panelLayout().get());
        check(section(controls, text.expansionKey()).isOpen(), "Nested submenu stays open");
        check(
            !section(controls, other.expansionKey()).isOpen(),
            "Matching child labels stay independent");
        SettingControl restored =
            controls.stream().filter(c -> c.setting.id().equals("modules-font")).findFirst().get();
        check(
            restored.expanded() && restored.setting.get().equals(value),
            "Choice stays open without changing value");
        checks += 5;
        ProductionHudProbe.capture("gui-expansion-" + frontend + ".png");
        method(reopened.getClass(), "resetLayout").invoke(reopened);
        check(
            !section(controls, root.expansionKey()).isOpen() && !restored.expanded(),
            "Explicit reset clears disclosures");
        checks++;
        display(game, null);
      }
      System.out.println("[PERIHELION_SMOKE] GUI_EXPANSION PASS checks=" + checks);
    } finally {
      display(game, null);
      for (Map.Entry<Setting<?>, Object> entry : saved.entrySet()) restore(runtime, entry);
      display(game, original);
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void restore(ClientRuntime runtime, Map.Entry<Setting<?>, Object> entry) {
    runtime.modules().setSetting("clickgui", (Setting) entry.getKey(), entry.getValue());
  }

  private static Object open(ClientRuntime runtime, Object game) throws Exception {
    runtime.commands().execute("gui", line -> {});
    Object screen = field(game.getClass(), "currentScreen", "field_71462_r").get(game);
    draw(screen);
    return screen;
  }

  @SuppressWarnings("unchecked")
  private static List<SettingControl> controls(Object screen) throws Exception {
    Object view = hudView(screen);
    return (List<SettingControl>) field(view.getClass(), "controls").get(view);
  }

  private static Object hudView(Object screen) throws Exception {
    Map<?, ?> views = (Map<?, ?>) field(screen.getClass(), "views").get(screen);
    for (Object view : (List<?>) views.get(ModuleCategory.RENDER)) {
      Object entry = field(view.getClass(), "entry").get(view);
      if (((perihelion.core.module.ModuleEntry) entry).info().id().equals("hud")) return view;
    }
    throw new AssertionError("HUD module missing");
  }

  private static SettingSection section(List<SettingControl> controls, String key) {
    return (SettingSection)
        controls.stream()
            .filter(c -> c instanceof SettingSection && c.expansionKey().equals(key))
            .findFirst()
            .get();
  }

  private static void query(Object screen, String query) throws Exception {
    method(screen.getClass(), new Class<?>[] {String.class}, "setQuery").invoke(screen, query);
  }

  private static void click(Object screen, float x, float y) throws Exception {
    method(screen.getClass(), new Class<?>[] {float.class, float.class, int.class}, "click")
        .invoke(screen, x, y, 1);
  }

  private static void draw(Object screen) throws Exception {
    method(NewVisualsProbe.class, new Class<?>[] {Object.class}, "draw").invoke(null, screen);
  }

  private static void display(Object game, Object screen) throws Exception {
    method(
            game.getClass(),
            new Class<?>[] {Class.forName("net.minecraft.client.gui.GuiScreen")},
            "displayGuiScreen",
            "func_147108_a")
        .invoke(game, screen);
  }

  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
