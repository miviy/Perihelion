package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class ScaffoldPlacementSearchTest {
  @Test
  public void boundedSearchMatchesOrderedDonorCandidateGeneration() {
    for (int seed = 0; seed < 32; seed++) {
      Random random = new Random(seed);
      Set<String> blocks = new HashSet<>();
      for (int x = -6; x <= 6; x++)
        for (int y = -6; y <= 2; y++)
          for (int z = -6; z <= 6; z++) if (random.nextInt(12) == 0) blocks.add(key(x, y, z));
      ScaffoldPlacementSearch.Cells cells = (x, y, z) -> blocks.contains(key(x, y, z));
      for (int[] offset : new int[][] {{0, 0, 0}, {4, 0, -2}, {-3, -1, 2}})
        for (Integer plane : new Integer[] {null, -1, -3}) {
          int maxY = offset[1] - 1;
          int[] expected = reference(cells, offset, plane, maxY, offset[1] < 0);
          ScaffoldPlacementSearch.Candidate actual =
              ScaffoldPlacementSearch.find(
                  cells, offset[0], offset[1], offset[2], plane, maxY, offset[1] < 0);
          if (expected == null) assertNull(actual);
          else {
            assertNotNull(actual);
            assertArrayEquals("seed " + seed, expected, new int[] {actual.x, actual.y, actual.z});
          }
        }
    }
  }

  @Test
  public void wallCoverAndDownwardOnlyCeilingFacesAreExcludedIndependently() {
    Set<String> blocks = new HashSet<>(Arrays.asList(key(1, -1, 0), key(1, 0, 0)));
    ScaffoldPlacementSearch.Cells cells = (x, y, z) -> blocks.contains(key(x, y, z));
    assertFalse(ScaffoldPlacementSearch.hasFace(cells, 0, -1, 0, false));
    blocks.remove(key(1, 0, 0));
    assertTrue(ScaffoldPlacementSearch.hasFace(cells, 0, -1, 0, false));
    blocks.clear();
    blocks.add(key(0, 0, 0));
    assertFalse(ScaffoldPlacementSearch.hasFace(cells, 0, -1, 0, false));
    assertTrue(ScaffoldPlacementSearch.hasFace(cells, 0, -1, 0, true));
  }

  private static int[] reference(
      ScaffoldPlacementSearch.Cells cells,
      int[] offset,
      Integer plane,
      int maxY,
      boolean downwards) {
    List<int[]> candidates = new ArrayList<>();
    int[][] generated = {{-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}};
    int range = 6 + Math.abs(offset[0]) + Math.abs(offset[2]);
    for (int x = -range; x <= range; x++)
      for (int y = -range; y <= range; y++)
        for (int z = -range; z <= range; z++)
          if (cells.solid(x, y, z))
            for (int[] side : generated)
              candidates.add(new int[] {x + side[0], y + side[1], z + side[2]});
    candidates.removeIf(
        p ->
            p[0] * p[0] + p[1] * p[1] + p[2] * p[2] > 25
                || cells.solid(p[0], p[1], p[2])
                || plane != null && p[1] != plane
                || p[1] > maxY);
    candidates.sort(
        Comparator.comparingInt(
            p -> {
              int x = offset[0] - p[0], y = offset[1] - 1 - p[1], z = offset[2] - p[2];
              return x * x + y * y + z * z;
            }));
    for (int[] p : candidates) {
      for (int[] side : new int[][] {{0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}})
        if (cells.solid(p[0] + side[0], p[1], p[2] + side[2])
            && !cells.solid(p[0] + side[0], p[1] + 1, p[2] + side[2])) return p;
      if (cells.solid(p[0], p[1] - 1, p[2]) || downwards && cells.solid(p[0], p[1] + 1, p[2]))
        return p;
    }
    return null;
  }

  private static String key(int x, int y, int z) {
    return x + "," + y + "," + z;
  }
}
