package perihelion.integration.minecraft189.mixin;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.*;

@Mixin(ItemRenderer.class)
public abstract class HandEffectsMixin {
  @org.spongepowered.asm.mixin.Unique private FireAppearanceEvent perihelion$appearance;

  @Inject(method = "renderItemInFirstPerson", at = @At("HEAD"), cancellable = true)
  private void perihelion$hideHand(float partial, CallbackInfo callback) {
    OverlayEffectEvent event = new OverlayEffectEvent(OverlayEffectEvent.Kind.HAND);
    HookBridge.clientEvent(event);
    if (event.isCancelled()) callback.cancel();
  }

  @Redirect(
      method = "renderFireInFirstPerson",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/client/renderer/GlStateManager;color(FFFF)V"))
  private void perihelion$fireColor(float r, float g, float b, float a) {
    // The final white reset must remain unchanged.
    if (a < 1 && perihelion$appearance != null) {
      GlStateManager.color(
          perihelion$appearance.red,
          perihelion$appearance.green,
          perihelion$appearance.blue,
          perihelion$appearance.alpha);
    } else GlStateManager.color(r, g, b, a);
  }

  @ModifyArg(
      method = "renderFireInFirstPerson",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/client/renderer/GlStateManager;translate(FFF)V"),
      index = 1)
  private float perihelion$fireOffset(float y) {
    return y + (perihelion$appearance == null ? 0 : perihelion$appearance.offset);
  }

  @Inject(
      method = "renderItemInFirstPerson",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/client/renderer/GlStateManager;pushMatrix()V",
              shift = At.Shift.AFTER))
  private void perihelion$hand(float partial, CallbackInfo callback) {
    HandTransformEvent event = new HandTransformEvent();
    HookBridge.clientEvent(event);
    GlStateManager.translate(event.x, event.y, event.z);
    GlStateManager.scale(event.scale, event.scale, event.scale);
  }

  @Inject(method = "renderFireInFirstPerson", at = @At("HEAD"), cancellable = true)
  private void perihelion$fire(float partial, CallbackInfo callback) {
    OverlayEffectEvent event = new OverlayEffectEvent(OverlayEffectEvent.Kind.FIRE);
    HookBridge.clientEvent(event);
    if (event.isCancelled()) callback.cancel();
    perihelion$appearance = new FireAppearanceEvent();
    if (!event.isCancelled()) HookBridge.clientEvent(perihelion$appearance);
  }
}
