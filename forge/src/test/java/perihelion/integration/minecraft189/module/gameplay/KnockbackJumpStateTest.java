package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;

import org.junit.Test;

public class KnockbackJumpStateTest {
  @Test
  public void freshKnockbackHasExactlyOneInputOpportunity() {
    KnockbackJumpState state = new KnockbackJumpState();
    state.receive(1000, true);
    assertTrue(state.consume(1050));
    assertFalse(state.consume(1050));
    assertFalse(state.consume(1100));
  }

  @Test
  public void stalledTicksCannotCauseAJumpAfterLandingOrClosingAMenu() {
    KnockbackJumpState state = new KnockbackJumpState();
    state.receive(1000, true);
    assertFalse(state.consume(1151));
    state.receive(2000, false);
    assertFalse(state.consume(2050));
  }

  @Test
  public void latestPacketReplacesTheCandidateAndNeverQueuesMultipleJumps() {
    KnockbackJumpState state = new KnockbackJumpState();
    state.receive(1000, true);
    state.receive(1001, false);
    assertFalse(state.consume(1002));
    state.receive(2000, true);
    state.receive(2001, true);
    assertTrue(state.consume(2002));
    assertFalse(state.consume(2003));
  }

  @Test
  public void settingResetAndReactivationCannotReusePendingDamage() {
    KnockbackJumpState state = new KnockbackJumpState();
    state.receive(1000, true);
    state.clear();
    assertFalse(state.consume(1001));
    assertFalse(new KnockbackJumpState().consume(1001));
  }

  @Test
  public void rejectsBackwardsTimeAndAcceptsLastFreshMillisecond() {
    KnockbackJumpState state = new KnockbackJumpState();
    state.receive(1000, true);
    assertFalse(state.consume(999));
    state.receive(1000, true);
    assertTrue(state.consume(1150));
  }
}
