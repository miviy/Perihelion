package perihelion.core.module;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Test;
import perihelion.core.concurrent.ClientTaskQueue;
import perihelion.core.diagnostics.ClientLog;
import perihelion.core.event.EventBus;
import perihelion.core.setting.ChangeSubscription;
import perihelion.core.setting.Setting;
import static org.junit.Assert.*;

public class ModuleSettingObserverTest {
    private final ClientTaskQueue tasks = new ClientTaskQueue(Thread.currentThread(), ClientLog.NONE);
    private final EventBus client = new EventBus(tasks::checkThread, ClientLog.NONE);
    private final EventBus network = new EventBus(() -> {}, ClientLog.NONE);
    private final ModuleManager manager = new ModuleManager(tasks, client, network, ClientLog.NONE);

    private class Probe extends ClientModule {
        final Setting<Integer> count = setting(Setting.integer("count", "Count", "", 0, 0, 10, tasks::checkThread));
        ModuleContext context;
        int disables;
        Probe(String id) { this(id, false); }
        Probe(String id, boolean world) { super(new ModuleInfo(id, id, "test-only", ModuleCategory.MISC, world)); }
        @Override protected void onEnable(ModuleContext scope) { context = scope; }
        @Override protected void onDisable() { disables++; }
    }

    private void enable(Probe... probes) {
        for (Probe probe : probes) manager.register(probe);
        manager.start();
        for (Probe probe : probes) manager.setEnabled(probe.info().id(), true);
    }
    @After public void close() { manager.close(); tasks.close(); }

    @Test public void crossModuleFailureStopsOnlyItsActivationAndPreservesDeliveryOrder() {
        Probe source = new Probe("source"), watcher = new Probe("watcher"), healthy = new Probe("healthy");
        enable(source, watcher, healthy);
        List<String> calls = new ArrayList<>();
        RuntimeException failure = new IllegalArgumentException("watcher");
        RuntimeException cleanupFailure = new IllegalStateException("cleanup");
        source.count.onChange((setting, before, after) -> calls.add("raw-before"));
        watcher.context.listen(String.class, event -> calls.add("event"));
        watcher.context.onCleanup(() -> { calls.add("cleanup"); throw cleanupFailure; });
        watcher.context.observe(source.count, (setting, before, after) -> {
            calls.add("watcher");
            assertSame(source.count, setting);
            assertEquals(Integer.valueOf(0), before);
            assertEquals(Integer.valueOf(1), after);
            assertEquals(after, setting.get());
            throw failure;
        });
        watcher.context.observe(source.count, (setting, before, after) -> calls.add("retired"));
        healthy.context.observe(source.count, (setting, before, after) -> calls.add("healthy"));
        source.count.onChange((setting, before, after) -> calls.add("raw-after"));

        assertTrue(manager.setSetting("source", source.count, 1));
        assertEquals(Arrays.asList("raw-before", "watcher", "healthy", "raw-after", "cleanup"), calls);
        assertEquals(ModuleState.ENABLED, manager.require("source").state());
        assertEquals(ModuleState.ENABLED, manager.require("healthy").state());
        ModuleEntry faulted = manager.require("watcher");
        assertEquals(ModuleState.FAULTED, faulted.state());
        assertFalse(faulted.requestedEnabled());
        assertSame(failure, faulted.lastFailure());
        assertArrayEquals(new Throwable[] { cleanupFailure }, failure.getSuppressed());
        assertEquals(1, watcher.disables);
        assertEquals(0, client.subscriptionCount());
        manager.processFaults();
        assertEquals(1, watcher.disables);
    }

    @Test public void directMutationAlsoAttributesMultipleOwnersWithoutBlamingItsCaller() {
        Probe source = new Probe("source"), first = new Probe("first"), second = new Probe("second");
        enable(source, first, second);
        RuntimeException firstFailure = new IllegalStateException("first");
        AssertionError secondFailure = new AssertionError("second");
        first.context.observe(source.count, (setting, before, after) -> { throw firstFailure; });
        second.context.observe(source.count, (setting, before, after) -> { throw secondFailure; });
        source.context.listen(String.class, event -> source.count.set(1));
        client.post("change");
        assertEquals(0, first.disables); // Cleanup waits for the client fault drain.
        manager.processFaults();
        assertSame(firstFailure, manager.require("first").lastFailure());
        assertSame(secondFailure, manager.require("second").lastFailure());
        assertEquals(ModuleState.ENABLED, manager.require("source").state());
        assertEquals(Integer.valueOf(1), source.count.get());
    }

