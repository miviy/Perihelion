package perihelion.integration.minecraft189.event;

import perihelion.core.event.CancellableEvent;

public final class OverlayEffectEvent extends CancellableEvent {
  public enum Kind {
    FIRE,
    HAND,
    BOSS_BAR
  }

  private final Kind kind;

  public OverlayEffectEvent(Kind kind) {
    this.kind = kind;
  }

  public Kind kind() {
    return kind;
  }
}
