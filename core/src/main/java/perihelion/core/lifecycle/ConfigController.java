package perihelion.core.lifecycle;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import perihelion.core.concurrent.ClientTaskQueue;
import perihelion.core.config.ConfigStore;
import perihelion.core.config.LoadResult;
import perihelion.core.diagnostics.ClientLog;
import perihelion.core.diagnostics.Failures;
import perihelion.core.input.Keybind;
import perihelion.core.module.ModuleEntry;
import perihelion.core.module.ModuleManager;
import perihelion.core.module.ModuleState;
import perihelion.core.setting.Setting;

/** Binds the durable JSON document to registered definitions on the client thread. */
public final class ConfigController {
  private final ConfigStore store;
  private final ModuleManager modules;
  private final ClientTaskQueue tasks;
  private final ClientLog log;
  private final LongSupplier nanoTime;
  private JsonObject preserved = new JsonObject();
  private boolean applying, dirty;
  private volatile boolean reloading, closed;
  private volatile CompletableFuture<Void> pendingReload;
  private long revision;
  private long lastChangeNanos;

  public ConfigController(
      ConfigStore store, ModuleManager modules, ClientTaskQueue tasks, ClientLog log) {
    this(store, modules, tasks, log, System::nanoTime);
  }

  ConfigController(
      ConfigStore store,
      ModuleManager modules,
      ClientTaskQueue tasks,
      ClientLog log,
      LongSupplier nanoTime) {
    this.store = store;
    this.modules = modules;
    this.tasks = tasks;
    this.log = log;
    this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
  }

  public ConfigStore store() {
    return store;
  }

  public boolean isReloading() {
    return reloading;
  }

  /** Includes live changes not yet captured as well as queued or failed disk writes. */
  public boolean isDirty() {
    tasks.checkThread();
    return dirty || store.isDirty();
  }

  public void load() {
    tasks.checkThread();
    LoadResult loaded = store.load();
    dirty = false;
    apply(loaded);
  }

  public void changed() {
    tasks.checkThread();
    if (applying || closed) return;
    revision++;
    if (store.isReadOnly()) return;
    dirty = true;
    lastChangeNanos = nanoTime.getAsLong();
  }

  /** Called at client tick END, after setting/module callbacks have finished. */
  public void tick() {
    tasks.checkThread();
    if (!dirty || applying || closed || reloading || store.isReadOnly()) return;
    if (nanoTime.getAsLong() - lastChangeNanos
        < TimeUnit.MILLISECONDS.toNanos(ConfigStore.SAVE_DEBOUNCE_MILLIS)) return;
    try {
      saveNow();
    } catch (Throwable failure) {
      Failures.rethrowFatal(failure);
      // Keep uncaptured edits pending without retrying on every tick.
      lastChangeNanos = nanoTime.getAsLong();
      log.error("Could not capture configuration for saving", failure);
    }
  }

  public CompletableFuture<Void> saveNow() {
    tasks.checkThread();
    if (closed) throw new IllegalStateException("Configuration stopped");
    CompletableFuture<Void> result = store.saveNow(snapshot());
    dirty = false; // The store now owns the immutable snapshot and any write failure.
    return result;
  }

  public CompletableFuture<Void> saveProfile(String name, boolean update) {
    tasks.checkThread();
    if (closed || reloading)
      throw new IllegalStateException("Wait for the current config operation");
    return store.writeProfile(name, snapshot(), update);
  }

  public CompletableFuture<Void> loadProfile(String name) {
    return reload(name);
  }

  /** Selective preset load; omitted settings and bindings retain their live values. */
  public CompletableFuture<Void> loadProfile(
      String name, boolean loadKeybinds, Predicate<ModuleEntry> loadSettings) {
    Objects.requireNonNull(name, "name");
    return reload(name, loadKeybinds, Objects.requireNonNull(loadSettings, "loadSettings"));
  }

  public CompletableFuture<Void> reload() {
    return reload(null);
  }

  private CompletableFuture<Void> reload(String profile) {
    return reload(profile, true, entry -> true);
  }

