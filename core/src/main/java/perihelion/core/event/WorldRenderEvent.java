package perihelion.core.event;

public final class WorldRenderEvent {
    private final float partialTicks;
    public WorldRenderEvent(float partialTicks) { this.partialTicks = partialTicks; }
    public float partialTicks() { return partialTicks; }
}
