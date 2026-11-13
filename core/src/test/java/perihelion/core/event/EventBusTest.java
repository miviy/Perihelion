package perihelion.core.event;

import org.junit.Test;
import perihelion.core.concurrent.ClientTaskQueue;
import perihelion.core.diagnostics.ClientLog;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

public class EventBusTest {
    private final EventBus bus = new EventBus(() -> {}, ClientLog.NONE);
    private EventOwner owner(String id) {
        EventOwner owner = new EventOwner(id, failure -> {});
        owner.activate();
        return owner;
    }
    static class Cancel extends CancellableEvent {}
    static class Child extends Cancel {}

    @Test public void priorityTiesAndExactTypesAreDeterministic() {
        List<Integer> order = new ArrayList<>();
        EventOwner owner = owner("test");
        bus.subscribe(owner, Cancel.class, 0, false, e -> order.add(3));
        bus.subscribe(owner, Cancel.class, 10, false, e -> order.add(1));
        bus.subscribe(owner, Cancel.class, 10, false, e -> order.add(2));
        bus.post(new Child());
        assertTrue(order.isEmpty());
        bus.post(new Cancel());
        assertEquals(Arrays.asList(1, 2, 3), order);
    }
    @Test public void failingOwnerRollsBackOnlyItsCancellationAndDetachesEveryListener() {
        List<String> calls = new ArrayList<>();
        AtomicInteger faults = new AtomicInteger();
        EventOwner bad = new EventOwner("bad", failure -> faults.incrementAndGet());
        bad.activate();
        bus.subscribe(bad, Cancel.class, 20, false, e -> { e.cancel(); throw new Exception("test"); });
        bus.subscribe(bad, Cancel.class, 10, true, e -> calls.add("bad-again"));
        bus.subscribe(owner("good"), Cancel.class, e -> calls.add("good"));
        assertFalse(bus.post(new Cancel()).isCancelled());
        bus.post(new Cancel());
        assertEquals(Arrays.asList("good", "good"), calls);
        assertEquals(1, faults.get());
        assertEquals(1, bus.subscriptionCount());
        assertTrue(bad.isClosed());
    }
    @Test public void earlierCancellationSurvivesFailingObserver() {
        EventOwner first = owner("first"), bad = owner("bad"), last = owner("last");
        AtomicInteger seen = new AtomicInteger();
        bus.subscribe(first, Cancel.class, 20, false, CancellableEvent::cancel);
        bus.subscribe(bad, Cancel.class, 10, true, e -> { throw new AssertionError("nonfatal"); });
        bus.subscribe(last, Cancel.class, e -> fail("cancelled event delivered"));
        bus.subscribe(last, Cancel.class, -1, true, e -> seen.incrementAndGet());
        assertTrue(bus.post(new Cancel()).isCancelled());
        assertEquals(1, seen.get());
    }
    @Test public void additionsWaitUntilNextDispatchAndRemovalsTakeEffectImmediately() {
        List<String> seen = new ArrayList<>();
        EventOwner owner = owner("test");
        Subscription[] removed = new Subscription[1];
        AtomicBoolean added = new AtomicBoolean();
        bus.subscribe(owner, String.class, 10, false, e -> {
            seen.add("first");
            removed[0].close();
            if (added.compareAndSet(false, true)) bus.subscribe(owner, String.class, x -> seen.add("new"));
        });
        removed[0] = bus.subscribe(owner, String.class, e -> seen.add("removed"));
        bus.post("one");
        assertEquals(Arrays.asList("first"), seen);
        bus.post("two");
        assertEquals(Arrays.asList("first", "first", "new"), seen);
        owner.close(); owner.close();
        assertEquals(0, bus.subscriptionCount());
        assertThrows(IllegalStateException.class, () -> bus.subscribe(owner, String.class, e -> {}));
    }
    @Test public void stagedListenersAreInactiveAndBusCloseIsIdempotent() {
        EventOwner staged = new EventOwner("staged", failure -> {});
        AtomicInteger calls = new AtomicInteger();
        bus.subscribe(staged, String.class, e -> calls.incrementAndGet());
        bus.post("before"); assertEquals(0, calls.get());
        staged.activate(); bus.post("after"); assertEquals(1, calls.get());
        bus.close(); bus.close(); staged.close();
        assertFalse(bus.hasListeners(String.class));
        assertThrows(IllegalStateException.class, () -> bus.subscribe(owner("later"), String.class, e -> {}));
    }
    @Test public void fatalErrorsPropagate() {
        OutOfMemoryError fatal = new OutOfMemoryError("test sentinel, no allocation failure");
        bus.subscribe(owner("fatal"), String.class, e -> { throw fatal; });
        assertSame(fatal, assertThrows(OutOfMemoryError.class, () -> bus.post("event")));
    }
    @Test public void mainThreadGuardAndOptionalTiming() throws Exception {
        ClientTaskQueue tasks = new ClientTaskQueue(Thread.currentThread(), ClientLog.NONE);
        EventBus guarded = new EventBus(tasks::checkThread, ClientLog.NONE);
        AtomicReference<Throwable> result = new AtomicReference<>();
        Thread other = new Thread(() -> { try { guarded.post("x"); } catch (Throwable e) { result.set(e); } });
        other.start(); other.join();
        assertTrue(result.get() instanceof IllegalStateException);
        guarded.subscribe(owner("timing"), String.class, e -> {});
        guarded.post("x"); assertEquals(0, guarded.measuredDispatches());
        guarded.setTimingEnabled(true); guarded.post("x");
        assertEquals(1, guarded.measuredDispatches());
        assertTrue(guarded.measuredNanos() > 0);
    }
    @Test public void concurrentNetworkOwnersCloseWithoutRetainedEntries() throws Exception {
        for (int cycle = 0; cycle < 40; cycle++) {
            EventOwner owner = owner("concurrent");
            Thread subscribe = new Thread(() -> {
                for (int i = 0; i < 100; i++) {
                    try { bus.subscribe(owner, String.class, e -> {}); }
                    catch (IllegalStateException closed) { return; }
                }
            });
            subscribe.start(); owner.close(); subscribe.join();
            assertEquals(0, bus.subscriptionCount());
        }
    }
}
