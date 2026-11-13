package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;

import org.junit.Test;
import perihelion.integration.minecraft189.control.GroundTickHistory;

public class TellyTimingTest {
  @Test
  public void landingClosesTheLatchOnEveryRealJumpCycle() {
    TellyTiming timing = new TellyTiming(0);
    GroundTickHistory history = new GroundTickHistory();
    for (int cycle = 0; cycle < 4; cycle++) {
      history.advance(true);
      assertFalse(timing.placement(1000, history.beforeAir(), history.ground(), false, false, 6));
      for (int airborne = 0; airborne <= 9; airborne++) {
        history.advance(false);
        assertEquals(
            "cycle " + cycle + " air " + airborne,
            airborne >= 6,
            timing.placement(1000, history.beforeAir(), history.ground(), false, false, 6));
      }
    }
  }

  @Test
  public void startupPlacementWindowEndsAtFourHundredMilliseconds() {
    TellyTiming timing = new TellyTiming(100);
    assertTrue(timing.placement(499, 0, 20, false, false, 6));
    assertFalse(timing.placement(500, 0, 20, false, false, 6));
    timing.reset(1000);
    assertTrue(timing.placement(1399, 0, 20, false, false, 6));
    assertFalse(timing.placement(1400, 0, 20, false, false, 6));
  }

  @Test
  public void hypixelEarlyWindowReopensPerJumpAndLateEntryDoesNotOpenIt() {
    TellyTiming timing = new TellyTiming(0);
    assertFalse(timing.placement(400, 0, 1, true, true, 4));
    assertFalse(timing.placement(450, 5, 0, true, true, 4));
    assertTrue(timing.placement(500, 4, 0, true, true, 4));
    assertTrue(timing.placement(550, 8, 0, true, true, 4));
    assertFalse(timing.placement(600, 8, 1, true, false, 6));
    assertFalse(timing.placement(650, 0, 0, true, false, 6));
    assertTrue(timing.placement(700, 5, 0, true, false, 6));
  }

  @Test
  public void directionChangesAndSkippedSamplesCannotMissTheAirThreshold() {
    TellyTiming timing = new TellyTiming(0);
    assertFalse(timing.placement(400, 0, 1, false, false, 6));
    assertFalse(timing.placement(450, 3, 0, false, false, 6));
    assertTrue(timing.placement(500, 5, 0, false, true, 4));
    assertTrue(timing.placement(550, 8, 0, false, false, 6));
    assertFalse(timing.placement(600, 0, 1, false, false, 1));
    assertTrue(timing.placement(650, 3, 0, false, false, 1));
  }

  @Test
  public void zeroThresholdOpensInAirButNeverOnGroundAfterStartup() {
    TellyTiming timing = new TellyTiming(0);
    assertFalse(timing.placement(500, 0, 1, false, false, 0));
    assertTrue(timing.placement(550, 0, 0, false, false, 0));
  }

  @Test
  public void startupAndGroundedReturnHavePriorityOverHypixelSpeeds() {
    assertEquals(5, TellyTiming.rotationSpeed(true, true, true, true, true, false, true, 7), 0);
    assertEquals(10, TellyTiming.rotationSpeed(true, true, false, true, true, false, false, 7), 0);
    assertEquals(20, TellyTiming.rotationSpeed(false, true, true, true, true, true, false, 7), 0);
    assertEquals(3.6f, TellyTiming.rotationSpeed(true, true, true, false, true, true, false, 7), 0);
    assertEquals(
        3.45f, TellyTiming.rotationSpeed(true, false, true, false, true, true, false, 7), 0);
    assertEquals(
        2.8f, TellyTiming.rotationSpeed(true, false, false, false, true, true, false, 7), 0);
    assertEquals(7, TellyTiming.rotationSpeed(false, true, false, false, true, true, false, 7), 0);
  }
}
