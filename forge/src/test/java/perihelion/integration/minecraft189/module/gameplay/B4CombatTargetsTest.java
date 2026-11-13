package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import org.junit.Test;

public class B4CombatTargetsTest {
  @Test public void finiteRangeUsesTheDonorFloatAnglesAndLookupTrigAtTheHitBoundary() {
    Vec3 eyes = new Vec3(0, 1.62, 0);
    AxisAlignedBB box = new AxisAlignedBB(1.5, 0, 2.5, 2.3, 2, 3.3);
    Vec3 direction = B4CombatTargets.direction(eyes, box, 0, 0, 0, 0);
    assertEquals(.5210752487182617, direction.xCoord, 0);
    assertEquals(9.587378590367734E-4, direction.yCoord, 0);
    assertEquals(.8535102009773254, direction.zCoord, 0);
    double range = 2.9290359577371596;
    assertNull(box.calculateIntercept(eyes, eyes.addVector(
        direction.xCoord * range, direction.yCoord * range, direction.zCoord * range)));
    // This mathematically normalized shortcut was the old bug: it falsely reports a hit.
    Vec3 normalized = new Vec3(1.567, .003, 2.567).normalize();
    assertNotNull(box.calculateIntercept(eyes, eyes.addVector(
        normalized.xCoord * range, normalized.yCoord * range, normalized.zCoord * range)));
  }

  @Test public void eyesInsideTheBoxUseTheVisibleAnglesInsteadOfSilentBaseAngles() {
    Vec3 eyes = new Vec3(0, 1, 0);
    AxisAlignedBB box = new AxisAlignedBB(-1, 0, -1, 1, 2, 1);
    Vec3 visible = B4CombatTargets.direction(eyes, box, 0, 0, 90, 0);
    Vec3 silent = B4CombatTargets.direction(eyes, box, 90, 0, 0, 0);
    assertEquals(-1, visible.xCoord, 0);
    assertEquals(1, silent.zCoord, 0);
    assertTrue(Math.abs(visible.zCoord) < .0001);
    assertTrue(Math.abs(silent.xCoord) < .0001);
  }
}
