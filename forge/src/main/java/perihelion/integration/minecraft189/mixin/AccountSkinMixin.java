package perihelion.integration.minecraft189.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import perihelion.integration.minecraft189.account.LocalAccountSkin;

@Mixin(AbstractClientPlayer.class)
public abstract class AccountSkinMixin {
  @Inject(method = "getLocationSkin", at = @At("HEAD"), cancellable = true)
  private void perihelion$localSkin(CallbackInfoReturnable<ResourceLocation> ci) {
    if ((Object) this == Minecraft.getMinecraft().thePlayer && LocalAccountSkin.location() != null)
      ci.setReturnValue(LocalAccountSkin.location());
  }

  @Inject(method = "getSkinType", at = @At("HEAD"), cancellable = true)
  private void perihelion$localModel(CallbackInfoReturnable<String> ci) {
    if ((Object) this == Minecraft.getMinecraft().thePlayer && LocalAccountSkin.location() != null)
      ci.setReturnValue(LocalAccountSkin.model());
  }
}
