// SPDX-License-Identifier: AGPL-3.0-only
// Adapted from Yuri 1cb86ad94ce1f01aa9b715577163c8ecc47b7def; Copyright (c) 2026 Unlegit Inc.
package perihelion.integration.minecraft189.module.gameplay;

/** Copied packet facts only: raw callbacks never inspect live player or setting state. */
final class ProjectilePacketGate {
  enum Kind {
    WALK,
    SLOT,
    ATTACK,
    INVENTORY,
    BLOCK,
    SWORD_USE,
    SWORD_RELEASE,
    OTHER
  }

  private boolean serverBlocking, block, attack, inventory, suppressing;

  synchronized void suppress(boolean value) {
    suppressing = value;
  }

  synchronized boolean serverBlocking() {
    return serverBlocking;
  }

  synchronized boolean canThrow() {
    return !block && !attack && !inventory;
  }

  synchronized void initialBlocking(boolean value) {
    serverBlocking = value;
  }

  synchronized void released() {
    serverBlocking = false;
  }

  synchronized void reset() {
    serverBlocking = block = attack = inventory = suppressing = false;
  }

  synchronized boolean accept(Kind kind, boolean own) {
    // The donor's own unblock bypasses its local blockThisTick bookkeeping.
    if (own) return true;
    switch (kind) {
      case WALK:
        block = attack = inventory = false;
        break;
      case SLOT:
        serverBlocking = false;
        break;
      case ATTACK:
        attack = true;
        break;
      case INVENTORY:
        inventory = true;
        break;
      case BLOCK:
        // AutoProjectile asks BadPacketsManager only about attack/inventory traffic.
        // Its separate blockThisTick flag tracks sword use/release exclusively.
        break;
      case SWORD_USE:
        if (suppressing && !own) return false;
        serverBlocking = block = true;
        break;
      case SWORD_RELEASE:
        if (!own && (suppressing || !serverBlocking)) return false;
        serverBlocking = false;
        block = true;
        break;
      default:
        break;
    }
    return true;
  }
}
