package perihelion.integration.minecraft189.gui.font;

/** White coverage masks preserve stroke contrast during atlas filtering. No raster is retained. */
final class FontAtlasPixels {
  private FontAtlasPixels() {}

  static void whiteCoverage(int[] pixels, int width, int height) {
    if (pixels == null || width <= 0 || height <= 0 || (long) width * height != pixels.length)
      throw new IllegalArgumentException("Font raster dimensions do not match its pixels");
    // Linear sampling should interpolate alpha coverage, never black RGB around a white glyph.
    for (int i = 0; i < pixels.length; i++) pixels[i] |= 0x00ffffff;
  }
}
