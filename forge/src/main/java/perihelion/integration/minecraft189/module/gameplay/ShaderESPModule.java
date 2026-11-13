package perihelion.integration.minecraft189.module.gameplay;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.player.EntityPlayer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

import perihelion.core.event.WorldRenderEvent;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.*;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.event.NameRenderEvent;
import perihelion.integration.minecraft189.render.*;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/** Activation-owned silhouette framebuffer and GLSL outline/glow pass. */
final class ShaderESPModule extends GameplayModule {
    enum Mode {
        GLOW,
        OUTLINE
    }

    private final Setting<Mode> mode;

    ShaderESPModule(ClientRuntime r) {
        super(
                r,
                "shaderesp",
                "Shader ESP",
                "Render an outline or glow around player silhouettes.",
                ModuleCategory.RENDER);
        mode = mode("mode", "Mode", Mode.OUTLINE, Mode.class);
    }

    @Override
    protected void onEnable(ModuleContext c) {
        Silhouette renderer = c.own(new Silhouette());
        c.listen(
                NameRenderEvent.class,
                e -> {
                    if (renderer.drawingMask) e.cancel();
                });
        c.listen(
                WorldRenderEvent.class,
                e -> renderer.draw(e.partialTicks(), mode.get() == Mode.GLOW));
    }

    private final class Silhouette implements AutoCloseable {
        private final RenderState state = new RenderState();
        private final IntBuffer viewport = BufferUtils.createIntBuffer(16);
        private final FloatBuffer clearColor = BufferUtils.createFloatBuffer(16);
        private boolean drawingMask;
        private Framebuffer mask;
        private int program;

        private int shader(int type, String source) {
            int id = GL20.glCreateShader(type);
            GL20.glShaderSource(id, source);
            GL20.glCompileShader(id);
            if (GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == 0) {
                String reason = GL20.glGetShaderInfoLog(id, 4096);
                GL20.glDeleteShader(id);
                throw new IllegalStateException("Silhouette shader: " + reason);
            }
            return id;
        }

        private void prepare() {
            if (!OpenGlHelper.isFramebufferEnabled() || !GLContext.getCapabilities().OpenGL20)
                throw new IllegalStateException("Shader ESP needs framebuffer and GLSL support");
            if (program == 0) {
                int vertex =
                        shader(
                                GL20.GL_VERTEX_SHADER,
                                "#version 120\n"
                                    + "void main(){gl_Position=ftransform();gl_TexCoord[0]=gl_MultiTexCoord0;}");
                int fragment = 0;
                try {
                    fragment =
                            shader(
                                    GL20.GL_FRAGMENT_SHADER,
                                    "#version 120\n"
                                        + "uniform sampler2D mask;uniform vec2 pixel;uniform float"
                                        + " glow;void main(){vec2 uv=gl_TexCoord[0].xy;float"
                                        + " center=texture2D(mask,uv).a;float edge=0.0;float"
                                        + " sum=0.0;for(int x=-4;x<=4;x++){for(int"
                                        + " y=-4;y<=4;y++){vec2 d=vec2(float(x),float(y));float"
                                        + " a=texture2D(mask,uv+d*pixel).a;sum+=a*exp(-dot(d,d)/8.0);if(abs(x)<=1&&abs(y)<=1)edge=max(edge,a);}}float"
                                        + " alpha=glow>0.5?min(0.85,sum/12.0)*(1.0-center):max(0.0,edge-center);gl_FragColor=vec4(0.68,0.55,1.0,alpha);}");
                    program = GL20.glCreateProgram();
                    GL20.glAttachShader(program, vertex);
                    GL20.glAttachShader(program, fragment);
                    GL20.glLinkProgram(program);
                    if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0)
                        throw new IllegalStateException(GL20.glGetProgramInfoLog(program, 4096));
                } finally {
                    GL20.glDeleteShader(vertex);
                    if (fragment != 0) GL20.glDeleteShader(fragment);
                }
            }
            if (mask == null
                    || mask.framebufferWidth != mc().displayWidth
                    || mask.framebufferHeight != mc().displayHeight) {
                if (mask != null) mask.deleteFramebuffer();
                mask = new Framebuffer(mc().displayWidth, mc().displayHeight, true);
                mask.setFramebufferColor(0, 0, 0, 0);
            }
        }

        void draw(float partial, boolean glow) {
            int framebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING),
                    previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            viewport.clear();
            GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
            clearColor.clear();
            GL11.glGetFloat(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
            boolean shadow = mc().getRenderManager().isRenderShadow();
            try (RenderState ignored = state.open()) {
                try {
                    prepare();
                    mask.framebufferClear();
                    mask.bindFramebuffer(true);
                    GL20.glUseProgram(0);
                    GlStateManager.enableDepth();
                    GlStateManager.depthMask(true);
                    drawingMask = true;
                    mc().getRenderManager().setRenderShadow(false);
                    for (EntityPlayer player : mc().theWorld.playerEntities)
                        if (player != mc().thePlayer || mc().gameSettings.thirdPersonView != 0)
                            mc().getRenderManager().renderEntityStatic(player, partial, true);
                    drawingMask = false;
                    mc().getRenderManager().setRenderShadow(shadow);
                    OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebuffer);
                    GL11.glViewport(
                            viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
                    RenderStyle.text();
                    GlStateManager.disableAlpha();
                    GL11.glMatrixMode(GL11.GL_PROJECTION);
                    GL11.glLoadIdentity();
                    GL11.glOrtho(0, 1, 0, 1, -1, 1);
                    GL11.glMatrixMode(GL11.GL_MODELVIEW);
                    GL11.glLoadIdentity();
                    GlStateManager.bindTexture(mask.framebufferTexture);
                    GL20.glUseProgram(program);
                    GL20.glUniform1i(GL20.glGetUniformLocation(program, "mask"), 0);
                    GL20.glUniform2f(
                            GL20.glGetUniformLocation(program, "pixel"),
                            1f / mask.framebufferWidth,
                            1f / mask.framebufferHeight);
                    GL20.glUniform1f(GL20.glGetUniformLocation(program, "glow"), glow ? 1 : 0);
                    GL11.glBegin(GL11.GL_QUADS);
                    GL11.glTexCoord2f(0, 0);
                    GL11.glVertex2f(0, 0);
                    GL11.glTexCoord2f(1, 0);
                    GL11.glVertex2f(1, 0);
                    GL11.glTexCoord2f(1, 1);
                    GL11.glVertex2f(1, 1);
                    GL11.glTexCoord2f(0, 1);
                    GL11.glVertex2f(0, 1);
                    GL11.glEnd();
                } finally {
                    drawingMask = false;
                    mc().getRenderManager().setRenderShadow(shadow);
                    GlStateManager.clearColor(
                            clearColor.get(0),
                            clearColor.get(1),
                            clearColor.get(2),
                            clearColor.get(3));
                    GL20.glUseProgram(previousProgram);
                    OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebuffer);
                    GL11.glViewport(
                            viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
                }
            }
        }

        @Override
        public void close() {
            if (mask != null) {
                mask.deleteFramebuffer();
                mask = null;
            }
            if (program != 0) {
                GL20.glDeleteProgram(program);
                program = 0;
            }
        }
    }
}
