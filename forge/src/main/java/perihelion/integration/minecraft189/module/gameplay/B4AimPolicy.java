package perihelion.integration.minecraft189.module.gameplay;

/** B4 u20 AimAssist rate, vector step and sensitivity grid; randomness is supplied for testing. */
final class B4AimPolicy {
  private B4AimPolicy() {}

  static float targetYaw(double dx, double dz) {
    return (float) -(Math.atan2(dx, dz) * (double) 57.29578f);
  }

  static float[] step(
      float yaw,
      float pitch,
      float wantedYaw,
      float wantedPitch,
      double speed,
      double fov,
      int nearRandom,
      float sensitivity,
      int jitter) {
    // toEntityBox unwraps against the current server rotation, including the snap branch.
    float nextYaw = yaw + wrap(wantedYaw - yaw), nextPitch = wantedPitch;
    if (speed != 100) {
      float dy = wrap(wantedYaw - yaw), dp = wantedPitch - pitch;
      double s = speed / 100;
      double rate = .3 + 14.7 * s * s;
      double ay = Math.abs(dy), ap = Math.abs(dp);
      double near = Math.min(1, ay / Math.max(10, fov));
      double step = rate * (.3 + (5 + nearRandom) * near);
      double divisor = Math.max(1, Math.max(ay / Math.max(1e-6, step), ap / Math.max(1e-6, step)));
      nextYaw = yaw + (float) Math.copySign(Math.min(ay, ay / divisor), dy);
      nextPitch = clamp(pitch + (float) Math.copySign(Math.min(ap, ap / divisor), dp));
    }
    float scale = sensitivity * .6f + .2f;
    double unit = (double) (scale * scale * scale) * 1.2;
    float dy = nextYaw - yaw;
    nextYaw = yaw + (float) (Math.round(dy / unit) * unit);
    nextPitch = pitch + (float) (Math.round((nextPitch - pitch) / unit) * unit);
    if (Math.abs(dy) >= unit) nextYaw += (float) (jitter * unit);
    return new float[] {nextYaw, clamp(nextPitch)};
  }

  static float wrap(float value) {
    value %= 360;
    return value >= 180 ? value - 360 : value < -180 ? value + 360 : value;
  }

  private static float clamp(float value) {
    return Math.max(-90, Math.min(90, value));
  }
}
