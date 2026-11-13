package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;

import org.junit.Test;

public class ClickTimingTest {
  @Test
  public void configuredRatesSurviveTickQuantization() {
    for (double cps : new double[] {1, 8, 9, 12, 13, 17, 20}) {
      double interval = 1000 / cps, due = interval;
      int clicks = 0;
      for (long now = 0; now <= 60000; now += 50) {
        if (now >= due) {
          clicks++;
          due = ClickTiming.next(due, now, interval);
        }
      }
      assertEquals("CPS " + cps, cps, clicks / 60.0, .04);
    }
  }

  @Test
  public void stalledOrRewoundClockDoesNotAccumulateDebt() {
    assertEquals(5100, ClickTiming.next(100, 5000, 100), 0);
    assertEquals(150, ClickTiming.next(1000, 50, 100), 0);
    assertEquals(200, ClickTiming.next(100, 140, 100), 0);
  }
}
