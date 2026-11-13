package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;

import org.junit.Test;

public class B4WaterPickupStateTest {
  @Test
  public void onlyTheTrackedLandingCanPickUpAndCompletionRetiresNextInput() {
    B4WaterPickupState s = new B4WaterPickupState();
    assertFalse(s.ready(0, true, true));
    s.placed(3, 1000);
    assertEquals(3, s.slot());
    assertFalse(s.ready(1100, false, true));
    assertTrue(s.ready(1200, true, true));
    s.pickedUp();
    assertTrue(s.waiting());
    assertFalse(s.ready(1201, true, true));
    assertFalse(s.waiting());
  }

  @Test
  public void sourceAirborneTimeoutIsStrictlyGreaterThanFiveHundredMilliseconds() {
    B4WaterPickupState s = new B4WaterPickupState();
    s.placed(1, 1000);
    assertFalse(s.ready(1500, false, true));
    assertTrue(s.waiting());
    assertFalse(s.ready(1501, false, true));
    assertFalse(s.waiting());
    s.placed(1, 1000);
    assertTrue(s.ready(1800, true, true));
  }

  @Test
  public void wrongHeldItemOnLandingAndResetRetireTheAttempt() {
    B4WaterPickupState s = new B4WaterPickupState();
    s.placed(1, 0);
    assertFalse(s.ready(100, true, false));
    assertFalse(s.waiting());
    s.placed(2, 100);
    s.reset();
    assertEquals(-1, s.slot());
    assertFalse(s.ready(200, true, true));
  }
}
