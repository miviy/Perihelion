package perihelion.integration.minecraft189.module.gameplay;

/** Yuri's current-candidate delay, measured from the previous transfer rather than selection. */
final class StealerTiming {
  private long lastTransfer;
  private int target = -1;
  private long delay;

  StealerTiming(long initializedAt) {
    lastTransfer = initializedAt;
  }

  boolean select(int slot) {
    if (target == slot) return false;
    target = slot;
    delay = 0;
    return true;
  }

  boolean needsDelay() {
    return delay == 0;
  }

  void decide(long delay) {
    this.delay = delay;
  }

  boolean ready(long now) {
    return target >= 0 && now - lastTransfer >= delay;
  }

  void transferred(long now) {
    lastTransfer = now;
    clearCandidate();
  }

  void clearCandidate() {
    target = -1;
    delay = 0;
  }

  static long dynamicDelay(
      long base,
      double physicalDistance,
      boolean distanceScaling,
      int distanceMultiplier,
      boolean positionScaling,
      int positionMultiplier,
      int slot) {
    long result = base;
    if (distanceScaling) result += Math.round(physicalDistance * distanceMultiplier);
    if (positionScaling) result += Math.round(slot * (double) positionMultiplier);
    return result;
  }
}
