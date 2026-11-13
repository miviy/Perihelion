package perihelion.integration.minecraft189.gui.modern;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;
import perihelion.integration.minecraft189.gui.widget.UiCanvas;

/**
 * Screen-owned, quarter-resolution world blur. Call on the client thread inside the caller's
 * RenderState scope, before painting the GUI. It does not own vanilla or OptiFine shader state.
 */
public final class ModernBackdrop implements AutoCloseable {
  private static final int FRAMEBUFFER_BINDING = 0x8ca6;
  private static final String VERTEX =
      "#version 120\n"
          + "varying vec2 uv;\n"
          + "void main(){uv=gl_MultiTexCoord0.xy;gl_Position=gl_Vertex;}\n";
  private static final String FRAGMENT =
      "#version 120\n"
          + "uniform sampler2D image;uniform vec2 limit;uniform vec2 pixel;"
          + "uniform vec2 stepSize;uniform float alpha;uniform int blurred;varying vec2 uv;\n"
          + "vec3 tap(vec2 p){return texture2D(image,clamp(p,pixel*0.5,limit-pixel*0.5)).rgb;}\n"
          + "void main(){vec2 p=uv*limit;vec3 c;"
          + "if(blurred==0){c=tap(p);}else{"
          + "c=tap(p)*0.375;"
          + "c+=(tap(p-stepSize)+tap(p+stepSize))*0.25;"
          + "c+=(tap(p-stepSize*2.0)+tap(p+stepSize*2.0))*0.0625;}"
          + "gl_FragColor=vec4(c,alpha);}\n";

  private final Target first = new Target(), second = new Target();
  private final IntBuffer viewport = BufferUtils.createIntBuffer(16);
  private final ByteBuffer colorMask = BufferUtils.createByteBuffer(16);
  private int program,
      imageUniform,
      limitUniform,
      pixelUniform,
      stepUniform,
      alphaUniform,
      blurUniform;
  private boolean disabled, diagnosticLogged;

