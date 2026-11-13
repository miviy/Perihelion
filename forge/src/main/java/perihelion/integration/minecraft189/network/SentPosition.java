package perihelion.integration.minecraft189.network;

/** Immutable scalar snapshots of accepted positional movement, safe to publish from raw threads. */
public final class SentPosition {
  public static final class Point {
    public final double x, y, z;

    public Point(double x, double y, double z) {
      if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
        throw new IllegalArgumentException("Position must be finite");
      this.x = x;
      this.y = y;
      this.z = z;
    }
  }

  private volatile Point point;

  public void record(double x, double y, double z) {
    if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)) point = new Point(x, y, z);
  }

  public Point point() {
    return point;
  }

  public void reset() {
    point = null;
  }
}
