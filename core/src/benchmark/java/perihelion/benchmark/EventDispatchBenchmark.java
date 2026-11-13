package perihelion.benchmark;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import perihelion.core.diagnostics.ClientLog;
import perihelion.core.event.EventBus;
import perihelion.core.event.EventOwner;

/** A bounded, dependency-free developer measurement, not a performance regression test. */
public final class EventDispatchBenchmark {
    private static final int POSTS = 500_000;
    private static final int WARMUP_SAMPLES = 5;
    private static final int MEASURED_SAMPLES = 9;
    private static volatile long consumed;

    private EventDispatchBenchmark() { }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) throw new IllegalArgumentException("Expected report path");
        StringBuilder report = new StringBuilder();
        append(report, "Perihelion event dispatch microbenchmark");
        append(report, "Timestamp: " + Instant.now());
        append(report, "Java: " + System.getProperty("java.version") + " / "
                + System.getProperty("java.vm.name") + " / " + System.getProperty("java.vendor"));
        append(report, "OS: " + System.getProperty("os.name") + " "
                + System.getProperty("os.version") + " / " + System.getProperty("os.arch"));
        append(report, "One JVM, " + WARMUP_SAMPLES + " warmup batches then " + MEASURED_SAMPLES
                + " measured batches per case; " + POSTS + " posts/batch.");
        append(report, "Client thread guard enabled; built-in timing disabled; reused noncancellable event.");
        append(report, "Includes listener arithmetic; excludes registration, event creation, faults and Minecraft.");
        Allocations allocations = Allocations.create();
        append(report, "Current-thread allocation counter: " + (allocations == null ? "unavailable" : "enabled"));
        for (int listeners : new int[] { 0, 1, 100 }) measure(listeners, allocations, report);
        append(report, "Consumed checksum: " + consumed);
        append(report, "Indicative dispatch-only measurement. No FPS or cross-machine performance claim.");
        Path destination = Paths.get(args[0]);
        Files.createDirectories(destination.toAbsolutePath().getParent());
        Files.write(destination, report.toString().getBytes(StandardCharsets.UTF_8));
        System.out.print(report);
    }

    private static void measure(int listenerCount, Allocations allocations, StringBuilder report) {
        Thread clientThread = Thread.currentThread();
        EventBus bus = new EventBus(() -> {
            if (Thread.currentThread() != clientThread) throw new IllegalStateException("Wrong thread");
        }, ClientLog.NONE);
        EventOwner owner = new EventOwner("benchmark", failure -> {
            throw new AssertionError("Unexpected listener failure", failure);
        });
        long[] state = new long[listenerCount];
        for (int index = 0; index < listenerCount; index++) {
            final int slot = index;
            bus.subscribe(owner, Pulse.class, pulse -> state[slot] += pulse.sequence + slot);
        }
        owner.activate();
        Pulse pulse = new Pulse();
        for (int warmup = 0; warmup < WARMUP_SAMPLES; warmup++) {
            postBatch(bus, pulse);
            consume(state, pulse);
            if (allocations != null) allocations.read();
        }
        double[] nanosPerPost = new double[MEASURED_SAMPLES];
        double[] bytesPerPost = new double[MEASURED_SAMPLES];
        for (int sample = 0; sample < MEASURED_SAMPLES; sample++) {
            long bytesBefore = allocations == null ? 0 : allocations.read();
            long start = System.nanoTime();
            postBatch(bus, pulse);
            long elapsed = System.nanoTime() - start;
            long bytesAfter = allocations == null ? 0 : allocations.read();
            nanosPerPost[sample] = elapsed / (double) POSTS;
            bytesPerPost[sample] = allocations == null ? Double.NaN
                    : (bytesAfter - bytesBefore) / (double) POSTS;
            consume(state, pulse);
        }
        long batches = WARMUP_SAMPLES + MEASURED_SAMPLES;
        long sequenceSum = (long) POSTS * (POSTS - 1) / 2;
        for (int index = 0; index < listenerCount; index++) {
            long expected = batches * (sequenceSum + (long) POSTS * index);
            if (state[index] != expected) throw new AssertionError("Listener result mismatch at " + index);
        }
        if (bus.subscriptionCount() != listenerCount) throw new AssertionError("Listener lost during measurement");
        owner.close();
        bus.close();
        append(report, "");
        append(report, "Listeners: " + listenerCount);
        append(report, "  ns/post samples: " + formatted(nanosPerPost));
        append(report, "  bytes/post samples: " + formatted(bytesPerPost));
        Arrays.sort(nanosPerPost);
        Arrays.sort(bytesPerPost);
        append(report, String.format(Locale.ROOT,
                "  ns/post min=%.3f median=%.3f max=%.3f; median bytes/post=%s",
                nanosPerPost[0], nanosPerPost[MEASURED_SAMPLES / 2], nanosPerPost[MEASURED_SAMPLES - 1],
                allocations == null ? "unavailable" : String.format(Locale.ROOT, "%.6f", bytesPerPost[MEASURED_SAMPLES / 2])));
    }

    private static void postBatch(EventBus bus, Pulse pulse) {
        for (int post = 0; post < POSTS; post++) {
            pulse.sequence = post;
            bus.post(pulse);
        }
    }

    private static void consume(long[] state, Pulse pulse) {
        long checksum = pulse.sequence;
        for (long value : state) checksum += value;
        consumed ^= checksum;
    }

    private static String formatted(double[] values) {
        StringBuilder result = new StringBuilder();
        for (double value : values) {
            if (result.length() != 0) result.append(", ");
            result.append(Double.isNaN(value) ? "unavailable" : String.format(Locale.ROOT, "%.3f", value));
        }
        return result.toString();
    }

    private static void append(StringBuilder report, String line) { report.append(line).append('\n'); }

    private static final class Pulse { long sequence; }

    private static final class Allocations {
        private final com.sun.management.ThreadMXBean bean;
        private final long threadId;

        private Allocations(com.sun.management.ThreadMXBean bean) {
            this.bean = bean;
            this.threadId = Thread.currentThread().getId();
        }

        static Allocations create() {
            java.lang.management.ThreadMXBean base = ManagementFactory.getThreadMXBean();
            if (!(base instanceof com.sun.management.ThreadMXBean)) return null;
            com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) base;
            if (!bean.isThreadAllocatedMemorySupported()) return null;
            try {
                if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
                Allocations allocations = new Allocations(bean);
                return allocations.read() < 0 ? null : allocations;
            } catch (SecurityException | UnsupportedOperationException unavailable) {
                return null;
            }
        }

        long read() { return bean.getThreadAllocatedBytes(threadId); }
    }
}
