package perihelion.integration.minecraft189.module;

import static org.junit.Assert.*;

import org.junit.Test;

public class B4MovementCorrectionTest {
  @Test
  public void quarterTurnMapsForwardToALegalStrafeAxis() {
    assertArrayEquals(new float[] {1, 0}, B4MovementCorrection.align(0, 1, 0, 90), 0);
    assertArrayEquals(new float[] {-1, 0}, B4MovementCorrection.align(0, 1, 0, -90), 0);
  }

  @Test
  public void halfTurnChangesTheForwardSignBeforeSprintEligibility() {
    assertArrayEquals(new float[] {0, -1}, B4MovementCorrection.align(0, 1, 0, 180), 0);
    assertArrayEquals(new float[] {0, 1}, B4MovementCorrection.align(0, -1, 0, 180), 0);
  }

  @Test
  public void diagonalCorrectionUsesFullDigitalAxesRatherThanAnalogTrigonometry() {
    assertArrayEquals(new float[] {1, 1}, B4MovementCorrection.align(0, 1, 0, 45), 0);
    assertArrayEquals(new float[] {.3F, -.3F}, B4MovementCorrection.align(.2F, .3F, 0, 90), 0);
  }

  @Test
  public void exactHalfSectorTiesRetainTheFirstSourceLoopCandidate() {
    assertArrayEquals(new float[] {0, 1}, B4MovementCorrection.align(0, 1, 0, 22.5F), 0);
    assertArrayEquals(new float[] {-1, 1}, B4MovementCorrection.align(0, 1, 0, -22.5F), 0);
    assertArrayEquals(new float[] {1, 1}, B4MovementCorrection.align(0, 1, 0, 22.500002F), 0);
  }

  @Test
  public void slowedInputKeepsItsSampledMagnitude() {
    assertArrayEquals(new float[] {.3F, 0}, B4MovementCorrection.align(0, .3F, 0, 90), 0);
    assertArrayEquals(new float[] {.2F, 0}, B4MovementCorrection.align(0, .2F, 0, 90), 0);
    assertArrayEquals(new float[] {0, -.3F}, B4MovementCorrection.align(.3F, 0, 0, 90), 0);
  }

  @Test
  public void wrappedCameraAndServerYawChooseTheSameAxes() {
    assertArrayEquals(new float[] {1, 0}, B4MovementCorrection.align(0, 1, 720, -630), 0);
  }

  @Test
  public void emptyInputRemainsEmpty() {
    assertArrayEquals(new float[] {0, 0}, B4MovementCorrection.align(0, 0, 10, 123), 0);
  }

  @Test(expected = IllegalArgumentException.class)
  public void nonfiniteInputIsRejectedAtTheNativeBoundary() {
    B4MovementCorrection.align(Float.NaN, 1, 0, 0);
  }
}
