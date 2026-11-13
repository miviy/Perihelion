package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;
import java.util.UUID;
import org.junit.Test;

/** Boundary vectors from OpenB4 u20's NoSlow, Long Jump and Bed Wars routines. */
public class B4MovementAndBedWarsStateTest {
  @Test public void bedwarsMatchStartUsesAllSevenDonorLinesAndTheOriginalChatTypeAndSpaceGates() {
    for (String line : new String[] {"  Protect your bed", "  Select an ultimate", "  Collect Lucky Blocks",
        "  Become a Soul Collector", "  All generators are maxed!", "  Destroy the enemy bed and",
        "  Every few seconds brings a new"}) {
      assertTrue(line, B4BedWarsState.gameStart(line + " additional instructions", 0));
      assertTrue(line, B4BedWarsState.gameStart(line, 1));
      assertFalse(line, B4BedWarsState.gameStart(line, 2));
      assertFalse(line, B4BedWarsState.gameStart(line.trim(), 1));
      assertFalse(line, B4BedWarsState.gameStart("Player: " + line, 0));
    }
    assertFalse(B4BedWarsState.gameStart("The game has started!", 1));
    assertFalse(B4BedWarsState.gameStart("", 1));
    assertFalse(B4BedWarsState.gameStart(null, 1));
  }

  @Test public void blinkAllowsThirteenSlowTicksThenOneTwentyTickBufferWindow() {
    B4NoSlowState state = new B4NoSlowState();
    for (int tick = 1; tick <= 40; tick++) {
      assertEquals("slowdown tick " + tick, tick < 14 ? .2f : 1f,
          state.tick(true, true, B4NoSlowState.Kind.BLINK), 0);
      assertEquals("buffer tick " + tick, tick >= 14 && tick < 34, state.blinking());
    }
    state.tick(false, true, B4NoSlowState.Kind.BLINK);
    assertFalse(state.blinking());
    assertEquals(.2f, state.tick(true, true, B4NoSlowState.Kind.BLINK), 0);
  }

  @Test public void releasingItemClosesBlinkImmediatelyAndModeResetDropsOldGroundState() {
    B4NoSlowState state = new B4NoSlowState();
    for (int i = 0; i < 14; i++) state.tick(true, false, B4NoSlowState.Kind.BLINK);
    assertTrue(state.blinking());
    assertEquals(1, state.tick(false, false, B4NoSlowState.Kind.BLINK), 0);
    assertFalse(state.blinking());
    state.placement(false, 4, false);
    assertEquals(.3f, state.tick(true, true, B4NoSlowState.Kind.GROUND), 0);
    assertTrue(state.suppressJump());
    state.reset();
    assertFalse(state.suppressJump());
    assertEquals(1, state.tick(true, true, B4NoSlowState.Kind.GROUND), 0);
  }

  @Test public void groundSpecialCasesMatchEleventhAirTickAndPointTwoLandingGuard() {
    B4NoSlowState state = new B4NoSlowState();
    state.placement(false, 11, true);
    assertEquals(1, state.tick(true, true, B4NoSlowState.Kind.GROUND), 0);
    state.placement(false, 10, true);
    assertEquals(.3f, state.tick(true, true, B4NoSlowState.Kind.GROUND), 0);
    assertEquals(1, state.tick(true, false, B4NoSlowState.Kind.GROUND), 0);
    state.placement(true, 0, false);
    assertEquals(1, state.tick(true, true, B4NoSlowState.Kind.GROUND), 0);
    state.placement(false, 11, false);
    assertEquals(.3f, state.tick(true, true, B4NoSlowState.Kind.GROUND), 0);
    assertEquals(1, state.tick(false, true, B4NoSlowState.Kind.GROUND), 0);
    assertFalse(state.suppressJump());
  }

