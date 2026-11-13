package perihelion.integration.minecraft189.module.gameplay;

/** OpenB4 Water Bucket's landing pickup window, owned by one NoFall activation. */
final class B4WaterPickupState {
  private int slot = -1;
  private long placedAt;
  private boolean completed;

  void placed(int slot, long now) {
    this.slot = slot;
    placedAt = now;
    completed = false;
  }

  int slot() {
    return slot;
  }

  boolean waiting() {
    return slot >= 0;
  }

  boolean ready(long now, boolean ground, boolean bucket) {
    if (!waiting()) return false;
    if (completed || ground && !bucket || !ground && now - placedAt > 500) {
      reset();
      return false;
    }
    return ground && bucket;
  }

  void pickedUp() {
    completed = true;
  }

  void reset() {
    slot = -1;
    completed = false;
  }
}
