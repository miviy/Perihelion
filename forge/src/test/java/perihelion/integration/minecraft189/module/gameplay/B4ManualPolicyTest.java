package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;

import org.junit.Test;

public class B4ManualPolicyTest {
  @Test
  public void snapKeepsContinuousYawAcrossTheWrapAndMultipleTurns() {
    float[] crossing = B4AimPolicy.step(179.9f, 0, -179.5f, 0, 100, 180, 0, .5f, 0);
    assertTrue(crossing[0] > 180);
    assertTrue(Math.abs(crossing[0] - 179.9f) < 1);
    assertEquals(
        crossing[0],
        perihelion.core.control.Rotation.serverUnwrapped(crossing[0], crossing[1]).yaw(),
        0);
    float[] turns = B4AimPolicy.step(725, 0, 10, 0, 100, 180, 0, .5f, 0);
    assertTrue(turns[0] > 725 && turns[0] < 731);
  }

  @Test
  public void silentDisplayNameDoesNotChangeSavedModeToken() {
    perihelion.core.setting.Setting<AimAssistModule.AimMode> setting =
        perihelion.core.setting.Setting.enumeration(
            "mode",
            "Mode",
            "",
            AimAssistModule.AimMode.SIMPLE,
            AimAssistModule.AimMode.class,
            () -> {});
    assertEquals(AimAssistModule.AimMode.B4_SILENT, setting.parse("b4_silent"));
    assertTrue(setting.metadata().options().contains("b4_silent"));
    assertEquals(
        "Silent",
        perihelion.integration.minecraft189.gui.GuiValues.optionLabel(setting, "b4_silent"));
  }

  @Test
  public void donorKnockbackHookExcludesZeroDownwardAndLargeVerticalVelocity() {
    assertFalse(B4JumpResetPolicy.acceptsVerticalVelocity(-1));
    assertFalse(B4JumpResetPolicy.acceptsVerticalVelocity(0));
    assertTrue(B4JumpResetPolicy.acceptsVerticalVelocity(1));
    assertTrue(B4JumpResetPolicy.acceptsVerticalVelocity(4499));
    assertFalse(B4JumpResetPolicy.acceptsVerticalVelocity(4500));
    assertFalse(B4JumpResetPolicy.acceptsVerticalVelocity(32767));
  }

  @Test
  public void jumpingMustActuallyCounterTheReceivedVelocity() {
    assertTrue(B4JumpResetPolicy.helps(0, -.4, 0));
    assertFalse(B4JumpResetPolicy.helps(0, .4, 0));
    assertTrue(B4JumpResetPolicy.helps(.4, 0, 90));
    assertTrue(B4JumpResetPolicy.helps(.4, 0, 1170));
    assertFalse(B4JumpResetPolicy.helps(-.4, 0, 90));
    assertFalse(B4JumpResetPolicy.helps(0, 0, 0));
    // The jump impulse can overshoot a very small opposing velocity and make it worse.
    assertFalse(B4JumpResetPolicy.helps(0, -.05, 0));
    assertTrue(B4JumpResetPolicy.helps(0, -.102, 0));
    assertFalse(B4JumpResetPolicy.helps(0, -.101, 0));
  }

  @Test
  public void sourceSpeedUsesQuadraticRateAndOneSharedYawPitchDivisor() {
    float[] angle = B4AimPolicy.step(0, 0, 90, 45, 50, 120, 0, .5f, 0);
    assertEquals(16.05f, angle[0], .00001);
    assertEquals(8.1f, angle[1], .00001);
    float[] moreNear = B4AimPolicy.step(0, 0, 90, 45, 50, 120, 2, .5f, 0);
    assertEquals(22.05f, moreNear[0], .00001);
    assertEquals(11.1f, moreNear[1], .00001);
  }

  @Test
  public void silentAimUsesReportedOriginWrapAndBoundedSensitivityJitter() {
    float[] wrapped = B4AimPolicy.step(179, 0, -179, 0, 50, 120, 0, .5f, 0);
    assertEquals(180.5f, wrapped[0], .00001);
    float[] snap = B4AimPolicy.step(0, 0, -90, 100, 100, 120, 0, .5f, 3);
    assertEquals(-89.55f, snap[0], .00001);
    assertEquals(90, snap[1], 0);
    float[] still = B4AimPolicy.step(10, 20, 10, 20, 50, 120, 2, .5f, 3);
    assertArrayEquals(new float[] {10, 20}, still, 0);
  }

  @Test
  public void dropSideUsesBothLateralRegionsAndRejectsAForwardDrop() {
    assertEquals(0, B4DisplacePolicy.side(0, 0, 0, 64, 3, -1, (x, y, z) -> x <= .5));
    assertEquals(1, B4DisplacePolicy.side(0, 0, 0, 64, 3, -1, (x, y, z) -> x >= -.5));
    assertEquals(-1, B4DisplacePolicy.side(0, 0, 0, 64, 3, -1, (x, y, z) -> z <= 3));
    assertEquals(-1, B4DisplacePolicy.side(0, 0, 0, 64, 3, -1, (x, y, z) -> true));
    assertEquals(-1, B4DisplacePolicy.side(0, 0, 0, 64, 3, -1, (x, y, z) -> false));
    assertEquals(-1, B4DisplacePolicy.side(0, 0, 0, 64, 0, -1, (x, y, z) -> false));
  }

  @Test
  public void equalLateralScoresKeepTheLastSideRatherThanOscillate() {
    assertEquals(0, B4DisplacePolicy.side(0, 0, 0, 64, 3, 0, (x, y, z) -> Math.abs(x) < .5));
    assertEquals(1, B4DisplacePolicy.side(0, 0, 0, 64, 3, 1, (x, y, z) -> Math.abs(x) < .5));
    assertEquals(1, B4DisplacePolicy.side(0, 0, 0, 64, 3, -1, (x, y, z) -> Math.abs(x) < .5));
  }
}
