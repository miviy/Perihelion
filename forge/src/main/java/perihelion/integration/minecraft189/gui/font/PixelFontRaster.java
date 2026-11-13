package perihelion.integration.minecraft189.gui.font;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;

/** Hinted grayscale glyphs packed at their final framebuffer size. Contains no GL objects. */
final class PixelFontRaster implements AutoCloseable {
  static final int MAX_RASTER_PIXELS = 256, MAX_ATLAS_EDGE = 4096;
  final BufferedImage image;
  final int[] advances = new int[256], widths = new int[256], heights = new int[256];
  final int[] glyphX = new int[256], glyphY = new int[256];
  final int[] offsetX = new int[256], offsetY = new int[256];
  final int atlasWidth, atlasHeight, height, ascent, inkTop, inkHeight;
  final int padding = 3;

  static int requestedPixels(float factor, float pixels) {
    if (!Float.isFinite(factor) || factor <= 0 || !Float.isFinite(pixels) || pixels <= 0)
      throw new IllegalArgumentException("Font scale must be finite and positive");
    double requested = 7.0 * factor * pixels;
    if (requested > Integer.MAX_VALUE)
      throw new IllegalArgumentException("Font scale exceeds supported coordinates");
    return Math.max(8, (int) Math.round(requested));
  }

  static int fontPixels(float factor, float pixels) {
    return Math.min(MAX_RASTER_PIXELS, requestedPixels(factor, pixels));
  }

  /** The practical raster ceiling must never make the logical text smaller. */
  static float unit(float factor, float pixels, int rasterPixels) {
    if (rasterPixels < 1) throw new IllegalArgumentException("Raster size must be positive");
    return requestedPixels(factor, pixels) / (float) rasterPixels / pixels;
  }

  PixelFontRaster(Font font) {
    this(font, true);
  }

  /** Metrics-only construction is used by layout and creates no atlas image. */
  PixelFontRaster(Font font, boolean rasterize) {
    BufferedImage sample = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    Graphics2D measure = sample.createGraphics();
    int measuredHeight, measuredAscent, measuredTop, measuredInk;
    try {
      configure(measure, font);
      FontMetrics metrics = measure.getFontMetrics();
      measuredHeight = metrics.getHeight();
      measuredAscent = metrics.getAscent();
      Rectangle cap =
          font.createGlyphVector(measure.getFontRenderContext(), "H")
              .getPixelBounds(measure.getFontRenderContext(), 0, metrics.getAscent());
      measuredTop = cap.y;
      measuredInk = cap.height;
      for (int c = 32; c < 256; c++) {
        advances[c] = metrics.charWidth((char) c);
        Rectangle bounds =
            font.createGlyphVector(measure.getFontRenderContext(), new char[] {(char) c})
                .getPixelBounds(measure.getFontRenderContext(), 0, metrics.getAscent());
        widths[c] = Math.max(1, bounds.width) + padding * 2;
        heights[c] = Math.max(1, bounds.height) + padding * 2;
        offsetX[c] = bounds.x - padding;
        offsetY[c] = bounds.y - padding;
      }
    } finally {
      measure.dispose();
      sample.flush();
    }
    height = measuredHeight;
    ascent = measuredAscent;
    inkTop = measuredTop;
    inkHeight = measuredInk;

    Integer[] order = new Integer[224];
    long area = 0;
    int widest = 0;
    for (int i = 0; i < order.length; i++) {
      int cp = order[i] = i + 32;
      area += (long) widths[cp] * heights[cp];
      widest = Math.max(widest, widths[cp]);
    }
    Arrays.sort(
        order,
        (a, b) -> {
          int heightOrder = Integer.compare(heights[b], heights[a]);
          return heightOrder != 0 ? heightOrder : Integer.compare(widths[b], widths[a]);
        });
    int width = Math.max(widest, Math.max(64, (int) Math.ceil(Math.sqrt(area) / 32) * 32));
    width = Math.min(MAX_ATLAS_EDGE, width);
    int packedHeight;
    do {
      packedHeight = pack(order, width);
      if (packedHeight <= MAX_ATLAS_EDGE) break;
      if (width == MAX_ATLAS_EDGE)
        throw new IllegalArgumentException("Font atlas exceeds texture limit");
      width = Math.min(MAX_ATLAS_EDGE, width + 128);
    } while (true);
    atlasWidth = width;
    atlasHeight = packedHeight;
    image =
        rasterize ? new BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB) : null;
    if (!rasterize) return;
    Graphics2D paint = image.createGraphics();
    try {
      configure(paint, font);
      paint.setColor(java.awt.Color.WHITE);
      for (int c = 32; c < 256; c++)
        // drawString may substitute control-range characters differently from createGlyphVector.
        // Paint the same glyph geometry whose pixel bounds were packed above.
        paint.drawGlyphVector(
            font.createGlyphVector(paint.getFontRenderContext(), new char[] {(char) c}),
            glyphX[c] - offsetX[c],
            glyphY[c] - offsetY[c] + ascent);
    } finally {
      paint.dispose();
    }
    FontAtlasPixels.whiteCoverage(
        ((DataBufferInt) image.getRaster().getDataBuffer()).getData(), atlasWidth, atlasHeight);
  }

  private int pack(Integer[] order, int width) {
    int x = 0, y = 0, shelf = 0;
    for (int cp : order) {
      if (widths[cp] > width) throw new IllegalArgumentException("Glyph exceeds atlas width");
      if (x + widths[cp] > width) {
        x = 0;
        y += shelf;
        shelf = 0;
      }
      glyphX[cp] = x;
      glyphY[cp] = y;
      shelf = Math.max(shelf, heights[cp]);
      x += widths[cp];
    }
    return y + shelf;
  }

  private static void configure(Graphics2D graphics, Font font) {
    graphics.setFont(font);
    graphics.setRenderingHint(
        RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    graphics.setRenderingHint(
        RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
  }

  @Override
  public void close() {
    if (image != null) image.flush();
  }
}
