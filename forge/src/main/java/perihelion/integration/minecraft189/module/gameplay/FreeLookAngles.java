package perihelion.integration.minecraft189.module.gameplay;

/** Mouse samples remain continuous across +/-180; render interpolation never moves the player. */
final class FreeLookAngles {
  private float yaw, pitch, previousYaw, previousPitch;

  FreeLookAngles(float yaw, float pitch) {
    this.yaw = previousYaw = yaw;
    this.pitch = previousPitch = pitch;
  }

  void mouse(float dx, float dy, boolean invert) {
    previousYaw = yaw;
    previousPitch = pitch;
    yaw += dx * .15f;
    pitch = Math.max(-90, Math.min(90, pitch + dy * .15f * (invert ? 1 : -1)));
  }

  float yaw(float partial) {
    return previousYaw + (yaw - previousYaw) * partial;
  }

  float pitch(float partial) {
    return previousPitch + (pitch - previousPitch) * partial;
  }
}
