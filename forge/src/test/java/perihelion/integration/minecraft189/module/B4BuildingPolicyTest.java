package perihelion.integration.minecraft189.module;

import static org.junit.Assert.*;
import static perihelion.integration.minecraft189.module.B4BuildingPolicy.Mining.*;

import org.junit.Test;

public class B4BuildingPolicyTest {
  @Test
  public void preSeedsOnlyStartedIncompleteDamageBelowTheThreshold() {
    assertEquals(0, B4BuildingPolicy.mining(PRE, 0, 0, .1F, 2), 0);
    assertEquals(.5, B4BuildingPolicy.mining(PRE, .1F, 0, .1F, 2), 0);
    assertEquals(.75, B4BuildingPolicy.mining(PRE, .75F, 0, .1F, 2), 0);
    assertEquals(1, B4BuildingPolicy.mining(PRE, 1, 0, .1F, 2), 0);
  }

  @Test
  public void postCompletesAtTheInclusiveReciprocalThreshold() {
    assertEquals(.49F, B4BuildingPolicy.mining(POST, .49F, 0, .1F, 2), 0);
    assertEquals(1, B4BuildingPolicy.mining(POST, .5F, 0, .1F, 2), 0);
    assertEquals(.4F, B4BuildingPolicy.mining(POST, .4F, 0, .1F, 1), 0);
  }

  @Test
  public void incrementUsesThePreviousUnmodifiedSampleAndSourceScaling() {
    assertEquals(.085694286, B4BuildingPolicy.mining(INCREMENT, .1F, .05F, .02F, 2), 1E-7);
    assertEquals(.1F, B4BuildingPolicy.mining(INCREMENT, .1F, .1F, .02F, 2), 0);
    assertEquals(.02F, B4BuildingPolicy.mining(INCREMENT, .02F, .1F, .02F, 2), 0);
    assertEquals(.1F, B4BuildingPolicy.mining(INCREMENT, .1F, .05F, .02F, 1), 0);
  }

  @Test
  public void stackChoiceMatchesDescendingHotbarAndRequiresAGreaterStack() {
    int[] counts = {1, 16, 64, 64, 0, 0, 0, 0, 64};
    boolean[] allowed = {true, true, true, true, false, false, false, false, true};
    assertEquals(8, B4BuildingPolicy.blockStack(0, 1, counts, allowed));
    allowed[8] = allowed[3] = false;
    assertEquals(2, B4BuildingPolicy.blockStack(0, 1, counts, allowed));
    assertEquals(-1, B4BuildingPolicy.blockStack(2, 64, counts, allowed));
    assertEquals(-1, B4BuildingPolicy.blockStack(0, 1, counts, new boolean[9]));
  }

  @Test
  public void projectionIncludesMotionAndNormalizesDiagonalInput() {
    assertArrayEquals(
        new double[] {.03, .118}, B4BuildingPolicy.projectedMotion(.03, .02, 1, 0, .1F, 0), 1E-6);
    assertArrayEquals(
        new double[] {-.098, 0}, B4BuildingPolicy.projectedMotion(0, 0, 1, 0, .1F, 90), 1E-6);
    assertArrayEquals(
        new double[] {.07071066, .07071066},
        B4BuildingPolicy.projectedMotion(0, 0, 1, 1, .1F, 0),
        1E-6);
    assertArrayEquals(
        new double[] {.3, -.2}, B4BuildingPolicy.projectedMotion(.3, -.2, 0, 0, .1F, 0), 0);
  }
}
