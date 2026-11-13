package perihelion.integration.minecraft189.module.gameplay;

import java.util.*;
import java.util.function.Predicate;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.*;
import perihelion.core.control.Rotation;

/** Bounded visible-first peeling toward either bed half, scored by B4-style mining time. */
final class BreakerPlan {
  private static final double[] SAMPLES = {.1, .5, .9};
  final BlockPos bed, target;
  final Rotation angle;
  final EnumFacing face;
  final double cost;
  final List<Step> route;

  static final class Step {
    final BlockPos pos;
    final IBlockState state;
    final int tool, ticks;

    Step(BlockPos pos, IBlockState state, int tool, int ticks) {
      this.pos = pos;
      this.state = state;
      this.tool = tool;
      this.ticks = ticks;
    }
  }

  BreakerPlan(BlockPos bed, BlockPos target, Rotation angle, EnumFacing face, double cost) {
    this(bed, target, angle, face, cost, Collections.emptyList());
  }

  BreakerPlan(
      BlockPos bed,
      BlockPos target,
      Rotation angle,
      EnumFacing face,
      double cost,
      List<Step> route) {
    this.bed = bed;
    this.target = target;
    this.angle = angle;
    this.face = face;
    this.cost = cost;
    this.route = Collections.unmodifiableList(new ArrayList<>(route));
  }

  static BreakerPlan find(
      Minecraft game,
      double reach,
      boolean covers,
      boolean tools,
      Predicate<BlockPos> protectedBlock,
      Rotation reported) {
    return find(game, reach, covers, tools, protectedBlock, reported, 360, false, 1);
  }

  static BreakerPlan find(
      Minecraft game,
      double reach,
      boolean covers,
      boolean tools,
      Predicate<BlockPos> protectedBlock,
      Rotation reported,
      double fov,
      boolean ignoreSlow,
      double speed) {
    return find(
        game, reach, covers, tools, protectedBlock, reported, fov, ignoreSlow, speed, false);
  }

  static BreakerPlan find(
      Minecraft game,
      double reach,
      boolean covers,
      boolean tools,
      Predicate<BlockPos> protectedBlock,
      Rotation reported,
      double fov,
      boolean ignoreSlow,
      double speed,
      boolean directBed) {
    BlockPos origin = new BlockPos(game.thePlayer);
    int radius = (int) Math.ceil(reach) + 1;
    Set<BlockPos> seen = new HashSet<>();
    BreakerPlan best = null;
    for (int x = -radius; x <= radius; x++)
      for (int y = -radius; y <= radius; y++)
        for (int z = -radius; z <= radius; z++) {
          BlockPos bed = origin.add(x, y, z);
          if (!game.theWorld.isBlockLoaded(bed) || seen.contains(bed)) continue;
          IBlockState state = game.theWorld.getBlockState(bed);
          if (state.getBlock() != Blocks.bed) continue;
          BlockPos other =
              bed.offset(
                  state.getValue(BlockBed.PART) == BlockBed.EnumPartType.FOOT
                      ? state.getValue(BlockBed.FACING)
                      : state.getValue(BlockBed.FACING).getOpposite());
          seen.add(bed);
          seen.add(other);
          if (protectedBlock.test(bed) || protectedBlock.test(other)) continue;
          if (!inFov(game, bed, fov) && !inFov(game, other, fov)) continue;
          for (BlockPos half : new BlockPos[] {bed, other}) {
            if (game.theWorld.getBlockState(half).getBlock() != Blocks.bed) continue;
            AxisAlignedBB box = box(game, half);
            Vec3 eyes = game.thePlayer.getPositionEyes(1);
            for (double u : SAMPLES)
              for (double v : SAMPLES)
                for (double w : SAMPLES) {
                  Vec3 point =
                      new Vec3(
                          box.minX + (box.maxX - box.minX) * u,
                          box.minY + (box.maxY - box.minY) * v,
                          box.minZ + (box.maxZ - box.minZ) * w);
                  if (eyes.squareDistanceTo(point) > reach * reach) continue;
                  Rotation angle = quantized(game, reported, point);
                  BreakerPlan next =
                      directBed
                          ? direct(game, bed, half, angle, reach, tools, ignoreSlow, speed)
                          : path(
                              game,
                              bed,
                              other,
                              angle,
                              reach,
                              covers,
                              tools,
                              protectedBlock,
                              ignoreSlow,
                              speed);
                  if (next != null
                      && (best == null
                          || next.cost < best.cost - 1e-6
                          || Math.abs(next.cost - best.cost) < 1e-6
                              && game.thePlayer.getDistanceSqToCenter(next.target)
                                  < game.thePlayer.getDistanceSqToCenter(best.target))) best = next;
                }
          }
        }
    return best;
  }

