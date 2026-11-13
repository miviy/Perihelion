package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;

import java.lang.reflect.*;
import java.util.*;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import perihelion.core.input.Keybind;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleCategory;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.gui.ClickGuiModule;
import perihelion.integration.minecraft189.gui.GuiTheme;
import perihelion.integration.minecraft189.gui.panel.PanelLayout;
import perihelion.integration.minecraft189.gui.widget.SettingControl;

/** Both generations through the packaged title, native input, live GL and the real HUD editor. */
public final class NewVisualsProbe {
  private static int checks;

  public static void title(ClientRuntime runtime, Object game) throws Exception {
    checks = 0;
    ClickGuiModule preferences = (ClickGuiModule) runtime.modules().require("clickgui").module();
    Map<Setting<?>, Object> saved = snapshot(runtime, "clickgui");
    Keybind binding = runtime.modules().require("clickgui").binding();
    Field current = field(game.getClass(), "currentScreen", "field_71462_r");
    Object menu = current.get(game);
    try {
      clickId(menu, 0x5042);
      Object settings = current.get(game);
      check(
          settings.getClass().getSimpleName().equals("ClientSettingsScreen"),
          "title opens Client Settings");
      int width = number(settings, "width", "field_146294_l"),
          height = number(settings, "height", "field_146295_m");
      resize(settings, game, 320, 240);
      draw(settings);
      bounds(settings);
      resize(settings, game, width, height);
      clickId(settings, 1);
      check(!preferences.modern(), "Old visuals applies immediately");
      clickId(settings, 0);
      Object old = current.get(game);
      draw(old);
      check(
          old.getClass().getSimpleName().equals("PanelScreen"),
          "Old opens the preserved panel GUI");
      key(old, Keyboard.KEY_ESCAPE);
      check(current.get(game) == settings, "Old GUI returns to Client Settings");
      String oldLayout = preferences.layout.get();
      clickId(settings, 2);
      draw(settings);
      check(preferences.modern(), "New visuals applies immediately");
      ProductionHudProbe.capture("client-settings.png");
      clickId(settings, 0);
      Object modern = current.get(game);
      check(modern.getClass().getSimpleName().equals("ModernScreen"), "New opens its own GUI");
      for (int[] size : new int[][] {{320, 240}, {427, 240}, {640, 360}, {854, 480}}) {
        resize(modern, game, size[0], size[1]);
        draw(modern);
        PanelLayout model = (PanelLayout) method(modern.getClass(), "model").invoke(modern);
        float vw = decimal(modern, "viewWidth"), vh = decimal(modern, "viewHeight");
        int shown = 0;
        for (PanelLayout.Panel panel : model.panels()) {
          if (!(Boolean)
              method(modern.getClass(), new Class<?>[] {PanelLayout.Panel.class}, "shown")
                  .invoke(modern, panel)) continue;
          shown++;
          float body =
              (Float)
                  method(modern.getClass(), new Class<?>[] {PanelLayout.Panel.class}, "body")
                      .invoke(modern, panel);
          check(
              panel.x >= 0
                  && panel.y >= 0
                  && panel.x + panel.width <= vw + 1
                  && panel.y + panel.headerHeight + body <= vh + 1,
              "Dropdown fits the resized viewport");
        }
        check(shown == 6, "Six independent category dropdowns are visible");
        check(GL11.glGetError() == GL11.GL_NO_ERROR, "New GUI has no GL errors");
      }
      resize(modern, game, width, height);
      draw(modern);
      ProductionHudProbe.capture("new-visuals-modules.png");
      tooltips(modern);
      RoundedGuiProbe.run(runtime, game, modern);
      motion(modern);
      PanelLayout model = (PanelLayout) method(modern.getClass(), "model").invoke(modern);
      PanelLayout.Panel combat = model.find(ModuleCategory.COMBAT);
      click(modern, combat.x + 8, combat.y + 8, 1);
      check(combat.collapsed, "Right-click collapses a category");
      click(modern, combat.x + 8, combat.y + 8, 1);
      check(!combat.collapsed, "Right-click opens a category");
      float originalX = combat.x;
      click(modern, combat.x + 8, combat.y + 8, 0);
      method(modern.getClass(), new Class<?>[] {float.class, float.class}, "drag")
          .invoke(modern, combat.x + 18, combat.y + 10);
      method(modern.getClass(), "release").invoke(modern);
      check(combat.x != originalX, "Category headers drag using the shared controller");
      check(preferences.layout.get().equals(oldLayout), "Dragging New preserves the old layout");
      DragSwayProbe.run(runtime, modern);
      click(modern, 12, decimal(modern, "viewHeight") - 16, 0);
      for (char c : "clickgui".toCharArray()) type(modern, c, 0);
      draw(modern);
      check(
          (Integer) method(modern.getClass(), "matchingModules").invoke(modern) == 1,
          "Search filters modules across all dropdowns");
      PanelLayout.Panel panel = model.find(ModuleCategory.MISC);
      click(modern, panel.x + 8, panel.y + panel.headerHeight + 8, 1);
      draw(modern);
      SettingControl slider =
          (SettingControl)
              method(
                      GuiHookProbe.class,
                      new Class<?>[] {Object.class, String.class, String.class},
                      "findControl")
                  .invoke(null, modern, "clickgui", "scale");
      int visible = 0;
      for (Setting<?> setting : preferences.settings().values()) if (setting.isVisible()) visible++;
      check(
          visible == 6,
          "New GUI exposes Visuals, Menu scale, Corner radius, Font, Bold and Tooltips");
      for (int i = 0; i < 12; i++) {
        float cy = decimal(slider, "y");
        float body =
            (Float)
                method(modern.getClass(), new Class<?>[] {PanelLayout.Panel.class}, "body")
                    .invoke(modern, panel);
        if (cy >= panel.y + panel.headerHeight
            && cy + slider.height() <= panel.y + panel.headerHeight + body) break;
        scroll(modern, panel.x + 12, panel.y + panel.headerHeight + 12, 18);
        draw(modern);
      }
      click(modern, panel.x + 12, decimal(slider, "y") + 4, 1);
      check(
          field(modern.getClass(), "editor").get(modern) != null,
          "Right-click opens exact numeric editing");
      for (char c : "1.05".toCharArray()) type(modern, c, 0);
      type(modern, '\n', Keyboard.KEY_RETURN);
      check(
          preferences.scale.get() == 1.05,
          "Dropdown commits numeric edits through native settings");
      draw(modern);
      ProductionHudProbe.capture("new-visuals-settings.png");
      GuiFontProbe.title(runtime, modern);
      ThemeProbe.title(runtime, modern);
      panel = model.find(ModuleCategory.MISC);
      click(modern, panel.x + 10, panel.y + panel.headerHeight + 17 + 8 - panel.scroll, 0);
      Object capture = field(modern.getClass(), "binding").get(modern);
      check(
          (Boolean) method(capture.getClass(), "active").invoke(capture),
          "Bind row starts exclusive key capture");
      type(modern, '\0', Keyboard.KEY_ESCAPE);
      check(
          runtime.modules().require("clickgui").binding().equals(binding)
              && !(Boolean) method(capture.getClass(), "active").invoke(capture),
          "Escape cancels binding capture");
      type(modern, '\0', Keyboard.KEY_ESCAPE);
      check(current.get(game) == settings, "Dropdown returns to Client Settings");
      key(settings, Keyboard.KEY_ESCAPE);
      check(current.get(game) == menu, "Client Settings returns to the same title");
      System.out.println("[PERIHELION_SMOKE] NEW_VISUALS_TITLE PASS checks=" + checks);
    } finally {
      restore(runtime, "clickgui", saved);
      runtime.modules().setBinding("clickgui", binding);
      display(game, menu);
      draw(menu);
    }
  }

