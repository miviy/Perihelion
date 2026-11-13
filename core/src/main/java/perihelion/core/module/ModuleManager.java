package perihelion.core.module;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import perihelion.core.concurrent.ClientTaskQueue;
import perihelion.core.control.SharedControls;
import perihelion.core.diagnostics.ClientLog;
import perihelion.core.diagnostics.Failures;
import perihelion.core.event.EventBus;
import perihelion.core.event.EventOwner;
import perihelion.core.input.Keybind;
import perihelion.core.setting.Setting;

public final class ModuleManager implements AutoCloseable {
    private final Map<String, ModuleEntry> entries = new LinkedHashMap<>();
    private final Collection<ModuleEntry> view = Collections.unmodifiableCollection(entries.values());
    private final ConcurrentLinkedQueue<Fault> faults = new ConcurrentLinkedQueue<>();
    private final ClientTaskQueue tasks;
    private final EventBus clientEvents, networkEvents;
    private final ClientLog log;
    private final SharedControls controls;
    private Runnable changed = () -> { };
    private Runnable bindingChanged = () -> { };
    private boolean worldReady, running, suspended, closed, frozen;

    public ModuleManager(ClientTaskQueue tasks, EventBus clientEvents, EventBus networkEvents, ClientLog log) {
        this.tasks = tasks;
        this.clientEvents = clientEvents;
        this.networkEvents = networkEvents;
        this.log = log;
        this.controls = new SharedControls(tasks);
    }
    public SharedControls controls() { return controls; }

    /** Receives persisted preference changes, including bindings and fault/self-disable resets. */
    public void onChange(Runnable changed) { tasks.checkThread(); this.changed = Objects.requireNonNull(changed, "changed"); }
    /** Receives actual binding changes before the general change callback. */
    public void onBindingChange(Runnable changed) { tasks.checkThread(); this.bindingChanged = Objects.requireNonNull(changed, "changed"); }
    public ModuleEntry register(ClientModule module) {
        tasks.checkThread();
        if (frozen || closed) throw new IllegalStateException("Module registration is closed");
        String id = module.info().id();
        if (entries.containsKey(id)) throw new IllegalArgumentException("Duplicate module ID: " + id);
        module.freeze();
        ModuleEntry entry = new ModuleEntry(module);
        entries.put(id, entry);
        return entry;
    }
    public Collection<ModuleEntry> entries() { tasks.checkThread(); return view; }
    public ModuleEntry require(String id) {
        tasks.checkThread();
        ModuleEntry entry = entries.get(id);
        if (entry == null) throw new IllegalArgumentException("Unknown module: " + id);
        return entry;
    }
    public void start() {
        tasks.checkThread();
        if (closed) throw new IllegalStateException("Manager stopped");
        if (running) return;
        frozen = true;
        running = true;
        for (ModuleEntry entry : entries.values()) reconcile(entry);
    }
    public void setEnabled(String id, boolean enabled) {
        tasks.checkThread();
        if (closed) throw new IllegalStateException("Manager stopped");
        ModuleEntry entry = require(id);
        if (entry.state == ModuleState.ENABLING || entry.state == ModuleState.DISABLING) {
            throw new IllegalStateException("Reentrant module transition: " + id);
        }
        if (entry.requested == enabled && entry.state != ModuleState.FAULTED) return;
        entry.requested = enabled;
        if (entry.state == ModuleState.FAULTED) { entry.state = ModuleState.DISABLED; entry.failure = null; }
        reconcile(entry);
        changed.run();
    }
    public void toggle(String id) { setEnabled(id, !require(id).requested); }
    public void setBinding(String id, Keybind binding) {
        tasks.checkThread();
        ModuleEntry entry = require(id);
        Objects.requireNonNull(binding, "binding");
        if (!entry.binding.equals(binding)) {
            entry.binding = binding;
            bindingChanged.run();
            changed.run();
        }
    }
    /**
     * Shared client-thread mutation boundary for editors, commands and configuration.
     * Invalid input throws without faulting a module. Owned observer faults are processed
     * after notification; the committed value is retained. Returns false if a raw observer
     * failed: lacking an activation owner, it conservatively quarantines the setting's
     * defining module. Otherwise returns true, including unchanged values.
     */
    public <T> boolean setSetting(String moduleId, Setting<T> setting, T candidate) {
        tasks.checkThread();
        if (closed) throw new IllegalStateException("Manager stopped");
        ModuleEntry entry = require(moduleId);
        Objects.requireNonNull(setting, "setting");
        if (entry.module().settings().get(setting.id()) != setting) {
            throw new IllegalArgumentException("Setting does not belong to module: " + moduleId);
        }
        T validated = setting.validate(candidate);
        T previous = setting.get();
        try {
            setting.set(validated);
            return true;
        } catch (RuntimeException | Error failure) {
            Failures.rethrowFatal(failure);
            // Guard/recursive-write failures happen before commit, not in an observer.
            if (Objects.equals(previous, setting.get())) throw failure;
            quarantine(entry, failure);
            return false;
        } finally {
            processFaults();
        }
    }
    public void setWorldReady(boolean ready) {
        tasks.checkThread();
        if (worldReady == ready) return;
        worldReady = ready;
        processFaults();
        for (ModuleEntry entry : entries.values()) reconcile(entry);
    }
    public void suspend() {
        tasks.checkThread();
        suspended = true;
        processFaults();
        for (ModuleEntry entry : entries.values()) reconcile(entry);
    }
    public void resume() {
        tasks.checkThread();
        suspended = false;
        for (ModuleEntry entry : entries.values()) reconcile(entry);
    }
    public void processFaults() {
        tasks.checkThread();
        Fault fault;
        while ((fault = faults.poll()) != null) {
            if (fault.entry.owner == fault.owner) quarantine(fault.entry, fault.failure);
        }
    }