    @Test public void registrationRemovalAndNoopChangesKeepSettingSnapshotSemantics() {
        Probe probe = new Probe("probe"); enable(probe);
        List<String> calls = new ArrayList<>();
        ChangeSubscription[] second = new ChangeSubscription[1];
        probe.context.observe(probe.count, (setting, before, after) -> {
            calls.add("first");
            second[0].close();
            probe.context.observe(setting, (ignored, oldValue, newValue) -> calls.add("late"));
        });
        second[0] = probe.context.observe(probe.count, (setting, before, after) -> calls.add("removed"));
        manager.setSetting("probe", probe.count, 1);
        manager.setSetting("probe", probe.count, 1);
        assertEquals(Arrays.asList("first"), calls);
        manager.setSetting("probe", probe.count, 2);
        assertEquals(Arrays.asList("first", "first", "late"), calls);
        second[0].close(); // Setting handles remain idempotent, including during cleanup.
    }

    @Test public void observersWaitForEnableAndStaySilentThroughRetirementAndRetry() {
        AtomicInteger observed = new AtomicInteger();
        Probe probe = new Probe("probe", true) {
            @Override protected void onEnable(ModuleContext scope) {
                super.onEnable(scope);
                scope.observe(count, (setting, before, after) -> observed.incrementAndGet());
                count.set(0); // No observer callback during enable.
            }
            @Override protected void onDisable() {
                super.onDisable();
                count.set(10); // Owner is closed before the hook or resource cleanup runs.
            }
        };
        manager.setWorldReady(true);
        enable(probe);
        probe.count.set(1); assertEquals(1, observed.get());
        ModuleContext retired = probe.context;
        manager.suspend(); probe.count.set(2); assertEquals(1, observed.get());
        manager.resume(); probe.count.set(3); assertEquals(2, observed.get());
        retired.observe(probe.count, (setting, before, after) -> fail("Retired context ran"));
        manager.setWorldReady(false); probe.count.set(4); assertEquals(2, observed.get());
        manager.setWorldReady(true); probe.count.set(5); assertEquals(3, observed.get());
        manager.setEnabled("probe", false); probe.count.set(6); assertEquals(3, observed.get());
        manager.setEnabled("probe", true); probe.count.set(7); assertEquals(4, observed.get());
        manager.close(); probe.count.set(8); assertEquals(4, observed.get());
        assertEquals(4, probe.disables);
    }

    @Test public void failedEnableReleasesObserverAndNeverActivatesIt() {
        AtomicInteger observed = new AtomicInteger();
        AtomicInteger cleaned = new AtomicInteger();
        RuntimeException failure = new IllegalStateException("enable");
        Probe probe = new Probe("probe") {
            @Override protected void onEnable(ModuleContext scope) {
                super.onEnable(scope);
                scope.observe(count, (setting, before, after) -> observed.incrementAndGet());
                scope.onCleanup(cleaned::incrementAndGet);
                count.set(1);
                throw failure;
            }
        };
        enable(probe); probe.count.set(2);
        assertEquals(0, observed.get()); assertEquals(0, probe.disables);
        assertEquals(1, cleaned.get());
        assertSame(failure, manager.require("probe").lastFailure());
    }

    @Test public void networkFaultGatesObserversBeforeClientThreadCleanup() throws Exception {
        Probe probe = new Probe("probe"); enable(probe);
        AtomicInteger observed = new AtomicInteger();
        AtomicReference<Thread> cleanupThread = new AtomicReference<>();
        probe.context.observe(probe.count, (setting, before, after) -> observed.incrementAndGet());
        probe.context.onCleanup(() -> cleanupThread.set(Thread.currentThread()));
        probe.context.listenNetwork(String.class, 0, false, event -> { throw new IllegalStateException("network"); });
        Thread worker = new Thread(() -> network.post("fault"));
        worker.start(); worker.join();
        probe.count.set(1);
        assertEquals(0, observed.get()); assertNull(cleanupThread.get());
        manager.processFaults();
        assertSame(Thread.currentThread(), cleanupThread.get());
        assertEquals(1, probe.disables);
    }

