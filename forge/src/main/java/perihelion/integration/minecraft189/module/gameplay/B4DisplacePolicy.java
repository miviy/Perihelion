package perihelion.integration.minecraft189.module.gameplay;

/** B4 u20's weighted six-block drop-side rays, including behind-side and tie rejection. */
final class B4DisplacePolicy {
  interface Support {
    boolean present(double x, double y, double z);
  }

  private B4DisplacePolicy() {}

  static int side(
      double playerX,
      double playerZ,
      double targetX,
      double targetY,
      double targetZ,
      int previous,
      Support support) {
    double dx = targetX - playerX, dz = targetZ - playerZ;
    double length = Math.sqrt(dx * dx + dz * dz);
    if (length < .001) return -1;
    dx /= length;
    dz /= length;
    double px = -dz, pz = dx;
    int minus = 0, plus = 0, behind = 0;
    for (int n : new int[] {1, 2, 3, 4, 6, 8}) {
      int weight = n < 6 ? 2 : 1;
      for (int i = -1; i <= 1; i++) {
        if (!support.present(targetX + dx * n + px * i, targetY, targetZ + dz * n + pz * i))
          behind += weight;
        if (!support.present(targetX - px * n + dx * i, targetY, targetZ - pz * n + dz * i))
          minus += weight;
        if (!support.present(targetX + px * n + dx * i, targetY, targetZ + pz * n + dz * i))
          plus += weight;
      }
    }
    if (minus == 0 && plus == 0 || behind >= minus && behind >= plus) return -1;
    if (minus == plus && previous != -1) return previous;
    return minus > plus ? 0 : 1;
  }
}
