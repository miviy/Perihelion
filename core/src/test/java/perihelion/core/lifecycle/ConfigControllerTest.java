package perihelion.core.lifecycle;

import static org.junit.Assert.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import perihelion.core.concurrent.ClientTaskQueue;
import perihelion.core.config.ConfigStore;
import perihelion.core.diagnostics.ClientLog;
import perihelion.core.event.EventBus;
import perihelion.core.input.Keybind;
import perihelion.core.module.ClientModule;
import perihelion.core.module.ModuleCategory;
import perihelion.core.module.ModuleContext;
import perihelion.core.module.ModuleEntry;
import perihelion.core.module.ModuleInfo;
import perihelion.core.module.ModuleManager;
import perihelion.core.module.ModuleState;
import perihelion.core.setting.Setting;

public class ConfigControllerTest {
  @Rule public TemporaryFolder folder = new TemporaryFolder();
  private final List<Fixture> fixtures = new ArrayList<>();

  @After
  public void closeFixtures() {
    for (Fixture fixture : fixtures) {
      fixture.config.close();
      fixture.modules.close();
      fixture.tasks.close();
    }
  }

  private Fixture fixture() {
    Fixture fixture =
        new Fixture(folder.getRoot().toPath().resolve("client-" + fixtures.size() + ".json"));
    fixtures.add(fixture);
    return fixture;
  }

  private static JsonObject module(boolean enabled, int count) {
    JsonObject module = new JsonObject();
    module.addProperty("enabled", enabled);
    JsonObject settings = new JsonObject();
    settings.addProperty("count", count);
    module.add("settings", settings);
    return module;
  }

  private static JsonObject document(String id, JsonObject module) {
    JsonObject document = new JsonObject();
    document.addProperty("schemaVersion", 1);
    JsonObject modules = new JsonObject();
    modules.add(id, module);
    document.add("modules", modules);
    return document;
  }

  private static void write(Fixture fixture, JsonObject document) throws Exception {
    Files.write(fixture.file, document.toString().getBytes(StandardCharsets.UTF_8));
  }

