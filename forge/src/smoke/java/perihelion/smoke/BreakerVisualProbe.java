package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;
import static perihelion.smoke.RotationPipelineProbe.number;

import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;
import perihelion.core.event.*;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.integration.minecraft189.render.RenderState;

/** Isolated framebuffer and state-restoration test of the actual route renderer. */
final class BreakerVisualProbe {
  static void render(ClientRuntime runtime, Object game, Object player, Object plan)
      throws Exception {
    Object manager = method(game.getClass(), "getRenderManager", "func_175598_ae").invoke(game);
    Field vx = field(manager.getClass(), "viewerPosX", "field_78730_l"),
        vy = field(manager.getClass(), "viewerPosY", "field_78731_m"),
        vz = field(manager.getClass(), "viewerPosZ", "field_78728_n");
    double ox = vx.getDouble(manager), oy = vy.getDouble(manager), oz = vz.getDouble(manager);
    double x = number(player, "posX", "field_70165_t"),
        y = number(player, "posY", "field_70163_u") + 1.62,
        z = number(player, "posZ", "field_70161_v");
    Object bed = field(plan.getClass(), "bed").get(plan);
    double
        bx =
            ((Number) method(bed.getClass(), "getX", "func_177958_n").invoke(bed)).doubleValue()
                + .5,
        by =
            ((Number) method(bed.getClass(), "getY", "func_177956_o").invoke(bed)).doubleValue()
                + .3,
        bz =
            ((Number) method(bed.getClass(), "getZ", "func_177952_p").invoke(bed)).doubleValue()
                + .5;
    Method snapshot = RenderHookProbe.class.getDeclaredMethod("readState");
    snapshot.setAccessible(true);
    int w = Display.getWidth(), h = Display.getHeight();
    Class<?> scaled = Class.forName("net.minecraft.client.gui.ScaledResolution");
    Object resolution = scaled.getConstructor(game.getClass()).newInstance(game);
    int
        sw =
            ((Number) method(scaled, "getScaledWidth", "func_78326_a").invoke(resolution))
                .intValue(),
        sh =
            ((Number) method(scaled, "getScaledHeight", "func_78328_b").invoke(resolution))
                .intValue(),
        scale =
            ((Number) method(scaled, "getScaleFactor", "func_78325_e").invoke(resolution))
                .intValue();
    try (RenderState saved = new RenderState().open()) {
      vx.setDouble(manager, x);
      vy.setDouble(manager, y);
      vz.setDouble(manager, z);
      GL11.glViewport(0, 0, w, h);
      GL11.glDisable(GL11.GL_SCISSOR_TEST);
      GL11.glClearColor(.04f, .055f, .08f, 1);
      GL11.glDepthMask(true);
      GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
      GL11.glMatrixMode(GL11.GL_PROJECTION);
      GL11.glLoadIdentity();
      GLU.gluPerspective(70, (float) w / h, .05f, 100);
      GL11.glMatrixMode(GL11.GL_MODELVIEW);
      GL11.glLoadIdentity();
      GLU.gluLookAt(0, 0, 0, (float) (bx - x), (float) (by - y), (float) (bz - z), 0, 1, 0);
      Object before = snapshot.invoke(null);
      runtime.clientEvents().post(new WorldRenderEvent(1));
      require(before.equals(snapshot.invoke(null)), "world outline restores GL state");
      GL11.glMatrixMode(GL11.GL_PROJECTION);
      GL11.glLoadIdentity();
      GL11.glOrtho(0, sw, sh, 0, -100, 100);
      GL11.glMatrixMode(GL11.GL_MODELVIEW);
      GL11.glLoadIdentity();
      before = snapshot.invoke(null);
      runtime.clientEvents().post(new HudRenderEvent(1, sw, sh, scale));
      require(before.equals(snapshot.invoke(null)), "HUD labels restore GL state");
      ByteBuffer pixels = BufferUtils.createByteBuffer(w * h * 4);
      GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
      GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
      BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
      int green = 0, blue = 0;
      for (int py = 0; py < h; py++)
        for (int px = 0; px < w; px++) {
          int offset = (px + py * w) * 4,
              r = pixels.get(offset) & 255,
              g = pixels.get(offset + 1) & 255,
              b = pixels.get(offset + 2) & 255;
          if (g > 180 && g > r * 1.3 && g > b * 1.2) green++;
          if (b > 180 && b > r * 1.3 && g > 100) blue++;
          image.setRGB(px, h - py - 1, (r << 16) | (g << 8) | b);
        }
      require(
          ImageIO.write(
              image,
              "png",
              new File(System.getProperty("perihelion.smoke.output"), "breaker-visual-route.png")),
          "route screenshot saved");
      require(
          green > 30 && blue > 10,
          "first target and later steps produce distinct colored pixels green="
              + green
              + " blue="
              + blue);
      require(GL11.glGetError() == GL11.GL_NO_ERROR, "route rendering has no GL errors");
      System.out.println(
          "[PERIHELION_SMOKE] BREAKER_VISUAL framebuffer=PASS state=restored green="
              + green
              + " blue="
              + blue);
    } finally {
      vx.setDouble(manager, ox);
      vy.setDouble(manager, oy);
      vz.setDouble(manager, oz);
    }
  }

  private static void require(boolean value, String label) {
    if (!value) throw new AssertionError(label);
  }
}
