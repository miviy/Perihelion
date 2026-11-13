package perihelion.integration.minecraft189.gui.modern;

import static org.junit.Assert.*;

import org.junit.Test;

public class DragSwayTest {
  @Test
  public void changingDragDirectionReversesTheLeanWithinItsSmallLimit() {
    DragSway sway = new DragSway();
    for (int i = 0; i < 12; i++) {
      sway.move(20, 0);
      sway.advance(1 / 60f, true);
      assertTrue(sway.angle < 0 && sway.angle >= -1.6f);
    }
    for (int i = 0; i < 24; i++) {
      sway.move(-20, 0);
      sway.advance(1 / 60f, true);
      assertTrue(Math.abs(sway.angle) <= 1.6f);
    }
    assertTrue(sway.angle > 0);
  }

  @Test
  public void theSameMotionHasTheSameLeanAtDifferentRefreshRates() {
    DragSway slow = new DragSway(), fast = new DragSway();
    for (int i = 0; i < 12; i++) {
      slow.move(240 * .2f / 12, 0);
      slow.advance(.2f / 12, true);
    }
    for (int i = 0; i < 48; i++) {
      fast.move(240 * .2f / 48, 0);
      fast.advance(.2f / 48, true);
    }
    assertEquals(slow.angle, fast.angle, .0001f);
  }

  @Test
  public void holdingStillOrReleasingAlwaysComesToACompleteRest() {
    for (boolean held : new boolean[] {false, true}) {
      DragSway sway = new DragSway();
      for (int i = 0; i < 16; i++) {
        sway.move(8, 0);
        sway.advance(1 / 60f, true);
      }
      for (int i = 0; i < 60; i++) {
        sway.advance(1 / 60f, held);
        assertTrue(Math.abs(sway.angle) <= 1.6f);
      }
      assertEquals(0, sway.angle, 0);
      sway.advance(1, held);
      assertEquals(0, sway.angle, 0);
    }
  }

  @Test
  public void verticalMovementHasARestrainedDirectionalResponseAndResetCancelsIt() {
    DragSway sway = new DragSway();
    for (int i = 0; i < 12; i++) {
      sway.move(0, 4);
      sway.advance(1 / 60f, true);
    }
    assertTrue(sway.angle > 0 && sway.angle < 1.4f);
    sway.reset();
    assertEquals(0, sway.angle, 0);
    for (int i = 0; i < 12; i++) {
      sway.move(0, -4);
      sway.advance(1 / 60f, true);
    }
    assertTrue(sway.angle < 0 && sway.angle > -1.4f);
  }
}
