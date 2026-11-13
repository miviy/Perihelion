package perihelion.integration.minecraft189.render;

import net.minecraft.client.Minecraft;

/** Reusable, client-thread-only rendering scope. Keep one per renderer, not per primitive. */
public final class RenderState implements AutoCloseable {
    interface Snapshot {
        void capture();
        void restore();
    }

    private static RenderState top;
    private final Snapshot snapshot;
    private final Runnable checkThread;
    private RenderState previous;
    private boolean active;

    public RenderState() {
        this(new GlSnapshot(), () -> {
            if (!Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
                throw new IllegalStateException("Rendering requires the Minecraft client thread");
            }
        });
    }

    RenderState(Snapshot snapshot, Runnable checkThread) {
        this.snapshot = snapshot;
        this.checkThread = checkThread;
    }

    static void requireOpen() {
        if (top == null) throw new IllegalStateException("Open a RenderState scope before applying a render style");
        top.checkThread.run();
    }

    /** Use only as a lexical try-with-resources scope; do not retain the returned alias. */
    public RenderState open() {
        checkThread.run();
        if (active) throw new IllegalStateException("RenderState is already open; use a separate instance for nesting");
        snapshot.capture();
        previous = top;
        top = this;
        active = true;
        return this;
    }

    @Override public void close() {
        checkThread.run();
        if (!active) return;
        if (top != this) throw new IllegalStateException("Render scopes must close in reverse order");
        try { snapshot.restore(); }
        finally {
            top = previous;
            previous = null;
            active = false;
        }
    }
}
