package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.lwjgl.opengl.GL11;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.gui.ClickGuiModule;
import perihelion.integration.minecraft189.gui.font.GuiFonts;
import perihelion.integration.minecraft189.gui.panel.PanelLayout;
import perihelion.integration.minecraft189.render.RenderState;

/** Native screenshots and typography checks across Modules, Configs, editor and chat HUD. */
public final class UiThemeProbe {
  private static int checks;

  public static void run(ClientRuntime runtime, Object game, Object hud) throws Exception {
    checks = 0;
    ClickGuiModule preferences = (ClickGuiModule) runtime.modules().require("clickgui").module();
    ClickGuiModule.Font oldFont = preferences.font.get();
    double oldScale = preferences.scale.get();
    String oldLayout = preferences.layout.get();
    boolean oldHud = runtime.modules().require("hud").requestedEnabled();
    Object oldPlayers = runtime.modules().require("hud").module().settings().get("players").get();
    Class<?> screenType = Class.forName("net.minecraft.client.gui.GuiScreen");
    Method display =
        method(game.getClass(), new Class<?>[] {screenType}, "displayGuiScreen", "func_147108_a");
    Field current = field(game.getClass(), "currentScreen", "field_71462_r");
    Object original = current.get(game);
    String preset = "ui-theme-preview";
    Object bus =
        Class.forName("net.minecraftforge.common.MinecraftForge").getField("EVENT_BUS").get(null);
    Object ingame = field(game.getClass(), "ingameGUI", "field_71456_v").get(game);
    Object chat = method(ingame.getClass(), "getChatGUI", "func_146158_b").invoke(ingame);
    List<?> history = (List<?>) field(chat.getClass(), "chatLines", "field_146252_h").get(chat);
    int historySize = history.size();
    try {
      runtime.config().saveProfile(preset, false).get(5, TimeUnit.SECONDS);
      runtime.commands().execute("gui reset", line -> {});
      Object menu = current.get(game);
      HudChatProbe.captureWorld(game, "ui-default-adjust.png");
      check(!preferences.customTextStyle.get(), "default appearance leaves custom styling off");
      check(
          runtime.modules().require("clickgui").info().category()
                  == perihelion.core.module.ModuleCategory.MISC
              && runtime.modules().require("inputfixes").info().category()
                  == perihelion.core.module.ModuleCategory.MISC,
          "client controls belong to Misc");
      PanelLayout initial =
          (PanelLayout) method(menu.getClass(), new Class<?>[0], "model").invoke(menu);
      check(
          !(Boolean)
              method(menu.getClass(), new Class<?>[] {PanelLayout.Panel.class}, "shown")
                  .invoke(menu, initial.find(perihelion.core.module.ModuleCategory.CLIENT)),
          "empty legacy Client category does not occupy a panel");
      GuiFonts fonts = (GuiFonts) field(menu.getClass(), "fonts").get(menu);
      for (ClickGuiModule.Font font :
          new ClickGuiModule.Font[] {
            ClickGuiModule.Font.SANS, ClickGuiModule.Font.MINECRAFT, ClickGuiModule.Font.MONO
          }) {
        runtime.modules().setSetting("clickgui", preferences.font, font);
        GuiFonts.Face face;
        try (RenderState ignored = new RenderState().open()) {
          face = fonts.get(font);
        }
        check(
            face.width("Wi\u03a9\u0416")
                == face.width("W") + face.width("i") + face.width("\u03a9") + face.width("\u0416"),
            "mixed Unicode advances match rendered font runs: " + font);
        check(face.height() <= 14 && face.height() > 0, "chosen font fits header height: " + font);
        for (double scale : new double[] {1, 1.05}) {
          String suffix = font.name().toLowerCase(Locale.ROOT) + (scale == 1 ? "" : "-fractional");
          runtime.modules().setSetting("clickgui", preferences.scale, scale);
          display.invoke(game, menu);
          // The same parent screen keeps the explicit native view transitions.
          float vw = field(menu.getClass(), "viewWidth").getFloat(menu);
          method(menu.getClass(), new Class<?>[] {float.class, float.class, int.class}, "click")
              .invoke(menu, vw - 70, 14f, 0);
          HudChatProbe.captureWorld(game, "ui-modules-" + suffix + ".png");
          chatGate(bus, game, true);
          PanelLayout layout = (PanelLayout) method(menu.getClass(), "model").invoke(menu);
          for (PanelLayout.Panel above : layout.panels())
            for (PanelLayout.Panel below : layout.panels()) {
              if (field(above.getClass(), "bodyFraction").getFloat(above) <= 0
                  || field(below.getClass(), "bodyFraction").getFloat(below) <= 0
                  || above == below
                  || above.x + above.width <= below.x
                  || below.x + below.width <= above.x
                  || above.y >= below.y) continue;
              float bodyHeight =
                  (Float)
                      method(menu.getClass(), new Class<?>[] {PanelLayout.Panel.class}, "body")
                          .invoke(menu, above);
              check(
                  above.y + PanelLayout.HEADER + bodyHeight <= below.y + .01f,
                  "default wrapped rows do not cover lower category headers: " + suffix);
            }
          check(
              field(field(menu.getClass(), "canvas").get(menu).getClass(), "font")
                      .get(field(menu.getClass(), "canvas").get(menu))
                  == face,
              "Modules uses selected shared font: " + suffix);
          vw = field(menu.getClass(), "viewWidth").getFloat(menu);
          method(menu.getClass(), new Class<?>[] {float.class, float.class, int.class}, "click")
              .invoke(menu, vw - 40, 14f, 0);
          method(menu.getClass(), new Class<?>[0], "release").invoke(menu);
          Object configScreen = current.get(game);
          check(
              configScreen != menu && configScreen.getClass().getName().endsWith("ConfigScreen"),
              "Configs opens its own native screen: " + suffix);
          Method pump =
              GuiHookProbe.class.getDeclaredMethod(
                  "pumpConfigs", ClientRuntime.class, Object.class);
          pump.setAccessible(true);
          pump.invoke(null, runtime, configScreen);
          HudChatProbe.captureWorld(game, "ui-configs-" + suffix + ".png");
          chatGate(bus, game, true);
          Object browser = field(configScreen.getClass(), "configs").get(configScreen);
          float left = field(browser.getClass(), "left").getFloat(browser),
              listTop = field(browser.getClass(), "listTop").getFloat(browser);
          method(
                  configScreen.getClass(),
                  new Class<?>[] {float.class, float.class, int.class},
                  "click")
              .invoke(configScreen, left + 15, listTop + 4, 0);
          HudChatProbe.captureWorld(game, "ui-configs-" + suffix + ".png");
          check(
              field(browser.getClass(), "width").getFloat(browser)
                  <= field(configScreen.getClass(), "viewWidth").getFloat(configScreen),
              "config browser stays compact: " + suffix);
          float viewHeight = field(configScreen.getClass(), "viewHeight").getFloat(configScreen);
          for (Object button : (List<?>) field(browser.getClass(), "buttons").get(browser))
            check(
                field(button.getClass(), "y").getFloat(button)
                        + field(button.getClass(), "h").getFloat(button)
                    <= viewHeight,
                "config action remains on-screen: " + suffix);
          method(configScreen.getClass(), new Class<?>[] {char.class, int.class}, "type")
              .invoke(configScreen, '\0', org.lwjgl.input.Keyboard.KEY_ESCAPE);
          check(
              current.get(game) == menu,
              "config screen returns to the same module menu: " + suffix);
          Object configFonts = field(configScreen.getClass(), "fonts").get(configScreen);
          check(
              ((Map<?, ?>) field(configFonts.getClass(), "atlases").get(configFonts)).isEmpty(),
              "closed Configs screen releases its font textures: " + suffix);
          method(hud.getClass(), new Class<?>[] {screenType}, "openEditor").invoke(hud, menu);
          Object editor = current.get(game);
          HudChatProbe.captureWorld(game, "ui-hud-editor-" + suffix + ".png");
          chatGate(bus, game, true);
          Object preview = field(editor.getClass(), "preview").get(editor),
              text = field(preview.getClass(), "text").get(preview);
          check(
              field(text.getClass(), "choice").get(text) == font,
              "HUD preview uses selected shared font: " + suffix);
          int plain =
              (Integer)
                  method(text.getClass(), new Class<?>[] {String.class}, "width")
                      .invoke(text, "Player");
          int colored =
              (Integer)
                  method(text.getClass(), new Class<?>[] {String.class}, "width")
                      .invoke(text, "\u00a7aPlayer\u00a7r");
          if (font == ClickGuiModule.Font.MINECRAFT) {
            int bold =
                (Integer)
                    method(text.getClass(), new Class<?>[] {String.class}, "width")
                        .invoke(text, "\u00a7lPlayer");
            check(bold > plain, "Minecraft HUD preserves native bold advances");
          }
          check(plain == colored, "color codes retain selected face metrics: " + suffix);
          Object elements = null;
          for (Object button :
              (List<?>) field(editor.getClass(), "buttonList", "field_146292_n").get(editor)) {
            check(
                button.getClass().getName().endsWith("ThemeButton"),
                "editor button uses shared chrome: " + suffix);
            if (field(button.getClass(), "id", "field_146127_k").getInt(button) == 3)
              elements = button;
          }
          Method click =
              method(
                  editor.getClass(),
                  new Class<?>[] {int.class, int.class, int.class},
                  "mouseClicked",
                  "func_73864_a");
          click.invoke(
              editor,
              field(elements.getClass(), "xPosition", "field_146128_h").getInt(elements) + 4,
              field(elements.getClass(), "yPosition", "field_146129_i").getInt(elements) + 4,
              0);
          HudChatProbe.captureWorld(game, "ui-hud-elements-" + suffix + ".png");
          check(
              field(editor.getClass(), "elementsMenu").getBoolean(editor),
              "themed Elements list opens: " + suffix);
          click.invoke(
              editor,
              field(elements.getClass(), "xPosition", "field_146128_h").getInt(elements) + 4,
              field(elements.getClass(), "yPosition", "field_146129_i").getInt(elements) + 4,
              0);
          Map<?, ?> boxes = (Map<?, ?>) field(preview.getClass(), "boxes").get(preview);
          Object watermark = null;
          for (Map.Entry<?, ?> entry : boxes.entrySet())
            if (((Enum<?>) entry.getKey()).name().equals("WATERMARK")) watermark = entry.getValue();
          click.invoke(
              editor,
              field(watermark.getClass(), "x").getInt(watermark) + 3,
              field(watermark.getClass(), "y").getInt(watermark) + 3,
              0);
          check(
              field(editor.getClass(), "dragging").get(editor) != null,
              "editor drag begins before font transition: " + suffix);
          runtime
              .modules()
              .setSetting(
                  "clickgui",
                  preferences.font,
                  font == ClickGuiModule.Font.MONO
                      ? ClickGuiModule.Font.SANS
                      : ClickGuiModule.Font.MONO);
          HudChatProbe.captureWorld(game, "ui-font-transition.png");
          check(
              field(editor.getClass(), "dragging").get(editor) == null,
              "font transition cancels old drag geometry: " + suffix);
          runtime.modules().setSetting("clickgui", preferences.font, font);
          method(
                  editor.getClass(),
                  new Class<?>[] {char.class, int.class},
                  "keyTyped",
                  "func_73869_a")
              .invoke(editor, '\0', 1);
          check(current.get(game) == menu, "editor returns to the same parent menu: " + suffix);
          Object editorFonts = field(editor.getClass(), "fonts").get(editor);
          check(
              ((Map<?, ?>) field(editorFonts.getClass(), "atlases").get(editorFonts)).isEmpty(),
              "closed editor releases chrome font textures: " + suffix);
          runtime.modules().setEnabled("hud", true);
          setPlayers(runtime, true);
          display.invoke(game, Class.forName("net.minecraft.client.gui.GuiChat").newInstance());
          HudChatProbe.captureWorld(game, "ui-hud-chat-" + suffix + ".png");
          chatGate(bus, game, false);
          Object renderer = field(hud.getClass(), "renderer").get(hud),
              productionText = field(renderer.getClass(), "text").get(renderer);
          check(
              field(productionText.getClass(), "choice").get(productionText) == font,
              "production chat HUD uses selected font: " + suffix);
          display.invoke(game, menu);
          check(
              GL11.glGetError() == GL11.GL_NO_ERROR,
              "native themed screens leave no GL error: " + suffix);
        }
      }
      textStyles(runtime, game, menu, display, fonts, preferences);
      check(history.size() == historySize, "menu rendering preserves chat history");
      System.out.println(
          "PERIHELION_SMOKE UI_THEME checks=" + checks + " fonts=3 scales=1,1.05 PASS");
    } finally {
      display.invoke(game, new Object[] {null});
      runtime.modules().setSetting("clickgui", preferences.font, oldFont);
      runtime.modules().setSetting("clickgui", preferences.scale, oldScale);
      runtime.modules().setSetting("clickgui", preferences.layout, oldLayout);
      setPlayers(runtime, oldPlayers);
      runtime.modules().setEnabled("hud", oldHud);
      if (java.nio.file.Files.isRegularFile(runtime.config().store().profilePath(preset)))
        runtime.config().store().deleteProfile(preset).get(5, TimeUnit.SECONDS);
      display.invoke(game, original);
    }
  }

