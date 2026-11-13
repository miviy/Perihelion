package perihelion.integration.minecraft189.module.gameplay;

/** Source-derived B4 default range/session math, without packet storage or game globals. */
final class B4LagRangeState {
  private int missing, movingAway;
  private long startedAt, maxTime;
  private volatile long delay;
  private volatile boolean active;
  private volatile long session;

  static double range(int speedLevel) { return 8.5 + speedLevel; }
  static double directionDot(double motionX, double motionZ, double deltaX, double deltaZ) {
    double motion = motionX * motionX + motionZ * motionZ, distance = deltaX * deltaX + deltaZ * deltaZ;
    if (!(motion >= 1.0E-8 && distance >= 1.0E-8)) return 0;
    double inverseMotion = 1.0 / Math.sqrt(motion), inverseDistance = 1.0 / Math.sqrt(distance);
    return motionX * inverseMotion * (deltaX * inverseDistance)
        + motionZ * inverseMotion * (deltaZ * inverseDistance);
  }
  static boolean approaches(double speed, double dot) { return speed >= .02 && dot >= .15; }

  void begin(long now, double distanceSq, int delayJitter, int durationJitter) {
    delay = (distanceSq > 42.25 ? 200 : 100) + delayJitter;
    startedAt = now;
    maxTime = 5000 + durationJitter;
    missing = movingAway = 0;
    session++;
    active = true;
  }

  boolean shouldStop(long now, boolean present, double distanceSq, double range,
      double speed, double dot) {
    if (now - startedAt > maxTime) return true;
    if (!present) return ++missing >= 3;
    missing = 0;
    if (distanceSq > (range + 1) * (range + 1)) return true;
    movingAway = speed >= .02 && dot <= -.15 ? movingAway + 1 : 0;
    return movingAway >= 4;
  }
  boolean active() { return active; }
  long session() { return session; }
  boolean current(long expected) { return active && session == expected; }
  long delay() { return delay; }
  void stop() { active = false; session++; missing = movingAway = 0; }
}
