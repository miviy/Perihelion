package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;

import java.util.UUID;
import org.junit.Test;

public class FireballResponseStateTest {
  @Test
  public void startupDelayIsInclusiveAtFiftyMilliseconds() {
    FireballResponseState s = new FireballResponseState(1000);
    assertFalse(s.ready(1049));
    assertTrue(s.ready(1050));
  }

  @Test
  public void attacksDoNotRestartTheSourceStopwatch() {
    FireballResponseState s = new FireballResponseState(0);
    s.world(1);
    s.record(new UUID(0, 1));
    assertTrue(s.ready(50));
    assertTrue(s.ready(51));
  }

  @Test
  public void ordinaryTogglesRetainSeenIdsButWorldsRetireThem() {
    FireballResponseState s = new FireballResponseState(0);
    UUID ball = new UUID(0, 1);
    s.world(1);
    s.record(ball);
    s.world(1);
    assertTrue(s.attacked(ball));
    s.world(2);
    assertFalse(s.attacked(ball));
  }

  @Test
  public void nearbyNoticeRearmsOnlyAfterAnEligibleEmptyScan() {
    FireballResponseState s = new FireballResponseState(0);
    assertTrue(s.observeNearby(true));
    assertFalse(s.observeNearby(true));
    assertFalse(s.observeNearby(false));
    assertFalse(s.observeNearby(false));
    assertTrue(s.observeNearby(true));
  }

  @Test
  public void nearbyNoticeSurvivesToggleButRetiresWithTheWorld() {
    FireballResponseState s = new FireballResponseState(0);
    s.world(1);
    assertTrue(s.observeNearby(true));
    s.world(1);
    assertFalse(s.observeNearby(true));
    s.world(2);
    assertTrue(s.observeNearby(true));
  }

  @Test
  public void idsAreBoundedWithoutRetainingEntities() {
    FireballResponseState s = new FireballResponseState(0);
    s.world(1);
    for (int i = 0; i < 1025; i++) s.record(new UUID(0, i));
    assertFalse(s.attacked(new UUID(0, 0)));
    assertTrue(s.attacked(new UUID(0, 1)));
    assertTrue(s.attacked(new UUID(0, 1024)));
  }
}
