package perihelion.integration.minecraft189.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.MovementYawEvent;

@Mixin(EntityLivingBase.class)
public abstract class JumpDirectionMixin {
  @Redirect(
      method = "jump",
      at =
          @At(
              value = "FIELD",
              target = "Lnet/minecraft/entity/EntityLivingBase;rotationYaw:F",
              opcode = 180))
  private float perihelion$jumpYaw(EntityLivingBase entity) {
    if (entity != Minecraft.getMinecraft().thePlayer) return entity.rotationYaw;
    MovementYawEvent event = new MovementYawEvent(entity.rotationYaw, true);
    HookBridge.clientEvent(event);
    return event.yaw();
  }
}
