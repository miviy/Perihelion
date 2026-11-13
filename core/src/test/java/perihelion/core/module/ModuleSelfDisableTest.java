package perihelion.core.module;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import perihelion.core.concurrent.ClientTaskQueue;
import perihelion.core.diagnostics.ClientLog;
import perihelion.core.event.EventBus;
import static org.junit.Assert.*;

public class ModuleSelfDisableTest {
    private final ClientTaskQueue tasks = new ClientTaskQueue(Thread.currentThread(), ClientLog.NONE);
    private final EventBus client = new EventBus(tasks::checkThread, ClientLog.NONE);
    private final EventBus network = new EventBus(() -> {}, ClientLog.NONE);
    private final ModuleManager manager = new ModuleManager(tasks, client, network, ClientLog.NONE);
    private final List<String> order = new ArrayList<>();

    private class Probe extends ClientModule {
        ModuleContext context;
        boolean requestOnEnable = true, requestOnCleanup;
        RuntimeException enableFailure, disableFailure, cleanupFailure;
        int enables, disables, cleanups, calls;

        Probe(boolean world) { super(new ModuleInfo("probe", "Probe", "test-only", ModuleCategory.MISC, world)); }

        @Override protected void onEnable(ModuleContext scope) {
            context = scope;
            enables++;
            order.add("enable-start");
            scope.listen(String.class, event -> calls++);
            scope.listenNetwork(Integer.class, 0, false, event -> calls++);
            scope.onCleanup(() -> {
                order.add("cleanup");
                cleanups++;
                if (requestOnCleanup) scope.requestDisable();
                if (cleanupFailure != null) throw cleanupFailure;
            });
            if (requestOnEnable) {
                scope.requestDisable();
                scope.requestDisable();
            }
            order.add("enable-end");
            if (enableFailure != null) throw enableFailure;
        }

        @Override protected void onDisable() {
            order.add("disable");
            disables++;
            if (requestOnCleanup) context.requestDisable();
            if (disableFailure != null) throw disableFailure;
        }
    }

    private ModuleEntry enable(Probe probe) {
        ModuleEntry entry = manager.register(probe);
        manager.start();
        manager.setEnabled("probe", true);
        return entry;
    }

    private void assertRetired(ModuleEntry entry, ModuleState state) {
        assertEquals(state, entry.state());
        assertFalse(entry.requestedEnabled());
        assertEquals(0, client.subscriptionCount());
        assertEquals(0, network.subscriptionCount());
    }

    @Test public void oneShotFinishesEnableBeforeCleanupWithoutPublishingListenersOrDeferredWork() {
        Probe probe = new Probe(false) {
            @Override protected void onEnable(ModuleContext scope) {
                super.onEnable(scope);
                assertEquals(ModuleState.ENABLING, manager.require("probe").state());
                assertTrue(manager.require("probe").requestedEnabled());
                assertEquals(0, disables);
                assertEquals(0, cleanups);
                assertThrows(IllegalStateException.class, () -> manager.setEnabled("probe", false));
                assertThrows(IllegalStateException.class, () -> manager.toggle("probe"));
                scope.onCleanup(() -> order.add("late-resource"));
                scope.defer(() -> calls++);
                scope.deferForWorld(() -> calls++);
                tasks.drain(); // Reentrant queue draining must not run inactive work or disable.
                assertEquals(0, disables);
                scope.defer(() -> calls++);
                client.post("during-enable");
                network.post(1);
                manager.resume();
            }

            @Override protected void onDisable() {
                assertEquals(0, client.subscriptionCount());
                assertEquals(0, network.subscriptionCount());
                assertEquals(ModuleState.DISABLING, manager.require("probe").state());
                assertThrows(IllegalStateException.class, () -> manager.setEnabled("probe", true));
                super.onDisable();
            }
        };
        probe.requestOnCleanup = true;
        ModuleEntry entry = enable(probe);
        tasks.drain(); client.post("after-enable"); network.post(2);
        assertRetired(entry, ModuleState.DISABLED);
        assertNull(entry.lastFailure());
        assertEquals(Arrays.asList("enable-start", "enable-end", "disable", "late-resource", "cleanup"), order);
        assertEquals(0, probe.calls);
        assertEquals(1, probe.enables); assertEquals(1, probe.disables); assertEquals(1, probe.cleanups);
        probe.context.requestDisable(); manager.close();
        assertEquals(1, probe.disables); assertEquals(1, probe.cleanups);
    }