  public static void world(ClientRuntime runtime, Object game) throws Exception {
    checks = 0;
    ClickGuiModule preferences = (ClickGuiModule) runtime.modules().require("clickgui").module();
    Map<Setting<?>, Object> savedGui = snapshot(runtime, "clickgui"),
        savedHud = snapshot(runtime, "hud");
    boolean oldEnabled = runtime.modules().require("hud").requestedEnabled();
    Field current = field(game.getClass(), "currentScreen", "field_71462_r");
    Object original = current.get(game);
    try {
      runtime.modules().setSetting("clickgui", preferences.visuals, ClickGuiModule.Visuals.NEW);
      runtime.modules().setEnabled("hud", true);
      for (String id :
          new String[] {
            "watermark", "info", "modules", "target", "keys", "effects", "notifications"
          }) {
        @SuppressWarnings("unchecked")
        Setting<Boolean> setting =
            (Setting<Boolean>) runtime.modules().require("hud").module().settings().get(id);
        runtime.modules().setSetting("hud", setting, true);
      }
      Object hud = runtime.modules().require("hud").module();
      runtime.commands().execute("gui", line -> {});
      Object modern = current.get(game);
      draw(modern);
      check(
          modern.getClass().getSimpleName().equals("ModernScreen"),
          "Right Shift/command service uses New in-world");
      captureWorld(game, modern, runtime);
      MoonGuiProbe.run(game, modern);
      Object hudTab = field(modern.getClass(), "hudTab").get(modern);
      click(modern, decimal(hudTab, "x") + 8, decimal(hudTab, "y") + 8, 0);
      method(modern.getClass(), "release").invoke(modern);
      MoonGuiProbe.closed(modern);
      Object editor = current.get(game);
      GL11.glClearColor(.045f, .05f, .06f, 1);
      GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
      draw(editor);
      check(
          editor.getClass().getSimpleName().equals("HudEditor"),
          "New GUI opens the real HUD editor");
      Object preview = field(editor.getClass(), "preview").get(editor);
      check(
          field(preview.getClass(), "modern").getBoolean(preview),
          "HUD editor preview uses New visuals");
      check(
          !field(preview.getClass(), "classic").getBoolean(preview),
          "New HUD does not route to Raven");
      GuiFontProbe.hud(runtime, editor, preview);
      ThemeProbe.hud(runtime, editor, preview);
      Map<?, ?> boxes = (Map<?, ?>) field(preview.getClass(), "boxes").get(preview);
      int visible = 0;
      for (Object box : boxes.values())
        if (field(box.getClass(), "visible").getBoolean(box)) visible++;
      check(visible >= 7, "New HUD draws the enabled preview elements");
      check(GL11.glGetError() == GL11.GL_NO_ERROR, "New HUD draws without GL errors");
      ProductionHudProbe.capture("new-visuals-hud-editor.png");
      clickId(editor, 3);
      draw(editor);
      ProductionHudProbe.capture("new-visuals-hud-elements.png");
      clickId(editor, 3);
      key(editor, Keyboard.KEY_ESCAPE);
      check(current.get(game) == modern, "HUD editor returns to New GUI");
      draw(modern);
      ConfigDropdownProbe.run(runtime, game, modern);
      check(current.get(game) == modern, "All config actions stay inside the dropdown");
      check(GuiTheme.modern(runtime), "Dropdown config actions preserve the selected frontend");
      System.out.println("[PERIHELION_SMOKE] NEW_VISUALS_WORLD PASS checks=" + checks);
    } finally {
      display(game, original);
      restore(runtime, "clickgui", savedGui);
      restore(runtime, "hud", savedHud);
      runtime.modules().setEnabled("hud", oldEnabled);
    }
  }

