package perihelion.integration.minecraft189.network;

import static org.junit.Assert.*;
import static perihelion.integration.minecraft189.network.ActionPacketWindow.Kind.*;

import org.junit.Test;

public class ActionPacketWindowTest {
  @Test
  public void foreignActionBlocksUntilAnOrdinaryWalk() {
    ActionPacketWindow w = new ActionPacketWindow();
    w.record(ACTION);
    assertTrue(w.bad());
    w.record(OTHER);
    assertTrue(w.bad());
    w.record(WALK);
    assertFalse(w.bad());
  }

  @Test
  public void repeatedActionsCannotClearEachOther() {
    ActionPacketWindow w = new ActionPacketWindow();
    for (int i = 0; i < 100; i++) {
      w.record(ACTION);
      assertTrue(w.bad());
    }
    w.record(WALK);
    assertFalse(w.bad());
    w.record(ACTION);
    assertTrue(w.bad());
  }

  @Test
  public void unrelatedTrafficAndRepeatedWalksStayClean() {
    ActionPacketWindow w = new ActionPacketWindow();
    w.record(WALK);
    w.record(WALK);
    w.record(OTHER);
    assertFalse(w.bad());
  }

  @Test
  public void retirementSeparatesWorlds() {
    ActionPacketWindow w = new ActionPacketWindow();
    w.record(ACTION);
    w.reset();
    assertFalse(w.bad());
    w.record(ACTION);
    assertTrue(w.bad());
  }
}
