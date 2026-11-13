package perihelion.integration.minecraft189.gui.modern;

import org.apache.logging.log4j.LogManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;
import perihelion.integration.minecraft189.gui.widget.UiCanvas;

/**
 * Screen-owned alpha mask for immediate-mode GUI content. Coordinates are local UI units in the
 * full-window orthographic projection; pixelScale includes every enclosing menu scale. It changes
 * only the current shader program and never touches the framebuffer, stencil or texture binding.
 */
public final class RoundedClip implements AutoCloseable, UiCanvas.TextureMode {
  private static final String VERTEX =
      "#version 120\n"
          + "varying vec4 tint;varying vec2 uv;\n"
          + "void main(){"
          + "gl_Position=ftransform();"
          + "tint=gl_Color;uv=gl_MultiTexCoord0.xy;"
          + "}\n";

  private static final String FRAGMENT =
      "#version 120\n"
          + "uniform sampler2D image;uniform vec2 center;uniform vec2 halfSize;uniform float"
          + " corner;uniform vec4 pose;uniform vec4 rectangle;uniform int textured;varying vec4"
          + " tint;varying vec2 uv;\n"
          + "void main(){vec2 delta=gl_FragCoord.xy-pose.xy;vec2"
          + " position=pose.xy+vec2(pose.z*delta.x-pose.w*delta.y,pose.w*delta.x+pose.z*delta.y);"
          + "if(position.x<rectangle.x||position.y<rectangle.y||position.x>rectangle.z||position.y>rectangle.w)discard;vec2"
          + " q=abs(position-center)-(halfSize-vec2(corner));float"
          + " d=length(max(q,vec2(0.0)))+min(max(q.x,q.y),0.0)-corner;float"
          + " coverage=clamp(0.5-d,0.0,1.0);vec4 color=tint;"
          + "if(textured!=0)color*=texture2D(image,uv);"
          + "gl_FragColor=vec4(color.rgb,color.a*coverage);}\n";

  private int program, centerUniform, halfSizeUniform, cornerUniform, texturedUniform, imageUniform;
  private int poseUniform, rectangleUniform;
  private float pixels;
  private int screenHeight;
  private int incomingProgram;
  private boolean active, textured, disabled, diagnosticLogged;

  /**
   * Begin one non-nested clip. Returns false when unsupported, disabled after a shader failure, or
   * given empty/nonfinite bounds; the caller can use square corners in that case. Call end in a
   * finally block after a successful begin, and notify texture mode before each primitive.
   */
  public boolean begin(
      float x,
      float y,
      float width,
      float height,
      float radius,
      float pixelScale,
      int framebufferHeight) {
    if (active) throw new IllegalStateException("Rounded clips cannot be nested");
    if (disabled
        || !Float.isFinite(x)
        || !Float.isFinite(y)
        || !Float.isFinite(width)
        || !Float.isFinite(height)
        || !Float.isFinite(radius)
        || !Float.isFinite(pixelScale)
        || width <= 0
        || height <= 0
        || pixelScale <= 0
        || framebufferHeight <= 0) return false;
    if (!available()) return false;

    // The single state query for each clip; every other value belongs to this screen's program.
    int previous = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    boolean started = false;
    try {
      GL20.glUseProgram(program);
      GL20.glUniform1i(imageUniform, 0);
      GL20.glUniform2f(
          centerUniform,
          (x + width / 2) * pixelScale,
          framebufferHeight - (y + height / 2) * pixelScale);
      GL20.glUniform2f(halfSizeUniform, width * pixelScale / 2, height * pixelScale / 2);
      GL20.glUniform1f(
          cornerUniform, Math.max(0, Math.min(radius, Math.min(width, height) / 2)) * pixelScale);
      GL20.glUniform1i(texturedUniform, 0);
      GL20.glUniform4f(poseUniform, 0, 0, 1, 0);
      GL20.glUniform4f(rectangleUniform, -1e7f, -1e7f, 1e7f, 1e7f);
      pixels = pixelScale;
      screenHeight = framebufferHeight;
      incomingProgram = previous;
      textured = false;
      active = true;
      started = true;
      return true;
    } catch (RuntimeException failure) {
      disabled = true;
      dispose();
      diagnostic(failure.toString());
      return false;
    } finally {
      // Also restore on Errors, without swallowing them or modifying the foreign program.
      if (!started) GL20.glUseProgram(previous);
    }
  }

