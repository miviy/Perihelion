package perihelion.core.control;

/** One atomic yaw/pitch request. SERVER leaves the camera and local movement direction alone. */
public final class Rotation {
  public enum Mode {
    VIEW,
    SERVER
  }

  private final float yaw, pitch;
  private final Mode mode;

  private Rotation(float yaw, float pitch, Mode mode) {
    this(yaw, pitch, mode, true);
  }

  private Rotation(float yaw, float pitch, Mode mode, boolean normalizeYaw) {
    if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || pitch < -90 || pitch > 90) {
      throw new IllegalArgumentException("Rotation must be finite with pitch between -90 and 90");
    }
    float wrapped = yaw % 360;
    this.yaw =
        normalizeYaw
            ? (wrapped >= 180 ? wrapped - 360 : wrapped < -180 ? wrapped + 360 : wrapped)
            : yaw;
    this.pitch = pitch;
    this.mode = mode;
  }

  public static Rotation view(float yaw, float pitch) {
    return new Rotation(yaw, pitch, Mode.VIEW);
  }

  public static Rotation server(float yaw, float pitch) {
    return new Rotation(yaw, pitch, Mode.SERVER);
  }

  /** Preserve a sensitivity-rounded, continuous server yaw across the +/-180 boundary. */
  public static Rotation serverUnwrapped(float yaw, float pitch) {
    return new Rotation(yaw, pitch, Mode.SERVER, false);
  }

  public float yaw() {
    return yaw;
  }

  public float pitch() {
    return pitch;
  }

  public Mode mode() {
    return mode;
  }
}
