package perihelion.core.event;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import perihelion.core.diagnostics.ClientLog;
import perihelion.core.diagnostics.Failures;

/** Exact-type dispatch. Only registration rebuilds arrays; callbacks never hold a bus lock. */
public final class EventBus implements AutoCloseable {
    private final ConcurrentHashMap<Class<?>, Entry<?>[]> listeners = new ConcurrentHashMap<>();
    private final Runnable threadGuard;
    private final ClientLog log;
    private final LongAdder measuredDispatches = new LongAdder();
    private final LongAdder measuredNanos = new LongAdder();
    private volatile boolean timing;
    private volatile boolean closed;
    private long sequence;

    public EventBus(Runnable threadGuard, ClientLog log) {
        this.threadGuard = Objects.requireNonNull(threadGuard, "threadGuard");
        this.log = Objects.requireNonNull(log, "log");
    }

    public <E> Subscription subscribe(EventOwner owner, Class<E> type, EventListener<E> listener) {
        return subscribe(owner, type, 0, false, listener);
    }

    public <E> Subscription subscribe(EventOwner owner, Class<E> type, int priority,
                                      boolean receiveCancelled, EventListener<E> listener) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(listener, "listener");
        Entry<E> entry;
        synchronized (this) {
            if (closed || owner.isClosed()) throw new IllegalStateException("Subscription scope is closed");
            entry = new Entry<>(owner, type, listener, priority, receiveCancelled, sequence++);
            Entry<?>[] previous = listeners.get(type);
            Entry<?>[] next = previous == null ? new Entry<?>[1] : Arrays.copyOf(previous, previous.length + 1);
            next[next.length - 1] = entry;
            Arrays.sort(next, (a, b) -> {
                int order = Integer.compare(b.priority, a.priority);
                return order != 0 ? order : Long.compare(a.sequence, b.sequence);
            });
            listeners.put(type, next);
        }
        owner.attach(entry);
        // A concurrent bus close may have detached this entry before it was attached.
        if (!entry.enabled) owner.detach(entry);
        return entry;
    }

    public boolean hasListeners(Class<?> type) { return listeners.containsKey(type); }

    public <E> E post(E event) {
        threadGuard.run();
        Objects.requireNonNull(event, "event");
        Entry<?>[] entries = listeners.get(event.getClass());
        if (entries == null) return event;
        boolean measure = timing;
        long start = measure ? System.nanoTime() : 0;
        try {
            CancellableEvent cancellable = event instanceof CancellableEvent ? (CancellableEvent) event : null;
            for (Entry<?> entry : entries) {
                if (!entry.enabled || !entry.owner.isActive()) continue;
                boolean previous = cancellable != null && cancellable.isCancelled();
                if (previous && !entry.receiveCancelled) continue;
                try {
                    entry.invoke(event);
                } catch (Throwable failure) {
                    Failures.rethrowFatal(failure);
                    if (cancellable != null) cancellable.restoreCancellation(previous);
                    try {
                        entry.owner.fail(failure);
                    } catch (Throwable cleanup) {
                        Failures.rethrowFatal(cleanup);
                        if (cleanup != failure) failure.addSuppressed(cleanup);
                    }
                    log.error("Listener failed: owner=" + entry.owner.id() + " event="
                            + event.getClass().getName() + " thread=" + Thread.currentThread().getName(), failure);
                }
            }
        } finally {
            if (measure) {
                measuredDispatches.increment();
                measuredNanos.add(System.nanoTime() - start);
            }
        }
        return event;
    }

    public void setTimingEnabled(boolean enabled) { timing = enabled; }
    public boolean isTimingEnabled() { return timing; }
    public long measuredDispatches() { return measuredDispatches.sum(); }
    public long measuredNanos() { return measuredNanos.sum(); }
    public int subscriptionCount() {
        int count = 0;
        for (Entry<?>[] entries : listeners.values()) count += entries.length;
        return count;
    }

    @Override public synchronized void close() {
        closed = true;
        for (Entry<?>[] entries : listeners.values()) {
            for (Entry<?> entry : entries) {
                entry.enabled = false;
                entry.owner.detach(entry);
            }
        }
        listeners.clear();
    }

    private final class Entry<E> implements Subscription {
        final EventOwner owner;
        final Class<E> type;
        final EventListener<E> listener;
        final int priority;
        final boolean receiveCancelled;
        final long sequence;
        volatile boolean enabled = true;

        Entry(EventOwner owner, Class<E> type, EventListener<E> listener, int priority,
              boolean receiveCancelled, long sequence) {
            this.owner = owner;
            this.type = type;
            this.listener = listener;
            this.priority = priority;
            this.receiveCancelled = receiveCancelled;
            this.sequence = sequence;
        }

        void invoke(Object event) throws Exception { listener.onEvent(type.cast(event)); }

        @Override public void close() {
            enabled = false;
            synchronized (EventBus.this) {
                Entry<?>[] old = listeners.get(type);
                if (old != null) {
                    int index = -1;
                    for (int i = 0; i < old.length; i++) if (old[i] == this) { index = i; break; }
                    if (index >= 0) {
                        if (old.length == 1) listeners.remove(type);
                        else {
                            Entry<?>[] next = new Entry<?>[old.length - 1];
                            System.arraycopy(old, 0, next, 0, index);
                            System.arraycopy(old, index + 1, next, index, old.length - index - 1);
                            listeners.put(type, next);
                        }
                    }
                }
            }
            owner.detach(this);
        }
    }
}
