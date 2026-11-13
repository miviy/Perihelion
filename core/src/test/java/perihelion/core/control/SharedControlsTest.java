package perihelion.core.control;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Test;
import perihelion.core.concurrent.ClientTaskQueue;
import perihelion.core.diagnostics.ClientLog;
import perihelion.core.event.EventBus;
import perihelion.core.module.ClientModule;
import perihelion.core.module.ModuleContext;
import perihelion.core.module.ModuleCategory;
import perihelion.core.module.ModuleInfo;
import perihelion.core.module.ModuleManager;
import perihelion.core.module.ModuleState;
import static org.junit.Assert.*;

public class SharedControlsTest {
    private final ClientTaskQueue tasks = new ClientTaskQueue(Thread.currentThread(), ClientLog.NONE);
    private final EventBus client = new EventBus(tasks::checkThread, ClientLog.NONE);
    private final EventBus network = new EventBus(() -> {}, ClientLog.NONE);
    private final ModuleManager manager = new ModuleManager(tasks, client, network, ClientLog.NONE);
    private final SharedControls controls = manager.controls();

    private class Probe extends ClientModule {
        ModuleContext context;
        Probe(String id) { this(id, false); }
        Probe(String id, boolean world) { super(new ModuleInfo(id, id, "test", ModuleCategory.MISC, world)); }
        @Override protected void onEnable(ModuleContext scope) { context = scope; }
    }
    private void start(Probe... probes) {
        for (Probe probe : probes) manager.register(probe);
        manager.start();
        for (Probe probe : probes) manager.setEnabled(probe.info().id(), true);
    }
    @After public void close() { manager.close(); client.close(); network.close(); tasks.close(); }

    @Test public void priorityThenClaimOrderIsStableAcrossUpdatesAndReleases() {
        Probe a = new Probe("a"), b = new Probe("b"); start(a, b);
        ControlRequest<Float> first = a.context.controls().timerSpeed(5);
        ControlRequest<Float> second = b.context.controls().timerSpeed(5);
        ControlRequest<Float> high = b.context.controls().timerSpeed(6);
        first.set(1.2f); second.set(1.3f);
        assertEquals(1.2f, controls.timerSpeed().get(), 0);
        second.set(1.4f); assertTrue(first.isWinning());
        high.set(2f); assertTrue(high.isWinning());
        assertEquals(1.2f, first.value().get(), 0); // Losing claims keep their own value.
        high.clear(); assertTrue(first.isWinning());
        first.clear(); assertTrue(second.isWinning());
        first.set(1.1f); assertTrue(first.isWinning());
        first.close(); first.close(); assertTrue(second.isWinning());
        assertFalse(first.set(3f)); assertEquals(2, controls.requestCount());
        manager.setEnabled("b", false);
        assertFalse(controls.timerSpeed().isPresent()); assertEquals(0, controls.requestCount());
    }

    @Test public void cameraClaimsYieldAndRetireWithTheirActivation() {
        Probe a = new Probe("auto"), b = new Probe("look"); start(a, b);
        ControlRequest<CameraPerspective> auto = a.context.controls().perspective(50);
        ControlRequest<CameraPerspective> look = b.context.controls().perspective(100);
        auto.set(CameraPerspective.THIRD_PERSON_BACK);
        look.set(CameraPerspective.THIRD_PERSON_FRONT);
        assertEquals(CameraPerspective.THIRD_PERSON_FRONT, controls.perspective().get());
        manager.setEnabled("look", false);
        assertTrue(auto.isWinning());
        manager.setEnabled("auto", false);
        assertFalse(controls.perspective().isPresent());
        assertFalse(look.set(CameraPerspective.FIRST_PERSON));
    }

    @Test public void channelsAreIndependentButRotationPairsAndHotbarModesAreAtomic() {
        Probe a = new Probe("a"), b = new Probe("b"); start(a, b);
        a.context.controls().rotation(1).set(Rotation.view(30, 20));
        ControlRequest<Rotation> aim = b.context.controls().rotation(2);
        aim.set(Rotation.server(100, -50));
        a.context.controls().hotbar(1).set(HotbarSelection.visible(2));
        ControlRequest<HotbarSelection> spoof = b.context.controls().hotbar(2);
        spoof.set(HotbarSelection.spoof(7));
        a.context.controls().key(InputKey.FORWARD, 1).set(true);
        b.context.controls().key(InputKey.SPRINT, 9).set(false);
        a.context.controls().sprinting(1).set(true);
        b.context.controls().sprinting(2).set(false);
        assertEquals(100, controls.rotation().get().yaw(), 0);
        assertEquals(-50, controls.rotation().get().pitch(), 0);
        assertEquals(Rotation.Mode.SERVER, controls.rotation().get().mode());
        assertTrue(controls.ownsHotbar(spoof));
        assertEquals(HotbarSelection.Mode.SPOOF, controls.hotbar().get().mode());
        assertTrue(controls.key(InputKey.FORWARD).get());
        assertFalse(controls.key(InputKey.SPRINT).get());
        assertFalse(controls.sprinting().get());
        manager.setEnabled("b", false);
        assertEquals(30, controls.rotation().get().yaw(), 0);
        assertEquals(2, controls.hotbar().get().slot());
        assertTrue(controls.sprinting().get());
        assertFalse(controls.key(InputKey.SPRINT).isPresent());
    }

