package perihelion.smoke;

import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.*;
import javax.imageio.ImageIO;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.*;
import perihelion.core.context.ConnectionContext;
import perihelion.core.diagnostics.ClientLog;
import perihelion.core.input.Keybind;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.*;
import perihelion.core.setting.*;
import perihelion.integration.minecraft189.gui.*;
import perihelion.integration.minecraft189.gui.font.GuiFonts;
import perihelion.integration.minecraft189.gui.panel.PanelLayout;
import perihelion.integration.minecraft189.gui.widget.*;
import perihelion.integration.minecraft189.render.RenderState;

/** One bounded behavior probe. Reflection is only for the test's MCP/SRG compatibility. */
public final class GuiHookProbe {
  private static int checks;

  private enum Choice {
    FIRST,
    SECOND
  }

  private static final class Fixture extends ClientModule {
    final Setting<Boolean> gate, conditional;
    final Setting<Integer> number;
    final Setting<Double> decimal;
    final Setting<Choice> choice;
    final Setting<DoubleRange> range;
    final Setting<ColorValue> color;
    final Setting<Set<Choice>> multi;
    final Setting<String> text;

    Fixture(Runnable guard) {
      super(
          new ModuleInfo(
              "gui_probe",
              "Fixture module",
              "Search needle for GUI smoke",
              ModuleCategory.COMBAT,
              false));
      gate =
          setting(
              Setting.bool(
                  "gate", "Conditional gate", "Show the conditional option", false, guard));
      number = setting(Setting.integer("number", "Number", "Integer", 2, 1, 10, guard));
      decimal = setting(Setting.decimal("decimal", "Decimal", "Fraction", 0.5, 0, 1, 0.1, guard));
      choice =
          setting(
              Setting.enumeration("choice", "Choice", "Mode", Choice.FIRST, Choice.class, guard));
      range =
          setting(
              Setting.range("range", "Range", "Endpoints", new DoubleRange(2, 8), 0, 10, 1, guard));
      color =
          setting(
              Setting.color("color", "Color", "RGBA", ColorValue.rgba(120, 140, 160, 180), guard));
      multi =
          setting(
              Setting.multiSelect(
                  "multi",
                  "Multi",
                  "Multiple choices",
                  EnumSet.of(Choice.FIRST),
                  Choice.class,
                  guard));
      text = setting(Setting.string("text", "Text", "String", "example", 100, guard));
      conditional =
          setting(
              Setting.bool("conditional", "Conditional", "Only when gate is on", false, guard)
                  .visibleWhen(gate::get));
    }
  }

