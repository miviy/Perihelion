package perihelion.integration.minecraft189.mixin;

import net.minecraft.client.settings.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import perihelion.integration.minecraft189.HookBridge;

@Mixin(KeyBinding.class)
public abstract class KeyBindingMixin {
    @Inject(method = "isKeyDown()Z", at = @At("RETURN"), cancellable = true)
    private void perihelion$heldState(CallbackInfoReturnable<Boolean> callback) {
        callback.setReturnValue(HookBridge.keyState((KeyBinding) (Object) this, callback.getReturnValue()));
    }
}