    @Test public void retiredActivationCannotReplaceItsSuccessorOrCreateMoreClaims() {
        Probe probe = new Probe("a"); start(probe);
        ModuleContext oldScope = probe.context;
        ControlRequest<Boolean> old = oldScope.controls().key(InputKey.JUMP, 100);
        old.set(true);
        manager.setEnabled("a", false); manager.setEnabled("a", true);
        ControlRequest<Boolean> current = probe.context.controls().key(InputKey.JUMP, 1);
        current.set(false);
        assertFalse(old.set(true)); old.clear(); old.close();
        assertFalse(oldScope.controls().key(InputKey.JUMP, 1000).set(true));
        assertTrue(current.isWinning()); assertEquals(1, controls.requestCount());
    }

    @Test public void expirationFallsBackAndRenewalRetainsTieOrder() {
        Probe a = new Probe("a"), b = new Probe("b"); start(a, b);
        ControlRequest<Float> fallback = a.context.controls().timerSpeed(0);
        ControlRequest<Float> timed = b.context.controls().timerSpeed(1);
        fallback.set(1.1f); timed.setForTicks(2f, 2);
        controls.beginTick(); assertTrue(timed.isWinning());
        controls.beginTick(); assertFalse(timed.value().isPresent()); assertTrue(fallback.isWinning());
        timed.setForTicks(3f, 1); assertTrue(timed.isWinning());
        controls.beginTick(); assertTrue(fallback.isWinning());
        timed.setForTicks(2f, 1); timed.setForTicks(3f, 3);
        controls.beginTick(); controls.beginTick(); assertTrue(timed.isWinning());
        controls.beginTick(); assertTrue(fallback.isWinning());
    }

    @Test public void worldGenerationInvalidatesValuesEvenForPersistentModules() {
        Probe probe = new Probe("a"); start(probe);
        ControlRequest<HotbarSelection> slot = probe.context.controls().hotbar(1);
        slot.set(HotbarSelection.spoof(3));
        tasks.advanceGeneration();
        assertFalse(slot.isWinning()); assertFalse(slot.value().isPresent());
        assertFalse(controls.hotbar().isPresent());
        assertTrue(slot.set(HotbarSelection.visible(4))); assertTrue(slot.isWinning());
    }

    @Test public void restoringHotbarHandoffsIncludeHiddenClaimsButIgnoreRetiredValues() {
        Probe low = new Probe("low"), high = new Probe("high"); start(low, high);
        ControlRequest<HotbarSelection> restore = low.context.controls().hotbar(1);
        ControlRequest<HotbarSelection> persistent = high.context.controls().hotbar(10);
        persistent.set(HotbarSelection.persistent(3));
        assertFalse(controls.hasRestoringHotbarRequest());
        restore.set(HotbarSelection.visible(2));
        assertTrue(persistent.isWinning());
        assertTrue(controls.hasRestoringHotbarRequest());
        restore.clear(); assertFalse(controls.hasRestoringHotbarRequest());
        restore.setForTicks(HotbarSelection.spoof(2), 1);
        assertTrue(controls.hasRestoringHotbarRequest());
        controls.beginTick(); assertFalse(controls.hasRestoringHotbarRequest());
        restore.set(HotbarSelection.visible(2));
        manager.setEnabled("low", false);
        assertFalse(controls.hasRestoringHotbarRequest());
        manager.setEnabled("low", true);
        low.context.controls().hotbar(1).set(HotbarSelection.spoof(2));
        assertTrue(controls.hasRestoringHotbarRequest());
        tasks.advanceGeneration(); assertFalse(controls.hasRestoringHotbarRequest());
    }

    @Test public void worldModuleReactivationOwnsNewHandles() {
        Probe probe = new Probe("a", true); start(probe);
        assertNull(probe.context);
        manager.setWorldReady(true);
        ControlRequest<Float> old = probe.context.controls().timerSpeed(10); old.set(2f);
        tasks.advanceGeneration(); manager.setWorldReady(false);
        assertEquals(0, controls.requestCount());
        manager.setWorldReady(true);
        probe.context.controls().timerSpeed(0).set(1.5f);
        assertFalse(old.set(3f)); assertEquals(1.5f, controls.timerSpeed().get(), 0);
    }

