package perihelion.integration.minecraft189.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.MoveEvent;
import perihelion.integration.minecraft189.event.SafeWalkEvent;
import perihelion.integration.minecraft189.event.StrafeEvent;

@Mixin(Entity.class)
public abstract class EntityMovementMixin
    implements perihelion.integration.minecraft189.control.EntityStateAccess {
  @Shadow protected boolean isInWeb;

  public boolean perihelion$inWeb() {
    return isInWeb;
  }

  @Shadow public double motionX, motionZ;
  @Shadow public float rotationYaw;

  @Shadow
  public abstract void moveEntity(double x, double y, double z);

  @Unique private boolean perihelion$moving;

  @Inject(method = "moveEntity(DDD)V", at = @At("HEAD"), cancellable = true)
  private void perihelion$move(double x, double y, double z, CallbackInfo callback) {
    if (perihelion$moving
        || (Object) this != Minecraft.getMinecraft().thePlayer
        || !HookBridge.isRunning()) return;
    MoveEvent event = new MoveEvent(x, y, z);
    HookBridge.clientEvent(event);
    if (x == event.x() && y == event.y() && z == event.z()) return;
    perihelion$moving = true;
    try {
      moveEntity(event.x(), event.y(), event.z());
    } finally {
      perihelion$moving = false;
    }
    callback.cancel();
  }

  @Redirect(
      method = "moveEntity(DDD)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;isSneaking()Z"))
  private boolean perihelion$safeWalk(Entity entity) {
    if (entity != Minecraft.getMinecraft().thePlayer || !HookBridge.isRunning())
      return entity.isSneaking();
    SafeWalkEvent event = new SafeWalkEvent();
    HookBridge.clientEvent(event);
    return entity.isSneaking() || event.isCancelled();
  }

  @Inject(method = "moveFlying(FFF)V", at = @At("HEAD"), cancellable = true)
  private void perihelion$strafe(
      float strafe, float forward, float friction, CallbackInfo callback) {
    if ((Object) this != Minecraft.getMinecraft().thePlayer || !HookBridge.isRunning()) return;
    StrafeEvent event = new StrafeEvent(strafe, forward, friction, rotationYaw);
    HookBridge.clientEvent(event);
    if (event.isCancelled()) {
      callback.cancel();
      return;
    }
    if (event.strafe() == strafe
        && event.forward() == forward
        && event.friction() == friction
        && event.yaw() == rotationYaw) return;
    float magnitude = event.strafe() * event.strafe() + event.forward() * event.forward();
    if (magnitude >= 1.0E-4F) {
      float scale = event.friction() / Math.max(1, MathHelper.sqrt_float(magnitude));
      float s = event.strafe() * scale, f = event.forward() * scale;
      float sin = MathHelper.sin(event.yaw() * (float) Math.PI / 180),
          cos = MathHelper.cos(event.yaw() * (float) Math.PI / 180);
      motionX += s * cos - f * sin;
      motionZ += f * cos + s * sin;
    }
    callback.cancel();
  }
}
