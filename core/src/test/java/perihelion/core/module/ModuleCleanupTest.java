package perihelion.core.module;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import perihelion.core.concurrent.ClientTaskQueue;
import perihelion.core.diagnostics.ClientLog;
import perihelion.core.event.EventBus;
import perihelion.core.setting.Setting;
import static org.junit.Assert.*;

public class ModuleCleanupTest {
    private final ClientTaskQueue tasks = new ClientTaskQueue(Thread.currentThread(), ClientLog.NONE);
    private final EventBus client = new EventBus(tasks::checkThread, ClientLog.NONE);
    private final EventBus network = new EventBus(() -> {}, ClientLog.NONE);
    private final ModuleManager manager = new ModuleManager(tasks, client, network, ClientLog.NONE);

    private class Probe extends ClientModule {
        ModuleContext context;
        int disables;
        Probe(String id) { super(new ModuleInfo(id, id, "test-only", ModuleCategory.MISC, false)); }
        @Override protected void onEnable(ModuleContext scope) {
            context = scope;
            scope.listen(String.class, event -> {});
        }
        @Override protected void onDisable() { disables++; }
    }

    private ModuleEntry enable(Probe probe) {
        ModuleEntry entry = manager.register(probe);
        manager.start(); manager.setEnabled(probe.info().id(), true);
        return entry;
    }

    @Test public void detachesListenersThenRunsHookThenResourcesInReverseOrder() {
        List<String> order = new ArrayList<>();
        Probe probe = new Probe("probe") {
            @Override protected void onDisable() {
                super.onDisable();
                assertEquals(0, client.subscriptionCount());
                assertTrue(order.isEmpty());
                order.add("disable");
            }
        };
        ModuleEntry entry = enable(probe);
        AutoCloseable resource = () -> order.add("resource");
        assertSame(resource, probe.context.own(resource));
        probe.context.onCleanup(() -> order.add("callback"));
        manager.setEnabled("probe", false); manager.close(); manager.close();
        assertEquals(Arrays.asList("disable", "callback", "resource"), order);
        assertEquals(1, probe.disables);
        assertEquals(ModuleState.DISABLED, entry.state()); assertNull(entry.lastFailure());
    }

    @Test public void failedEnableReleasesResourcesAndKeepsOriginalFailure() {
        IllegalStateException enableFailure = new IllegalStateException("enable");
        IOException cleanupFailure = new IOException("cleanup");
        AtomicInteger closed = new AtomicInteger();
        Probe probe = new Probe("probe") {
            @Override protected void onEnable(ModuleContext scope) {
                super.onEnable(scope);
                scope.onCleanup(closed::incrementAndGet);
                scope.own(() -> { throw cleanupFailure; });
                throw enableFailure;
            }
        };
        ModuleEntry entry = enable(probe);
        assertSame(enableFailure, entry.lastFailure());
        assertArrayEquals(new Throwable[] { cleanupFailure }, enableFailure.getSuppressed());
        assertEquals(ModuleState.FAULTED, entry.state()); assertFalse(entry.requestedEnabled());
        assertEquals(0, client.subscriptionCount());
        manager.processFaults(); manager.close();
        assertEquals(0, probe.disables); assertEquals(1, closed.get());
    }

    @Test public void cleanupFailuresDoNotStopRemainingResourcesOrOtherModules() {
        List<String> order = new ArrayList<>();
        IOException first = new IOException("first");
        AssertionError second = new AssertionError("second");
        Probe bad = new Probe("bad"), good = new Probe("good");
        ModuleEntry entry = manager.register(bad); manager.register(good);
        manager.start(); manager.setEnabled("bad", true); manager.setEnabled("good", true);
        bad.context.onCleanup(() -> order.add("last"));
        bad.context.own(() -> { order.add("second"); throw second; });
        bad.context.own(() -> { order.add("first"); throw first; });
        good.context.onCleanup(() -> order.add("good"));
        manager.close(); manager.close();
        assertEquals(Arrays.asList("first", "second", "last", "good"), order);
        assertSame(first, entry.lastFailure());
        assertArrayEquals(new Throwable[] { second }, first.getSuppressed());
        assertEquals(ModuleState.FAULTED, entry.state()); assertFalse(entry.requestedEnabled());
        assertEquals(1, bad.disables); assertEquals(1, good.disables);
        assertEquals(0, client.subscriptionCount());
    }

