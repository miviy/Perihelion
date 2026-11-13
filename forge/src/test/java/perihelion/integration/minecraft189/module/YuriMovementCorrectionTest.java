package perihelion.integration.minecraft189.module;

import static org.junit.Assert.*;

import org.junit.Test;

public class YuriMovementCorrectionTest {
  @Test
  public void quarterAndHalfTurnsChooseLegalInputBeforeSprintChecks() {
    assertArrayEquals(new float[] {1, 0}, YuriMovementCorrection.align(0, 1, 0, 90), 0);
    assertArrayEquals(new float[] {0, -1}, YuriMovementCorrection.align(0, 1, 0, 180), 0);
    assertArrayEquals(new float[] {-1, 0}, YuriMovementCorrection.align(0, 1, 0, 270), 0);
  }

  @Test
  public void diagonalInputStaysDigitalAndSneakMagnitudeSurvivesThePostInputBoundary() {
    assertArrayEquals(new float[] {1, 1}, YuriMovementCorrection.align(0, 1, 0, 45), 0);
    assertArrayEquals(new float[] {.3f, .3f}, YuriMovementCorrection.align(0, .3f, 0, 45), 0);
  }

  @Test
  public void unwrappedAnglesProduceTheSameDirectionsWithoutMovingAnIdlePlayer() {
    assertArrayEquals(
        YuriMovementCorrection.align(-1, 1, 10, 150),
        YuriMovementCorrection.align(-1, 1, 730, -570),
        0);
    assertArrayEquals(new float[] {0, 0}, YuriMovementCorrection.align(0, 0, 170, 45), 0);
  }

  @Test
  public void sourceLoopRetainsTheFirstExactAngularTie() {
    assertArrayEquals(new float[] {0, 1}, YuriMovementCorrection.align(0, 1, 0, 22.5f), 0);
    assertArrayEquals(new float[] {1, 0}, YuriMovementCorrection.align(0, 1, 0, 67.5f), 0);
  }

  @Test(expected = IllegalArgumentException.class)
  public void nonfiniteYawIsRejected() {
    YuriMovementCorrection.align(0, 1, 0, Float.NaN);
  }
}
