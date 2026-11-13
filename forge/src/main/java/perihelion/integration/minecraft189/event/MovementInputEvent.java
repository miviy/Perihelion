package perihelion.integration.minecraft189.event;

/** Local input after key/sneak sampling, before sprint checks and item-use slowdown. */
public final class MovementInputEvent {
  private float strafe, forward;

  public MovementInputEvent(float strafe, float forward) {
    input(strafe, forward);
  }

  public float strafe() {
    return strafe;
  }

  public float forward() {
    return forward;
  }

  public void input(float strafe, float forward) {
    if (!Float.isFinite(strafe) || !Float.isFinite(forward))
      throw new IllegalArgumentException("Nonfinite movement input");
    this.strafe = strafe;
    this.forward = forward;
  }
}
