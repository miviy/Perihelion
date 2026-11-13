package perihelion.integration.minecraft189.event;

import perihelion.core.control.Rotation;

/** Local render-only orientation; subscribers are activation scoped. */
public final class ModelRotationEvent {
  private Rotation rotation;
  private final boolean previous;

  public ModelRotationEvent(Rotation rotation, boolean previous) {
    this.rotation = rotation;
    this.previous = previous;
  }

  public Rotation rotation() {
    return rotation;
  }

  public boolean previous() {
    return previous;
  }

  public void angles(float yaw, float pitch) {
    rotation = Rotation.server(yaw, pitch);
  }
}
