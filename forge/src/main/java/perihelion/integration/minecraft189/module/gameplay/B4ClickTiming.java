package perihelion.integration.minecraft189.module.gameplay;

import java.util.Random;

/** OpenB4 u20 AutoClicker scheduling, independent of Minecraft and input ownership. */
final class B4ClickTiming {
  enum Action { NONE, PRESS, RELEASE }
  private final Random random;
  long nextPress, nextRelease;
  private long phaseEnd, extraEnd;
  private boolean slowPhase;
  private double slowFactor;
  private final double slowMin, slowMax;
  private final int extraMin, extraMax;

  B4ClickTiming(Random random) {
    this.random = random;
    slowMin = between(1.09, 1.12);
    slowMax = between(1.22, 1.25);
    extraMin = integer(40, 50);
    extraMax = integer(85, 95);
  }

  private double between(double low, double high) { return low + (high - low) * random.nextDouble(); }
  private int integer(int low, int high) { return low + (int) ((high - low + 1) * random.nextDouble()); }

  void schedule(long now, double minimum, double maximum, boolean inventory, double inventoryCps) {
    double cps = inventory ? inventoryCps + between(-.6, .8)
        : (minimum == maximum ? minimum
            : between(Math.min(minimum, maximum), Math.max(minimum, maximum))) + between(-.3, .4);
    long delay = (int) Math.round(1000.0 / cps);
    if (now > phaseEnd) {
      if (slowPhase) slowPhase = false;
      else if (random.nextInt(100) >= 80) {
        slowPhase = true;
        slowFactor = between(slowMin, slowMax);
      }
      phaseEnd = now + integer(700, 2200);
    }
    if (slowPhase) delay = (long) (delay * slowFactor);
    if (now > extraEnd) {
      if (random.nextInt(100) >= 75) delay += integer(extraMin, extraMax);
      extraEnd = now + integer(700, 2200);
    }
    nextPress = now + delay;
    nextRelease = now + delay / 2L - random.nextInt(10);
  }

  Action click(long now, double minimum, double maximum) {
    if (nextPress > 0 && nextRelease > 0) {
      if (now > nextPress) {
        schedule(now, minimum, maximum, false, 0);
        return Action.PRESS;
      }
      if (nextRelease != 1 && now > nextRelease) {
        nextRelease = 1;
        return Action.RELEASE;
      }
    } else schedule(now, minimum, maximum, false, 0);
    return Action.NONE;
  }

  boolean inventory(long now, double cps) {
    if (nextRelease == 0 || nextPress == 0) schedule(now, 0, 0, true, cps);
    else if (now > nextPress) {
      schedule(now, 0, 0, true, cps);
      return true;
    }
    return false;
  }

  void damage(long now) {
    if (now < nextRelease) { nextRelease = 2; nextPress = now + 20; }
    else nextPress = 2;
  }

  void resume() { nextRelease = nextPress = 2; }
  void reset() { nextRelease = nextPress = 0; }
}