  private CompletableFuture<Void> reload(
      String profile, boolean loadKeybinds, Predicate<ModuleEntry> loadSettings) {
    tasks.checkThread();
    if (closed || reloading)
      throw new IllegalStateException("Configuration is stopped or already reloading");
    if (profile != null) {
      if (store.isReadOnly()) throw new IllegalStateException("Current configuration is read-only");
      store.profilePath(profile); // Reject invalid names before changing lifecycle state.
    }
    reloading = true;
    long expectedRevision = revision;
    CompletableFuture<Void> result = new CompletableFuture<>();
    pendingReload = result;
    try {
      CompletableFuture<LoadResult> read =
          profile == null ? store.reload() : store.readProfile(profile);
      if (profile == null) dirty = false; // Explicit reload discards uncaptured edits.
      read.whenComplete(
          (loaded, failure) -> {
            if (result.isDone()) return;
            try {
              boolean accepted =
                  tasks.execute(
                      () -> {
                        if (result.isDone()) return;
                        if (failure != null) {
                          finishReload(result, failure);
                          return;
                        }
                        if (closed || revision != expectedRevision) {
                          finishReload(
                              result,
                              new IllegalStateException(
                                  "Configuration changed during load; retry"));
                          return;
                        }
                        try {
                          apply(loaded, loadKeybinds, loadSettings);
                          if (profile == null) finishReload(result, null);
                          else {
                            // Persist the loaded working setup, without overwriting the shared
                            // preset.
                            changed();
                            saveNow()
                                .whenComplete(
                                    (ignored, saveFailure) -> finishReload(result, saveFailure));
                          }
                        } catch (Throwable applyFailure) {
                          Failures.rethrowFatal(applyFailure);
                          finishReload(result, applyFailure);
                        }
                      });
              if (!accepted)
                finishReload(result, new IllegalStateException("Client task queue unavailable"));
            } catch (Throwable handoffFailure) {
              Failures.rethrowFatal(handoffFailure);
              finishReload(result, handoffFailure);
            }
          });
    } catch (Throwable readFailure) {
      Failures.rethrowFatal(readFailure);
      finishReload(result, readFailure);
    }
    return result;
  }

  private void finishReload(CompletableFuture<Void> result, Throwable failure) {
    if (pendingReload == result) {
      pendingReload = null;
      reloading = false;
    }
    if (failure == null) result.complete(null);
    else result.completeExceptionally(failure);
  }

  private void apply(LoadResult loaded) {
    apply(loaded, true, entry -> true);
  }

