package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;

import org.junit.Test;

public class StealerTimingTest {
  @Test
  public void physicalDistanceAppliesToFirstSlotWithSourceRounding() {
    // A chest 2.5 world blocks away adds 38ms, even for slot zero with no previous slot.
    assertEquals(43, StealerTiming.dynamicDelay(5, 2.5, true, 15, false, 5, 0));
    assertEquals(5, StealerTiming.dynamicDelay(5, 2.5, false, 15, false, 5, 0));
    // The donor supplies zero for non-tile inventories, including InventoryLargeChest.
    assertEquals(5, StealerTiming.dynamicDelay(5, 0, true, 15, false, 5, 0));
  }

  @Test
  public void currentSlotPositionAndPhysicalDistanceAreIndependent() {
    assertEquals(173, StealerTiming.dynamicDelay(5, 2.5, true, 15, true, 5, 26));
    assertEquals(135, StealerTiming.dynamicDelay(5, 200, false, 15, true, 5, 26));
    // Adjacent slots have the same physical distance contribution, not a cursor-grid increment.
    assertEquals(48, StealerTiming.dynamicDelay(5, 2.5, true, 15, true, 5, 1));
  }

  @Test
  public void nextCandidateUsesItsOwnDelayFromTheLastTransfer() {
    StealerTiming timing = new StealerTiming(1000);
    timing.select(26);
    timing.decide(StealerTiming.dynamicDelay(5, 0, false, 15, true, 5, 26));
    assertFalse(timing.ready(1134));
    assertTrue(timing.ready(1135));
    timing.transferred(1135);
    assertFalse(timing.ready(2000)); // No selected candidate after an action.
    timing.select(0);
    timing.decide(StealerTiming.dynamicDelay(5, 0, false, 15, true, 5, 0));
    assertFalse(timing.ready(1139));
    assertTrue(timing.ready(1140)); // The old slot's 130ms extra cannot delay slot zero.
  }

  @Test
  public void changingCandidatesResamplesWithoutRestartingTransferClock() {
    StealerTiming timing = new StealerTiming(1000);
    assertTrue(timing.select(26));
    timing.decide(135);
    assertFalse(timing.select(26));
    assertFalse(timing.needsDelay());
    assertFalse(timing.ready(1120));
    assertTrue(timing.select(0));
    assertTrue(timing.needsDelay());
    timing.decide(5);
    assertTrue(timing.ready(1120)); // Candidate changes do not create a fresh now+delay wait.
  }

  @Test
  public void chestAndActivationCleanupDropCandidateButKeepElapsedTransferTime() {
    StealerTiming timing = new StealerTiming(1000);
    timing.select(5);
    timing.decide(500);
    timing.clearCandidate();
    assertFalse(timing.ready(2000));
    assertTrue(timing.select(5));
    assertTrue(timing.needsDelay());
    timing.decide(500);
    assertTrue(timing.ready(2000));
  }

  @Test
  public void zeroDelayIsImmediatelyReadyAndCanBeResampledAsInSource() {
    StealerTiming timing = new StealerTiming(1000);
    timing.select(0);
    timing.decide(0);
    assertTrue(timing.needsDelay());
    assertTrue(timing.ready(1000));
    timing.decide(10);
    assertFalse(timing.ready(1000));
    assertTrue(timing.ready(1010));
  }
}