  public static void run(ClientRuntime production) throws Exception {
    Object game =
        method(
                Class.forName("net.minecraft.client.Minecraft"),
                new Class<?>[0],
                "getMinecraft",
                "func_71410_x")
            .invoke(null);
    Field screenField = field(game.getClass(), "currentScreen", "field_71462_r");
    Method display =
        method(
            game.getClass(),
            new Class<?>[] {Class.forName("net.minecraft.client.gui.GuiScreen")},
            "displayGuiScreen",
            "func_147108_a");
    Object original = screenField.get(game);
    production.commands().execute("gui", line -> {});
    Object main = screenField.get(game);
    check(
        main != null && main.getClass().getName().endsWith("PanelScreen"),
        "production GUI command opens panel frontend");
    draw(main);
    float tabWidth = field(main.getClass(), "moduleTabWidth").getFloat(main);
    Object hudTab = field(main.getClass(), "hudTab").get(main);
    Object configTab = field(main.getClass(), "configTab").get(main);
    float hudX = field(hudTab.getClass(), "x").getFloat(hudTab);
    float configX = field(configTab.getClass(), "x").getFloat(configTab);
    float modulesX = field(main.getClass(), "moduleTabX").getFloat(main);
    check(
        field(main.getClass(), "hudTabWidth").getFloat(main) == tabWidth
            && field(main.getClass(), "configTabWidth").getFloat(main) == tabWidth,
        "toolbar tabs share one width");
    check(
        Math.abs(modulesX - hudX - tabWidth - 4) < .01f
            && Math.abs(configX - modulesX - tabWidth - 4) < .01f,
        "default toolbar gaps are equal");
    check(
        field(hudTab.getClass(), "y").getFloat(hudTab) == 7
            && field(configTab.getClass(), "y").getFloat(configTab) == 7,
        "docked toolbar tabs share the search field's top inset");
    check(
        field(main.getClass(), "TAB_HEIGHT").getFloat(null) == 16,
        "toolbar height matches the search field");
    cleanDraw(main);
    capture("clickgui.png");
    ClickGuiModule productionGui =
        (ClickGuiModule) production.modules().require("clickgui").module();
    boolean savedCustomText = productionGui.customTextStyle.get();
    production.modules().setSetting("clickgui", productionGui.customTextStyle, true);
    PanelLayout productionLayout = (PanelLayout) main.getClass().getMethod("model").invoke(main);
    check(productionLayout.panels().size() == 7, "seven production categories without Exploits");
    for (String id :
        new String[] {
          "breaker",
          "beddefender",
          "blockin",
          "clutch",
          "scaffold",
          "chestaura",
          "fastbreak",
          "fastplace"
        })
      check(
          production.modules().require(id).info().category() == ModuleCategory.WORLD,
          id + " belongs with the World block tools");
    Object categoryViews = field(main.getClass(), "views").get(main);
    for (java.util.List<?> rows : ((java.util.Map<?, java.util.List<?>>) categoryViews).values()) {
      String previousLabel = "";
      for (Object row : rows) {
        String nextLabel = (String) field(row.getClass(), "label").get(row);
        check(
            String.CASE_INSENSITIVE_ORDER.compare(previousLabel, nextLabel) <= 0,
            "production module rows are alphabetical within their category");
        previousLabel = nextLabel;
      }
    }
    PanelLayout.Panel clientPanel = productionLayout.find(ModuleCategory.MISC);
    clientPanel.x = 24;
    clientPanel.y = 34;
    productionLayout.front(clientPanel);
    float rowOffset = 0;
    for (Object row :
        ((java.util.Map<?, java.util.List<?>>) categoryViews).get(ModuleCategory.MISC)) {
      ModuleEntry entry = (ModuleEntry) field(row.getClass(), "entry").get(row);
      if (entry.info().id().equals("clickgui")) break;
      rowOffset += (Float) method(row.getClass(), new Class<?>[0], "height").invoke(row);
    }
    clientPanel.scroll = rowOffset;
    click(main, 32, clientPanel.y + PanelLayout.HEADER + 4, 1);
    cleanDraw(main);
    capture("clickgui-settings.png");
    click(
        main,
        clientPanel.x + 15,
        field(findControl(main, "clickgui", "accent").getClass(), "y")
                .getFloat(findControl(main, "clickgui", "accent"))
            + 4,
        1);
    check(
        field(main.getClass(), "editor").get(main) != null
            && field(main.getClass(), "editorPanel").get(main) == clientPanel,
        "color value opens in its panel row");
    cleanDraw(main);
    capture("clickgui-inline-hex.png");
    float textX = field(main.getClass(), "editX").getFloat(main),
        textY = field(main.getClass(), "editY").getFloat(main);
    click(main, textX + 1, textY + 8, 0);
    Object inline = field(main.getClass(), "editor").get(main);
    check(
        ((Number)
                    method(inline.getClass(), new Class<?>[0], "getCursorPosition", "func_146198_h")
                        .invoke(inline))
                .intValue()
            == 0,
        "themed inline click positions the caret at the start");
    invoke(main, "type", new Class<?>[] {char.class, int.class}, '\0', Keyboard.KEY_ESCAPE);
    check(field(main.getClass(), "editor").get(main) == null, "Escape cancels inline color entry");

    double savedOpacity = productionGui.panelOpacity.get();
    production.modules().setSetting("clickgui", productionGui.panelOpacity, 0.6);
    cleanDraw(main);
    capture("clickgui-settings-translucent.png");
    double savedScale = productionGui.scale.get();
    production.modules().setSetting("clickgui", productionGui.scale, 1.05);
    cleanDraw(main);
    capture("clickgui-fractional-scale.png");
    click(
        main,
        clientPanel.x + 15,
        field(findControl(main, "clickgui", "accent").getClass(), "y")
                .getFloat(findControl(main, "clickgui", "accent"))
            + 4,
        1);
    check(
        field(main.getClass(), "editor").get(main) != null,
        "themed inline editor opens at fractional menu scale");
    cleanDraw(main);
    capture("clickgui-inline-hex-fractional.png");
    invoke(main, "type", new Class<?>[] {char.class, int.class}, '\0', Keyboard.KEY_ESCAPE);
    List<PanelLayout.Panel> picturePanels = productionLayout.panels();
    float[] originalX = new float[picturePanels.size()];
    for (int i = 0; i < picturePanels.size(); i++) {
      PanelLayout.Panel panel = picturePanels.get(i);
      originalX[i] = panel.x;
      if (panel != clientPanel) panel.x = 10000;
    }
    cleanDraw(main);
    capture("clickgui-settings-isolated.png");
    for (int i = 0; i < picturePanels.size(); i++) picturePanels.get(i).x = originalX[i];
    production.modules().setSetting("clickgui", productionGui.scale, savedScale);
    production.modules().setSetting("clickgui", productionGui.panelOpacity, savedOpacity);
    combatSettings(production, main, productionLayout);
    configs(production, main, productionGui);
    utilityControls(production, main, productionGui);
    production.modules().setSetting("clickgui", productionGui.customTextStyle, savedCustomText);
    readableControls(production, main, productionGui);
    stablePanelBounds(production, main, productionGui);
    display.invoke(game, original);
    final ClickGuiService[] gui = {null};
    final Fixture[] fixture = {null};
    ClientRuntime runtime =
        new ClientRuntime(
            Paths.get(System.getProperty("perihelion.smoke.output"), "gui-fixture.json"),
            ClientLog.NONE,
            r -> {
              fixture[0] = new Fixture(r.tasks()::checkThread);
              r.modules().register(fixture[0]);
              gui[0] = new ClickGuiService(r);
            });
    try {
      runtime.start();
      runtime
          .modules()
          .setSetting("clickgui", gui[0].preferences().visuals, ClickGuiModule.Visuals.OLD);
      runtime.updateWorld(
          true, new ConnectionContext(ConnectionContext.Kind.SINGLEPLAYER, ""), true);
      Keybind opener = runtime.modules().require("clickgui").binding();
      check(
          opener.equals(new Keybind(Keybind.Device.KEYBOARD, Keyboard.KEY_RSHIFT)),
          "GUI uses the existing binding manager with Right Shift default");
      runtime.input().setAllowed(true, Collections.emptyList());
      runtime.input().accept(opener, true);
      Object screen = screenField.get(game);
      check(
          screen != null && !runtime.input().isAllowed(),
          "bound opener opens GUI and blocks gameplay input");
      PanelLayout layout = (PanelLayout) screen.getClass().getMethod("model").invoke(screen);
      check(
          layout.panels().size() == ModuleCategory.values().length,
          "every core category has a panel");
      check(layout.panels().size() == 7, "seven categories without an Exploits panel");
      Method snapshot = RenderHookProbe.class.getDeclaredMethod("readState");
      snapshot.setAccessible(true);
      Object before = snapshot.invoke(null);
      draw(screen);
      check(
          before.equals(snapshot.invoke(null)),
          "GUI restores GL state, matrices, texture bindings and scissor");
      for (ClickGuiModule.Font font : ClickGuiModule.Font.values()) {
        runtime.modules().setSetting("clickgui", gui[0].preferences().font, font);
        draw(screen);
      }
      check(
          GL11.glGetError() == GL11.GL_NO_ERROR,
          "all cached font choices render without GL errors");
      for (double scale : new double[] {0.75, 1.5, 1.0}) {
        runtime.modules().setSetting("clickgui", gui[0].preferences().scale, scale);
        draw(screen);
      }
      check(
          before.equals(snapshot.invoke(null)),
          "GUI scale changes preserve render state and recover layout");
      PanelLayout.Panel p = layout.find(ModuleCategory.COMBAT);
      p.x = 12;
      p.y = 60;
      layout.front(p);
      draw(screen);
      click(screen, 20, 83, 0);
      check(
          runtime.modules().require("gui_probe").state() == ModuleState.ENABLED,
          "module row toggles through manager");
      cleanDraw(screen);
      capture("clickgui-enabled-row.png");
      click(screen, 20, 83, 1);
      draw(screen);
      check(!fixture[0].conditional.isVisible(), "conditional setting starts hidden");
      click(screen, 22, 107, 0);
      draw(screen);
      check(fixture[0].conditional.isVisible(), "boolean editor updates conditional visibility");
      float numberY =
          field(findControl(screen, "gui_probe", "number").getClass(), "y")
                  .getFloat(findControl(screen, "gui_probe", "number"))
              + 4;
      click(screen, 80, numberY, 0);
      invoke(screen, "drag", new Class<?>[] {float.class, float.class}, -100f, 133f);
      invoke(screen, "release", new Class<?>[0]);
      check(
          fixture[0].number.get() == 1,
          "numeric slider captures drag outside its bounds and clamps to minimum");
      click(screen, 80, numberY, 1);
      invoke(screen, "type", new Class<?>[] {char.class, int.class}, '\0', Keyboard.KEY_LSHIFT);
      invoke(screen, "type", new Class<?>[] {char.class, int.class}, '7', Keyboard.KEY_7);
      invoke(screen, "type", new Class<?>[] {char.class, int.class}, '\r', Keyboard.KEY_RETURN);
      check(
          fixture[0].number.get() == 7 && field(screen.getClass(), "editor").get(screen) == null,
          "inline number entry applies without a popup");
      click(screen, 80, numberY, 1);
      invoke(screen, "type", new Class<?>[] {char.class, int.class}, 'x', Keyboard.KEY_X);
      invoke(screen, "type", new Class<?>[] {char.class, int.class}, '\r', Keyboard.KEY_RETURN);
      check(
          fixture[0].number.get() == 7 && field(screen.getClass(), "editor").get(screen) != null,
          "invalid inline entry keeps the editor and setting unchanged");
      invoke(screen, "type", new Class<?>[] {char.class, int.class}, '\0', Keyboard.KEY_ESCAPE);
      check(
          field(screen.getClass(), "editor").get(screen) == null,
          "Escape cancels inline numeric entry without closing GUI");
      click(screen, 80, numberY, 1);
      click(screen, 20, 83, 0);
      check(
          field(screen.getClass(), "editor").get(screen) == null
              && runtime.modules().require("gui_probe").state() == ModuleState.ENABLED,
          "outside click cancels inline editing without toggling a module");
      // Force real screen-edge overflow even when this small fixture fits the shared cap.
      float scrollY = p.y;
      p.y = field(screen.getClass(), "viewHeight").getFloat(screen) - 110;
      draw(screen);
      invoke(
          screen,
          "scroll",
          new Class<?>[] {float.class, float.class, float.class},
          30f,
          p.y + PanelLayout.HEADER + 4,
          1000f);
      check(p.scroll > 0, "expanded settings scroll inside panel");
      check(layout.find(ModuleCategory.MISC).scroll == 0, "wheel does not scroll another panel");
      p.y = scrollY;
      click(screen, 30, 65, 1);
      check(p.collapsed, "right header collapses panel");
      click(screen, 30, 65, 0);
      invoke(screen, "drag", new Class<?>[] {float.class, float.class}, 100f, 90f);
      invoke(screen, "release", new Class<?>[0]);
      check(p.x == 82 && p.y == 85, "collapsed panel can be dragged");
      click(screen, 95, 90, 1);
      check(!p.collapsed, "right header expands panel");
      p.scroll = 0;
      // Use a populated panel: empty categories are intentionally hidden.
      PanelLayout.Panel cover = layout.find(ModuleCategory.MISC);
      cover.x = p.x;
      cover.y = p.y;
      layout.front(cover);
      draw(screen);
      click(screen, p.x + 8, p.y + 7, 1);
      check(
          cover.collapsed && !p.collapsed,
          "overlapping top panel consumes hit instead of underlying header");
      layout.front(p);
      draw(screen);
      click(screen, p.x + 8, p.y + 24, 2);
      invoke(screen, "type", new Class<?>[] {char.class, int.class}, 'k', Keyboard.KEY_K);
      Keybind key = new Keybind(Keybind.Device.KEYBOARD, Keyboard.KEY_K);
      check(
          runtime.modules().require("gui_probe").binding().equals(key),
          "keybind editor assigns requested key");
      runtime.input().accept(key, true);
      check(
          runtime.modules().require("gui_probe").state() == ModuleState.ENABLED,
          "bind capture and GUI typing cannot trigger modules");
      runtime.modules().setSetting("clickgui", gui[0].preferences().search, true);
      draw(screen);
      invoke(screen, "setQuery", new Class<?>[] {String.class}, "needle");
      check(
          ((Number) screen.getClass().getMethod("matchingModules").invoke(screen)).intValue() == 1,
          "search matches description");
      runtime.modules().setSetting("clickgui", gui[0].preferences().search, false);
      draw(screen);
      check(
          ((Number) screen.getClass().getMethod("matchingModules").invoke(screen)).intValue()
              == runtime.modules().entries().size(),
          "disabling search clears filtering");
      runtime.modules().setSetting("clickgui", gui[0].preferences().tooltips, false);
      draw(screen);
      check(!gui[0].preferences().tooltips.get(), "tooltips can be disabled while the GUI is open");
      runtime.modules().quarantine("gui_probe", new IllegalStateException("deliberate GUI fault"));
      draw(screen);
      check(
          runtime.modules().require("gui_probe").state() == ModuleState.FAULTED,
          "fault indicator renders without clearing quarantine");
      editors(runtime, fixture[0]);
      p.x = -1000;
      p.y = 9000;
      // Native resize callback exercises recovery without generating another window.
      method(screen.getClass(), new Class<?>[0], "initGui", "func_73866_w_").invoke(screen);
      check(
          p.x >= 0
              && p.x + p.width <= field(screen.getClass(), "viewWidth").getFloat(screen)
              && p.y + PanelLayout.HEADER
                  <= field(screen.getClass(), "viewHeight").getFloat(screen),
          "resize recovers off-screen header at the current viewport size");
      invoke(screen, "resetLayout", new Class<?>[0]);
      for (PanelLayout.Panel panel : layout.panels())
        check(!panel.collapsed, "reset restores " + panel.category);
      check(!gui[0].preferences().search.get(), "clean layout has no search bar unless enabled");
      String saved = gui[0].preferences().layout.get();
      runtime
          .modules()
          .setSetting(
              "clickgui",
              gui[0].preferences().accent,
              ColorValue.rgb(20, 80, 120).withRainbow(true, 2));
      invoke(screen, "type", new Class<?>[] {char.class, int.class}, '\0', Keyboard.KEY_ESCAPE);
      check(screenField.get(game) == null, "Escape closes normally");
      ClickGuiModule.Font persistedFont = gui[0].preferences().font.get();
      runtime.stop();
      final ClickGuiService[] reloaded = {null};
      ClientRuntime next =
          new ClientRuntime(
              Paths.get(System.getProperty("perihelion.smoke.output"), "gui-fixture.json"),
              ClientLog.NONE,
              r -> {
                r.modules().register(new Fixture(r.tasks()::checkThread));
                reloaded[0] = new ClickGuiService(r);
              });
      try {
        next.start();
        check(
            reloaded[0].preferences().layout.get().equals(saved),
            "layout persists through the existing config store");
        check(
            !reloaded[0].preferences().search.get()
                && !reloaded[0].preferences().tooltips.get()
                && reloaded[0].preferences().font.get() == persistedFont,
            "GUI preferences persist");
        check(
            reloaded[0].preferences().accent.get().rainbow()
                && reloaded[0].preferences().accent.get().rainbowSpeed() == 2,
            "rainbow color and speed persist");
      } finally {
        next.stop();
      }
      System.out.println(
          "[PERIHELION_SMOKE] GUI PASS checks="
              + checks
              + " panels="
              + ModuleCategory.values().length
              + " fonts="
              + ClickGuiModule.Font.values().length);
    } finally {
      runtime.stop();
      display.invoke(game, original);
    }
  }

