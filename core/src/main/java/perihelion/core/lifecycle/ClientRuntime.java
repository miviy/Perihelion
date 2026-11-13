package perihelion.core.lifecycle;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import perihelion.core.command.CommandDispatcher;
import perihelion.core.concurrent.ClientTaskQueue;
import perihelion.core.config.ConfigStore;
import perihelion.core.context.ConnectionContext;
import perihelion.core.control.SharedControls;
import perihelion.core.diagnostics.ClientLog;
import perihelion.core.diagnostics.Failures;
import perihelion.core.event.ClientTickEvent;
import perihelion.core.event.EventBus;
import perihelion.core.event.WorldContextEvent;
import perihelion.core.input.InputManager;
import perihelion.core.module.ModuleEntry;
import perihelion.core.module.ModuleManager;
import perihelion.core.setting.Setting;

/** Explicit composition root. Game classes and loader annotations never enter the core. */
public final class ClientRuntime implements AutoCloseable {
    private final ClientLog log;
    private final ClientTaskQueue tasks;
    private final EventBus clientEvents, networkEvents;
    private final ModuleManager modules;
    private final InputManager input;
    private final ConfigController config;
    private final CommandDispatcher commands;
    private final Consumer<ClientRuntime> definitions;
    private final List<AutoCloseable> observers = new ArrayList<>();
    private final Deque<AutoCloseable> resources = new ArrayDeque<>();
    private volatile LifecycleState state = LifecycleState.NEW;
    private ConnectionContext connection = ConnectionContext.DISCONNECTED;
    private boolean worldReady;
    private boolean shuttingDown;
    private volatile Throwable failure;

    public ClientRuntime(Path configFile, ClientLog log, Consumer<ClientRuntime> definitions) {
        this.log = log;
        this.definitions = definitions;
        tasks = new ClientTaskQueue(Thread.currentThread(), log);
        clientEvents = new EventBus(tasks::checkThread, log);
        networkEvents = new EventBus(() -> { }, log);
        modules = new ModuleManager(tasks, clientEvents, networkEvents, log);
        input = new InputManager(tasks, modules, clientEvents);
        config = new ConfigController(new ConfigStore(configFile, log), modules, tasks, log);
        commands = new CommandDispatcher(this, log);
        modules.onBindingChange(input::rebuildBindings);
        modules.onChange(() -> {
            if (state == LifecycleState.RUNNING) config.changed();
        });
    }
    public void start() {
        tasks.checkThread();
        if (state == LifecycleState.RUNNING || state == LifecycleState.STARTING) return;
        if (state != LifecycleState.NEW) throw new IllegalStateException("Runtime cannot restart after " + state);
        state = LifecycleState.STARTING;
        try {
            definitions.accept(this);
            if (state != LifecycleState.STARTING) return;
            config.load();
            if (state != LifecycleState.STARTING) return;
            for (ModuleEntry entry : modules.entries()) {
                for (Setting<?> setting : entry.module().settings().values()) {
                    observers.add(setting.onChange((changed, oldValue, newValue) -> {
                        if (state == LifecycleState.RUNNING) config.changed();
                    }));
                }
            }
            input.rebuildBindings();
            modules.start();
            if (state != LifecycleState.STARTING) return;
            state = LifecycleState.RUNNING;
            config.changed();
            log.info("Perihelion started: modules=" + modules.entries().size());
        } catch (Throwable startupFailure) {
            Failures.rethrowFatal(startupFailure);
            failure = startupFailure;
            state = LifecycleState.FAILED;
            cleanup(false);
            log.error("Perihelion initialization failed; runtime detached", startupFailure);
        }
    }
    public void tickStart() {
        tasks.checkThread();
        if (!isRunning()) return;
        controls().beginTick();
        modules.processFaults();
        tasks.drain();
        if (isRunning() && clientEvents.hasListeners(ClientTickEvent.class)) clientEvents.post(ClientTickEvent.START);
    }
    public void tickEnd() {
        tasks.checkThread();
        if (isRunning() && clientEvents.hasListeners(ClientTickEvent.class)) clientEvents.post(ClientTickEvent.END);
        if (isRunning()) config.tick();
    }
    public void updateWorld(boolean ready, ConnectionContext next, boolean identityChanged) {
        tasks.checkThread();
        if (!isRunning()) return;
        boolean changed = identityChanged || worldReady != ready || connection.kind() != next.kind()
                || !connection.address().equals(next.address());
        if (!changed) return;
        tasks.advanceGeneration();
        modules.setWorldReady(false);
        worldReady = ready;
        connection = next;
        if (clientEvents.hasListeners(WorldContextEvent.class)) clientEvents.post(new WorldContextEvent(tasks.generation(), ready, next));
        modules.setWorldReady(ready);
    }
    @Override public void close() { stop(); }
    public void stop() {
        tasks.checkThread();
        if (shuttingDown || state == LifecycleState.STOPPED || state == LifecycleState.FAILED) return;
        shuttingDown = true;
        state = LifecycleState.STOPPING;
        cleanup(true);
        state = LifecycleState.STOPPED;
        log.info("Perihelion stopped; subscriptions=" + (clientEvents.subscriptionCount() + networkEvents.subscriptionCount()));
    }
    private void cleanup(boolean save) {
        safe("modules", modules::close);
        AutoCloseable resource;
        while ((resource = resources.pollFirst()) != null) safe("runtime resource", resource::close);
        safe("input", input::reset);
        safe("client events", clientEvents::close);
        safe("network events", networkEvents::close);
        for (AutoCloseable observer : observers) safe("setting observer", observer::close);
        observers.clear();
        if (save) safe("configuration", () -> { if (!config.close()) log.warn("Configuration flush did not finish within two seconds"); });
        else safe("configuration", () -> config.store().close(2, java.util.concurrent.TimeUnit.SECONDS));
        connection = ConnectionContext.DISCONNECTED;
        worldReady = false;
        safe("task queue", tasks::close);
    }
    private void safe(String component, Cleanup cleanup) {
        try { cleanup.run(); }
        catch (Throwable error) { Failures.rethrowFatal(error); log.error("Cleanup failed: " + component, error); }
    }
    @FunctionalInterface private interface Cleanup { void run() throws Exception; }
    /** Owns an adapter resource for startup failure and shutdown, after module cleanup. */
    public <T extends AutoCloseable> T own(T resource) {
        tasks.checkThread();
        Objects.requireNonNull(resource, "resource");
        if (state == LifecycleState.STOPPING || state == LifecycleState.STOPPED || state == LifecycleState.FAILED) {
            safe("late runtime resource", resource::close);
        } else resources.addFirst(resource);
        return resource;
    }
    public LifecycleState state() { return state; }
    public boolean isRunning() { return state == LifecycleState.RUNNING; }
    public Throwable lastFailure() { return failure; }
    public ClientTaskQueue tasks() { return tasks; }
    public EventBus clientEvents() { return clientEvents; }
    public EventBus networkEvents() { return networkEvents; }
    public ModuleManager modules() { return modules; }
    public SharedControls controls() { return modules.controls(); }
    public InputManager input() { return input; }
    public ConfigController config() { return config; }
    public CommandDispatcher commands() { return commands; }
    public ConnectionContext connection() { tasks.checkThread(); return connection; }
    public boolean worldReady() { tasks.checkThread(); return worldReady; }
}
