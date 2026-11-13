package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;
import net.minecraft.util.Vec3;
import org.junit.Test;

public class B4MovementMathTest {
  @Test public void pickupRayRetainsTheDonorFloatLookupVectorAtAnObliqueLandingAim() {
    // Independent u20 RotationUtils lookup-table vector; a double trig ray differs on all axes.
    Vec3 ray = B4MovementSupport.direction(31.5f, 81);
    assertEquals(-.08171188086271286, ray.xCoord, 0);
    assertEquals(-.9876793622970581, ray.yCoord, 0);
    assertEquals(.13335314393043518, ray.zCoord, 0);
    assertTrue(Math.abs(ray.xCoord + Math.sin(Math.toRadians(31.5)) * Math.cos(Math.toRadians(81))) > 1e-6);
  }

  @Test public void fireballDownRayAndBoostRadiansRetainSourceFloatBoundaries() {
    Vec3 ray = B4MovementSupport.direction(0, 90);
    assertEquals(0, ray.xCoord, 0);
    assertEquals(-1, ray.yCoord, 0);
    assertEquals(0, ray.zCoord, 0);
    assertEquals(1.5707963705062866, (double) B4MovementSupport.radians(90), 0);
    assertNotEquals(Math.PI / 2, (double) B4MovementSupport.radians(90), 0);
    ray = B4MovementSupport.direction(179.9f, -19.25f);
    assertEquals(-.0017197740962728858, ray.xCoord, 0);
    assertEquals(.3296584486961365, ray.yCoord, 0);
    assertEquals(-.944098711013794, ray.zCoord, 0);
  }
}
