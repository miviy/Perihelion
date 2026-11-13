package perihelion.core.lifecycle;

import com.google.gson.JsonParser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import perihelion.core.context.ConnectionContext;
import perihelion.core.diagnostics.ClientLog;
import perihelion.core.event.*;
import perihelion.core.input.Keybind;
import perihelion.core.module.*;
import perihelion.core.setting.Setting;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class ClientRuntimeTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Test public void runtimeResourcesRestoreAfterModulesAndContinuePastCleanupFailures() {
        java.util.List<String> order = new java.util.ArrayList<>();
        ClientRuntime runtime = new ClientRuntime(config(), ClientLog.NONE, r -> {
            r.modules().register(new ClientModule(new ModuleInfo("probe", "Probe", "test", ModuleCategory.MISC, false)) {
                @Override protected void onEnable(ModuleContext scope) { scope.controls().timerSpeed(1).set(2f); }
                @Override protected void onDisable() { order.add("module"); }
            });
            r.own(() -> { assertEquals(0, r.controls().requestCount()); order.add("adapter"); });
            r.own(() -> { order.add("failure"); throw new IllegalStateException("cleanup"); });
        });
        runtime.start(); runtime.modules().setEnabled("probe", true);
        runtime.stop(); runtime.stop();
        runtime.own(() -> order.add("late"));
        assertEquals(java.util.Arrays.asList("module", "failure", "adapter", "late"), order);
    }
    @Test public void runtimeResourcesAlsoCloseOnStartupFailure() {
        AtomicInteger closes = new AtomicInteger();
        ClientRuntime runtime = new ClientRuntime(config(), ClientLog.NONE, r -> {
            r.own(closes::incrementAndGet);
            throw new IllegalStateException("startup");
        });
        runtime.start(); runtime.stop();
        assertEquals(LifecycleState.FAILED, runtime.state()); assertEquals(1, closes.get());
    }
    private Path config() { return temporary.getRoot().toPath().resolve("config/client.json"); }
    private int savedCount() throws Exception {
        return new JsonParser().parse(new String(Files.readAllBytes(config()), "UTF-8")).getAsJsonObject()
                .getAsJsonObject("modules").getAsJsonObject("probe").getAsJsonObject("settings").get("count").getAsInt();
    }
    @Test public void stateBindingAndSettingChangesRemainPersisted() throws Exception {
        AtomicReference<Setting<Integer>> count = new AtomicReference<>();
        AtomicReference<ModuleContext> context = new AtomicReference<>();
        ClientRuntime runtime = new ClientRuntime(config(), ClientLog.NONE, r -> {
            r.modules().register(new ClientModule(new ModuleInfo("probe", "Probe", "test-only", ModuleCategory.MISC, false)) {
                { count.set(setting(Setting.integer("count", "Count", "", 0, 0, 10, r.tasks()::checkThread))); }
                @Override protected void onEnable(ModuleContext scope) { context.set(scope); }
            });
        });
        try {
            runtime.start();
            saveAndAssertClean(runtime);
            runtime.modules().setEnabled("probe", true);
            assertTrue(runtime.config().isDirty());
            saveAndAssertClean(runtime);
            runtime.modules().setBinding("probe", Keybind.parse("keyboard:25"));
            assertTrue(runtime.config().isDirty());
            saveAndAssertClean(runtime);
            count.get().set(7);
            assertTrue(runtime.config().isDirty());
            saveAndAssertClean(runtime);
            com.google.gson.JsonObject saved = new JsonParser().parse(new String(Files.readAllBytes(config()), "UTF-8"))
                    .getAsJsonObject().getAsJsonObject("modules").getAsJsonObject("probe");
            assertTrue(saved.get("enabled").getAsBoolean());
            assertEquals(25, saved.getAsJsonObject("binding").get("code").getAsInt());
            assertEquals(7, savedCount());

            runtime.modules().setEnabled("probe", true);
            runtime.modules().setBinding("probe", Keybind.parse("keyboard:25"));
            count.get().set(7);
            assertFalse(runtime.config().isDirty());
            context.get().requestDisable();
            assertTrue(runtime.config().isDirty());
            saveAndAssertClean(runtime);
            runtime.modules().setEnabled("probe", true);
            saveAndAssertClean(runtime);
            runtime.modules().quarantine("probe", new IllegalStateException("test fault"));
            assertTrue(runtime.config().isDirty());
            saveAndAssertClean(runtime);
            saved = new JsonParser().parse(new String(Files.readAllBytes(config()), "UTF-8"))
                    .getAsJsonObject().getAsJsonObject("modules").getAsJsonObject("probe");
            assertFalse(saved.get("enabled").getAsBoolean());
        } finally { runtime.stop(); }
    }
    private void saveAndAssertClean(ClientRuntime runtime) throws Exception {
        runtime.config().saveNow().get(3, TimeUnit.SECONDS);
        assertFalse(runtime.config().isDirty());
    }
    @Test public void rebindingUpdatesInputWithoutResettingHeldKeys() {
        Keybind first = Keybind.parse("keyboard:25"), second = Keybind.parse("mouse:0");
        ClientRuntime runtime = new ClientRuntime(config(), ClientLog.NONE, r ->
                r.modules().register(new ClientModule(new ModuleInfo("probe", "Probe", "test-only", ModuleCategory.MISC, false)) {}));
        try {
            runtime.start();
            runtime.input().setAllowed(true, java.util.Collections.emptyList());
            runtime.modules().setBinding("probe", first);
            runtime.input().accept(first, true);
            assertTrue(runtime.modules().require("probe").requestedEnabled());
            runtime.modules().setBinding("probe", second);
            runtime.input().accept(first, false); runtime.input().accept(first, true);
            assertTrue(runtime.modules().require("probe").requestedEnabled());
            runtime.input().accept(second, true);
            assertFalse(runtime.modules().require("probe").requestedEnabled());
            runtime.modules().setBinding("probe", first);
            runtime.input().accept(first, true);
            assertFalse(runtime.modules().require("probe").requestedEnabled());
            runtime.input().accept(first, false); runtime.input().accept(first, true);
            assertTrue(runtime.modules().require("probe").requestedEnabled());
            runtime.modules().setBinding("probe", Keybind.UNBOUND);
            runtime.input().accept(first, false); runtime.input().accept(first, true);
            assertTrue(runtime.modules().require("probe").requestedEnabled());
        } finally { runtime.stop(); }
    }
    @Test public void tickEndSavesDebouncedChangesEvenWithoutEventListeners() throws Exception {
        AtomicReference<Setting<Integer>> count = new AtomicReference<>();
        ClientRuntime runtime = new ClientRuntime(config(), ClientLog.NONE, r -> {
            r.modules().register(new ClientModule(new ModuleInfo("probe", "Probe", "test-only", ModuleCategory.MISC, false)) {
                { count.set(setting(Setting.integer("count", "Count", "", 0, 0, 1000, r.tasks()::checkThread))); }
            });
        });
        try {
            runtime.start();
            for (int i = 1; i <= 1000; i++) { count.get().set(i); runtime.tickEnd(); }
            assertEquals(0, runtime.clientEvents().subscriptionCount());
            assertTrue(runtime.config().isDirty()); assertFalse(runtime.config().store().isDirty());
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (runtime.config().isDirty() && System.nanoTime() < deadline) {
                runtime.tickEnd();
                Thread.sleep(1);
            }
            assertFalse("Automatic save completed", runtime.config().isDirty());
            assertNull(runtime.config().store().lastFailure());
            assertEquals(1000, savedCount());
        } finally { runtime.stop(); }
    }
    @Test public void shutdownCapturesCleanupChangesEvenAfterAnExplicitSaveWasClean() throws Exception {
        AtomicReference<Setting<Integer>> count = new AtomicReference<>();
        ClientRuntime runtime = new ClientRuntime(config(), ClientLog.NONE, r -> {
            r.modules().register(new ClientModule(new ModuleInfo("probe", "Probe", "test-only", ModuleCategory.MISC, false)) {
                { count.set(setting(Setting.integer("count", "Count", "", 0, 0, 10, r.tasks()::checkThread))); }
                @Override protected void onDisable() { count.get().set(9); }
            });
        });
        try {
            runtime.start();
            runtime.modules().setEnabled("probe", true);
            count.get().set(3);
            runtime.config().saveNow().get(3, TimeUnit.SECONDS);
            assertFalse(runtime.config().isDirty()); assertEquals(3, savedCount());
            runtime.stop(); runtime.stop();
            assertEquals(9, savedCount());
            assertFalse(runtime.config().isDirty());
            assertTrue(runtime.modules().require("probe").requestedEnabled());
        } finally { runtime.stop(); }
    }
    @Test public void startAndStopAreIdempotentAndEmptyRegistryIsUsable() throws Exception {
        AtomicInteger definitions = new AtomicInteger();
        ClientRuntime runtime = new ClientRuntime(config(), ClientLog.NONE, r -> definitions.incrementAndGet());
        runtime.start(); runtime.start();
        assertEquals(1, definitions.get()); assertEquals(LifecycleState.RUNNING, runtime.state());
        assertTrue(runtime.modules().entries().isEmpty());
        runtime.stop(); runtime.stop();
        assertEquals(LifecycleState.STOPPED, runtime.state()); assertTrue(Files.isRegularFile(config()));
        assertFalse(runtime.config().store().isDirty());
        assertEquals(0, runtime.clientEvents().subscriptionCount()); assertEquals(0, runtime.networkEvents().subscriptionCount());
        assertEquals(0, runtime.tasks().pendingCount()); assertTrue(runtime.tasks().isClosed());
        assertThrows(IllegalStateException.class, runtime::start);
    }
    @Test public void startupRollbackClosesPartialScopesAndPreservesOriginalFile() throws Exception {
        Files.createDirectories(config().getParent());
        byte[] original = "{\"schemaVersion\":1,\"unknown\":true}".getBytes("UTF-8");
        Files.write(config(), original);
        ClientRuntime runtime = new ClientRuntime(config(), ClientLog.NONE, r -> {
            EventOwner owner = new EventOwner("partial", e -> {}); owner.activate();
            r.clientEvents().subscribe(owner, String.class, e -> {});
            r.networkEvents().subscribe(owner, String.class, e -> {});
            r.tasks().execute(() -> fail("startup rollback must discard work"));
            throw new IllegalStateException("test startup");
        });
        runtime.start(); runtime.stop();
        assertEquals(LifecycleState.FAILED, runtime.state()); assertNotNull(runtime.lastFailure());
        assertEquals(0, runtime.clientEvents().subscriptionCount()); assertEquals(0, runtime.networkEvents().subscriptionCount());
        assertTrue(runtime.tasks().isClosed()); assertEquals(0, runtime.tasks().pendingCount());
        assertArrayEquals(original, Files.readAllBytes(config()));
    }
    @Test public void identityChangeDiscardsDeferredWorkAndReactivatesWorldModules() {
        AtomicInteger enables = new AtomicInteger(), disables = new AtomicInteger(), events = new AtomicInteger();
        ClientRuntime runtime = new ClientRuntime(config(), ClientLog.NONE, r -> {
            r.modules().register(new ClientModule(new ModuleInfo("world", "World", "test-only", ModuleCategory.MISC, true)) {
                protected void onEnable(ModuleContext c) { enables.incrementAndGet(); }
                protected void onDisable() { disables.incrementAndGet(); }
            });
        });
        runtime.start(); runtime.modules().setEnabled("world", true);
        EventOwner owner = new EventOwner("test", e -> {}); owner.activate();
        runtime.clientEvents().subscribe(owner, WorldContextEvent.class, e -> events.incrementAndGet());
        ConnectionContext local = new ConnectionContext(ConnectionContext.Kind.SINGLEPLAYER, "");
        runtime.updateWorld(true, local, true);
        long first = runtime.tasks().generation();
        runtime.tasks().executeForGeneration(first, () -> fail("previous world task"));
        runtime.updateWorld(true, local, false);
        assertEquals(first, runtime.tasks().generation());
        runtime.updateWorld(true, local, true); runtime.tickStart();
        assertEquals(first + 1, runtime.tasks().generation());
        assertEquals(2, enables.get()); assertEquals(1, disables.get()); assertEquals(2, events.get());
        runtime.updateWorld(false, ConnectionContext.DISCONNECTED, true);
        assertEquals(ModuleState.WAITING_FOR_WORLD, runtime.modules().require("world").state());
        runtime.stop(); assertEquals(2, disables.get());
    }
    @Test public void stoppingDuringDefinitionsCannotRestartTheRuntime() {
        ClientRuntime runtime = new ClientRuntime(config(), ClientLog.NONE, ClientRuntime::stop);
        runtime.start();
        assertEquals(LifecycleState.STOPPED, runtime.state());
        assertTrue(runtime.tasks().isClosed());
        assertThrows(IllegalStateException.class, runtime::start);
    }
    @Test public void stoppingDuringModuleStartupCannotPublishAnActivation() {
        AtomicInteger cleanup = new AtomicInteger();
        ClientRuntime runtime = new ClientRuntime(config(), ClientLog.NONE, r -> {
            r.modules().register(new ClientModule(new ModuleInfo("stop", "Stop", "test-only", ModuleCategory.MISC, false)) {
                protected void onEnable(ModuleContext scope) {
                    scope.listen(String.class, event -> fail("Stopped activation"));
                    r.stop();
                }
                protected void onDisable() { cleanup.incrementAndGet(); }
            });
            r.modules().setEnabled("stop", true);
        });
        // Configuration owns requested state, so request the test activation there.
        try {
            Files.createDirectories(config().getParent());
            Files.write(config(), "{\"schemaVersion\":1,\"modules\":{\"stop\":{\"enabled\":true}}}".getBytes("UTF-8"));
        } catch (java.io.IOException failure) { throw new AssertionError(failure); }
        runtime.start();
        assertEquals(LifecycleState.STOPPED, runtime.state());
        assertEquals(ModuleState.DISABLED, runtime.modules().require("stop").state());
        assertEquals(1, cleanup.get());
        assertEquals(0, runtime.clientEvents().subscriptionCount());
        assertEquals(0, runtime.networkEvents().subscriptionCount());
        assertTrue(runtime.tasks().isClosed());
        runtime.stop();
        assertEquals(1, cleanup.get());
    }
}
