package perihelion.integration.minecraft189.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.VisualEffectEvent.Kind;

@Mixin(EntityRenderer.class)
public abstract class CameraEffectsMixin {
  @Inject(method = "getFOVModifier", at = @At("RETURN"), cancellable = true)
  private void perihelion$zoom(
      float partial,
      boolean worldFov,
      org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Float> callback) {
    if (!worldFov) return;
    perihelion.integration.minecraft189.event.CameraFovEvent event =
        new perihelion.integration.minecraft189.event.CameraFovEvent(callback.getReturnValue());
    HookBridge.clientEvent(event);
    callback.setReturnValue(event.value());
  }

  @Redirect(
      method = "setupCameraTransform",
      at =
          @At(
              value = "FIELD",
              target = "Lnet/minecraft/client/entity/EntityPlayerSP;prevTimeInPortal:F",
              opcode = 180))
  private float perihelion$previousPortal(net.minecraft.client.entity.EntityPlayerSP player) {
    return HookBridge.suppressEffect(Kind.NAUSEA) && player.isPotionActive(Potion.confusion)
        ? 0
        : player.prevTimeInPortal;
  }

  @Redirect(
      method = "setupCameraTransform",
      at =
          @At(
              value = "FIELD",
              target = "Lnet/minecraft/client/entity/EntityPlayerSP;timeInPortal:F",
              opcode = 180))
  private float perihelion$portal(net.minecraft.client.entity.EntityPlayerSP player) {
    return HookBridge.suppressEffect(Kind.NAUSEA) && player.isPotionActive(Potion.confusion)
        ? 0
        : player.timeInPortal;
  }

  @Unique private boolean perihelion$fullbright;

  @Inject(method = "updateLightmap(F)V", at = @At("HEAD"))
  private void perihelion$lightmap(float partialTicks, CallbackInfo callback) {
    perihelion$fullbright = HookBridge.suppressEffect(Kind.GAMMA);
  }

  @Inject(method = "hurtCameraEffect(F)V", at = @At("HEAD"), cancellable = true)
  private void perihelion$hurt(float partialTicks, CallbackInfo callback) {
    if (localView() && HookBridge.suppressEffect(Kind.HURT_CAMERA)) callback.cancel();
  }

  @Inject(method = "setupViewBobbing(F)V", at = @At("HEAD"), cancellable = true)
  private void perihelion$bob(float partialTicks, CallbackInfo callback) {
    if (localView() && HookBridge.suppressEffect(Kind.VIEW_BOBBING)) callback.cancel();
  }

  @Redirect(
      method = {"setupFog(IF)V", "updateFogColor(F)V"},
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/entity/EntityLivingBase;isPotionActive(Lnet/minecraft/potion/Potion;)Z"))
  private boolean perihelion$blindness(EntityLivingBase entity, Potion potion) {
    if (potion == Potion.blindness
        && entity == Minecraft.getMinecraft().thePlayer
        && HookBridge.suppressEffect(Kind.BLINDNESS)) return false;
    return entity.isPotionActive(potion);
  }

  @Redirect(
      method = "updateLightmap(F)V",
      at =
          @At(
              value = "FIELD",
              target = "Lnet/minecraft/client/settings/GameSettings;gammaSetting:F",
              opcode = 180))
  private float perihelion$gamma(GameSettings settings) {
    return perihelion$fullbright ? 100.0f : settings.gammaSetting;
  }

  private static boolean localView() {
    Minecraft game = Minecraft.getMinecraft();
    return game.thePlayer != null && game.getRenderViewEntity() == game.thePlayer;
  }
}