  private static void motion(Object modern) throws Exception {
    PanelLayout model = (PanelLayout) method(modern.getClass(), "model").invoke(modern);
    PanelLayout.Panel p = model.find(ModuleCategory.COMBAT);
    float x = p.x + 8, y = p.y + p.headerHeight + 8;
    click(modern, x, y, 1);
    frame(modern, 1 / 60f);
    Map<?, ?> rows = (Map<?, ?>) field(modern.getClass(), "motions").get(modern);
    Object view =
        ((List<?>)
                ((Map<?, ?>) field(modern.getClass(), "views").get(modern))
                    .get(ModuleCategory.COMBAT))
            .get(0);
    Object record = rows.get(view);
    float partial = decimal(record, "height");
    float target =
        (Float)
            method(modern.getClass(), new Class<?>[] {view.getClass()}, "settingsHeight")
                .invoke(modern, view);
    check(partial > 0 && partial < target, "Settings open with a partial displayed height");
    ProductionHudProbe.capture("moon-gui-expanding.png");
    click(modern, x, y, 1);
    frame(modern, 1 / 60f);
    check(decimal(record, "height") < partial, "Reversing an expansion contracts smoothly");
    draw(modern);
    check(
        decimal(record, "height") == 0,
        "Collapsed outgoing settings release their measured height");
    click(modern, x, y, 1);
    draw(modern);
    ProductionHudProbe.capture("moon-gui-expanded.png");
    click(modern, x, y, 1);
    draw(modern);
  }