  private static BreakerPlan direct(
      Minecraft game,
      BlockPos bed,
      BlockPos half,
      Rotation angle,
      double reach,
      boolean tools,
      boolean ignoreSlow,
      double speed) {
    MovingObjectPosition hit = trace(game, angle, reach);
    if (hit == null) return null;
    int slot = BreakerMining.tool(game, half, tools, ignoreSlow);
    float strength =
        BreakerMining.strength(
            game, half, game.thePlayer.inventory.getStackInSlot(slot), ignoreSlow);
    if (!(strength > 0) || !Float.isFinite(strength)) return null;
    int ticks = Math.max(1, (int) Math.ceil((1 - BreakerTiming.credit(speed)) / strength));
    return new BreakerPlan(
        bed,
        half,
        angle,
        hit.sideHit,
        ticks,
        Collections.singletonList(new Step(half, game.theWorld.getBlockState(half), slot, ticks)));
  }

  private static BreakerPlan path(
      Minecraft game,
      BlockPos bed,
      BlockPos other,
      Rotation angle,
      double reach,
      boolean covers,
      boolean tools,
      Predicate<BlockPos> protectedBlock,
      boolean ignoreSlow,
      double speed) {
    Vec3 eyes = game.thePlayer.getPositionEyes(1), direction = Placement.direction(angle);
    Vec3
        end =
            eyes.addVector(
                direction.xCoord * reach, direction.yCoord * reach, direction.zCoord * reach),
        start = eyes;
    MovingObjectPosition first = null;
    double cost = 0;
    List<Step> route = new ArrayList<>();
    for (int layer = 0; layer < 16; layer++) {
      MovingObjectPosition hit = game.theWorld.rayTraceBlocks(start, end, false, true, false);
      if (hit == null
          || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
          || protectedBlock.test(hit.getBlockPos())) return null;
      BlockPos pos = hit.getBlockPos();
      if (first == null) first = hit;
      int slot = BreakerMining.tool(game, pos, tools, ignoreSlow);
      float strength =
          BreakerMining.strength(
              game, pos, game.thePlayer.inventory.getStackInSlot(slot), ignoreSlow);
      if (!(strength > 0) || !Float.isFinite(strength)) return null;
      int ticks = Math.max(1, (int) Math.ceil((1.0 - BreakerTiming.credit(speed)) / strength));
      cost += ticks + (route.isEmpty() ? 0 : 1);
      route.add(new Step(pos, game.theWorld.getBlockState(pos), slot, ticks));
      if ((pos.equals(bed) || pos.equals(other))
          && game.theWorld.getBlockState(pos).getBlock() == Blocks.bed)
        return new BreakerPlan(bed, first.getBlockPos(), angle, first.sideHit, cost, route);
      if (!covers) return null;
      // Advance past this voxel without changing the world or pretending it is already broken.
      double exit = Double.POSITIVE_INFINITY;
      double[] d = {direction.xCoord, direction.yCoord, direction.zCoord},
          p = {eyes.xCoord, eyes.yCoord, eyes.zCoord};
      int[] cell = {pos.getX(), pos.getY(), pos.getZ()};
      for (int axis = 0; axis < 3; axis++)
        if (Math.abs(d[axis]) > 1e-10)
          exit = Math.min(exit, ((d[axis] > 0 ? cell[axis] + 1 : cell[axis]) - p[axis]) / d[axis]);
      if (!Double.isFinite(exit) || exit < 0 || exit >= reach) return null;
      start =
          eyes.addVector(
              direction.xCoord * (exit + 1e-4),
              direction.yCoord * (exit + 1e-4),
              direction.zCoord * (exit + 1e-4));
    }
    return null;
  }

