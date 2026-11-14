package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;

import com.google.gson.JsonObject;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.*;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.gui.ClickGuiModule;
import perihelion.integration.minecraft189.gui.GuiTheme;
import perihelion.integration.minecraft189.gui.font.GuiFonts;
import perihelion.integration.minecraft189.gui.panel.PanelLayout;
import perihelion.integration.minecraft189.render.RenderState;

/** Packaged style switching, native screen geometry, GL restoration and profile persistence. */
public final class ClientStyleProbe {
  private static int checks;

  @SuppressWarnings({"rawtypes", "unchecked"})
  public static void run(ClientRuntime runtime, Object game, Object hud) throws Exception {
    checks = 0;
    ClickGuiModule preferences = (ClickGuiModule) runtime.modules().require("clickgui").module();
    Map<String, Map<Setting<?>, Object>> saved = new LinkedHashMap<>();
    for (String id : new String[] {"clickgui", "hud"}) {
      Map<Setting<?>, Object> values = new LinkedHashMap<>();
      for (Setting<?> setting : runtime.modules().require(id).module().settings().values())
        values.put(setting, setting.get());
      saved.put(id, values);
    }
    Map<String, Boolean> enabled = new LinkedHashMap<>();
    for (String id : new String[] {"hud", "targethud", "fullbright", "sprint"})
      enabled.put(id, runtime.modules().require(id).requestedEnabled());
    Class<?> screenType = Class.forName("net.minecraft.client.gui.GuiScreen");
    Method display =
        method(game.getClass(), new Class<?>[] {screenType}, "displayGuiScreen", "func_147108_a");
    Field current = field(game.getClass(), "currentScreen", "field_71462_r");
    Object original = current.get(game);
    String preset = "cs-probe-" + UUID.randomUUID();
    boolean profileCreated = false;
    try (RenderState ignored = new RenderState().open()) {
      display.invoke(game, new Object[] {null});
      runtime.modules().setSetting("clickgui", preferences.layout, "[]");
      runtime.modules().setSetting("clickgui", preferences.ravenLayout, "[]");
      runtime.modules().setSetting("clickgui", preferences.utilityLayout, "{}");
      runtime.modules().setSetting("clickgui", preferences.ravenUtilityLayout, "{}");
      runtime.modules().setSetting("clickgui", preferences.scale, 1d);
      runtime.modules().setSetting("clickgui", preferences.customTextStyle, false);
      runtime.modules().setSetting("clickgui", preferences.font, ClickGuiModule.Font.MONO);
      runtime
          .modules()
          .setSetting("clickgui", preferences.clientStyle, ClickGuiModule.ClientStyle.ADJUST);
      runtime.modules().setSetting("clickgui", preferences.search, true);
      for (String id : enabled.keySet()) runtime.modules().setEnabled(id, true);
      for (String id :
          new String[] {
            "watermark",
            "tab_gui",
            "modules",
            "info",
            "target",
            "armor",
            "effects",
            "keys",
            "players",
            "notifications"
          }) set(runtime, "hud", id, true);
      set(runtime, "hud", "watermark-info", false);
      set(runtime, "hud", "hide-visuals", false);
      method(hud.getClass(), "resetLayout").invoke(hud);

      runtime.commands().execute("gui", line -> {});
      Object menu = current.get(game);
      draw(menu);
      Object control = styleControl(menu);
      select(menu, control, 1);
      menu = current.get(game);
      check(
          menu.getClass().getName().endsWith("RavenScreen"),
          "Raven selects its own frontend class");
      check(
          preferences.clientStyle.get() == ClickGuiModule.ClientStyle.RAVEN,
          "native Client style dropdown selects Raven");
      check(
          preferences.font.get() == ClickGuiModule.Font.MONO
              && GuiTheme.font(runtime) == ClickGuiModule.Font.MINECRAFT,
          "Raven forces the native font without replacing the saved Adjust font");
      control = styleControl(menu);
      HudChatProbe.captureWorld(game, "ui-raven-settings.png");
      select(menu, control, 0);
      menu = current.get(game);
      check(
          menu.getClass().getName().endsWith("PanelScreen"),
          "Adjust restores its own frontend class");
      check(
          preferences.clientStyle.get() == ClickGuiModule.ClientStyle.ADJUST
              && GuiTheme.font(runtime) == ClickGuiModule.Font.MONO,
          "native Client style dropdown restores Adjust and its saved font");

      int[] adjustBoxes = null;
      int[] ravenBoxes = null;
      for (ClickGuiModule.ClientStyle style :
          new ClickGuiModule.ClientStyle[] {
            ClickGuiModule.ClientStyle.ADJUST,
            ClickGuiModule.ClientStyle.RAVEN,
            ClickGuiModule.ClientStyle.ADJUST
          }) {
        // The dropdown fixture deliberately moved Misc. Capture the native default arrangement.
        display.invoke(game, new Object[] {null});
        runtime.modules().setSetting("clickgui", preferences.clientStyle, style);
        runtime.modules().setSetting("clickgui", preferences.search, false);
        runtime.commands().execute("gui reset", line -> {});
        menu = current.get(game);
        for (List<?> group : ((Map<?, List<?>>) field(menu.getClass(), "views").get(menu)).values())
          for (Object view : group) field(view.getClass(), "expanded").setBoolean(view, false);
        draw(menu);
        screenTheme(menu, style);
        check(
            menu.getClass()
                .getName()
                .endsWith(
                    style == ClickGuiModule.ClientStyle.RAVEN ? "RavenScreen" : "PanelScreen"),
            "style uses an independent frontend class: " + style);
        if (style == ClickGuiModule.ClientStyle.RAVEN)
          check(
              !Class.forName("perihelion.integration.minecraft189.gui.panel.PanelScreen")
                  .isInstance(menu),
              "Raven does not decorate or inherit the Adjust frontend");
        separatedPanels(menu, style);
        HudChatProbe.captureWorld(
            game,
            style == ClickGuiModule.ClientStyle.RAVEN
                ? "ui-raven.png"
                : adjustBoxes == null ? "ui-adjust-baseline.png" : "ui-adjust-restored.png");

        if (style == ClickGuiModule.ClientStyle.RAVEN) {
          PanelLayout panelModel = (PanelLayout) method(menu.getClass(), "model").invoke(menu);
          PanelLayout.Panel miscPanel = panelModel.find(ModuleCategory.MISC);
          click(menu, miscPanel.x + 5, miscPanel.y + 5, 0);
          draw(menu);
          check(!miscPanel.collapsed, "Raven category icon opens its native floating panel");
          check(
              (Float)
                      method(menu.getClass(), new Class<?>[] {PanelLayout.Panel.class}, "body")
                          .invoke(menu, miscPanel)
                  > 16,
              "opening a Raven category reveals a scrollable module list");
          HudChatProbe.captureWorld(game, "ui-raven-category.png");
          click(menu, miscPanel.x + 5, miscPanel.y + 5, 0);
          draw(menu);
          check(miscPanel.collapsed, "Raven category icon closes its panel");
        }
        if (style == ClickGuiModule.ClientStyle.RAVEN) checkIcons(menu);
        clickTab(menu, "configTab");
        Object configs = current.get(game);
        check(configs.getClass().getName().endsWith("ConfigScreen"), "Configs opens for " + style);
        method(
                GuiHookProbe.class,
                new Class<?>[] {ClientRuntime.class, Object.class},
                "pumpConfigs")
            .invoke(null, runtime, configs);
        draw(configs);
        screenTheme(configs, style);
        if (style == ClickGuiModule.ClientStyle.RAVEN)
          HudChatProbe.captureWorld(game, "ui-raven-configs.png");
        escape(configs);
        check(current.get(game) == menu, "Configs returns to the same Modules screen for " + style);

        draw(menu);
        clickTab(menu, "hudTab");
        check(current.get(game) == menu, "HUD settings stay in Modules for " + style);
        method(menu.getClass(), "arrangeHud").invoke(menu);
        Object editor = current.get(game);
        check(editor.getClass().getName().endsWith("HudEditor"), "HUD editor opens for " + style);
        check(
            ((List<?>) field(screenType, "buttonList", "field_146292_n").get(editor)).isEmpty(),
            "Arrange view has no editor controls for " + style);
        draw(editor);
        screenTheme(editor, style);
        Object preview = field(editor.getClass(), "preview").get(editor);
        check(
            field(preview.getClass(), "classic").getBoolean(preview)
                == (style == ClickGuiModule.ClientStyle.RAVEN),
            "HUD preview uses the selected style for " + style);
        Object font = field(preview.getClass(), "text").get(preview);
        check(
            field(font.getClass(), "choice").get(font) == GuiTheme.font(runtime),
            "HUD preview measures with the effective font for " + style);
        int width = field(screenType, "width", "field_146294_l").getInt(editor);
        int height = field(screenType, "height", "field_146295_m").getInt(editor);
        int[] measured = boxes(preview, width, height);
        if (style == ClickGuiModule.ClientStyle.RAVEN) ravenBoxes = measured;
        else if (adjustBoxes == null) adjustBoxes = measured;
        else
          check(
              Arrays.equals(adjustBoxes, measured), "switching back restores Adjust HUD geometry");
        if (style == ClickGuiModule.ClientStyle.RAVEN) {
          long oldAdjustDraws = field(preview.getClass(), "adjustDraws").getLong(preview);
          render(preview, width, height, true, null, 0, 0);
          Object ravenRenderer = field(preview.getClass(), "raven").get(preview);
          check(
              field(ravenRenderer.getClass(), "frames").getLong(ravenRenderer) > 0,
              "Raven has its own active HUD renderer");
          check(
              ((Set<?>) field(ravenRenderer.getClass(), "drawn").get(ravenRenderer)).size() == 10,
              "all ten Raven HUD elements are drawn by its own renderer");
          Map<?, ?> nativeBoxes = (Map<?, ?>) field(preview.getClass(), "boxes").get(preview);
          for (Map.Entry<?, ?> entry : nativeBoxes.entrySet())
            if (((Enum<?>) entry.getKey()).name().equals("ARMOR")) {
              int cell = field(entry.getValue().getClass(), "w").getInt(entry.getValue()) / 4;
              for (String durability :
                  (String[]) field(preview.getClass(), "durability").get(preview))
                if (durability != null)
                  check(
                      (Integer)
                              method(font.getClass(), new Class<?>[] {String.class}, "width")
                                  .invoke(font, durability)
                          <= cell - 2,
                      "Raven armor uses measured durability text without clipping");
            }
          check(
              field(preview.getClass(), "adjustDraws").getLong(preview) == oldAdjustDraws,
              "Raven never falls back to Adjust element drawing");
        }
        recover(runtime, preview, width, height);
        draw(editor);
        if (style == ClickGuiModule.ClientStyle.RAVEN) {
          Object players =
              runtime.modules().require("hud").module().settings().get("players").get();
          Object notifications =
              runtime.modules().require("hud").module().settings().get("notifications").get();
          try {
            set(runtime, "hud", "players", false);
            set(runtime, "hud", "notifications", false);
            draw(editor);
            HudChatProbe.captureWorld(game, "ui-raven-hud-editor.png");
          } finally {
            set(runtime, "hud", "players", players);
            set(runtime, "hud", "notifications", notifications);
            draw(editor);
          }
        }
        escape(editor);
        check(current.get(game) == menu, "HUD editor returns to Modules for " + style);
        if (style == ClickGuiModule.ClientStyle.RAVEN) {
          display.invoke(game, new Object[] {null});
          Object renderer = field(hud.getClass(), "renderer").get(hud);
          render(renderer, width, height, false, null, 0, 0);
          check(
              field(renderer.getClass(), "classic").getBoolean(renderer),
              "production HUD switches to Raven on the same frame");
          HudChatProbe.captureWorld(game, "ui-raven-hud.png");
          display.invoke(game, menu);
        }
      }
      check(
          adjustBoxes != null && ravenBoxes != null && !Arrays.equals(adjustBoxes, ravenBoxes),
          "Raven has a distinct measured HUD layout");
      check(
          preferences.font.get() == ClickGuiModule.Font.MONO,
          "all screen transitions preserve the stored Adjust font");

      display.invoke(game, new Object[] {null});
      runtime
          .modules()
          .setSetting("clickgui", preferences.clientStyle, ClickGuiModule.ClientStyle.RAVEN);
      runtime.config().saveProfile(preset, false).get(5, TimeUnit.SECONDS);
      profileCreated = true;
      JsonObject stored =
          runtime
              .config()
              .store()
              .readProfile(preset)
              .get(5, TimeUnit.SECONDS)
              .json()
              .getAsJsonObject("modules")
              .getAsJsonObject("clickgui")
              .getAsJsonObject("settings");
      check(
          stored.get("client-style").getAsString().equals("raven")
              && stored.get("font").getAsString().equals("mono"),
          "profile records style separately from the saved font");
      runtime
          .modules()
          .setSetting("clickgui", preferences.clientStyle, ClickGuiModule.ClientStyle.ADJUST);
      runtime.modules().setSetting("clickgui", preferences.font, ClickGuiModule.Font.SANS);
      CompletableFuture<Void> loaded = runtime.config().loadProfile(preset);
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while (!loaded.isDone() && System.nanoTime() < deadline) {
        runtime.tasks().drain();
        Thread.sleep(1);
      }
      loaded.get(1, TimeUnit.SECONDS);
      check(
          preferences.clientStyle.get() == ClickGuiModule.ClientStyle.RAVEN
              && preferences.font.get() == ClickGuiModule.Font.MONO
              && GuiTheme.font(runtime) == ClickGuiModule.Font.MINECRAFT,
          "profile load restores Raven and the independent stored Adjust font");
      runtime
          .modules()
          .setSetting("clickgui", preferences.clientStyle, ClickGuiModule.ClientStyle.ADJUST);
      check(
          GuiTheme.font(runtime) == ClickGuiModule.Font.MONO,
          "Adjust font remains available after a persisted Raven round trip");
      check(GL11.glGetError() == GL11.GL_NO_ERROR, "style probes leave GL clean");
      System.out.println("[PERIHELION_SMOKE] CLIENT_STYLE PASS checks=" + checks);
    } finally {
      display.invoke(game, new Object[] {null});
      for (Map.Entry<String, Map<Setting<?>, Object>> module : saved.entrySet())
        for (Map.Entry<Setting<?>, Object> setting : module.getValue().entrySet())
          runtime
              .modules()
              .setSetting(module.getKey(), (Setting) setting.getKey(), setting.getValue());
      for (Map.Entry<String, Boolean> entry : enabled.entrySet())
        runtime.modules().setEnabled(entry.getKey(), entry.getValue());
      runtime.config().saveNow().get(5, TimeUnit.SECONDS);
      if (profileCreated) Files.deleteIfExists(runtime.config().store().profilePath(preset));
      display.invoke(game, original);
    }
  }

