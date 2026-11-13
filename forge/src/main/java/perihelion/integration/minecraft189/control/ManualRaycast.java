// SPDX-License-Identifier: AGPL-3.0-only
// Adapted from Yuri RayCastUtils, revision 1cb86ad94ce1f01aa9b715577163c8ecc47b7def.
package perihelion.integration.minecraft189.control;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

/**
 * Ordinary entity/block selection from the last walking angles, without mutating camera or boxes.
 */
public final class ManualRaycast {
  private ManualRaycast() {}

  public static MovingObjectPosition normalRightClick(Minecraft game) {
    if (game.thePlayer == null
        || game.theWorld == null
        || game.getRenderViewEntity() != game.thePlayer
        || !(game.thePlayer instanceof PlayerRotationAccess)) return null;
    PlayerRotationAccess access = (PlayerRotationAccess) game.thePlayer;
    return trace(game, access.perihelion$reportedYaw(), access.perihelion$reportedPitch(), 4.5);
  }

  private static MovingObjectPosition trace(Minecraft game, float yaw, float pitch, double range) {
    Vec3 direction = direction(yaw, pitch);
    Vec3 blockEyes = game.thePlayer.getPositionEyes(1);
    Vec3 blockEnd =
        blockEyes.addVector(
            direction.xCoord * range, direction.yCoord * range, direction.zCoord * range);
    MovingObjectPosition selected =
        game.theWorld.rayTraceBlocks(blockEyes, blockEnd, false, false, true);
    float partialTicks = ((MinecraftTimerAccess) game).perihelion$timer().renderPartialTicks;
    Vec3 eyes = game.thePlayer.getPositionEyes(partialTicks);
    Vec3 end =
        eyes.addVector(
            direction.xCoord * range, direction.yCoord * range, direction.zCoord * range);
    double blockDistance = selected == null ? range : eyes.distanceTo(selected.hitVec);
    double closest = blockDistance;
    Entity target = null;
    Vec3 targetPoint = null;
    for (Entity entity :
        game.theWorld.getEntitiesWithinAABBExcludingEntity(
            game.thePlayer,
            game.thePlayer
                .getEntityBoundingBox()
                .addCoord(
                    direction.xCoord * range, direction.yCoord * range, direction.zCoord * range)
                .expand(1, 1, 1))) {
      if (entity instanceof EntityPlayer && ((EntityPlayer) entity).isSpectator()
          || !entity.canBeCollidedWith()) continue;
      float border = entity.getCollisionBorderSize();
      AxisAlignedBB box = entity.getEntityBoundingBox().expand(border, border, border);
      MovingObjectPosition intercept = box.calculateIntercept(eyes, end);
      if (box.isVecInside(eyes)) {
        if (closest >= 0) {
          target = entity;
          targetPoint = intercept == null ? eyes : intercept.hitVec;
          closest = 0;
        }
      } else if (intercept != null) {
        double distance = eyes.distanceTo(intercept.hitVec);
        if (distance < closest || closest == 0) {
          target = entity;
          targetPoint = intercept.hitVec;
          closest = distance;
        }
      }
    }
    return target != null && (closest < blockDistance || selected == null)
        ? new MovingObjectPosition(target, targetPoint)
        : selected;
  }

  static Vec3 direction(float yaw, float pitch) {
    double yawRadians = Math.toRadians(yaw), pitchRadians = Math.toRadians(pitch);
    return new Vec3(
        -Math.sin(yawRadians) * Math.cos(pitchRadians),
        -Math.sin(pitchRadians),
        Math.cos(yawRadians) * Math.cos(pitchRadians));
  }
}
