package perihelion.integration.minecraft189.gui.font;

import static org.junit.Assert.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;
import org.junit.Test;
import perihelion.integration.minecraft189.gui.ClickGuiModule;

public class PixelFontScaleTest {
  @Test
  public void highDpiTextKeepsItsLogicalSizeIncludingRealBoldAndRasterOverflow() throws Exception {
    for (ClickGuiModule.Font font :
        new ClickGuiModule.Font[] {
          ClickGuiModule.Font.ROBOTO_CONDENSED, ClickGuiModule.Font.NERD_MONO
        })
      for (boolean bold : new boolean[] {false, true})
        for (float factor : new float[] {1.2f, 1.5f}) {
          GuiFonts owner = new GuiFonts();
          GuiFonts.Face atSix = metrics(owner, font, bold, factor, 6);
          for (float pixels : new float[] {18, 30}) {
            GuiFonts.Face face = metrics(owner, font, bold, factor, pixels);
            float widthRatio = face.width("Movement Wi 012345") / atSix.width("Movement Wi 012345");
            assertEquals("Logical width stays stable at high DPI", 1, widthRatio, .035f);
            assertEquals(
                "Logical line height stays stable at high DPI",
                1,
                face.height() / atSix.height(),
                .06f);
            int points = intField(field(face, "key"), "size");
            if (pixels == 18)
              assertTrue("High DPI uses vector glyphs above the old 64px cap", points > 64);
          }
          assertTrue(((Map<?, ?>) field(owner, "pixelAtlases")).isEmpty());
          assertEquals(0, ((Number) field(owner, "residentPixelBytes")).longValue());
        }
  }

  @Test
  public void nearbyLayoutScalesShareGlyphMetricsRatherThanRebuildingTheSameRaster()
      throws Exception {
    GuiFonts owner = new GuiFonts();
    GuiFonts.Face first = metrics(owner, ClickGuiModule.Font.ROBOTO_CONDENSED, false, 1.3f, 1.6f);
    Object atlas = field(first, "atlas");
    for (int i = 0; i < 32; i++) {
      GuiFonts.Face nearby =
          metrics(owner, ClickGuiModule.Font.ROBOTO_CONDENSED, false, 1.3f, 1.6f + i * .0001f);
      assertSame(
          "Equal final raster size shares its atlas across viewport rounding",
          atlas,
          field(nearby, "atlas"));
    }
    assertEquals(1, ((Map<?, ?>) field(owner, "pixelMetrics")).size());
    assertEquals(0, ((Map<?, ?>) field(owner, "pixelAtlases")).size());
  }

  @Test
  public void glyphMetricCacheRemainsBoundedWithoutAllocatingLargeImages() throws Exception {
    GuiFonts owner = new GuiFonts();
    for (int points = 96; points < 136; points++) {
      metrics(owner, ClickGuiModule.Font.ROBOTO_CONDENSED, false, points / 7f, 1);
      assertTrue(((Map<?, ?>) field(owner, "pixelMetrics")).size() <= 32);
    }
    assertTrue(((Map<?, ?>) field(owner, "pixelAtlases")).isEmpty());
    assertEquals(0, ((Number) field(owner, "residentPixelBytes")).longValue());
  }

  private static GuiFonts.Face metrics(
      GuiFonts owner, ClickGuiModule.Font font, boolean bold, float factor, float pixels)
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
            font, bold, false, PixelFontRaster.fontPixels(factor, pixels), pixels, factor);
    Constructor<?> face =
        Class.forName(GuiFonts.class.getName() + "$PixelFace")
            .getDeclaredConstructor(GuiFonts.class, key, boolean.class);
    face.setAccessible(true);
    return (GuiFonts.Face) face.newInstance(owner, value, false);
  }

  private static Object field(Object owner, String name) throws Exception {
    Field field = owner.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return field.get(owner);
  }

  private static int intField(Object owner, String name) throws Exception {
    return ((Number) field(owner, name)).intValue();
  }
}