  private static void checkIcons(Object menu) throws Exception {
    java.awt.image.BufferedImage frame =
        javax.imageio.ImageIO.read(
            new java.io.File(System.getProperty("perihelion.smoke.output"), "ui-raven.png"));
    float pixels = field(menu.getClass(), "pixels").getFloat(menu);
    PanelLayout layout = (PanelLayout) method(menu.getClass(), "model").invoke(menu);
    for (PanelLayout.Panel panel : layout.panels()) {
      if (!(Boolean)
          method(menu.getClass(), new Class<?>[] {PanelLayout.Panel.class}, "shown")
              .invoke(menu, panel)) continue;
      Set<Integer> colors = new HashSet<>();
      int left = Math.round((panel.x + 3) * pixels), top = Math.round((panel.y + 4) * pixels);
      for (int y = top; y < Math.min(frame.getHeight(), top + Math.round(8 * pixels)); y++)
        for (int x = left; x < Math.min(frame.getWidth(), left + Math.round(8 * pixels)); x++)
          colors.add(frame.getRGB(x, y) & 0x00f0f0f0);
      check(
          colors.size() >= 3,
          "Raven category icon is textured rather than a solid quad: " + panel.category);
    }
  }

  private static Object styleControl(Object menu) throws Exception {
    method(menu.getClass(), new Class<?>[] {String.class}, "setQuery").invoke(menu, "clickgui");
    draw(menu);
    PanelLayout layout = (PanelLayout) method(menu.getClass(), "model").invoke(menu);
    PanelLayout.Panel panel = layout.find(ModuleCategory.MISC);
    panel.x = 6;
    panel.y = 30;
    panel.collapsed = false;
    panel.scroll = 0;
    layout.front(panel);
    click(
        menu,
        panel.x + 4,
        panel.y + (Float) method(menu.getClass(), "headerHeight").invoke(menu) + 3,
        1);
    draw(menu);
    for (List<?> group : ((Map<?, List<?>>) field(menu.getClass(), "views").get(menu)).values())
      for (Object view : group)
        if (((ModuleEntry) field(view.getClass(), "entry").get(view))
            .info()
            .id()
            .equals("clickgui"))
          for (Object control : (List<?>) field(view.getClass(), "controls").get(view))
            if (((Setting<?>) field(control.getClass(), "setting").get(control))
                .id()
                .equals("client-style")) return control;
    throw new AssertionError("Client style control is not reachable");
  }

