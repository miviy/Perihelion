package perihelion.core.concurrent;

import org.junit.Test;
import perihelion.core.diagnostics.ClientLog;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class ClientTaskQueueTest {
    private final ClientTaskQueue queue = new ClientTaskQueue(Thread.currentThread(), ClientLog.NONE);
    @Test public void boundedQueueDrainsAtMost128AndAcceptsConcurrentProducer() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Thread producer = new Thread(() -> {
            for (int i = 0; i < 1024; i++) assertTrue(queue.execute(calls::incrementAndGet));
        });
        producer.start(); producer.join();
        assertEquals(1024, queue.pendingCount());
        assertFalse(queue.execute(calls::incrementAndGet));
        queue.drain();
        assertEquals(128, calls.get());
        assertEquals(896, queue.pendingCount());
        queue.close(); assertEquals(0, queue.pendingCount());
        assertFalse(queue.execute(calls::incrementAndGet));
    }
    @Test public void staleWorldWorkIsDiscardedAndFailuresDoNotStopQueue() {
        AtomicInteger calls = new AtomicInteger();
        queue.executeForGeneration(queue.generation(), () -> fail("stale work"));
        queue.advanceGeneration();
        queue.execute(() -> { throw new IllegalStateException("test"); });
        queue.executeForGeneration(queue.generation(), calls::incrementAndGet);
        queue.execute(calls::incrementAndGet);
        queue.drain();
        assertEquals(2, calls.get()); assertEquals(0, queue.pendingCount());
    }
    @Test public void selfReschedulingCannotStarveTheTick() {
        AtomicInteger calls = new AtomicInteger();
        Runnable[] task = new Runnable[1];
        task[0] = () -> { calls.incrementAndGet(); queue.execute(task[0]); };
        queue.execute(task[0]); queue.drain();
        assertEquals(128, calls.get()); assertEquals(1, queue.pendingCount());
        queue.close();
    }
}