  private static SettingControl findControl(Object screen, String module, String setting)
      throws Exception {
    Map<?, ?> views = (Map<?, ?>) field(screen.getClass(), "views").get(screen);
    for (Object rows : views.values())
      for (Object view : (List<?>) rows) {
        ModuleEntry entry = (ModuleEntry) field(view.getClass(), "entry").get(view);
        if (!entry.info().id().equals(module)) continue;
        for (Object item : (List<?>) field(view.getClass(), "controls").get(view)) {
          SettingControl control = (SettingControl) item;
          if (control.setting.id().equals(setting)) return control;
        }
      }
    throw new AssertionError("Missing control: " + module + "/" + setting);
  }

  private static void stablePanelBounds(
      ClientRuntime runtime, Object screen, ClickGuiModule preferences) throws Exception {
    Map<?, ?> views = (Map<?, ?>) field(screen.getClass(), "views").get(screen);
    Map<Object, Boolean> expanded = new IdentityHashMap<>();
    for (Object rows : views.values())
      for (Object view : (List<?>) rows) {
        expanded.put(view, field(view.getClass(), "expanded").getBoolean(view));
        field(view.getClass(), "expanded").setBoolean(view, false);
      }
    ClickGuiModule.Font savedFont = preferences.font.get();
    double savedScale = preferences.scale.get();
    try {
      for (ClickGuiModule.Font font : ClickGuiModule.Font.values()) {
        runtime.modules().setSetting("clickgui", preferences.font, font);
        for (double requestedScale : new double[] {0.75, 1.5}) {
          runtime.modules().setSetting("clickgui", preferences.scale, requestedScale);
          cleanDraw(screen);
          PanelLayout layout = (PanelLayout) invoke(screen, "model", new Class<?>[0]);
          float scale = field(screen.getClass(), "scale").getFloat(screen);
          Map<PanelLayout.Panel, float[]> bounds = new IdentityHashMap<>();
          for (PanelLayout.Panel panel : layout.panels())
            bounds.put(panel, new float[] {panel.x, panel.y, panel.width});
          for (Object view : expanded.keySet())
            field(view.getClass(), "expanded").setBoolean(view, true);
          cleanDraw(screen);
          for (PanelLayout.Panel panel : layout.panels()) {
            float[] saved = bounds.get(panel);
            check(
                panel.x == saved[0] && panel.y == saved[1] && panel.width == saved[2],
                "opening every module preserves panel bounds: " + panel.category + " / " + font);
          }
          check(
              scale == field(screen.getClass(), "scale").getFloat(screen),
              "opening every module preserves menu scale: " + font + " / " + requestedScale);
          for (Object view : expanded.keySet())
            field(view.getClass(), "expanded").setBoolean(view, false);
        }
      }
    } finally {
      for (Map.Entry<Object, Boolean> saved : expanded.entrySet())
        field(saved.getKey().getClass(), "expanded").setBoolean(saved.getKey(), saved.getValue());
      runtime.modules().setSetting("clickgui", preferences.font, savedFont);
      runtime.modules().setSetting("clickgui", preferences.scale, savedScale);
      cleanDraw(screen);
    }
  }