  private static void select(Object menu, Object control, int option) throws Exception {
    draw(menu);
    // A style switch loads its own saved layout; keep this input fixture in clear view.
    PanelLayout layout = (PanelLayout) method(menu.getClass(), "model").invoke(menu);
    PanelLayout.Panel panel = layout.find(ModuleCategory.MISC);
    panel.x = 6;
    panel.y = 30;
    panel.scroll = 0;
    layout.front(panel);
    method(menu.getClass(), "persist").invoke(menu);
    draw(menu);
    click(
        menu,
        field(control.getClass(), "x").getFloat(control) + 4,
        field(control.getClass(), "y").getFloat(control) + 3,
        0);
    draw(menu);
    check(field(control.getClass(), "expanded").getBoolean(control), "Client style dropdown opens");
    float y =
        field(control.getClass(), "y").getFloat(control)
            + field(control.getClass(), "headerHeight").getFloat(control);
    for (int i = 0; i < option; i++)
      y +=
          (Float)
              method(control.getClass(), new Class<?>[] {int.class}, "optionHeight")
                  .invoke(control, i);
    click(menu, field(control.getClass(), "x").getFloat(control) + 7, y + 3, 0);
    draw(menu);
  }

  private static void screenTheme(Object screen, ClickGuiModule.ClientStyle style)
      throws Exception {
    Object canvas = field(screen.getClass(), "canvas").get(screen);
    GuiFonts fonts = (GuiFonts) field(screen.getClass(), "fonts").get(screen);
    boolean raven = style == ClickGuiModule.ClientStyle.RAVEN;
    check(
        field(canvas.getClass(), "classic").getBoolean(canvas) == raven,
        screen.getClass().getSimpleName() + " uses " + style + " chrome");
    check(
        (field(canvas.getClass(), "settingPainter").get(canvas) != null) == raven,
        "Raven widgets use their own painter, and Adjust keeps its original painter");
    try (RenderState ignored = new RenderState().open()) {
      check(
          field(canvas.getClass(), "font").get(canvas)
              == fonts.get(raven ? ClickGuiModule.Font.MINECRAFT : ClickGuiModule.Font.MONO),
          screen.getClass().getSimpleName() + " uses the effective " + style + " font");
    }
  }

