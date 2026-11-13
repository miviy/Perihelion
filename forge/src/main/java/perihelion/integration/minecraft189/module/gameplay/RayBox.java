package perihelion.integration.minecraft189.module.gameplay;

/** Allocation-free broad phase. The game's exact intersection remains authoritative. */
final class RayBox {
  private RayBox() {}

  static boolean intersects(
      double x,
      double y,
      double z,
      double dx,
      double dy,
      double dz,
      double minX,
      double minY,
      double minZ,
      double maxX,
      double maxY,
      double maxZ) {
    double near = 0, far = 1;
    for (int axis = 0; axis < 3; axis++) {
      double start = axis == 0 ? x : axis == 1 ? y : z;
      double delta = axis == 0 ? dx : axis == 1 ? dy : dz;
      double min = (axis == 0 ? minX : axis == 1 ? minY : minZ) - 1e-7;
      double max = (axis == 0 ? maxX : axis == 1 ? maxY : maxZ) + 1e-7;
      if (delta == 0) {
        if (start < min || start > max) return false;
      } else {
        double first = (min - start) / delta, last = (max - start) / delta;
        near = Math.max(near, Math.min(first, last));
        far = Math.min(far, Math.max(first, last));
        if (near > far) return false;
      }
    }
    return true;
  }
}
