package perihelion.integration.minecraft189.event;

import perihelion.core.event.CancellableEvent;

/** Runs immediately before vanilla moveFlying, with current movement input. */
public final class StrafeEvent extends CancellableEvent {
    private float strafe, forward, friction, yaw;

    public StrafeEvent(float strafe, float forward, float friction, float yaw) {
        this.strafe = strafe;
        this.forward = forward;
        this.friction = friction;
        this.yaw = yaw;
    }

    public float strafe() {
        return strafe;
    }

    public float forward() {
        return forward;
    }

    public float friction() {
        return friction;
    }

    public float yaw() {
        return yaw;
    }

    public void friction(float value) {
        finite(value);
        friction = value;
    }

    public void yaw(float value) {
        finite(value);
        yaw = value;
    }

    public void input(float strafe, float forward) {
        finite(strafe);
        finite(forward);
        this.strafe = strafe;
        this.forward = forward;
    }

    private static void finite(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Nonfinite strafe");
    }
}
