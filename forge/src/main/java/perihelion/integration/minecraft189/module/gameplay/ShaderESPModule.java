package perihelion.integration.minecraft189.module.gameplay;

import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.RenderManager;
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
import java.util.ArrayList;
import java.util.List;

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
        private static final String VERTEX =
                "#version 120\n"
                        + "void main(){gl_Position=ftransform();gl_TexCoord[0]=gl_MultiTexCoord0;}";
        // Outline only needs the 3x3 neighbourhood; glow keeps its 9x9 Gaussian falloff.
        private static final String OUTLINE =
                "#version 120\n"
                        + "uniform sampler2D mask;uniform vec2 pixel;void main(){vec2"
                        + " uv=gl_TexCoord[0].xy;float center=texture2D(mask,uv).a;float"
                        + " edge=0.0;for(int x=-1;x<=1;x++){for(int y=-1;y<=1;y++){edge=max(edge,"
                        + "texture2D(mask,uv+vec2(float(x),float(y))*pixel).a);}}"
                        + "gl_FragColor=vec4(0.68,0.55,1.0,max(0.0,edge-center));}";
        private static final String GLOW =
                "#version 120\n"
                        + "uniform sampler2D mask;uniform vec2 pixel;void main(){vec2"
                        + " uv=gl_TexCoord[0].xy;float center=texture2D(mask,uv).a;float"
                        + " sum=0.0;for(int x=-4;x<=4;x++){for(int y=-4;y<=4;y++){vec2"
                        + " d=vec2(float(x),float(y));sum+=texture2D(mask,uv+d*pixel).a"
                        + "*exp(-dot(d,d)/8.0);}}"
                        + "gl_FragColor=vec4(0.68,0.55,1.0,min(0.85,sum/12.0)*(1.0-center));}";

        private final RenderState state = new RenderState();
        private final IntBuffer viewport = BufferUtils.createIntBuffer(16);
        private final FloatBuffer clearColor = BufferUtils.createFloatBuffer(16);
        private final List<EntityPlayer> visible = new ArrayList<>();
        private final VisualDraw.Projection projection = new VisualDraw.Projection();
        // Index 0 is outline, 1 is glow.
        private final int[] programs = new int[2], maskUniforms = new int[2], pixelUniforms = new int[2];
        private boolean drawingMask;
        private Framebuffer mask;

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

        private int program(int index) {
            if (programs[index] != 0) return programs[index];
            int vertex = shader(GL20.GL_VERTEX_SHADER, VERTEX), fragment = 0, program = 0;
            try {
                fragment = shader(GL20.GL_FRAGMENT_SHADER, index == 1 ? GLOW : OUTLINE);
                program = GL20.glCreateProgram();
                GL20.glAttachShader(program, vertex);
                GL20.glAttachShader(program, fragment);
                GL20.glLinkProgram(program);
                if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
                    String reason = GL20.glGetProgramInfoLog(program, 4096);
                    GL20.glDeleteProgram(program);
                    throw new IllegalStateException(reason);
                }
                maskUniforms[index] = GL20.glGetUniformLocation(program, "mask");
                pixelUniforms[index] = GL20.glGetUniformLocation(program, "pixel");
                programs[index] = program;
                return program;
            } finally {
                GL20.glDeleteShader(vertex);
                if (fragment != 0) GL20.glDeleteShader(fragment);
            }
        }

        private void prepare() {
            if (!OpenGlHelper.isFramebufferEnabled() || !GLContext.getCapabilities().OpenGL20)
                throw new IllegalStateException("Shader ESP needs framebuffer and GLSL support");
            if (mask == null
                    || mask.framebufferWidth != mc().displayWidth
                    || mask.framebufferHeight != mc().displayHeight) {
                if (mask != null) mask.deleteFramebuffer();
                mask = new Framebuffer(mc().displayWidth, mc().displayHeight, true);
                mask.setFramebufferColor(0, 0, 0, 0);
            }
        }

        /** Players vanilla would draw this frame: in render range and inside the view frustum. */
        private void collectVisible() {
            visible.clear();
            RenderManager manager = mc().getRenderManager();
            Frustum frustum = new Frustum();
            double x = manager.viewerPosX, y = manager.viewerPosY, z = manager.viewerPosZ;
            frustum.setPosition(x, y, z);
            for (EntityPlayer player : mc().theWorld.playerEntities)
                if ((player != mc().thePlayer || mc().gameSettings.thirdPersonView != 0)
                        && manager.shouldRender(player, frustum, x, y, z)) visible.add(player);
        }

        /**
         * Window-pixel scissor {x, y, width, height} around the visible silhouettes plus the
         * kernel radius, or null when a player crosses the near plane and needs the full screen.
         */
        private int[] screenBounds(float partial, int radius) {
            projection.capture(mc());
            int scale = new ScaledResolution(mc()).getScaleFactor();
            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            for (EntityPlayer player : visible) {
                // The model, held items and swinging limbs reach past the collision box.
                double[] bounds =
                        projection.box(VisualDraw.bounds(player, partial).expand(0.6, 0.4, 0.6));
                if (bounds == null) return null;
                minX = Math.min(minX, bounds[0]);
                minY = Math.min(minY, bounds[1]);
                maxX = Math.max(maxX, bounds[2]);
                maxY = Math.max(maxY, bounds[3]);
            }
            int pad = radius + 2, width = mc().displayWidth, height = mc().displayHeight;
            int left = Math.max(0, (int) Math.floor(minX * scale) - pad);
            int right = Math.min(width, (int) Math.ceil(maxX * scale) + pad);
            int bottom = Math.max(0, height - (int) Math.ceil(maxY * scale) - pad);
            int top = Math.min(height, height - (int) Math.floor(minY * scale) + pad);
            return new int[] {left, bottom, Math.max(0, right - left), Math.max(0, top - bottom)};
        }

        void draw(float partial, boolean glow) {
            collectVisible();
            // Nothing to outline: skip the mask pass, the full-screen filter and the GL queries.
            if (visible.isEmpty()) return;
            int index = glow ? 1 : 0;
            int[] scissor = screenBounds(partial, glow ? 4 : 1);
            if (scissor != null && (scissor[2] == 0 || scissor[3] == 0)) return;
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
                    int program = program(index);
                    mask.framebufferClear();
                    mask.bindFramebuffer(true);
                    GL20.glUseProgram(0);
                    GlStateManager.enableDepth();
                    GlStateManager.depthMask(true);
                    drawingMask = true;
                    mc().getRenderManager().setRenderShadow(false);
                    for (EntityPlayer player : visible)
                        mc().getRenderManager().renderEntityStatic(player, partial, true);
                    drawingMask = false;
                    mc().getRenderManager().setRenderShadow(shadow);
                    OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebuffer);
                    GL11.glViewport(
                            viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
                    RenderStyle.text();
                    GlStateManager.disableAlpha();
                    if (scissor != null) {
                        GL11.glEnable(GL11.GL_SCISSOR_TEST);
                        GL11.glScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
                    }
                    GL11.glMatrixMode(GL11.GL_PROJECTION);
                    GL11.glLoadIdentity();
                    GL11.glOrtho(0, 1, 0, 1, -1, 1);
                    GL11.glMatrixMode(GL11.GL_MODELVIEW);
                    GL11.glLoadIdentity();
                    GlStateManager.bindTexture(mask.framebufferTexture);
                    GL20.glUseProgram(program);
                    GL20.glUniform1i(maskUniforms[index], 0);
                    GL20.glUniform2f(
                            pixelUniforms[index],
                            1f / mask.framebufferWidth,
                            1f / mask.framebufferHeight);
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
                    visible.clear();
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
            visible.clear();
            if (mask != null) {
                mask.deleteFramebuffer();
                mask = null;
            }
            for (int i = 0; i < programs.length; i++) {
                if (programs[i] != 0) GL20.glDeleteProgram(programs[i]);
                programs[i] = 0;
            }
        }
    }
}
