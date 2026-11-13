package perihelion.integration.minecraft189.hud;

import static org.junit.Assert.*;

import java.util.Arrays;
import org.junit.Test;

public class HudArrayShapeTest {
  private HudArrayShape shape(boolean left, float radius, int padding) {
    return new HudArrayShape(
        Arrays.asList(
            new HudRenderer.ModuleRow("First", 120),
            new HudRenderer.ModuleRow("Second", 80),
            new HudRenderer.ModuleRow("Third", 50)),
        120,
        14,
        padding,
        0,
        left,
        radius);
  }

  @Test
  public void squareSteppedRowsShareAnEdgeWithoutGaps() {
    HudArrayShape shape = shape(false, 0, 0);
    assertEquals(120, shape.width, 0);
    assertEquals(42, shape.height, 0);
    assertEquals(0, shape.edge(0, 14, false), 0);
    assertEquals(40, shape.edge(1, 0, false), 0);
    assertEquals(120, shape.edge(0, 14, true), 0);
    assertEquals(120, shape.edge(1, 0, true), 0);
    assertEquals(70, shape.edge(2, 0, false), 0);
  }

  @Test
  public void roundingOnlyRetractsOuterCornersAndLeavesAdjacentRowsConnected() {
    HudArrayShape shape = shape(false, 4, 0);
    assertEquals(4, shape.edge(0, 0, false), .01);
    assertEquals(116, shape.edge(0, 0, true), .01);
    assertEquals(120, shape.edge(0, 14, true), 0);
    assertEquals(120, shape.edge(1, 0, true), 0);
    assertTrue(shape.edge(0, 14, false) < shape.edge(1, 0, false));
    assertEquals(116, shape.edge(2, 14, true), .01);
  }

  @Test
  public void concaveJoinsRoundAsPartOfOneOutlineInsteadOfSeparateRowPills() {
    HudArrayShape shape = shape(false, 4, 0);
    float join = shape.edge(1, 0, false);
    assertTrue(join >= 36 && join < 40);
    assertEquals(40, shape.edge(1, 7, false), .01);
    assertEquals(120, shape.edge(1, 0, true), .01);
    assertTrue(shape.edge(0, 14, false) < join);
  }

  @Test
  public void leftAlignmentAndOuterPaddingKeepTheSameConnectedSilhouette() {
    HudArrayShape shape = shape(true, 0, 3);
    assertEquals(126, shape.width, 0);
    assertEquals(48, shape.height, 0);
    assertEquals(0, shape.edge(0, 17, false), 0);
    assertEquals(0, shape.edge(1, 0, false), 0);
    assertEquals(126, shape.edge(0, 17, true), 0);
    assertEquals(86, shape.edge(1, 0, true), 0);
  }
}
