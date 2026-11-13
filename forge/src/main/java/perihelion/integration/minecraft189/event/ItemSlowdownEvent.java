package perihelion.integration.minecraft189.event;

import perihelion.core.event.CancellableEvent;

public final class ItemSlowdownEvent extends CancellableEvent {
  private float factor = .2f;

  public float factor() {
    return factor;
  }

  public void factor(float value) {
    if (!Float.isFinite(value) || value < 0 || value > 1)
      throw new IllegalArgumentException("Invalid item slowdown factor");
    factor = value;
  }
}