  private static void readableControls(
      ClientRuntime runtime, Object screen, ClickGuiModule preferences) throws Exception {
    ClickGuiModule.Font savedFont = preferences.font.get();
    boolean savedSearch = preferences.search.get();
    String savedLayout = preferences.layout.get();
    try {
      runtime.modules().setSetting("clickgui", preferences.search, true);
      invoke(screen, "setQuery", new Class<?>[] {String.class}, "blockesp");
      PanelLayout layout = (PanelLayout) invoke(screen, "model", new Class<?>[0]);
      PanelLayout.Panel panel = layout.find(ModuleCategory.RENDER);
      panel.x = 24;
      panel.y = 34;
      panel.scroll = 0;
      layout.front(panel);
      cleanDraw(screen);
      click(screen, panel.x + 8, panel.y + PanelLayout.HEADER + 4, 1);
      for (ClickGuiModule.Font font :
          new ClickGuiModule.Font[] {
            ClickGuiModule.Font.SANS, ClickGuiModule.Font.MINECRAFT, ClickGuiModule.Font.MONO
          }) {
        runtime.modules().setSetting("clickgui", preferences.font, font);
        cleanDraw(screen);
        UiCanvas canvas = (UiCanvas) field(screen.getClass(), "canvas").get(screen);
        Map<?, ?> views = (Map<?, ?>) field(screen.getClass(), "views").get(screen);
        boolean foundEnder = false, foundWalls = false;
        for (Object view : (List<?>) views.get(ModuleCategory.RENDER)) {
          ModuleEntry entry = (ModuleEntry) field(view.getClass(), "entry").get(view);
          if (!entry.info().id().equals("blockesp")) continue;
          for (Object item : (List<?>) field(view.getClass(), "controls").get(view)) {
            SettingControl control = (SettingControl) item;
            if (control.setting.metadata().kind() != SettingMetadata.Kind.BOOLEAN) continue;
            String label = (String) field(control.getClass(), "label").get(control);
            List<String> lines =
                (List<String>) field(control.getClass(), "labelLines").get(control);
            check(
                String.join("", lines).equals(label),
                "complete BlockESP label: " + label + " / " + font);
            for (String line : lines)
              check(
                  canvas.font.width(line) <= panel.width - 11 - 6,
                  "BlockESP label stays inside the highlighted row: " + label + " / " + font);
            foundEnder |= control.setting.id().equals("ender-chests");
            foundWalls |= control.setting.id().equals("through-walls");
          }
        }
        check(foundEnder && foundWalls, "screenshot regression settings are present");
        capture("clickgui-readable-" + font.name().toLowerCase(Locale.ROOT) + ".png");
      }
    } finally {
      runtime.modules().setSetting("clickgui", preferences.font, savedFont);
      runtime.modules().setSetting("clickgui", preferences.search, savedSearch);
      runtime.modules().setSetting("clickgui", preferences.layout, savedLayout);
      cleanDraw(screen);
    }
  }

