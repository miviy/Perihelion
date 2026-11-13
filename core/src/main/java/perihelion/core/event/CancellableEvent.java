package perihelion.core.event;

/** Cancellation is monotonic for listeners. Only the bus can undo a failed callback. */
public abstract class CancellableEvent {
    private boolean cancelled;

    public final boolean isCancelled() { return cancelled; }
    public final void cancel() { cancelled = true; }
    final void restoreCancellation(boolean previous) { cancelled = previous; }
}
