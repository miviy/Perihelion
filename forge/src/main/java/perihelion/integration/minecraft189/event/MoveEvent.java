package perihelion.integration.minecraft189.event;

public final class MoveEvent {
    private double x, y, z;

    public MoveEvent(double x, double y, double z) {
        set(x, y, z);
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public void set(double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
            throw new IllegalArgumentException("Nonfinite movement");
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
