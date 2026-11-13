package perihelion.integration.minecraft189.event;

import perihelion.core.event.CancellableEvent;

/** Native right-click boundary, after local-interaction policy and before vanilla selection use. */
public final class RightClickEvent extends CancellableEvent {
  private boolean normalRangeRay;

  /** Reach and Hitbox Expand restore the donor's ordinary 4.5-block right-click selection. */
  public void requestNormalRangeRay() {
    normalRangeRay = true;
  }

  public boolean normalRangeRay() {
    return normalRangeRay;
  }
}
