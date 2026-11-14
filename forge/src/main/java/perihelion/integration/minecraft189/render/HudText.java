package perihelion.integration.minecraft189.render;

import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

/** Native-size, GUI-coordinate text using Minecraft's current resource-pack/Unicode font. */
public final class HudText {
    private final RenderState state = new RenderState();

    public int width(String text) { return font().getStringWidth(Objects.requireNonNull(text, "text")); }
    public int height() { return font().FONT_HEIGHT; }

    /** ARGB, including explicit alpha. The caller supplies the normal scaled GUI projection. */
    public void draw(String text, float x, float y, int argb, boolean shadow) {
        try (RenderState ignored = state.open()) { drawInScope(text, x, y, argb, shadow); }
    }

    /** Batch multiple labels inside the caller's RenderState, avoiding a snapshot per label. */
    public void drawInScope(String text, float x, float y, int argb, boolean shadow) {
        RenderState.requireOpen();
        Objects.requireNonNull(text, "text");
        if (!Float.isFinite(x) || !Float.isFinite(y))
            throw new IllegalArgumentException("Text coordinates must be finite");
        FontRenderer font = font();
        if (text.isEmpty() || (argb >>> 24) == 0) return;
        RenderStyle.text();
        // Callers align to their framebuffer grid; rounding again in local GUI units
        // would undo that alignment under menu and HUD transforms.
        font.drawString(text, x, y, fontColor(argb), shadow);
    }

    static int fontColor(int argb) {
        // Vanilla interprets alpha 0..3 as unspecified/opaque. Avoid opaque flashes at
        // the end of fades: draw skips zero and clamps the remaining values to 4/255.
        return (argb >>> 24) < 4 ? (argb & 0x00ffffff) | 0x04000000 : argb;
    }

    private static FontRenderer font() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (!minecraft.isCallingFromMinecraftThread()) throw new IllegalStateException("Text requires the Minecraft client thread");
        return minecraft.fontRendererObj;
    }
}
