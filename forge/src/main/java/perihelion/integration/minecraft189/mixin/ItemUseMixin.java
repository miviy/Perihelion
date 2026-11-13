package perihelion.integration.minecraft189.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.ItemSlowdownEvent;

@Mixin(EntityPlayerSP.class)
public abstract class ItemUseMixin {
  @ModifyConstant(method = "onLivingUpdate()V", constant = @Constant(floatValue = 0.2F))
  private float perihelion$slowdown(float original) {
    if ((Object) this != Minecraft.getMinecraft().thePlayer) return original;
    ItemSlowdownEvent event = new ItemSlowdownEvent();
    HookBridge.clientEvent(event);
    return event.isCancelled() ? 1 : event.factor();
  }
}
