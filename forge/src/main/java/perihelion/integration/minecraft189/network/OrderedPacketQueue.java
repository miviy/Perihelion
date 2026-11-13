package perihelion.integration.minecraft189.network;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Small synchronized mailbox shared by raw hooks and the client tick. */
public final class OrderedPacketQueue<T> {
    private static final class Entry<T> {
        final T value;
        final long due;
        final int bytes;

        Entry(T value, long due, int bytes) {
            this.value = value;
            this.due = due;
            this.bytes = bytes;
        }
    }

    private final ArrayDeque<Entry<T>> entries = new ArrayDeque<>();
    private final int limit, byteLimit;
    private int bytes;
    private boolean closed;

    public OrderedPacketQueue(int limit, int byteLimit) {
        this.limit = limit;
        this.byteLimit = byteLimit;
    }

    public synchronized boolean offer(T value, long due, int size) {
        if (closed || size < 0 || entries.size() >= limit || size > byteLimit - bytes) return false;
        entries.addLast(new Entry<>(value, due, size));
        bytes += size;
        return true;
    }

    public synchronized List<T> drain(long now, boolean all) {
        List<T> result = new ArrayList<>();
        // A shorter later delay must never overtake an earlier packet.
        while (!entries.isEmpty() && (all || entries.peekFirst().due <= now)) {
            Entry<T> entry = entries.removeFirst();
            bytes -= entry.bytes;
            result.add(entry.value);
        }
        return result;
    }

    public synchronized void close() {
        closed = true;
        entries.clear();
        bytes = 0;
    }

    public synchronized int size() {
        return entries.size();
    }
}
