package perihelion.integration.minecraft189.module.gameplay;

/** Resumable volume traversal. Callers enforce a per-tick work budget, including initial scans. */
final class ScanCursor {
  private int radius, height, index;

  ScanCursor(int radius, int height) {
    reset(radius, height);
  }

  void reset(int radius, int height) {
    if (radius < 1 || radius > 48 || height < 1 || height > 32)
      throw new IllegalArgumentException("Scan bounds");
    this.radius = radius;
    this.height = height;
    index = 0;
  }

  int x() {
    return index % (radius * 2 + 1) - radius;
  }

  int y() {
    return index / ((radius * 2 + 1) * (radius * 2 + 1)) - height;
  }

  /** Visit the player's level first, then alternate above and below it. */
  int centeredY() {
    int row = y() + height;
    return (row + 1) / 2 * (row % 2 == 0 ? -1 : 1);
  }

  int z() {
    return index / (radius * 2 + 1) % (radius * 2 + 1) - radius;
  }

  void advance() {
    index = (index + 1) % volume();
  }

  int volume() {
    return (radius * 2 + 1) * (radius * 2 + 1) * (height * 2 + 1);
  }
}
