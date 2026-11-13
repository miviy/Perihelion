package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;
import static perihelion.integration.minecraft189.module.gameplay.InventoryMoveState.Inventory.BLINK;
import static perihelion.integration.minecraft189.module.gameplay.InventoryMoveState.Inventory.LEGIT;
import static perihelion.integration.minecraft189.module.gameplay.InventoryMoveState.Inventory.LEGIT_SLOW;
import static perihelion.integration.minecraft189.module.gameplay.InventoryMoveState.Kind.*;

import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class InventoryMoveStateTest {
  private boolean click(
      InventoryMoveState<String> state, int tick, String packet, boolean holding) {
    return state.click(LEGIT, true, false, holding, false, tick, packet, 1, true);
  }

  @Test
  public void legitReplaysOpenThenOneTickBatchThenCloseInOrder() {
    InventoryMoveState<String> state = new InventoryMoveState<>();
    assertFalse(state.open(LEGIT, true));
    assertFalse(click(state, 10, "first", true));
    assertFalse(click(state, 10, "second", false));
    assertEquals(OPEN, state.tick(LEGIT, false).get(0).kind);
    List<InventoryMoveState.Action<String>> actions = state.tick(LEGIT, false);
    assertEquals(3, actions.size());
    assertEquals(CLICK, actions.get(0).kind);
    assertEquals("first", actions.get(0).packet);
    assertEquals("second", actions.get(1).packet);
    assertEquals(CLOSE, actions.get(2).kind);
    assertEquals(0, state.queuedPackets());
    assertEquals(InventoryMoveState.Phase.FINISH, state.phase());
    assertTrue(state.tick(LEGIT, true).isEmpty());
    assertEquals(InventoryMoveState.Phase.IDLE, state.phase());
  }

  @Test
  public void differentClickTicksRemainSeparateAndCloseRequestFinishesHeldCursor() {
    InventoryMoveState<String> state = new InventoryMoveState<>();
    click(state, 10, "first", true);
    click(state, 11, "second", true);
    assertFalse(state.close(LEGIT, InventoryMoveState.Chest.VANILLA, false));
    state.tick(LEGIT, true);
    assertTrue(state.consumeStop(LEGIT, InventoryMoveState.Chest.VANILLA, false, false));
    List<InventoryMoveState.Action<String>> first = state.tick(LEGIT, true);
    assertEquals(1, first.size());
    assertEquals("first", first.get(0).packet);
    List<InventoryMoveState.Action<String>> second = state.tick(LEGIT, true);
    assertEquals(2, second.size());
    assertEquals("second", second.get(0).packet);
    assertEquals(CLOSE, second.get(1).kind);
  }

  @Test
  public void cursorHoldingKeepsSessionActiveUntilCloseIsRequested() {
    InventoryMoveState<String> state = new InventoryMoveState<>();
    click(state, 1, "pickup", true);
    state.tick(LEGIT, false);
    assertTrue(state.consumeStop(LEGIT, InventoryMoveState.Chest.VANILLA, true, false));
    assertEquals(1, state.tick(LEGIT, false).size());
    assertEquals(InventoryMoveState.Phase.ACTIVE, state.phase());
    assertFalse(state.close(LEGIT, InventoryMoveState.Chest.VANILLA, false));
    assertEquals(CLOSE, state.tick(LEGIT, true).get(0).kind);
  }

  @Test
  public void legitimateSlowAddsTheFinishTickPause() {
    InventoryMoveState<String> state = new InventoryMoveState<>();
    state.click(LEGIT_SLOW, true, false, false, false, 1, "click", 1, true);
    state.tick(LEGIT_SLOW, false);
    state.tick(LEGIT_SLOW, false);
    assertTrue(state.consumeStop(LEGIT_SLOW, InventoryMoveState.Chest.VANILLA, true, false));
    state.tick(LEGIT_SLOW, false);
    assertFalse(state.consumeStop(LEGIT_SLOW, InventoryMoveState.Chest.VANILLA, true, false));
  }

  @Test
  public void rejectedClickDoesNotStartTransactionOrMotionPause() {
    InventoryMoveState<String> state = new InventoryMoveState<>();
    assertFalse(state.click(LEGIT, true, false, false, true, 1, "invalid", 1, true));
    assertEquals(0, state.queuedPackets());
    assertFalse(state.clicked());
    assertTrue(state.tick(LEGIT, false).isEmpty());
  }

  @Test
  public void blinkBeginsAtOpenAndClosesAfterTwoTicks() {
    InventoryMoveState<String> state = new InventoryMoveState<>();
    assertTrue(state.open(BLINK, true));
    assertTrue(state.blinking());
    assertTrue(state.close(BLINK, InventoryMoveState.Chest.VANILLA, true));
    assertEquals(2, state.releaseTicks());
    state.tick(BLINK, true);
    assertFalse(state.releaseReady());
    state.tick(BLINK, true);
    assertTrue(state.releaseReady());
    state.finishBlink();
    assertFalse(state.blinking());
  }

  @Test
  public void closeModeBypassIsDistinctFromOrderedClickReplay() {
    InventoryMoveState<String> state = new InventoryMoveState<>();
    assertFalse(state.open(InventoryMoveState.Inventory.CLOSE, true));
    assertTrue(
        state.click(
            InventoryMoveState.Inventory.CLOSE, true, false, false, false, 10, null, 0, true));
    assertTrue(state.blinking());
    assertEquals(3, state.releaseTicks());
    List<InventoryMoveState.Action<String>> actions =
        state.tick(InventoryMoveState.Inventory.CLOSE, false);
    assertEquals(1, actions.size());
    assertEquals(BYPASS_CLOSE, actions.get(0).kind);
    assertNull(actions.get(0).packet);
    assertFalse(state.releaseReady());
    state.tick(InventoryMoveState.Inventory.CLOSE, false);
    state.tick(InventoryMoveState.Inventory.CLOSE, false);
    assertTrue(state.releaseReady());
  }

  @Test
  public void foreignBlinkDoesNotBecomeOwnedByInventoryMove() {
    InventoryMoveState<String> state = new InventoryMoveState<>();
    state.open(BLINK, false);
    assertFalse(state.blinking());
    state.click(InventoryMoveState.Inventory.CLOSE, true, false, false, false, 10, null, 0, false);
    assertFalse(state.blinking());
    assertEquals(0, state.releaseTicks());
    assertEquals(BYPASS_CLOSE, state.tick(InventoryMoveState.Inventory.CLOSE, false).get(0).kind);
  }

  @Test
  public void countAndByteOverflowRejectAndFaultInsteadOfReplayingPartialTransactions() {
    InventoryMoveState<String> count = new InventoryMoveState<>(1, 10);
    click(count, 1, "first", false);
    assertFalse(click(count, 2, "second", false));
    try {
      count.tick(LEGIT, false);
      fail("overflow must fault");
    } catch (IllegalStateException expected) {
      assertTrue(expected.getMessage().contains("capacity"));
    }
    InventoryMoveState<String> bytes = new InventoryMoveState<>(10, 1);
    assertFalse(bytes.click(LEGIT, true, false, false, false, 1, "large", 2, true));
    assertEquals(0, bytes.queuedPackets());
  }

  @Test
  public void retirementDiscardsSnapshotsAndRejectsRacingOrLateCallbacks() throws Exception {
    InventoryMoveState<String> state = new InventoryMoveState<>();
    click(state, 1, "stale", false);
    state.startBlink(true);
    state.retire();
    FutureTask<Void> late =
        new FutureTask<>(
            () -> {
              assertTrue(click(state, 2, "late", false));
              assertTrue(state.open(BLINK, true));
              state.startBlink(true);
              return null;
            });
    new Thread(late).start();
    late.get(5, TimeUnit.SECONDS);
    state.reset(); // A setting observer cannot revive a retired activation.
    assertTrue(state.retired());
    assertFalse(state.blinking());
    assertEquals(0, state.queuedPackets());
    assertTrue(state.tick(LEGIT, false).isEmpty());
  }
}
