package perihelion.integration.minecraft189.gui.font;

import static org.junit.Assert.*;

import org.junit.Test;

public class FontAtlasPixelsTest {
  @Test
  public void keepsEveryAlphaAndRemovesTransparentBlackFringes() {
    int[] pixels = {0, 0x40112233, 0x80aabbcc, 0xff000000};
    FontAtlasPixels.whiteCoverage(pixels, 2, 2);
    assertArrayEquals(new int[] {0x00ffffff, 0x40ffffff, 0x80ffffff, 0xffffffff}, pixels);
  }

  @Test
  public void thinStrokesAndAlignedCellsKeepTheirExactCoverage() {
    int[] pixels = new int[32], alpha = new int[32];
    for (int i = 0; i < pixels.length; i++) {
      alpha[i] = i % 8 < 4 ? 1 + i * 7 : 0;
      pixels[i] = alpha[i] << 24;
    }
    FontAtlasPixels.whiteCoverage(pixels, 8, 4);
    for (int i = 0; i < pixels.length; i++) {
      assertEquals(alpha[i], pixels[i] >>> 24);
      assertEquals(0x00ffffff, pixels[i] & 0xffffff);
    }
  }

  @Test
  public void rejectsInvalidDimensionsBeforeMutatingPixels() {
    int[] pixels = {0, 0, 0};
    assertThrows(IllegalArgumentException.class, () -> FontAtlasPixels.whiteCoverage(pixels, 3, 2));
    assertArrayEquals(new int[3], pixels);
    assertThrows(
        IllegalArgumentException.class, () -> FontAtlasPixels.whiteCoverage(pixels, -1, 3));
    assertThrows(IllegalArgumentException.class, () -> FontAtlasPixels.whiteCoverage(null, 1, 1));
  }
}
