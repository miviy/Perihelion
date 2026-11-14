package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;

import java.lang.reflect.*;
import org.lwjgl.opengl.GL11;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.integration.minecraft189.gui.*;
import perihelion.integration.minecraft189.gui.font.GuiFonts;
import perihelion.integration.minecraft189.gui.widget.SettingControl;
import perihelion.integration.minecraft189.gui.widget.UiCanvas;

/** Every font and weight through the packaged renderer and native measured setting controls. */
public final class GuiFontProbe {
  private static int checks;

  public static void title(ClientRuntime runtime, Object modern) throws Exception {
    checks = 0;
    ClickGuiModule p = (ClickGuiModule) runtime.modules().require("clickgui").module();
    ClickGuiModule.Font saved = p.modernFont.get();
    boolean bold = p.bold.get();
    GuiFonts fonts = (GuiFonts) field(modern.getClass(), "fonts").get(modern);
    try {
      MinecraftFontProbe.run();
      resourceLifetime(modern);
      draw(modern);
      formattedLabels(modern, fonts);
      for (ClickGuiModule.Font choice : ClickGuiModule.Font.values())
        for (boolean weight : new boolean[] {false, true}) {
          runtime.modules().setSetting("clickgui", p.modernFont, choice);
          runtime.modules().setSetting("clickgui", p.bold, weight);
          draw(modern);
          UiCanvas canvas = (UiCanvas) field(modern.getClass(), "canvas").get(modern);
          GuiFonts.Face face = canvas.font, title = canvas.sectionFont;
          if (choice == ClickGuiModule.Font.MINECRAFT) {
            check(
                face == fonts.minecraft(weight, canvas.pixelScale),
                "Minecraft keeps native glyphs and selected weight");
            check(title == face, "Minecraft category title keeps native glyphs");
          } else {
            check(
                face
                    == fonts.pixelFace(
                        choice, weight, GuiTheme.MODERN_BODY_SCALE, canvas.pixelScale),
                "Dropdown labels use final-size selected font and weight: "
                    + choice
                    + " "
                    + weight);
            check(
                field(field(title.getClass(), "key").get(title).getClass(), "choice")
                        .get(field(title.getClass(), "key").get(title))
                    == choice,
                "Category title uses the selected family");
            Object titleKey = field(title.getClass(), "key").get(title);
            check(
                field(titleKey.getClass(), "bold").getBoolean(titleKey) == weight,
                "Category title follows the same Bold preference as body text");
            Object key = field(face.getClass(), "key").get(face);
            check(
                Math.abs(field(key.getClass(), "pixels").getFloat(key) - canvas.pixelScale)
                    < .0001f,
                "Glyph raster scale includes the actual menu transform");
            check(
                Math.abs(
                        face.width("Wi 012") * canvas.pixelScale
                            - Math.round(face.width("Wi 012") * canvas.pixelScale))
                    < .001f,
                "Hinted glyph advances land on physical pixels");
          }
          check(title.width("Movement") <= 83.01f, "Title fits beside its icon: " + choice);
          check(
              canvas.font == method(modern.getClass(), "editorFont").invoke(modern),
              "Inline/search editing shares selected drawing metrics");
          check(canvas.font.width("Wi 012") > 0, "Selected font has usable metrics");
          check(
              canvas.font.width("Hud") + 10 <= number(modern, "hudTabWidth"),
              "HUD button fits the selected weight");
          check(
              canvas.font.width("Modules") + 10 <= number(modern, "moduleTabWidth"),
              "Modules button fits the selected weight");
          check(
              canvas.font.width("Configs") + 10 <= number(modern, "configTabWidth"),
              "Configs button fits the selected weight");

          check(
              GL11.glGetError() == GL11.GL_NO_ERROR, "Every font/weight renders without GL errors");
          if ((!weight
                  && (choice == ClickGuiModule.Font.ROBOTO
                      || choice == ClickGuiModule.Font.INTER
                      || choice == ClickGuiModule.Font.ROBOTO_CONDENSED
                      || choice == ClickGuiModule.Font.MONASPACE))
              || choice == ClickGuiModule.Font.MINECRAFT
              || choice == ClickGuiModule.Font.NERD_MONO)
            ProductionHudProbe.capture(
                "gui-font-"
                    + choice.name().toLowerCase()
                    + "-"
                    + (weight ? "bold" : "regular")
                    + ".png");
        }
      runtime.modules().setSetting("clickgui", p.modernFont, ClickGuiModule.Font.INTER);
      runtime.modules().setSetting("clickgui", p.bold, false);
      draw(modern);
      SettingControl font =
          (SettingControl)
              method(
                      GuiHookProbe.class,
                      new Class<?>[] {Object.class, String.class, String.class},
                      "findControl")
                  .invoke(null, modern, "clickgui", "modern-font");
      font.press(number(font, "x") + 3, number(font, "y") + 3, 0);
      draw(modern);
      java.util.List<?> options =
          (java.util.List<?>) field(font.getClass(), "optionLines").get(font);
      UiCanvas canvas = (UiCanvas) field(modern.getClass(), "canvas").get(modern);
      for (Object option : options)
        for (Object line : (java.util.List<?>) option)
          check(
              canvas.font.width((String) line) <= number(font, "w") - 25 + .01f,
              "Choice labels reserve a separate checkbox column");
      ProductionHudProbe.capture("gui-font-selector.png");
      font.press(number(font, "x") + 3, number(font, "y") + 3, 1);
      check(
          p.modernFont.metadata().options().size() == 13,
          "Thirteen actual font choices are selectable");
      System.out.println(
          "[PERIHELION_SMOKE] GUI_FONTS_TITLE PASS checks=" + checks + " choices=13 weights=2");
    } finally {
      runtime.modules().setSetting("clickgui", p.modernFont, saved);
      runtime.modules().setSetting("clickgui", p.bold, bold);
      draw(modern);
    }
  }

