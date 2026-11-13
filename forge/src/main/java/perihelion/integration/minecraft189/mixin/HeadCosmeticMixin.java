package perihelion.integration.minecraft189.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.control.MinecraftTimerAccess;
import perihelion.integration.minecraft189.event.PlayerHeadRenderEvent;

@Mixin(ModelPlayer.class)
public abstract class HeadCosmeticMixin {
  @Inject(method = "render", at = @At("RETURN"))
  private void perihelion$head(
      Entity entity,
      float limb,
      float amount,
      float age,
      float yaw,
      float pitch,
      float scale,
      CallbackInfo ci) {
    if (entity instanceof AbstractClientPlayer)
      HookBridge.clientEvent(
          new PlayerHeadRenderEvent(
              (AbstractClientPlayer) entity,
              ((ModelPlayer) (Object) this).bipedHead,
              ((MinecraftTimerAccess) Minecraft.getMinecraft())
                  .perihelion$timer()
                  .renderPartialTicks));
  }
}
