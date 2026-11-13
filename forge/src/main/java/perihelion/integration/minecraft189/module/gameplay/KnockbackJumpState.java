package perihelion.integration.minecraft189.module.gameplay;

/** One input opportunity per received knockback; never carry a jump across landing or a menu. */
final class KnockbackJumpState {
  private boolean pending;
  private long receivedAt;

  void receive(long now, boolean eligible) {
    pending = eligible;
    receivedAt = now;
  }

  boolean consume(long now) {
    boolean result = pending && now >= receivedAt && now - receivedAt <= 150;
    clear();
    return result;
  }

  void clear() {
    pending = false;
  }
}
