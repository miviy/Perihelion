package perihelion.integration.minecraft189.mixin;

import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.TileRenderEvent;
import perihelion.integration.minecraft189.render.RenderState;

@Mixin(TileEntityRendererDispatcher.class)
public abstract class TileRenderMixin {
    @Unique private final RenderState perihelion$tileState = new RenderState();

    @Redirect(
            method = "renderTileEntityAt(Lnet/minecraft/tileentity/TileEntity;DDDFI)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer;renderTileEntityAt(Lnet/minecraft/tileentity/TileEntity;DDDFI)V"))
    private void perihelion$tile(
            TileEntitySpecialRenderer<TileEntity> renderer,
            TileEntity tile,
            double x,
            double y,
            double z,
            float partial,
            int stage) {
        TileRenderEvent event = new TileRenderEvent(tile);
        HookBridge.clientEvent(event);
        if (!event.isCancelled()) {
            renderer.renderTileEntityAt(tile, x, y, z, partial, stage);
            return;
        }
        try (RenderState ignored = perihelion$tileState.open()) {
            GL11.glDepthRange(0, .01);
            renderer.renderTileEntityAt(tile, x, y, z, partial, stage);
        }
    }
}
