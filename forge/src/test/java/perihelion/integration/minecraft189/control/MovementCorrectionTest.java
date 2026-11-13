package perihelion.integration.minecraft189.control;

import static org.junit.Assert.*;

import org.junit.Test;
import perihelion.integration.minecraft189.event.StrafeEvent;

public class MovementCorrectionTest {
  @Test
  public void preservesSlowAndDiagonalAccelerationAcrossYawWraps() {
    for (float forward : new float[] {0, .2f, .3f, .98f, -.3f, -1})
      for (float strafe : new float[] {0, .2f, .3f, .98f, -.3f, -1})
        for (int view = -360; view <= 360; view += 30)
          for (int server = -180; server <= 180; server += 15) {
            double[] before = acceleration(strafe, forward, view);
            StrafeEvent event = new StrafeEvent(strafe, forward, .1f, view);
            MovementCorrection.align(event, view, server);
            assertArrayEquals(
                before, acceleration(event.strafe(), event.forward(), event.yaw()), 1e-7);
            assertEquals(.1f, event.friction(), 0);
          }
  }

  @Test
  public void quarterSpeedForwardNeverRoundsToZero() {
    StrafeEvent event = new StrafeEvent(0, .3f, .1f, 0);
    MovementCorrection.align(event, 0, 90);
    assertEquals(.3f, event.strafe(), 1e-6);
    assertEquals(0f, event.forward(), 1e-6);
  }

  private static double[] acceleration(float strafe, float forward, float yaw) {
    double scale = .1 / Math.max(1, Math.sqrt(strafe * strafe + forward * forward));
    double sin = Math.sin(Math.toRadians(yaw)), cos = Math.cos(Math.toRadians(yaw));
    return new double[] {
      (strafe * cos - forward * sin) * scale, (forward * cos + strafe * sin) * scale
    };
  }
}
