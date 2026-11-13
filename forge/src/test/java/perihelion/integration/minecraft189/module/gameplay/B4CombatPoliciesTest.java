package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;
import java.util.Random;
import org.junit.Test;

public class B4CombatPoliciesTest {
  private static final class Samples extends Random {
    private final int roll;
    Samples(int roll) { this.roll = roll; }
    @Override public double nextDouble() { return .5; }
    @Override public int nextInt(int bound) { return Math.min(roll, bound - 1); }
  }

  @Test public void clicksUseStrictPressAndReleaseBoundariesWithoutCatchUpBursts() {
    B4ClickTiming clock = new B4ClickTiming(new Samples(0));
    assertEquals(B4ClickTiming.Action.NONE, clock.click(1000, 10, 10));
    assertEquals(1100, clock.nextPress);
    assertEquals(1050, clock.nextRelease);
    assertEquals(B4ClickTiming.Action.NONE, clock.click(1050, 10, 10));
    assertEquals(B4ClickTiming.Action.RELEASE, clock.click(1051, 10, 10));
    assertEquals(B4ClickTiming.Action.NONE, clock.click(1100, 10, 10));
    assertEquals(B4ClickTiming.Action.PRESS, clock.click(1101, 10, 10));
    assertEquals(B4ClickTiming.Action.PRESS, clock.click(10000, 10, 10));
    assertEquals(B4ClickTiming.Action.NONE, clock.click(10000, 10, 10));
  }

  @Test public void slowPhaseAndExtraDelayRetainSourceTruncationAndHalfRelease() {
    B4ClickTiming clock = new B4ClickTiming(new Samples(95));
    clock.click(1000, 10, 10);
    // With source midpoints: 100ms * 1.17 truncates to117, then +68ms.
    assertEquals(1185, clock.nextPress);
    assertEquals(1083, clock.nextRelease);
  }

  @Test public void inventoryClickIsDelayedAndDamageCanAdvancePendingAttack() {
    B4ClickTiming clock = new B4ClickTiming(new Samples(0));
    assertFalse(clock.inventory(1000, 10));
    assertEquals(1099, clock.nextPress);
    assertFalse(clock.inventory(1099, 10));
    assertTrue(clock.inventory(1100, 10));
    clock.reset();
    clock.click(2000, 10, 10);
    clock.damage(2020);
    assertEquals(2040, clock.nextPress);
    assertEquals(2, clock.nextRelease);
    clock.resume();
    assertEquals(B4ClickTiming.Action.PRESS, clock.click(2041, 10, 10));
    clock.reset();
    assertEquals(0, clock.nextPress);
    assertEquals(0, clock.nextRelease);
  }

  @Test public void guardHoldsThreePostMotionIntervalsThenCoolsDownFive() {
    B4GuardState state = new B4GuardState();
    assertEquals(B4GuardState.Action.PRESS, state.update(true, false));
    assertEquals(B4GuardState.Action.NONE, state.update(true, false));
    assertEquals(B4GuardState.Action.NONE, state.update(true, false));
    assertEquals(B4GuardState.Action.RELEASE, state.update(true, false));
    for (int i = 0; i < 4; i++) assertEquals(B4GuardState.Action.NONE, state.update(true, false));
    assertEquals(B4GuardState.Action.PRESS, state.update(true, false));
    assertTrue(state.knockback());
    assertFalse(state.knockback());
    state.reset();
    assertEquals(B4GuardState.Action.NONE, state.update(true, true));
    assertEquals(B4GuardState.Action.PRESS, state.update(true, false));
    assertEquals(B4GuardState.Action.RELEASE, state.update(false, false));
  }

  @Test public void manualHoldDoesNotCreateAnAutomatedCooldownOnRelease() {
    B4GuardState state = new B4GuardState();
    state.update(true, false);
    assertEquals(B4GuardState.Action.RELEASE, state.update(false, true));
    assertEquals(B4GuardState.Action.PRESS, state.update(true, false));
  }

  @Test public void lagRangeUsesSelfMotionDotAndExactCloseFarBoundary() {
    assertEquals(8.5, B4LagRangeState.range(0), 0);
    assertEquals(10.5, B4LagRangeState.range(2), 0);
    assertEquals(1, B4LagRangeState.directionDot(.1, 0, 3, 0), 1e-12);
    assertEquals(-1, B4LagRangeState.directionDot(.1, 0, -3, 0), 1e-12);
    assertEquals(0, B4LagRangeState.directionDot(0, 0, 3, 0), 0);
    assertTrue(B4LagRangeState.approaches(.02, .15));
    assertFalse(B4LagRangeState.approaches(.01999, .9));
    assertFalse(B4LagRangeState.approaches(.2, .14999));
    B4LagRangeState state = new B4LagRangeState();
    state.begin(1000, 42.25, -12, -454);
    assertEquals(88, state.delay());
    state.begin(1000, 42.25001, 12, 454);
    assertEquals(212, state.delay());
  }

  @Test public void retreatRequiresFourConsecutiveIntervalsAndMissingRequiresThree() {
    B4LagRangeState state = new B4LagRangeState();
    state.begin(1000, 16, 0, 0);
    for (int i = 0; i < 3; i++) assertFalse(state.shouldStop(1100, true, 16, 8.5, .02, -.15));
    assertFalse(state.shouldStop(1100, true, 16, 8.5, .02, 0));
    for (int i = 0; i < 3; i++) assertFalse(state.shouldStop(1100, true, 16, 8.5, .02, -.15));
    assertTrue(state.shouldStop(1100, true, 16, 8.5, .02, -.15));
    state.begin(1000, 16, 0, 0);
    assertFalse(state.shouldStop(1100, false, 0, 8.5, 0, 0));
    assertFalse(state.shouldStop(1100, false, 0, 8.5, 0, 0));
    assertTrue(state.shouldStop(1100, false, 0, 8.5, 0, 0));
  }

  @Test public void maxSessionAndKeepRangeUseSourceStrictGreaterComparisons() {
    B4LagRangeState state = new B4LagRangeState();
    state.begin(1000, 16, 0, 0);
    assertFalse(state.shouldStop(6000, true, 90.25, 8.5, 0, 0));
    assertTrue(state.shouldStop(6001, true, 90.25, 8.5, 0, 0));
    assertTrue(state.shouldStop(5000, true, 90.25001, 8.5, 0, 0));
    state.stop();
    assertFalse(state.active());
  }

  @Test public void approachThresholdRetainsDonorInverseMultiplicationGrouping() {
    double dot = B4LagRangeState.directionDot(.0293698354835235, .014719391229731068,
        -.9113790515793535, 2.8062859711420427);
    assertEquals(.15000000000000002, dot, 0);
    // Dividing each component instead yields .14999999999999997 and rejects this approach.
    assertTrue(B4LagRangeState.approaches(.03285, dot));
  }

  @Test public void deferredInteractionTokenCannotStopAReplacementSession() {
    B4LagRangeState state = new B4LagRangeState();
    state.begin(1000, 16, 0, 0);
    long pendingRelease = state.session();
    assertTrue(state.current(pendingRelease));
    state.stop();
    state.begin(1100, 16, 0, 0);
    assertFalse(state.current(pendingRelease));
    assertTrue(state.current(state.session()));
    long current = state.session();
    state.stop();
    assertFalse(state.current(current));
  }
}
