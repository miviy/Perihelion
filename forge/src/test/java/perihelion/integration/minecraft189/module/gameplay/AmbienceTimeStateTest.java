package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;
import org.junit.Test;

public class AmbienceTimeStateTest {
  @Test public void initiallyFrozenZeroDoesNotStartAdvancingWithoutAPacket() {
    AmbienceTimeState state = new AmbienceTimeState(0, false, 100);
    assertEquals(0, state.restoredTime(1100));
    assertFalse(state.daylight());
  }

  @Test public void negativePacketRestoresFrozenAbsoluteTimeAndRule() {
    AmbienceTimeState state = new AmbienceTimeState(6000, true, 100);
    state.packet(-4321, 200);
    assertEquals(4321, state.restoredTime(5200));
    assertFalse(state.daylight());
  }

  @Test public void advancingPacketAddsOnlyElapsedWholeTicks() {
    AmbienceTimeState state = new AmbienceTimeState(6000, false, 100);
    state.packet(12000, 200);
    assertEquals(12000, state.restoredTime(249));
    assertEquals(12001, state.restoredTime(250));
    assertEquals(12100, state.restoredTime(5200));
    assertTrue(state.daylight());
  }

  @Test public void newestPacketReplacesBothTheTimeAndItsRule() {
    AmbienceTimeState state = new AmbienceTimeState(6000, true, 100);
    state.packet(12000, 200);
    state.packet(-700, 300);
    assertEquals(700, state.restoredTime(5300));
    assertFalse(state.daylight());
  }
}