  public static void hud(ClientRuntime runtime, Object editor, Object preview) throws Exception {
    checks = 0;
    ClickGuiModule p = (ClickGuiModule) runtime.modules().require("clickgui").module();
    ClickGuiModule.Font saved = p.modernFont.get();
    boolean savedBold = p.bold.get();
    try {
      Object text = field(preview.getClass(), "text").get(preview);
      GuiFonts fonts = (GuiFonts) field(text.getClass(), "fonts").get(text);
      for (ClickGuiModule.Font choice :
          new ClickGuiModule.Font[] {
            ClickGuiModule.Font.ROBOTO_CONDENSED, ClickGuiModule.Font.INTER,
            ClickGuiModule.Font.NERD_MONO, ClickGuiModule.Font.MINECRAFT
          })
        for (boolean weight : new boolean[] {false, true}) {
          runtime.modules().setSetting("clickgui", p.modernFont, choice);
          runtime.modules().setSetting("clickgui", p.bold, weight);
          draw(editor);
          GuiFonts.Face face = (GuiFonts.Face) method(text.getClass(), "face").invoke(text);
          check(
              face
                  == (choice == ClickGuiModule.Font.MINECRAFT
                      ? fonts.minecraft(weight, number(text, "rasterPixels"))
                      : fonts.pixelFace(choice, weight, 1.35f, number(text, "rasterPixels"))),
              "HUD uses final-size selected family and Bold: " + choice);
          String label = "Wi§c colored§r reset";
          if (choice == ClickGuiModule.Font.MINECRAFT) {
            UiCanvas editorCanvas = (UiCanvas) field(editor.getClass(), "canvas").get(editor);
            check(
                editorCanvas.font.width("Elements") + 8 <= 59,
                "Minecraft Elements label fits its unchanged toolbar button");
            int measured =
                (Integer)
                    method(text.getClass(), new Class<?>[] {String.class}, "width")
                        .invoke(text, label);
            check(
                measured == (int) Math.ceil(face.width(label)),
                "Bold native formatted HUD measurement matches drawing");
          }
          check(GL11.glGetError() == GL11.GL_NO_ERROR, "HUD font/weight renders cleanly");
          if (weight)
            ProductionHudProbe.capture("hud-font-" + choice.name().toLowerCase() + "-bold.png");
        }
      System.out.println("[PERIHELION_SMOKE] GUI_FONTS_HUD PASS checks=" + checks);
    } finally {
      runtime.modules().setSetting("clickgui", p.modernFont, saved);
      runtime.modules().setSetting("clickgui", p.bold, savedBold);
      draw(editor);
    }
  }

