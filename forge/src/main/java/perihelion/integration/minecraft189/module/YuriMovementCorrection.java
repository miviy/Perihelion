// SPDX-License-Identifier: AGPL-3.0-only
// Adapted from Yuri 1cb86ad94ce1f01aa9b715577163c8ecc47b7def; Copyright (c) 2026 Unlegit Inc.
package perihelion.integration.minecraft189.module;

/** Yuri NORMAL's eight input directions, applied before sprint eligibility and item slowdown. */
public final class YuriMovementCorrection {
  private YuriMovementCorrection() {}

  /** Returns {strafe, forward}; the native post-input hook has already applied sneak magnitude. */
  public static float[] align(float strafe, float forward, float cameraYaw, float serverYaw) {
    if (!Float.isFinite(strafe)
        || !Float.isFinite(forward)
        || !Float.isFinite(cameraYaw)
        || !Float.isFinite(serverYaw))
      throw new IllegalArgumentException("Nonfinite movement correction");
    if (strafe == 0 && forward == 0) return new float[] {strafe, forward};
    double angle = wrap(direction(cameraYaw, forward, strafe));
    float bestForward = 0, bestStrafe = 0, best = Float.MAX_VALUE;
    for (float candidateForward = -1; candidateForward <= 1; candidateForward++) {
      for (float candidateStrafe = -1; candidateStrafe <= 1; candidateStrafe++) {
        if (candidateForward == 0 && candidateStrafe == 0) continue;
        double candidate = wrap(direction(serverYaw, candidateForward, candidateStrafe));
        double difference =
            Math.min(
                Math.abs(angle - candidate),
                Math.min(
                    Math.abs(angle - 360) - Math.abs(candidate),
                    Math.abs(candidate - 360) - Math.abs(angle)));
        if (difference < best) {
          best = (float) difference;
          bestForward = candidateForward;
          bestStrafe = candidateStrafe;
        }
      }
    }
    float magnitude = Math.max(Math.abs(forward), Math.abs(strafe));
    return new float[] {bestStrafe * magnitude, bestForward * magnitude};
  }

  private static double direction(float yaw, float forward, float strafe) {
    if (forward < 0) yaw += 180;
    float factor = forward < 0 ? -.5f : forward > 0 ? .5f : 1;
    if (strafe > 0) yaw -= 90 * factor;
    if (strafe < 0) yaw += 90 * factor;
    return Math.toDegrees(Math.toRadians(yaw));
  }

  private static double wrap(double value) {
    value %= 360;
    if (value >= 180) value -= 360;
    if (value < -180) value += 360;
    return value;
  }
}