  private void apply(LoadResult loaded, boolean loadKeybinds, Predicate<ModuleEntry> loadSettings) {
    tasks.checkThread();
    JsonObject document = loaded.json();
    if (loaded.readOnly()) {
      log.warn("Configuration is read-only; preserving file and current values");
      return;
    }
    JsonObject moduleValues = object(document.get("modules"));
    Map<ModuleEntry, Values> validated = new LinkedHashMap<>();
    for (ModuleEntry entry : modules.entries()) {
      JsonObject source = object(moduleValues.get(entry.info().id()));
      Values values = new Values();
      values.binding = entry.module().defaultBinding();
      values.previousFailure = entry.lastFailure();
      JsonElement enabled = source.get("enabled");
      if (enabled != null && enabled.isJsonPrimitive() && enabled.getAsJsonPrimitive().isBoolean())
        values.enabled = enabled.getAsBoolean();
      else if (enabled != null)
        log.warn("Invalid enabled state for " + entry.info().id() + "; using false");
      JsonElement binding = source.get("binding");
      try {
        if (binding != null) {
          JsonObject key = binding.getAsJsonObject();
          JsonElement code = key.get("code");
          if (code == null || !code.isJsonPrimitive() || !code.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Missing code");
          int intCode = code.getAsBigDecimal().intValueExact();
          values.binding =
              new Keybind(Keybind.Device.valueOf(key.get("device").getAsString()), intCode);
        }
      } catch (RuntimeException invalid) {
        values.binding = Keybind.UNBOUND;
        log.warn("Invalid binding for " + entry.info().id() + "; using none");
      }
      JsonObject settingValues = object(source.get("settings"));
      for (Setting<?> setting : entry.module().settings().values()) {
        Object value = setting.defaultValue();
        JsonElement encoded = settingValues.get(setting.id());
        if (encoded != null) {
          try {
            value = setting.fromJson(encoded);
          } catch (IllegalArgumentException invalid) {
            log.warn(
                "Invalid setting " + entry.info().id() + "." + setting.id() + "; using default");
          }
        }
        values.settings.put(setting, value);
      }
      if (!loadKeybinds) values.binding = entry.binding();
      if (!loadSettings.test(entry)) {
        values.enabled = entry.requestedEnabled();
        for (Setting<?> setting : entry.module().settings().values())
          values.settings.put(setting, setting.get());
      }
      validated.put(entry, values);
    }
    applying = true;
    try {
      modules.suspend();
      for (Map.Entry<ModuleEntry, Values> item : validated.entrySet()) {
        ModuleEntry entry = item.getKey();
        Values value = item.getValue();
        // A cleanup failure during suspend must not be cleared by this reload.
        if (entry.state() == ModuleState.FAULTED && entry.lastFailure() != value.previousFailure)
          continue;
        try {
          modules.setEnabled(entry.info().id(), false);
          boolean settingsApplied = true;
          for (Map.Entry<Setting<?>, Object> setting : value.settings.entrySet()) {
            if (!applySetting(entry.info().id(), setting.getKey(), setting.getValue())) {
              settingsApplied = false;
              break;
            }
          }
          if (!settingsApplied) continue;
          modules.setBinding(entry.info().id(), value.binding);
          modules.setEnabled(entry.info().id(), value.enabled);
        } catch (Throwable failure) {
          Failures.rethrowFatal(failure);
          modules.quarantine(entry.info().id(), failure);
          log.error(
              "Configuration application failed for module "
                  + entry.info().id()
                  + "; module quarantined",
              failure);
        }
      }
      preserved = document;
    } finally {
      try {
        modules.resume();
      } finally {
        applying = false;
      }
    }
  }

  @SuppressWarnings("unchecked")
  private <T> boolean applySetting(String moduleId, Setting<T> setting, Object value) {
    return modules.setSetting(moduleId, setting, (T) value);
  }

  private static JsonObject object(JsonElement value) {
    return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
  }

  public JsonObject snapshot() {
    tasks.checkThread();
    JsonObject root = new JsonParser().parse(preserved.toString()).getAsJsonObject();
    root.addProperty("schemaVersion", 1);
    JsonObject moduleValues = object(root.get("modules"));
    root.add("modules", moduleValues);
    for (ModuleEntry entry : modules.entries()) {
      JsonObject encoded = object(moduleValues.get(entry.info().id()));
      encoded.addProperty("enabled", entry.requestedEnabled());
      JsonObject key = object(encoded.get("binding"));
      key.addProperty("device", entry.binding().device().name());
      key.addProperty("code", entry.binding().code());
      encoded.add("binding", key);
      JsonObject settings = object(encoded.get("settings"));
      for (Setting<?> setting : entry.module().settings().values())
        settings.add(setting.id(), setting.toJson());
      encoded.add("settings", settings);
      moduleValues.add(entry.info().id(), encoded);
    }
    return root;
  }

  public boolean close() {
    tasks.checkThread();
    if (closed) return store.close(2, TimeUnit.SECONDS);
    closed = true;
    CompletableFuture<Void> pending = pendingReload;
    if (pending != null)
      finishReload(pending, new IllegalStateException("Configuration stopped during reload"));
    boolean snapshotSaved = true;
    boolean writerStopped;
    try {
      if (!store.isReadOnly()) {
        // Always capture final values, including changes made by module shutdown hooks.
        store.save(snapshot());
        dirty = false;
      }
    } catch (Throwable failure) {
      Failures.rethrowFatal(failure);
      snapshotSaved = false;
      log.error("Could not capture configuration during shutdown", failure);
    } finally {
      writerStopped = store.close(2, TimeUnit.SECONDS);
    }
    return snapshotSaved && writerStopped;
  }

  private static final class Values {
    boolean enabled;
    Throwable previousFailure;
    Keybind binding = Keybind.UNBOUND;
    final Map<Setting<?>, Object> settings = new LinkedHashMap<>();
  }
}
