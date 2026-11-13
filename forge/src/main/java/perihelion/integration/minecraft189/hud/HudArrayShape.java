package perihelion.integration.minecraft189.hud;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import perihelion.integration.minecraft189.gui.widget.UiCanvas;

/** A single rounded outline, rasterized once for the fill, blur coverage and outside shadow. */
final class HudArrayShape {
  final float width, height;
  private final float[] tops, heights;
  private final List<Point> outline;
  private float[] left, right;
  private float rasterPixels;

  private static final class Point {
    final float x, y;

    Point(float x, float y) {
      this.x = x;
      this.y = y;
    }
  }

  HudArrayShape(
      List<HudRenderer.ModuleRow> rows,
      int contentWidth,
      int rowHeight,
      int padding,
      int horizontalPadding,
      boolean leftAligned,
      float radius) {
    int count = Math.max(1, rows.size());
    width = contentWidth + padding * 2;
    height = count * rowHeight + padding * 2;
    tops = new float[count];
    heights = new float[count];
    float[] edges = new float[count];
    for (int i = 0; i < count; i++) {
      float w =
          Math.min(
                  contentWidth,
                  rows.isEmpty() ? contentWidth : rows.get(i).width + horizontalPadding * 2)
              + padding * 2;
      edges[i] = width - w;
      tops[i] = i * rowHeight + (i == 0 ? 0 : padding);
      heights[i] = rowHeight + (i == 0 ? padding : 0) + (i == count - 1 ? padding : 0);
    }
    List<Point> polygon = new ArrayList<>();
    polygon.add(new Point(edges[0], 0));
    polygon.add(new Point(width, 0));
    polygon.add(new Point(width, height));
    polygon.add(new Point(edges[count - 1], height));
    for (int i = count - 1; i > 0; i--) {
      polygon.add(new Point(edges[i], tops[i]));
      polygon.add(new Point(edges[i - 1], tops[i]));
    }
    if (leftAligned)
      for (int i = 0; i < polygon.size(); i++)
        polygon.set(i, new Point(width - polygon.get(i).x, polygon.get(i).y));
    simplify(polygon);
    outline = rounded(polygon, Math.max(0, radius));
  }

