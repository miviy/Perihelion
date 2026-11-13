package perihelion.integration.minecraft189.module.gameplay;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.*;
import perihelion.core.control.Rotation;

/**
 * Yuri-derived shell with turn-minimizing, sensitivity-validated native placement plans. Behavior
 * reference: Yuri 1cb86ad, BedDefenderModule (AGPL-3.0); see docs/DONORS.md.
 */
final class BedDefensePlan {
  private BedDefensePlan() {}

  static BlockPos nearest(Minecraft game) {
    BlockPos origin = new BlockPos(game.thePlayer), best = null;
    double distance = Double.MAX_VALUE;
    for (int x = -8; x <= 8; x++)
      for (int y = -2; y <= 2; y++)
        for (int z = -8; z <= 8; z++) {
          BlockPos pos = origin.add(x, y, z);
          if (!game.theWorld.isBlockLoaded(pos)) continue;
          net.minecraft.block.state.IBlockState state = game.theWorld.getBlockState(pos);
          if (!(state.getBlock() instanceof BlockBed)
              || state.getValue(BlockBed.PART) != BlockBed.EnumPartType.FOOT) continue;
          double next = game.thePlayer.getDistanceSqToCenter(pos);
          if (next < distance) {
            best = pos;
            distance = next;
          }
        }
    return best;
  }

  static BlockPos[] shell(BlockPos foot, EnumFacing facing) {
    BlockPos head = foot.offset(facing);
    EnumFacing left = facing.rotateYCCW(), right = left.getOpposite();
    return new BlockPos[] {
      foot.offset(left),
      foot.offset(right),
      head.offset(left),
      head.offset(right),
      foot.offset(facing.getOpposite()),
      head.offset(facing),
      foot.up(),
      head.up()
    };
  }

  static boolean replaceable(Minecraft game, BlockPos pos) {
    Block block = game.theWorld.getBlockState(pos).getBlock();
    return block == Blocks.air
        || block == Blocks.water
        || block == Blocks.flowing_water
        || block == Blocks.lava
        || block == Blocks.flowing_lava
        || block == Blocks.fire;
  }

  static Placement at(
      Minecraft game, BlockPos target, boolean roof, double range, Rotation reported) {
    Vec3 eyes = game.thePlayer.getPositionEyes(1);
    // A previously submitted ray can already cover this face. Retain that exact hit first.
    Vec3 view = Placement.direction(reported);
    MovingObjectPosition current =
        game.theWorld.rayTraceBlocks(
            eyes, eyes.addVector(view.xCoord * range, view.yCoord * range, view.zCoord * range));
    if (current != null
        && current.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
        && target.equals(current.getBlockPos().offset(current.sideHit))
        && (roof
            || !(game.theWorld.getBlockState(current.getBlockPos()).getBlock()
                instanceof BlockBed)))
      return new Placement(target, current.getBlockPos(), current.sideHit, current.hitVec, range);
    Placement best = null;
    double difference = Double.MAX_VALUE;
    for (EnumFacing offset : EnumFacing.values()) {
      BlockPos support = target.offset(offset);
      Block block = game.theWorld.getBlockState(support).getBlock();
      if (replaceable(game, support)
          || !roof && block instanceof BlockBed
          || eyes.squareDistanceTo(new Vec3(support).addVector(.5, .5, .5))
              > (range + 1.5) * (range + 1.5)) continue;
      EnumFacing face = offset.getOpposite();
      for (double u : new double[] {.2, .4, .5, .6, .8})
        for (double v : new double[] {.2, .4, .5, .6, .8}) {
          Vec3 point = point(support, face, u, v);
          if (eyes.squareDistanceTo(point) > range * range) continue;
          Rotation desired = angle(eyes, point);
          Rotation aim =
              RotationDynamics.quantize(
                  Rotation.serverUnwrapped(
                      reported.yaw()
                          + MathHelper.wrapAngleTo180_float(desired.yaw() - reported.yaw()),
                      desired.pitch()),
                  reported,
                  game.gameSettings.mouseSensitivity);
          Vec3 direction = Placement.direction(aim);
          MovingObjectPosition hit =
              game.theWorld.rayTraceBlocks(
                  eyes,
                  eyes.addVector(
                      direction.xCoord * range,
                      direction.yCoord * range,
                      direction.zCoord * range));
          if (hit == null
              || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
              || !support.equals(hit.getBlockPos())
              || face != hit.sideHit) continue;
          double next = turnCost(reported, aim);
          if (next < difference) {
            difference = next;
            best = new Placement(target, support, face, hit.hitVec, range);
          }
        }
    }
    return best;
  }

  static double turnCost(Rotation from, Rotation to) {
    return Math.hypot(
        MathHelper.wrapAngleTo180_float(to.yaw() - from.yaw()), to.pitch() - from.pitch());
  }

  static Rotation angle(Vec3 eyes, Vec3 point) {
    double x = point.xCoord - eyes.xCoord,
        y = point.yCoord - eyes.yCoord,
        z = point.zCoord - eyes.zCoord;
    return Rotation.server(
        (float) (Math.toDegrees(Math.atan2(z, x)) - 90),
        (float) -Math.toDegrees(Math.atan2(y, Math.sqrt(x * x + z * z))));
  }

  private static Vec3 point(BlockPos pos, EnumFacing face, double u, double v) {
    switch (face) {
      case DOWN:
        return new Vec3(pos.getX() + u, pos.getY(), pos.getZ() + v);
      case UP:
        return new Vec3(pos.getX() + u, pos.getY() + 1, pos.getZ() + v);
      case NORTH:
        return new Vec3(pos.getX() + u, pos.getY() + v, pos.getZ());
      case SOUTH:
        return new Vec3(pos.getX() + u, pos.getY() + v, pos.getZ() + 1);
      case WEST:
        return new Vec3(pos.getX(), pos.getY() + v, pos.getZ() + u);
      default:
        return new Vec3(pos.getX() + 1, pos.getY() + v, pos.getZ() + u);
    }
  }
}
