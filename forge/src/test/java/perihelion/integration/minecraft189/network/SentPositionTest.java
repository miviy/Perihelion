package perihelion.integration.minecraft189.network;

import static org.junit.Assert.*;

import org.junit.Test;

public class SentPositionTest {
  @Test
  public void snapshotsRemainCoherentWhenLaterMovementArrives() {
    SentPosition s = new SentPosition();
    assertNull(s.point());
    s.record(-3.5, 64.125, 8.75);
    SentPosition.Point first = s.point();
    s.record(1, 2, 3);
    assertEquals(-3.5, first.x, 0);
    assertEquals(64.125, first.y, 0);
    assertEquals(8.75, first.z, 0);
    assertEquals(1, s.point().x, 0);
    assertEquals(2, s.point().y, 0);
    assertEquals(3, s.point().z, 0);
  }

  @Test
  public void malformedCoordinatesCannotReplaceTheLastFinitePosition() {
    SentPosition s = new SentPosition();
    s.record(1, 2, 3);
    SentPosition.Point good = s.point();
    s.record(Double.NaN, 2, 3);
    s.record(1, Double.POSITIVE_INFINITY, 3);
    s.record(1, 2, Double.NEGATIVE_INFINITY);
    assertSame(good, s.point());
  }

  @Test
  public void retirementRemovesTheOldWorldPosition() {
    SentPosition s = new SentPosition();
    s.record(1, 2, 3);
    s.reset();
    assertNull(s.point());
    s.record(-1, -2, -3);
    assertEquals(-2, s.point().y, 0);
  }

  @Test
  public void rawThreadPublishesOneImmutableCoordinateTuple() throws Exception {
    SentPosition s = new SentPosition();
    Thread sender = new Thread(() -> s.record(11, 22, 33), "sent-position-fixture");
    sender.start();
    sender.join();
    SentPosition.Point result = s.point();
    assertNotNull(result);
    assertEquals(11, result.x, 0);
    assertEquals(22, result.y, 0);
    assertEquals(33, result.z, 0);
  }
}
