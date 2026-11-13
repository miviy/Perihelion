package perihelion.integration.minecraft189.render;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.*;

public class RenderStateTest {
    private int value;
    private int captures, restores;
    private RenderState scope() {
        Thread owner = Thread.currentThread();
        return new RenderState(new RenderState.Snapshot() {
            int saved;
            public void capture() { captures++; saved = value; }
            public void restore() { restores++; value = saved; }
        }, () -> {
            if (Thread.currentThread() != owner) throw new IllegalStateException("wrong thread");
        });
    }

    @Test public void nestedThrowingRenderRestoresEachEntryValueAndCanBeReused() {
        RenderState outer = scope(), inner = scope();
        RuntimeException failure = new IllegalStateException("draw failed");
        for (int i = 0; i < 3; i++) {
            value = 7;
            try (RenderState ignored = outer.open()) {
                value = 11;
                assertSame(failure, assertThrows(RuntimeException.class, () -> {
                    try (RenderState nested = inner.open()) { value = 99; throw failure; }
                }));
                assertEquals(11, value);
            }
            assertEquals(7, value);
        }
        outer.close(); inner.close();
        assertEquals(6, captures); assertEquals(6, restores);
        assertThrows(IllegalStateException.class, RenderState::requireOpen);
    }

    @Test public void outOfOrderCloseAndReentryLeaveScopesRecoverable() {
        RenderState outer = scope(), inner = scope();
        try (RenderState ignored = outer.open()) {
            assertThrows(IllegalStateException.class, outer::open);
            try (RenderState nested = inner.open()) {
                assertThrows(IllegalStateException.class, outer::close);
                assertEquals(0, restores);
            }
        }
        assertEquals(2, captures); assertEquals(2, restores);
    }

    @Test public void wrongThreadCannotCaptureOrCloseAnActiveScope() throws Exception {
        RenderState state = scope();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try { state.open(); } catch (Throwable thrown) { failure.set(thrown); }
        });
        worker.start(); worker.join();
        assertTrue(failure.get() instanceof IllegalStateException); assertEquals(0, captures);
        try (RenderState ignored = state.open()) {
            failure.set(null);
            worker = new Thread(() -> {
                try { state.close(); } catch (Throwable thrown) { failure.set(thrown); }
            });
            worker.start(); worker.join();
            assertTrue(failure.get() instanceof IllegalStateException); assertEquals(0, restores);
        }
        assertEquals(1, restores);
    }

    @Test public void failedCaptureDoesNotPublishAScope() {
        RenderState state = new RenderState(new RenderState.Snapshot() {
            public void capture() { throw new IllegalStateException("capture"); }
            public void restore() { fail("Failed capture must not be restored"); }
        }, () -> {});
        assertThrows(IllegalStateException.class, state::open);
        state.close();
        assertThrows(IllegalStateException.class, RenderState::requireOpen);
    }
}
