package perihelion.integration.minecraft189.event;

/** Local successful sprint-hit slowdown and sprint continuation. */
public final class HitSlowdownEvent {
    private double factor = 0.6;
    private boolean sprint;

    public double factor() {
        return factor;
    }

    public boolean sprint() {
        return sprint;
    }

    public void factor(double value) {
        if (!Double.isFinite(value) || value < 0 || value > 1)
            throw new IllegalArgumentException("Invalid hit factor");
        factor = value;
    }

    public void sprint(boolean value) {
        sprint = value;
    }
}
