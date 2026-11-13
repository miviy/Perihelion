package perihelion.integration.minecraft189.event;

/** Final world FOV only; does not write the user's saved FOV preference. */
public final class CameraFovEvent {
  private float value;

  public CameraFovEvent(float value) {
    this.value = value;
  }

  public float value() {
    return value;
  }

  public void value(float value) {
    if (!Float.isFinite(value)) throw new IllegalArgumentException("Nonfinite FOV");
    this.value = Math.max(1, Math.min(179, value));
  }
}
