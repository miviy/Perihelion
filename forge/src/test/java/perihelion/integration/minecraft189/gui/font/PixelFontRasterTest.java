package perihelion.integration.minecraft189.gui.font;

import static org.junit.Assert.*;

import java.awt.image.DataBufferInt;
import org.junit.Test;
import perihelion.integration.minecraft189.gui.ClickGuiModule;

public class PixelFontRasterTest {
  @Test
  public void allSelectableFamiliesAndWeightsStayInsideTheirCellsAtUiPixelSizes() {
    for (ClickGuiModule.Font font : ClickGuiModule.Font.values()) {
      if (font == ClickGuiModule.Font.MINECRAFT) continue;
      for (boolean bold : new boolean[] {false, true})
        for (int pixels : new int[] {11, 14, 24})
          try (PixelFontRaster raster =
              new PixelFontRaster(BundledFonts.load(font, bold, pixels))) {
            int[] image = ((DataBufferInt) raster.image.getRaster().getDataBuffer()).getData();
            assertTrue(raster.inkHeight > 0);
            for (int cp = 32; cp < 256; cp++) {
              assertTrue(font + " glyph advance", raster.advances[cp] >= 0);
              assertGlyphPadding(
                  font.name() + " bold=" + bold + " size=" + pixels, raster, image, cp);
            }
          }
    }
  }

  @Test
  public void smallReferenceFontHasOpaqueStemsAndGrayscaleEdgesInBothWeights() {
    for (boolean bold : new boolean[] {false, true})
      try (PixelFontRaster raster =
          new PixelFontRaster(BundledFonts.load(ClickGuiModule.Font.ROBOTO_CONDENSED, bold, 14))) {
        int[] image = ((DataBufferInt) raster.image.getRaster().getDataBuffer()).getData();
        boolean opaque = false, antialiased = false;
        int cp = 'H', x = raster.glyphX[cp], y = raster.glyphY[cp];
        for (int row = 0; row < raster.heights[cp]; row++)
          for (int col = 0; col < raster.widths[cp]; col++) {
            int argb = image[(y + row) * raster.atlasWidth + x + col], alpha = argb >>> 24;
            opaque |= alpha == 255;
            antialiased |= alpha > 0 && alpha < 255;
            assertEquals("Neutral edge RGB", 0xffffff, argb & 0xffffff);
          }
        assertTrue("Opaque stroke survives at the actual UI size", opaque);
        assertTrue("Curves and edges retain grayscale antialiasing", antialiased);
      }
  }

  @Test
  public void pixelSizesFollowEffectiveScaleWithBoundedLimitsAndRejectInvalidInputs() {
    assertEquals(15, PixelFontRaster.fontPixels(1.3f, 1.6f));
    assertEquals(8, PixelFontRaster.fontPixels(1, .5f));
    assertEquals(224, PixelFontRaster.fontPixels(4, 8));
    assertEquals(164, PixelFontRaster.fontPixels(1.3f, 18));
    assertEquals(252, PixelFontRaster.fontPixels(1.2f, 30));
    assertEquals(256, PixelFontRaster.fontPixels(1.5f, 30));
    assertEquals(315 / 256f / 30, PixelFontRaster.unit(1.5f, 30, 256), .000001f);
    assertThrows(IllegalArgumentException.class, () -> PixelFontRaster.fontPixels(0, 1));
    assertThrows(IllegalArgumentException.class, () -> PixelFontRaster.fontPixels(1, Float.NaN));
    assertThrows(IllegalArgumentException.class, () -> PixelFontRaster.fontPixels(1, -1));
  }

  @Test
  public void compactPackingPreservesLargeVectorGlyphsWithoutPowerOfTwoGridGrowth() {
    for (ClickGuiModule.Font font :
        new ClickGuiModule.Font[] {
          ClickGuiModule.Font.ROBOTO_CONDENSED, ClickGuiModule.Font.NERD_MONO
        })
      for (boolean bold : new boolean[] {false, true})
        for (int points : new int[] {11, 24, 64, 96, 160, 256})
          try (PixelFontRaster raster =
              new PixelFontRaster(BundledFonts.load(font, bold, points))) {
            int[] image = ((DataBufferInt) raster.image.getRaster().getDataBuffer()).getData();
            assertTrue(raster.atlasWidth <= PixelFontRaster.MAX_ATLAS_EDGE);
            assertTrue(raster.atlasHeight <= PixelFontRaster.MAX_ATLAS_EDGE);
            for (int cp = 32; cp < 256; cp++)
              assertGlyphPadding(
                  font.name() + " bold=" + bold + " size=" + points, raster, image, cp);
            if (points == 160) {
              // The old 16x16 square grid allocated 4096x4096 pixels at this point size.
              assertTrue(
                  "Compact 160px atlas stays below 24MiB",
                  (long) raster.atlasWidth * raster.atlasHeight * 4 < 24L * 1024 * 1024);
            }
            for (int a = 32; a < 256; a++)
              for (int b = a + 1; b < 256; b++)
                assertTrue(
                    "Packed glyph rectangles never overlap",
                    raster.glyphX[a] + raster.widths[a] <= raster.glyphX[b]
                        || raster.glyphX[b] + raster.widths[b] <= raster.glyphX[a]
                        || raster.glyphY[a] + raster.heights[a] <= raster.glyphY[b]
                        || raster.glyphY[b] + raster.heights[b] <= raster.glyphY[a]);
          }
  }

  @Test
  public void measuringLargeGlyphsCreatesNoImageAndMatchesPaintedPacking() {
    java.awt.Font font = BundledFonts.load(ClickGuiModule.Font.ROBOTO_CONDENSED, true, 160);
    try (PixelFontRaster metrics = new PixelFontRaster(font, false);
        PixelFontRaster painted = new PixelFontRaster(font)) {
      assertNull(metrics.image);
      assertEquals(painted.atlasWidth, metrics.atlasWidth);
      assertEquals(painted.atlasHeight, metrics.atlasHeight);
      assertArrayEquals(painted.advances, metrics.advances);
      assertArrayEquals(painted.glyphX, metrics.glyphX);
      assertArrayEquals(painted.glyphY, metrics.glyphY);
    }
  }

  private static void assertGlyphPadding(String font, PixelFontRaster raster, int[] image, int cp) {
    font += " cp=" + cp;
    int x = raster.glyphX[cp], y = raster.glyphY[cp], w = raster.widths[cp], h = raster.heights[cp];
    assertTrue(x >= 0 && y >= 0 && x + w <= raster.atlasWidth && y + h <= raster.atlasHeight);
    for (int col = 0; col < w; col++) {
      assertEquals(font + " top glyph edge", 0, image[y * raster.atlasWidth + x + col] >>> 24);
      assertEquals(
          font + " bottom glyph edge", 0, image[(y + h - 1) * raster.atlasWidth + x + col] >>> 24);
    }
    for (int row = 0; row < h; row++) {
      assertEquals(font + " left glyph edge", 0, image[(y + row) * raster.atlasWidth + x] >>> 24);
      assertEquals(
          font + " right glyph edge", 0, image[(y + row) * raster.atlasWidth + x + w - 1] >>> 24);
    }
  }
}
