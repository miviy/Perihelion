package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;

import java.util.Random;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import org.junit.Test;

public class RayBoxTest {
  @Test
  public void broadPhaseNeverRejectsAnExactGameIntersection() {
    Random random = new Random(8721);
    AxisAlignedBB cube = new AxisAlignedBB(-1, -1, -1, 1, 1, 1);
    for (int i = 0; i < 20000; i++) {
      Vec3 start =
          new Vec3(
              random.nextDouble() * 10 - 5,
              random.nextDouble() * 10 - 5,
              random.nextDouble() * 10 - 5);
      Vec3 end =
          new Vec3(
              random.nextDouble() * 10 - 5,
              random.nextDouble() * 10 - 5,
              random.nextDouble() * 10 - 5);
      if (cube.isVecInside(start) || cube.calculateIntercept(start, end) != null)
        assertTrue(
            RayBox.intersects(
                start.xCoord,
                start.yCoord,
                start.zCoord,
                end.xCoord - start.xCoord,
                end.yCoord - start.yCoord,
                end.zCoord - start.zCoord,
                -1,
                -1,
                -1,
                1,
                1,
                1));
    }
  }

  @Test
  public void handlesParallelInsideBoundaryAndUnreachableRays() {
    assertTrue(RayBox.intersects(.5, .5, .5, 0, 0, 0, 0, 0, 0, 1, 1, 1));
    assertTrue(RayBox.intersects(0, 0, -1, 0, 0, 1, 0, 0, 0, 1, 1, 1));
    assertFalse(RayBox.intersects(2, 0, -1, 0, 0, 2, 0, 0, 0, 1, 1, 1));
    assertFalse(RayBox.intersects(0, 0, -2, 0, 0, 1, 0, 0, 0, 1, 1, 1));
  }
}
