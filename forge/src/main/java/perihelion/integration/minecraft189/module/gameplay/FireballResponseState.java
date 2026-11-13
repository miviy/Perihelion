package perihelion.integration.minecraft189.module.gameplay;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Source one-time startup delay and persistent seen IDs, with native world retirement/bounds. */
final class FireballResponseState {
  private static final int CAPACITY = 1024;
  private final long createdAt;
  private final Set<UUID> attacked = new LinkedHashSet<>();
  private long generation = Long.MIN_VALUE;
  private boolean nearby;

  FireballResponseState(long createdAt) {
    this.createdAt = createdAt;
  }

  boolean ready(long now) {
    return now - createdAt >= 50;
  }

  void world(long current) {
    if (generation == current) return;
    attacked.clear();
    nearby = false;
    generation = current;
  }

  boolean observeNearby(boolean detected) {
    boolean notify = detected && !nearby;
    nearby = detected;
    return notify;
  }

  boolean attacked(UUID id) {
    return attacked.contains(id);
  }

  void record(UUID id) {
    attacked.add(id);
    if (attacked.size() > CAPACITY) attacked.remove(attacked.iterator().next());
  }
}
