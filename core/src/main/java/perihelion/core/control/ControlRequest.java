package perihelion.core.control;

import java.util.Optional;
import perihelion.core.event.EventOwner;

/** Reusable activation-owned handle. All operations require the client thread. */
public final class ControlRequest<T> implements AutoCloseable {
    final SharedControls.Channel<T> channel;
    final EventOwner owner;
    final int priority;
    T value;
    long generation, expiresAt;
    boolean closed;

    ControlRequest(SharedControls.Channel<T> channel, EventOwner owner, int priority) {
        this.channel = channel; this.owner = owner; this.priority = priority;
    }
    /** Holds a value until cleared/closed or the world generation changes. False means retired. */
    public boolean set(T value) { return submit(value, Long.MAX_VALUE); }
    /** Holds a value for this client tick and the next ticks-1 ticks. Renew to keep it alive. */
    public boolean setForTicks(T value, int ticks) {
        channel.hub.tasks.checkThread();
        if (ticks < 1) throw new IllegalArgumentException("Request duration must be positive");
        return submit(value, channel.hub.tick + ticks);
    }
    private boolean submit(T next, long expiresAt) {
        channel.hub.tasks.checkThread();
        if (closed || owner.isClosed() || channel.hub.closed) return false;
        channel.validate.accept(next);
        this.value = next;
        this.generation = channel.hub.tasks.generation();
        this.expiresAt = expiresAt;
        return true;
    }
    public void clear() { channel.hub.tasks.checkThread(); value = null; }
    public boolean isWinning() { channel.hub.tasks.checkThread(); return channel.winner() == this; }
    /** Current eligible value, even if a higher-priority request wins. */
    public Optional<T> value() { channel.hub.tasks.checkThread(); return eligible() ? Optional.of(value) : Optional.empty(); }
    public String ownerId() { return owner.id(); }
    public int priority() { return priority; }
    boolean eligible() {
        return !closed && !channel.hub.closed && owner.isActive() && value != null
                && generation == channel.hub.tasks.generation() && channel.hub.tick < expiresAt;
    }
    @Override public void close() {
        channel.hub.tasks.checkThread();
        if (closed) return;
        closed = true; value = null;
        channel.requests.remove(this);
    }
}
