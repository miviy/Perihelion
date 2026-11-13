package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class ScanCursorTest {
  @Test
  public void IncrementalBatchesCoverEachCellOnceBeforeWrapping() {
    ScanCursor scan = new ScanCursor(4, 2);
    Set<String> visited = new HashSet<>();
    for (int i = 0; i < scan.volume(); i++) {
      assertTrue(Math.abs(scan.x()) <= 4);
      assertTrue(Math.abs(scan.z()) <= 4);
      assertTrue(Math.abs(scan.y()) <= 2);
      assertTrue(visited.add(scan.x() + ":" + scan.y() + ":" + scan.z()));
      scan.advance();
    }
    assertEquals(405, visited.size());
    assertEquals(-4, scan.x());
    assertEquals(-2, scan.y());
    assertEquals(-4, scan.z());
  }

  @Test
  public void CenteredLayersPrioritizeNearbyBedsWithoutSkippingCells() {
    ScanCursor scan = new ScanCursor(2, 2);
    int[] expected = {0, 1, -1, 2, -2};
    Set<String> visited = new HashSet<>();
    for (int layer : expected) {
      for (int i = 0; i < 25; i++) {
        assertEquals(layer, scan.centeredY());
        assertTrue(visited.add(scan.x() + ":" + scan.centeredY() + ":" + scan.z()));
        scan.advance();
      }
    }
    assertEquals(scan.volume(), visited.size());
    assertEquals(0, scan.centeredY());
  }

  @Test
  public void ResizeRestartsAndRejectsUnboundedVolumes() {
    ScanCursor scan = new ScanCursor(48, 32);
    scan.advance();
    scan.reset(2, 1);
    assertEquals(-2, scan.x());
    assertEquals(-1, scan.y());
    try {
      scan.reset(Integer.MAX_VALUE, 1);
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }
}