  private static void textStyles(
      ClientRuntime runtime,
      Object game,
      Object menu,
      Method display,
      GuiFonts fonts,
      ClickGuiModule preferences)
      throws Exception {
    boolean oldCustom = preferences.customTextStyle.get();
    ClickGuiModule.TextWeight oldWeight = preferences.textWeight.get();
    boolean oldSections = preferences.boldSections.get();
    perihelion.core.setting.ColorValue oldSection = preferences.sectionText.get(),
        oldSelected = preferences.selectionText.get(),
        oldHighlight = preferences.selectionHighlight.get(),
        oldMuted = preferences.mutedText.get();
    try {
      runtime.modules().setSetting("clickgui", preferences.customTextStyle, true);
      runtime
          .modules()
          .setSetting(
              "clickgui",
              preferences.sectionText,
              perihelion.core.setting.ColorValue.rgb(255, 190, 90));
      runtime
          .modules()
          .setSetting(
              "clickgui",
              preferences.selectionText,
              perihelion.core.setting.ColorValue.rgb(120, 240, 210));
      runtime
          .modules()
          .setSetting(
              "clickgui",
              preferences.selectionHighlight,
              perihelion.core.setting.ColorValue.rgba(80, 120, 220, 105));
      runtime
          .modules()
          .setSetting(
              "clickgui",
              preferences.mutedText,
              perihelion.core.setting.ColorValue.rgb(150, 160, 180));
      for (ClickGuiModule.Font choice :
          new ClickGuiModule.Font[] {
            ClickGuiModule.Font.SANS, ClickGuiModule.Font.MINECRAFT, ClickGuiModule.Font.MONO
          }) {
        runtime.modules().setSetting("clickgui", preferences.font, choice);
        runtime.modules().setSetting("clickgui", preferences.scale, 1.05);
        runtime
            .modules()
            .setSetting("clickgui", preferences.textWeight, ClickGuiModule.TextWeight.BOLD);
        display.invoke(game, menu);
        float viewWidth = field(menu.getClass(), "viewWidth").getFloat(menu);
        method(menu.getClass(), new Class<?>[] {float.class, float.class, int.class}, "click")
            .invoke(menu, viewWidth - 70, 14f, 0);
        GuiFonts.Face regular, bold;
        try (RenderState ignored = new RenderState().open()) {
          regular = fonts.get(choice);
          bold = fonts.get(choice, true);
        }
        check(
            bold != regular && bold == fonts.get(choice, true),
            "weight variants are distinct and cached: " + choice);
        check(bold.height() <= 13, "bold body text fits existing module row: " + choice);
        check(
            bold.width("Wi\u03a9\u0416")
                == bold.width("W") + bold.width("i") + bold.width("\u03a9") + bold.width("\u0416"),
            "bold Unicode advances match glyph runs: " + choice);
        HudChatProbe.captureWorld(
            game, "ui-custom-text-bold-" + choice.name().toLowerCase(Locale.ROOT) + ".png");
        Object canvas = field(menu.getClass(), "canvas").get(menu);
        check(
            bold.width("Configs") + 8 <= field(menu.getClass(), "configTabWidth").getFloat(menu),
            "Configs tab fits chosen bold font: " + choice);
        check(
            bold.width("Modules") + 8 <= field(menu.getClass(), "moduleTabWidth").getFloat(menu),
            "Modules tab fits chosen bold font: " + choice);
        check(
            field(menu.getClass(), "hudTabWidth").getFloat(menu)
                    == field(menu.getClass(), "moduleTabWidth").getFloat(menu)
                && field(menu.getClass(), "configTabWidth").getFloat(menu)
                    == field(menu.getClass(), "moduleTabWidth").getFloat(menu),
            "toolbar widths stay equal across font weights: " + choice);
        check(
            field(canvas.getClass(), "font").get(canvas) == bold,
            "body uses selected weight: " + choice);
        check(
            field(canvas.getClass(), "sectionText").getInt(canvas) == 0xffffbe5a,
            "section color resolves independently");
        check(
            field(canvas.getClass(), "selectedText").getInt(canvas) == 0xff78f0d2,
            "selected text color resolves independently");
        check(
            field(canvas.getClass(), "selection").getInt(canvas) == 0x695078dc,
            "highlight preserves user alpha");
        check(
            field(canvas.getClass(), "muted").getInt(canvas) == 0xff96a0b4,
            "helper text resolves independently");
        runtime
            .modules()
            .setSetting("clickgui", preferences.textWeight, ClickGuiModule.TextWeight.REGULAR);
        runtime.modules().setSetting("clickgui", preferences.boldSections, true);
        HudChatProbe.captureWorld(
            game, "ui-custom-text-sections-" + choice.name().toLowerCase(Locale.ROOT) + ".png");
        check(
            field(canvas.getClass(), "font").get(canvas) == regular
                && field(canvas.getClass(), "sectionFont").get(canvas) == bold,
            "bold sections are independent of regular body: " + choice);
        check(
            GL11.glGetError() == GL11.GL_NO_ERROR,
            "custom text styles restore GL state: " + choice);
      }
      fonts.close();
      check(
          ((Map<?, ?>) field(fonts.getClass(), "boldAtlases").get(fonts)).isEmpty(),
          "bold atlases are released with GUI fonts");
      check(
          ((Map<?, ?>) field(fonts.getClass(), "atlases").get(fonts)).isEmpty(),
          "regular atlases are released with GUI fonts");
    } finally {
      runtime.modules().setSetting("clickgui", preferences.customTextStyle, oldCustom);
      runtime.modules().setSetting("clickgui", preferences.textWeight, oldWeight);
      runtime.modules().setSetting("clickgui", preferences.boldSections, oldSections);
      runtime.modules().setSetting("clickgui", preferences.sectionText, oldSection);
      runtime.modules().setSetting("clickgui", preferences.selectionText, oldSelected);
      runtime.modules().setSetting("clickgui", preferences.selectionHighlight, oldHighlight);
      runtime.modules().setSetting("clickgui", preferences.mutedText, oldMuted);
    }
  }

  private static void chatGate(Object bus, Object game, boolean hidden) throws Exception {
    Class<?> resolutionType = Class.forName("net.minecraft.client.gui.ScaledResolution");
    Object resolution = resolutionType.getConstructor(game.getClass()).newInstance(game);
    Object parent =
        RenderGameOverlayEvent.class
            .getConstructor(float.class, resolutionType)
            .newInstance(0f, resolution);
    Object event =
        RenderGameOverlayEvent.Chat.class
            .getConstructor(RenderGameOverlayEvent.class, int.class, int.class)
            .newInstance(parent, 0, 0);
    boolean canceled =
        (Boolean)
            bus.getClass()
                .getMethod(
                    "post", Class.forName("net.minecraftforge.fml.common.eventhandler.Event"))
                .invoke(bus, event);
    check(canceled == hidden, "Forge chat gate agrees with active menu/chat screen");
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void setPlayers(ClientRuntime runtime, Object value) {
    Setting setting = runtime.modules().require("hud").module().settings().get("players");
    runtime.modules().setSetting("hud", setting, value);
  }

  private static void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }
}
