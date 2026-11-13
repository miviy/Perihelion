// SPDX-License-Identifier: AGPL-3.0-only
// Source behavior: Yuri 1cb86ad94ce1f01aa9b715577163c8ecc47b7def TargetStrafe/MoveUtils.
package perihelion.integration.minecraft189.module.gameplay;

import java.util.function.IntPredicate;
import net.minecraft.util.MathHelper;
import perihelion.integration.minecraft189.event.MovementYawEvent;
import perihelion.integration.minecraft189.event.StrafeEvent;

/** Source orbit geometry and collision-edge motion without global target/control ownership. */
final class TargetStrafePolicy {
  private TargetStrafePolicy() {}

  static final class State {
    boolean enabled, left, colliding;
    float yaw;

    void apply(StrafeEvent event) {
      if (enabled) event.yaw(yaw);
    }

    void apply(MovementYawEvent event) {
      if (enabled) event.yaw(yaw);
    }

    boolean collision(boolean value) {
      boolean edge = value && !colliding;
      if (edge) left = !left;
      colliding = value;
      return edge;
    }
  }

  static boolean fullBlockBelow(IntPredicate fullBlockAtOffset) {
    for (int offset = 0; offset < 5; offset++) if (fullBlockAtOffset.test(offset)) return true;
    return false;
  }

  static double[] collisionMotion(double x, double z, double radians) {
    // Source MoveUtils.strafe() recomputes speed after assigning motionX.
    x = -MathHelper.sin((float) radians) * Math.hypot(x, z);
    z = MathHelper.cos((float) radians) * Math.hypot(x, z);
    return new double[] {x, z};
  }

  static float orbitYaw(
      double x, double z, double targetX, double targetZ, boolean left, double range) {
    float yaw = sourceYaw(targetX - x, targetZ - z) + 135 * (left ? -1 : 1);
    double orbitX = -MathHelper.sin((float) Math.toRadians(yaw)) * range + targetX;
    double orbitZ = MathHelper.cos((float) Math.toRadians(yaw)) * range + targetZ;
    return sourceYaw(orbitX - x, orbitZ - z);
  }

  private static float sourceYaw(double x, double z) {
    return (float) (MathHelper.atan2(z, x) * (180f / (float) Math.PI)) - 90f;
  }
}