    @Test public void throwingDisableHookStillReleasesResourcesWithoutSelfSuppression() {
        IllegalStateException hookFailure = new IllegalStateException("disable");
        IOException cleanupFailure = new IOException("resource");
        AtomicInteger closed = new AtomicInteger();
        Probe probe = new Probe("probe") {
            @Override protected void onDisable() { super.onDisable(); throw hookFailure; }
        };
        ModuleEntry entry = enable(probe);
        probe.context.onCleanup(closed::incrementAndGet);
        probe.context.own(() -> { throw cleanupFailure; });
        probe.context.own(() -> { throw hookFailure; });
        manager.setEnabled("probe", false); manager.close();
        assertEquals(1, closed.get()); assertEquals(1, probe.disables);
        assertEquals(ModuleState.FAULTED, entry.state()); assertFalse(entry.requestedEnabled());
        assertSame(hookFailure, entry.lastFailure());
        assertArrayEquals(new Throwable[] { cleanupFailure }, hookFailure.getSuppressed());
    }

    @Test public void networkFaultDetachesImmediatelyButCleansOnClientThreadOnce() throws Exception {
        Thread clientThread = Thread.currentThread();
        IllegalStateException networkFailure = new IllegalStateException("network");
        IOException cleanupFailure = new IOException("cleanup");
        AtomicReference<Thread> cleanupThread = new AtomicReference<>();
        AtomicInteger closed = new AtomicInteger();
        Probe probe = new Probe("probe");
        ModuleEntry entry = enable(probe);
        probe.context.onCleanup(() -> { cleanupThread.set(Thread.currentThread()); closed.incrementAndGet(); });
        probe.context.own(() -> { throw cleanupFailure; });
        probe.context.listenNetwork(Integer.class, 0, false, event -> { throw networkFailure; });
        Thread worker = new Thread(() -> { network.post(1); network.post(2); }, "test-network");
        worker.start(); worker.join();
        assertEquals(0, client.subscriptionCount()); assertEquals(0, network.subscriptionCount());
        assertEquals(0, closed.get()); assertEquals(0, probe.disables);
        manager.processFaults(); manager.processFaults();
        assertSame(clientThread, cleanupThread.get());
        assertSame(networkFailure, entry.lastFailure());
        assertArrayEquals(new Throwable[] { cleanupFailure }, networkFailure.getSuppressed());
        manager.quarantine("probe", new IllegalStateException("later")); manager.close();
        assertEquals(1, closed.get()); assertEquals(1, probe.disables);
        assertEquals(ModuleState.FAULTED, entry.state()); assertFalse(entry.requestedEnabled());
    }

    @Test public void ownedSettingObserverDoesNotLeakAcrossSuspensionOrRetry() {
        Setting<Integer> setting = Setting.integer("count", "Count", "", 0, 0, 10, tasks::checkThread);
        AtomicInteger observed = new AtomicInteger();
        Probe probe = new Probe("probe") {
            @Override protected void onEnable(ModuleContext scope) {
                super.onEnable(scope);
                scope.own(setting.onChange((changed, before, after) -> observed.incrementAndGet()));
            }
        };
        ModuleEntry entry = enable(probe);
        setting.set(1); assertEquals(1, observed.get());
        manager.suspend(); setting.set(2); assertEquals(1, observed.get());
        manager.resume(); setting.set(3); assertEquals(2, observed.get());
        manager.quarantine("probe", new IllegalStateException("setting failure"));
        setting.set(4); assertEquals(2, observed.get());
        manager.setEnabled("probe", true); setting.set(5); assertEquals(3, observed.get());
        assertEquals(ModuleState.ENABLED, entry.state()); assertNull(entry.lastFailure());
        manager.close(); setting.set(6); assertEquals(3, observed.get());
        assertEquals(3, probe.disables);
    }