    /** Fault boundary for owned work performed outside event dispatch, such as setting observers. */
    public void quarantine(String id, Throwable failure) {
        tasks.checkThread();
        if (closed) throw new IllegalStateException("Manager stopped");
        Objects.requireNonNull(failure, "failure");
        Failures.rethrowFatal(failure);
        quarantine(require(id), failure);
    }

    private void reconcile(ModuleEntry entry) {
        // Lifecycle callbacks may change world/suspension state. The current transition
        // completes before those changes can start another transition for this entry.
        if (entry.state == ModuleState.FAULTED || entry.state == ModuleState.ENABLING
                || entry.state == ModuleState.DISABLING) return;
        boolean activate = shouldActivate(entry);
        if (activate && entry.state != ModuleState.ENABLED) enable(entry);
        else if (!activate) {
            if (entry.state == ModuleState.ENABLED) disable(entry);
            if (entry.state != ModuleState.FAULTED) entry.state = entry.requested && entry.info().requiresWorld() && !worldReady
                    ? ModuleState.WAITING_FOR_WORLD : ModuleState.DISABLED;
        }
    }
    private boolean shouldActivate(ModuleEntry entry) {
        return running && !suspended && !closed && entry.requested
                && (!entry.info().requiresWorld() || worldReady);
    }
    private void requestDisable(ModuleEntry entry, EventOwner owner) {
        if (entry.owner != owner || owner.isClosed()) return;
        // ENABLING requests belong to this scope and are consumed only after the hook returns.
        if (entry.state == ModuleState.ENABLED) setEnabled(entry.info().id(), false);
    }
    private void enable(ModuleEntry entry) {
        entry.state = ModuleState.ENABLING;
        EventOwner[] captured = new EventOwner[1];
        EventOwner owner = new EventOwner(entry.info().id(), failure -> faults.add(new Fault(entry, captured[0], failure)));
        captured[0] = owner;
        entry.owner = owner;
        ModuleContext context = new ModuleContext(owner, clientEvents, networkEvents, tasks,
                () -> requestDisable(entry, owner), controls);
        entry.context = context;
        boolean disableRequested = false;
        try {
            entry.module.onEnable(context);
            if (entry.owner != owner) return; // Another fault boundary already retired this scope.
            disableRequested = context.isDisableRequested();
            if (disableRequested) entry.requested = false;
            if (!shouldActivate(entry)) {
                disable(entry);
                reconcile(entry);
            } else {
                owner.activate();
                entry.state = ModuleState.ENABLED;
            }
        } catch (Throwable failure) {
            Failures.rethrowFatal(failure);
            quarantine(entry, failure);
        }
        // Also persist self-disable when activation came from start, world readiness or resume.
        if (disableRequested && entry.state != ModuleState.FAULTED) changed.run();
    }
    private void disable(ModuleEntry entry) {
        Throwable failure = cleanup(entry, true, null);
        if (failure == null) entry.state = ModuleState.DISABLED;
        else {
            entry.failure = failure;
            entry.requested = false;
            entry.state = ModuleState.FAULTED;
            log.error("Module cleanup failed: " + entry.info().id(), failure);
            changed.run();
        }
    }
    private void quarantine(ModuleEntry entry, Throwable failure) {
        if (entry.state == ModuleState.DISABLING && entry.context != null) {
            entry.context.recordCleanupFailure(failure);
            return; // Keep the transition guarded until every cleanup has been attempted.
        }
        failure = cleanup(entry, entry.state == ModuleState.ENABLED, failure);
        entry.failure = failure;
        entry.requested = false;
        entry.state = ModuleState.FAULTED;
        log.error("Module quarantined: " + entry.info().id(), failure);
        changed.run();
    }
    private Throwable cleanup(ModuleEntry entry, boolean invokeDisable, Throwable failure) {
        entry.state = ModuleState.DISABLING;
        EventOwner owner = entry.owner;
        ModuleContext context = entry.context;
        entry.owner = null;
        if (owner != null) owner.close();
        if (context != null) context.recordCleanupFailure(failure);
        try { if (invokeDisable) entry.module.onDisable(); }
        catch (Throwable cleanup) {
            Failures.rethrowFatal(cleanup);
            if (failure == null) failure = cleanup;
            else if (cleanup != failure) failure.addSuppressed(cleanup);
        } finally {
            try { if (context != null) failure = context.cleanup(failure); }
            finally { entry.context = null; }
        }
        return failure;
    }
    @Override public void close() {
        tasks.checkThread();
        if (closed) return;
        closed = true;
        running = false;
        processFaults();
        for (ModuleEntry entry : entries.values()) {
            if (entry.state == ModuleState.ENABLED) disable(entry);
        }
        faults.clear();
        controls.close();
    }
    private static final class Fault {
        final ModuleEntry entry;
        final EventOwner owner;
        final Throwable failure;
        Fault(ModuleEntry entry, EventOwner owner, Throwable failure) { this.entry = entry; this.owner = owner; this.failure = failure; }
    }
}
