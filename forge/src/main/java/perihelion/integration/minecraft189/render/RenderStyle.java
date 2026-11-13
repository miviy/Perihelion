package perihelion.integration.minecraft189.render;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/** Apply only inside an open RenderState scope, with the appropriate projection already set. */
public final class RenderStyle {
    private RenderStyle() { }

    public static void lines(boolean throughWalls, float width, int argb) {
        if (!Float.isFinite(width) || width <= 0) throw new IllegalArgumentException("Line width must be finite and positive");
        common();
        GlSnapshot.capability(GL11.GL_TEXTURE_2D, false);
        GlSnapshot.capability(GL11.GL_DEPTH_TEST, !throughWalls);
        GlSnapshot.capability(GL11.GL_ALPHA_TEST, false);
        GL11.glLineWidth(width);
        color(argb);
    }

    public static void text() {
        common();
        GlSnapshot.capability(GL11.GL_TEXTURE_2D, true);
        GlSnapshot.capability(GL11.GL_DEPTH_TEST, false);
        GlSnapshot.capability(GL11.GL_ALPHA_TEST, true);
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0); GL11.glAlphaFunc(GL11.GL_GREATER, 0);
        color(0xffffffff);
    }

    private static void common() {
        RenderState.requireOpen();
        GlSnapshot.activeTexture(OpenGlHelper.lightmapTexUnit);
        GlSnapshot.capability(GL11.GL_TEXTURE_2D, false);
        GlSnapshot.activeTexture(OpenGlHelper.defaultTexUnit);
        GlSnapshot.capability(GL11.GL_LIGHTING, false);
        GlSnapshot.capability(GL11.GL_COLOR_MATERIAL, false);
        GlSnapshot.capability(GL11.GL_FOG, false);
        GlSnapshot.capability(GL11.GL_CULL_FACE, false);
        GlSnapshot.capability(GL11.GL_POLYGON_OFFSET_FILL, false);
        GlSnapshot.capability(GL11.GL_COLOR_LOGIC_OP, false);
        GlSnapshot.capability(GL11.GL_BLEND, true);
        GlStateManager.depthMask(false); GL11.glDepthMask(false);
        GlStateManager.colorMask(true, true, true, true); GL11.glColorMask(true, true, true, true);
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL14.glBlendEquation(GL14.GL_FUNC_ADD);
    }

    public static void color(int argb) {
        RenderState.requireOpen();
        GlStateManager.resetColor();
        GlStateManager.color((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f, (argb >>> 24) / 255f);
    }
}
