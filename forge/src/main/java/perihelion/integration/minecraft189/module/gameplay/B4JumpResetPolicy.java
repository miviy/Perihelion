package perihelion.integration.minecraft189.module.gameplay;

/** OpenB4 u20's sprint-jump benefit calculation, using the received horizontal velocity. */
final class B4JumpResetPolicy {
  private B4JumpResetPolicy() {}

  static boolean acceptsVerticalVelocity(int motionY) {
    // OpenB4's shared knockback hook filters before dispatching to Jump Reset.
    return motionY > 0 && motionY < 4500;
  }

  static boolean helps(double x, double z, float movementYaw) {
    if (x == 0 && z == 0) return false;
    double before = Math.sqrt(x * x + z * z);
    double radians = Math.toRadians(B4AimPolicy.wrap(movementYaw));
    double nextX = x - Math.sin(radians) * .2;
    double nextZ = z + Math.cos(radians) * .2;
    double after = Math.sqrt(nextX * nextX + nextZ * nextZ);
    return (before - after) / before >= .03;
  }
}
