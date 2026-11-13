package perihelion.smoke;

import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import perihelion.integration.minecraft189.render.HudText;
import perihelion.integration.minecraft189.render.RenderState;
import perihelion.integration.minecraft189.render.RenderStyle;

/** Actual GL/cache and font checks, compiled only into the isolated smoke probe JAR. */
public final class RenderHookProbe {
    private static int checks;
    private RenderHookProbe() { }

    public static void run() throws Exception {
        RenderState original = new RenderState(), outer = new RenderState(), inner = new RenderState();
        try (RenderState saved = original.open()) {
            // Deliberately non-default state, including a stale Minecraft cache from raw GL calls.
            try (RenderState ignored = outer.open()) { RenderStyle.text(); }
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
            GL11.glLineWidth(2);
            GL11.glColor4f(0.25f, 0.5f, 0.75f, 0.8f);
            GL11.glEnable(GL11.GL_SCISSOR_TEST); GL11.glScissor(2, 3, 100, 110);
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glTranslatef(0.1f, 0.2f, 0);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glMatrixMode(GL11.GL_TEXTURE); GL11.glTranslatef(0.1f, 0.2f, 0);
            Map<String, String> before = readState();
            RuntimeException expected = new IllegalStateException("intentional render failure");
            try {
                try (RenderState ignored = outer.open()) {
                    RenderStyle.lines(true, 3, 0xff00ff00);
                    Map<String, String> nestedBefore = readState();
                    try (RenderState nested = inner.open()) {
                        RenderStyle.text();
                        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPushMatrix(); GL11.glTranslatef(8, 9, 10);
                    }
                    check(nestedBefore.equals(readState()), "Nested state differs");
                    GL11.glDepthMask(true); GL11.glEnable(GL11.GL_DEPTH_TEST);
                    GL20.glBlendEquationSeparate(GL14.GL_FUNC_SUBTRACT, GL14.GL_FUNC_REVERSE_SUBTRACT);
                    GL11.glDisable(GL11.GL_SCISSOR_TEST);
                    GL13.glActiveTexture(GL13.GL_TEXTURE1); GL11.glDisable(GL11.GL_TEXTURE_2D);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
                    GL11.glMatrixMode(GL11.GL_TEXTURE); GL11.glPushMatrix(); GL11.glLoadIdentity();
                    throw expected;
                }
            } catch (RuntimeException failure) { check(failure == expected, "Render exception replaced"); }
            Map<String, String> after = readState();
            for (Map.Entry<String, String> entry : before.entrySet()) {
                check(entry.getValue().equals(after.get(entry.getKey())), "Not restored: " + entry.getKey()
                        + " before=" + entry.getValue() + " after=" + after.get(entry.getKey()));
            }
            gl("enableDepth", "func_179126_j"); check(GL11.glIsEnabled(GL11.GL_DEPTH_TEST), "Depth cache stale");
            gl("disableDepth", "func_179097_i"); check(!GL11.glIsEnabled(GL11.GL_DEPTH_TEST), "Depth disable cache stale");
            gl("disableBlend", "func_179084_k"); check(!GL11.glIsEnabled(GL11.GL_BLEND), "Blend cache stale");
            gl("enableBlend", "func_179147_l"); check(GL11.glIsEnabled(GL11.GL_BLEND), "Blend enable cache stale");
            for (int i = 0; i < 100; i++) {
                try (RenderState ignored = outer.open()) { RenderStyle.lines(false, 1, 0xffffffff); }
            }
            check(GL11.glGetError() == GL11.GL_NO_ERROR, "GL error after repeated restoration");
        }

        HudText text = new HudText();
        Map<String, String> beforeText = readState();
        text.draw("Perihelion render helpers", 8.2f, 8.2f, 0xffffffff, true);
        try (RenderState batch = outer.open()) {
            text.drawInScope("Plain: ABC abc 0123456789", 8, 21, 0xffeeeeee, false);
            text.drawInScope("\u00a7bColor \u00a7lBold\u00a7r / \u00a7oItalic\u00a7r", 8, 34, 0xffffffff, true);
            text.drawInScope("Unicode: caf\u00e9 \u03a9 \u0416", 8, 47, 0xffffffff, true);
            text.drawInScope("Half alpha", 8, 60, 0x80ffffff, false);
            text.drawInScope("Invisible", 8, 73, 0x00ffffff, false);
        }
        check(text.width("Perihelion") > 0 && text.height() > 0, "Invalid font metrics");
        try (RenderState batch = outer.open();
             perihelion.integration.minecraft189.gui.font.GuiFonts fonts =
                 new perihelion.integration.minecraft189.gui.font.GuiFonts()) {
            perihelion.integration.minecraft189.gui.font.GuiFonts.Face small =
                fonts.get(perihelion.integration.minecraft189.gui.ClickGuiModule.Font.MINECRAFT);
            check(small.width("Minecraft") == text.width("Minecraft"),
                "Minecraft width must match native bitmap glyphs without downsampling");
            check(small.height() == text.height(),
                "Minecraft line height preserves every native bitmap row");
            int row = 0;
            for (perihelion.integration.minecraft189.gui.ClickGuiModule.Font choice :
                perihelion.integration.minecraft189.gui.ClickGuiModule.Font.values()) {
                perihelion.integration.minecraft189.gui.font.GuiFonts.Face face = fonts.get(choice);
                check(face.width("Wi\u03a9\u0416") == face.width("W") + face.width("i")
                    + face.width("\u03a9") + face.width("\u0416"),
                    "Selected font metrics agree with mixed Unicode drawing: " + choice);
                face.draw("Font sample: Wi 012 caf\u00e9 \u03a9 \u0416", 8, 75 + row++ * 13, 0xffeeeeee);
            }
        }
        check(beforeText.equals(readState()), "Text leaked rendering state");
        check(GL11.glGetError() == GL11.GL_NO_ERROR, "GL error after text");
        System.out.println("[PERIHELION_SMOKE] RENDER PASS checks=" + checks + " state=restored fonts=native");
    }

