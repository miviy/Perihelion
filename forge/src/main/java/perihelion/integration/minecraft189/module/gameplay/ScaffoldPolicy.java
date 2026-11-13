package perihelion.integration.minecraft189.module.gameplay;

/** Pure timing/height rules shared by Scaffold's input and final placement passes. */
final class ScaffoldPolicy {
  private ScaffoldPolicy() {}

  static boolean diagonal(float cameraYaw, float forward, float strafe) {
    if (forward == 0 && strafe == 0) return false;
    double yaw = (cameraYaw + Math.toDegrees(Math.atan2(-strafe, forward))) % 90;
    if (yaw < 0) yaw += 90;
    return yaw > 20 && yaw < 70;
  }

  /** Project the player's actual position into the block grid, including negative cells. */
  static int[] expansionOffset(double x, double z, double direction, int distance) {
    double dx = -Math.sin(direction), dz = Math.cos(direction);
    if (Math.abs(dx) < 1e-10) dx = 0;
    if (Math.abs(dz) < 1e-10) dz = 0;
    return new int[] {
      (int) Math.floor(x + dx * distance) - (int) Math.floor(x),
      (int) Math.floor(z + dz * distance) - (int) Math.floor(z)
    };
  }

  static double hypixelRotationSpeed(boolean speedPotion, boolean diagonal, boolean jumping) {
    return speedPotion ? diagonal ? 10 : 7 : diagonal || jumping ? 6.75f : 4.8f;
  }

  static boolean resetHeight(
      double y,
      double startY,
      boolean jumping,
      boolean grounded,
      boolean justLanded,
      boolean moving,
      boolean hypixel) {
    return jumping
        || hypixel && grounded
        || grounded
            && (y < startY || y > startY && justLanded || Math.abs(y - startY) > .5 && !moving);
  }

  static boolean autoJump(
      boolean enabled, boolean jumping, boolean grounded, boolean moving, boolean speedEnabled) {
    return enabled && !jumping && grounded && moving && !speedEnabled;
  }

  static boolean hypixelTower(boolean jumpPotion, boolean fullBlock) {
    return !jumpPotion && fullBlock;
  }

  static double hypixelTowerAdjustment(int air, boolean moving, double speed) {
    return moving || speed != 0 ? 0 : air == 4 ? -.03 : air == 5 ? -.5 : 0;
  }
}
