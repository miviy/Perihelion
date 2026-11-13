package perihelion.integration.minecraft189.module.gameplay;

/** B4's progress credit and Swap cap; speed is not a multiplier. */
final class BreakerTiming {
  private BreakerTiming() {}

  static float credit(double speed) {
    return (float) (speed - 1);
  }

  static float swapStep(float strength, double speed) {
    return Math.min(strength, (float) ((2 - speed) / 2 - .01));
  }

  static float legitProgress(float progress, float step, double speed) {
    return Math.max(progress + step, credit(speed));
  }

  static int quickTicks(boolean maximize, boolean swap, boolean fatigue) {
    return swap && fatigue ? 17 : maximize ? 2 : 4;
  }

  static int scanTicks(double seconds) {
    return Math.max(1, (int) (seconds * 20));
  }
}
