package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.util.MathHelper;
import org.junit.Test;
import perihelion.integration.minecraft189.event.MovementYawEvent;
import perihelion.integration.minecraft189.event.StrafeEvent;

public class TargetStrafePolicyTest {
  @Test
  public void partialSupportDoesNotCountAndOnlyOffsetsZeroThroughFourCount() {
    // A collidable slab/fence still returns false from the source isFullBlock check.
    assertFalse(TargetStrafePolicy.fullBlockBelow(offset -> false));
    for (int selected = 0; selected < 5; selected++) {
      final int fullBlock = selected;
      assertTrue(TargetStrafePolicy.fullBlockBelow(offset -> offset == fullBlock));
    }
    List<Integer> visited = new ArrayList<>();
    assertFalse(
        TargetStrafePolicy.fullBlockBelow(
            offset -> {
              visited.add(offset);
              return offset == 5;
            }));
    assertEquals(Arrays.asList(0, 1, 2, 3, 4), visited);
  }

  @Test
  public void aSustainedCollisionStrafesAndFlipsOnlyAtItsFirstEdge() {
    TargetStrafePolicy.State state = new TargetStrafePolicy.State();
    int redirects = 0;
    double x = .3, z = .4;
    for (boolean collision : new boolean[] {false, true, true, true, false, true}) {
      if (state.collision(collision)) {
        double[] moved = TargetStrafePolicy.collisionMotion(x, z, 0);
        x = moved[0];
        z = moved[1];
        redirects++;
      }
    }
    assertEquals(2, redirects);
    assertFalse(state.left);
    assertEquals(0, x, 0);
    assertEquals(.4, z, 0); // A cached original speed would produce .5 instead.
  }

  @Test
  public void sourceOrbitUsesGameAtan2AndFloatLookupTrigForBothSides() {
    double[][] positions = {
      {0, 0, 2.718, 3.141}, {300000, -200000, 300001.1, -200003.9}, {-2.2, .6, -5.3, -.17}
    };
    for (double[] p : positions)
      for (boolean left : new boolean[] {false, true}) {
        float target =
            (float) (MathHelper.atan2(p[3] - p[1], p[2] - p[0]) * (180f / (float) Math.PI)) - 90f;
        float orbit = target + (90 + 45) * (left ? -1 : 1);
        double px = -MathHelper.sin((float) Math.toRadians(orbit)) * 1.007 + p[2];
        double pz = MathHelper.cos((float) Math.toRadians(orbit)) * 1.007 + p[3];
        float expected =
            (float) (MathHelper.atan2(pz - p[1], px - p[0]) * (180f / (float) Math.PI)) - 90f;
        assertEquals(
            Float.floatToIntBits(expected),
            Float.floatToIntBits(TargetStrafePolicy.orbitYaw(p[0], p[1], p[2], p[3], left, 1.007)));
      }
  }

  @Test
  public void losingMovementActivationImmediatelyStopsOverridingEveryYawConsumer() {
    TargetStrafePolicy.State state = new TargetStrafePolicy.State();
    state.enabled = true;
    state.yaw = -112.25f;
    StrafeEvent active = new StrafeEvent(0, 1, .1f, 71);
    MovementYawEvent activeJump = new MovementYawEvent(71, true);
    state.apply(active);
    state.apply(activeJump);
    assertEquals(-112.25f, active.yaw(), 0);
    assertEquals(-112.25f, activeJump.yaw(), 0);
    state.enabled = false;
    StrafeEvent next = new StrafeEvent(0, 1, .1f, 71);
    MovementYawEvent helper = new MovementYawEvent(71);
    MovementYawEvent jump = new MovementYawEvent(71, true);
    state.apply(next);
    state.apply(helper);
    state.apply(jump);
    assertEquals(71f, next.yaw(), 0);
    assertEquals(71f, helper.yaw(), 0);
    assertEquals(71f, jump.yaw(), 0);
  }
}
