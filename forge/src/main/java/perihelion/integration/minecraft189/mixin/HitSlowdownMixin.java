package perihelion.integration.minecraft189.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.HitSlowdownEvent;

@Mixin(EntityPlayer.class)
public abstract class HitSlowdownMixin {
    @Unique private HitSlowdownEvent perihelion$hit;

    @Inject(method = "attackTargetEntityWithCurrentItem", at = @At("HEAD"))
    private void perihelion$reset(Entity target, CallbackInfo callback) {
        perihelion$hit = null;
    }

    @ModifyConstant(
            method = "attackTargetEntityWithCurrentItem",
            constant = @Constant(doubleValue = 0.6))
    private double perihelion$factor(double vanilla) {
        if ((Object) this != Minecraft.getMinecraft().thePlayer) return vanilla;
        if (perihelion$hit == null) {
            perihelion$hit = new HitSlowdownEvent();
            HookBridge.clientEvent(perihelion$hit);
        }
        return perihelion$hit.factor();
    }

    @Redirect(
            method = "attackTargetEntityWithCurrentItem",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/entity/player/EntityPlayer;setSprinting(Z)V"))
    private void perihelion$sprint(EntityPlayer player, boolean sprint) {
        player.setSprinting(
                player == Minecraft.getMinecraft().thePlayer && perihelion$hit != null
                        ? perihelion$hit.sprint()
                        : sprint);
    }
}
