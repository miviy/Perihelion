package perihelion.integration.minecraft189.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.control.MinecraftControls;

/** Display the local submitted rotation without mutating the camera or other entities. */
@Mixin(RendererLivingEntity.class)
public abstract class RotationModelMixin {
  @Unique
  private float yaw(EntityLivingBase entity, float vanilla, boolean previous) {
    MinecraftControls controls = HookBridge.gameControls();
    return entity == Minecraft.getMinecraft().thePlayer && controls != null
        ? controls.modelYaw(vanilla, previous)
        : vanilla;
  }

  @Unique
  private float pitch(EntityLivingBase entity, float vanilla, boolean previous) {
    MinecraftControls controls = HookBridge.gameControls();
    return entity == Minecraft.getMinecraft().thePlayer && controls != null
        ? controls.modelPitch(vanilla, previous)
        : vanilla;
  }

  @Redirect(
      method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
      at =
          @At(
              value = "FIELD",
              target = "Lnet/minecraft/entity/EntityLivingBase;renderYawOffset:F",
              opcode = 180))
  private float perihelion$bodyYaw(EntityLivingBase entity) {
    return yaw(entity, entity.renderYawOffset, false);
  }

  @Redirect(
      method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
      at =
          @At(
              value = "FIELD",
              target = "Lnet/minecraft/entity/EntityLivingBase;prevRenderYawOffset:F",
              opcode = 180))
  private float perihelion$previousBodyYaw(EntityLivingBase entity) {
    return yaw(entity, entity.prevRenderYawOffset, true);
  }

  @Redirect(
      method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
      at =
          @At(
              value = "FIELD",
              target = "Lnet/minecraft/entity/EntityLivingBase;rotationYawHead:F",
              opcode = 180))
  private float perihelion$headYaw(EntityLivingBase entity) {
    return yaw(entity, entity.rotationYawHead, false);
  }

  @Redirect(
      method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
      at =
          @At(
              value = "FIELD",
              target = "Lnet/minecraft/entity/EntityLivingBase;prevRotationYawHead:F",
              opcode = 180))
  private float perihelion$previousHeadYaw(EntityLivingBase entity) {
    return yaw(entity, entity.prevRotationYawHead, true);
  }

  @Redirect(
      method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
      at =
          @At(
              value = "FIELD",
              target = "Lnet/minecraft/entity/EntityLivingBase;rotationPitch:F",
              opcode = 180))
  private float perihelion$headPitch(EntityLivingBase entity) {
    return pitch(entity, entity.rotationPitch, false);
  }

  @Redirect(
      method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
      at =
          @At(
              value = "FIELD",
              target = "Lnet/minecraft/entity/EntityLivingBase;prevRotationPitch:F",
              opcode = 180))
  private float perihelion$previousHeadPitch(EntityLivingBase entity) {
    return pitch(entity, entity.prevRotationPitch, true);
  }
}
