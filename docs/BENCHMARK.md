# Event dispatch measurement

Run `./gradlew :core:benchmark` with Java 17 for Gradle and a discoverable Java 8
toolchain. The task starts a separate Java 8 process and writes the raw results to
`core/build/reports/benchmark.txt`. It is an explicit developer task, separate
from `check`; its source set is excluded from production JARs.

## Method

Each of the 0, 1 and 100 active-listener cases performs five warmup batches and
nine measured batches of 500,000 posts. Each listener maintains an accumulator;
the results are consumed through a volatile checksum and checked against the
expected sums after measurement. There are no absolute timing assertions.

The client-thread guard runs on every post. Internal diagnostic timing stays
disabled. The same noncancellable event object is reused, and no subscriptions
change during a measured batch. Timing includes the guard, map lookup, dispatch
and callback arithmetic. Registration, event-object allocation, failure handling
and Minecraft work are outside this measurement.

When available, the JVM's `com.sun.management.ThreadMXBean` reports allocation
for the current thread around each measured batch. Unsupported JVMs report the
counter as unavailable. The task reports every sample and the minimum, median
and maximum elapsed nanoseconds per post.

The recorded [raw output](verification/benchmark.txt) is retained with this documentation.

## Recorded run

2026-09-20, Azul OpenJDK 1.8.0_502, macOS 26.6.2, aarch64. The final verification rerun at 19:50 UTC is recorded below. Gradle 8.4 ran under
the project's local Java 17 installation. The task completed successfully and
all listener checksum checks passed.

| Active listeners | Median ns/post | Min–max ns/post | Median measured bytes/post |
| --- | ---: | ---: | ---: |
| 0 | 5.059 | 4.960–17.183 | 0.000000 |
| 1 | 21.766 | 17.583–25.669 | 0.000000 |
| 100 | 392.221 | 385.205–402.937 | 0.000000 |

These results describe one warmed JVM on one machine. They are not an FPS
measurement, a performance guarantee, or a comparison with an unmodified client.
The harness is deliberately small and does not provide JMH-style fork isolation
or a statistically controlled comparison. Re-run it when changing dispatch;
measure actual frames separately when adding rendering or gameplay features.
