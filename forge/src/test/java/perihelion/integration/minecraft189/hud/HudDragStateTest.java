package perihelion.integration.minecraft189.hud;

import static org.junit.Assert.*;

import org.junit.Test;

public class HudDragStateTest {
  @Test
  public void grabOffsetIsPreservedAndMotionOnlyChangesTransientCoordinates() {
    HudDragState drag = new HudDragState();
    drag.begin(HudModule.Element.WATERMARK, 25, 38, 20, 30, 427, 240, 65, 65);
    assertEquals(20, drag.x);
    assertEquals(30, drag.y);
    drag.move(100, 110);
    assertEquals(95, drag.x);
    assertEquals(102, drag.y);
    assertEquals(HudModule.Element.WATERMARK, drag.element);
  }

  @Test
  public void dragClampsEveryEdgeAndOversizedPanelsRemainReachable() {
    HudDragState drag = new HudDragState();
    drag.begin(HudModule.Element.PLAYERS, 10, 10, 10, 10, 320, 240, 224, 100);
    drag.move(-99, -99);
    assertEquals(0, drag.x);
    assertEquals(0, drag.y);
    drag.move(999, 999);
    assertEquals(96, drag.x);
    assertEquals(140, drag.y);
    drag.begin(HudModule.Element.PLAYERS, 10, 10, 0, 0, 100, 80, 224, 100);
    drag.move(999, 999);
    assertEquals(0, drag.x);
    assertEquals(0, drag.y);
  }

  @Test
  public void cancellationPreventsAStaleDragFromResumingAfterResizeOrReopen() {
    HudDragState drag = new HudDragState();
    drag.begin(HudModule.Element.INFO, 10, 10, 2, 2, 427, 240, 100, 21);
    drag.move(60, 70);
    int x = drag.x, y = drag.y;
    drag.cancel();
    drag.move(100, 100);
    assertNull(drag.element);
    assertEquals(x, drag.x);
    assertEquals(y, drag.y);
    drag.begin(HudModule.Element.MODULES, 200, 20, 195, 15, 854, 480, 120, 80);
    drag.move(210, 25);
    assertEquals(205, drag.x);
    assertEquals(20, drag.y);
  }
}
