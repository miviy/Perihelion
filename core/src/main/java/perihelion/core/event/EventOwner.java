package perihelion.core.event;

import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** A single activation's subscription scope. Closed owners cannot be reused. */
public final class EventOwner implements AutoCloseable {
    private final String id;
    private final Consumer<Throwable> failureHandler;
    private final ConcurrentLinkedQueue<Subscription> subscriptions = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile boolean active;

    public EventOwner(String id, Consumer<Throwable> failureHandler) {
        this.id = Objects.requireNonNull(id, "id");
        this.failureHandler = Objects.requireNonNull(failureHandler, "failureHandler");
    }

    public String id() { return id; }
    public boolean isActive() { return active && !closed.get(); }
    public boolean isClosed() { return closed.get(); }

    public void activate() {
        if (closed.get()) throw new IllegalStateException("Owner is closed: " + id);
        active = true;
    }

    void attach(Subscription subscription) {
        subscriptions.add(subscription);
        if (closed.get()) {
            subscription.close();
            subscriptions.remove(subscription);
        }
    }

    void detach(Subscription subscription) { subscriptions.remove(subscription); }

    /** Quarantine this activation, including failures in its deferred client work. */
    public boolean fail(Throwable failure) {
        if (!closed.compareAndSet(false, true)) return false;
        active = false;
        detachAll();
        failureHandler.accept(failure);
        return true;
    }

    @Override public void close() {
        closed.set(true);
        active = false;
        detachAll();
    }

    private void detachAll() {
        Subscription subscription;
        while ((subscription = subscriptions.poll()) != null) subscription.close();
    }
}
