// SPDX-License-Identifier: AGPL-3.0-only
// Adapted from Yuri 1cb86ad94ce1f01aa9b715577163c8ecc47b7def; Copyright (c) 2026 Unlegit Inc.
package perihelion.integration.minecraft189.module.gameplay;

/** Primitive packet facts and the shift-clutch attempt window. */
final class ClutchState {
  enum Traffic {
    WALK,
    SLOT,
    ATTACK,
    SWING,
    BLOCK,
    INVENTORY,
    OTHER
  }

  private boolean slot, attack, swing, block, inventory;
  private int airTicks, onAir;
  // The source starts at zero. One additional opportunity is needed to submit a
  // native owned walking aim before the first PRE action may use it.
  private int toggle = 1;

  synchronized void packet(Traffic traffic) {
    switch (traffic) {
      case WALK:
        slot = attack = swing = block = inventory = false;
        break;
      case SLOT:
        slot = true;
        break;
      case ATTACK:
        attack = true;
        break;
      case SWING:
        swing = true;
        break;
      case BLOCK:
        block = true;
        break;
      case INVENTORY:
        inventory = true;
        break;
      default:
        break;
    }
  }

  synchronized boolean bad() {
    return slot || attack || swing || block || inventory;
  }

  synchronized boolean placementBad() {
    return attack || inventory;
  }

  void flight(int completedAirTicks) {
    airTicks = completedAirTicks;
  }

  boolean attemptWindow(boolean blockAtTenBelow) {
    if (airTicks > 3 && !blockAtTenBelow) toggle = 10;
    return toggle-- >= 0;
  }

  void underfoot(boolean replaceable) {
    onAir = replaceable ? onAir + 1 : 0;
  }

  int onAir() {
    return onAir;
  }

  void attempted() {
    onAir = 0;
  }

  boolean delayPassed(int min, int max, double fraction) {
    return onAir > Math.min(min, max) + fraction * Math.abs(max - min);
  }
}
