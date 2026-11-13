// SPDX-License-Identifier: AGPL-3.0-only
// Adapted from Yuri DisplaceModule, revision 1cb86ad94ce1f01aa9b715577163c8ecc47b7def.
package perihelion.integration.minecraft189.module.gameplay;

/**
 * Donor displacement math and bounded hold timing, independent of Minecraft and module ownership.
 */
final class DisplaceBehavior {
  private DisplaceBehavior() {}

  interface Ground {
    boolean present(double x, int y, double z);
  }

  static boolean cooldownReady(long now, long lastAt, double seconds) {
    return lastAt < 0 || now - lastAt >= (long) (seconds * 1000.0) - 20L;
  }

  static boolean canDisplace(
      boolean dead,
      boolean player,
      boolean playersOnly,
      double distance,
      double range,
      int hurtTime,
      int maxHurtTime,
      boolean sprinting,
      int knockback,
      boolean onlyKnockbackItems,
      boolean cooldownReady) {
    return !dead
        && (!playersOnly || player)
        && distance <= range
        && hurtTime <= maxHurtTime
        && (sprinting || knockback != 0)
        && (!onlyKnockbackItems || knockback != 0)
        && cooldownReady;
  }

  static float offset(boolean left, float angle, float randomUnit) {
    return (left ? -angle : angle) + randomUnit * 10.0f - 5.0f;
  }

  static float[] aim(
      double playerX,
      double playerY,
      double playerZ,
      float playerEye,
      float playerYaw,
      float playerPitch,
      double targetX,
      double targetY,
      double targetZ,
      float targetEye) {
    double x = targetX - playerX;
    double y = targetY + targetEye - 0.2 - (playerY + playerEye);
    double z = targetZ - playerZ;
    double distance = (float) Math.sqrt(x * x + z * z);
    float yaw = (float) (Math.atan2(z, x) * 180.0 / Math.PI) - 90.0f;
    float pitch = (float) -(Math.atan2(y, distance) * 180.0 / Math.PI);
    return new float[] {playerYaw + wrap(yaw - playerYaw), playerPitch + wrap(pitch - playerPitch)};
  }

  static int voidSide(
      double targetX, double targetY, double targetZ, float baseYaw, float angle, Ground ground) {
    for (int side = 0; side < 2; side++) {
      double rad = Math.toRadians(baseYaw + (side == 0 ? -angle : angle));
      double x = targetX - Math.sin(rad) * 2.0;
      double z = targetZ + Math.cos(rad) * 2.0;
      int startY = (int) Math.floor(targetY) - 1;
      boolean found = false;
      for (int y = startY; y >= Math.max(0, startY - 8); y--) {
        if (ground.present(x, y, z)) {
          found = true;
          break;
        }
      }
      if (!found) return side;
    }
    return -1;
  }

  private static float wrap(float angle) {
    angle %= 360;
    return angle >= 180 ? angle - 360 : angle < -180 ? angle + 360 : angle;
  }

  static final class Hold {
    private boolean armed, finishing;
    private int waitTicks;

    void arm() {
      armed = true;
      finishing = false;
      waitTicks = 0;
    }

    boolean hold(boolean valid, boolean badPackets) {
      if (!armed || !valid || badPackets || waitTicks >= 3) {
        reset();
        return false;
      }
      waitTicks++;
      return true;
    }

    void attacked() {
      if (!armed) return;
      armed = false;
      finishing = true;
    }

    boolean armed() {
      return armed;
    }

    boolean finishing() {
      return finishing;
    }

    boolean overriding() {
      return armed || finishing;
    }

    void reset() {
      armed = finishing = false;
      waitTicks = 0;
    }
  }

  static final class ArrowFrame {
    final double alpha, distance, bob;

    private ArrowFrame(double alpha, double distance, double bob) {
      this.alpha = alpha;
      this.distance = distance;
      this.bob = bob;
    }

    static ArrowFrame at(long age) {
      if (age > 600) return null;
      double t = clamp01(age / 100.0);
      double alpha = 0.08 + 0.92 * (t * t * (3.0 - 2.0 * t));
      if (age > 500) {
        double fade = clamp01((age - 500) / 100.0);
        alpha *= 1.0 - 0.92 * (fade * fade * (3.0 - 2.0 * fade));
      }
      double p = clamp01(age / 450.0);
      double inverse = 1.0 - p;
      double distance = 0.8 * (1.0 - inverse * inverse * inverse);
      double bob = Math.sin(age * 0.015) * 0.02 * (0.4 + 0.6 * alpha);
      return new ArrowFrame(alpha, distance, bob);
    }

    private static double clamp01(double value) {
      return value < 0 ? 0 : Math.min(value, 1);
    }
  }
}