  private static void pump(Fixture fixture, CompletableFuture<?> result) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
    while (!result.isDone() && System.nanoTime() < deadline) {
      fixture.tasks.drain();
      Thread.sleep(1);
    }
    assertTrue("Reload completed", result.isDone());
  }

  private static JsonObject read(Path file) throws Exception {
    return new JsonParser()
        .parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8))
        .getAsJsonObject();
  }

  private static JsonObject savedModule(Path file) throws Exception {
    return read(file).getAsJsonObject("modules").getAsJsonObject("sample");
  }

  private static int savedCount(Path file) throws Exception {
    return savedModule(file).getAsJsonObject("settings").get("count").getAsInt();
  }

  private static void awaitWrite(Fixture fixture) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
    while (fixture.store.isDirty()
        && fixture.store.lastFailure() == null
        && System.nanoTime() < deadline) Thread.sleep(1);
    assertNull(fixture.store.lastFailure());
    assertFalse("Disk write completed", fixture.store.isDirty());
  }

  @Test
  public void rapidChangesCoalesceUntilQuietAndIdleTicksDoNotWriteAgain() throws Exception {
    Fixture fixture = fixture();
    Sample sample = fixture.add("sample");
    fixture.config.load();
    fixture.modules.start();
    for (int i = 1; i <= 1000; i++) {
      sample.count.set(i % 11);
      fixture.modules.setEnabled("sample", i % 2 == 0);
      fixture.modules.setBinding("sample", new Keybind(Keybind.Device.KEYBOARD, i % 10 + 1));
      fixture.advanceMillis(10);
      fixture.config.tick();
    }
    assertTrue(fixture.config.isDirty());
    assertFalse(fixture.store.isDirty()); // No JSON snapshot has been submitted to the writer.
    assertFalse(Files.exists(fixture.file));
    assertEquals(0, fixture.tasks.pendingCount());
    fixture.advanceMillis(ConfigStore.SAVE_DEBOUNCE_MILLIS - 11);
    fixture.config.tick();
    assertFalse(fixture.store.isDirty());
    assertFalse(Files.exists(fixture.file));
    fixture.advanceMillis(1);
    fixture.config.tick();
    awaitWrite(fixture);
    assertFalse(fixture.config.isDirty());
    assertEquals(10, savedCount(fixture.file));
    assertTrue(savedModule(fixture.file).get("enabled").getAsBoolean());
    assertEquals(1, savedModule(fixture.file).getAsJsonObject("binding").get("code").getAsInt());
    Path backup = fixture.file.resolveSibling(fixture.file.getFileName() + ".bak");
    for (int i = 0; i < 20; i++) {
      fixture.advanceMillis(ConfigStore.SAVE_DEBOUNCE_MILLIS);
      fixture.config.tick();
    }
    assertFalse(fixture.store.isDirty());
    assertFalse(Files.exists(backup));
    sample.count.set(4);
    fixture.advanceMillis(ConfigStore.SAVE_DEBOUNCE_MILLIS);
    fixture.config.tick();
    awaitWrite(fixture);
    assertEquals(4, savedCount(fixture.file));
    assertEquals(10, savedCount(backup));
  }

  @Test
  public void explicitSaveBypassesDebounceAndWriteCompletionCannotClearNewEdits() throws Exception {
    Fixture fixture = fixture();
    Sample sample = fixture.add("sample");
    sample.count.set(2);
    CompletableFuture<Void> first = fixture.config.saveNow();
    sample.count.set(3);
    first.get(3, TimeUnit.SECONDS);
    assertEquals(2, savedCount(fixture.file));
    assertTrue(fixture.config.isDirty());
    assertFalse(fixture.store.isDirty());
    fixture.advanceMillis(ConfigStore.SAVE_DEBOUNCE_MILLIS);
    fixture.config.tick();
    awaitWrite(fixture);
    assertEquals(3, savedCount(fixture.file));
    assertFalse(fixture.config.isDirty());
  }

  @Test
  public void shutdownCapturesLatestChangesWithoutAnyTickAndOnlyClosesOnce() throws Exception {
    Fixture fixture = fixture();
    Sample sample = fixture.add("sample");
    for (int i = 0; i < 100; i++) sample.count.set(i % 11);
    sample.count.set(7);
    fixture.modules.setEnabled("sample", true);
    fixture.modules.setBinding("sample", new Keybind(Keybind.Device.MOUSE, 2));
    assertFalse(Files.exists(fixture.file));
    assertFalse(fixture.store.isDirty());
    assertTrue(fixture.config.close());
    assertEquals(7, savedCount(fixture.file));
    assertTrue(savedModule(fixture.file).get("enabled").getAsBoolean());
    assertEquals(
        "MOUSE", savedModule(fixture.file).getAsJsonObject("binding").get("device").getAsString());
    assertFalse(fixture.config.isDirty());
    assertTrue(fixture.config.close());
    fixture.advanceMillis(ConfigStore.SAVE_DEBOUNCE_MILLIS);
    fixture.config.tick();
    assertFalse(Files.exists(fixture.file.resolveSibling(fixture.file.getFileName() + ".bak")));
  }

  @Test
  public void reloadDiscardsUncapturedChangesAndPausesAutosaveUntilApplication() throws Exception {
    Fixture fixture = fixture();
    Sample sample = fixture.add("sample");
    write(fixture, document("sample", module(true, 7)));
    sample.count.set(3);
    assertTrue(fixture.config.isDirty());
    CompletableFuture<Void> pending = fixture.config.reload();
    fixture.advanceMillis(ConfigStore.SAVE_DEBOUNCE_MILLIS);
    fixture.config.tick();
    assertFalse(fixture.config.isDirty());
    assertEquals(7, savedCount(fixture.file));
    pump(fixture, pending);
    pending.get();
    assertEquals(Integer.valueOf(7), sample.count.get());
    fixture.config.tick();
    assertFalse(fixture.config.isDirty());
    assertFalse(Files.exists(fixture.file.resolveSibling(fixture.file.getFileName() + ".bak")));
  }

  @Test
  public void newerSchemaRemainsUntouchedByChangesTicksAndShutdown() throws Exception {
    Fixture fixture = fixture();
    Sample sample = fixture.add("sample");
    JsonObject newer = document("sample", module(true, 7));
    newer.addProperty("schemaVersion", 2);
    write(fixture, newer);
    byte[] original = Files.readAllBytes(fixture.file);
    fixture.config.load();
    sample.count.set(3);
    fixture.modules.setEnabled("sample", true);
    fixture.advanceMillis(ConfigStore.SAVE_DEBOUNCE_MILLIS);
    fixture.config.tick();
    assertTrue(fixture.store.isReadOnly());
    assertFalse(fixture.config.isDirty());
    assertTrue(fixture.config.close());
    assertArrayEquals(original, Files.readAllBytes(fixture.file));
    assertFalse(Files.exists(fixture.file.resolveSibling(fixture.file.getFileName() + ".bak")));
  }

  @Test
  public void failedAutomaticWriteRemainsDirtyUntilExplicitRetry() throws Exception {
    Fixture fixture = fixture();
    Sample sample = fixture.add("sample");
    Files.createDirectory(fixture.file); // A directory cannot be replaced by the JSON file.
    sample.count.set(3);
    fixture.advanceMillis(ConfigStore.SAVE_DEBOUNCE_MILLIS);
    fixture.config.tick();
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
    while (fixture.store.lastFailure() == null && System.nanoTime() < deadline) Thread.sleep(1);
    assertNotNull(fixture.store.lastFailure());
    assertTrue(fixture.config.isDirty());
    assertTrue(fixture.store.isDirty());
    Files.delete(fixture.file);
    fixture.config.saveNow().get(3, TimeUnit.SECONDS);
    assertEquals(3, savedCount(fixture.file));
    assertFalse(fixture.config.isDirty());
    assertNull(fixture.store.lastFailure());
  }

  @Test
  public void invalidValuesDefaultAndUnknownMembersRoundTrip() throws Exception {
    Fixture fixture = fixture();
    Sample sample = fixture.add("sample");
    JsonObject source = module(true, 999);
    source.addProperty("futureModuleData", "keep");
    source.getAsJsonObject("settings").addProperty("futureSetting", "keep");
    JsonObject badBinding = new JsonObject();
    badBinding.addProperty("device", "KEYBOARD");
    badBinding.addProperty("code", 1.5);
    source.add("binding", badBinding);
    JsonObject document = document("sample", source);
    document.addProperty("futureRootData", "keep");
    document.getAsJsonObject("modules").add("futureModule", module(true, 8));
    write(fixture, document);
    fixture.config.load();
    fixture.modules.start();
    assertEquals(Integer.valueOf(1), sample.count.get());
    assertEquals(Keybind.UNBOUND, fixture.modules.require("sample").binding());
    assertEquals(ModuleState.ENABLED, fixture.modules.require("sample").state());
    JsonObject snapshot = fixture.config.snapshot();
    assertEquals("keep", snapshot.get("futureRootData").getAsString());
    assertTrue(snapshot.getAsJsonObject("modules").has("futureModule"));
    JsonObject roundTrip = snapshot.getAsJsonObject("modules").getAsJsonObject("sample");
    assertEquals("keep", roundTrip.get("futureModuleData").getAsString());
    assertEquals("keep", roundTrip.getAsJsonObject("settings").get("futureSetting").getAsString());
    assertEquals(1, roundTrip.getAsJsonObject("settings").get("count").getAsInt());
  }

  @Test
  public void missingBindingUsesDefinitionDefaultButExplicitUnboundPersists() throws Exception {
    Fixture fixture = fixture();
    Keybind rightShift = Keybind.parse("keyboard:54");
    fixture.modules.register(
        new ClientModule(
            new ModuleInfo("opener", "Opener", "test-only", ModuleCategory.CLIENT, false)) {
          {
            defaultBinding(rightShift);
          }
        });
    assertEquals(rightShift, fixture.modules.require("opener").binding());
    fixture.config.load();
    assertEquals(rightShift, fixture.modules.require("opener").binding());
    fixture.modules.setBinding("opener", Keybind.UNBOUND);
    fixture.config.saveNow().get(3, TimeUnit.SECONDS);
    fixture.config.load();
    assertEquals(Keybind.UNBOUND, fixture.modules.require("opener").binding());
  }

  @Test
  public void throwingSettingObserverQuarantinesOnlyItsModule() throws Exception {
    Fixture fixture = fixture();
    Sample broken = fixture.add("broken");
    Sample healthy = fixture.add("healthy");
    broken.count.onChange(
        (setting, before, after) -> {
          throw new IllegalStateException("Broken observer");
        });
    JsonObject document = document("broken", module(true, 2));
    document.getAsJsonObject("modules").add("healthy", module(true, 3));
    write(fixture, document);
    fixture.config.load();
    fixture.modules.start();
    ModuleEntry faulted = fixture.modules.require("broken");
    assertEquals(ModuleState.FAULTED, faulted.state());
    assertFalse(faulted.requestedEnabled());
    assertEquals("Broken observer", faulted.lastFailure().getMessage());
    assertEquals(ModuleState.ENABLED, fixture.modules.require("healthy").state());
    assertEquals(Integer.valueOf(3), healthy.count.get());
    assertEquals(0, broken.enables);
    assertEquals(1, healthy.enables);
    fixture.modules.close();
    assertEquals(0, broken.disables);
    assertEquals(1, healthy.disables);
  }

  @Test
  public void throwingSettingObserverDuringReloadDoesNotRepeatCleanup() throws Exception {
    Fixture fixture = fixture();
    Sample broken = fixture.add("broken");
    fixture.modules.setEnabled("broken", true);
    fixture.modules.start();
    broken.count.onChange(
        (setting, before, after) -> {
          throw new IllegalStateException("Broken observer");
        });
    write(fixture, document("broken", module(true, 2)));
    CompletableFuture<Void> reload = fixture.config.reload();
    pump(fixture, reload);
    reload.get();
    ModuleEntry faulted = fixture.modules.require("broken");
    assertEquals(ModuleState.FAULTED, faulted.state());
    assertFalse(faulted.requestedEnabled());
    assertEquals("Broken observer", faulted.lastFailure().getMessage());
    assertEquals(1, broken.enables);
    fixture.modules.close();
    assertEquals(1, broken.disables);
  }

  @Test
  public void cleanupFailureDuringReloadIsNotAutomaticallyReenabled() throws Exception {
    Fixture fixture = fixture();
    Sample broken = fixture.add("broken");
    Sample healthy = fixture.add("healthy");
    fixture.modules.setEnabled("broken", true);
    fixture.modules.setEnabled("healthy", true);
    fixture.modules.start();
    broken.breakCleanup = true;
    JsonObject document = document("broken", module(true, 2));
    document.getAsJsonObject("modules").add("healthy", module(true, 3));
    write(fixture, document);
    CompletableFuture<Void> reload = fixture.config.reload();
    pump(fixture, reload);
    reload.get();
    assertEquals(ModuleState.FAULTED, fixture.modules.require("broken").state());
    assertFalse(fixture.modules.require("broken").requestedEnabled());
    assertEquals(1, broken.enables);
    assertEquals(ModuleState.ENABLED, fixture.modules.require("healthy").state());
    assertEquals(Integer.valueOf(3), healthy.count.get());
  }

  @Test
  public void reloadRetiresCrossModuleObserversAndResumesOneFreshActivation() throws Exception {
    Fixture fixture = fixture();
    Sample source = fixture.add("source");
    List<Integer> observed = new ArrayList<>();
    List<Integer> enabledValues = new ArrayList<>();
    fixture.modules.register(
        new ClientModule(
            new ModuleInfo("watcher", "Watcher", "test-only", ModuleCategory.MISC, false)) {
          @Override
          protected void onEnable(ModuleContext context) {
            enabledValues.add(source.count.get());
            context.observe(source.count, (setting, before, after) -> observed.add(after));
          }
        });
    fixture.modules.start();
    fixture.modules.setEnabled("source", true);
    fixture.modules.setEnabled("watcher", true);
    fixture.modules.setSetting("source", source.count, 2);
    JsonObject document = document("source", module(true, 7));
    document.getAsJsonObject("modules").add("watcher", module(true, 0));
    write(fixture, document);
    CompletableFuture<Void> reload = fixture.config.reload();
    pump(fixture, reload);
    reload.get();
    assertEquals(java.util.Arrays.asList(2), observed);
    assertEquals(java.util.Arrays.asList(1, 7), enabledValues);
    assertTrue(fixture.modules.setSetting("source", source.count, 8));
    assertEquals(java.util.Arrays.asList(2, 8), observed);
    assertEquals(ModuleState.ENABLED, fixture.modules.require("source").state());
    assertEquals(ModuleState.ENABLED, fixture.modules.require("watcher").state());
  }

  @Test
  public void reloadDoesNotReviveAnObserverWhoseFaultIsAwaitingCleanup() throws Exception {
    Fixture fixture = fixture();
    Sample source = fixture.add("source");
    RuntimeException failure = new IllegalStateException("watcher");
    fixture.modules.register(
        new ClientModule(
            new ModuleInfo("watcher", "Watcher", "test-only", ModuleCategory.MISC, false)) {
          @Override
          protected void onEnable(ModuleContext context) {
            context.observe(
                source.count,
                (setting, before, after) -> {
                  throw failure;
                });
          }
        });
    fixture.modules.start();
    fixture.modules.setEnabled("source", true);
    fixture.modules.setEnabled("watcher", true);
    source.count.set(2); // Queued owner fault, before the usual tick/boundary drain.
    JsonObject document = document("source", module(true, 7));
    document.getAsJsonObject("modules").add("watcher", module(true, 0));
    write(fixture, document);
    CompletableFuture<Void> reload = fixture.config.reload();
    pump(fixture, reload);
    reload.get();
    assertEquals(ModuleState.ENABLED, fixture.modules.require("source").state());
    assertEquals(Integer.valueOf(7), source.count.get());
    assertEquals(ModuleState.FAULTED, fixture.modules.require("watcher").state());
    assertSame(failure, fixture.modules.require("watcher").lastFailure());
    assertFalse(fixture.modules.require("watcher").requestedEnabled());
  }

  @Test
  public void rejectedReloadHandoffClearsFlagAndAllowsRetry() throws Exception {
    Fixture fixture = fixture();
    fixture.add("sample");
    for (int i = 0; i < ClientTaskQueue.CAPACITY; i++) assertTrue(fixture.tasks.execute(() -> {}));
    CompletableFuture<Void> rejected = fixture.config.reload();
    assertThrows(ExecutionException.class, () -> rejected.get(3, TimeUnit.SECONDS));
    assertFalse(fixture.config.isReloading());
    while (fixture.tasks.pendingCount() > 0) fixture.tasks.drain();
    CompletableFuture<Void> retry = fixture.config.reload();
    pump(fixture, retry);
    retry.get();
    assertFalse(fixture.config.isReloading());
  }

  @Test
  public void storeFailureDoesNotLeaveReloadFlagStuck() throws Exception {
    Fixture fixture = fixture();
    fixture.add("sample");
    assertTrue(fixture.store.close(2, TimeUnit.SECONDS));
    CompletableFuture<Void> failed = fixture.config.reload();
    assertThrows(ExecutionException.class, () -> failed.get(3, TimeUnit.SECONDS));
    assertFalse(fixture.config.isReloading());
    // Failed save submission during close is reported without escaping or skipping writer close.
    assertFalse(fixture.config.close());
    assertTrue(fixture.store.close(2, TimeUnit.SECONDS));
  }

  @Test
  public void closeCompletesReloadWhoseAcceptedClientTaskIsNeverDrained() throws Exception {
    Fixture fixture = fixture();
    fixture.add("sample");
    CompletableFuture<Void> pending = fixture.config.reload();
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
    while (fixture.tasks.pendingCount() == 0 && System.nanoTime() < deadline) Thread.sleep(1);
    assertTrue(fixture.tasks.pendingCount() > 0);
    assertTrue(fixture.config.close());
    fixture.tasks.close();
    assertThrows(ExecutionException.class, pending::get);
    assertFalse(fixture.config.isReloading());
  }

  @Test
  public void editsMadeDuringReloadAreNotOverwrittenByReadResult() throws Exception {
    Fixture fixture = fixture();
    Sample sample = fixture.add("sample");
    write(fixture, document("sample", module(true, 7)));
    CompletableFuture<Void> pending = fixture.config.reload();
    sample.count.set(3);
    fixture.advanceMillis(ConfigStore.SAVE_DEBOUNCE_MILLIS);
    fixture.config.tick();
    assertFalse(fixture.store.isDirty()); // Do not race a still-pending reload with a new write.
    assertTrue(fixture.config.isDirty());
    pump(fixture, pending);
    assertThrows(ExecutionException.class, pending::get);
    assertEquals(Integer.valueOf(3), sample.count.get());
    assertFalse(fixture.config.isReloading());
    fixture.config.tick();
    awaitWrite(fixture);
    assertEquals(3, savedCount(fixture.file));
    assertFalse(fixture.config.isDirty());
  }

  @Test
  public void namedLoadPersistsWorkingSetupAndLeavesPresetUnchanged() throws Exception {
    Fixture f = fixture();
    Sample sample = f.add("sample");
    f.config.load();
    f.modules.start();
    sample.count.set(3);
    awaitProfile(f.config.saveProfile("build", false));
    sample.count.set(7);
    CompletableFuture<Void> loaded = f.config.loadProfile("build");
    pump(f, loaded);
    loaded.get();
    assertEquals(Integer.valueOf(3), sample.count.get());
    assertEquals(3, savedCount(f.file));
    sample.count.set(8);
    f.advanceMillis(751);
    f.config.tick();
    awaitWrite(f);
    assertEquals(8, savedCount(f.file));
    assertEquals(3, savedCount(f.store.profilePath("build")));
  }

  @Test
  public void invalidOrRacingProfileLoadKeepsLiveEditsAndCanRetry() throws Exception {
    Fixture f = fixture();
    Sample sample = f.add("sample");
    f.config.load();
    f.modules.start();
    sample.count.set(2);
    awaitProfile(f.config.saveProfile("build", false));
    CompletableFuture<Void> loaded = f.config.loadProfile("build");
    sample.count.set(9);
    pump(f, loaded);
    assertThrows(ExecutionException.class, () -> loaded.get());
    assertEquals(Integer.valueOf(9), sample.count.get());
    assertFalse(f.config.isReloading());
    Files.write(f.store.profilePath("build"), "{broken".getBytes(StandardCharsets.UTF_8));
    CompletableFuture<Void> invalid = f.config.loadProfile("build");
    pump(f, invalid);
    assertThrows(ExecutionException.class, () -> invalid.get());
    assertEquals(Integer.valueOf(9), sample.count.get());
    assertTrue(f.config.isDirty());
    awaitProfile(f.config.saveProfile("valid", false));
    CompletableFuture<Void> retry = f.config.loadProfile("valid");
    pump(f, retry);
    retry.get();
    assertFalse(f.config.isReloading());
  }

  @Test
  public void closingDuringProfileLoadCompletesFutureWithoutApplyingLateResult() throws Exception {
    Fixture f = fixture();
    Sample sample = f.add("sample");
    f.config.load();
    f.modules.start();
    sample.count.set(2);
    awaitProfile(f.config.saveProfile("build", false));
    sample.count.set(6);
    CompletableFuture<Void> pending = f.config.loadProfile("build");
    f.config.close();
    f.tasks.drain();
    assertTrue(pending.isCompletedExceptionally());
    assertEquals(Integer.valueOf(6), sample.count.get());
  }

  private static void awaitProfile(CompletableFuture<?> operation) throws Exception {
    operation.get(3, TimeUnit.SECONDS);
  }

  @Test
  public void selectiveLoadsIndependentlyIncludeVisualsAndBindingsAndPersistTheResult()
      throws Exception {
    for (boolean visuals : new boolean[] {false, true}) {
      for (boolean bindings : new boolean[] {false, true}) {
        Fixture f = fixture();
        Sample gameplay = f.add("sample"), visual = f.add("visual");
        f.config.load();
        f.modules.start();
        gameplay.count.set(3);
        visual.count.set(4);
        Keybind preset = new Keybind(Keybind.Device.KEYBOARD, 42);
        f.modules.setBinding("sample", preset);
        f.modules.setBinding("visual", preset);
        f.modules.setEnabled("visual", true);
        awaitProfile(f.config.saveProfile("selective-" + fixtures.size(), false));
        byte[] original = Files.readAllBytes(f.store.profilePath("selective-" + fixtures.size()));
        gameplay.count.set(7);
        visual.count.set(8);
        Keybind live = new Keybind(Keybind.Device.MOUSE, 2);
        f.modules.setBinding("sample", live);
        f.modules.setBinding("visual", live);
        f.modules.setEnabled("visual", false);
        CompletableFuture<Void> loaded =
            f.config.loadProfile(
                "selective-" + fixtures.size(),
                bindings,
                entry -> visuals || !entry.info().id().equals("visual"));
        pump(f, loaded);
        loaded.get();
        assertEquals(Integer.valueOf(3), gameplay.count.get());
        assertEquals(Integer.valueOf(visuals ? 4 : 8), visual.count.get());
        assertEquals(visuals, f.modules.require("visual").requestedEnabled());
        for (String id : new String[] {"sample", "visual"}) {
          assertEquals(bindings ? preset : live, f.modules.require(id).binding());
          JsonObject saved = read(f.file).getAsJsonObject("modules").getAsJsonObject(id);
          assertEquals(bindings ? 42 : 2, saved.getAsJsonObject("binding").get("code").getAsInt());
        }
        assertEquals(3, savedCount(f.file));
        assertEquals(
            visuals ? 4 : 8,
            read(f.file)
                .getAsJsonObject("modules")
                .getAsJsonObject("visual")
                .getAsJsonObject("settings")
                .get("count")
                .getAsInt());
        assertArrayEquals(
            original, Files.readAllBytes(f.store.profilePath("selective-" + fixtures.size())));
      }
    }
  }

  private static final class Fixture {
    final Path file;
    final ClientTaskQueue tasks = new ClientTaskQueue(Thread.currentThread(), ClientLog.NONE);
    final EventBus clientEvents = new EventBus(tasks::checkThread, ClientLog.NONE);
    final EventBus networkEvents = new EventBus(() -> {}, ClientLog.NONE);
    final ModuleManager modules =
        new ModuleManager(tasks, clientEvents, networkEvents, ClientLog.NONE);
    final ConfigStore store;
    final ConfigController config;
    long nanos;

    Fixture(Path file) {
      this.file = file;
      store = new ConfigStore(file, ClientLog.NONE);
      config = new ConfigController(store, modules, tasks, ClientLog.NONE, () -> nanos);
      modules.onChange(config::changed);
    }

    void advanceMillis(long millis) {
      nanos += TimeUnit.MILLISECONDS.toNanos(millis);
    }

    Sample add(String id) {
      Sample sample = new Sample(id, tasks);
      modules.register(sample);
      sample.count.onChange((setting, before, after) -> config.changed());
      return sample;
    }
  }

  private static final class Sample extends ClientModule {
    final Setting<Integer> count;
    int enables, disables;
    boolean breakCleanup;

    Sample(String id, ClientTaskQueue tasks) {
      super(new ModuleInfo(id, id, "Internal test", ModuleCategory.MISC, false));
      count = setting(Setting.integer("count", "Count", "", 1, 0, 10, tasks::checkThread));
    }

    @Override
    protected void onEnable(ModuleContext context) {
      enables++;
    }

    @Override
    protected void onDisable() {
      disables++;
      if (breakCleanup) throw new IllegalStateException("Broken cleanup");
    }
  }
}
