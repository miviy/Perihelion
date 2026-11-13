package perihelion.core.module;

import org.junit.Test;
import perihelion.core.concurrent.ClientTaskQueue;
import perihelion.core.diagnostics.ClientLog;
import perihelion.core.event.EventBus;
import perihelion.core.input.Keybind;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class ModuleManagerTest {
    private final ClientTaskQueue tasks = new ClientTaskQueue(Thread.currentThread(), ClientLog.NONE);
    private final EventBus client = new EventBus(tasks::checkThread, ClientLog.NONE);
    private final EventBus network = new EventBus(() -> {}, ClientLog.NONE);
    private final ModuleManager manager = new ModuleManager(tasks, client, network, ClientLog.NONE);
    private class Probe extends ClientModule {
        int enables, disables, calls, cleanups;
        boolean failEnable, failDisable, failCallback;
        ModuleContext context;
        Probe(String id, boolean world) { super(new ModuleInfo(id, id, "test-only", ModuleCategory.MISC, world)); }
        protected void onEnable(ModuleContext scope) {
            enables++; context = scope;
            scope.onCleanup(() -> cleanups++);
            scope.listen(String.class, e -> { calls++; if (failCallback) throw new Exception("test"); });
            scope.listenNetwork(Integer.class, 0, false, e -> { calls++; if (failCallback) throw new Exception("test"); });
            client.post("during-enable");
            if (failEnable) throw new IllegalStateException("test-enable");
        }
        protected void onDisable() {
            disables++;
            client.post("during-disable");
            if (failDisable) throw new IllegalStateException("test-disable");
        }
    }
    @Test public void onlyActualBindingChangesNotifyBindingObservers() {
        Probe probe = new Probe("probe", true);
        ModuleEntry entry = manager.register(probe);
        AtomicInteger changes = new AtomicInteger(), bindings = new AtomicInteger();
        manager.onChange(changes::incrementAndGet);
        manager.onBindingChange(bindings::incrementAndGet);
        try {
            manager.start();
            manager.setBinding("probe", Keybind.UNBOUND);
            manager.setEnabled("probe", false);
            assertEquals(0, changes.get()); assertEquals(0, bindings.get());

            manager.setBinding("probe", Keybind.parse("keyboard:25"));
            manager.setBinding("probe", Keybind.parse("keyboard:25"));
            assertEquals(1, changes.get()); assertEquals(1, bindings.get());
            manager.setEnabled("probe", true);
            manager.setWorldReady(true);
            manager.suspend(); manager.resume();
            assertEquals(2, changes.get()); assertEquals(1, bindings.get());

            probe.context.requestDisable();
            assertEquals(3, changes.get()); assertEquals(1, bindings.get());
            manager.setEnabled("probe", true);
            probe.failCallback = true;
            client.post("fault"); manager.processFaults();
            assertEquals(ModuleState.FAULTED, entry.state());
            assertEquals(5, changes.get()); assertEquals(1, bindings.get());

            manager.setBinding("probe", Keybind.parse("mouse:0"));
            manager.setBinding("probe", Keybind.UNBOUND);
            manager.setBinding("probe", Keybind.UNBOUND);
            assertEquals(7, changes.get()); assertEquals(3, bindings.get());
        } finally { manager.close(); }
    }
    @Test public void worldPreferencesSurviveRepeatedCyclesAndShutdown() {
        Probe probe = new Probe("probe", true);
        ModuleEntry entry = manager.register(probe);
        manager.setEnabled("probe", true); manager.start();
        assertEquals(ModuleState.WAITING_FOR_WORLD, entry.state());
        for (int i = 0; i < 25; i++) {
            manager.setWorldReady(true);
            assertEquals(ModuleState.ENABLED, entry.state());
            assertEquals(0, probe.calls);
            manager.setWorldReady(false);
            assertEquals(ModuleState.WAITING_FOR_WORLD, entry.state());
            assertEquals(0, client.subscriptionCount()); assertEquals(0, network.subscriptionCount());
            assertTrue(entry.requestedEnabled());
        }
        assertEquals(25, probe.enables); assertEquals(25, probe.disables); assertEquals(25, probe.cleanups);
        manager.setWorldReady(true); manager.close(); manager.close();
        assertEquals(26, probe.disables); assertTrue(entry.requestedEnabled());
        assertEquals(26, probe.cleanups);
        assertThrows(IllegalStateException.class, () -> manager.register(new Probe("late", false)));
    }
    @Test public void failedEnableCleansScopeWithoutDisableHookAndExplicitRetryWorks() {
        Probe probe = new Probe("bad", false); probe.failEnable = true;
        ModuleEntry entry = manager.register(probe); manager.start(); manager.setEnabled("bad", true);
        assertEquals(ModuleState.FAULTED, entry.state()); assertFalse(entry.requestedEnabled());
        assertEquals(0, probe.disables); assertEquals(0, probe.calls);
        assertEquals(1, probe.cleanups);
        assertEquals("test-enable", entry.lastFailure().getMessage());
        assertEquals(0, client.subscriptionCount()); assertEquals(0, network.subscriptionCount());
        probe.failEnable = false; manager.setEnabled("bad", true); client.post("works");
        assertEquals(ModuleState.ENABLED, entry.state()); assertEquals(1, probe.calls);
        assertNull(entry.lastFailure());
        manager.close();
        assertEquals(1, probe.disables);
        assertEquals(2, probe.cleanups);
    }
    @Test public void normalDisableDetachesScopeAndCleansExactlyOnce() {
        Probe probe = new Probe("probe", false);
        ModuleEntry entry = manager.register(probe); manager.start(); manager.setEnabled("probe", true);
        client.post("active"); network.post(1);
        assertEquals(2, probe.calls);
        manager.setEnabled("probe", false);
        assertEquals(ModuleState.DISABLED, entry.state()); assertFalse(entry.requestedEnabled());
        assertNull(entry.lastFailure());
        assertEquals(0, client.subscriptionCount()); assertEquals(0, network.subscriptionCount());
        client.post("disabled"); network.post(2);
        manager.setEnabled("probe", false);
        manager.close();
        assertEquals(1, probe.disables); assertEquals(2, probe.calls);
        assertEquals(1, probe.cleanups);
    }
    @Test public void faultedModuleIsNotCleanedAgainAndCleanupFailureIsSuppressed() {
        Probe probe = new Probe("bad", false); probe.failCallback = true; probe.failDisable = true;
        ModuleEntry entry = manager.register(probe); manager.start(); manager.setEnabled("bad", true);
        client.post("fault");
        assertEquals(0, probe.disables);
        assertEquals(0, client.subscriptionCount()); assertEquals(0, network.subscriptionCount());
        manager.processFaults();
        assertEquals(1, probe.disables);
        assertEquals(1, probe.cleanups);
        assertEquals(ModuleState.FAULTED, entry.state()); assertFalse(entry.requestedEnabled());
        assertEquals("test", entry.lastFailure().getMessage());
        assertEquals(1, entry.lastFailure().getSuppressed().length);
        assertEquals("test-disable", entry.lastFailure().getSuppressed()[0].getMessage());
        IllegalStateException laterFailure = new IllegalStateException("later observer failure");
        manager.quarantine("bad", laterFailure);
        assertSame(laterFailure, entry.lastFailure());
        assertEquals(0, laterFailure.getSuppressed().length);
        manager.processFaults(); manager.close();
        assertEquals(ModuleState.FAULTED, entry.state()); assertFalse(entry.requestedEnabled());
        assertEquals(1, probe.disables);
        assertEquals(1, probe.cleanups);
    }
    @Test public void networkFailureImmediatelyDetachesThenCleansOnClientThread() throws Exception {
        Probe bad = new Probe("bad", false), good = new Probe("good", false);
        bad.failCallback = true;
        ModuleEntry entry = manager.register(bad); manager.register(good);
        manager.start(); manager.setEnabled("bad", true); manager.setEnabled("good", true);
        // The second module's enable posts a client event, so start with a fresh activation.
        manager.processFaults(); bad.failCallback = false; manager.setEnabled("bad", true); bad.failCallback = true;
        int goodBefore = good.calls;
        Thread worker = new Thread(() -> { network.post(1); network.post(2); }, "test-network");
        worker.start(); worker.join();
        assertEquals(goodBefore + 2, good.calls);
        assertEquals(1, network.subscriptionCount()); assertEquals(1, client.subscriptionCount());
        int cleanupBefore = bad.disables;
        manager.processFaults();
        assertEquals(cleanupBefore + 1, bad.disables); assertEquals(ModuleState.FAULTED, entry.state());
        assertFalse(entry.requestedEnabled()); manager.close();
    }
    @Test public void cleanupFailureDoesNotLeaveSubscriptionsOrStopOtherModules() {
        Probe bad = new Probe("bad", false), good = new Probe("good", false);
        manager.register(bad); manager.register(good); manager.start();
        manager.setEnabled("bad", true); manager.setEnabled("good", true); bad.failDisable = true;
        manager.close();
        assertEquals(ModuleState.FAULTED, manager.require("bad").state());
        assertEquals(1, good.disables); assertEquals(0, client.subscriptionCount()); assertEquals(0, network.subscriptionCount());
    }
    @Test public void deferredWorkHonorsScopeWorldAndFailureOwnership() {
        Probe probe = new Probe("probe", false); manager.register(probe); manager.start(); manager.setEnabled("probe", true);
        AtomicInteger calls = new AtomicInteger();
        probe.context.deferForWorld(calls::incrementAndGet); tasks.advanceGeneration(); tasks.drain(); assertEquals(0, calls.get());
        probe.context.defer(calls::incrementAndGet); manager.setEnabled("probe", false); tasks.drain(); assertEquals(0, calls.get());
        manager.setEnabled("probe", true);
        probe.context.defer(() -> { throw new IllegalStateException("test deferred failure"); });
        probe.context.defer(calls::incrementAndGet); tasks.drain(); manager.processFaults();
        assertEquals(0, calls.get()); assertEquals(ModuleState.FAULTED, manager.require("probe").state());
        assertEquals(0, network.subscriptionCount()); manager.close();
        assertEquals(2, probe.cleanups);
    }
    @Test public void duplicateIdsAndLateRegistrationAreRejected() {
        manager.register(new Probe("same", false));
        assertThrows(IllegalArgumentException.class, () -> manager.register(new Probe("same", false)));
        manager.start(); assertThrows(IllegalStateException.class, () -> manager.register(new Probe("other", false)));
        manager.close();
    }
    @Test public void stoppingInsideEnableRetiresTheUnpublishedScope() {
        AtomicInteger cleanup = new AtomicInteger(), resources = new AtomicInteger();
        manager.register(new ClientModule(new ModuleInfo("stop", "Stop", "test-only", ModuleCategory.MISC, false)) {
            protected void onEnable(ModuleContext scope) {
                scope.listen(String.class, event -> fail("Stopped activation must not become visible"));
                scope.listenNetwork(Integer.class, 0, false, event -> fail("Stopped activation must not become visible"));
                scope.onCleanup(resources::incrementAndGet);
                manager.close();
            }
            protected void onDisable() { cleanup.incrementAndGet(); }
        });
        manager.start();
        manager.setEnabled("stop", true);
        assertEquals(ModuleState.DISABLED, manager.require("stop").state());
        assertTrue(manager.require("stop").requestedEnabled());
        assertEquals(1, cleanup.get());
        assertEquals(1, resources.get());
        assertEquals(0, client.subscriptionCount());
        assertEquals(0, network.subscriptionCount());
        manager.close();
        assertEquals(1, cleanup.get());
        assertEquals(1, resources.get());
    }
    @Test public void worldChangeInsideEnableDoesNotRecursivelyActivateAndPreservesPreference() {
        AtomicInteger enables = new AtomicInteger(), cleanup = new AtomicInteger(), resources = new AtomicInteger();
        manager.register(new ClientModule(new ModuleInfo("world", "World", "test-only", ModuleCategory.MISC, true)) {
            protected void onEnable(ModuleContext scope) {
                scope.listen(String.class, event -> {});
                scope.onCleanup(resources::incrementAndGet);
                if (enables.incrementAndGet() == 1) manager.setWorldReady(false);
                else manager.resume(); // Reconcile while ENABLING must not enter onEnable again.
            }
            protected void onDisable() { cleanup.incrementAndGet(); }
        });
        manager.start();
        manager.setEnabled("world", true);
        manager.setWorldReady(true);
        assertEquals(1, enables.get());
        assertEquals(1, cleanup.get());
        assertEquals(1, resources.get());
        assertEquals(0, client.subscriptionCount());
        assertEquals(ModuleState.WAITING_FOR_WORLD, manager.require("world").state());
        assertTrue(manager.require("world").requestedEnabled());
        manager.setWorldReady(true);
        assertEquals(2, enables.get());
        assertEquals(ModuleState.ENABLED, manager.require("world").state());
        manager.close();
        assertEquals(2, cleanup.get());
        assertEquals(2, resources.get());
    }
}
