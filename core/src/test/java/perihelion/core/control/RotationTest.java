package perihelion.core.control;

import static org.junit.Assert.*;

import org.junit.Test;

public class RotationTest {
  @Test
  public void continuousServerYawPreservesTheSubmittedMouseStep() {
    assertEquals(180.5f, Rotation.serverUnwrapped(180.5f, 12).yaw(), 0);
    assertEquals(725.25f, Rotation.serverUnwrapped(725.25f, 12).yaw(), 0);
    assertEquals(-725.25f, Rotation.serverUnwrapped(-725.25f, 12).yaw(), 0);
    assertEquals(Rotation.Mode.SERVER, Rotation.serverUnwrapped(180.5f, 12).mode());
    assertEquals(-179.5f, Rotation.server(180.5f, 12).yaw(), 0);
    assertEquals(-179.5f, Rotation.view(180.5f, 12).yaw(), 0);
  }

  @Test
  public void continuousFactoryRetainsFiniteAndPitchValidation() {
    for (float yaw : new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
      try {
        Rotation.serverUnwrapped(yaw, 0);
        fail("nonfinite yaw accepted");
      } catch (IllegalArgumentException expected) {
      }
    }
    for (float pitch : new float[] {Float.NaN, -90.01f, 90.01f}) {
      try {
        Rotation.serverUnwrapped(720, pitch);
        fail("invalid pitch accepted");
      } catch (IllegalArgumentException expected) {
      }
    }
  }
}
