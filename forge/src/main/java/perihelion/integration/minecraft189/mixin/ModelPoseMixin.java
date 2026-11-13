package perihelion.integration.minecraft189.mixin;

import net.minecraft.client.model.ModelPlayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.ModelPoseEvent;

@Mixin(ModelPlayer.class)
public abstract class ModelPoseMixin {
    @Inject(method = "setRotationAngles", at = @At("RETURN"))
    private void perihelion$pose(
            float limb,
            float amount,
            float age,
            float yaw,
            float pitch,
            float scale,
            Entity entity,
            CallbackInfo callback) {
        if (entity instanceof EntityPlayer)
            HookBridge.clientEvent(
                    new ModelPoseEvent((EntityPlayer) entity, (ModelPlayer) (Object) this));
    }
}
