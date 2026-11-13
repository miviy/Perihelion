package perihelion.integration.minecraft189.module.gameplay;

/** Client-thread match state. Acquisition stays anchored even while chunks are unavailable. */
final class HomeBedState {
  static final int RANGE = 16;

  static final class Position {
    final int x, y, z;

    Position(int x, int y, int z) {
      this.x = x;
      this.y = y;
      this.z = z;
    }

    double distanceSq(Position other) {
      double dx = x - (double) other.x, dy = y - (double) other.y, dz = z - (double) other.z;
      return dx * dx + dy * dy + dz * dz;
    }
  }

  private Position anchor, foot, head;
  private long revision;

  void reset() {
    anchor = foot = head = null;
    revision++;
  }

  void anchor(Position position) {
    if (anchor == null) anchor = position;
  }

  Position anchor() { return anchor; }
  Position foot() { return foot; }
  boolean acquired() { return foot != null; }
  long revision() { return revision; }

  boolean capture(Position foot, Position head) {
    if (acquired() || anchor == null || foot == null || head == null
        || foot.distanceSq(anchor) > RANGE * RANGE
        || foot.y != head.y || foot.distanceSq(head) != 1) return false;
    this.foot = foot;
    this.head = head;
    revision++;
    return true;
  }

  boolean protects(Position position) {
    return acquired()
        && (position.distanceSq(foot) <= 2.25 || position.distanceSq(head) <= 2.25);
  }
}
