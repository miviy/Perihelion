package perihelion.integration.minecraft189.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import net.minecraft.util.Timer;
import perihelion.integration.minecraft189.control.MinecraftTimerAccess;
import perihelion.integration.minecraft189.HookBridge;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftLifecycleMixin implements MinecraftTimerAccess, perihelion.integration.minecraft189.control.ItemUseDelayAccess {
    @Shadow private Timer timer;
    @Shadow private int rightClickDelayTimer;
    @Override public int perihelion$itemUseDelay() { return rightClickDelayTimer; }
    @Override public void perihelion$itemUseDelay(int ticks) { rightClickDelayTimer = ticks; }
    @Override public Timer perihelion$timer() { return timer; }
    @Inject(method = "shutdownMinecraftApplet()V", at = @At("HEAD"))
    private void perihelion$stop(CallbackInfo callback) {
        HookBridge.stop();
    }
}
