package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;

import org.junit.Test;

public class BreakerTimingTest {
  @Test
  public void speedIsInitialCreditRatherThanMultiplyingEachTick() {
    assertEquals(0, BreakerTiming.credit(1), 0);
    assertEquals(.8f, BreakerTiming.credit(1.8), 1e-6);
    assertEquals(.09f, BreakerTiming.swapStep(.3f, 1.8), 1e-6);
    assertEquals(.01f, BreakerTiming.swapStep(.01f, 1.8), 1e-6);
    assertEquals(.8f, BreakerTiming.legitProgress(0, .2f, 1.8), 1e-6);
    assertEquals(1f, BreakerTiming.legitProgress(.8f, .2f, 1.8), 1e-6);
  }

  @Test
  public void quickBreakMatchesB4CountdownAndFatigueOverride() {
    assertEquals(4, BreakerTiming.quickTicks(false, false, false));
    assertEquals(2, BreakerTiming.quickTicks(true, false, true));
    assertEquals(17, BreakerTiming.quickTicks(true, true, true));
    assertEquals(4, BreakerTiming.scanTicks(.2));
    assertEquals(1, BreakerTiming.scanTicks(.05));
  }
}
