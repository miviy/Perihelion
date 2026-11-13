package perihelion.integration.minecraft189.module.gameplay;

/** Native PRE/POST schedule for Yuri's single LagManager spoof lease or range-held Blink. */
final class LagRangeTiming {
  static int sampleDelay(int first, int second) {
    if (first == second) return first;
    return (int)
        java.util.concurrent.ThreadLocalRandom.current()
            .nextDouble(Math.min(first, second), Math.max(first, second));
  }

  enum Action {
    NONE,
    COLLECT,
    FLUSH
  }

  private boolean lagging, arm, expired;
  private long end, lease;
  private volatile int delay;

  int delay() {
    return delay;
  }

  boolean lagging() {
    return lagging;
  }

  Action pre(
      long now,
      boolean moving,
      Double distance,
      boolean blink,
      double low,
      double high,
      int sampledDelay) {
    return pre(now, moving, distance, blink, low, high, () -> sampledDelay);
  }

  Action pre(
      long now,
      boolean moving,
      Double distance,
      boolean blink,
      double low,
      double high,
      java.util.function.IntSupplier sampler) {
    boolean band =
        distance != null && distance > Math.min(low, high) && distance <= Math.max(low, high);
    if (!moving || distance == null) {
      if (lagging) {
        lagging = arm = false;
        return Action.FLUSH;
      }
      return Action.NONE;
    }
    if (lagging) {
      if (blink ? !band : now >= end) {
        lagging = arm = false;
        return Action.FLUSH;
      }
      return Action.NONE;
    }
    if (!band) return Action.NONE;
    lagging = true;
    expired = false;
    delay = sampler.getAsInt();
    end = now + delay;
    lease = now + 100;
    arm = !blink;
    return blink ? Action.COLLECT : Action.NONE;
  }

  Action post(long now, boolean blink) {
    if (!lagging || blink || expired) return Action.NONE;
    if (now >= lease) {
      arm = false;
      expired = true;
      return Action.FLUSH;
    }
    if (arm) {
      arm = false;
      return Action.COLLECT;
    }
    return Action.NONE;
  }

  void reset() {
    lagging = arm = expired = false;
    delay = 0;
  }
}
