package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;

import org.junit.Test;

public class GhostClickTimingTest {
  @Test
  public void clampsGaussianTailsBeforeSubClickJitterAndRounding() {
    assertEquals(8, GhostClickTiming.cps(8, 14, -100, -.4));
    assertEquals(14, GhostClickTiming.cps(8, 14, 100, .399));
    assertEquals(11, GhostClickTiming.cps(8, 14, 0, 0));
    assertEquals(13, GhostClickTiming.cps(8, 14, 1, .399));
    assertEquals(12, GhostClickTiming.cps(8, 14, 1, -.4));
  }

  @Test
  public void reversedOrEqualBoundsUseTheMaximumSettingWithoutSampling() {
    assertEquals(8, GhostClickTiming.cps(14, 8, 100, 100));
    assertEquals(8, GhostClickTiming.cps(8, 8, -100, -100));
    assertEquals(1, GhostClickTiming.cps(25, 1, 0, 0));
  }

  @Test
  public void halfStepSettingsRetainTheDonorIntegerConversion() {
    assertEquals(8, GhostClickTiming.cps(8.5, 8.5, 100, 100));
    assertEquals(11, GhostClickTiming.cps(8.5, 14.5, 0, 0));
  }

  @Test
  public void queueCadenceUsesOneIntegerDivisionPeriodWithoutCatchUpBursts() {
    assertTrue(GhostClickTiming.due(0, -1, 14));
    assertFalse(GhostClickTiming.due(1070, 1000, 14));
    assertTrue(GhostClickTiming.due(1071, 1000, 14));
    assertFalse(GhostClickTiming.due(1039, 1000, 25));
    assertTrue(GhostClickTiming.due(1040, 1000, 25));
  }
}
