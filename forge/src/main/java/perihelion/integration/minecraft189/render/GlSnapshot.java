package perihelion.integration.minecraft189.render;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

/** Explicit fixed-function state; see docs/RENDERING.md for the boundary. */
final class GlSnapshot implements RenderState.Snapshot {
    private static final int[] CAPS = { GL11.GL_DEPTH_TEST, GL11.GL_BLEND, GL11.GL_ALPHA_TEST,
            GL11.GL_LIGHTING, GL11.GL_FOG, GL11.GL_CULL_FACE, GL11.GL_POLYGON_OFFSET_FILL,
            GL11.GL_COLOR_MATERIAL, GL11.GL_NORMALIZE, org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL,
            GL11.GL_COLOR_LOGIC_OP, GL11.GL_LINE_SMOOTH, GL11.GL_SCISSOR_TEST };
    private final boolean[] enabled = new boolean[CAPS.length];
    private final FloatBuffer color = BufferUtils.createFloatBuffer(16);
    private final DoubleBuffer depthRange = BufferUtils.createDoubleBuffer(16);
    private final FloatBuffer modelview = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer projection = BufferUtils.createFloatBuffer(16);
    private final IntBuffer scissor = BufferUtils.createIntBuffer(16);
    private final ByteBuffer colorMask = BufferUtils.createByteBuffer(16);
    private final TextureState base = new TextureState();
    private final TextureState lightmap = new TextureState();
    private boolean depthMask, separateEquation;
    private int activeTexture, matrixMode, depthFunc, alphaFunc, shadeModel, cullFace, logicOp;
    private int modelviewDepth, projectionDepth;
    private int srcRgb, dstRgb, srcAlpha, dstAlpha, equationRgb, equationAlpha;
    private float alphaRef, lineWidth, offsetFactor, offsetUnits;

