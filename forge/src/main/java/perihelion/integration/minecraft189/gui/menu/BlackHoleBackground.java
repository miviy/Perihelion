package perihelion.integration.minecraft189.gui.menu;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.renderer.GlStateManager;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;
import perihelion.integration.minecraft189.gui.widget.UiCanvas;
import perihelion.integration.minecraft189.render.RenderStyle;

/** Screen-owned shader and animated fixed-function fallback. Draw inside a RenderState scope. */
public final class BlackHoleBackground implements AutoCloseable {
  private int program,
      aspectUniform,
      timeUniform,
      sizeUniform,
      centerUniform,
      pointerUniform,
      subduedUniform;
  private boolean attempted;

  public void render(UiCanvas canvas, int width, int height, float seconds) {
    render(canvas, width, height, seconds, 0, 0, false);
  }

  public void render(
      UiCanvas canvas,
      int width,
      int height,
      float seconds,
      float pointerX,
      float pointerY,
      boolean subdued) {
    float radius = Math.min(width * .123f, height * .26f);
    float x = width * .735f + pointerX * 8, y = height * .48f + pointerY * 6;
    if (!attempted) initialize();
    if (program == 0) {
      fallback(canvas, width, height, x, y, radius, seconds, subdued);
      return;
    }
    int previous = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    try {
      GlStateManager.disableTexture2D();
      GL20.glUseProgram(program);
      GL20.glUniform1f(aspectUniform, width / (float) height);
      GL20.glUniform1f(timeUniform, seconds);
      GL20.glUniform1f(sizeUniform, radius / height);
      GL20.glUniform2f(centerUniform, x / width, y / height);
      GL20.glUniform2f(pointerUniform, pointerX, pointerY);
      GL20.glUniform1f(subduedUniform, subdued ? 1 : 0);
      GL11.glBegin(GL11.GL_QUADS);
      try {
        GL11.glTexCoord2f(0, 0);
        GL11.glVertex2f(0, 0);
        GL11.glTexCoord2f(0, 1);
        GL11.glVertex2f(0, height);
        GL11.glTexCoord2f(1, 1);
        GL11.glVertex2f(width, height);
        GL11.glTexCoord2f(1, 0);
        GL11.glVertex2f(width, 0);
      } finally {
        GL11.glEnd();
      }
    } finally {
      GL20.glUseProgram(previous);
    }
  }