    @Test public void queuedFaultFromRetiredActivationCannotFaultItsReplacement() {
        AtomicInteger observed = new AtomicInteger();
        Probe probe = new Probe("probe") {
            @Override protected void onEnable(ModuleContext scope) {
                super.onEnable(scope);
                scope.observe(count, (setting, before, after) -> {
                    if (observed.incrementAndGet() == 1) throw new IllegalStateException("old activation");
                });
            }
        };
        enable(probe); probe.count.set(1);
        ModuleContext retired = probe.context;
        manager.setEnabled("probe", false); manager.setEnabled("probe", true);
        retired.observe(probe.count, (setting, before, after) -> fail("Stale observer"));
        manager.processFaults(); probe.count.set(2);
        assertEquals(2, observed.get());
        assertEquals(ModuleState.ENABLED, manager.require("probe").state());
        assertNull(manager.require("probe").lastFailure());
    }

    @Test public void validationAndForeignSettingsDoNotFaultModulesOrMutateValues() throws Exception {
        Probe source = new Probe("source"), other = new Probe("other"); enable(source, other);
        assertThrows(IllegalArgumentException.class, () -> manager.setSetting("source", source.count, 99));
        assertThrows(IllegalArgumentException.class, () -> manager.setSetting("source", source.count, null));
        assertThrows(IllegalArgumentException.class, () -> manager.setSetting("source", other.count, 1));
        assertThrows(NullPointerException.class, () -> source.context.observe(null, (s, a, b) -> {}));
        assertThrows(NullPointerException.class, () -> source.context.observe(source.count, null));
        AtomicReference<Throwable> unexpected = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                assertThrows(IllegalStateException.class, () -> manager.setSetting("source", source.count, 1));
                assertThrows(IllegalStateException.class, () -> source.context.observe(source.count, (s, a, b) -> {}));
            } catch (Throwable failure) { unexpected.set(failure); }
        });
        worker.start(); worker.join();
        if (unexpected.get() != null) throw new AssertionError(unexpected.get());
        assertEquals(Integer.valueOf(0), source.count.get());
        assertEquals(Integer.valueOf(0), other.count.get());
        assertEquals(ModuleState.ENABLED, manager.require("source").state());
        assertEquals(ModuleState.ENABLED, manager.require("other").state());
        manager.close();
        assertThrows(IllegalStateException.class, () -> manager.setSetting("source", source.count, 1));
    }

    @Test public void recursiveMutationFaultsTheObserverWithoutBlamingTheSettingDefinition() {
        Probe source = new Probe("source"), watcher = new Probe("watcher"); enable(source, watcher);
        AtomicInteger delivered = new AtomicInteger();
        watcher.context.observe(source.count, (setting, before, after) -> manager.setSetting("source", setting, 2));
        source.count.onChange((setting, before, after) -> delivered.incrementAndGet());
        assertTrue(manager.setSetting("source", source.count, 1));
        assertEquals(Integer.valueOf(1), source.count.get()); assertEquals(1, delivered.get());
        assertEquals(ModuleState.ENABLED, manager.require("source").state());
        assertEquals(ModuleState.FAULTED, manager.require("watcher").state());
        assertTrue(manager.require("watcher").lastFailure().getMessage().contains("Recursive change"));
    }

    @Test public void rawFailuresRetainFallbackAndAggregationAlongsideOwnedFailures() {
        Probe source = new Probe("source"), watcher = new Probe("watcher"); enable(source, watcher);
        RuntimeException raw = new IllegalArgumentException("raw");
        AssertionError laterRaw = new AssertionError("later raw");
        RuntimeException owned = new IllegalStateException("owned");
        source.count.onChange((setting, before, after) -> { throw raw; });
        watcher.context.observe(source.count, (setting, before, after) -> { throw owned; });
        source.count.onChange((setting, before, after) -> { throw laterRaw; });
        assertFalse(manager.setSetting("source", source.count, 1));
        assertSame(raw, manager.require("source").lastFailure());
        assertArrayEquals(new Throwable[] { laterRaw }, raw.getSuppressed());
        assertSame(owned, manager.require("watcher").lastFailure());
        assertEquals(Integer.valueOf(1), source.count.get());
        assertEquals(1, source.disables); assertEquals(1, watcher.disables);
    }

    @Test public void fatalObserverErrorsPropagateWithoutQuarantine() {
        Probe source = new Probe("source"), watcher = new Probe("watcher"); enable(source, watcher);
        ThreadDeath fatal = new ThreadDeath();
        ChangeSubscription subscription = watcher.context.observe(source.count, (setting, before, after) -> { throw fatal; });
        assertSame(fatal, assertThrows(ThreadDeath.class, () -> manager.setSetting("source", source.count, 1)));
        assertEquals(ModuleState.ENABLED, manager.require("source").state());
        assertEquals(ModuleState.ENABLED, manager.require("watcher").state());
        subscription.close();
        assertTrue(manager.setSetting("source", source.count, 2));
    }
}
