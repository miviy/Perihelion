package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;
import static perihelion.integration.minecraft189.module.gameplay.BackTrackState.Mode.*;
import static perihelion.integration.minecraft189.module.gameplay.BackTrackState.Style.*;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import perihelion.integration.minecraft189.network.OrderedPacketQueue;

public class BackTrackStateTest {
  private BackTrackState state(BackTrackState.Mode mode, BackTrackState.Style style) {
    BackTrackState state = new BackTrackState(new BackTrackState.Policy(mode, style, 400, 2, 4));
    state.begin(7, 1, 96, 64, -96, 100);
    return state;
  }

  @Test
  public void basicNewAndMmcDelayAllTrafficWithoutLegacyExpiryOrFlush() {
    for (BackTrackState state : new BackTrackState[] {state(BASIC, NEW), state(MMC, OLD)}) {
      assertTrue(state.captures(false, false));
      assertTrue(state.captures(true, false));
      assertTrue(state.captures(false, true));
      assertFalse(state.expired(100000));
      state.captured(true);
      assertFalse(state.flush);
    }
    assertEquals(500, state(BASIC, NEW).releaseAt(100, true));
    assertEquals(1100, state(MMC, OLD).releaseAt(100, false));
  }

  @Test
  public void legacyModesCaptureOnlyMovementAndFlushTriggersWithExactTimeBoundary() {
    for (BackTrackState state : new BackTrackState[] {state(BASIC, OLD), state(HYPIXEL, NEW)}) {
      assertFalse(state.captures(false, false));
      assertTrue(state.captures(true, false));
      assertTrue(state.captures(false, true));
      assertEquals(Long.MAX_VALUE, state.releaseAt(100, false));
      assertEquals(0, state.releaseAt(100, true));
      assertFalse(state.expired(100 + state.policy.delay - 1));
      assertTrue(state.expired(100 + state.policy.delay));
      state.captured(true);
      assertTrue(state.flush);
    }
    assertEquals(250, state(HYPIXEL, NEW).policy.delay);
  }

  @Test
  public void rangeIsInclusiveAndPreservesConfiguredBoundsWhileMmcOverridesThem() {
    BackTrackState.Policy basic = state(BASIC, NEW).policy;
    assertTrue(basic.inRange(2));
    assertTrue(basic.inRange(4));
    assertFalse(basic.inRange(1.999));
    assertFalse(basic.inRange(4.001));
    assertFalse(new BackTrackState.Policy(BASIC, OLD, 400, 4, 2).inRange(3));
    assertFalse(basic.inRange(Double.NaN));
    BackTrackState.Policy mmc = new BackTrackState.Policy(MMC, OLD, 40, 5, 6);
    assertTrue(mmc.inRange(0));
    assertTrue(mmc.inRange(8));
    assertFalse(mmc.inRange(8.001));
    assertEquals(1000, mmc.delay);
    assertFalse(mmc.legacy());
  }

  @Test
  public void realPositionFollowsOnlyTargetWithExactServerFixedPointArithmetic() {
    BackTrackState state = state(BASIC, NEW);
    state.move(7, 1, -2, 3);
    state.move(8, 100, 100, 100);
    BackTrackState.Position beforeTeleport = state.position();
    assertEquals(97 / 32.0, beforeTeleport.x, 0);
    assertEquals(62 / 32.0, beforeTeleport.y, 0);
    assertEquals(-93 / 32.0, beforeTeleport.z, 0);
    state.teleport(8, 0, 0, 0);
    assertEquals(beforeTeleport.x, state.position().x, 0);
    state.teleport(7, -321, 128, 33);
    state.move(7, -1, 1, -1);
    assertEquals(-322 / 32.0, state.position().x, 0);
    assertEquals(129 / 32.0, state.position().y, 0);
    assertEquals(1, state.position().z, 0);
    assertEquals(97 / 32.0, beforeTeleport.x, 0); // Published positions are immutable.
  }

