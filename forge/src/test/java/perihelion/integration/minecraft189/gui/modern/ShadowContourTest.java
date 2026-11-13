package perihelion.integration.minecraft189.gui.modern;

import static org.junit.Assert.*;

import org.junit.Test;

public class ShadowContourTest {
  @Test
  public void completeShadowContoursAndTheirChordsStayOutsideTheLiveRoundedPanel() {
    float x = 17.3f, y = 9.1f, width = 110, height = 63;
    for (float requested : new float[] {0, 4, 15, 90}) {
      float radius = ShadowContour.radius(width, height, requested);
      for (float distance : new float[] {0, .75f, 3, 6}) {
        for (int point = 0; point < ShadowContour.POINTS - 1; point++) {
          float ax = ShadowContour.x(x, width, radius, point, distance);
          float ay = ShadowContour.y(y, height, radius, point, distance);
          float bx = ShadowContour.x(x, width, radius, point + 1, distance);
          float by = ShadowContour.y(y, height, radius, point + 1, distance);
          for (int step = 0; step <= 10; step++) {
            float amount = step / 10f;
            double actual = signedDistance(
                ax + (bx - ax) * amount, ay + (by - ay) * amount,
                x, y, width, height, radius);
            assertTrue("Shadow chord entered the panel: " + actual, actual >= distance - .0001);
            assertTrue("Shadow contour has an oversized gap", actual < distance + .1);
          }
        }
      }
    }
  }

  @Test
  public void squarePanelsHaveExactInnerCornersAndClosedContours() {
    for (float requested : new float[] {0, -4}) {
      float radius = ShadowContour.radius(110, 22, requested);
      assertEquals(0, radius, 0);
      for (int point : new int[] {0, 13, 26, 39}) {
        float x = ShadowContour.x(10, 110, radius, point, 0);
        float y = ShadowContour.y(20, 22, radius, point, 0);
        assertTrue(x == 10 || x == 120);
        assertTrue(y == 20 || y == 42);
      }
      assertEquals(
          ShadowContour.x(10, 110, radius, 0, 6),
          ShadowContour.x(10, 110, radius, ShadowContour.POINTS - 1, 6), 0);
      assertEquals(
          ShadowContour.y(20, 22, radius, 0, 6),
          ShadowContour.y(20, 22, radius, ShadowContour.POINTS - 1, 6), 0);
    }
  }

  @Test
  public void radiusClampsToTheLiveBoundsWhileAFrameIsCollapsing() {
    assertEquals(4, ShadowContour.radius(110, 80, 4), 0);
    assertEquals(11, ShadowContour.radius(110, 22, 100), 0);
    assertEquals(.25f, ShadowContour.radius(110, .5f, 5), 0);
  }

  private static double signedDistance(
      double px, double py, double x, double y, double width, double height, double radius) {
    double dx = Math.abs(px - x - width / 2) - (width / 2 - radius);
    double dy = Math.abs(py - y - height / 2) - (height / 2 - radius);
    return Math.hypot(Math.max(dx, 0), Math.max(dy, 0))
        + Math.min(Math.max(dx, dy), 0) - radius;
  }
}
