package perihelion.integration.minecraft189.event;

import net.minecraft.tileentity.TileEntity;

import perihelion.core.event.CancellableEvent;

/** Cancellation requests a scoped through-wall tile-entity draw. */
public final class TileRenderEvent extends CancellableEvent {
    private final TileEntity tile;

    public TileRenderEvent(TileEntity tile) {
        this.tile = tile;
    }

    public TileEntity tile() {
        return tile;
    }
}
