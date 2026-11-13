package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;

import org.junit.Test;
import perihelion.core.control.Rotation;

public class RotationDynamicsTest {
  @Test
  public void movementKeysCoverAllCombinationsIncludingOpposedKeys() {
    float[] expected = {0, 0, 180, 0, 270, 315, 225, 315, 90, 45, 135, 45, 0, 0, 180, 0};
    for (int mask = 0; mask < 16; mask++)
      assertEquals(
          "keys " + mask,
          expected[mask],
          RotationDynamics.movementYaw(
              0, (mask & 1) != 0, (mask & 2) != 0, (mask & 4) != 0, (mask & 8) != 0),
          .001);
    assertEquals(35, RotationDynamics.movementYaw(350, true, false, false, true), .001);
    assertEquals(305, RotationDynamics.movementYaw(-10, true, false, true, false), .001);
  }

  @Test
  public void diagonalTurningUsesOneVectorBudget() {
    Rotation angle = RotationDynamics.step(Rotation.server(0, 0), Rotation.server(60, 80), 50);
    assertEquals(30, angle.yaw(), .001);
    assertEquals(40, angle.pitch(), .001);
    assertEquals(50, Math.hypot(angle.yaw(), angle.pitch()), .001);
  }

  @Test
  public void wrappedTurningTakesTheShortPathAndSettledAnglesStayFinite() {
    Rotation a = RotationDynamics.step(Rotation.server(170, 10), Rotation.server(-170, 10), 10);
    assertEquals(180, a.yaw(), .001);
    assertEquals(10, a.pitch(), .001);
    Rotation b = RotationDynamics.step(a, a, 180);
    assertTrue(Float.isFinite(b.yaw()));
    assertEquals(a.pitch(), b.pitch(), 0);
  }

  @Test
  public void sensitivityRoundingUsesReportedAnglesAcrossTheWrap() {
    Rotation angle =
        RotationDynamics.quantize(
            Rotation.serverUnwrapped(180.3f, 25.24f), Rotation.serverUnwrapped(179, 25), .5f);
    assertEquals(180.35, angle.yaw(), .001);
    assertEquals(25.3, angle.pitch(), .001);
    assertEquals(
        90,
        RotationDynamics.quantize(Rotation.server(0, 90), Rotation.server(0, 89.92f), .5f).pitch(),
        0);
  }

  @Test
  public void continuousMultiTurnYawDoesNotRewrapDuringStepOrSensitivityPatch() {
    Rotation step =
        RotationDynamics.step(Rotation.serverUnwrapped(720, 5), Rotation.server(3, 5), 2);
    assertEquals(722, step.yaw(), .001);
    assertEquals(
        721.95, RotationDynamics.quantize(step, Rotation.serverUnwrapped(720, 5), .5f).yaw(), .001);
  }

  @Test
  public void defaultYuriBaseSpeedUsesTheNoArgumentPotionScaling() {
    assertEquals(.2873, GameActions.baseSpeed(0), 1e-12);
    assertEquals(.333268, GameActions.baseSpeed(1), 1e-12);
    assertEquals(.379236, GameActions.baseSpeed(2), 1e-12);
  }

  @Test
  public void yuriTargetMathKeepsTheGameAtan2AndFloatConversionBeforeQuantization() {
    for (double[] vector :
        new double[][] {{1.7, -.23, 4.1}, {-3.4, 2.2, .17}, {.003, -9, -.006}, {-5, 0, -7}}) {
      float degrees = 180f / (float) Math.PI;
      Rotation actual = GameActions.yuriAngle(vector[0], vector[1], vector[2]);
      float yaw =
          (float) (net.minecraft.util.MathHelper.atan2(vector[2], vector[0]) * degrees) - 90;
      assertEquals(Rotation.server(yaw, 0).yaw(), actual.yaw(), 0);
      assertEquals(
          (float)
              -(net.minecraft.util.MathHelper.atan2(vector[1], Math.hypot(vector[0], vector[2]))
                  * degrees),
          actual.pitch(),
          0);
    }
    assertNotEquals(
        (float) Math.toDegrees(Math.atan2(1, 1)) - 90, GameActions.yuriAngle(1, 0, 1).yaw(), 0);
  }

  @Test
  public void randomizedSensitivityCastsOnlyAfterTheCompleteSourceExpression() {
    float sensitivity = .07f;
    double jitter = .1;
    float delta = .00850349385291338f;
    float scale = (float) (sensitivity * (1 + jitter / 10000000) * .6f + .2f);
    double unit = scale * scale * scale * 8f * .15;
    assertEquals(
        (float) unit,
        RotationDynamics.quantize(
                Rotation.server(delta, 0), Rotation.server(0, 0), sensitivity, jitter)
            .yaw(),
        0);
    float earlyScale = (float) (sensitivity * (1 + jitter / 10000000)) * .6f + .2f;
    double earlyUnit = earlyScale * earlyScale * earlyScale * 8f * .15;
    assertEquals(0, Math.round(delta / earlyUnit));
    assertEquals(1, Math.round(delta / unit));
  }

  @Test
  public void sensitivityOriginCannotShiftTheSelectedHeadingByAWholeTurn() {
    Rotation selected = Rotation.serverUnwrapped(-15, 20);
    Rotation patched = RotationDynamics.quantize(selected, Rotation.serverUnwrapped(170, 0), .5f);
    assertTrue(Math.abs(patched.yaw() - selected.yaw()) < .1);
    assertTrue(Math.abs(patched.pitch() - selected.pitch()) < .1);
  }
}
