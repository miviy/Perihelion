package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;

import org.junit.Test;

public class ScaffoldPolicyTest {
  @Test
  public void diagonalsFollowWorldMovementRatherThanTheStrafeKey() {
    assertTrue(ScaffoldPolicy.diagonal(0, 1, -1));
    assertFalse(ScaffoldPolicy.diagonal(45, 1, -1));
    assertFalse(ScaffoldPolicy.diagonal(0, 0, -1));
    assertTrue(ScaffoldPolicy.diagonal(45, 0, -1));
    assertFalse(ScaffoldPolicy.diagonal(45, 0, 0));
    for (int yaw = -720; yaw <= 720; yaw += 90) {
      assertTrue(ScaffoldPolicy.diagonal(yaw, 1, 1));
      assertFalse(ScaffoldPolicy.diagonal(yaw + 45, -1, 1));
    }
  }

  @Test
  public void expansionCrossesDiagonalCellBoundariesAndMirrorsAcrossNegativeCoordinates() {
    assertArrayEquals(new int[] {-1, 1}, ScaffoldPolicy.expansionOffset(.5, .5, Math.PI / 4, 1));
    assertArrayEquals(new int[] {1, 1}, ScaffoldPolicy.expansionOffset(.5, .5, -Math.PI / 4, 1));
    assertArrayEquals(new int[] {-1, 1}, ScaffoldPolicy.expansionOffset(-.5, -.5, Math.PI / 4, 1));
    assertArrayEquals(new int[] {0, 0}, ScaffoldPolicy.expansionOffset(.9, .1, Math.PI / 4, 1));
    assertArrayEquals(new int[] {0, -1}, ScaffoldPolicy.expansionOffset(0, 0, Math.PI, 1));
  }

  @Test
  public void keepHeightResetsAtLandingAndDropsButNotEveryGroundedStep() {
    assertFalse(ScaffoldPolicy.resetHeight(65, 64, false, true, false, true, false));
    assertTrue(ScaffoldPolicy.resetHeight(65, 64, false, true, true, true, false));
    assertTrue(ScaffoldPolicy.resetHeight(63.9, 64, false, true, false, true, false));
    assertFalse(ScaffoldPolicy.resetHeight(64.5, 64, false, true, false, false, false));
    assertTrue(ScaffoldPolicy.resetHeight(64.51, 64, false, true, false, false, false));
    assertTrue(ScaffoldPolicy.resetHeight(65, 64, false, true, false, true, true));
  }

  @Test
  public void jumpAndTowerRespectPotionSupportAndStationaryAirTicks() {
    assertFalse(ScaffoldPolicy.autoJump(true, false, true, true, true));
    assertTrue(ScaffoldPolicy.autoJump(true, false, true, true, false));
    assertFalse(ScaffoldPolicy.hypixelTower(true, true));
    assertFalse(ScaffoldPolicy.hypixelTower(false, false));
    assertTrue(ScaffoldPolicy.hypixelTower(false, true));
    assertEquals(-.03, ScaffoldPolicy.hypixelTowerAdjustment(4, false, 0), 0);
    assertEquals(-.5, ScaffoldPolicy.hypixelTowerAdjustment(5, false, 0), 0);
    assertEquals(0, ScaffoldPolicy.hypixelTowerAdjustment(5, false, .001), 0);
    assertEquals(0, ScaffoldPolicy.hypixelTowerAdjustment(5, true, 0), 0);
  }
}