  private static void combatSettings(ClientRuntime runtime, Object screen, PanelLayout layout)
      throws Exception {
    ClickGuiModule gui = (ClickGuiModule) runtime.modules().require("clickgui").module();
    boolean oldSearch = gui.search.get();
    runtime.modules().setSetting("clickgui", gui.search, true);
    draw(screen);
    runtime.commands().execute("set velocity mode legit", line -> {});
    invoke(screen, "setQuery", new Class<?>[] {String.class}, "velocity reduce ticks");
    check(
        ((Number) screen.getClass().getMethod("matchingModules").invoke(screen)).intValue() == 1,
        "search finds a module by multiple words from its settings");
    Object search = field(screen.getClass(), "searchField").get(screen);
    method(search.getClass(), new Class<?>[] {String.class}, "setText", "func_146180_a")
        .invoke(search, "velocity reduce ticks");
    PanelLayout.Panel panel = layout.find(ModuleCategory.COMBAT);
    float oldX = panel.x, oldY = panel.y;
    panel.x = 24;
    panel.y = 34;
    layout.front(panel);
    draw(screen);
    click(screen, panel.x + 8, panel.y + PanelLayout.HEADER + 4, 1);
    cleanDraw(screen);
    capture("clickgui-velocity-flow.png");
    Map<?, ?> views = (Map<?, ?>) field(screen.getClass(), "views").get(screen);
    Object slider = null;
    for (Object view : (List<?>) views.get(ModuleCategory.COMBAT)) {
      ModuleEntry entry = (ModuleEntry) field(view.getClass(), "entry").get(view);
      if (!entry.info().id().equals("velocity")) continue;
      for (Object control : (List<?>) field(view.getClass(), "controls").get(view)) {
        Setting<?> setting = (Setting<?>) field(control.getClass(), "setting").get(control);
        if (setting.id().equals("reduce-ticks")) slider = control;
      }
    }
    check(slider != null, "Velocity reduction tick editor is present");
    // Compact rows can put this setting below the viewport; reach it through the panel scroll.
    panel.scroll +=
        field(slider.getClass(), "y").getFloat(slider) - panel.y - PanelLayout.HEADER - 40;
    cleanDraw(screen);
    click(
        screen,
        field(slider.getClass(), "x").getFloat(slider) + 5,
        field(slider.getClass(), "y").getFloat(slider) + 5,
        1);
    check(
        field(screen.getClass(), "editor").get(screen) != null,
        "Velocity numeric setting opens its inline editor");
    runtime.commands().execute("set velocity mode custom", line -> {});
    cleanDraw(screen);
    check(
        field(screen.getClass(), "editor").get(screen) == null,
        "conditional editor retires when its parent setting hides it");
    runtime.commands().execute("set velocity mode legit", line -> {});
    invoke(screen, "setQuery", new Class<?>[] {String.class}, "");
    check(
        ((Number) screen.getClass().getMethod("matchingModules").invoke(screen)).intValue()
            == runtime.modules().entries().size(),
        "clearing the filter restores every module");
    Object game =
        method(
                Class.forName("net.minecraft.client.Minecraft"),
                new Class<?>[0],
                "getMinecraft",
                "func_71410_x")
            .invoke(null);
    check(
        field(game.getClass(), "currentScreen", "field_71462_r").get(game) == screen,
        "explicitly clearing a search keeps the GUI open");
    invoke(screen, "setQuery", new Class<?>[] {String.class}, "jumpreset nearby player");
    check(
        ((Number) screen.getClass().getMethod("matchingModules").invoke(screen)).intValue() == 1,
        "standalone Jump Reset is discoverable through its name and settings");
    invoke(screen, "setQuery", new Class<?>[] {String.class}, "no-such-module-zzzz");
    cleanDraw(screen);
    check(
        ((Number) screen.getClass().getMethod("matchingModules").invoke(screen)).intValue() == 0,
        "empty search results render without fault");
    invoke(screen, "setQuery", new Class<?>[] {String.class}, "");
    runtime.commands().execute("set velocity mode cancel", line -> {});
    panel.x = oldX;
    panel.y = oldY;
    runtime.modules().setSetting("clickgui", gui.search, oldSearch);
  }

