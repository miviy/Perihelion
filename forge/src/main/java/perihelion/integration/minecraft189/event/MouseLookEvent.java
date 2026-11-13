package perihelion.integration.minecraft189.event;

import perihelion.core.event.CancellableEvent;

/** Vanilla sensitivity-adjusted mouse input, before it rotates the local player. */
public final class MouseLookEvent extends CancellableEvent {
    private final float yaw, pitch;

    public MouseLookEvent(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }
}
