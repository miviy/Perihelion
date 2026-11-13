package perihelion.core.event;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import perihelion.core.concurrent.ClientTaskQueue;
import perihelion.core.diagnostics.ClientLog;
import static org.junit.Assert.*;

public class HudRenderEventTest {
    @Test public void hudPassesStayDistinctFromFrameAndWorldEventsAndKeepTheirResolution() {
        ClientTaskQueue tasks = new ClientTaskQueue(Thread.currentThread(), ClientLog.NONE);
        EventBus bus = new EventBus(tasks::checkThread, ClientLog.NONE);
        EventOwner owner = new EventOwner("hud", failure -> fail(failure.toString()));
        List<HudRenderEvent> received = new ArrayList<>();
        try {
            bus.subscribe(owner, HudRenderEvent.class, received::add);
            owner.activate();
            bus.post(new RenderTickEvent(RenderTickEvent.Phase.END, 0.5f));
            bus.post(new WorldRenderEvent(0.5f));
            assertTrue(received.isEmpty());
            bus.post(new HudRenderEvent(0.25f, 427, 240, 2));
            bus.post(new HudRenderEvent(0.75f, 854, 480, 1));
            assertEquals(2, received.size());
            assertEquals(0.25f, received.get(0).partialTicks(), 0);
            assertEquals(427, received.get(0).scaledWidth());
            assertEquals(240, received.get(0).scaledHeight());
            assertEquals(2, received.get(0).scaleFactor());
            assertEquals(0.75f, received.get(1).partialTicks(), 0);
            assertEquals(854, received.get(1).scaledWidth());
            assertEquals(480, received.get(1).scaledHeight());
            assertEquals(1, received.get(1).scaleFactor());
            owner.close();
            bus.post(new HudRenderEvent(0, 640, 360, 2));
            assertEquals(2, received.size());
            assertFalse(bus.hasListeners(HudRenderEvent.class));
        } finally { owner.close(); bus.close(); tasks.close(); }
    }
}