    @Test public void enablingClaimsAreInvisibleAndFailedOrOneShotEnableLeavesNothing() {
        Probe failed = new Probe("failed") {
            @Override protected void onEnable(ModuleContext scope) {
                super.onEnable(scope);
                ControlRequest<Float> claim = scope.controls().timerSpeed(99);
                assertTrue(claim.set(3f)); assertFalse(claim.isWinning());
                assertFalse(controls.timerSpeed().isPresent());
                throw new IllegalStateException("enable failed");
            }
        };
        Probe once = new Probe("once") {
            @Override protected void onEnable(ModuleContext scope) {
                super.onEnable(scope); scope.controls().timerSpeed(99).set(4f);
                scope.requestDisable(); scope.requestDisable();
                assertFalse(controls.timerSpeed().isPresent());
            }
        };
        start(failed, once);
        assertEquals(ModuleState.FAULTED, manager.require("failed").state());
        assertEquals(ModuleState.DISABLED, manager.require("once").state());
        assertFalse(controls.timerSpeed().isPresent()); assertEquals(0, controls.requestCount());
    }

    @Test public void disableClosesOwnerBeforeHookAndThrowingCleanupStillRemovesClaims() {
        Probe probe = new Probe("a") {
            @Override protected void onDisable() {
                assertFalse(controls.timerSpeed().isPresent());
                throw new IllegalStateException("disable failed");
            }
        };
        start(probe); probe.context.controls().timerSpeed(1).set(2f);
        probe.context.onCleanup(() -> { throw new IllegalStateException("cleanup failed"); });
        manager.setEnabled("a", false);
        assertEquals(ModuleState.FAULTED, manager.require("a").state());
        assertEquals(0, controls.requestCount());
    }

    @Test public void networkFaultImmediatelyYieldsBeforeClientCleanup() throws Exception {
        Probe a = new Probe("a"), b = new Probe("b"); start(a, b);
        a.context.controls().timerSpeed(1).set(1.2f);
        ControlRequest<Float> faulty = b.context.controls().timerSpeed(2); faulty.set(2f);
        b.context.listenNetwork(String.class, 0, false, ignored -> { throw new IllegalStateException("network fault"); });
        Thread worker = new Thread(() -> network.post("fault")); worker.start(); worker.join();
        assertEquals(1.2f, controls.timerSpeed().get(), 0);
        assertFalse(faulty.set(3f)); assertEquals(2, controls.requestCount());
        manager.processFaults();
        assertEquals(ModuleState.FAULTED, manager.require("b").state()); assertEquals(1, controls.requestCount());
    }

    @Test public void clientAndDeferredFaultsReleaseRequests() {
        Probe a = new Probe("a"), b = new Probe("b"); start(a, b);
        a.context.controls().sprinting(1).set(true);
        b.context.controls().key(InputKey.USE_ITEM, 1).set(true);
        a.context.listen(String.class, ignored -> { throw new IllegalStateException("client fault"); });
        b.context.defer(() -> { throw new IllegalStateException("deferred fault"); });
        client.post("fault"); tasks.drain();
        assertFalse(controls.sprinting().isPresent()); assertFalse(controls.key(InputKey.USE_ITEM).isPresent());
        manager.processFaults(); assertEquals(0, controls.requestCount());
    }

    @Test public void invalidValuesDoNotReplaceValidRequests() {
        Probe a = new Probe("a"); start(a);
        ControlRequest<Float> timer = a.context.controls().timerSpeed(0); timer.set(1.2f);
        assertThrows(IllegalArgumentException.class, () -> timer.set(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> timer.set(Float.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> timer.set(0f));
        assertThrows(IllegalArgumentException.class, () -> timer.setForTicks(2f, 0));
        assertThrows(IllegalArgumentException.class, () -> HotbarSelection.spoof(9));
        assertThrows(IllegalArgumentException.class, () -> HotbarSelection.visible(-1));
        assertThrows(IllegalArgumentException.class, () -> Rotation.server(0, 91));
        assertThrows(IllegalArgumentException.class, () -> Rotation.view(Float.NaN, 0));
        assertEquals(1.2f, timer.value().get(), 0);
        assertEquals(-90, Rotation.server(270, 30).yaw(), 0);
    }

    @Test public void offThreadReadsAndMutationsFailWithoutChangingState() throws Exception {
        Probe a = new Probe("a"); start(a);
        ControlRequest<Float> timer = a.context.controls().timerSpeed(0); timer.set(1.2f);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                assertThrows(IllegalStateException.class, () -> timer.set(2f));
                assertThrows(IllegalStateException.class, timer::clear);
                assertThrows(IllegalStateException.class, timer::close);
                assertThrows(IllegalStateException.class, timer::isWinning);
                assertThrows(IllegalStateException.class, controls::timerSpeed);
                assertThrows(IllegalStateException.class, () -> a.context.controls().timerSpeed(9));
            } catch (Throwable error) { failure.set(error); }
        });
        worker.start(); worker.join();
        if (failure.get() != null) throw new AssertionError(failure.get());
        assertEquals(1.2f, controls.timerSpeed().get(), 0); assertEquals(1, controls.requestCount());
        manager.close(); assertFalse(timer.set(3f)); assertEquals(0, controls.requestCount());
    }
}