    @Test public void repeatedActivationsHaveIndependentRequests() {
        Probe probe = new Probe(false);
        ModuleEntry entry = enable(probe);
        for (int i = 0; i < 10; i++) {
            manager.setEnabled("probe", true);
            assertRetired(entry, ModuleState.DISABLED);
            assertNull(entry.lastFailure());
        }
        manager.close();
        assertEquals(11, probe.enables); assertEquals(11, probe.disables); assertEquals(11, probe.cleanups);
    }

    @Test public void startWorldReadinessAndResumeReportTheFinalDisabledPreference() {
        for (int trigger = 0; trigger < 3; trigger++) {
            ModuleSelfDisableTest fixture = new ModuleSelfDisableTest();
            Probe probe = fixture.new Probe(trigger == 1);
            ModuleEntry entry = fixture.manager.register(probe);
            fixture.manager.setEnabled("probe", true);
            if (trigger == 1) fixture.manager.start();
            if (trigger == 2) { fixture.manager.suspend(); fixture.manager.start(); }
            AtomicInteger changes = new AtomicInteger();
            fixture.manager.onChange(() -> {
                fixture.assertRetired(entry, ModuleState.DISABLED);
                assertEquals(1, probe.disables); assertEquals(1, probe.cleanups);
                changes.incrementAndGet();
            });
            if (trigger == 0) fixture.manager.start();
            else if (trigger == 1) fixture.manager.setWorldReady(true);
            else fixture.manager.resume();
            fixture.assertRetired(entry, ModuleState.DISABLED);
            assertEquals(1, changes.get());
            fixture.manager.setWorldReady(false); fixture.manager.setWorldReady(true);
            fixture.manager.suspend(); fixture.manager.resume(); fixture.manager.close();
            assertEquals(1, probe.enables); assertEquals(1, probe.disables); assertEquals(1, probe.cleanups);
        }
    }

    @Test public void failedEnableWinsOverRequestAndDoesNotDisableSuccessfulRetry() {
        Probe probe = new Probe(false);
        RuntimeException failure = new IllegalStateException("enable");
        probe.enableFailure = failure;
        ModuleEntry entry = enable(probe);
        ModuleContext failed = probe.context;
        assertRetired(entry, ModuleState.FAULTED);
        assertSame(failure, entry.lastFailure());
        assertEquals(0, probe.disables); assertEquals(1, probe.cleanups);
        failed.requestDisable();
        assertSame(failure, entry.lastFailure());
        probe.enableFailure = null; probe.requestOnEnable = false;
        manager.setEnabled("probe", true);
        failed.requestDisable(); tasks.drain(); manager.processFaults();
        assertEquals(ModuleState.ENABLED, entry.state()); assertTrue(entry.requestedEnabled());
        assertNull(entry.lastFailure()); assertNotSame(failed, probe.context);
        manager.close();
        assertEquals(1, probe.disables); assertEquals(2, probe.cleanups);
    }

    @Test public void disableAndResourceFailuresRemainFaultsWithoutRepeatingCleanup() {
        Probe probe = new Probe(false);
        RuntimeException disable = new IllegalStateException("disable");
        RuntimeException cleanup = new IllegalStateException("cleanup");
        probe.disableFailure = disable; probe.cleanupFailure = cleanup; probe.requestOnCleanup = true;
        ModuleEntry entry = enable(probe);
        assertRetired(entry, ModuleState.FAULTED);
        assertSame(disable, entry.lastFailure());
        assertArrayEquals(new Throwable[] { cleanup }, disable.getSuppressed());
        probe.context.requestDisable(); manager.processFaults(); manager.close();
        assertEquals(1, probe.disables); assertEquals(1, probe.cleanups);
    }

