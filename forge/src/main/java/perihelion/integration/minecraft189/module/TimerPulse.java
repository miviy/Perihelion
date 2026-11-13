package perihelion.integration.minecraft189.module;

/** Yuri's two 100 ms windows, including its hold on the cycle-reset update. */
final class TimerPulse {
  private long start;
  private float last = 1.0f;

  TimerPulse(long now) {
    start = now;
  }

  void resume(float currentSpeed) {
    last = currentSpeed;
  }

  float speed(long now, boolean pulse, float first, float second) {
    if (!pulse) {
      return last = first;
    }
    long elapsed = now - start;
    if (elapsed < 100_000_000L) last = second;
    else if (elapsed <= 200_000_000L) last = first;
    else start = now;
    return last;
  }
}
