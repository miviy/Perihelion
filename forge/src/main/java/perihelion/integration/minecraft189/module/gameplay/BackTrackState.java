// SPDX-License-Identifier: AGPL-3.0-only
// Adapted from Yuri 1cb86ad94ce1f01aa9b715577163c8ecc47b7def; Copyright (c) 2026 Unlegit Inc.
package perihelion.integration.minecraft189.module.gameplay;

/** Packet-independent Back Track policy and fixed-point server-position history. */
final class BackTrackState {
  enum Mode {
    BASIC,
    MMC,
    HYPIXEL
  }

  enum Style {
    NEW,
    OLD
  }

  static final class Policy {
    final Mode mode;
    final Style style;
    final long delay;
    final double minRange, maxRange;

    Policy(Mode mode, Style style, long delay, double minRange, double maxRange) {
      this.mode = mode;
      this.style = style;
      this.delay = mode == Mode.MMC ? 1000 : mode == Mode.HYPIXEL ? 250 : delay;
      this.minRange = mode == Mode.MMC ? 0 : minRange;
      this.maxRange = mode == Mode.MMC ? 8 : maxRange;
    }

    boolean legacy() {
      return mode == Mode.HYPIXEL || mode == Mode.BASIC && style == Style.OLD;
    }

    boolean inRange(double distance) {
      return distance >= minRange && distance <= maxRange;
    }
  }

  static final class Position {
    final double x, y, z;

    Position(int x, int y, int z) {
      this.x = x / 32.0;
      this.y = y / 32.0;
      this.z = z / 32.0;
    }
  }

  static final class AttackPosition {
    final long session;
    final int targetId;
    final Position position;

    AttackPosition(long session, int targetId, Position position) {
      this.session = session;
      this.targetId = targetId;
      this.position = position;
    }
  }

  Policy policy;
  boolean holding, flush;
  int targetId = -1, localId = -1;
  private int realX, realY, realZ;
  private long started;
  private long session;

  BackTrackState(Policy policy) {
    this.policy = policy;
  }

  /** A different capture policy must drain old deadlines before it starts collecting again. */
  boolean changePolicy(Policy next) {
    boolean flushHeld = policy.legacy() != next.legacy();
    policy = next;
    if (flushHeld) stop();
    return flushHeld;
  }

  void begin(int targetId, int localId, int x, int y, int z, long now) {
    session++;
    this.targetId = targetId;
    this.localId = localId;
    realX = x;
    realY = y;
    realZ = z;
    started = now;
    holding = true;
    flush = false;
  }

  boolean captures(boolean movement, boolean flushPacket) {
    return holding && (!policy.legacy() || movement || flushPacket);
  }

  long releaseAt(long now, boolean flushPacket) {
    return !policy.legacy() ? now + policy.delay : flushPacket ? 0 : Long.MAX_VALUE;
  }

  void captured(boolean flushPacket) {
    if (policy.legacy() && flushPacket) flush = true;
  }

  boolean expired(long now) {
    return holding && policy.legacy() && now - started >= policy.delay;
  }

  void move(int id, int dx, int dy, int dz) {
    if (!holding || id != targetId) return;
    realX += dx;
    realY += dy;
    realZ += dz;
  }

  void teleport(int id, int x, int y, int z) {
    if (!holding || id != targetId) return;
    realX = x;
    realY = y;
    realZ = z;
  }

  Position position() {
    return holding ? new Position(realX, realY, realZ) : null;
  }

  AttackPosition attackPosition(int id) {
    return holding && policy.legacy() && targetId == id
        ? new AttackPosition(session, id, position()) : null;
  }

  boolean accepts(AttackPosition pending) {
    return pending != null && holding && policy.legacy()
        && session == pending.session && targetId == pending.targetId;
  }

  void stop() {
    session++;
    holding = flush = false;
    targetId = localId = -1;
  }
}
