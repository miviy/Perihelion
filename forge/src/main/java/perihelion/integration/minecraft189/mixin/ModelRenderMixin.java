package perihelion.integration.minecraft189.mixin;

import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.ModelRenderEvent;
import perihelion.integration.minecraft189.event.NameRenderEvent;
import perihelion.integration.minecraft189.render.RenderState;

@Mixin(RendererLivingEntity.class)
public abstract class ModelRenderMixin {
    @Inject(
            method = "renderName(Lnet/minecraft/entity/EntityLivingBase;DDD)V",
            at = @At("HEAD"),
            cancellable = true)
    private void perihelion$name(
            EntityLivingBase entity, double x, double y, double z, CallbackInfo callback) {
        // Runs for every living entity each frame; build the event only for a listener.
        if (!HookBridge.wantsClientEvent(NameRenderEvent.class)) return;
        NameRenderEvent event = new NameRenderEvent(entity);
        HookBridge.clientEvent(event);
        if (event.isCancelled()) callback.cancel();
    }

    @Shadow
    protected abstract void renderModel(
            EntityLivingBase entity,
            float limb,
            float amount,
            float age,
            float yaw,
            float pitch,
            float scale);

    @Unique private final RenderState perihelion$modelState = new RenderState();

    @Redirect(
            method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/entity/RendererLivingEntity;renderModel(Lnet/minecraft/entity/EntityLivingBase;FFFFFF)V"))
    private void perihelion$model(
            RendererLivingEntity<?> renderer,
            EntityLivingBase entity,
            float limb,
            float amount,
            float age,
            float yaw,
            float pitch,
            float scale) {
        if (!HookBridge.wantsClientEvent(ModelRenderEvent.class)) {
            renderModel(entity, limb, amount, age, yaw, pitch, scale);
            return;
        }
        ModelRenderEvent event = new ModelRenderEvent(entity);
        HookBridge.clientEvent(event);
        if (!event.isCancelled()) {
            renderModel(entity, limb, amount, age, yaw, pitch, scale);
            return;
        }
        try (RenderState ignored = perihelion$modelState.open()) {
            GL11.glDepthRange(0, 0.01);
            renderModel(entity, limb, amount, age, yaw, pitch, scale);
        }
    }
}
