package perihelion.smoke;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import perihelion.integration.minecraft189.gui.font.GuiFonts;
import perihelion.integration.minecraft189.render.HudText;
import perihelion.integration.minecraft189.render.RenderState;

/** Compare actual native glyph pixels against an independently drawn whole-pixel reference. */
public final class MinecraftFontProbe {
  private static final int W = 512, H = 96;

  public static void run() throws Exception {
    IntBuffer viewport = BufferUtils.createIntBuffer(16);
    FloatBuffer clear = BufferUtils.createFloatBuffer(16);
    GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
    GL11.glGetFloat(GL11.GL_COLOR_CLEAR_VALUE, clear);
    int checks = 0;
    HudText nativeText = new HudText();
    try (RenderState ignored = new RenderState().open();
        GuiFonts fonts = new GuiFonts()) {
      GL11.glViewport(0, 0, W, H);
      GL11.glMatrixMode(GL11.GL_PROJECTION);
      GL11.glLoadIdentity();
      GL11.glOrtho(0, W, H, 0, -1000, 1000);
      GL11.glMatrixMode(GL11.GL_MODELVIEW);
      GL11.glDisable(GL11.GL_SCISSOR_TEST);
      GL11.glClearColor(0, 0, 0, 1);
      float[] scales = {1, 1.25f, 1.5f, 1.68f, 2, 2.1f, 2.5f, 3.6f};
      int[] referenceSizes = {1, 1, 1, 2, 2, 2, 2, 4};
      for (int sample = 0; sample < scales.length; sample++)
        for (boolean bold : new boolean[] {false, true})
          for (boolean shadow : new boolean[] {false, true}) {
            float pixels = scales[sample];
            String label = "Wi 012 §cColor §rreset §lBold §rΩ Ж";
            GuiFonts.Face face = fonts.minecraft(bold, pixels);
            GL11.glLoadIdentity();
            GL11.glScalef(pixels, pixels, 1);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            face.draw(label, 8 / pixels, 8 / pixels, 0xffeeeeee, shadow, null);
            byte[] actual = read();
            GL11.glLoadIdentity();
            int integerPixels = referenceSizes[sample];
            GL11.glScalef(integerPixels, integerPixels, 1);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            String reference = bold ? GuiFonts.forceBold(label) : label;
            nativeText.drawInScope(
                reference, 8f / integerPixels, 8f / integerPixels, 0xffeeeeee, shadow);
            if (!Arrays.equals(actual, read()))
              throw new AssertionError(
                  "Native glyph pixels differ at "
                      + pixels
                      + " bold="
                      + bold
                      + " shadow="
                      + shadow);
            float expected = nativeText.width(reference) * integerPixels;
            if (Math.abs(face.width(label) * pixels - expected) > .001f)
              throw new AssertionError("Native drawing and width differ at " + pixels);
            checks += 2;
          }
      if (GL11.glGetError() != GL11.GL_NO_ERROR)
        throw new AssertionError("Minecraft font GL error");
      System.out.println(
          "[PERIHELION_SMOKE] MINECRAFT_FONT PASS checks="
              + checks
              + " pixelReference=vanilla scales=1,1.25,1.5,1.68,2,2.1,2.5,3.6 weights=2 shadows=2");
    } finally {
      GL11.glViewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
      GL11.glClearColor(clear.get(0), clear.get(1), clear.get(2), clear.get(3));
    }
  }

  private static byte[] read() {
    ByteBuffer data = BufferUtils.createByteBuffer(W * H * 4);
    GL11.glReadPixels(0, 0, W, H, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, data);
    byte[] result = new byte[data.capacity()];
    data.get(result);
    return result;
  }
}