  private static void frame(Object screen, float seconds) throws Exception {
    GL11.glClearColor(.035f, .037f, .042f, 1);
    GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    field(screen.getClass(), "previousFrame")
        .setLong(screen, System.nanoTime() - (long) (seconds * 1_000_000_000L));
    method(
            screen.getClass(),
            new Class<?>[] {int.class, int.class, float.class},
            "drawScreen",
            "func_73863_a")
        .invoke(screen, -1, -1, 0f);
  }

  private static void captureWorld(Object game, Object modern, ClientRuntime runtime)
      throws Exception {
    String[] fixtures = {"fullbright", "sprint", "nofov"};
    Map<String, Boolean> enabled = new LinkedHashMap<>();
    try {
      for (String id : fixtures) {
        try {
          enabled.put(id, runtime.modules().require(id).requestedEnabled());
          runtime.modules().setEnabled(id, true);
        } catch (IllegalArgumentException ignored) {
        }
      }
      draw(modern);
      Object framebuffer = method(game.getClass(), "getFramebuffer", "func_147110_a").invoke(game);
      method(
              framebuffer.getClass(),
              new Class<?>[] {boolean.class},
              "bindFramebuffer",
              "func_147610_a")
          .invoke(framebuffer, true);
      Object renderer = field(game.getClass(), "entityRenderer", "field_71460_t").get(game);
      method(
              renderer.getClass(),
              new Class<?>[] {float.class, long.class},
              "updateCameraAndRender",
              "func_181560_a")
          .invoke(renderer, 0f, System.nanoTime());
      check(
          GL11.glGetError() == GL11.GL_NO_ERROR,
          "World blur and dropdowns render without GL errors");
      ProductionHudProbe.capture("moon-gui-in-world.png");
    } finally {
      for (Map.Entry<String, Boolean> e : enabled.entrySet())
        runtime.modules().setEnabled(e.getKey(), e.getValue());
    }
  }

