package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;

import org.junit.Test;

public class FreeLookAnglesTest {
  @Test
  public void mouseSampleInterpolatesAcrossYawWrapWithoutFlippingInitialPitch() {
    FreeLookAngles a = new FreeLookAngles(179, 30);
    a.mouse(20, 20, true);
    assertEquals(179, a.yaw(0), 0);
    assertEquals(182, a.yaw(1), .001);
    assertEquals(180.5, a.yaw(.5f), .001);
    assertEquals(30, a.pitch(0), 0);
    assertEquals(31.5, a.pitch(.5f), .001);
  }

  @Test
  public void samplesHaveTheirOwnPreviousAnglesAndPitchClampsBothWays() {
    FreeLookAngles a = new FreeLookAngles(0, 89);
    a.mouse(10, 100, true);
    assertEquals(90, a.pitch(1), 0);
    a.mouse(-10, 2000, false);
    assertEquals(90, a.pitch(0), 0);
    assertEquals(-90, a.pitch(1), 0);
    assertEquals(0, a.pitch(.5f), 0);
    assertEquals(1.5, a.yaw(0), .001);
    assertEquals(0, a.yaw(1), .001);
  }
}
