package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;

import org.junit.Test;

public class TickBaseTimingTest {
  @Test
  public void sourceFloatBandIncludesItsUpperEdgeAndExcludesLower() {
    float seek = 4.2f;
    assertEquals(8, new TickBaseTiming().update(false, false, seek + .15, 20), 0);
    assertEquals(1, new TickBaseTiming().update(false, false, Math.nextUp(seek + .15), 20), 0);
    assertEquals(1, new TickBaseTiming().update(false, false, seek - .55, 20), 0);
    assertEquals(8, new TickBaseTiming().update(false, false, Math.nextUp(seek - .55), 20), 0);
  }

  @Test
  public void burstRepaysEvenAfterMovingOutOfBand() {
    TickBaseTiming s = new TickBaseTiming();
    assertEquals(8, s.update(false, false, 4d, 3), 0);
    assertEquals(.1f, s.update(false, false, 2d, 3), 0);
    assertEquals(.55f, s.update(false, false, 4d, 3), 0);
    assertEquals(.775f, s.update(false, false, 4d, 3), 0);
    assertEquals(.8875f, s.update(false, false, 4d, 3), 1e-6);
    assertEquals(8, s.update(false, false, 4d, 3), 0);
  }

  @Test
  public void auraDisabledPausesCooldownWhileTimerReturnsToNormal() {
    TickBaseTiming s = new TickBaseTiming();
    s.update(true, true, 4d, 2);
    s.update(true, true, 4d, 2);
    for (int i = 0; i < 10; i++) s.update(true, false, null, 2);
    assertEquals(1, s.update(true, true, 4d, 2), 0);
    assertTrue(s.update(true, true, 4d, 2) <= 1);
    assertEquals(8, s.update(true, true, 4d, 2), 0);
  }

  @Test
  public void targetLossRetainsThePendingRepayment() {
    TickBaseTiming s = new TickBaseTiming();
    s.update(false, false, 4d, 2);
    assertEquals(4.5f, s.update(false, false, null, 2), 0);
    assertEquals(.1f, s.update(false, false, 2d, 2), 0);
  }

  @Test
  public void settlingTestsToleranceBeforeEasing() {
    TickBaseTiming s = new TickBaseTiming();
    s.update(false, false, 4d, 20);
    s.update(false, false, 4d, 20);
    float previous = .1f;
    for (int i = 0; i < 7; i++) {
      previous += (1 - previous) * .5f;
      assertEquals(previous, s.update(false, false, null, 20), 0);
    }
    assertEquals(1, s.update(false, false, null, 20), 0);
  }
}
