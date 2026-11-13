package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;

import org.junit.Test;

public class EagleTimingTest {
  @Test
  public void thresholdsAreResampledOnEveryGroundUpdate() {
    EagleTiming t = new EagleTiming();
    t.edge();
    assertTrue(t.hold(1000, 100, 0));
    assertTrue(t.hold(1097, 100, .999));
    assertFalse(t.hold(1097, 100, 0));
    assertFalse(t.hold(1097, 100, .999));
  }

  @Test
  public void edgeReentryRestartsGroundReturnTime() {
    EagleTiming t = new EagleTiming();
    t.edge();
    t.hold(1000, 100, .5);
    t.edge();
    assertTrue(t.hold(2000, 100, .5));
    assertFalse(t.hold(2100, 100, .5));
    t.edge();
    t.release();
    assertFalse(t.held());
    assertFalse(t.hold(3000, 100, .5));
  }

  @Test
  public void zeroDelayReleasesOnTheGroundReturnUpdate() {
    EagleTiming t = new EagleTiming();
    t.edge();
    assertFalse(t.hold(1000, 0, .9));
  }
}
