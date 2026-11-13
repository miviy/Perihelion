package perihelion.integration.minecraft189.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.VisualEffectEvent.Kind;

@Mixin(AbstractClientPlayer.class)
public abstract class PlayerFovMixin {
    @Inject(method = "getFovModifier()F", at = @At("HEAD"), cancellable = true)
    private void perihelion$fov(CallbackInfoReturnable<Float> callback) {
        if ((Object) this == Minecraft.getMinecraft().thePlayer && HookBridge.suppressEffect(Kind.DYNAMIC_FOV)) {
            callback.setReturnValue(1.0f);
        }
    }
}