  @Test
  public void targetSwitchAndStopCannotKeepOldPositionOrFlushRequest() {
    BackTrackState state = state(BASIC, OLD);
    state.captured(true);
    state.begin(8, 2, 0, 32, 64, 500);
    assertFalse(state.flush);
    assertFalse(state.expired(899));
    state.move(7, 64, 64, 64);
    assertEquals(0, state.position().x, 0);
    state.stop();
    assertNull(state.position());
    assertFalse(state.captures(true, true));
    assertFalse(state.flush);
    assertEquals(-1, state.targetId);
    assertEquals(-1, state.localId);
  }

  @Test
  public void legacyCorrectionCannotOvertakeHeldMovementBeforeFullFlush() {
    BackTrackState state = state(BASIC, OLD);
    OrderedPacketQueue<String> queue = new OrderedPacketQueue<>(8, 100);
    queue.offer("movement", state.releaseAt(110, false), 1);
    queue.offer("correction", state.releaseAt(120, true), 1);
    state.captured(true);
    assertTrue(state.flush);
    assertEquals(Collections.emptyList(), queue.drain(120, false));
    assertEquals(Arrays.asList("movement", "correction"), queue.drain(120, true));
  }

  @Test
  public void shorterDelayChangeCannotOvertakeOlderPackets() {
    BackTrackState state = state(BASIC, NEW);
    OrderedPacketQueue<String> queue = new OrderedPacketQueue<>(8, 100);
    queue.offer("older", state.releaseAt(100, false), 1);
    assertFalse(state.changePolicy(new BackTrackState.Policy(BASIC, NEW, 40, 2, 4)));
    queue.offer("newer", state.releaseAt(101, false), 1);
    assertEquals(Collections.emptyList(), queue.drain(141, false));
    assertEquals(Arrays.asList("older", "newer"), queue.drain(500, false));
  }

  @Test
  public void switchingBetweenLegacyAndTimedPoliciesRetiresTheOldBatch() {
    for (BackTrackState.Mode nextMode : new BackTrackState.Mode[] {BASIC, MMC}) {
      BackTrackState state = state(BASIC, OLD);
      assertTrue(state.changePolicy(new BackTrackState.Policy(nextMode, NEW, 400, 2, 4)));
      assertFalse(state.holding);
      assertNull(state.position());
      assertFalse(state.captures(true, false));
      state.begin(8, 1, 32, 64, 96, 900);
      assertTrue(state.captures(false, false));
      assertTrue(state.changePolicy(new BackTrackState.Policy(BASIC, OLD, 400, 2, 4)));
      assertFalse(state.holding);
      assertFalse(state.flush);
    }
  }

  @Test
  public void changingLegacyDelayKeepsTheBatchAndUsesTheNewExpiry() {
    BackTrackState state = state(BASIC, OLD);
    assertFalse(state.changePolicy(new BackTrackState.Policy(HYPIXEL, OLD, 400, 2, 4)));
    assertTrue(state.holding);
    assertEquals(7, state.targetId);
    assertFalse(state.expired(349));
    assertTrue(state.expired(350));
  }

  @Test
  public void deferredAttackCannotApplyAcrossSameIdStopAndRestart() {
    BackTrackState state = state(BASIC, OLD);
    BackTrackState.AttackPosition pending = state.attackPosition(7);
    assertTrue(state.accepts(pending));
    state.stop();
    state.begin(7, 1, 320, 64, -96, 200);
    assertFalse(state.accepts(pending));
    assertEquals(3, pending.position.x, 0);
    assertTrue(state.accepts(state.attackPosition(7)));
    assertEquals(10, state.attackPosition(7).position.x, 0);
  }

  @Test
  public void deferredAttackCannotApplyAfterTimedPolicyRoundTrip() {
    BackTrackState state = state(BASIC, OLD);
    BackTrackState.AttackPosition pending = state.attackPosition(7);
    state.changePolicy(new BackTrackState.Policy(BASIC, NEW, 400, 2, 4));
    state.begin(7, 1, 320, 64, -96, 200);
    assertNull(state.attackPosition(7));
    state.changePolicy(new BackTrackState.Policy(BASIC, OLD, 400, 2, 4));
    state.begin(7, 1, 640, 64, -96, 300);
    assertFalse(state.accepts(pending));
    assertTrue(state.accepts(state.attackPosition(7)));
    assertNull(state.attackPosition(8));
  }
}
