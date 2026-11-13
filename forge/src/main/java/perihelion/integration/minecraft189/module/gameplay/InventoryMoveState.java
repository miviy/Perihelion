// SPDX-License-Identifier: AGPL-3.0-only
// Adapted from Yuri 1cb86ad94ce1f01aa9b715577163c8ecc47b7def; Copyright (c) 2026 Unlegit Inc.
package perihelion.integration.minecraft189.module.gameplay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Yuri's inventory transaction cadence, using owned packet snapshots rather than live packets. */
final class InventoryMoveState<T> {
  enum Inventory {
    DISABLED("Disabled"),
    VANILLA("Vanilla"),
    LEGIT("Legit"),
    LEGIT_SLOW("Legit Slow"),
    PAUSE("Pause"),
    BLINK("Blink"),
    CLOSE("Close");

    private final String label;

    Inventory(String label) {
      this.label = label;
    }

    boolean legit() {
      return this == LEGIT || this == LEGIT_SLOW;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  enum Chest {
    DISABLED("Disabled"),
    VANILLA("Vanilla"),
    PAUSE("Pause"),
    BLINK("Blink");

    private final String label;

    Chest(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  enum Phase {
    IDLE,
    OPEN,
    ACTIVE,
    FINISH
  }

  enum Kind {
    OPEN,
    CLICK,
    CLOSE,
    BYPASS_CLOSE
  }

  static final class Action<T> {
    final Kind kind;
    final T packet;

    Action(Kind kind, T packet) {
      this.kind = kind;
      this.packet = packet;
    }
  }

  private static final class Batch<T> {
    final List<T> packets = new ArrayList<>();
    boolean emptyCursor;
    int bytes;
  }

  private final LinkedHashMap<Integer, Batch<T>> queued = new LinkedHashMap<>();
  private final int packetLimit, byteLimit;
  private Phase phase = Phase.IDLE;
  private boolean closeQueued,
      clickedThisTick,
      clicked,
      blinking,
      releaseDue,
      sendClose,
      overflow,
      retired;
  private int slowTicks, releaseTicks, packets, bytes;

  InventoryMoveState() {
    this(1024, 2 * 1024 * 1024);
  }

  InventoryMoveState(int packetLimit, int byteLimit) {
    this.packetLimit = packetLimit;
    this.byteLimit = byteLimit;
  }

  synchronized boolean open(Inventory inventory, boolean blinkAvailable) {
    if (retired) return true;
    if (inventory.legit() || inventory == Inventory.CLOSE) return false;
    if (inventory == Inventory.BLINK) startBlink(blinkAvailable);
    return true;
  }

  synchronized boolean close(Inventory inventory, Chest chest, boolean inInventory) {
    if (retired) return true;
    clicked = false;
    if (inventory.legit() && (inInventory || phase != Phase.IDLE || !queued.isEmpty())) {
      if (phase != Phase.IDLE || !queued.isEmpty()) closeQueued = true;
      return false;
    }
    if (inventory == Inventory.BLINK || chest == Chest.BLINK) {
      if (blinking) releaseTicks = 2;
    } else if (inventory == Inventory.CLOSE && inInventory) {
      return false;
    }
    return true;
  }

  synchronized boolean click(
      Inventory inventory,
      boolean inInventory,
      boolean chestLike,
      boolean holding,
      boolean reject,
      int tick,
      T packet,
      int size,
      boolean blinkAvailable) {
    if (retired) return true;
    if (inventory.legit() && inInventory) {
      if (reject) return false;
      if (overflow || size < 0 || packets >= packetLimit || size > byteLimit - bytes) {
        overflow = true;
        return false;
      }
      Batch<T> batch = queued.get(tick);
      if (batch == null) {
        batch = new Batch<>();
        queued.put(tick, batch);
      }
      batch.packets.add(packet);
      batch.bytes += size;
      batch.emptyCursor = !holding;
      packets++;
      bytes += size;
      clicked = clickedThisTick = true;
      if (phase == Phase.IDLE) phase = Phase.OPEN;
      return false;
    }
    if (inventory == Inventory.CLOSE && !chestLike) {
      sendClose = true;
      if (!blinking) {
        startBlink(blinkAvailable);
        if (blinking) releaseTicks = 3;
      }
    }
    clicked = clickedThisTick = true;
    return true;
  }

  /** The same order as donor PreUpdate: finish, slow countdown, release, transactions, bypass. */
  synchronized List<Action<T>> tick(Inventory inventory, boolean noScreen) {
    if (retired) return Collections.emptyList();
    if (overflow) throw new IllegalStateException("Inventory click buffer capacity exceeded");
    if (blinking && releaseTicks == 0 && noScreen) releaseTicks = 2;
    if (inventory.legit()) {
      if (phase == Phase.FINISH) phase = queued.isEmpty() ? Phase.IDLE : Phase.OPEN;
      if (slowTicks > 0) slowTicks--;
    }
    if (releaseTicks > 0 && --releaseTicks == 0) releaseDue = true;

    List<Action<T>> actions = new ArrayList<>();
    if (inventory.legit()) {
      if (phase == Phase.OPEN) {
        actions.add(new Action<>(Kind.OPEN, null));
        phase = Phase.ACTIVE;
      } else if (phase == Phase.ACTIVE) {
        if (!queued.isEmpty()) {
          Map.Entry<Integer, Batch<T>> first = queued.entrySet().iterator().next();
          Batch<T> batch = first.getValue();
          for (T packet : batch.packets) actions.add(new Action<>(Kind.CLICK, packet));
          packets -= batch.packets.size();
          bytes -= batch.bytes;
          queued.remove(first.getKey());
          if (queued.isEmpty() && (batch.emptyCursor || closeQueued)) finish(inventory, actions);
        } else if (closeQueued) finish(inventory, actions);
      }
    } else clearTransactions();
    if (sendClose) {
      sendClose = false;
      actions.add(new Action<>(Kind.BYPASS_CLOSE, null));
    }
    return Collections.unmodifiableList(actions);
  }

  private void finish(Inventory inventory, List<Action<T>> actions) {
    actions.add(new Action<>(Kind.CLOSE, null));
    if (inventory == Inventory.LEGIT_SLOW) slowTicks = 1;
    closeQueued = false;
    phase = Phase.FINISH;
  }

  synchronized boolean consumeStop(
      Inventory inventory, Chest chest, boolean inInventory, boolean inChest) {
    if (retired) return false;
    boolean stop =
        inventory.legit() && (slowTicks > 0 || phase == Phase.ACTIVE && waiting(inventory));
    stop |=
        clickedThisTick
            && (inventory == Inventory.PAUSE && inInventory || chest == Chest.PAUSE && inChest);
    clickedThisTick = false;
    return stop;
  }

  private boolean waiting(Inventory inventory) {
    if (inventory == Inventory.LEGIT_SLOW) return true;
    if (closeQueued) return queued.size() > 1;
    if (queued.size() != 1) return true;
    return !queued.entrySet().iterator().next().getValue().emptyCursor;
  }

  synchronized void startBlink(boolean available) {
    if (retired) return;
    if (!blinking && available) {
      blinking = true;
      releaseDue = false;
    }
  }

  synchronized boolean blinking() {
    return blinking;
  }

  synchronized boolean releaseReady() {
    return blinking && releaseDue;
  }

  synchronized int releaseTicks() {
    return releaseTicks;
  }

  synchronized void finishBlink() {
    blinking = releaseDue = false;
    releaseTicks = 0;
  }

  synchronized boolean clicked() {
    return clicked;
  }

  synchronized Phase phase() {
    return phase;
  }

  synchronized int queuedPackets() {
    return packets;
  }

  synchronized boolean retired() {
    return retired;
  }

  /** Retirement is irreversible, including for callbacks already racing teardown. */
  synchronized void retire() {
    reset();
    retired = true;
  }

  /** Drop transactions on mode changes, overflow, world teardown and activation retirement. */
  synchronized void reset() {
    clearTransactions();
    clicked = clickedThisTick = blinking = releaseDue = sendClose = overflow = false;
    slowTicks = releaseTicks = 0;
  }

  private void clearTransactions() {
    phase = Phase.IDLE;
    queued.clear();
    packets = bytes = 0;
    closeQueued = false;
  }
}
