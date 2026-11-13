package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;

import org.junit.Test;

public class HitSelectStateTest {
  @Test
  public void pauseBoundaryAndCriticalAscentAreIndependent() {
    HitSelectState s = new HitSelectState();
    assertFalse(s.hold(100, 1, 8, 100, 0, false, false, false, false));
    assertTrue(s.hold(100, 1, 9, 100, 0, false, false, false, false));
    assertTrue(s.hold(100, 1, 0, 0, 0, false, false, true, false));
    assertFalse(s.hold(100, 1, 10, 500, 0, true, false, true, false));
    assertFalse(s.hold(100, 1, 10, 500, 0, false, true, true, false));
  }

  @Test
  public void forceFirstIsPerTargetAndExpiresAfterTenSeconds() {
    HitSelectState s = new HitSelectState();
    assertFalse(s.hold(100, 1, 10, 50, 0, false, false, false, true));
    assertTrue(s.hold(101, 1, 10, 50, 0, false, false, false, true));
    assertFalse(s.hold(102, 2, 10, 50, 0, false, false, false, true));
    assertTrue(s.hold(10101, 1, 10, 50, 0, false, false, false, true));
    assertFalse(s.hold(20102, 1, 10, 50, 0, false, false, false, true));
  }

  @Test
  public void hurtEdgeCountsOnceAndSwapAndTimeoutResetCombo() {
    HitSelectState s = new HitSelectState();
    s.attacked(1, 100, true);
    s.tick(150, 0, 0, 10, 10, 10, true, true, true, false);
    assertEquals(1, s.combo());
    s.tick(151, 0, 0, 10, 10, 10, true, true, true, false);
    assertEquals(1, s.combo());
    assertFalse(s.hold(160, 1, 10, 50, 2, false, false, false, false));
    s.tick(200, 1, 0, 10, 9, 10, true, true, true, false);
    assertEquals(0, s.combo());
    s.attacked(1, 300, true);
    s.tick(350, 1, 0, 10, 10, 10, true, true, false, false);
    assertEquals(1, s.combo());
    s.tick(1551, 1, 0, 10, 0, 10, true, true, false, false);
    assertEquals(0, s.combo());
  }

  @Test
  public void damageEscapeHasExactBoundaryAndFreshActivationHasNoHistory() {
    HitSelectState s = new HitSelectState();
    s.tick(100, 0, 10, 10, 0, 10, false, true, false, true);
    assertFalse(s.hold(269, 1, 10, 50, 0, false, false, false, false));
    assertTrue(s.hold(270, 1, 10, 50, 0, false, false, false, false));
    assertTrue(new HitSelectState().hold(100, 1, 10, 50, 0, false, false, false, false));
  }

  @Test
  public void staleHurtOutsideAttackWindowCannotCount() {
    HitSelectState s = new HitSelectState();
    s.attacked(1, 100, true);
    s.tick(850, 0, 0, 10, 10, 10, true, true, false, false);
    assertEquals(0, s.combo());
  }
}
