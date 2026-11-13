package perihelion.integration.minecraft189.event;

import perihelion.core.event.CancellableEvent;

public final class MouseScrollEvent extends CancellableEvent {
  private final int delta;

  public MouseScrollEvent(int delta) {
    this.delta = delta;
  }

  public int delta() {
    return delta;
  }
}