    @Test public void requestsDuringCleanupAndFromRetiredContextsCannotDisableNextActivation() {
        Probe probe = new Probe(false);
        probe.requestOnEnable = false; probe.requestOnCleanup = true;
        ModuleEntry entry = enable(probe);
        ModuleContext retired = probe.context;
        manager.suspend();
        assertTrue(entry.requestedEnabled());
        manager.resume(); retired.requestDisable();
        assertEquals(ModuleState.ENABLED, entry.state()); assertTrue(entry.requestedEnabled());
        assertNull(entry.lastFailure());
        assertEquals(2, probe.enables); assertEquals(1, probe.disables);
        manager.close(); retired.requestDisable(); probe.context.requestDisable();
        assertTrue(entry.requestedEnabled());
        assertEquals(2, probe.disables); assertEquals(2, probe.cleanups);
    }

    @Test public void enabledClientCallbackCanRequestDisable() {
        Probe probe = new Probe(false);
        probe.requestOnEnable = false;
        ModuleEntry entry = enable(probe);
        probe.context.listen(String.class, event -> probe.context.requestDisable());
        client.post("stop"); client.post("again");
        assertRetired(entry, ModuleState.DISABLED);
        assertNull(entry.lastFailure());
        assertEquals(1, probe.calls); assertEquals(1, probe.disables); assertEquals(1, probe.cleanups);
        manager.close();
    }

    @Test public void offThreadRequestIsRejectedWithoutPoisoningLaterDeferredRequest() throws Exception {
        Probe probe = new Probe(false);
        probe.requestOnEnable = false;
        ModuleEntry entry = enable(probe);
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try { probe.context.requestDisable(); }
            catch (Throwable failure) { thrown.set(failure); }
        }, "test-network");
        worker.start(); worker.join();
        assertTrue(thrown.get() instanceof IllegalStateException);
        assertEquals(ModuleState.ENABLED, entry.state()); assertTrue(entry.requestedEnabled());
        assertEquals(0, probe.disables);
        probe.context.listenNetwork(Integer.class, 0, false, event -> {
            if (!probe.context.defer(probe.context::requestDisable)) throw new IllegalStateException("queue rejected");
        });
        worker = new Thread(() -> network.post(1), "test-network");
        worker.start(); worker.join();
        assertEquals(ModuleState.ENABLED, entry.state());
        tasks.drain();
        assertRetired(entry, ModuleState.DISABLED);
        assertNull(entry.lastFailure()); assertEquals(1, probe.disables); assertEquals(1, probe.cleanups);
        manager.close();
    }

    @Test public void pendingFaultCannotBeClearedBySelfDisable() {
        Probe probe = new Probe(false);
        probe.requestOnEnable = false;
        ModuleEntry entry = enable(probe);
        RuntimeException failure = new IllegalStateException("listener");
        probe.context.listen(String.class, event -> { throw failure; });
        client.post("fail"); probe.context.requestDisable(); manager.processFaults();
        assertRetired(entry, ModuleState.FAULTED);
        assertSame(failure, entry.lastFailure());
        manager.close(); assertEquals(1, probe.disables); assertEquals(1, probe.cleanups);
    }

    @Test public void shutdownInsideEnableStillHonorsThePendingRequest() {
        Probe probe = new Probe(false) {
            @Override protected void onEnable(ModuleContext scope) {
                super.onEnable(scope);
                manager.close(); scope.requestDisable();
                assertEquals(0, disables);
            }
        };
        ModuleEntry entry = enable(probe);
        assertRetired(entry, ModuleState.DISABLED);
        assertNull(entry.lastFailure());
        manager.close(); assertEquals(1, probe.disables); assertEquals(1, probe.cleanups);
    }
}
