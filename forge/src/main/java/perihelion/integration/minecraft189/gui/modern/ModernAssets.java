package perihelion.integration.minecraft189.gui.modern;

import java.awt.image.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import perihelion.integration.minecraft189.gui.widget.UiCanvas;

/** Screen-owned native-size icons and geometry-only shadows. */
final class ModernAssets implements AutoCloseable {
  private static final float[] COS = new float[53], SIN = new float[53];

  static {
    for (int i = 0; i <= 52; i++) {
      int p = i % 52;
      double angle = (p / 13 * 90 + p % 13 * 7.5) * Math.PI / 180;
      COS[i] = (float) Math.cos(angle);
      SIN[i] = (float) Math.sin(angle);
    }
  }

  // Geometry-only shadows deliberately own no texture; this handle stays null.
  private Integer shadow;
  private Integer icons;
  private int iconCell, maximumIconCell;

  void initialize() {
    // Icon allocation is lazy; the outside-only shadow owns no texture or framebuffer.
  }

  private static int upload(BufferedImage image, boolean coverage) {
    int allocated = TextureUtil.glGenTextures();
    if (allocated == 0) throw new IllegalStateException("Cannot allocate GUI artwork");
    try {
      int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
      if (coverage) for (int i = 0; i < pixels.length; i++) pixels[i] |= 0x00ffffff;
      TextureUtil.allocateTexture(allocated, image.getWidth(), image.getHeight());
      TextureUtil.uploadTexture(allocated, pixels, image.getWidth(), image.getHeight());
      GlStateManager.bindTexture(allocated);
      int filter = coverage ? GL11.GL_NEAREST : GL11.GL_LINEAR;
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
      return allocated;
    } catch (RuntimeException | Error failure) {
      GlStateManager.deleteTexture(allocated);
      throw failure;
    }
  }

