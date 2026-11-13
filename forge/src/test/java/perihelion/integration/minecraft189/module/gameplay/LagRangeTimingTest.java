package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;
import static perihelion.integration.minecraft189.module.gameplay.LagRangeTiming.Action.*;

import org.junit.Test;

public class LagRangeTimingTest {
  @Test
  public void delaySamplerRunsOnlyOnNewRangeEntryIncludingBlink() {
    for (boolean blink : new boolean[] {false, true}) {
      LagRangeTiming s = new LagRangeTiming();
      java.util.concurrent.atomic.AtomicInteger calls =
          new java.util.concurrent.atomic.AtomicInteger();
      java.util.function.IntSupplier sampler =
          () -> {
            calls.incrementAndGet();
            return 200;
          };
      s.pre(0, false, 4d, blink, 3.5, 4.2, sampler);
      s.pre(1, true, null, blink, 3.5, 4.2, sampler);
      s.pre(2, true, 3.5, blink, 3.5, 4.2, sampler);
      s.pre(3, true, 4.3, blink, 3.5, 4.2, sampler);
      assertEquals(0, calls.get());
      s.pre(4, true, 4d, blink, 3.5, 4.2, sampler);
      assertEquals(1, calls.get());
      s.post(4, blink);
      s.pre(5, true, 4d, blink, 3.5, 4.2, sampler);
      s.post(105, blink);
      s.pre(106, true, 4d, blink, 3.5, 4.2, sampler);
      assertEquals(1, calls.get());
      assertEquals(FLUSH, s.pre(107, false, 4d, blink, 3.5, 4.2, sampler));
      assertEquals(1, calls.get());
      s.pre(108, true, 4d, blink, 3.5, 4.2, sampler);
      assertEquals(2, calls.get());
      s.reset();
      s.pre(109, true, 4d, blink, 3.5, 4.2, sampler);
      assertEquals(3, calls.get());
    }
  }

  @Test
  public void sourceDelayRangeHandlesFixedAndReversedEndpoints() {
    assertEquals(0, LagRangeTiming.sampleDelay(0, 0));
    assertEquals(5000, LagRangeTiming.sampleDelay(5000, 5000));
    for (int i = 0; i < 100; i++) {
      int forward = LagRangeTiming.sampleDelay(50, 200);
      int reverse = LagRangeTiming.sampleDelay(200, 50);
      assertTrue(forward >= 50 && forward < 200);
      assertTrue(reverse >= 50 && reverse < 200);
    }
  }

  @Test
  public void lagArmsAfterItsInitialWalkingPass() {
    LagRangeTiming s = new LagRangeTiming();
    assertFalse(s.lagging());
    assertEquals(NONE, s.pre(0, true, 4d, false, 3.5, 4.2, 200));
    assertTrue(s.lagging());
    assertEquals(COLLECT, s.post(0, false));
    assertEquals(NONE, s.post(1, false));
    assertEquals(200, s.delay());
  }

  @Test
  public void singleSpoofLeaseEndsAtOneHundredMilliseconds() {
    LagRangeTiming s = new LagRangeTiming();
    s.pre(1000, true, 4d, false, 3.5, 4.2, 500);
    s.post(1000, false);
    assertEquals(NONE, s.post(1099, false));
    assertEquals(FLUSH, s.post(1100, false));
    assertTrue(s.lagging()); // Marker remains visible for the source target-duration cycle.
    assertEquals(NONE, s.post(1101, false));
    assertEquals(NONE, s.pre(1200, true, 4d, false, 3.5, 4.2, 500));
    assertEquals(NONE, s.post(1200, false));
    assertEquals(FLUSH, s.pre(1500, true, 4d, false, 3.5, 4.2, 500));
    assertFalse(s.lagging());
    assertEquals(NONE, s.post(1500, false));
    s.pre(1501, true, 4d, false, 3.5, 4.2, 500);
    assertEquals(COLLECT, s.post(1501, false));
  }

  @Test
  public void shortDelayFlushesBeforeTheEndingWalkingPass() {
    LagRangeTiming s = new LagRangeTiming();
    s.pre(0, true, 4d, false, 3.5, 4.2, 50);
    s.post(0, false);
    s.pre(49, true, 4d, false, 3.5, 4.2, 50);
    assertEquals(NONE, s.post(49, false));
    assertEquals(FLUSH, s.pre(50, true, 4d, false, 3.5, 4.2, 50));
    assertEquals(NONE, s.post(50, false));
  }

  @Test
  public void blinkCollectsImmediatelyAndWaitsForRangeExit() {
    LagRangeTiming s = new LagRangeTiming();
    assertEquals(COLLECT, s.pre(0, true, 4d, true, 3.5, 4.2, 50));
    assertEquals(NONE, s.post(1000, true));
    s.pre(1000, true, 4d, true, 3.5, 4.2, 50);
    assertEquals(NONE, s.post(1000, true));
    assertEquals(FLUSH, s.pre(1001, true, 3.5, true, 3.5, 4.2, 50));
    assertEquals(NONE, s.post(1001, true));
  }

  @Test
  public void lossOfTargetOrMovementFinishesAtPre() {
    for (boolean move : new boolean[] {false, true}) {
      LagRangeTiming s = new LagRangeTiming();
      s.pre(0, true, 4d, true, 3.5, 4.2, 50);
      assertEquals(FLUSH, s.pre(1, move, move ? null : 4d, true, 3.5, 4.2, 50));
      assertEquals(NONE, s.post(1, true));
    }
  }

  @Test
  public void sourceRangeIsStrictAtMinimumAndInclusiveAtMaximum() {
    assertEquals(NONE, new LagRangeTiming().pre(0, true, 3.5, true, 3.5, 4.2, 50));
    assertEquals(COLLECT, new LagRangeTiming().pre(0, true, 4.2, true, 3.5, 4.2, 50));
  }

  @Test
  public void resetRetiresTheOldSequence() {
    LagRangeTiming s = new LagRangeTiming();
    s.pre(0, true, 4d, false, 3.5, 4.2, 50);
    s.reset();
    assertFalse(s.lagging());
    assertEquals(NONE, s.post(1, false));
    assertEquals(0, s.delay());
  }
}
