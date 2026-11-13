package perihelion.smoke;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import perihelion.core.event.EventOwner;
import perihelion.core.event.HudRenderEvent;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.integration.minecraft189.render.HudText;
import perihelion.integration.minecraft189.render.RenderState;

/** Runs across actual frames in the isolated world scenario; never shipped as a HUD/module. */
public final class HudHookProbe implements AutoCloseable {
    private final ClientRuntime runtime;
    private final Object minecraft, settings;
    private final Field world, player, screen, hideGui, guiScale, displayWidth, displayHeight, resolution;
    private final Method width, height, factor, showScreen;
    private final EventOwner observer, broken;
    private final RenderState state = new RenderState();
    private final HudText text = new HudText();
    private final FloatBuffer matrix = BufferUtils.createFloatBuffer(16);
    private final RuntimeException expectedFailure = new IllegalStateException("Intentional HUD fixture failure");
    private RenderGameOverlayEvent.Post overlay;
    private Throwable failure;
    private int calls, before, checks, faults, phase, frames, phaseCalls, phasePosts, posts, cancelled, originalScale;
    private boolean originalHide, ownsChat, checkRestored, savedDepth;
    private float savedWidth;

    public HudHookProbe(ClientRuntime runtime, Object minecraft) throws Exception {
        this.runtime = runtime; this.minecraft = minecraft;
        Class<?> game = minecraft.getClass();
        settings = field(game, "gameSettings", "field_71474_y").get(minecraft);
        world = field(game, "theWorld", "field_71441_e");
        player = field(game, "thePlayer", "field_71439_g");
        screen = field(game, "currentScreen", "field_71462_r");
        displayWidth = field(game, "displayWidth", "field_71443_c");
        displayHeight = field(game, "displayHeight", "field_71440_d");
        hideGui = field(settings.getClass(), "hideGUI", "field_74319_N");
        guiScale = field(settings.getClass(), "guiScale", "field_74335_Z");
        resolution = RenderGameOverlayEvent.class.getField("resolution");
        Class<?> scaled = Class.forName("net.minecraft.client.gui.ScaledResolution");
        width = method(scaled, new Class<?>[0], "getScaledWidth", "func_78326_a");
        height = method(scaled, new Class<?>[0], "getScaledHeight", "func_78328_b");
        factor = method(scaled, new Class<?>[0], "getScaleFactor", "func_78325_e");
        showScreen = method(game, new Class<?>[] { Class.forName("net.minecraft.client.gui.GuiScreen") },
                "displayGuiScreen", "func_147108_a");
        observer = new EventOwner("hud-smoke", thrown -> failure = thrown);
        broken = new EventOwner("hud-smoke-broken", thrown -> {
            faults++;
            if (thrown != expectedFailure) failure = thrown;
        });
        runtime.clientEvents().subscribe(broken, HudRenderEvent.class, 10, false, event -> {
            savedDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            savedWidth = GL11.glGetFloat(GL11.GL_LINE_WIDTH);
            checkRestored = true;
            try (RenderState ignored = state.open()) {
                if (savedDepth) GL11.glDisable(GL11.GL_DEPTH_TEST); else GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glLineWidth(3);
                GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPushMatrix(); GL11.glTranslatef(99, 88, 0);
                throw expectedFailure;
            }
        });
        runtime.clientEvents().subscribe(observer, HudRenderEvent.class, this::hud);
        broken.activate(); observer.activate();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void before(RenderGameOverlayEvent.Post event) {
        overlay = event; before = calls;
        if (event.type == RenderGameOverlayEvent.ElementType.ALL) posts++;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void after(RenderGameOverlayEvent.Post event) {
        try {
            boolean eligible = event.type == RenderGameOverlayEvent.ElementType.ALL && runtime.isRunning()
                    && world.get(minecraft) != null && player.get(minecraft) != null && !hideGui.getBoolean(settings);
            check(calls - before == (eligible ? 1 : 0), "HUD must fire once for ALL, and never for other elements/hidden GUI");
        } catch (Throwable thrown) { failure = thrown; }
        finally { overlay = null; }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void cancel(RenderGameOverlayEvent.Pre event) {
        if (phase == 4 && event.type == RenderGameOverlayEvent.ElementType.ALL) { event.setCanceled(true); cancelled++; }
    }

    private void hud(HudRenderEvent event) throws Exception {
        calls++;
        check(runtime.tasks().isClientThread(), "HUD left client thread");
        check(overlay != null && overlay.type == RenderGameOverlayEvent.ElementType.ALL, "HUD is outside Forge Post ALL");
        Object scaled = resolution.get(overlay);
        check(event.partialTicks() == overlay.partialTicks, "Partial ticks differ from Forge");
        check(event.scaledWidth() == (Integer) width.invoke(scaled), "Scaled width differs from Forge");
        check(event.scaledHeight() == (Integer) height.invoke(scaled), "Scaled height differs from Forge");
        check(event.scaleFactor() == (Integer) factor.invoke(scaled), "Scale factor differs from Forge");
        check(world.get(minecraft) != null && player.get(minecraft) != null, "HUD rendered outside a world");
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, matrix);
        check(Math.abs(matrix.get(0) - 2f * event.scaleFactor() / displayWidth.getInt(minecraft)) < 0.000001f,
                "HUD horizontal projection is not scaled GUI space");
        check(Math.abs(matrix.get(5) + 2f * event.scaleFactor() / displayHeight.getInt(minecraft)) < 0.000001f,
                "HUD vertical projection is not scaled GUI space");
        check(Math.abs(matrix.get(12) + 1) < 0.000001f && Math.abs(matrix.get(13) - 1) < 0.000001f,
                "HUD origin is not top-left: x=" + matrix.get(12) + " y=" + matrix.get(13));
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, matrix);
        check(matrix.get(12) == 0 && matrix.get(13) == 0, "HUD modelview has a leaked translation");
        if (checkRestored) {
            check(broken.isClosed() && faults == 1, "Throwing HUD owner was not detached");
            check(GL11.glIsEnabled(GL11.GL_DEPTH_TEST) == savedDepth && GL11.glGetFloat(GL11.GL_LINE_WIDTH) == savedWidth,
                    "Throwing HUD listener contaminated the next listener");
            checkRestored = false;
        }
        if (phase == 1) check(event.scaleFactor() == 1, "GUI scale 1 was not refreshed");
        if (phase == 2) check(event.scaleFactor() == 2, "GUI scale 2 was not refreshed");
        text.draw("HUD event: " + event.scaledWidth() + " x " + event.scaledHeight()
                + " / scale " + event.scaleFactor(), 8, 8, 0xffffffff, true);
    }

    /** Advance only at frame END, outside the overlay callback being tested. */
    public void frameEnd() throws Exception {
        if (failure != null) throw new AssertionError("HUD fixture failed", failure);
        if (phase == 6) return;
        if (phase == 0) {
            if (!runtime.worldReady() || screen.get(minecraft) != null || calls == 0) return;
            originalScale = guiScale.getInt(settings); originalHide = hideGui.getBoolean(settings);
            guiScale.setInt(settings, 1); nextPhase(); return;
        }
        if (++frames < 3) return;
        if (phase == 1) {
            check(calls > phaseCalls, "No HUD at GUI scale 1"); guiScale.setInt(settings, 2);
        } else if (phase == 2) {
            check(calls > phaseCalls, "No HUD at GUI scale 2");
            hideGui.setBoolean(settings, true);
            ownsChat = true;
            showScreen.invoke(minecraft, Class.forName("net.minecraft.client.gui.GuiChat").newInstance());
        } else if (phase == 3) {
            check(calls == phaseCalls && posts > phasePosts, "F1 with an open screen must suppress Perihelion HUD");
            hideGui.setBoolean(settings, false);
        } else if (phase == 4) {
            check(calls == phaseCalls && cancelled > 0, "Cancelled overlay delivered HUD");
        } else if (phase == 5) {
            check(calls > phaseCalls && screen.get(minecraft) != null, "HUD did not resume behind the open screen");
            restoreSettings();
            check(faults == 1, "Throwing listener was retried");
            System.out.println("[PERIHELION_SMOKE] HUD PASS checks=" + checks
                    + " guiScales=1,2 f1=hidden cancelled=hidden openScreen=behind exception=restored");
        }
        nextPhase();
    }

    private void nextPhase() { phase++; frames = 0; phaseCalls = calls; phasePosts = posts; }
    public boolean complete() { return phase == 6; }
    public int subscriptions() { return (observer.isClosed() ? 0 : 1) + (broken.isClosed() ? 0 : 1); }
    private void restoreSettings() throws Exception {
        if (ownsChat) { showScreen.invoke(minecraft, new Object[] { null }); ownsChat = false; }
        guiScale.setInt(settings, originalScale); hideGui.setBoolean(settings, originalHide);
    }
    @Override public void close() throws Exception {
        observer.close(); broken.close(); MinecraftForge.EVENT_BUS.unregister(this);
        overlay = null;
        if (phase > 0 && phase < 6) restoreSettings();
    }
    private void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    private static Field field(Class<?> type, String... names) throws Exception {
        for (String name : names) try { return type.getField(name); } catch (NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(type.getName());
    }
    private static Method method(Class<?> type, Class<?>[] args, String... names) throws Exception {
        for (String name : names) try { return type.getMethod(name, args); } catch (NoSuchMethodException ignored) { }
        throw new NoSuchMethodException(type.getName());
    }
}
