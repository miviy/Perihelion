package perihelion.integration.minecraft189.network;

import org.junit.Test;
import static org.junit.Assert.*;

public class PacketOrderBarrierTest {
  @Test public void anotherBufferCannotStealAnUnfinishedSequence() {
    PacketOrderBarrier<Object> order = new PacketOrderBarrier<>();
    Object a = new Object(), b = new Object();
    assertTrue(order.queued(a)); assertTrue(order.queued(a));
    assertFalse(order.queued(b));
    order.replayed(a); assertSame(a, order.owner());
    order.replayed(a); assertNull(order.owner());
    assertTrue(order.queued(b)); assertSame(b, order.owner());
  }
  @Test public void retiringALosingScopeDoesNotDropTheOwner() {
    PacketOrderBarrier<Object> order = new PacketOrderBarrier<>();
    Object a = new Object(), b = new Object();
    order.queued(a); order.discard(b); order.replayed(b);
    assertSame(a, order.owner()); order.replayed(a); assertNull(order.owner());
  }
  @Test public void StaleCompletionCannotReplaceANewSequence() {
    PacketOrderBarrier<Object> order = new PacketOrderBarrier<>();
    Object a = new Object(), b = new Object();
    order.queued(a); order.clear(); order.queued(b);
    order.replayed(a); order.discard(a); assertSame(b, order.owner());
    order.replayed(b); assertNull(order.owner());
  }
}