  private static void configs(ClientRuntime runtime, Object parent, ClickGuiModule preferences)
      throws Exception {
    Object screen = parent;
    final java.util.concurrent.atomic.AtomicInteger packets =
        new java.util.concurrent.atomic.AtomicInteger();
    perihelion.core.event.EventOwner owner =
        new perihelion.core.event.EventOwner(
            "config-chat-smoke",
            failure -> {
              throw new AssertionError(failure);
            });
    runtime
        .networkEvents()
        .subscribe(
            owner,
            perihelion.integration.minecraft189.event.PacketSendEvent.class,
            event -> packets.incrementAndGet());
    owner.activate();
    try {
      Method send =
          method(
              Class.forName("net.minecraft.client.gui.GuiScreen"),
              new Class<?>[] {String.class, boolean.class},
              "sendChatMessage",
              "func_175281_b");
      Object game =
          method(
                  Class.forName("net.minecraft.client.Minecraft"),
                  new Class<?>[0],
                  "getMinecraft",
                  "func_71410_x")
              .invoke(null);
      Class<?> chatType = Class.forName("net.minecraft.client.gui.GuiChat");
      Object chat = chatType.getConstructor(String.class).newInstance(".con");
      method(
              chatType,
              new Class<?>[] {game.getClass(), int.class, int.class},
              "setWorldAndResolution",
              "func_146280_a")
          .invoke(chat, game, 427, 240);
      method(chatType, new Class<?>[0], "autocompletePlayerNames", "func_146404_p_").invoke(chat);
      Object input = field(chatType, "inputField", "field_146415_a").get(chat);
      check(
          ".config"
              .equals(
                  method(input.getClass(), new Class<?>[0], "getText", "func_146179_b")
                      .invoke(input)),
          "dot command Tab completion uses local command registry");
      check(packets.get() == 0, "dot Tab completion never requests suggestions from the server");
      send.invoke(screen, ".status", true);
      send.invoke(screen, ".not-a-command", true);
      check(packets.get() == 0, "transformed dot commands and typos never emit server packets");
      cleanDraw(screen);
      float width = field(screen.getClass(), "viewWidth").getFloat(screen);
      click(screen, width - 40, 14, 0);
      screen = field(game.getClass(), "currentScreen", "field_71462_r").get(game);
      check(
          screen != parent && screen.getClass().getName().endsWith("ConfigScreen"),
          "Configs opens its own screen without rendering module panels");
      cleanDraw(screen);
      pumpConfigs(runtime, screen);
      cleanDraw(screen);
      Object browser = field(screen.getClass(), "configs").get(screen);
      java.util.List<?> bundled =
          (java.util.List<?>) field(browser.getClass(), "profiles").get(browser);
      check(
          bundled.contains("default") && bundled.contains("hypxile"),
          "bundled default and hypxile appear in the ordinary config browser");
      check(
          runtime
                  .config()
                  .store()
                  .readProfile("hypxile")
                  .get(3, java.util.concurrent.TimeUnit.SECONDS)
                  .json()
                  .getAsJsonObject("modules")
                  .getAsJsonObject("aura")
                  .getAsJsonObject("settings")
                  .get("min-cps")
                  .getAsDouble()
              == 18.8,
          "packaged hypxile resource is installed with the converted settings");
      click(
          screen,
          field(browser.getClass(), "fieldX").getFloat(browser) + 3,
          field(browser.getClass(), "fieldY").getFloat(browser) + 5,
          0);
      check(
          (Boolean) method(browser.getClass(), new Class<?>[0], "focused").invoke(browser),
          "preset name owns keyboard input only when focused");
      method(browser.getClass(), new Class<?>[0], "blur").invoke(browser);
      check(
          !(Boolean) method(browser.getClass(), new Class<?>[0], "focused").invoke(browser)
              && field(game.getClass(), "currentScreen", "field_71462_r").get(game) == screen,
          "blurring preset input keeps its screen open");
      Object name = field(browser.getClass(), "name").get(browser);
      method(name.getClass(), new Class<?>[] {String.class}, "setText", "func_146180_a")
          .invoke(name, "smoke-preset");
      cleanDraw(screen);
      configButton(screen, browser, "save");
      pumpConfigs(runtime, screen);
      cleanDraw(screen);
      java.nio.file.Path file = runtime.config().store().profilePath("smoke-preset");
      check(java.nio.file.Files.isRegularFile(file), "GUI creates a shareable JSON preset");
      check(
          ((java.util.List<?>) field(browser.getClass(), "profiles").get(browser))
              .contains("smoke-preset"),
          "new preset appears in the GUI list");
      double configScale = preferences.scale.get();
      for (double scale : new double[] {0.75, 1.05, 1.5}) {
        runtime.modules().setSetting("clickgui", preferences.scale, scale);
        cleanDraw(screen);
        float viewHeight = field(screen.getClass(), "viewHeight").getFloat(screen);
        for (Object button : (java.util.List<?>) field(browser.getClass(), "buttons").get(browser))
          check(
              field(button.getClass(), "y").getFloat(button)
                      + field(button.getClass(), "h").getFloat(button)
                  <= viewHeight,
              "config controls remain on-screen at scale " + scale);
        check(
            field(browser.getClass(), "listHeight").getFloat(browser) >= 4 * 16,
            "config list keeps at least four visible rows in a small window");
      }
      runtime.modules().setSetting("clickgui", preferences.scale, configScale);
      cleanDraw(screen);
      capture("clickgui-configs.png");
      double saved = preferences.background.get();
      runtime.modules().setSetting("clickgui", preferences.background, saved == 0.2 ? 0.4 : 0.2);
      configButton(screen, browser, "load");
      pumpConfigs(runtime, screen);
      cleanDraw(screen);
      check(preferences.background.get() == saved, "GUI load restores actual settings");
      double updated = saved == 0.3 ? 0.5 : 0.3;
      runtime.modules().setSetting("clickgui", preferences.background, updated);
      configButton(screen, browser, "update");
      cleanDraw(screen);
      configButton(screen, browser, "update");
      pumpConfigs(runtime, screen);
      cleanDraw(screen);
      check(
          java.nio.file.Files.isRegularFile(file.resolveSibling("smoke-preset.json.bak")),
          "GUI update retains previous preset backup");
      runtime.modules().setSetting("clickgui", preferences.background, saved);
      send.invoke(screen, ".config load smoke-preset", true);
      long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(4);
      while (runtime.config().isReloading() && System.nanoTime() < deadline) {
        runtime.tasks().drain();
        Thread.sleep(1);
      }
      check(preferences.background.get() == updated, "dot config load applies the saved profile");
      runtime.modules().setSetting("clickgui", preferences.background, saved);
      check(packets.get() == 0, "config commands stay local through asynchronous completion");
      cleanDraw(screen);
      configButton(screen, browser, "delete");
      cleanDraw(screen);
      check(java.nio.file.Files.exists(file), "GUI delete requires confirmation");
      configButton(screen, browser, "delete");
      pumpConfigs(runtime, screen);
      cleanDraw(screen);
      check(
          !java.nio.file.Files.exists(file),
          "confirmed GUI delete removes preset from active list");
      invoke(screen, "type", new Class<?>[] {char.class, int.class}, '\0', Keyboard.KEY_ESCAPE);
      check(
          field(game.getClass(), "currentScreen", "field_71462_r").get(game) == parent,
          "Escape returns to the same parent menu");
      cleanDraw(parent);
    } finally {
      owner.close();
    }
  }

