package perihelion.integration.minecraft189.control;

/** Last walking-packet angles, used as the mouse-sensitivity quantization origin. */
public interface PlayerRotationAccess {
  float perihelion$reportedYaw();

  float perihelion$reportedPitch();
  void perihelion$restoreReportedRotation(float expectedYaw, float expectedPitch, float yaw, float pitch);
  void perihelion$forceRotationResend(float yaw);
}