  private static void separatedPanels(Object menu, ClickGuiModule.ClientStyle style)
      throws Exception {
    PanelLayout layout = (PanelLayout) method(menu.getClass(), "model").invoke(menu);
    List<PanelLayout.Panel> visible = new ArrayList<>();
    Map<PanelLayout.Panel, Float> heights = new IdentityHashMap<>();
    Method shown = method(menu.getClass(), new Class<?>[] {PanelLayout.Panel.class}, "shown");
    Method body = method(menu.getClass(), new Class<?>[] {PanelLayout.Panel.class}, "body");
    float width = field(menu.getClass(), "viewWidth").getFloat(menu),
        height = field(menu.getClass(), "viewHeight").getFloat(menu);
    for (PanelLayout.Panel panel : layout.panels()) {
      if (!(Boolean) shown.invoke(menu, panel)) continue;
      float panelHeight =
          (Float) method(menu.getClass(), "headerHeight").invoke(menu)
              + (Float) body.invoke(menu, panel);
      check(
          panel.x >= 0
              && panel.y >= 0
              && panel.x + panel.width <= width + .01f
              && panel.y + panelHeight <= height + .01f,
          style + " default " + panel.category + " panel fits the screen");
      visible.add(panel);
      heights.put(panel, panelHeight);
    }
    for (int i = 0; i < visible.size(); i++)
      for (int j = i + 1; j < visible.size(); j++) {
        PanelLayout.Panel first = visible.get(i), second = visible.get(j);
        check(
            first.x + first.width <= second.x + .01f
                || second.x + second.width <= first.x + .01f
                || first.y + heights.get(first) <= second.y + .01f
                || second.y + heights.get(second) <= first.y + .01f,
            style + " default panels do not overlap: " + first.category + " / " + second.category);
      }
  }

