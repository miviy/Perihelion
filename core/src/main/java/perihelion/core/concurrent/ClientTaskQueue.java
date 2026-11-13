package perihelion.core.concurrent;

import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import perihelion.core.diagnostics.ClientLog;
import perihelion.core.diagnostics.Failures;

/** Bounded handoff only; this does not create another execution thread. */
public final class ClientTaskQueue implements AutoCloseable {
    public static final int CAPACITY = 1024;
    public static final int PER_TICK = 128;
    private final Thread clientThread;
    private final ClientLog log;
    private final ConcurrentLinkedQueue<Task> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pending = new AtomicInteger();
    private volatile long generation;
    private volatile boolean closed;
    private long lastWarning;

    public ClientTaskQueue(Thread clientThread, ClientLog log) {
        this.clientThread = Objects.requireNonNull(clientThread, "clientThread");
        this.log = Objects.requireNonNull(log, "log");
    }

    public boolean isClientThread() { return Thread.currentThread() == clientThread; }
    public void checkThread() {
        if (!isClientThread()) throw new IllegalStateException("Client-thread operation from " + Thread.currentThread().getName());
    }
    public long generation() { return generation; }
    public int pendingCount() { return pending.get(); }
    public boolean isClosed() { return closed; }
    public void advanceGeneration() { checkThread(); generation++; }
    public boolean execute(Runnable action) { return offer(-1, action); }
    public boolean executeForGeneration(long expected, Runnable action) {
        if (expected < 0) throw new IllegalArgumentException("Invalid generation");
        return offer(expected, action);
    }

    private synchronized boolean offer(long expected, Runnable action) {
        Objects.requireNonNull(action, "action");
        if (closed || pending.get() >= CAPACITY) {
            long now = System.nanoTime();
            if (lastWarning == 0 || now - lastWarning >= 1_000_000_000L) {
                lastWarning = now;
                log.warn("Client task rejected: " + (closed ? "runtime stopped" : "queue full"));
            }
            return false;
        }
        pending.incrementAndGet();
        queue.add(new Task(expected, action));
        return true;
    }

    public void drain() {
        checkThread();
        for (int i = 0; i < PER_TICK; i++) {
            Task task = queue.poll();
            if (task == null) break;
            pending.decrementAndGet();
            if (task.generation >= 0 && task.generation != generation) continue;
            try { task.action.run(); }
            catch (Throwable failure) {
                Failures.rethrowFatal(failure);
                log.error("Deferred client task failed", failure);
            }
        }
    }

    @Override public synchronized void close() {
        checkThread();
        closed = true;
        generation++;
        queue.clear();
        pending.set(0);
    }

    private static final class Task {
        final long generation;
        final Runnable action;
        Task(long generation, Runnable action) { this.generation = generation; this.action = action; }
    }
}
