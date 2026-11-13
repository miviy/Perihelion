package perihelion.integration.minecraft189.gui.menu;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import perihelion.integration.minecraft189.gui.font.GuiFonts;
import perihelion.integration.minecraft189.gui.modern.ModernTheme;
import perihelion.integration.minecraft189.gui.widget.UiCanvas;

/** Shared restrained surfaces for title/menu controls. Requires a RenderState scope. */
public final class MenuSurface {
  public static final int TEXT = 0xffe9e7e3, MUTED = 0xff85888f;
  private static final double[] COS = new double[53], SIN = new double[53];

  static {
    for (int i = 0; i <= 52; i++) {
      int point = i % 52;
      double angle = (point / 13 * 90 + point % 13 * 7.5) * Math.PI / 180;
      COS[i] = Math.cos(angle);
      SIN[i] = Math.sin(angle);
    }
  }

  private MenuSurface() {}

  public static void rounded(
      UiCanvas canvas, float x, float y, float width, float height, float radius, int fill) {
    if (width <= 0 || height <= 0) return;
    radius = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
    if (radius == 0) {
      canvas.rect(x, y, width, height, fill);
      return;
    }
    canvas.texture(false);
    float fringe = .8f / Math.max(1, canvas.pixelScale);
    GlStateManager.disableTexture2D();
    GlStateManager.shadeModel(GL11.GL_SMOOTH);
    GL11.glBegin(GL11.GL_TRIANGLE_FAN);
    try {
      color(fill);
      GL11.glVertex2f(x + width / 2, y + height / 2);
      for (int i = 0; i <= 52; i++) vertex(x, y, width, height, radius, i, 0);
    } finally {
      GL11.glEnd();
    }
    GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
    try {
      for (int i = 0; i <= 52; i++) {
        color(fill);
        vertex(x, y, width, height, radius, i, 0);
        color(fill & 0x00ffffff);
        vertex(x, y, width, height, radius, i, fringe);
      }
    } finally {
      GL11.glEnd();
      GlStateManager.shadeModel(GL11.GL_FLAT);
    }
  }

  private static void vertex(
      float x, float y, float width, float height, float radius, int i, float fringe) {
    int point = i % 52, corner = point / 13;
    float cx = x + (corner == 0 || corner == 3 ? width - radius : radius);
    float cy = y + (corner < 2 ? height - radius : radius);
    GL11.glVertex2d(cx + COS[i] * (radius + fringe), cy + SIN[i] * (radius + fringe));
  }

  public static void button(
      UiCanvas canvas,
      float x,
      float y,
      float width,
      float height,
      boolean primary,
      boolean enabled,
      float hover) {
    if (width <= 0 || height <= 0) return;
    if (canvas.modern) {
      ModernTheme.button(canvas, x, y, width, height, primary, enabled, hover);
      return;
    }
    hover = enabled ? Math.max(0, Math.min(1, hover)) : 0;
    float line = 1 / Math.max(1, canvas.pixelScale);
    float radius = Math.min(3, Math.min(width, height) / 2);
    int border = blend(primary ? 0xff5b564f : 0xff303339, 0xff82766a, hover);
    int fill = blend(primary ? 0xff1e1e1f : 0xff15181d, primary ? 0xff292725 : 0xff22262b, hover);
    if (!enabled) {
      border = 0xff26292e;
      fill = 0xff111419;
    }
    rounded(canvas, x, y, width, height, radius, border);
    rounded(
        canvas,
        x + line,
        y + line,
        width - line * 2,
        height - line * 2,
        Math.max(0, radius - line),
        fill);
  }

  public static void label(
      String text, float x, float y, float scale, GuiFonts.Face face, int color) {
    GlStateManager.pushMatrix();
    try {
      GlStateManager.translate(x, y, 0);
      GlStateManager.scale(scale, scale, 1);
      face.draw(text, 0, 0, color);
    } finally {
      GlStateManager.popMatrix();
    }
  }

  public static int blend(int from, int to, float amount) {
    int result = 0;
    for (int shift = 0; shift <= 24; shift += 8)
      result |=
          Math.round((from >> shift & 255) + ((to >> shift & 255) - (from >> shift & 255)) * amount)
              << shift;
    return result;
  }

  private static void color(int c) {
    GL11.glColor4f(
        (c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f, (c >>> 24) / 255f);
  }
}