  /** Compile once without changing the current program, framebuffer or texture state. */
  public boolean available() {
    if (disabled) return false;
    if (!GLContext.getCapabilities().OpenGL20) {
      disabled = true;
      diagnostic("OpenGL 2.0 is unavailable");
      return false;
    }
    try {
      initialize();
      return true;
    } catch (RuntimeException failure) {
      disabled = true;
      dispose();
      diagnostic(failure.toString());
      return false;
    }
  }

  /** Switch the fragment source without querying or changing fixed-function texture state. */
  @Override
  public void texture(boolean value) {
    if (!active || textured == value) return;
    try {
      GL20.glUniform1i(texturedUniform, value ? 1 : 0);
      textured = value;
    } catch (RuntimeException | Error failure) {
      end();
      throw failure;
    }
  }

  /** Undo only the panel's lean in framebuffer space, including nested native font transforms. */
  public void rotation(float pivotX, float pivotY, float degrees) {
    if (!active) return;
    double radians = Math.toRadians(degrees);
    GL20.glUniform4f(
        poseUniform,
        pivotX * pixels,
        screenHeight - pivotY * pixels,
        (float) Math.cos(radians),
        (float) Math.sin(radians));
  }

  /** The same inverse pose keeps row scissoring aligned with the rounded panel. */
  public void rectangle(float x, float y, float width, float height) {
    if (!active) return;
    GL20.glUniform4f(
        rectangleUniform,
        x * pixels,
        screenHeight - (y + Math.max(0, height)) * pixels,
        (x + Math.max(0, width)) * pixels,
        screenHeight - y * pixels);
  }

  /** Restore the exact incoming program, including a nonzero OptiFine or compatibility program. */
  public void end() {
    if (!active) return;
    active = false;
    int previous = incomingProgram;
    incomingProgram = 0;
    GL20.glUseProgram(previous);
  }

  private void initialize() {
    if (program != 0) return;
    int vertex = compile(GL20.GL_VERTEX_SHADER, VERTEX), fragment = 0, candidate = 0;
    try {
      fragment = compile(GL20.GL_FRAGMENT_SHADER, FRAGMENT);
      candidate = GL20.glCreateProgram();
      if (candidate == 0) throw new IllegalStateException("Cannot allocate rounded clip program");
      GL20.glAttachShader(candidate, vertex);
      GL20.glAttachShader(candidate, fragment);
      GL20.glLinkProgram(candidate);
      if (GL20.glGetProgrami(candidate, GL20.GL_LINK_STATUS) == 0)
        throw new IllegalStateException(GL20.glGetProgramInfoLog(candidate, 2048));
      centerUniform = uniform(candidate, "center");
      halfSizeUniform = uniform(candidate, "halfSize");
      cornerUniform = uniform(candidate, "corner");
      texturedUniform = uniform(candidate, "textured");
      imageUniform = uniform(candidate, "image");
      poseUniform = uniform(candidate, "pose");
      rectangleUniform = uniform(candidate, "rectangle");
      program = candidate;
      candidate = 0;
    } finally {
      GL20.glDeleteShader(vertex);
      if (fragment != 0) GL20.glDeleteShader(fragment);
      if (candidate != 0) GL20.glDeleteProgram(candidate);
    }
  }

  private static int uniform(int owner, String name) {
    int location = GL20.glGetUniformLocation(owner, name);
    if (location < 0) throw new IllegalStateException("Missing rounded clip uniform " + name);
    return location;
  }

  private static int compile(int type, String source) {
    int shader = GL20.glCreateShader(type);
    if (shader == 0) throw new IllegalStateException("Cannot allocate rounded clip shader");
    try {
      GL20.glShaderSource(shader, source);
      GL20.glCompileShader(shader);
      if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0)
        throw new IllegalStateException(GL20.glGetShaderInfoLog(shader, 2048));
      return shader;
    } catch (RuntimeException | Error failure) {
      GL20.glDeleteShader(shader);
      throw failure;
    }
  }

  private void diagnostic(String reason) {
    if (diagnosticLogged) return;
    diagnosticLogged = true;
    LogManager.getLogger("Perihelion")
        .warn("GUI rounded clipping unavailable; using square corners: {}", reason);
  }

  private void dispose() {
    if (program != 0) GL20.glDeleteProgram(program);
    program = 0;
  }

  @Override
  public void close() {
    try {
      end();
    } finally {
      dispose();
    }
  }
}
