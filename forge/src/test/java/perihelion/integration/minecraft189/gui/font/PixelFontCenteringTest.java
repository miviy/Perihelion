package perihelion.integration.minecraft189.gui.font;

import static org.junit.Assert.*;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.junit.Test;
import perihelion.integration.minecraft189.gui.ClickGuiModule;

public class PixelFontCenteringTest {
  @Test
  public void buttonInkBoundsMatchVisibleCapitalAndDescenderGlyphsAtDifferentDpi()
      throws Exception {
    for (boolean bold : new boolean[] {false, true})
      for (float pixels : new float[] {2, 3, 9}) {
        GuiFonts owner = new GuiFonts();
        int points = PixelFontRaster.fontPixels(1.3f, pixels);
        GuiFonts.Face face = metrics(owner, bold, points, pixels);
        Method ink = face.getClass().getDeclaredMethod("centeredInk", String.class);
        ink.setAccessible(true);
        Font font = BundledFonts.load(ClickGuiModule.Font.ROBOTO_CONDENSED, bold, points);
        BufferedImage sample = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = sample.createGraphics();
        try {
          graphics.setFont(font);
          graphics.setRenderingHint(
              RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
          graphics.setRenderingHint(
              RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
          for (String label :
              new String[] {"MODS", "Singleplayer", "Client Settings", "  Options  "}) {
            Rectangle expected =
                font.createGlyphVector(graphics.getFontRenderContext(), label)
                    .getPixelBounds(
                        graphics.getFontRenderContext(), 0, graphics.getFontMetrics().getAscent());
            float[] actual = (float[]) ink.invoke(face, label);
            assertNotNull(label, actual);
            assertEquals(label + " left side bearing", expected.x, actual[0] * pixels, .001f);
            assertEquals(label + " top ink", expected.y, actual[1] * pixels, .001f);
            assertEquals(
                label + " right ink", expected.x + expected.width, actual[2] * pixels, .001f);
            assertEquals(
                label + " descender extent",
                expected.y + expected.height,
                actual[3] * pixels,
                .001f);
          }
        } finally {
          graphics.dispose();
          sample.flush();
        }
      }
  }

  private static GuiFonts.Face metrics(GuiFonts owner, boolean bold, int points, float pixels)
      throws Exception {
    Class<?> key = Class.forName(GuiFonts.class.getName() + "$PixelKey");
    Constructor<?> makeKey =
        key.getDeclaredConstructor(
            ClickGuiModule.Font.class,
            boolean.class,
            boolean.class,
            int.class,
            float.class,
            float.class);
    makeKey.setAccessible(true);
    Object value =
        makeKey.newInstance(
            ClickGuiModule.Font.ROBOTO_CONDENSED, bold, false, points, pixels, 1.3f);
    Constructor<?> face =
        Class.forName(GuiFonts.class.getName() + "$PixelFace")
            .getDeclaredConstructor(GuiFonts.class, key, boolean.class);
    face.setAccessible(true);
    return (GuiFonts.Face) face.newInstance(owner, value, false);
  }
}
