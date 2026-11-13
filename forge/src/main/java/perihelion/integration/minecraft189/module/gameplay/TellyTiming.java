package perihelion.integration.minecraft189.module.gameplay;

/** Yuri-derived startup window with a placement latch scoped to each airborne run. */
final class TellyTiming {
  static final long SAFE_MILLIS = 400;
  private long started;
  private boolean allowed = true;

  TellyTiming(long now) {
    started = now;
  }

  void reset(long now) {
    started = now;
    allowed = true;
  }

  boolean starting(long now) {
    return now - started < SAFE_MILLIS;
  }

  boolean placement(
      long now, int air, int ground, boolean hypixel, boolean diagonal, int threshold) {
    if (starting(now)) return allowed = true;
    // Ground is the CURRENT ground count; air is the preceding PRE-update count.
    // Both preceding counters are never zero again after the first player tick.
    if (ground > 0) return allowed = false;
    if (hypixel ? air > 0 && air <= (diagonal ? 4 : 5) : air >= threshold) allowed = true;
    return allowed;
  }

  static double rotationSpeed(
      boolean hypixel,
      boolean diagonal,
      boolean jumping,
      boolean grounded,
      boolean moving,
      boolean sprinting,
      boolean starting,
      double normal) {
    return rotationSpeed(
        hypixel,
        diagonal,
        jumping,
        grounded && moving && (!sprinting || jumping),
        starting,
        normal);
  }

  /** Ground-return eligibility is sampled before physics, as in the donor PRE update. */
  static double rotationSpeed(
      boolean hypixel,
      boolean diagonal,
      boolean jumping,
      boolean returning,
      boolean starting,
      double normal) {
    if (starting) return 5;
    if (returning) return hypixel ? 10 : 20;
    return hypixel ? diagonal ? 3.6f : jumping ? 3.45f : 2.8f : normal;
  }
}
