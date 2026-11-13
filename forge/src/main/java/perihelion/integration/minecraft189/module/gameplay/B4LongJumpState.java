package perihelion.integration.minecraft189.module.gameplay;

/** OpenB4 Fireball Auto's two-motion throw, 750ms timeout and one boost tick. */
final class B4LongJumpState {
  private int throwTicks = 1, stage = -1, jumpTicks;
  private long fireballAt;

  boolean advance() {
    if (stage != -1 && ++stage > 1) return false;
    return true;
  }

  boolean throwNow() {
    if (throwTicks <= 0 || ++throwTicks < 3) return false;
    throwTicks = 0;
    return true;
  }

  void thrown(long now) { fireballAt = now; jumpTicks = 2; }
  boolean explosion(double distanceSquared) {
    if (fireballAt == 0 || distanceSquared > 9) return false;
    fireballAt = 0;
    stage = 0;
    return true;
  }

  boolean timedOut(long now) { return fireballAt > 0 && Math.abs(now - fireballAt) > 750; }
  boolean jumpNow() { return jumpTicks > 0 && --jumpTicks == 0; }
  boolean aiming() { return throwTicks > 0; }
  boolean boosting() { return stage == 1; }
}