    @Override public void capture() {
        if (!GLContext.getCapabilities().OpenGL14) throw new IllegalStateException("RenderState requires OpenGL 1.4 or newer");
        // Query the driver once per batch, never once per vertex or glyph.
        for (int i = 0; i < CAPS.length; i++) enabled[i] = GL11.glIsEnabled(CAPS[i]);
        depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        GL11.glGetDouble(GL11.GL_DEPTH_RANGE, depthRange);
        alphaFunc = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
        alphaRef = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
        shadeModel = GL11.glGetInteger(GL11.GL_SHADE_MODEL);
        cullFace = GL11.glGetInteger(GL11.GL_CULL_FACE_MODE);
        logicOp = GL11.glGetInteger(GL11.GL_LOGIC_OP_MODE);
        srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        separateEquation = GLContext.getCapabilities().OpenGL20;
        equationRgb = GL11.glGetInteger(GL14.GL_BLEND_EQUATION);
        equationAlpha = separateEquation ? GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA) : equationRgb;
        lineWidth = GL11.glGetFloat(GL11.GL_LINE_WIDTH);
        offsetFactor = GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_FACTOR);
        offsetUnits = GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_UNITS);
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, color);
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, colorMask);
        GL11.glGetInteger(GL11.GL_SCISSOR_BOX, scissor);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelview);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
        modelviewDepth = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);
        projectionDepth = GL11.glGetInteger(GL11.GL_PROJECTION_STACK_DEPTH);
        matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        try {
            activeTexture(OpenGlHelper.defaultTexUnit); base.capture();
            activeTexture(OpenGlHelper.lightmapTexUnit); lightmap.capture();
        } finally { activeTexture(activeTexture); }
    }

    @Override public void restore() {
        // Update Minecraft's cache AND the driver. The raw call is intentional: a renderer
        // may have used GL11 directly while Minecraft's cache still held the saved value.
        for (int i = 0; i < CAPS.length; i++) capability(CAPS[i], enabled[i]);
        GlStateManager.depthMask(depthMask); GL11.glDepthMask(depthMask);
        GlStateManager.depthFunc(depthFunc); GL11.glDepthFunc(depthFunc);
        GL11.glDepthRange(depthRange.get(0), depthRange.get(1));
        GlStateManager.alphaFunc(alphaFunc, alphaRef); GL11.glAlphaFunc(alphaFunc, alphaRef);
        GlStateManager.shadeModel(shadeModel); GL11.glShadeModel(shadeModel);
        GlStateManager.cullFace(cullFace); GL11.glCullFace(cullFace);
        GlStateManager.colorLogicOp(logicOp); GL11.glLogicOp(logicOp);
        GlStateManager.tryBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
        OpenGlHelper.glBlendFunc(srcRgb, dstRgb, srcAlpha, dstAlpha);
        if (separateEquation) GL20.glBlendEquationSeparate(equationRgb, equationAlpha);
        else GL14.glBlendEquation(equationRgb);
        GL11.glLineWidth(lineWidth);
        GlStateManager.doPolygonOffset(offsetFactor, offsetUnits); GL11.glPolygonOffset(offsetFactor, offsetUnits);
        boolean r = colorMask.get(0) != 0, g = colorMask.get(1) != 0, b = colorMask.get(2) != 0, a = colorMask.get(3) != 0;
        GlStateManager.colorMask(r, g, b, a); GL11.glColorMask(r, g, b, a);
        GlStateManager.resetColor();
        GlStateManager.color(color.get(0), color.get(1), color.get(2), color.get(3));
        GL11.glScissor(scissor.get(0), scissor.get(1), scissor.get(2), scissor.get(3));
        activeTexture(OpenGlHelper.defaultTexUnit); base.restore();
        activeTexture(OpenGlHelper.lightmapTexUnit); lightmap.restore();
        activeTexture(activeTexture);
        restoreMatrix(GL11.GL_PROJECTION, GL11.GL_PROJECTION_STACK_DEPTH, projectionDepth, projection);
        restoreMatrix(GL11.GL_MODELVIEW, GL11.GL_MODELVIEW_STACK_DEPTH, modelviewDepth, modelview);
        GL11.glMatrixMode(matrixMode);
    }

    private static void restoreMatrix(int mode, int depthName, int savedDepth, FloatBuffer matrix) {
        GL11.glMatrixMode(mode);
        int depth = GL11.glGetInteger(depthName);
        // Unwind pushes skipped by an exception. Popping an enclosing renderer's matrices
        // is outside the contract: the contents of those lost lower entries are unknowable.
        while (depth-- > savedDepth) GL11.glPopMatrix();
        GL11.glLoadMatrix(matrix);
    }

    static void activeTexture(int unit) {
        GlStateManager.setActiveTexture(unit);
        OpenGlHelper.setActiveTexture(unit);
    }

    static void capability(int cap, boolean enabled) {
        switch (cap) {
            case GL11.GL_DEPTH_TEST: if (enabled) GlStateManager.enableDepth(); else GlStateManager.disableDepth(); break;
            case GL11.GL_BLEND: if (enabled) GlStateManager.enableBlend(); else GlStateManager.disableBlend(); break;
            case GL11.GL_ALPHA_TEST: if (enabled) GlStateManager.enableAlpha(); else GlStateManager.disableAlpha(); break;
            case GL11.GL_TEXTURE_2D: if (enabled) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D(); break;
            case GL11.GL_LIGHTING: if (enabled) GlStateManager.enableLighting(); else GlStateManager.disableLighting(); break;
            case GL11.GL_FOG: if (enabled) GlStateManager.enableFog(); else GlStateManager.disableFog(); break;
            case GL11.GL_CULL_FACE: if (enabled) GlStateManager.enableCull(); else GlStateManager.disableCull(); break;
            case GL11.GL_POLYGON_OFFSET_FILL: if (enabled) GlStateManager.enablePolygonOffset(); else GlStateManager.disablePolygonOffset(); break;
            case GL11.GL_COLOR_MATERIAL: if (enabled) GlStateManager.enableColorMaterial(); else GlStateManager.disableColorMaterial(); break;
            case GL11.GL_NORMALIZE: if (enabled) GlStateManager.enableNormalize(); else GlStateManager.disableNormalize(); break;
            case org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL: if (enabled) GlStateManager.enableRescaleNormal(); else GlStateManager.disableRescaleNormal(); break;
            case GL11.GL_COLOR_LOGIC_OP: if (enabled) GlStateManager.enableColorLogic(); else GlStateManager.disableColorLogic(); break;
            default: break; // No Minecraft cache for line smoothing or scissor.
        }
        if (enabled) GL11.glEnable(cap); else GL11.glDisable(cap);
    }

    private static final class TextureState {
        final FloatBuffer matrix = BufferUtils.createFloatBuffer(16);
        int binding;
        int depth;
        boolean enabled;
        void capture() {
            binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            enabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
            GL11.glGetFloat(GL11.GL_TEXTURE_MATRIX, matrix);
            depth = GL11.glGetInteger(GL11.GL_TEXTURE_STACK_DEPTH);
        }
        void restore() {
            capability(GL11.GL_TEXTURE_2D, enabled);
            GlStateManager.bindTexture(binding); GL11.glBindTexture(GL11.GL_TEXTURE_2D, binding);
            restoreMatrix(GL11.GL_TEXTURE, GL11.GL_TEXTURE_STACK_DEPTH, depth, matrix);
        }
    }
}
