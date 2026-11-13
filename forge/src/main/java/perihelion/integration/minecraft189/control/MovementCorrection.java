package perihelion.integration.minecraft189.control;

import perihelion.integration.minecraft189.event.StrafeEvent;

/** Changes input basis while preserving its world direction and analogue magnitude. */
public final class MovementCorrection {
  private MovementCorrection() {}

  public static void align(StrafeEvent event, float viewYaw, float rotationYaw) {
    double difference = Math.toRadians((double) viewYaw - rotationYaw);
    double sin = Math.sin(difference), cos = Math.cos(difference);
    float strafe = (float) (event.strafe() * cos - event.forward() * sin);
    float forward = (float) (event.forward() * cos + event.strafe() * sin);
    // moveFlying receives already-scaled sneak/use-item input. Rounding here stops
    // slow movement entirely and changes diagonal speed at intermediate angles.
    event.input(strafe, forward);
    event.yaw(rotationYaw);
  }
}
