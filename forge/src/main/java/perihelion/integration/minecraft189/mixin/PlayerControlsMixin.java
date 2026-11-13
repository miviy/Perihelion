package perihelion.integration.minecraft189.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import perihelion.integration.minecraft189.HookBridge;

@Mixin(EntityPlayerSP.class)
public abstract class PlayerControlsMixin
    implements perihelion.integration.minecraft189.control.PlayerRotationAccess,
        perihelion.integration.minecraft189.control.PlayerGroundAccess {
  @org.spongepowered.asm.mixin.Unique
  private final perihelion.integration.minecraft189.control.GroundTickHistory
      perihelion$groundTicks = new perihelion.integration.minecraft189.control.GroundTickHistory();

  public perihelion.integration.minecraft189.control.GroundTickHistory perihelion$groundTicks() {
    return perihelion$groundTicks;
  }

  @org.spongepowered.asm.mixin.Shadow private float lastReportedYaw, lastReportedPitch;

  public float perihelion$reportedYaw() {
    return lastReportedYaw;
  }

  public float perihelion$reportedPitch() {
    return lastReportedPitch;
  }

  public void perihelion$restoreReportedRotation(
      float expectedYaw, float expectedPitch, float yaw, float pitch) {
    if (lastReportedYaw == expectedYaw) lastReportedYaw = yaw;
    if (lastReportedPitch == expectedPitch) lastReportedPitch = pitch;
  }

  public void perihelion$forceRotationResend(float yaw) {
    lastReportedYaw = yaw + 180;
  }

  @Inject(method = "onLivingUpdate()V", at = @At("HEAD"))
  private void perihelion$beforeInput(CallbackInfo callback) {
    if ((Object) this == Minecraft.getMinecraft().thePlayer) {
      perihelion$groundTicks.advance(((EntityPlayerSP) (Object) this).onGround);
      HookBridge.beforeInput();
    }
  }

  @Inject(method = "onUpdateWalkingPlayer()V", at = @At("HEAD"))
  private void perihelion$beforeMotion(CallbackInfo callback) {
    if ((Object) this == Minecraft.getMinecraft().thePlayer) HookBridge.beforeMotion();
  }

  @Inject(method = "onUpdateWalkingPlayer()V", at = @At("RETURN"))
  private void perihelion$afterMotion(CallbackInfo callback) {
    if ((Object) this == Minecraft.getMinecraft().thePlayer) HookBridge.afterMotion();
  }

  // Optional only because another client may replace the whole method. The plugin verifies the
  // pair.
  @Redirect(
      method = "onUpdateWalkingPlayer()V",
      require = 0,
      at =
          @At(
              value = "FIELD",
              target = "Lnet/minecraft/client/entity/EntityPlayerSP;rotationYaw:F",
              opcode = 180))
  private float perihelion$motionYaw(EntityPlayerSP player) {
    return player == Minecraft.getMinecraft().thePlayer
        ? HookBridge.motionYaw(player.rotationYaw)
        : player.rotationYaw;
  }

  @Redirect(
      method = "onUpdateWalkingPlayer()V",
      require = 0,
      at =
          @At(
              value = "FIELD",
              target = "Lnet/minecraft/client/entity/EntityPlayerSP;rotationPitch:F",
              opcode = 180))
  private float perihelion$motionPitch(EntityPlayerSP player) {
    return player == Minecraft.getMinecraft().thePlayer
        ? HookBridge.motionPitch(player.rotationPitch)
        : player.rotationPitch;
  }

  @Redirect(
      method = "onUpdateWalkingPlayer()V",
      at = @At(value = "FIELD", target = "Lnet/minecraft/util/AxisAlignedBB;minY:D", opcode = 180))
  private double perihelion$motionY(net.minecraft.util.AxisAlignedBB box) {
    return (Object) this == Minecraft.getMinecraft().thePlayer
        ? HookBridge.motionY(box.minY)
        : box.minY;
  }

  @Redirect(
      method = "onUpdateWalkingPlayer()V",
      at =
          @At(
              value = "FIELD",
              target = "Lnet/minecraft/client/entity/EntityPlayerSP;onGround:Z",
              opcode = 180))
  private boolean perihelion$motionGround(EntityPlayerSP player) {
    return player == Minecraft.getMinecraft().thePlayer
        ? HookBridge.motionGround(player.onGround)
        : player.onGround;
  }
}
