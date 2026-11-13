package perihelion.core.module;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import perihelion.core.concurrent.ClientTaskQueue;
import perihelion.core.control.SharedControls;
import perihelion.core.diagnostics.Failures;
import perihelion.core.event.EventBus;
import perihelion.core.event.EventListener;
import perihelion.core.event.EventOwner;
import perihelion.core.event.Subscription;
import perihelion.core.setting.ChangeListener;
import perihelion.core.setting.ChangeSubscription;
import perihelion.core.setting.Setting;

/** One activation's scope. Network listeners explicitly opt into concurrent callbacks. */
public final class ModuleContext {
    private final EventOwner owner;
    private final EventBus clientEvents, networkEvents;
    private final ClientTaskQueue tasks;
    private final Runnable disableRequest;
    private final SharedControls.Scope controls;
    private final Deque<AutoCloseable> resources = new ArrayDeque<>();
    private boolean cleaned, disableRequested;
    private Throwable cleanupFailure;
    ModuleContext(EventOwner owner, EventBus clientEvents, EventBus networkEvents, ClientTaskQueue tasks,
                  Runnable disableRequest, SharedControls controls) {
        this.owner = owner;
        this.clientEvents = clientEvents;
        this.networkEvents = networkEvents;
        this.tasks = tasks;
        this.disableRequest = disableRequest;
        this.controls = controls.scope(owner, resource -> own(resource));
    }
    /** Requests created here are automatically owned by this activation. */
    public SharedControls.Scope controls() { return controls; }
    /**
     * Requests disable on the client thread. During onEnable, the manager waits for the
     * hook to return successfully before disabling, without activating listeners. An
     * already-enabled activation disables immediately. Repeated requests and requests
     * from retired contexts do nothing. Network callbacks must defer this call.
     */
    public void requestDisable() {
        tasks.checkThread();
        if (disableRequested || owner.isClosed()) return;
        disableRequested = true;
        disableRequest.run();
    }
    boolean isDisableRequested() { return disableRequested; }
    /**
     * Transfers a resource to this activation and returns it for use. Register on the client
     * thread, immediately after acquisition. A retired activation closes new resources immediately.
     * Each registration is closed once; do not also close the resource manually.
     */
    public <T extends AutoCloseable> T own(T resource) {
        tasks.checkThread();
        Objects.requireNonNull(resource, "resource");
        if (cleaned || owner.isClosed()) {
            try { resource.close(); }
            catch (Throwable failure) {
                Failures.rethrowFatal(failure);
                throw new IllegalStateException("Late activation cleanup failed: " + owner.id(), failure);
            }
        } else resources.addFirst(resource);
        return resource;
    }
    /** Registers client-thread cleanup, or runs it immediately if this activation has ended. */
    public void onCleanup(Runnable cleanup) {
        Objects.requireNonNull(cleanup, "cleanup");
        own(cleanup::run);
    }
    /** Called after the owner is closed and onDisable (when applicable) has been attempted. */
    Throwable cleanup(Throwable failure) {
        tasks.checkThread();
        recordCleanupFailure(failure);
        if (cleaned) return cleanupFailure;
        cleaned = true;
        AutoCloseable resource;
        while ((resource = resources.pollFirst()) != null) {
            try { resource.close(); }
            catch (Throwable cleanup) { recordCleanupFailure(cleanup); }
        }
        return cleanupFailure;
    }
    void recordCleanupFailure(Throwable failure) {
        if (failure == null) return;
        Failures.rethrowFatal(failure);
        if (cleanupFailure == null) cleanupFailure = failure;
        else if (failure != cleanupFailure) cleanupFailure.addSuppressed(failure);
    }
    /**
     * Observes any setting for this activation, in normal registration order. Like event
     * listeners, callbacks run only after enable succeeds and stop as soon as the owner
     * retires. Ordinary failures fault this observer's module, even for another module's
     * setting. Removal uses the existing client-thread cleanup stack.
     */
    public <T> ChangeSubscription observe(Setting<T> setting, ChangeListener<T> listener) {
        tasks.checkThread();
        Objects.requireNonNull(setting, "setting");
        Objects.requireNonNull(listener, "listener");
        return own(setting.onChange((source, before, after) ->
                runOwned(() -> listener.changed(source, before, after))));
    }
    public <E> Subscription listen(Class<E> type, EventListener<E> listener) {
        return clientEvents.subscribe(owner, type, listener);
    }
    public <E> Subscription listen(Class<E> type, int priority, boolean receiveCancelled, EventListener<E> listener) {
        return clientEvents.subscribe(owner, type, priority, receiveCancelled, listener);
    }
    public <E> Subscription listenNetwork(Class<E> type, int priority, boolean receiveCancelled, EventListener<E> listener) {
        return networkEvents.subscribe(owner, type, priority, receiveCancelled, listener);
    }
    public boolean defer(Runnable action) {
        return tasks.execute(() -> runOwned(action));
    }
    public boolean deferForWorld(Runnable action) {
        return tasks.executeForGeneration(tasks.generation(), () -> runOwned(action));
    }
    private void runOwned(Runnable action) {
        if (!owner.isActive()) return;
        try { action.run(); }
        catch (Throwable failure) {
            Failures.rethrowFatal(failure);
            owner.fail(failure);
        }
    }
}