  private static int[] boxes(Object renderer, int width, int height) throws Exception {
    List<Integer> result = new ArrayList<>();
    Map<?, ?> boxes = (Map<?, ?>) field(renderer.getClass(), "boxes").get(renderer);
    for (Map.Entry<?, ?> entry : boxes.entrySet()) {
      Object box = entry.getValue();
      if (!field(box.getClass(), "visible").getBoolean(box)) continue;
      int x = field(box.getClass(), "x").getInt(box),
          y = field(box.getClass(), "y").getInt(box),
          w = field(box.getClass(), "w").getInt(box),
          h = field(box.getClass(), "h").getInt(box);
      check(
          w > 0 && h > 0 && x >= 0 && y >= 0 && x + w <= width && y + h <= height,
          entry.getKey() + " measured bounds fit " + width + "x" + height);
      if (Arrays.asList("WATERMARK", "TARGET", "EFFECTS", "MODULES")
          .contains(((Enum<?>) entry.getKey()).name()))
        for (int value : new int[] {x, y, w, h}) result.add(value);
    }
    int[] values = new int[result.size()];
    for (int i = 0; i < values.length; i++) values[i] = result.get(i);
    return values;
  }

  private static void recover(ClientRuntime runtime, Object renderer, int width, int height)
      throws Exception {
    Class<?> element = Class.forName("perihelion.integration.minecraft189.hud.HudModule$Element");
    Object watermark = Enum.valueOf((Class) element, "WATERMARK");
    Object oldX = runtime.modules().require("hud").module().settings().get("watermark-x").get(),
        oldY = runtime.modules().require("hud").module().settings().get("watermark-y").get();
    try {
      set(runtime, "hud", "watermark-x", .97d);
      set(runtime, "hud", "watermark-y", .98d);
      for (int[] size : new int[][] {{320, 240}, {427, 240}, {width, height}}) {
        render(renderer, size[0], size[1], true, null, 0, 0);
        boxes(renderer, size[0], size[1]);
        for (int coordinate : new int[] {-10000, 10000}) {
          render(renderer, size[0], size[1], true, watermark, coordinate, coordinate);
          boxes(renderer, size[0], size[1]);
        }
      }
    } finally {
      set(runtime, "hud", "watermark-x", oldX);
      set(runtime, "hud", "watermark-y", oldY);
      render(renderer, width, height, true, null, 0, 0);
    }
  }

