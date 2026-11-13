package perihelion.integration.minecraft189.gui.modern;

/** Directional lean only: pointer position and saved geometry never pass through this spring. */
final class DragSway {
  float angle;
  private float rate, dx, dy;

  void move(float x, float y) {
    dx += x;
    dy += y;
  }

  void advance(float seconds, boolean held) {
    float dt = Math.max(0, Math.min(.05f, seconds));
    if (dt == 0) return;
    float target = held ? Math.max(-1.4f, Math.min(1.4f, (-dx * .004f + dy * .0015f) / dt)) : 0;
    dx = dy = 0;
    // Exact critically damped response: consistent at different frame rates, with no idle shake.
    float omega = 24, decay = (float) Math.exp(-omega * dt);
    float offset = angle - target, momentum = rate + omega * offset;
    angle = Math.max(-1.6f, Math.min(1.6f, target + (offset + momentum * dt) * decay));
    rate = (rate - omega * momentum * dt) * decay;
    if (target == 0 && Math.abs(angle) < .015f && Math.abs(rate) < .1f) angle = rate = 0;
  }

  void reset() {
    angle = rate = dx = dy = 0;
  }
}
