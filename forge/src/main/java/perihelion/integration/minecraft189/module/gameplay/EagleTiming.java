package perihelion.integration.minecraft189.module.gameplay;

/** Source edge-return timing; the random threshold is sampled on each grounded update. */
final class EagleTiming {
  private boolean over, holding;
  private long lastSneak;

  void edge() {
    over = holding = true;
  }

  void release() {
    holding = false;
  }

  boolean held() {
    return holding;
  }

  boolean hold(long now, int delay, double sample) {
    if (over) lastSneak = now;
    over = false;
    if (now - lastSneak >= (long) (delay * (sample * .1 + .95))) holding = false;
    return holding;
  }
}
