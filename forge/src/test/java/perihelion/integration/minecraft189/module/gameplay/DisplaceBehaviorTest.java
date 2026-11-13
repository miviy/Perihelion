package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class DisplaceBehaviorTest {
  @Test
  public void requiresActualKnockbackAndHonorsTheOptionalItemRestriction() {
    assertFalse(eligible(false, 0, false));
    assertTrue(eligible(true, 0, false));
    assertTrue(eligible(false, 1, false));
    assertFalse(eligible(true, 0, true));
    assertTrue(eligible(false, 1, true));
    assertFalse(DisplaceBehavior.canDisplace(false, false, true, 3, 3, 3, 3, true, 1, false, true));
    assertFalse(
        DisplaceBehavior.canDisplace(false, true, true, 3.01, 3, 3, 3, true, 1, false, true));
    assertFalse(DisplaceBehavior.canDisplace(false, true, true, 3, 3, 4, 3, true, 1, false, true));
  }

  private static boolean eligible(boolean sprinting, int knockback, boolean itemOnly) {
    return DisplaceBehavior.canDisplace(
        false, true, true, 3, 3, 3, 3, sprinting, knockback, itemOnly, true);
  }

  @Test
  public void cooldownBeginsWhenArmedAndKeepsTheDonorTwentyMillisecondAllowance() {
    assertTrue(DisplaceBehavior.cooldownReady(0, -1, 5));
    assertFalse(DisplaceBehavior.cooldownReady(1979, 1000, 1));
    assertTrue(DisplaceBehavior.cooldownReady(1980, 1000, 1));
    assertTrue(DisplaceBehavior.cooldownReady(1000, 1000, 0));
    assertFalse(DisplaceBehavior.cooldownReady(5979, 1000, 5));
    assertTrue(DisplaceBehavior.cooldownReady(5980, 1000, 5));
  }

  @Test
  public void yawJitterIsAppliedAfterTheChosenSide() {
    assertEquals(-50, DisplaceBehavior.offset(true, 45, 0), 0);
    assertEquals(-45, DisplaceBehavior.offset(true, 45, .5f), 0);
    assertEquals(40, DisplaceBehavior.offset(false, 45, 0), 0);
    assertEquals(50, DisplaceBehavior.offset(false, 45, 1), 0);
  }

  @Test
  public void aimUsesEyesMinusPointTwoAndWrapsFromTheCurrentCamera() {
    float[] straight = DisplaceBehavior.aim(0, 64, 0, 1.6f, 350, 0, 0, 64, 3, 1.6f);
    assertEquals(360, straight[0], .0001);
    assertEquals(Math.toDegrees(Math.atan2(.2, 3)), straight[1], .0001);
    float[] right = DisplaceBehavior.aim(0, 64, 0, 1.6f, 179, 0, 3, 64, 0, 1.6f);
    assertEquals(270, right[0], .0001);
  }

  @Test
  public void voidSelectionChecksLeftFirstTwoBlocksAwayAndAtMostNineVerticalCells() {
    List<Integer> visited = new ArrayList<>();
    int side =
        DisplaceBehavior.voidSide(
            0,
            64,
            0,
            0,
            45,
            (x, y, z) -> {
              assertEquals(Math.sqrt(2), Math.abs(x), .000001);
              assertEquals(Math.sqrt(2), z, .000001);
              visited.add(y);
              return x > 0 && y == 55;
            });
    assertEquals(1, side);
    assertEquals(18, visited.size());
    assertEquals(Integer.valueOf(63), visited.get(0));
    assertEquals(Integer.valueOf(55), visited.get(8));
    assertEquals(Integer.valueOf(63), visited.get(9));
    assertEquals(0, DisplaceBehavior.voidSide(0, 64, 0, 0, 45, (x, y, z) -> false));
    assertEquals(-1, DisplaceBehavior.voidSide(0, 64, 0, 0, 45, (x, y, z) -> true));
  }

  @Test
  public void voidSearchStopsAtZeroAndDoesNotAcceptGroundBelowItsEightBlockWindow() {
    List<Integer> visited = new ArrayList<>();
    assertEquals(
        0,
        DisplaceBehavior.voidSide(
            0,
            3,
            0,
            0,
            45,
            (x, y, z) -> {
              visited.add(y);
              return false;
            }));
    assertEquals(java.util.Arrays.asList(2, 1, 0), visited);
    assertEquals(0, DisplaceBehavior.voidSide(0, 64, 0, 0, 45, (x, y, z) -> y == 54));
  }

  @Test
  public void holdAllowsThreeRenewalsThenReleasesEvenWithoutAnAttack() {
    DisplaceBehavior.Hold hold = new DisplaceBehavior.Hold();
    hold.arm();
    for (int i = 0; i < 3; i++) assertTrue(hold.hold(true, false));
    assertFalse(hold.hold(true, false));
    assertFalse(hold.overriding());
    hold.arm();
    assertFalse(hold.hold(false, false));
    hold.arm();
    assertFalse(hold.hold(true, true));
  }

  @Test
  public void acceptedHitRetainsTheOverrideUntilPostMotionCleanup() {
    DisplaceBehavior.Hold hold = new DisplaceBehavior.Hold();
    hold.arm();
    hold.attacked();
    assertFalse(hold.armed());
    assertTrue(hold.finishing());
    assertTrue(hold.overriding());
    hold.reset();
    assertFalse(hold.overriding());
    hold.attacked();
    assertFalse(hold.finishing());
  }

  @Test
  public void arrowPreservesDonorFadeTravelAndExpiryBoundaries() {
    DisplaceBehavior.ArrowFrame start = DisplaceBehavior.ArrowFrame.at(0);
    assertEquals(.08, start.alpha, .000001);
    assertEquals(0, start.distance, 0);
    assertEquals(1, DisplaceBehavior.ArrowFrame.at(100).alpha, .000001);
    assertEquals(.8, DisplaceBehavior.ArrowFrame.at(450).distance, .000001);
    assertEquals(1, DisplaceBehavior.ArrowFrame.at(500).alpha, .000001);
    assertEquals(.54, DisplaceBehavior.ArrowFrame.at(550).alpha, .000001);
    assertEquals(.08, DisplaceBehavior.ArrowFrame.at(600).alpha, .000001);
    assertNull(DisplaceBehavior.ArrowFrame.at(601));
  }
}
