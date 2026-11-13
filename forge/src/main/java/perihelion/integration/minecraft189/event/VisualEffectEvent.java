package perihelion.integration.minecraft189.event;

import perihelion.core.event.CancellableEvent;

/** Cancels only a local camera effect, without changing potion or game settings state. */
public final class VisualEffectEvent extends CancellableEvent {
  public enum Kind {
    HURT_CAMERA,
    VIEW_BOBBING,
    DYNAMIC_FOV,
    BLINDNESS,
    GAMMA,
    NAUSEA
  }

  private final Kind kind;

  public VisualEffectEvent(Kind kind) {
    this.kind = kind;
  }

  public Kind kind() {
    return kind;
  }
}