  @Test public void autoFireballThrowsOnSecondMotionAndAcceptsOnlyOwnNearbyExplosion() {
    B4LongJumpState state = new B4LongJumpState();
    assertFalse(state.throwNow());
    assertTrue(state.aiming());
    assertTrue(state.throwNow());
    assertFalse(state.aiming());
    assertFalse(state.throwNow());
    assertFalse(state.explosion(0));
    state.thrown(1000);
    assertFalse(state.explosion(9.001));
    assertFalse(state.timedOut(1750));
    assertTrue(state.explosion(9));
    assertFalse(state.boosting());
    assertTrue(state.advance());
    assertTrue(state.boosting());
    assertFalse(state.explosion(1));
    assertFalse(state.advance());
  }

  @Test public void fireballJumpDelayAndTimeoutUseDonorInclusiveBoundaries() {
    B4LongJumpState state = new B4LongJumpState();
    state.thrown(1000);
    assertFalse(state.jumpNow());
    assertTrue(state.jumpNow());
    assertFalse(state.jumpNow());
    assertFalse(state.timedOut(1750));
    assertTrue(state.timedOut(1751));
    assertTrue(state.timedOut(249));
  }

  @Test public void heldThreatsTriggerOnTransitionsAndRearmAfterPuttingItemAway() {
    B4BedWarsState state = new B4BedWarsState();
    UUID player = UUID.randomUUID();
    B4BedWarsState.Alert fireball = B4BedWarsState.Alert.FIREBALL;
    assertTrue(state.transition(1, player, fireball, true, 1000));
    assertFalse(state.transition(1, player, fireball, true, 4000));
    assertFalse(state.transition(1, player, fireball, false, 4000));
    assertTrue(state.transition(1, player, fireball, true, 4000));
    assertTrue(state.transition(1, player, B4BedWarsState.Alert.ENDERPEARL, true, 4000));
  }

  @Test public void alertThrottleIsPerPlayerPerThreatAndInclusiveAtTwoSeconds() {
    B4BedWarsState state = new B4BedWarsState();
    UUID a = UUID.randomUUID(), b = UUID.randomUUID();
    B4BedWarsState.Alert alert = B4BedWarsState.Alert.OBSIDIAN_PLACED;
    assertTrue(state.permit(a, alert, 1000));
    assertFalse(state.permit(a, alert, 3000));
    assertTrue(state.permit(a, alert, 3001));
    assertTrue(state.permit(b, alert, 3001));
    assertTrue(state.permit(a, B4BedWarsState.Alert.OBSIDIAN_HELD, 3001));
  }

  @Test public void permanentPurchasesStayFlaggedAndSuppressedTransitionsDoNotRetryWhileHeld() {
    B4BedWarsState state = new B4BedWarsState();
    UUID player = UUID.randomUUID();
    B4BedWarsState.Alert armor = B4BedWarsState.Alert.DIA_ARMOR;
    assertTrue(state.transition(2, player, armor, true, 1000));
    assertFalse(state.transition(2, player, armor, false, 4000));
    assertFalse(state.transition(2, player, armor, true, 4000));
    B4BedWarsState.Alert pearl = B4BedWarsState.Alert.ENDERPEARL;
    assertTrue(state.transition(2, player, pearl, true, 1000));
    assertFalse(state.transition(2, player, pearl, false, 1100));
    assertFalse(state.transition(2, player, pearl, true, 1200));
    assertFalse(state.transition(2, player, pearl, true, 4000));
    state.reset();
    assertTrue(state.transition(2, player, armor, true, 4000));
  }

  @Test public void magicMilkRunsSixHundredTicksThenFortyTicksOfFinishedAndResets() {
    B4BedWarsState state = new B4BedWarsState();
    state.milkFinished();
    for (int expected = 600; expected > 0; expected--) {
      assertEquals(expected, state.milkTicks());
      state.tickMilk();
    }
    assertEquals(-40, state.milkTicks());
    for (int i = 0; i < 40; i++) state.tickMilk();
    assertEquals(0, state.milkTicks());
    state.milkFinished();
    state.tickMilk();
    state.milkFinished();
    assertEquals(600, state.milkTicks());
    state.reset();
    assertEquals(0, state.milkTicks());
  }
}