  /**
   * Composite a blurred copy of the current world, without transforming the GUI's logical space.
   * Unsupported hardware or a foreign framebuffer simply leaves the original background intact.
   */
  public void render(UiCanvas canvas, float width, float height, float opacity) {
    Minecraft game = Minecraft.getMinecraft();
    if (disabled
        || game.theWorld == null
        || !Float.isFinite(width)
        || !Float.isFinite(height)
        || width <= 0
        || height <= 0
        || !Float.isFinite(opacity)
        || opacity <= 0
        || !OpenGlHelper.isFramebufferEnabled()
        || !GLContext.getCapabilities().OpenGL20) return;

    Framebuffer source = game.getFramebuffer();
    int incomingFramebuffer = GL11.glGetInteger(FRAMEBUFFER_BINDING);
    if (source == null
        || source.framebufferObject <= 0
        || source.framebufferTexture <= 0
        || source.framebufferWidth <= 0
        || source.framebufferHeight <= 0
        || source.framebufferTextureWidth <= 0
        || source.framebufferTextureHeight <= 0
        || incomingFramebuffer != source.framebufferObject) return;

    viewport.clear();
    GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
    // A differently sized viewport can belong to another renderer or a compatibility mod.
    if (viewport.get(0) != 0
        || viewport.get(1) != 0
        || viewport.get(2) != source.framebufferWidth
        || viewport.get(3) != source.framebufferHeight) return;

    int incomingProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    boolean separateReadFramebuffer =
        GLContext.getCapabilities().OpenGL30
            || GLContext.getCapabilities().GL_ARB_framebuffer_object;
    int incomingReadFramebuffer =
        separateReadFramebuffer
            ? GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
            : incomingFramebuffer;
    int incomingActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
    boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    boolean alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
    boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
    boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
    boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
    boolean stencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
    int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
    int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
    int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
    int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
    int equationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
    int equationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
    colorMask.clear();
    GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, colorMask);
    activeTexture(OpenGlHelper.defaultTexUnit);
    int incomingTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
    try {
      initialize();
      int targetWidth = Math.max(1, (source.framebufferWidth + 3) / 4);
      int targetHeight = Math.max(1, (source.framebufferHeight + 3) / 4);
      first.resize(targetWidth, targetHeight);
      second.resize(targetWidth, targetHeight);

      capability(GL11.GL_DEPTH_TEST, false);
      GlStateManager.depthMask(false);
      GL11.glDepthMask(false);
      capability(GL11.GL_ALPHA_TEST, false);
      capability(GL11.GL_BLEND, false);
      capability(GL11.GL_CULL_FACE, false);
      capability(GL11.GL_SCISSOR_TEST, false);
      capability(GL11.GL_STENCIL_TEST, false);
      GlStateManager.colorMask(true, true, true, true);
      GL11.glColorMask(true, true, true, true);
      GL20.glUseProgram(program);
      GL20.glUniform1i(imageUniform, 0);

      float sourceLimitX = source.framebufferWidth / (float) source.framebufferTextureWidth;
      float sourceLimitY = source.framebufferHeight / (float) source.framebufferTextureHeight;
      first.bind();
      pass(
          source.framebufferTexture,
          sourceLimitX,
          sourceLimitY,
          1f / source.framebufferTextureWidth,
          1f / source.framebufferTextureHeight,
          sourceLimitX * 1.5f / targetWidth,
          0,
          1,
          true);
      second.bind();
      pass(
          first.texture,
          1,
          1,
          1f / targetWidth,
          1f / targetHeight,
          0,
          1.5f / targetHeight,
          1,
          true);

      OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, incomingFramebuffer);
      restoreViewport();
      capability(GL11.GL_BLEND, true);
      GlStateManager.tryBlendFuncSeparate(
          GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
      OpenGlHelper.glBlendFunc(
          GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
      GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
      pass(
          second.texture,
          1,
          1,
          1f / targetWidth,
          1f / targetHeight,
          0,
          0,
          Math.min(1, opacity),
          false);
    } catch (RuntimeException failure) {
      disabled = true;
      dispose();
      if (!diagnosticLogged) {
        diagnosticLogged = true;
        LogManager.getLogger("Perihelion")
            .warn(
                "GUI background blur unavailable; using the unblurred world: {}",
                failure.toString());
      }
    } finally {
      GL20.glUseProgram(incomingProgram);
      OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, incomingFramebuffer);
      if (separateReadFramebuffer)
        OpenGlHelper.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, incomingReadFramebuffer);
      restoreViewport();
      GlStateManager.tryBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
      OpenGlHelper.glBlendFunc(srcRgb, dstRgb, srcAlpha, dstAlpha);
      GL20.glBlendEquationSeparate(equationRgb, equationAlpha);
      capability(GL11.GL_DEPTH_TEST, depth);
      GlStateManager.depthMask(depthMask);
      GL11.glDepthMask(depthMask);
      capability(GL11.GL_ALPHA_TEST, alpha);
      capability(GL11.GL_BLEND, blend);
      capability(GL11.GL_CULL_FACE, cull);
      capability(GL11.GL_SCISSOR_TEST, scissor);
      capability(GL11.GL_STENCIL_TEST, stencil);
      boolean r = colorMask.get(0) != 0, g = colorMask.get(1) != 0;
      boolean b = colorMask.get(2) != 0, a = colorMask.get(3) != 0;
      GlStateManager.colorMask(r, g, b, a);
      GL11.glColorMask(r, g, b, a);
      bindTexture(incomingTexture);
      activeTexture(incomingActiveTexture);
    }
  }

  private void initialize() {
    if (program != 0) return;
    int vertex = compile(GL20.GL_VERTEX_SHADER, VERTEX), fragment = 0;
    try {
      fragment = compile(GL20.GL_FRAGMENT_SHADER, FRAGMENT);
      program = GL20.glCreateProgram();
      if (program == 0) throw new IllegalStateException("Cannot create GUI blur program");
      GL20.glAttachShader(program, vertex);
      GL20.glAttachShader(program, fragment);
      GL20.glLinkProgram(program);
      if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0)
        throw new IllegalStateException(GL20.glGetProgramInfoLog(program, 2048));
      imageUniform = GL20.glGetUniformLocation(program, "image");
      limitUniform = GL20.glGetUniformLocation(program, "limit");
      pixelUniform = GL20.glGetUniformLocation(program, "pixel");
      stepUniform = GL20.glGetUniformLocation(program, "stepSize");
      alphaUniform = GL20.glGetUniformLocation(program, "alpha");
      blurUniform = GL20.glGetUniformLocation(program, "blurred");
    } finally {
      GL20.glDeleteShader(vertex);
      if (fragment != 0) GL20.glDeleteShader(fragment);
    }
  }

  private static int compile(int type, String source) {
    int shader = GL20.glCreateShader(type);
    if (shader == 0) throw new IllegalStateException("Cannot create GUI blur shader");
    try {
      GL20.glShaderSource(shader, source);
      GL20.glCompileShader(shader);
      if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0)
        throw new IllegalStateException(GL20.glGetShaderInfoLog(shader, 2048));
      return shader;
    } catch (RuntimeException failure) {
      GL20.glDeleteShader(shader);
      throw failure;
    }
  }

  private void pass(
      int texture,
      float limitX,
      float limitY,
      float pixelX,
      float pixelY,
      float stepX,
      float stepY,
      float alpha,
      boolean blur) {
    bindTexture(texture);
    GL20.glUniform2f(limitUniform, limitX, limitY);
    GL20.glUniform2f(pixelUniform, pixelX, pixelY);
    GL20.glUniform2f(stepUniform, stepX, stepY);
    GL20.glUniform1f(alphaUniform, alpha);
    GL20.glUniform1i(blurUniform, blur ? 1 : 0);
    GL11.glBegin(GL11.GL_QUADS);
    try {
      GL11.glTexCoord2f(0, 0);
      GL11.glVertex2f(-1, -1);
      GL11.glTexCoord2f(1, 0);
      GL11.glVertex2f(1, -1);
      GL11.glTexCoord2f(1, 1);
      GL11.glVertex2f(1, 1);
      GL11.glTexCoord2f(0, 1);
      GL11.glVertex2f(-1, 1);
    } finally {
      GL11.glEnd();
    }
  }

  private void restoreViewport() {
    GlStateManager.viewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
    GL11.glViewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
  }

  private static void activeTexture(int unit) {
    GlStateManager.setActiveTexture(unit);
    OpenGlHelper.setActiveTexture(unit);
  }

  private static void bindTexture(int texture) {
    GlStateManager.bindTexture(texture);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
  }

  private static void capability(int capability, boolean enabled) {
    switch (capability) {
      case GL11.GL_DEPTH_TEST:
        if (enabled) GlStateManager.enableDepth();
        else GlStateManager.disableDepth();
        break;
      case GL11.GL_ALPHA_TEST:
        if (enabled) GlStateManager.enableAlpha();
        else GlStateManager.disableAlpha();
        break;
      case GL11.GL_BLEND:
        if (enabled) GlStateManager.enableBlend();
        else GlStateManager.disableBlend();
        break;
      case GL11.GL_CULL_FACE:
        if (enabled) GlStateManager.enableCull();
        else GlStateManager.disableCull();
        break;
      default:
        break; // Scissor and stencil have no Minecraft cache.
    }
    if (enabled) GL11.glEnable(capability);
    else GL11.glDisable(capability);
  }

  private void dispose() {
    first.close();
    second.close();
    if (program != 0) GL20.glDeleteProgram(program);
    program = 0;
  }

  /** Release GL resources on the client thread; the same screen may recreate them on return. */
  @Override
  public void close() {
    dispose();
    disabled = false;
  }

  private static final class Target implements AutoCloseable {
    private int framebuffer, texture, width, height;

    void resize(int nextWidth, int nextHeight) {
      if (framebuffer != 0 && width == nextWidth && height == nextHeight) return;
      close();
      width = nextWidth;
      height = nextHeight;
      texture = GL11.glGenTextures();
      framebuffer = OpenGlHelper.glGenFramebuffers();
      if (texture == 0 || framebuffer == 0)
        throw new IllegalStateException("Cannot allocate GUI blur target");
      bindTexture(texture);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
      GL11.glTexImage2D(
          GL11.GL_TEXTURE_2D,
          0,
          GL11.GL_RGBA8,
          width,
          height,
          0,
          GL11.GL_RGBA,
          GL11.GL_UNSIGNED_BYTE,
          (ByteBuffer) null);
      OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebuffer);
      OpenGlHelper.glFramebufferTexture2D(
          OpenGlHelper.GL_FRAMEBUFFER,
          OpenGlHelper.GL_COLOR_ATTACHMENT0,
          GL11.GL_TEXTURE_2D,
          texture,
          0);
      if (OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER)
          != OpenGlHelper.GL_FRAMEBUFFER_COMPLETE)
        throw new IllegalStateException("Incomplete GUI blur framebuffer");
    }

    void bind() {
      OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebuffer);
      GlStateManager.viewport(0, 0, width, height);
      GL11.glViewport(0, 0, width, height);
    }

    @Override
    public void close() {
      if (texture != 0) GlStateManager.deleteTexture(texture);
      if (framebuffer != 0) OpenGlHelper.glDeleteFramebuffers(framebuffer);
      texture = framebuffer = width = height = 0;
    }
  }
}
