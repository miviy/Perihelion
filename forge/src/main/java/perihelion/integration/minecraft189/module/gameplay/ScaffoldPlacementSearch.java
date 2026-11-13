package perihelion.integration.minecraft189.module.gameplay;

/** Bounded equivalent of Yuri's support-generated, stably distance-sorted candidate list. */
final class ScaffoldPlacementSearch {
  interface Cells {
    boolean solid(int x, int y, int z);
  }

  // Neighbor order is the donor's north, south, west, east, below, above order.
  static final int[][] NEIGHBORS = {
    {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}
  };

  static final class Candidate {
    final int x, y, z;

    Candidate(int x, int y, int z) {
      this.x = x;
      this.y = y;
      this.z = z;
    }
  }

  private ScaffoldPlacementSearch() {}

  static Candidate find(
      Cells cells,
      int offsetX,
      int offsetY,
      int offsetZ,
      Integer plane,
      int maxY,
      boolean downwards) {
    Candidate best = null;
    long bestDistance = Long.MAX_VALUE;
    int bestOrder = Integer.MAX_VALUE;
    // Every donor candidate is at most five units from the player. Its generating
    // support is therefore within six; the donor's larger expand cube adds no result.
    for (int x = -5; x <= 5; x++)
      for (int y = -5; y <= Math.min(5, maxY); y++)
        for (int z = -5; z <= 5; z++) {
          if (x * x + y * y + z * z > 25
              || plane != null && y != plane
              || cells.solid(x, y, z)
              || !hasFace(cells, x, y, z, downwards)) continue;
          long dx = offsetX - x, dy = offsetY - 1L - y, dz = offsetZ - z;
          long distance = dx * dx + dy * dy + dz * dz;
          if (distance > bestDistance) continue;
          int order = generationOrder(cells, x, y, z);
          if (distance < bestDistance || order < bestOrder) {
            best = new Candidate(x, y, z);
            bestDistance = distance;
            bestOrder = order;
          }
        }
    return best;
  }

  static boolean hasFace(Cells cells, int x, int y, int z, boolean downwards) {
    for (int i = 0; i < 4; i++) {
      int[] side = NEIGHBORS[i];
      if (cells.solid(x + side[0], y, z + side[2]) && !cells.solid(x + side[0], y + 1, z + side[2]))
        return true;
    }
    return cells.solid(x, y - 1, z) || downwards && cells.solid(x, y + 1, z);
  }

  private static int generationOrder(Cells cells, int x, int y, int z) {
    int order = Integer.MAX_VALUE;
    for (int i = 0; i < NEIGHBORS.length; i++) {
      int[] side = NEIGHBORS[i];
      int sx = x + side[0], sy = y + side[1], sz = z + side[2];
      if (!cells.solid(sx, sy, sz)) continue;
      // Candidate generation order is x-,x+,y-,y+,z-,z+ from each support.
      int generatedSide = i == 0 ? 5 : i == 1 ? 4 : i == 2 ? 1 : i == 3 ? 0 : i == 4 ? 3 : 2;
      order = Math.min(order, (((sx + 6) * 13 + sy + 6) * 13 + sz + 6) * 6 + generatedSide);
    }
    return order;
  }
}