  private static void configButton(Object screen, Object browser, String id) throws Exception {
    for (Object button : (java.util.List<?>) field(browser.getClass(), "buttons").get(browser)) {
      if (id.equals(field(button.getClass(), "id").get(button))) {
        check(
            field(button.getClass(), "enabled").getBoolean(button),
            "config action available: " + id);
        click(
            screen,
            field(button.getClass(), "x").getFloat(button) + 3,
            field(button.getClass(), "y").getFloat(button) + 3,
            0);
        return;
      }
    }
    throw new AssertionError("Missing config button: " + id);
  }

  private static void pumpConfigs(ClientRuntime runtime, Object screen) throws Exception {
    Object browser = field(screen.getClass(), "configs").get(screen);
    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(4);
    while (System.nanoTime() < deadline) {
      runtime.tasks().drain();
      method(screen.getClass(), new Class<?>[0], "updateScreen", "func_73876_c").invoke(screen);
      if (field(browser.getClass(), "listing").get(browser) == null
          && field(browser.getClass(), "operation").get(browser) == null) {
        check(
            !field(browser.getClass(), "error").getBoolean(browser),
            "config browser operation succeeded: "
                + field(browser.getClass(), "status").get(browser));
        return;
      }
      Thread.sleep(1);
    }
    throw new AssertionError("Config GUI operation timed out");
  }

  private static void editors(ClientRuntime runtime, Fixture f) throws Exception {
    GuiFonts fonts = new GuiFonts();
    RenderState state = new RenderState();
    UiCanvas c = new UiCanvas();
    c.text = 0xffdddddd;
    c.accent = 0xff80aacc;
    try (RenderState ignored = state.open()) {
      c.font = fonts.get(ClickGuiModule.Font.SANS);
      SettingControl.Actions noEdit = (label, value, x, y, width, submit) -> {};
      SettingControl range = SettingControl.create(runtime.modules(), "gui_probe", f.range, noEdit);
      range.render(c, 0, 0, 120);
      range.press(10, 5, 0);
      range.render(c, 0, 0, 120);
      range.press(116, 22, 0);
      check(f.range.get().lower() == f.range.get().upper(), "range editor cannot cross endpoints");
      SettingControl mode = SettingControl.create(runtime.modules(), "gui_probe", f.choice, noEdit);
      mode.render(c, 0, 0, 120);
      mode.press(10, 5, 0);
      mode.press(10, 36, 0);
      check(f.choice.get() == Choice.SECOND, "enum selection uses metadata choices");
      SettingControl multi = SettingControl.create(runtime.modules(), "gui_probe", f.multi, noEdit);
      multi.render(c, 0, 0, 120);
      multi.press(10, 5, 0);
      multi.press(10, 20, 0);
      check(f.multi.get().isEmpty(), "multi-select toggles independently");
      SettingControl color = SettingControl.create(runtime.modules(), "gui_probe", f.color, noEdit);
      color.render(c, 0, 0, 120);
      color.press(10, 5, 0);
      color.render(c, 0, 0, 120);
      color.press(70, 30, 0);
      color.press(119, 132, 0);
      color.press(10, 150, 0);
      color.press(119, 165, 0);
      check(
          f.color.get().alpha() == 255
              && f.color.get().rainbow()
              && f.color.get().rainbowSpeed() == 10,
          "reusable picker edits visual color, alpha, rainbow and speed");
      SettingControl hex =
          SettingControl.create(
              runtime.modules(),
              "gui_probe",
              f.color,
              (label, value, x, y, width, submit) -> submit.accept("#11223344"));
      hex.render(c, 0, 0, 120);
      hex.press(10, 5, 1);
      check(
          f.color.get().red() == 17 && f.color.get().alpha() == 68,
          "inline hex entry uses the reusable color setting mutation");
      SettingControl text =
          SettingControl.create(
              runtime.modules(),
              "gui_probe",
              f.text,
              (label, value, x, y, width, submit) -> submit.accept("edited"));
      text.render(c, 0, 0, 120);
      text.press(10, 5, 0);
      check(f.text.get().equals("edited"), "string editor uses safe mutation");
      SettingControl number =
          SettingControl.create(
              runtime.modules(),
              "gui_probe",
              f.decimal,
              (label, value, x, y, width, submit) -> submit.accept("0.7"));
      number.render(c, 0, 0, 120);
      number.press(10, 5, 1);
      check(f.decimal.get() == 0.7, "direct numeric entry respects setting parsing");
    } finally {
      fonts.close();
    }
  }

