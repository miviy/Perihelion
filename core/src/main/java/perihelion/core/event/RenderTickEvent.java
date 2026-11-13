package perihelion.core.event;

public final class RenderTickEvent {
    public enum Phase { START, END }
    private final Phase phase;
    private final float partialTicks;
    public RenderTickEvent(Phase phase, float partialTicks) { this.phase = phase; this.partialTicks = partialTicks; }
    public Phase phase() { return phase; }
    public float partialTicks() { return partialTicks; }
}
