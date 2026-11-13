package perihelion.integration.minecraft189.module;

/** OpenB4's post-input, eight-direction movement correction; no client state. */
public final class B4MovementCorrection {
  private B4MovementCorrection() {}

  /** Returns {strafe, forward}, preserving the largest sampled input magnitude. */
  public static float[] align(float strafe, float forward, float cameraYaw, float serverYaw) {
    if (!Float.isFinite(strafe) || !Float.isFinite(forward)
        || !Float.isFinite(cameraYaw) || !Float.isFinite(serverYaw))
      throw new IllegalArgumentException("Nonfinite movement correction");
    if (strafe == 0 && forward == 0) return new float[] {strafe, forward};
    float target = wrap(moveYaw(cameraYaw, forward, strafe));
    float bestForward = 0, bestStrafe = 0, best = -1;
    for (float candidateForward = -1; candidateForward <= 1; candidateForward += 1) {
      for (float candidateStrafe = -1; candidateStrafe <= 1; candidateStrafe += 1) {
        if (candidateForward == 0 && candidateStrafe == 0) continue;
        float yaw = wrap(moveYaw(serverYaw, candidateForward, candidateStrafe));
        float difference = Math.abs(wrap(target - yaw));
        // Strict comparison retains the first candidate at an exact angular tie.
        if (best != -1 && !(difference < best)) continue;
        best = difference;
        bestForward = candidateForward;
        bestStrafe = candidateStrafe;
      }
    }
    float magnitude = Math.max(Math.abs(forward), Math.abs(strafe));
    return new float[] {bestStrafe * magnitude, bestForward * magnitude};
  }

  private static float moveYaw(float yaw, float forward, float strafe) {
    float factor = 1;
    if (forward < 0) {
      yaw += 180;
      factor = -.5F;
    } else if (forward > 0) factor = .5F;
    if (strafe > 0) yaw -= 90 * factor;
    else if (strafe < 0) yaw += 90 * factor;
    return yaw;
  }

  private static float wrap(float angle) {
    angle %= 360;
    if (angle >= 180) angle -= 360;
    if (angle < -180) angle += 360;
    return angle;
  }
}
