package perihelion.integration.minecraft189.module.gameplay;

/** OpenB4's use-tick thresholds and ground-use state, independent of the network thread. */
final class B4NoSlowState {
  enum Kind { BLINK, GROUND, INVALID }
  private int useTicks;
  private boolean airborneUse, blinking;

  synchronized void placement(boolean grounded, int airTicks, boolean groundWithinPointTwo) {
    airborneUse = !(grounded || airTicks == 11 && groundWithinPointTwo);
  }

  synchronized float tick(boolean using, boolean grounded, Kind mode) {
    useTicks = using ? useTicks + 1 : 0;
    if (mode == Kind.BLINK) {
      if (!using || useTicks >= 34) blinking = false;
      else if (useTicks == 14) blinking = true;
      return using && useTicks < 14 ? .2f : 1;
    }
    blinking = false;
    return mode == Kind.GROUND && using && airborneUse && grounded ? .3f : 1;
  }

  synchronized boolean blinking() { return blinking; }
  synchronized boolean suppressJump() { return useTicks > 0; }
  synchronized void reset() { useTicks = 0; airborneUse = blinking = false; }
}