  private static void render(
      Object renderer, int width, int height, boolean preview, Object dragging, int x, int y)
      throws Exception {
    Method snapshot = method(RenderHookProbe.class, "readState");
    Object before = snapshot.invoke(null);
    method(
            renderer.getClass(),
            new Class<?>[] {
              int.class,
              int.class,
              boolean.class,
              Class.forName("perihelion.integration.minecraft189.hud.HudModule$Element"),
              int.class,
              int.class
            },
            "render")
        .invoke(renderer, width, height, preview, dragging, x, y);
    check(
        before.equals(snapshot.invoke(null)),
        "HUD restores GL while switching and recovering styles");
  }

  private static void draw(Object screen) throws Exception {
    GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    Method snapshot = method(RenderHookProbe.class, "readState");
    Object before = snapshot.invoke(null);
    method(
            screen.getClass(),
            new Class<?>[] {int.class, int.class, float.class},
            "drawScreen",
            "func_73863_a")
        .invoke(screen, 0, 0, 0f);
    check(before.equals(snapshot.invoke(null)), screen.getClass().getSimpleName() + " restores GL");
  }

  private static void escape(Object screen) throws Exception {
    method(
            Class.forName("net.minecraft.client.gui.GuiScreen"),
            new Class<?>[] {char.class, int.class},
            "keyTyped",
            "func_73869_a")
        .invoke(screen, '\0', Keyboard.KEY_ESCAPE);
  }

  private static void clickTab(Object menu, String id) throws Exception {
    Object tab = field(menu.getClass(), id).get(menu);
    click(
        menu,
        field(tab.getClass(), "x").getFloat(tab) + 3,
        field(tab.getClass(), "y").getFloat(tab) + 3,
        0);
  }

  private static void click(Object menu, float x, float y, int button) throws Exception {
    method(menu.getClass(), new Class<?>[] {float.class, float.class, int.class}, "click")
        .invoke(menu, x, y, button);
    method(menu.getClass(), "release").invoke(menu);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void set(ClientRuntime runtime, String module, String id, Object value) {
    runtime
        .modules()
        .setSetting(
            module, (Setting) runtime.modules().require(module).module().settings().get(id), value);
  }

  private static void check(boolean pass, String message) {
    checks++;
    if (!pass) throw new AssertionError("Client style: " + message);
  }
}