  private static void resourceLifetime(Object modern) throws Exception {
    Object game =
        method(Class.forName("net.minecraft.client.Minecraft"), "getMinecraft", "func_71410_x")
            .invoke(null);
    Object manager = method(game.getClass(), "getTextureManager", "func_110434_K").invoke(game);
    java.util.Map<?, ?> textures =
        (java.util.Map<?, ?>)
            field(manager.getClass(), "mapTextureObjects", "field_110585_a").get(manager);
    int baseline = textures.size();
    try (perihelion.integration.minecraft189.render.RenderState ignored =
        new perihelion.integration.minecraft189.render.RenderState().open()) {
      for (int i = 0; i < 6; i++) {
        GuiFonts temporary = new GuiFonts();
        int normal = 0, weight = 0;
        try {
          GuiFonts.Face a = temporary.get(ClickGuiModule.Font.INTER, false),
              b = temporary.get(ClickGuiModule.Font.INTER, true);
          normal = field(a.getClass(), "texture").getInt(a);
          weight = field(b.getClass(), "texture").getInt(b);
          check(
              GL11.glIsTexture(normal) && GL11.glIsTexture(weight),
              "Temporary font atlases allocate valid GPU textures");
          check(
              textures.size() == baseline,
              "Font atlases retain no TextureManager CPU pixel arrays");
          method(
                  Class.forName("net.minecraft.client.renderer.GlStateManager"),
                  new Class<?>[] {int.class},
                  "bindTexture",
                  "func_179144_i")
              .invoke(null, weight);
          check(
              GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER)
                  == GL11.GL_LINEAR,
              "Font atlas samples its full-resolution source");
          check(
              GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH) == 1024
                  && GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 1, GL11.GL_TEXTURE_WIDTH)
                      == 0,
              "Full-resolution glyph texture has no lower-resolution mip");

        } finally {
          temporary.close();
        }
        check(
            !GL11.glIsTexture(normal) && !GL11.glIsTexture(weight),
            "Closing font owner deletes both GPU atlases");
      }
      GuiFonts temporary = new GuiFonts();
      java.util.List<Integer> residentIds = new java.util.ArrayList<>();
      try {
        GuiFonts.Face initial =
            temporary.pixelFace(ClickGuiModule.Font.ROBOTO_CONDENSED, false, 1.3f, 1.6f);
        check(
            initial == temporary.pixelFace(ClickGuiModule.Font.ROBOTO_CONDENSED, false, 1.3f, 1.6f),
            "Warmed pixel font reuses its face and raster");
        int first = pixelTexture(initial);
        check(GL11.glIsTexture(first), "Final-size font owns a valid GPU raster");
        check(
            GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER)
                    == GL11.GL_NEAREST
                && GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER)
                    == GL11.GL_NEAREST,
            "Final-size text is sampled one-to-one without a blur filter");
        for (int i = 0; i < 12; i++)
          temporary.pixelFace(ClickGuiModule.Font.INTER, i % 2 == 0, 1.3f, 1 + i * .1f);
        check(pixelTexture(initial) == 0, "Eviction releases the oldest pixel raster");
        initial.draw("Pixel reuse", 0, 0, 0xffffffff);
        check(
            GL11.glIsTexture(pixelTexture(initial)),
            "An evicted referenced face safely restores its raster");
        java.util.Map<?, ?> cache =
            (java.util.Map<?, ?>) field(temporary.getClass(), "pixelAtlases").get(temporary);
        check(cache.size() <= 8, "Pixel raster residency remains bounded across scale changes");
        highDpi(modern, temporary);
        check(
            ((java.util.Map<?, ?>) field(temporary.getClass(), "pixelFaces").get(temporary)).size()
                <= 32,
            "Logical face wrappers remain bounded across resolution changes");
        check(
            field(temporary.getClass(), "residentPixelBytes").getLong(temporary)
                <= 96L * 1024 * 1024,
            "High DPI font textures stay within the GPU byte budget");
        for (Object atlas : cache.values())
          residentIds.add(field(atlas.getClass(), "texture").getInt(atlas));
        check(textures.size() == baseline, "Pixel fonts retain no TextureManager CPU images");
      } finally {
        temporary.close();
      }
      for (int id : residentIds)
        check(!GL11.glIsTexture(id), "Closing owner deletes each resident pixel raster");
    }
  }

  private static int pixelTexture(Object face) throws Exception {
    Object atlas = field(face.getClass(), "atlas").get(face);
    return field(atlas.getClass(), "texture").getInt(atlas);
  }

  private static void highDpi(Object modern, GuiFonts fonts) throws Exception {
    float vanillaPixels = number(modern, "pixels") / number(modern, "scale");
    for (boolean bold : new boolean[] {false, true}) {
      for (float pixels : new float[] {1, 1.25f, 2, 3, 6, 9, 18, 30, 60}) {
        GuiFonts.Face face =
            fonts.pixelFace(ClickGuiModule.Font.ROBOTO_CONDENSED, bold, 1.3f, pixels);
        Object atlas = field(face.getClass(), "atlas").get(face);
        Object key = field(face.getClass(), "key").get(face);
        int pointSize = field(key.getClass(), "size").getInt(key);
        if (pixels == 18)
          check(pointSize > 128, "High DPI glyphs rasterize above the old 64px cap");
        check(
            face.width("Movement") > 25 && face.width("Movement") < 70,
            "Logical label width stays stable at physical scale " + pixels);
        check(
            face.height() > 8 && face.height() < 20,
            "Logical line height stays stable at physical scale " + pixels);
        GL11.glClearColor(.12f, .13f, .14f, 1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glPushMatrix();
        try {
          GL11.glScalef(pixels / vanillaPixels, pixels / vanillaPixels, 1);
          face.draw("Wi0", 1, 1, 0xffffffff);
        } finally {
          GL11.glPopMatrix();
        }
        check(GL11.glIsTexture(pixelTexture(face)), "High DPI face owns a real texture");
        check(
            GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH)
                == field(atlas.getClass(), "textureWidth").getInt(atlas),
            "Packed glyph atlas uploads its declared physical width");
        check(GL11.glGetError() == GL11.GL_NO_ERROR, "High DPI glyphs render without GL errors");
        if (pixels == 18 || pixels == 30)
          ProductionHudProbe.capture(
              "gui-font-scale-" + (int) pixels + (bold ? "-bold" : "-regular") + ".png");
      }
    }
    GuiFonts.Face first = fonts.pixelFace(ClickGuiModule.Font.INTER, false, 1.3f, 2);
    GuiFonts.Face nearby = fonts.pixelFace(ClickGuiModule.Font.INTER, false, 1.3f, 2.001f);
    check(
        field(first.getClass(), "atlas").get(first)
            == field(nearby.getClass(), "atlas").get(nearby),
        "Nearby scales sharing a physical point size reuse the same glyph raster");
  }

  private static float number(Object value, String key) throws Exception {
    return field(value.getClass(), key).getFloat(value);
  }

  private static void formattedLabels(Object modern, GuiFonts fonts) throws Exception {
    UiCanvas canvas = (UiCanvas) field(modern.getClass(), "canvas").get(modern);
    try (perihelion.integration.minecraft189.render.RenderState ignored =
        new perihelion.integration.minecraft189.render.RenderState().open()) {
      GuiFonts.Face regular =
          fonts.pixelFace(ClickGuiModule.Font.ROBOTO_CONDENSED, false, 1.3f, canvas.pixelScale);
      GuiFonts.Face bold =
          fonts.pixelFace(ClickGuiModule.Font.ROBOTO_CONDENSED, true, 1.3f, canvas.pixelScale);
      float menuScale = number(modern, "scale");
      GL11.glScalef(menuScale, menuScale, 1);
      check(
          Math.abs(regular.width("Wi colored") - regular.width("§6Wi colored")) < .001f,
          "Color formatting keeps selected-family glyph advances");
      check(
          Math.abs(
                  regular.width("Wi §lBold§r reset")
                      - (regular.width("Wi ") + bold.width("Bold") + regular.width(" reset")))
              < .001f,
          "Inline Bold measures the real selected-family bold face");
      check(
          Math.abs(bold.width("Wi colored") - bold.width("§6Wi §rcolored")) < .001f,
          "Global Bold survives color and reset formatting");
      regular.draw("Perihelion  §6Selected family  §lBold§r regular", 12, 40, 0xfff2f2f2);
      regular.draw("Roboto Condensed  §nUnderline§r  §oItalic§r  §mStrike", 12, 58, 0xfff2f2f2);
      check(
          GL11.glGetError() == GL11.GL_NO_ERROR,
          "Selected-family formatting renders without GL errors");
      ProductionHudProbe.capture("gui-font-formatted.png");
    }
  }

  private static void draw(Object value) throws Exception {
    method(NewVisualsProbe.class, new Class<?>[] {Object.class}, "draw").invoke(null, value);
  }

  private static void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }
}