  private void initialize() {
    attempted = true;
    if (!GLContext.getCapabilities().OpenGL20) return;
    int vertex = 0, fragment = 0;
    try {
      vertex =
          compile(
              GL20.GL_VERTEX_SHADER,
              "#version 120\n"
                  + "varying vec2 uv;\n"
                  + "void main(){uv=gl_MultiTexCoord0.xy;gl_Position=ftransform();}\n");
      fragment = compile(GL20.GL_FRAGMENT_SHADER, source());
      program = GL20.glCreateProgram();
      if (program == 0) throw new IllegalStateException("Could not allocate title shader");
      GL20.glAttachShader(program, vertex);
      GL20.glAttachShader(program, fragment);
      GL20.glLinkProgram(program);
      if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0)
        throw new IllegalStateException(GL20.glGetProgramInfoLog(program, 4096));
      aspectUniform = GL20.glGetUniformLocation(program, "aspect");
      timeUniform = GL20.glGetUniformLocation(program, "time");
      sizeUniform = GL20.glGetUniformLocation(program, "size");
      centerUniform = GL20.glGetUniformLocation(program, "center");
      pointerUniform = GL20.glGetUniformLocation(program, "pointer");
      subduedUniform = GL20.glGetUniformLocation(program, "subdued");
    } catch (IOException | RuntimeException failure) {
      if (program != 0) GL20.glDeleteProgram(program);
      program = 0;
      LogManager.getLogger("Perihelion")
          .warn("Using the title's animated fallback background", failure);
    } finally {
      if (vertex != 0) GL20.glDeleteShader(vertex);
      if (fragment != 0) GL20.glDeleteShader(fragment);
    }
  }

  private static String source() throws IOException {
    try (InputStream input =
        BlackHoleBackground.class.getResourceAsStream(
            "/assets/perihelion/shaders/main_menu.frag")) {
      if (input == null) throw new IOException("Missing title shader resource");
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      byte[] buffer = new byte[4096];
      int count;
      while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
      return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
  }

  private static int compile(int type, String source) {
    int shader = GL20.glCreateShader(type);
    if (shader == 0) throw new IllegalStateException("Could not allocate title shader stage");
    try {
      GL20.glShaderSource(shader, source);
      GL20.glCompileShader(shader);
      if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0)
        throw new IllegalStateException(GL20.glGetShaderInfoLog(shader, 4096));
      return shader;
    } catch (RuntimeException failure) {
      GL20.glDeleteShader(shader);
      throw failure;
    }
  }

  private static void fallback(
      UiCanvas canvas,
      int width,
      int height,
      float x,
      float y,
      float radius,
      float seconds,
      boolean subdued) {
    canvas.gradient(0, 0, width, height, 0xff070910, 0xff10151e, 0xff070a10, 0xff070910);
    RenderStyle.lines(false, 1, 0xffc49368);
    for (int band = 0; band < 15; band++) {
      GL11.glBegin(GL11.GL_LINE_LOOP);
      try {
        for (int i = 0; i < 256; i++) {
          double angle = i * Math.PI * 2 / 256;
          float light =
              .35f
                  + .65f * (float) Math.pow(.5 + .5 * Math.sin(angle * 7 - seconds * .7 + band), 2);
          GL11.glColor4f(1, .70f + light * .18f, .40f + light * .24f, light * .4f);
          double r = radius * (.85 + band * .016);
          GL11.glVertex2d(x + Math.cos(angle) * r, y + Math.sin(angle) * r);
        }
      } finally {
        GL11.glEnd();
      }
    }
    for (int band = 0; band < 10; band++) {
      GL11.glBegin(GL11.GL_LINE_LOOP);
      try {
        for (int i = 0; i < 256; i++) {
          double angle = i * Math.PI * 2 / 256;
          float light = .3f + .7f * (float) (.5 + .5 * Math.sin(angle * 9 - seconds + band));
          GL11.glColor4f(1, .73f, .48f, light * .3f);
          double r = radius * (1.05 + band * .11),
              dx = Math.cos(angle) * r,
              dy = Math.sin(angle) * r * .245;
          GL11.glVertex2d(x + dx, y + dy + dx * -.12);
        }
      } finally {
        GL11.glEnd();
      }
    }
    RenderStyle.text();
    canvas.gradient(0, 0, width * .67f, height, 0xff070910, 0x00070910, 0x00070910, 0xff070910);
    fallbackSky(canvas, width, height, x, y, radius, seconds);
    if (subdued) canvas.rect(0, 0, width, height, 0xaa070910);
  }

  private static void fallbackSky(
      UiCanvas canvas, int width, int height, float x, float y, float radius, float seconds) {
    float pixel = 1 / Math.max(1, canvas.pixelScale);
    for (int i = 0; i < 190; i++) {
      float seed = seed(i + 3), sx = seed(i + 203) * width, sy = seed(i + 503) * height;
      if (Math.hypot(sx - x, sy - y) < radius * .85) continue;
      int alpha = (int) ((45 + seed * 140) * (.67 + .33 * Math.sin(seconds * (seed + .7) + i)));
      float extent = seed > .94f ? pixel * 1.6f : pixel;
      canvas.rect(sx, sy, extent, extent, (alpha << 24) | 0xc4d1e7);
      if (seed > .96f) {
        canvas.hairline(sx - pixel * 2, sy, pixel * 5, ((alpha / 3) << 24) | 0xe9ddbf);
        canvas.verticalHairline(sx, sy - pixel * 2, pixel * 5, ((alpha / 3) << 24) | 0xe9ddbf);
      }
    }
    RenderStyle.lines(false, 1, 0xffd6c4a1);
    for (int i = 0; i < 18; i++) {
      float seed = seed(i + 43);
      float phase = (seconds * (.045f + seed * .026f) + seed * 13) % 1;
      GL11.glBegin(GL11.GL_LINE_STRIP);
      try {
        for (int j = 0; j <= 20; j++) {
          float tail = j / 20f;
          double progress = Math.max(0, phase - (1 - tail) * .045);
          double r = radius * (3.25 - 2.4 * Math.pow(progress, 1.7));
          double angle = seed * Math.PI * 2 + progress * 8;
          float light = tail * .5f * Math.min(1, phase * 8) * Math.min(1, (1 - phase) * 12);
          if (r < radius * .85) light = 0;
          GL11.glColor4f(1, .7f, .4f, light);
          GL11.glVertex2d(x + Math.cos(angle) * r, y + Math.sin(angle) * r);
        }
      } finally {
        GL11.glEnd();
      }
    }
    for (int i = 0; i < 2; i++) {
      float clock = seconds + i * 5.7f;
      float age = clock % 11.5f;
      if (age > 1.35f) continue;
      float cycle = (float) Math.floor(clock / 11.5f);
      float sx = width * (.28f + seed((int) cycle + i * 31 + 701) * .55f) + age * height * .30f;
      float sy = height * (.06f + seed((int) cycle + i * 11 + 801) * .27f) + age * height * .14f;
      float alpha = Math.min(1, age / .16f) * Math.min(1, (1.35f - age) / .5f);
      GL11.glBegin(GL11.GL_LINES);
      try {
        GL11.glColor4f(.76f, .86f, 1, 0);
        GL11.glVertex2f(sx - height * .10f, sy - height * .047f);
        GL11.glColor4f(.76f, .86f, 1, alpha * .8f);
        GL11.glVertex2f(sx, sy);
      } finally {
        GL11.glEnd();
      }
    }
    RenderStyle.text();
  }

  private static float seed(int value) {
    double raw = Math.sin(value * 127.1 + 311.7) * 43758.5453;
    return (float) (raw - Math.floor(raw));
  }

  @Override
  public void close() {
    if (program != 0) GL20.glDeleteProgram(program);
    program = 0;
    attempted = false;
  }
}
