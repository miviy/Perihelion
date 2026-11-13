package perihelion.integration.minecraft189.module;

import static org.junit.Assert.*;

import org.junit.Test;

public class ForwardTapCycleTest {
  @Test
  public void delayedReleaseHasABoundedEndAndCannotRetriggerDuringCooldown() {
    ForwardTapCycle cycle = new ForwardTapCycle();
    cycle.attack(1000, 275, 75);
    assertFalse(cycle.update(1274, true));
    assertTrue(cycle.update(1275, true));
    cycle.attack(1300, 0, 5000);
    assertTrue(cycle.update(1349, true));
    assertFalse(cycle.update(1350, true));
    cycle.attack(1400, 0, 50);
    assertFalse(cycle.update(1401, true));
    cycle.attack(1500, 0, 50);
    assertTrue(cycle.update(1500, true));
  }

  @Test
  public void ReleaseAndActivationCleanupDiscardPendingWork() {
    ForwardTapCycle cycle = new ForwardTapCycle();
    cycle.attack(100, 200, 50);
    assertFalse(cycle.update(200, false));
    assertFalse(cycle.update(310, true));
    cycle.attack(700, 0, 100);
    cycle.clear();
    assertFalse(cycle.update(710, true));
  }
}
