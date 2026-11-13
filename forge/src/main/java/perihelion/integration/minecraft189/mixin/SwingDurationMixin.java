package perihelion.integration.minecraft189.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.SwingDurationEvent;

@Mixin(EntityLivingBase.class)
public abstract class SwingDurationMixin {
    @Inject(method = "getArmSwingAnimationEnd", at = @At("RETURN"), cancellable = true)
    private void perihelion$swing(CallbackInfoReturnable<Integer> callback) {
        if ((Object) this != Minecraft.getMinecraft().thePlayer) return;
        SwingDurationEvent event = new SwingDurationEvent(callback.getReturnValue());
        HookBridge.clientEvent(event);
        callback.setReturnValue(event.ticks());
    }
}
