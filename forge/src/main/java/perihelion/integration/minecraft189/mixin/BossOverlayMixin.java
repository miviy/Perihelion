package perihelion.integration.minecraft189.mixin;

import net.minecraft.client.gui.GuiIngame;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.OverlayEffectEvent;

@Mixin(GuiIngame.class)
public abstract class BossOverlayMixin {
    @Inject(method = "renderBossHealth", at = @At("HEAD"), cancellable = true)
    private void perihelion$boss(CallbackInfo callback) {
        OverlayEffectEvent event = new OverlayEffectEvent(OverlayEffectEvent.Kind.BOSS_BAR);
        HookBridge.clientEvent(event);
        if (event.isCancelled()) callback.cancel();
    }
}
