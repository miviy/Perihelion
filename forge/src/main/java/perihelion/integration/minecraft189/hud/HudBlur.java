package perihelion.integration.minecraft189.hud;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/**
 * One owned scene copy and a separable Gaussian filter, composited through the HUD's coverage mask.
 */
final class HudBlur implements AutoCloseable {
  private static final String VERTEX =
      "#version 120\n"
          + "varying float coverage;void main(){coverage=gl_Color.a;gl_Position=ftransform();}";
  private static final String FRAGMENT =
      "#version 120\n"
          + "uniform sampler2D scene;uniform vec2 imageSize;uniform vec4 bounds;uniform float"
          + " radius;uniform int shaped;varying float coverage;void main(){float"
          + " a=coverage;if(shaped==0){vec2"
          + " q=abs(gl_FragCoord.xy-bounds.xy)-(bounds.zw-vec2(radius));float"
          + " d=length(max(q,vec2(0.0)))+min(max(q.x,q.y),0.0)-radius;a*=1.0-smoothstep(-0.75,0.75,d);}"
          + "if(a<=0.0)discard;gl_FragColor=vec4(texture2D(scene,gl_FragCoord.xy/imageSize).rgb,a);}";
  private static final String FILTER_VERTEX =
      "#version 120\nvarying vec2 uv;void main(){uv=gl_MultiTexCoord0.xy;gl_Position=gl_Vertex;}";
  private static final String FILTER =
      "#version 120\n"
          + "uniform sampler2D scene;uniform vec2 stepSize;uniform vec2 footprint;uniform vec2"
          + " samplePixel;uniform float sigma;varying vec2 uv;vec3 tap(vec2 p){vec2"
          + " lo=samplePixel*0.5,hi=vec2(1.0)-lo;return"
          + " (texture2D(scene,clamp(p+footprint,lo,hi)).rgb"
          + "+texture2D(scene,clamp(p-footprint,lo,hi)).rgb+texture2D(scene,clamp(p+vec2(footprint.x,-footprint.y),lo,hi)).rgb"
          + "+texture2D(scene,clamp(p+vec2(-footprint.x,footprint.y),lo,hi)).rgb)*0.25;}void"
          + " main(){vec3 color=vec3(0.0);float total=0.0;for(int i=-24;i<=24;i++){float"
          + " d=float(i);if(abs(d)>ceil(sigma*3.0))continue;float"
          + " weight=exp(-0.5*d*d/(sigma*sigma));"
          + "color+=tap(uv+stepSize*d)*weight;total+=weight;}gl_FragColor=vec4(color/total,1.0);}";
  private int program, filterProgram, texture, width, height;
  private int imageUniform, sizeUniform, boundsUniform, radiusUniform, shapeUniform;
  private int filterImage, filterStep, filterFootprint, filterPixel, filterSigma;
  private final Target horizontal = new Target(), vertical = new Target();
  private final IntBuffer viewport = BufferUtils.createIntBuffer(16);
  private boolean ready, failed;
  private float filteredSigma = -1;

  void skip() {
    ready = false;
  }

  void capture(Minecraft game) {
    ready = false;
    filteredSigma = -1;
    if (failed || !GLContext.getCapabilities().OpenGL20 || !OpenGlHelper.isFramebufferEnabled())
      return;
    if (game.getFramebuffer() == null
        || GL11.glGetInteger(0x8ca6) != game.getFramebuffer().framebufferObject) return;
    int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    if (previousProgram != 0) return;
    int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    activeTexture(OpenGlHelper.defaultTexUnit);
    int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
    try {
      initialize();
      if (texture == 0) texture = GL11.glGenTextures();
      bindTexture(texture);
      parameters();
      if (width != game.displayWidth || height != game.displayHeight) {
        width = game.displayWidth;
        height = game.displayHeight;
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
      }
      GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
      ready = true;
    } catch (RuntimeException failure) {
      disable(failure);
    } finally {
      bindTexture(previousTexture);
      activeTexture(active);
      GL20.glUseProgram(previousProgram);
    }
  }

  void draw(
      float x,
      float y,
      float w,
      float h,
      float radius,
      float strength,
      int viewWidth,
      int viewHeight,
      float pixels) {
    drawRegion(x, y, w, h, radius, strength, viewWidth, viewHeight, pixels, null, 1);
  }

  void drawShape(
      float x,
      float y,
      float scale,
      HudArrayShape shape,
      float strength,
      int viewWidth,
      int viewHeight,
      float pixels) {
    drawRegion(
        x,
        y,
        shape.width * scale,
        shape.height * scale,
        0,
        strength,
        viewWidth,
        viewHeight,
        pixels,
        shape,
        scale);
  }

