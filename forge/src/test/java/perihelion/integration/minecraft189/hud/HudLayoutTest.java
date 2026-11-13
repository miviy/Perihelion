package perihelion.integration.minecraft189.hud;

import static org.junit.Assert.*;

import org.junit.Test;

public class HudLayoutTest {
  @Test
  public void edgesKeepAMarginAtEveryResolution() {
    for (int screen : new int[] {320, 427, 854, 1920, 3840}) {
      assertEquals(2, HudLayout.position(0, screen, 123));
      assertEquals(screen - 125, HudLayout.position(1, screen, 123));
      assertEquals((screen - 123) / 2.0, HudLayout.position(.5, screen, 123), .5);
    }
  }

  @Test
  public void dragRoundTripsWithinOnePixel() {
    for (int screen : new int[] {320, 427, 1920})
      for (int x = 2; x <= screen - 122; x++) {
        double fraction = HudLayout.fraction(x, screen, 120);
        assertEquals(x, HudLayout.position(fraction, screen, 120));
      }
  }

  @Test
  public void oversizeAndInvalidCoordinatesRemainOnScreen() {
    assertEquals(0, HudLayout.position(1, 60, 120));
    assertEquals(0, HudLayout.fraction(20, 60, 120), 0);
    assertEquals(2, HudLayout.position(Double.NaN, 320, 120));
    assertEquals(2, HudLayout.position(-100, 320, 120));
    assertEquals(198, HudLayout.position(100, 320, 120));
    assertEquals(0, HudLayout.fraction(-100, 320, 120), 0);
    assertEquals(1, HudLayout.fraction(999, 320, 120), 0);
  }

  @Test
  public void durationDoesNotWrapOrGoNegative() {
    assertEquals("0:00", HudLayout.duration(-1));
    assertEquals("0:09", HudLayout.duration(9));
    assertEquals("1:00", HudLayout.duration(60));
    assertEquals("120:01", HudLayout.duration(7201));
  }

  @Test
  public void clicksExpireAtOneSecondAndIgnoreFutureTimestamps() {
    HudLayout.Clicks clicks = new HudLayout.Clicks();
    clicks.add(0);
    clicks.add(500_000_000L);
    assertEquals(1, clicks.count(0));
    assertEquals(2, clicks.count(999_999_999L));
    assertEquals(1, clicks.count(1_000_000_000L));
    assertEquals(0, clicks.count(1_500_000_000L));
  }

  @Test
  public void excessiveClicksHaveBoundedStorage() {
    HudLayout.Clicks clicks = new HudLayout.Clicks();
    for (int i = 0; i < 10000; i++) clicks.add(i);
    assertEquals(128, clicks.count(10001));
    assertEquals(0, clicks.count(2_000_000_000L));
  }
}
