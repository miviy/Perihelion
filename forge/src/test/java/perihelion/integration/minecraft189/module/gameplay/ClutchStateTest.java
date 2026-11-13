package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;

import org.junit.Test;

public class ClutchStateTest {
  @Test
  public void midairEnableUsesExistingPlayerFlightImmediately() {
    ClutchState state = new ClutchState();
    state.attemptWindow(true);
    state.attemptWindow(true);
    state.flight(20);
    assertTrue(state.attemptWindow(false));
  }

  @Test
  public void initialWindowAllowsAnOwnedWalkingBoundaryThenExpires() {
    ClutchState state = new ClutchState();
    assertTrue(state.attemptWindow(true));
    assertTrue(state.attemptWindow(true));
    assertFalse(state.attemptWindow(true));
  }

  @Test
  public void refreshNeedsMoreThanThreeAirTicksAndNoBlockAtTenBelow() {
    ClutchState state = new ClutchState();
    state.attemptWindow(true);
    state.attemptWindow(true);
    for (int i = 0; i < 3; i++) {
      state.flight(i + 1);
      assertFalse(state.attemptWindow(false));
    }
    state.flight(4);
    assertFalse(state.attemptWindow(true));
    assertTrue(state.attemptWindow(false));
    for (int i = 0; i < 10; i++) assertTrue(state.attemptWindow(true));
    assertFalse(state.attemptWindow(true));
    state.flight(0);
    assertFalse(state.attemptWindow(false));
  }

  @Test
  public void zeroToOneDelayIsContinuousAndAlwaysPassesAtFirstAirTick() {
    ClutchState state = new ClutchState();
    state.underfoot(true);
    assertTrue(state.delayPassed(0, 1, 0));
    assertTrue(state.delayPassed(0, 1, .999999));
    assertTrue(state.delayPassed(1, 0, .999999));
    assertFalse(state.delayPassed(1, 1, .5));
    state.underfoot(true);
    assertTrue(state.delayPassed(1, 1, .5));
    state.attempted();
    assertEquals(0, state.onAir());
    state.underfoot(true);
    state.underfoot(false);
    assertEquals(0, state.onAir());
  }

  @Test
  public void initialAndPlacementPacketWindowsUseDifferentSourcePredicates() {
    for (ClutchState.Traffic traffic : ClutchState.Traffic.values()) {
      ClutchState state = new ClutchState();
      state.packet(traffic);
      boolean initialBad =
          traffic != ClutchState.Traffic.WALK && traffic != ClutchState.Traffic.OTHER;
      assertEquals(initialBad, state.bad());
      assertEquals(
          traffic == ClutchState.Traffic.ATTACK || traffic == ClutchState.Traffic.INVENTORY,
          state.placementBad());
      state.packet(ClutchState.Traffic.WALK);
      assertFalse(state.bad());
      assertFalse(state.placementBad());
    }
  }
}
