package perihelion.integration.minecraft189.event;

/** Override the local visual arm-swing duration. */
public final class SwingDurationEvent {
    private int ticks;

    public SwingDurationEvent(int ticks) {
        this.ticks = ticks;
    }

    public int ticks() {
        return ticks;
    }

    public void ticks(int value) {
        ticks = Math.max(1, Math.min(1000, value));
    }
}