  void icon(UiCanvas canvas, int id, float x, float y, float size, int color) {
    float pixels = Math.max(.01f, canvas.pixelScale);
    if (maximumIconCell == 0)
      maximumIconCell = Math.max(1, Math.min(512, GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE) / 8));
    // One horizontal atlas contains eight cells; never exceed this context's texture limit.
    if (maximumIconCell < 8) return;
    int requested = Math.max(8, Math.round(size * pixels));
    int cell = Math.min(maximumIconCell, requested);
    if (icons == null || iconCell != cell) {
      BufferedImage image = CategoryArtwork.atlas(cell);
      int replacement;
      try {
        replacement = upload(image, true);
      } finally {
        image.flush();
      }
      if (icons != null) GlStateManager.deleteTexture(icons);
      icons = replacement;
      iconCell = cell;
    }
    GlStateManager.bindTexture(icons);
    canvas.texture(true);
    GlStateManager.enableTexture2D();
    color(color);
    float left = Math.round(x * pixels) / pixels;
    float top = Math.round(y * pixels) / pixels;
    // A capped raster may resample, but it must not shrink the requested logical symbol.
    float snapped = requested > maximumIconCell ? size : cell / pixels;
    quad(left, top, snapped, snapped, id / 8f, 0, (id + 1) / 8f, 1);
  }

  void shadow(UiCanvas canvas, float x, float y, float w, float h, float radius, float opacity) {
    if (!Float.isFinite(x)
        || !Float.isFinite(y)
        || !Float.isFinite(w)
        || !Float.isFinite(h)
        || !Float.isFinite(radius)
        || !Float.isFinite(opacity)
        || w <= 0
        || h <= 0
        || opacity <= 0) return;
    radius = ShadowContour.radius(w, h, radius);
    opacity = Math.min(1, opacity);
    final int rings = 8;
    final float spread = 6;
    canvas.texture(false);
    GlStateManager.disableTexture2D();
    GlStateManager.shadeModel(GL11.GL_SMOOTH);
    try {
      // Adjacent strips share exactly one contour; no fills overlap or enter the panel.
      for (int ring = 0; ring < rings; ring++) {
        float inner = spread * ring / rings, outer = spread * (ring + 1) / rings;
        float innerFade = 1 - inner / spread, outerFade = 1 - outer / spread;
        int innerInk = Math.round(opacity * .23f * innerFade * innerFade * 255) << 24;
        int outerInk = Math.round(opacity * .23f * outerFade * outerFade * 255) << 24;
        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
        try {
          for (int point = 0; point < ShadowContour.POINTS; point++) {
            color(innerInk);
            GL11.glVertex2f(
                ShadowContour.x(x, w, radius, point, inner),
                ShadowContour.y(y, h, radius, point, inner));
            color(outerInk);
            GL11.glVertex2f(
                ShadowContour.x(x, w, radius, point, outer),
                ShadowContour.y(y, h, radius, point, outer));
          }
        } finally {
          GL11.glEnd();
        }
      }
    } finally {
      GlStateManager.shadeModel(GL11.GL_FLAT);
    }
  }

  private static void quad(
      float x, float y, float w, float h, float u, float v, float u2, float v2) {
    GL11.glBegin(GL11.GL_QUADS);
    GL11.glTexCoord2f(u, v);
    GL11.glVertex2f(x, y);
    GL11.glTexCoord2f(u, v2);
    GL11.glVertex2f(x, y + h);
    GL11.glTexCoord2f(u2, v2);
    GL11.glVertex2f(x + w, y + h);
    GL11.glTexCoord2f(u2, v);
    GL11.glVertex2f(x + w, y);
    GL11.glEnd();
  }

  static void rounded(
      UiCanvas c,
      float x,
      float y,
      float w,
      float h,
      float radius,
      int fill,
      boolean top,
      boolean bottom) {
    if (w <= 0 || h <= 0) return;
    c.texture(false);
    radius = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
    if (radius == 0 || !top && !bottom) {
      c.rect(x, y, w, h, fill);
      return;
    }
    GlStateManager.disableTexture2D();
    GlStateManager.shadeModel(GL11.GL_SMOOTH);
    GL11.glBegin(GL11.GL_TRIANGLE_FAN);
    color(fill);
    GL11.glVertex2f(x + w / 2, y + h / 2);
    for (int i = 0; i <= 52; i++) vertex(x, y, w, h, radius, i, 0, top, bottom);
    GL11.glEnd();
    float fringe = .7f / Math.max(.5f, c.pixelScale);
    GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
    for (int i = 0; i <= 52; i++) {
      color(fill);
      vertex(x, y, w, h, radius, i, 0, top, bottom);
      color(fill & 0xffffff);
      vertex(x, y, w, h, radius, i, fringe, top, bottom);
    }
    GL11.glEnd();
    GlStateManager.shadeModel(GL11.GL_FLAT);
  }

  private static void vertex(
      float x,
      float y,
      float w,
      float h,
      float r,
      int i,
      float fringe,
      boolean top,
      boolean bottom) {
    int corner = i % 52 / 13;
    boolean round = corner < 2 ? bottom : top;
    float radius = round ? r : 0;
    float cx = x + (corner == 0 || corner == 3 ? w - radius : radius),
        cy = y + (corner < 2 ? h - radius : radius);
    GL11.glVertex2f(cx + COS[i] * (radius + fringe), cy + SIN[i] * (radius + fringe));
  }

  static void arrow(UiCanvas c, float x, float y, float amount, int fill) {
    float a = amount * (float) Math.PI,
        co = amount == 0 ? 1 : amount == 1 ? -1 : (float) Math.cos(a),
        si = amount == 0 || amount == 1 ? 0 : (float) Math.sin(a);
    c.texture(false);
    GlStateManager.disableTexture2D();
    float pixels = Math.max(.01f, c.pixelScale);
    x = Math.round(x * pixels) / pixels;
    y = Math.round(y * pixels) / pixels;
    GlStateManager.resetColor();
    color(fill);
    // A joined open chevron, with no overlapping strokes or font-dependent glyphs.
    GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
    point(x, y, -3.6f, -1.35f, co, si);
    point(x, y, -2.8f, -2.15f, co, si);
    point(x, y, 0, 2.15f, co, si);
    point(x, y, 0, .55f, co, si);
    point(x, y, 3.6f, -1.35f, co, si);
    point(x, y, 2.8f, -2.15f, co, si);
    GL11.glEnd();
  }

  private static void point(float x, float y, float px, float py, float co, float si) {
    GL11.glVertex2f(x + px * co - py * si, y + px * si + py * co);
  }

  private static void color(int c) {
    GlStateManager.color(
        (c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f, (c >>> 24) / 255f);
  }

  @Override
  public void close() {
    if (shadow != null) GlStateManager.deleteTexture(shadow);
    if (icons != null) GlStateManager.deleteTexture(icons);
    shadow = icons = null;
    iconCell = maximumIconCell = 0;
  }
}
