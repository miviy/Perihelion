package perihelion.integration.minecraft189.mixin;

import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.item.ItemSword;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.SwordPoseEvent;

@Mixin(ItemRenderer.class)
public abstract class SwordPoseMixin {
    @Redirect(
            method = "renderItemInFirstPerson(F)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/entity/AbstractClientPlayer;getItemInUseCount()I"))
    private int perihelion$swordPose(AbstractClientPlayer player) {
        if (player.getHeldItem() != null && player.getHeldItem().getItem() instanceof ItemSword) {
            SwordPoseEvent event = new SwordPoseEvent();
            HookBridge.clientEvent(event);
            if (event.isCancelled()) return Math.max(1, player.getItemInUseCount());
        }
        return player.getItemInUseCount();
    }
}