    private static Map<String, String> readState() {
        Map<String, String> result = new LinkedHashMap<>();
        int[] flags = { GL11.GL_DEPTH_TEST, GL11.GL_BLEND, GL11.GL_ALPHA_TEST, GL11.GL_LIGHTING,
                GL11.GL_FOG, GL11.GL_CULL_FACE, GL11.GL_POLYGON_OFFSET_FILL, GL11.GL_COLOR_MATERIAL,
                GL11.GL_NORMALIZE, org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL, GL11.GL_COLOR_LOGIC_OP,
                GL11.GL_LINE_SMOOTH, GL11.GL_SCISSOR_TEST };
        for (int flag : flags) result.put("enable:" + flag, Boolean.toString(GL11.glIsEnabled(flag)));
        int[] integers = { GL11.GL_DEPTH_FUNC, GL11.GL_ALPHA_TEST_FUNC, GL11.GL_SHADE_MODEL,
                GL11.GL_CULL_FACE_MODE, GL11.GL_LOGIC_OP_MODE, GL14.GL_BLEND_SRC_RGB, GL14.GL_BLEND_DST_RGB,
                GL14.GL_BLEND_SRC_ALPHA, GL14.GL_BLEND_DST_ALPHA, GL14.GL_BLEND_EQUATION,
                GL20.GL_BLEND_EQUATION_ALPHA, GL11.GL_MATRIX_MODE, GL13.GL_ACTIVE_TEXTURE,
                GL11.GL_MODELVIEW_STACK_DEPTH, GL11.GL_PROJECTION_STACK_DEPTH };
        for (int name : integers) result.put("integer:" + name, Integer.toString(GL11.glGetInteger(name)));
        result.put("depthMask", Boolean.toString(GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)));
        FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
        int[] names = { GL11.GL_ALPHA_TEST_REF, GL11.GL_LINE_WIDTH, GL11.GL_POLYGON_OFFSET_FACTOR,
                GL11.GL_POLYGON_OFFSET_UNITS, GL11.GL_CURRENT_COLOR, GL11.GL_COLOR_WRITEMASK,
                GL11.GL_SCISSOR_BOX, GL11.GL_DEPTH_RANGE, GL11.GL_MODELVIEW_MATRIX, GL11.GL_PROJECTION_MATRIX };
        for (int name : names) result.put("float:" + name, vector(name, buffer));
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        for (int unit = GL13.GL_TEXTURE0; unit <= GL13.GL_TEXTURE1; unit++) {
            GL13.glActiveTexture(unit);
            result.put("texture:" + unit, GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) + ":" + GL11.glIsEnabled(GL11.GL_TEXTURE_2D));
            result.put("textureMatrix:" + unit, vector(GL11.GL_TEXTURE_MATRIX, buffer));
            result.put("textureStack:" + unit, Integer.toString(GL11.glGetInteger(GL11.GL_TEXTURE_STACK_DEPTH)));
        }
        GL13.glActiveTexture(active);
        return result;
    }

    private static String vector(int name, FloatBuffer buffer) {
        for (int i = 0; i < 16; i++) buffer.put(i, 0);
        GL11.glGetFloat(name, buffer);
        float[] values = new float[16];
        for (int i = 0; i < 16; i++) values[i] = buffer.get(i);
        return Arrays.toString(values);
    }

    private static void gl(String named, String srg) throws Exception {
        Class<?> type = Class.forName("net.minecraft.client.renderer.GlStateManager");
        Method method;
        try { method = type.getMethod(named); }
        catch (NoSuchMethodException missing) { method = type.getMethod(srg); }
        method.invoke(null);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