  static BreakerPlan exposed(
      Minecraft game, BreakerPlan previous, double reach, Rotation reported) {
    IBlockState state = game.theWorld.getBlockState(previous.bed);
    if (state.getBlock() != Blocks.bed) return null;
    BlockPos other =
        previous.bed.offset(
            state.getValue(BlockBed.PART) == BlockBed.EnumPartType.FOOT
                ? state.getValue(BlockBed.FACING)
                : state.getValue(BlockBed.FACING).getOpposite());
    for (BlockPos half : new BlockPos[] {previous.bed, other}) {
      if (game.theWorld.getBlockState(half).getBlock() != Blocks.bed) continue;
      BreakerPlan direct =
          visible(
              game,
              new BreakerPlan(previous.bed, half, reported, EnumFacing.UP, 0),
              reach,
              reported);
      if (direct != null) return direct;
    }
    return null;
  }

  static BreakerPlan visible(
      Minecraft game, BreakerPlan previous, double reach, Rotation reported) {
    return visible(game, previous, reach, reported, false);
  }

  static BreakerPlan visible(
      Minecraft game, BreakerPlan previous, double reach, Rotation reported, boolean directBed) {
    AxisAlignedBB box = box(game, previous.target);
    BreakerPlan best = null;
    double turn = Double.MAX_VALUE;
    MovingObjectPosition current = trace(game, reported, reach);
    if (current != null && previous.target.equals(current.getBlockPos()))
      return new BreakerPlan(
          previous.bed, previous.target, reported, current.sideHit, previous.cost, previous.route);
    for (double u : SAMPLES)
      for (double v : SAMPLES)
        for (double w : SAMPLES) {
          Vec3 point =
              new Vec3(
                  box.minX + (box.maxX - box.minX) * u,
                  box.minY + (box.maxY - box.minY) * v,
                  box.minZ + (box.maxZ - box.minZ) * w);
          if (game.thePlayer.getPositionEyes(1).squareDistanceTo(point) > reach * reach) continue;
          Rotation angle = quantized(game, reported, point);
          MovingObjectPosition hit = trace(game, angle, reach);
          if (hit == null || !directBed && !previous.target.equals(hit.getBlockPos())) continue;
          double next = BedDefensePlan.turnCost(reported, angle);
          if (next < turn) {
            turn = next;
            best =
                new BreakerPlan(
                    previous.bed,
                    previous.target,
                    angle,
                    hit.sideHit,
                    previous.cost,
                    previous.route);
          }
        }
    return best;
  }

  static MovingObjectPosition trace(Minecraft game, Rotation angle, double reach) {
    Vec3 eyes = game.thePlayer.getPositionEyes(1), d = Placement.direction(angle);
    return game.theWorld.rayTraceBlocks(
        eyes,
        eyes.addVector(d.xCoord * reach, d.yCoord * reach, d.zCoord * reach),
        false,
        true,
        false);
  }

  private static Rotation quantized(Minecraft game, Rotation origin, Vec3 point) {
    Rotation angle = BedDefensePlan.angle(game.thePlayer.getPositionEyes(1), point);
    return RotationDynamics.quantize(
        Rotation.serverUnwrapped(
            origin.yaw() + MathHelper.wrapAngleTo180_float(angle.yaw() - origin.yaw()),
            angle.pitch()),
        origin,
        game.gameSettings.mouseSensitivity);
  }

  static boolean inFov(Minecraft game, BlockPos pos, double fov) {
    if (fov >= 360) return true;
    Rotation angle =
        BedDefensePlan.angle(
            game.thePlayer.getPositionEyes(1), new Vec3(pos).addVector(.5, .3, .5));
    return Math.abs(MathHelper.wrapAngleTo180_float(angle.yaw() - game.thePlayer.rotationYaw))
        < fov / 2;
  }

  static AxisAlignedBB box(Minecraft game, BlockPos pos) {
    Block block = game.theWorld.getBlockState(pos).getBlock();
    block.setBlockBoundsBasedOnState(game.theWorld, pos);
    AxisAlignedBB box = block.getSelectedBoundingBox(game.theWorld, pos);
    return box == null ? new AxisAlignedBB(pos, pos.add(1, 1, 1)) : box;
  }
}
