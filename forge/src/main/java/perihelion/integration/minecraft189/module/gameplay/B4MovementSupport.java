package perihelion.integration.minecraft189.module.gameplay;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.*;

final class B4MovementSupport {
  private B4MovementSupport() {}

  /** RotationUtils.getVectorForRotation uses float products and Minecraft's sine lookup table. */
  static Vec3 direction(float yaw, float pitch) {
    float cosYaw = MathHelper.cos((yaw *= -((float) Math.PI / 180)) - (float) Math.PI);
    float sinYaw = MathHelper.sin(yaw - (float) Math.PI);
    float cosPitch = -MathHelper.cos(pitch *= -((float) Math.PI / 180));
    float sinPitch = MathHelper.sin(pitch);
    return new Vec3(sinYaw * cosPitch, sinPitch, cosYaw * cosPitch);
  }

  static double movementRadians(net.minecraft.client.entity.EntityPlayerSP player) {
    perihelion.integration.minecraft189.event.MovementYawEvent event =
        new perihelion.integration.minecraft189.event.MovementYawEvent(player.rotationYaw);
    perihelion.integration.minecraft189.HookBridge.clientEvent(event);
    float yaw = event.yaw(), forward = player.movementInput.moveForward,
        strafe = player.movementInput.moveStrafe;
    if (forward < 0) yaw += 180;
    float factor = forward < 0 ? -.5f : forward > 0 ? .5f : 1;
    if (strafe > 0) yaw -= 90 * factor;
    else if (strafe < 0) yaw += 90 * factor;
    return radians(yaw);
  }

  static float radians(float yaw) { return yaw * ((float) Math.PI / 180); }

  /** Donor's downward eye ray and feet-to-ground distance, including partial block tops. */
  static double heightAboveGround(Minecraft game) {
    Vec3 feet = new Vec3(game.thePlayer.posX, game.thePlayer.posY, game.thePlayer.posZ);
    Vec3 eyes = feet.addVector(0, game.thePlayer.getEyeHeight(), 0);
    Vec3 direction = direction(0, 90);
    MovingObjectPosition hit = game.theWorld.rayTraceBlocks(eyes, eyes.addVector(
        direction.xCoord * 100, direction.yCoord * 100, direction.zCoord * 100), false, false, false);
    if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return -1;
    Block block = game.theWorld.getBlockState(hit.getBlockPos()).getBlock();
    double y = feet.yCoord;
    if (block.getMaterial().isSolid() || block == Blocks.carpet || block == Blocks.waterlily)
      y -= block.getBlockBoundsMaxY();
    return Math.max(y - hit.getBlockPos().getY(), 0);
  }
}