  private static void simplify(List<Point> points) {
    boolean changed = true;
    while (changed && points.size() > 3) {
      changed = false;
      for (int i = 0; i < points.size(); i++) {
        Point a = points.get((i + points.size() - 1) % points.size()),
            b = points.get(i),
            c = points.get((i + 1) % points.size());
        float cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x);
        if (Math.abs(cross) < .0001f) {
          points.remove(i);
          changed = true;
          break;
        }
      }
    }
  }

  private static List<Point> rounded(List<Point> polygon, float radius) {
    List<Point> result = new ArrayList<>();
    for (int i = 0; i < polygon.size(); i++) {
      Point a = polygon.get((i + polygon.size() - 1) % polygon.size()),
          b = polygon.get(i),
          c = polygon.get((i + 1) % polygon.size());
      float in = (float) Math.hypot(b.x - a.x, b.y - a.y),
          out = (float) Math.hypot(c.x - b.x, c.y - b.y);
      float r = Math.min(radius, Math.min(in, out) / 2);
      if (r <= .001f) {
        result.add(b);
        continue;
      }
      float ix = (b.x - a.x) / in,
          iy = (b.y - a.y) / in,
          ox = (c.x - b.x) / out,
          oy = (c.y - b.y) / out;
      float cx = b.x - ix * r + ox * r, cy = b.y - iy * r + oy * r;
      double start = Math.atan2(-oy, -ox), sweep = Math.copySign(Math.PI / 2, ix * oy - iy * ox);
      int segments = Math.max(4, (int) Math.ceil(r * 2));
      for (int j = 0; j <= segments; j++) {
        double angle = start + sweep * j / segments;
        result.add(new Point(cx + (float) Math.cos(angle) * r, cy + (float) Math.sin(angle) * r));
      }
    }
    return result;
  }

  private void span(float y, float[] output) {
    y = Math.max(.00001f, Math.min(height - .00001f, y));
    float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
    for (int i = 0; i < outline.size(); i++) {
      Point a = outline.get(i), b = outline.get((i + 1) % outline.size());
      if ((a.y <= y && b.y > y) || (b.y <= y && a.y > y)) {
        float x = a.x + (b.x - a.x) * (y - a.y) / (b.y - a.y);
        min = Math.min(min, x);
        max = Math.max(max, x);
      }
    }
    output[0] = min;
    output[1] = max;
  }

  float edge(int row, float y, boolean rightSide) {
    float[] range = new float[2];
    span(tops[row] + Math.max(.00001f, Math.min(heights[row] - .00001f, y)), range);
    return range[rightSide ? 1 : 0];
  }

  private void raster(float pixels) {
    if (left != null && rasterPixels == pixels) return;
    rasterPixels = pixels;
    int count = Math.max(1, (int) Math.ceil(height * pixels));
    left = new float[count];
    right = new float[count];
    float[] range = new float[2];
    for (int i = 0; i < count; i++) {
      span(height * (i + .5f) / count, range);
      left[i] = range[0];
      right[i] = range[1];
    }
  }

  void fill(UiCanvas canvas, float x, float y, int first, int second, boolean horizontal) {
    canvas.texture(false);
    GlStateManager.disableTexture2D();
    GlStateManager.shadeModel(GL11.GL_SMOOTH);
    raster(canvas.pixelScale);
    float feather = .65f / Math.max(.01f, canvas.pixelScale);
    GL11.glBegin(GL11.GL_QUADS);
    try {
      for (int i = 0; i < left.length; i++) {
        float y0 = height * i / left.length,
            y1 = height * (i + 1) / left.length,
            l = left[i],
            r = right[i];
        band(x, y, l - feather, l + feather, y0, y1, 0, 1, first, second, horizontal);
        band(x, y, l + feather, r - feather, y0, y1, 1, 1, first, second, horizontal);
        band(x, y, r - feather, r + feather, y0, y1, 1, 0, first, second, horizontal);
      }
    } finally {
      GL11.glEnd();
      GlStateManager.shadeModel(GL11.GL_FLAT);
    }
  }

  private void band(
      float x,
      float y,
      float l,
      float r,
      float top,
      float bottom,
      float a,
      float b,
      int first,
      int second,
      boolean horizontal) {
    vertex(x + l, y + top, l, top, a, first, second, horizontal);
    vertex(x + l, y + bottom, l, bottom, a, first, second, horizontal);
    vertex(x + r, y + bottom, r, bottom, b, first, second, horizontal);
    vertex(x + r, y + top, r, top, b, first, second, horizontal);
  }

  private void vertex(
      float x,
      float y,
      float localX,
      float localY,
      float coverage,
      int first,
      int second,
      boolean horizontal) {
    int color =
        HudAppearance.blend(
            first, second, horizontal ? localX / Math.max(1, width) : localY / Math.max(1, height));
    GL11.glColor4f(
        (color >> 16 & 255) / 255f,
        (color >> 8 & 255) / 255f,
        (color & 255) / 255f,
        (color >>> 24) / 255f * coverage);
    GL11.glVertex2f(x, y);
  }

  /** The blur shader receives exactly the same per-pixel coverage as the tinted background. */
  void geometry(float x, float y, float scale, float pixels) {
    raster(pixels * scale);
    float feather = .65f / Math.max(.01f, pixels * scale);
    GL11.glBegin(GL11.GL_QUADS);
    try {
      for (int i = 0; i < left.length; i++) {
        float top = y + height * i / left.length * scale,
            bottom = y + height * (i + 1) / left.length * scale;
        coverageQuad(
            x + (left[i] - feather) * scale, x + (left[i] + feather) * scale, top, bottom, 0, 1);
        coverageQuad(
            x + (left[i] + feather) * scale, x + (right[i] - feather) * scale, top, bottom, 1, 1);
        coverageQuad(
            x + (right[i] - feather) * scale, x + (right[i] + feather) * scale, top, bottom, 1, 0);
      }
    } finally {
      GL11.glEnd();
    }
  }

  private static void coverageQuad(float l, float r, float top, float bottom, float a, float b) {
    GL11.glColor4f(1, 1, 1, a);
    GL11.glVertex2f(l, top);
    GL11.glVertex2f(l, bottom);
    GL11.glColor4f(1, 1, 1, b);
    GL11.glVertex2f(r, bottom);
    GL11.glVertex2f(r, top);
  }

  void shadow(UiCanvas canvas, float x, float y, float scale, float spread, float opacity) {
    if (spread <= 0 || opacity <= 0) return;
    canvas.texture(false);
    GlStateManager.disableTexture2D();
    GlStateManager.shadeModel(GL11.GL_SMOOTH);
    raster(canvas.pixelScale * scale);
    float step = height / left.length;
    int slices = Math.max(1, (int) Math.ceil((height + spread * 2) / step));
    GL11.glBegin(GL11.GL_QUADS);
    try {
      for (int i = 0; i < slices; i++) {
        float y0 = -spread + (height + spread * 2) * i / slices,
            y1 = -spread + (height + spread * 2) * (i + 1) / slices;
        float yy = (y0 + y1) / 2;
        int at = Math.max(0, Math.min(left.length - 1, (int) (yy / step)));
        float l = left[at], r = right[at], outerL = Float.MAX_VALUE, outerR = -Float.MAX_VALUE;
        int start = Math.max(0, (int) ((yy - spread) / step) - 1),
            end = Math.min(left.length - 1, (int) ((yy + spread) / step) + 1);
        for (int j = start; j <= end; j++) {
          float dy = Math.abs(yy - (j + .5f) * step);
          if (dy > spread) continue;
          float reach = (float) Math.sqrt(Math.max(0, spread * spread - dy * dy));
          outerL = Math.min(outerL, left[j] - reach);
          outerR = Math.max(outerR, right[j] + reach);
        }
        if (outerL == Float.MAX_VALUE) continue;
        float fade = 1 - Math.max(0, Math.max(-yy, yy - height)) / spread,
            strength = opacity * fade * fade;
        for (int band = 0; band < 8; band++) {
          float f0 = band / 8f, f1 = (band + 1) / 8f;
          shadowQuad(
              x + (outerL + (l - outerL) * f0) * scale,
              x + (outerL + (l - outerL) * f1) * scale,
              y + y0 * scale,
              y + y1 * scale,
              strength * f0 * f0,
              strength * f1 * f1);
          shadowQuad(
              x + (r + (outerR - r) * f0) * scale,
              x + (r + (outerR - r) * f1) * scale,
              y + y0 * scale,
              y + y1 * scale,
              strength * (1 - f0) * (1 - f0),
              strength * (1 - f1) * (1 - f1));
        }
        if (yy < 0 || yy > height)
          shadowQuad(
              x + l * scale, x + r * scale, y + y0 * scale, y + y1 * scale, strength, strength);
      }
    } finally {
      GL11.glEnd();
      GlStateManager.shadeModel(GL11.GL_FLAT);
    }
  }

  private static void shadowQuad(float l, float r, float top, float bottom, float a, float b) {
    GL11.glColor4f(0, 0, 0, a);
    GL11.glVertex2f(l, top);
    GL11.glVertex2f(l, bottom);
    GL11.glColor4f(0, 0, 0, b);
    GL11.glVertex2f(r, bottom);
    GL11.glVertex2f(r, top);
  }
}
