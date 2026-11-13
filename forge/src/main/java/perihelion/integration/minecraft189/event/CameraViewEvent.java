package perihelion.integration.minecraft189.event;

import perihelion.core.event.CancellableEvent;

/**
 * Cancel with an alternate camera orientation; the player's view and movement remain independent.
 */
public final class CameraViewEvent extends CancellableEvent {
  private float yaw, pitch;
  private final float partialTicks;

  public CameraViewEvent(float yaw, float pitch) {
    this(yaw, pitch, 1);
  }

  public CameraViewEvent(float yaw, float pitch, float partialTicks) {
    this.yaw = yaw;
    this.pitch = pitch;
    this.partialTicks = partialTicks;
  }

  public float partialTicks() {
    return partialTicks;
  }

  public float yaw() {
    return yaw;
  }

  public float pitch() {
    return pitch;
  }

  public void angles(float yaw, float pitch) {
    if (!Float.isFinite(yaw) || !Float.isFinite(pitch))
      throw new IllegalArgumentException("Nonfinite camera");
    this.yaw = yaw;
    this.pitch = Math.max(-90, Math.min(90, pitch));
    cancel();
  }
}