  private static void utilityControls(
      ClientRuntime runtime, Object screen, ClickGuiModule preferences) throws Exception {
    String saved = preferences.utilityLayout.get(), panels = preferences.layout.get();
    boolean search = preferences.search.get();
    try {
      runtime.modules().setSetting("clickgui", preferences.search, false);
      runtime.modules().setSetting("clickgui", preferences.utilityLayout, "{}");
      cleanDraw(screen);
      float vw = field(screen.getClass(), "viewWidth").getFloat(screen);
      click(screen, vw - 70, 13, 0);
      PanelLayout layout = (PanelLayout) screen.getClass().getMethod("model").invoke(screen);
      PanelLayout.Panel render = layout.find(ModuleCategory.RENDER);
      render.x = 12;
      render.y = 30;
      layout.front(render);
      invoke(screen, "click", new Class<?>[] {float.class, float.class, int.class}, 20f, 34f, 0);
      invoke(screen, "drag", new Class<?>[] {float.class, float.class}, 20f, 6f);
      invoke(screen, "release", new Class<?>[0]);
      check(render.y == 2, "disabled Search gives panels its top area");
      runtime.modules().setSetting("clickgui", preferences.search, true);
      cleanDraw(screen);
      check(render.y >= 27, "visible Search recovers panels below the input");
      runtime.modules().setSetting("clickgui", preferences.search, false);
      cleanDraw(screen);
      Object hudTab = field(screen.getClass(), "hudTab").get(screen);
      float hx = field(hudTab.getClass(), "x").getFloat(hudTab),
          hy = field(hudTab.getClass(), "y").getFloat(hudTab);
      invoke(
          screen, "click", new Class<?>[] {float.class, float.class, int.class}, hx + 4, hy + 4, 0);
      invoke(screen, "drag", new Class<?>[] {float.class, float.class}, 24f, 144f);
      invoke(screen, "release", new Class<?>[0]);
      check(
          field(hudTab.getClass(), "x").getFloat(hudTab) == 20
              && field(hudTab.getClass(), "y").getFloat(hudTab) == 140,
          "compact HUD tab drags without opening the editor");
      click(screen, 24, 144, 1);
      check(
          field(screen.getClass(), "hudMenu").getBoolean(screen),
          "right-click HUD exposes editor, toggle and reset actions");
      click(screen, 24, 144, 1);
      Object configTab = field(screen.getClass(), "configTab").get(screen);
      float cx = field(configTab.getClass(), "x").getFloat(configTab),
          cy = field(configTab.getClass(), "y").getFloat(configTab);
      invoke(
          screen, "click", new Class<?>[] {float.class, float.class, int.class}, cx + 4, cy + 4, 0);
      invoke(screen, "drag", new Class<?>[] {float.class, float.class}, 84f, 184f);
      invoke(screen, "release", new Class<?>[0]);
      cleanDraw(screen);
      check(
          field(configTab.getClass(), "x").getFloat(configTab) == 80
              && field(configTab.getClass(), "y").getFloat(configTab) == 180,
          "compact Configs tab drags and saves separately");
      click(screen, 84, 184, 1);
      cleanDraw(screen);
      Object game =
          method(
                  Class.forName("net.minecraft.client.Minecraft"),
                  new Class<?>[0],
                  "getMinecraft",
                  "func_71410_x")
              .invoke(null);
      Object configScreen = field(game.getClass(), "currentScreen", "field_71462_r").get(game);
      check(
          configScreen != screen && configScreen.getClass().getName().endsWith("ConfigScreen"),
          "right-click Configs opens its separate screen");
      cleanDraw(configScreen);
      Object window = field(configScreen.getClass(), "configWindow").get(configScreen);
      float wx = field(window.getClass(), "x").getFloat(window),
          wy = field(window.getClass(), "y").getFloat(window);
      click(configScreen, wx + 4, wy + 4, 1);
      check(
          field(window.getClass(), "collapsed").getBoolean(window),
          "right-click config window collapses its body");
      invoke(
          configScreen,
          "click",
          new Class<?>[] {float.class, float.class, int.class},
          wx + 4,
          wy + 4,
          0);
      invoke(configScreen, "drag", new Class<?>[] {float.class, float.class}, 42f, 16f);
      invoke(configScreen, "release", new Class<?>[0]);
      check(
          field(window.getClass(), "x").getFloat(window) == 38
              && field(window.getClass(), "y").getFloat(window) == 12,
          "config header moves while collapsed (actual "
              + field(window.getClass(), "x").getFloat(window)
              + ", "
              + field(window.getClass(), "y").getFloat(window)
              + ")");
      invoke(
          configScreen, "type", new Class<?>[] {char.class, int.class}, '\0', Keyboard.KEY_ESCAPE);
      check(
          field(game.getClass(), "currentScreen", "field_71462_r").get(game) == screen,
          "config screen closes back to its parent");
      check(
          preferences.utilityLayout.get().contains("140.0"),
          "utility geometry uses persistent native settings");
      cleanDraw(screen);
      capture("clickgui-movable-utilities.png");
    } finally {
      runtime.modules().setSetting("clickgui", preferences.utilityLayout, saved);
      runtime.modules().setSetting("clickgui", preferences.layout, panels);
      runtime.modules().setSetting("clickgui", preferences.search, search);
      cleanDraw(screen);
    }
  }

  private static void click(Object screen, float x, float y, int button) throws Exception {
    invoke(screen, "click", new Class<?>[] {float.class, float.class, int.class}, x, y, button);
    if (screen.getClass().getName().endsWith("PanelScreen")
        && field(screen.getClass(), "utilityDrag").get(screen) != null)
      invoke(screen, "release", new Class<?>[0]);
  }

  private static void draw(Object screen) throws Exception {
    method(
            screen.getClass(),
            new Class<?>[] {int.class, int.class, float.class},
            "drawScreen",
            "func_73863_a")
        .invoke(screen, 0, 0, 0f);
  }

  private static void cleanDraw(Object screen) throws Exception {
    GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    draw(screen);
  }

  private static Object invoke(Object target, String name, Class<?>[] types, Object... arguments)
      throws Exception {
    return target.getClass().getMethod(name, types).invoke(target, arguments);
  }

  private static Field field(Class<?> type, String... names) throws Exception {
    for (Class<?> c = type; c != null; c = c.getSuperclass())
      for (String n : names)
        try {
          Field f = c.getDeclaredField(n);
          f.setAccessible(true);
          return f;
        } catch (NoSuchFieldException ignored) {
        }
    throw new NoSuchFieldException(Arrays.toString(names));
  }

  private static Method method(Class<?> type, Class<?>[] args, String... names) throws Exception {
    for (Class<?> c = type; c != null; c = c.getSuperclass())
      for (String n : names)
        try {
          Method m = c.getDeclaredMethod(n, args);
          m.setAccessible(true);
          return m;
        } catch (NoSuchMethodException ignored) {
        }
    throw new NoSuchMethodException(Arrays.toString(names));
  }

  private static void capture(String name) throws Exception {
    int w = Display.getWidth(), h = Display.getHeight();
    ByteBuffer data = BufferUtils.createByteBuffer(w * h * 4);
    GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, data);
    BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < h; y++)
      for (int x = 0; x < w; x++) {
        int i = (x + y * w) * 4;
        image.setRGB(
            x,
            h - y - 1,
            (data.get(i) & 255) << 16 | (data.get(i + 1) & 255) << 8 | data.get(i + 2) & 255);
      }
    ImageIO.write(image, "png", new File(System.getProperty("perihelion.smoke.output"), name));
  }

  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
    checks++;
  }
}
