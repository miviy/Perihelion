package perihelion.integration.minecraft189.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.*;

@Mixin(EntityRenderer.class)
public abstract class FreeLookMixin {
  @Unique private CameraViewEvent perihelion$view;

  @Inject(method = "orientCamera", at = @At("HEAD"))
  private void perihelion$orient(float partial, CallbackInfo callback) {
    Entity entity = Minecraft.getMinecraft().getRenderViewEntity();
    perihelion$view = new CameraViewEvent(entity.rotationYaw, entity.rotationPitch, partial);
    if (entity == Minecraft.getMinecraft().thePlayer) HookBridge.clientEvent(perihelion$view);
  }

  @Redirect(
      method = "orientCamera",
      at =
          @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;rotationYaw:F", opcode = 180))
  private float perihelion$yaw(Entity entity) {
    return perihelion$view.isCancelled() ? perihelion$view.yaw() : entity.rotationYaw;
  }

  @Redirect(
      method = "orientCamera",
      at =
          @At(
              value = "FIELD",
              target = "Lnet/minecraft/entity/Entity;prevRotationYaw:F",
              opcode = 180))
  private float perihelion$previousYaw(Entity entity) {
    return perihelion$view.isCancelled() ? perihelion$view.yaw() : entity.prevRotationYaw;
  }

  @Redirect(
      method = "orientCamera",
      at =
          @At(
              value = "FIELD",
              target = "Lnet/minecraft/entity/Entity;rotationPitch:F",
              opcode = 180))
  private float perihelion$pitch(Entity entity) {
    return perihelion$view.isCancelled() ? perihelion$view.pitch() : entity.rotationPitch;
  }

  @Redirect(
      method = "orientCamera",
      at =
          @At(
              value = "FIELD",
              target = "Lnet/minecraft/entity/Entity;prevRotationPitch:F",
              opcode = 180))
  private float perihelion$previousPitch(Entity entity) {
    return perihelion$view.isCancelled() ? perihelion$view.pitch() : entity.prevRotationPitch;
  }

  @Redirect(
      method = "updateCameraAndRender",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/client/entity/EntityPlayerSP;setAngles(FF)V"))
  private void perihelion$mouse(EntityPlayerSP player, float yaw, float pitch) {
    MouseLookEvent event = new MouseLookEvent(yaw, pitch);
    HookBridge.clientEvent(event);
    if (!event.isCancelled()) player.setAngles(yaw, pitch);
  }
}