  private void drawRegion(
      float x,
      float y,
      float w,
      float h,
      float radius,
      float strength,
      int viewWidth,
      int viewHeight,
      float pixels,
      HudArrayShape shape,
      float scale) {
    if (!ready || w <= 0 || h <= 0) return;
    int scene = filtered(strength * pixels * .85f);
    if (scene == 0) return;
    int previous = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
        active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    activeTexture(OpenGlHelper.defaultTexUnit);
    int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
    try {
      GlStateManager.enableTexture2D();
      bindTexture(scene);
      GL20.glUseProgram(program);
      GL20.glUniform1i(imageUniform, 0);
      GL20.glUniform2f(sizeUniform, width, height);
      float sx = width / (float) viewWidth, sy = height / (float) viewHeight;
      GL20.glUniform4f(
          boundsUniform, (x + w / 2) * sx, height - (y + h / 2) * sy, w * sx / 2, h * sy / 2);
      GL20.glUniform1f(radiusUniform, Math.min(radius, Math.min(w, h) / 2) * pixels);
      GL20.glUniform1i(shapeUniform, shape == null ? 0 : 1);
      if (shape != null) shape.geometry(x, y, scale, pixels);
      else {
        float fringe = 1.5f / Math.max(.01f, pixels);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x - fringe, y - fringe);
        GL11.glVertex2f(x - fringe, y + h + fringe);
        GL11.glVertex2f(x + w + fringe, y + h + fringe);
        GL11.glVertex2f(x + w + fringe, y - fringe);
        GL11.glEnd();
      }
    } finally {
      GL20.glUseProgram(previous);
      bindTexture(previousTexture);
      activeTexture(active);
    }
  }

  private int filtered(float requestedSigma) {
    int divisor = Math.max(2, height / 180),
        tw = Math.max(1, (width + divisor - 1) / divisor),
        th = Math.max(1, (height + divisor - 1) / divisor);
    float sigma = Math.max(.55f, Math.min(8, Math.round(requestedSigma / divisor * 4) / 4f));
    if (filteredSigma == sigma && vertical.texture != 0) return vertical.texture;
    int framebuffer = GL11.glGetInteger(0x8ca6);
    boolean separateRead =
        GLContext.getCapabilities().OpenGL30
            || GLContext.getCapabilities().GL_ARB_framebuffer_object;
    int read = separateRead ? GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING) : framebuffer;
    int previous = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
        active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    activeTexture(OpenGlHelper.defaultTexUnit);
    int bound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
    viewport.clear();
    GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
    boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
        alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST),
        blend = GL11.glIsEnabled(GL11.GL_BLEND);
    boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE),
        scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),
        stencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
    try {
      horizontal.resize(tw, th);
      vertical.resize(tw, th);
      capability(GL11.GL_DEPTH_TEST, false);
      capability(GL11.GL_ALPHA_TEST, false);
      capability(GL11.GL_BLEND, false);
      capability(GL11.GL_CULL_FACE, false);
      capability(GL11.GL_SCISSOR_TEST, false);
      capability(GL11.GL_STENCIL_TEST, false);
      GL20.glUseProgram(filterProgram);
      GL20.glUniform1i(filterImage, 0);
      GL20.glUniform1f(filterSigma, sigma);
      horizontal.bind();
      filterPass(texture, 1f / tw, 0, .25f / tw, .25f / th, 1f / width, 1f / height);
      vertical.bind();
      filterPass(horizontal.texture, 0, 1f / th, 0, 0, 1f / tw, 1f / th);
      filteredSigma = sigma;
      return vertical.texture;
    } catch (RuntimeException failure) {
      disable(failure);
      return 0;
    } finally {
      OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebuffer);
      if (separateRead) OpenGlHelper.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
      GlStateManager.viewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
      GL11.glViewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
      capability(GL11.GL_DEPTH_TEST, depth);
      capability(GL11.GL_ALPHA_TEST, alpha);
      capability(GL11.GL_BLEND, blend);
      capability(GL11.GL_CULL_FACE, cull);
      capability(GL11.GL_SCISSOR_TEST, scissor);
      capability(GL11.GL_STENCIL_TEST, stencil);
      GL20.glUseProgram(previous);
      bindTexture(bound);
      activeTexture(active);
    }
  }

  private void filterPass(
      int texture,
      float x,
      float y,
      float footprintX,
      float footprintY,
      float pixelX,
      float pixelY) {
    bindTexture(texture);
    GL20.glUniform2f(filterStep, x, y);
    GL20.glUniform2f(filterFootprint, footprintX, footprintY);
    GL20.glUniform2f(filterPixel, pixelX, pixelY);
    GL11.glBegin(GL11.GL_QUADS);
    GL11.glTexCoord2f(0, 0);
    GL11.glVertex2f(-1, -1);
    GL11.glTexCoord2f(1, 0);
    GL11.glVertex2f(1, -1);
    GL11.glTexCoord2f(1, 1);
    GL11.glVertex2f(1, 1);
    GL11.glTexCoord2f(0, 1);
    GL11.glVertex2f(-1, 1);
    GL11.glEnd();
  }

  private void initialize() {
    if (program != 0) return;
    program = link(VERTEX, FRAGMENT);
    filterProgram = link(FILTER_VERTEX, FILTER);
    imageUniform = GL20.glGetUniformLocation(program, "scene");
    sizeUniform = GL20.glGetUniformLocation(program, "imageSize");
    boundsUniform = GL20.glGetUniformLocation(program, "bounds");
    radiusUniform = GL20.glGetUniformLocation(program, "radius");
    shapeUniform = GL20.glGetUniformLocation(program, "shaped");
    filterImage = GL20.glGetUniformLocation(filterProgram, "scene");
    filterStep = GL20.glGetUniformLocation(filterProgram, "stepSize");
    filterFootprint = GL20.glGetUniformLocation(filterProgram, "footprint");
    filterPixel = GL20.glGetUniformLocation(filterProgram, "samplePixel");
    filterSigma = GL20.glGetUniformLocation(filterProgram, "sigma");
  }

  private static int link(String vertexSource, String fragmentSource) {
    int vertex = 0, fragment = 0, result = 0;
    try {
      vertex = shader(GL20.GL_VERTEX_SHADER, vertexSource);
      fragment = shader(GL20.GL_FRAGMENT_SHADER, fragmentSource);
      result = GL20.glCreateProgram();
      GL20.glAttachShader(result, vertex);
      GL20.glAttachShader(result, fragment);
      GL20.glLinkProgram(result);
      if (GL20.glGetProgrami(result, GL20.GL_LINK_STATUS) == 0)
        throw new IllegalStateException(GL20.glGetProgramInfoLog(result, 2048));
      int linked = result;
      result = 0;
      return linked;
    } finally {
      if (vertex != 0) GL20.glDeleteShader(vertex);
      if (fragment != 0) GL20.glDeleteShader(fragment);
      if (result != 0) GL20.glDeleteProgram(result);
    }
  }

  private static int shader(int type, String source) {
    int shader = GL20.glCreateShader(type);
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

  private void disable(RuntimeException failure) {
    failed = true;
    close();
    LogManager.getLogger("Perihelion")
        .warn("HUD blur unavailable; using the configured background without blur", failure);
  }

  private static void activeTexture(int unit) {
    GlStateManager.setActiveTexture(unit);
    OpenGlHelper.setActiveTexture(unit);
  }

  private static void bindTexture(int texture) {
    GlStateManager.bindTexture(texture);
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
  }

  private static void parameters() {
    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
  }

  private static void capability(int flag, boolean enabled) {
    switch (flag) {
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
        break;
    }
    if (enabled) GL11.glEnable(flag);
    else GL11.glDisable(flag);
  }

  @Override
  public void close() {
    ready = false;
    filteredSigma = -1;
    horizontal.close();
    vertical.close();
    if (texture != 0) {
      GlStateManager.deleteTexture(texture);
      texture = 0;
    }
    if (program != 0) {
      GL20.glDeleteProgram(program);
      program = 0;
    }
    if (filterProgram != 0) {
      GL20.glDeleteProgram(filterProgram);
      filterProgram = 0;
    }
    width = height = 0;
  }

  private static final class Target {
    int framebuffer, texture, width, height;

    void resize(int w, int h) {
      if (framebuffer != 0 && width == w && height == h) return;
      close();
      width = w;
      height = h;
      texture = GL11.glGenTextures();
      framebuffer = OpenGlHelper.glGenFramebuffers();
      if (texture == 0 || framebuffer == 0)
        throw new IllegalStateException("Cannot allocate HUD blur framebuffer");
      bindTexture(texture);
      parameters();
      GL11.glTexImage2D(
          GL11.GL_TEXTURE_2D,
          0,
          GL11.GL_RGBA8,
          w,
          h,
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
        throw new IllegalStateException("Incomplete HUD blur framebuffer");
    }

    void bind() {
      OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebuffer);
      GlStateManager.viewport(0, 0, width, height);
      GL11.glViewport(0, 0, width, height);
    }

    void close() {
      if (framebuffer != 0) OpenGlHelper.glDeleteFramebuffers(framebuffer);
      if (texture != 0) GlStateManager.deleteTexture(texture);
      framebuffer = texture = width = height = 0;
    }
  }
}