    @Test public void lateRegistrationsAreClosedImmediatelyAndCannotAffectNextActivation() {
        Probe probe = new Probe("probe");
        ModuleEntry entry = enable(probe);
        ModuleContext retired = probe.context;
        AtomicInteger closed = new AtomicInteger();
        manager.setEnabled("probe", false); manager.setEnabled("probe", true);
        retired.onCleanup(closed::incrementAndGet);
        retired.own((AutoCloseable) closed::incrementAndGet);
        assertEquals(2, closed.get());
        IOException lateFailure = new IOException("late");
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> retired.own(() -> { throw lateFailure; }));
        assertSame(lateFailure, thrown.getCause());
        assertEquals(ModuleState.ENABLED, entry.state()); assertNull(entry.lastFailure());
        manager.close(); assertEquals(2, closed.get());
    }

    @Test public void reentrantShutdownAndRegistrationDoNotRepeatCleanup() {
        Probe probe = new Probe("probe");
        ModuleEntry entry = enable(probe);
        AtomicInteger closed = new AtomicInteger();
        probe.context.onCleanup(closed::incrementAndGet);
        probe.context.onCleanup(() -> {
            manager.close();
            probe.context.onCleanup(closed::incrementAndGet);
        });
        manager.setEnabled("probe", false); manager.close();
        assertEquals(2, closed.get()); assertEquals(1, probe.disables);
        assertEquals(ModuleState.DISABLED, entry.state()); assertNull(entry.lastFailure());
    }

    @Test public void quarantineDuringCleanupCannotStartAnotherActivation() {
        Probe probe = new Probe("probe");
        ModuleEntry entry = enable(probe);
        IllegalStateException failure = new IllegalStateException("nested quarantine");
        AtomicInteger closed = new AtomicInteger();
        probe.context.onCleanup(closed::incrementAndGet);
        probe.context.onCleanup(() -> {
            manager.quarantine("probe", failure);
            assertEquals(ModuleState.DISABLING, entry.state());
            assertThrows(IllegalStateException.class, () -> manager.setEnabled("probe", true));
        });
        manager.setEnabled("probe", false);
        assertEquals(ModuleState.FAULTED, entry.state()); assertFalse(entry.requestedEnabled());
        assertSame(failure, entry.lastFailure());
        assertEquals(0, failure.getSuppressed().length);
        manager.close();
        assertEquals(1, closed.get()); assertEquals(1, probe.disables);
        assertEquals(0, client.subscriptionCount());
    }

    @Test public void registrationRequiresClientThreadAndNonNullResources() throws Exception {
        Probe probe = new Probe("probe"); enable(probe);
        assertThrows(NullPointerException.class, () -> probe.context.own(null));
        assertThrows(NullPointerException.class, () -> probe.context.onCleanup(null));
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        AtomicInteger closed = new AtomicInteger();
        Thread worker = new Thread(() -> {
            try { probe.context.onCleanup(closed::incrementAndGet); }
            catch (Throwable failure) { thrown.set(failure); }
        }, "test-network");
        worker.start(); worker.join();
        assertTrue(thrown.get() instanceof IllegalStateException);
        manager.close(); assertEquals(0, closed.get());
    }

    @Test public void fatalCleanupErrorsPropagate() {
        Probe probe = new Probe("probe"); enable(probe);
        ThreadDeath fatal = new ThreadDeath();
        probe.context.onCleanup(() -> { throw fatal; });
        assertSame(fatal, assertThrows(ThreadDeath.class, () -> manager.setEnabled("probe", false)));
    }
}
