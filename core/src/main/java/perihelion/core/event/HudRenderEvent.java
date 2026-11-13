package perihelion.core.event;

/**
 * Client-thread drawing after the in-game HUD, with Minecraft's scaled GUI projection.
 * Delivered once per completed overlay pass, including behind open in-game screens.
 * Not delivered with F1 hiding the GUI, without a world/player, or if the overlay is cancelled.
 * Renderers must restore their own temporary rendering state before returning or throwing.
 */
public final class HudRenderEvent {
    private final float partialTicks;
    private final int scaledWidth, scaledHeight, scaleFactor;

    public HudRenderEvent(float partialTicks, int scaledWidth, int scaledHeight, int scaleFactor) {
        this.partialTicks = partialTicks;
        this.scaledWidth = scaledWidth;
        this.scaledHeight = scaledHeight;
        this.scaleFactor = scaleFactor;
    }

    public float partialTicks() { return partialTicks; }
    /** GUI dimensions rounded up, matching Minecraft's ScaledResolution. */
    public int scaledWidth() { return scaledWidth; }
    public int scaledHeight() { return scaledHeight; }
    public int scaleFactor() { return scaleFactor; }
}
