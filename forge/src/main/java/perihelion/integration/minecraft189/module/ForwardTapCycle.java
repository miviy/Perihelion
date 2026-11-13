package perihelion.integration.minecraft189.module;

/** Activation-local timing. A GUI, release or mode change cancels rather than defers a tap. */
final class ForwardTapCycle {
  private long start, end, next;
  private boolean pending;

  void attack(long now, long delay, long duration) {
    if (pending || now < next) return;
    start = now + delay;
    end = start + duration;
    next = now + 500;
    pending = true;
  }

  boolean update(long now, boolean eligible) {
    if (!eligible || now >= end) pending = false;
    return pending && now >= start;
  }

  void clear() {
    pending = false;
    start = end = next = 0;
  }
}
