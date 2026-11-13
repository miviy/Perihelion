package perihelion.integration.minecraft189.event;

/** Client-thread walking-packet coordinates; does not move the local player. */
public final class MotionPacketEvent {
    private double y;
    private boolean grounded;

    public MotionPacketEvent(double y, boolean grounded) {
        this.y = y;
        this.grounded = grounded;
    }

    public double y() {
        return y;
    }

    public void y(double y) {
        if (!Double.isFinite(y)) throw new IllegalArgumentException("Nonfinite motion");
        this.y = y;
    }

    public boolean grounded() {
        return grounded;
    }

    public void grounded(boolean value) {
        grounded = value;
    }
}