  private static Map<Setting<?>, Object> snapshot(ClientRuntime runtime, String id) {
    Map<Setting<?>, Object> saved = new LinkedHashMap<>();
    for (Setting<?> setting : runtime.modules().require(id).module().settings().values())
      saved.put(setting, setting.get());
    return saved;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void restore(ClientRuntime runtime, String id, Map<Setting<?>, Object> saved) {
    for (Map.Entry<Setting<?>, Object> e : saved.entrySet())
      runtime.modules().setSetting(id, (Setting) e.getKey(), e.getValue());
  }

  private static void tooltips(Object screen) throws Exception {
    perihelion.integration.minecraft189.gui.widget.Tooltip tooltip =
        new perihelion.integration.minecraft189.gui.widget.Tooltip();
    perihelion.integration.minecraft189.gui.widget.UiCanvas canvas =
        (perihelion.integration.minecraft189.gui.widget.UiCanvas)
            field(screen.getClass(), "canvas").get(screen);
    float width = decimal(screen, "viewWidth"), height = decimal(screen, "viewHeight");
    String description =
        "Shows module descriptions after hovering for two seconds. "
            + "Move away or interact with a control to dismiss this tooltip.";
    try (perihelion.integration.minecraft189.render.RenderState state =
        new perihelion.integration.minecraft189.render.RenderState().open()) {
      perihelion.integration.minecraft189.render.RenderStyle.text();
      float scale = decimal(screen, "scale");
      GL11.glScalef(scale, scale, 1);
      check(
          !tooltip.draw(canvas, description, 110, 70, width, height),
          "Tooltip stays hidden when entering a row");
      field(tooltip.getClass(), "since").setLong(tooltip, System.nanoTime() - 300_000_000L);
      check(
          !tooltip.draw(canvas, description, 110, 70, width, height),
          "Tooltip stays hidden after the former 300ms delay");
      field(tooltip.getClass(), "since").setLong(tooltip, System.nanoTime() - 2_100_000_000L);
      check(
          tooltip.draw(canvas, description, 110, 70, width, height),
          "Tooltip appears after a two-second dwell");
      ProductionHudProbe.capture("tooltip-themed.png");
      tooltip.clear();
      check(
          !tooltip.draw(canvas, description, width - 4, height - 4, width, height),
          "Clearing starts a fresh hover delay");
      field(tooltip.getClass(), "since").setLong(tooltip, System.nanoTime() - 2_100_000_000L);
      check(
          tooltip.draw(canvas, description, width - 4, height - 4, width, height),
          "Tooltip draws beside a viewport corner");
      ProductionHudProbe.capture("tooltip-edge.png");
      check(GL11.glGetError() == GL11.GL_NO_ERROR, "Tooltip painting has no GL errors");
    }
    draw(screen);
  }

  private static float decimal(Object object, String name) throws Exception {
    return field(object.getClass(), name).getFloat(object);
  }

  private static int number(Object object, String... names) throws Exception {
    return field(object.getClass(), names).getInt(object);
  }

  private static void clickId(Object screen, int id) throws Exception {
    method(MainMenuHookProbe.class, new Class<?>[] {Object.class, int.class}, "clickId")
        .invoke(null, screen, id);
  }

  private static void click(Object screen, float x, float y, int button) throws Exception {
    method(screen.getClass(), new Class<?>[] {float.class, float.class, int.class}, "click")
        .invoke(screen, x, y, button);
  }

  private static void scroll(Object screen, float x, float y, float delta) throws Exception {
    method(screen.getClass(), new Class<?>[] {float.class, float.class, float.class}, "scroll")
        .invoke(screen, x, y, delta);
  }

  private static void type(Object screen, char c, int code) throws Exception {
    method(screen.getClass(), new Class<?>[] {char.class, int.class}, "type")
        .invoke(screen, c, code);
  }

  private static void key(Object screen, int code) throws Exception {
    method(MainMenuHookProbe.class, new Class<?>[] {Object.class, int.class}, "type")
        .invoke(null, screen, code);
  }

  private static void draw(Object screen) throws Exception {
    GL11.glClearColor(.035f, .037f, .042f, 1);
    GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    Method readState = method(RenderHookProbe.class, "readState");
    Object before = readState.invoke(null);
    Method paint =
        method(
            screen.getClass(),
            new Class<?>[] {int.class, int.class, float.class},
            "drawScreen",
            "func_73863_a");
    int frames = screen.getClass().getSimpleName().equals("ModernScreen") ? 32 : 1;
    for (int i = 0; i < frames; i++) {
      GL11.glClearColor(.035f, .037f, .042f, 1);
      GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
      if (frames > 1)
        field(screen.getClass(), "previousFrame").setLong(screen, System.nanoTime() - 16_666_667L);
      paint.invoke(screen, -1, -1, 0f);
    }
    check(before.equals(readState.invoke(null)), "Screen restores GL state");
  }

  private static void resize(Object screen, Object game, int w, int h) throws Exception {
    method(
            screen.getClass(),
            new Class<?>[] {game.getClass(), int.class, int.class},
            "setWorldAndResolution",
            "func_146280_a")
        .invoke(screen, game, w, h);
  }

  private static void display(Object game, Object screen) throws Exception {
    method(
            game.getClass(),
            new Class<?>[] {Class.forName("net.minecraft.client.gui.GuiScreen")},
            "displayGuiScreen",
            "func_147108_a")
        .invoke(game, screen);
  }

  private static void bounds(Object screen) throws Exception {
    List<?> buttons =
        (List<?>) field(screen.getClass(), "buttonList", "field_146292_n").get(screen);
    int w = number(screen, "width", "field_146294_l"),
        h = number(screen, "height", "field_146295_m");
    for (Object button : buttons) {
      int x = number(button, "xPosition", "field_146128_h"),
          y = number(button, "yPosition", "field_146129_i");
      check(
          x >= 0
              && y >= 0
              && x + number(button, "width", "field_146120_f") <= w
              && y + number(button, "height", "field_146121_g") <= h,
          "Client Settings controls fit small viewport");
    }
  }

  private static void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }
}
