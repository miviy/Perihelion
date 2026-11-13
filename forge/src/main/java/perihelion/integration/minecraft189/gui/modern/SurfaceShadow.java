package perihelion.integration.minecraft189.gui.modern;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import perihelion.integration.minecraft189.gui.widget.UiCanvas;

/** Shared geometry-only shadow outside a surface; owns no framebuffer or texture. */
public final class SurfaceShadow {
  private SurfaceShadow() {}

  public static void draw(
      UiCanvas canvas,
      float x,
      float y,
      float w,
      float h,
      float radius,
      float spread,
      float opacity) {
    if (w <= 0 || h <= 0 || spread <= 0 || opacity <= 0) return;
    radius = ShadowContour.radius(w, h, radius);
    opacity = Math.max(0, Math.min(1, opacity));
    canvas.texture(false);
    GlStateManager.disableTexture2D();
    GlStateManager.shadeModel(GL11.GL_SMOOTH);
    try {
      for (int ring = 0; ring < 8; ring++) {
        float inner = spread * ring / 8, outer = spread * (ring + 1) / 8;
        float first = 1 - inner / spread, second = 1 - outer / spread;
        float a = opacity * first * first, b = opacity * second * second;
        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
        try {
          for (int point = 0; point < ShadowContour.POINTS; point++) {
            GL11.glColor4f(0, 0, 0, a);
            GL11.glVertex2f(
                ShadowContour.x(x, w, radius, point, inner),
                ShadowContour.y(y, h, radius, point, inner));
            GL11.glColor4f(0, 0, 0, b);
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
}
