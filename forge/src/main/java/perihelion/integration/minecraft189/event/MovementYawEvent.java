package perihelion.integration.minecraft189.event;

/** Direction for local movement helpers and sprint-jump acceleration. */
public final class MovementYawEvent {
  private float yaw;
  private final boolean jump;

  public MovementYawEvent(float yaw) {
    this(yaw, false);
  }

  public MovementYawEvent(float yaw, boolean jump) {
    this.yaw = yaw;
    this.jump = jump;
  }

  public boolean jump() {
    return jump;
  }

  public float yaw() {
    return yaw;
  }

  public void yaw(float value) {
    if (!Float.isFinite(value)) throw new IllegalArgumentException("Nonfinite movement yaw");
    yaw = value;
  }
}
