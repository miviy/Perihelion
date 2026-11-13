package perihelion.integration.minecraft189.module.gameplay;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.*;
import perihelion.core.control.Rotation;

/** Yuri's wall-avoiding Scaffold planner, isolated from other native placement tools. */
final class ScaffoldPlacement {
  private static final EnumFacing[] FACES = {
    EnumFacing.SOUTH,
    EnumFacing.NORTH,
    EnumFacing.EAST,
    EnumFacing.WEST,
    EnumFacing.UP,
    EnumFacing.DOWN
  };

  private ScaffoldPlacement() {}

  static Placement nearby(Minecraft game, BlockPos offset, Integer plane, Rotation previous) {
    BlockPos base = new BlockPos(game.thePlayer);
    // Cache the bounded support cube once. Candidate generation, face validation and
    // stable tie ordering share these cells rather than repeatedly querying chunks.
    boolean[][][] solid = new boolean[13][13][13];
    for (int x = -6; x <= 6; x++)
      for (int y = -6; y <= 6; y++)
        for (int z = -6; z <= 6; z++)
          solid[x + 6][y + 6][z + 6] = !Placement.replaceable(game, base.add(x, y, z));
    ScaffoldPlacementSearch.Cells cells =
        (x, y, z) ->
            x >= -6
                && x <= 6
                && y >= -6
                && y <= 6
                && z >= -6
                && z <= 6
                && solid[x + 6][y + 6][z + 6];
    int maxY =
        MathHelper.floor_double(game.thePlayer.posY - 1 + offset.getY() + 1.0E-3) - base.getY();
    ScaffoldPlacementSearch.Candidate chosen =
        ScaffoldPlacementSearch.find(
            cells,
            offset.getX(),
            offset.getY(),
            offset.getZ(),
            plane == null ? null : plane - base.getY(),
            maxY,
            offset.getY() < 0);
    if (chosen == null) return null;
    BlockPos target = base.add(chosen.x, chosen.y, chosen.z);
    List<Integer> sides = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      int[] side = ScaffoldPlacementSearch.NEIGHBORS[i];
      if (cells.solid(chosen.x + side[0], chosen.y, chosen.z + side[2])
          && !cells.solid(chosen.x + side[0], chosen.y + 1, chosen.z + side[2])) sides.add(i);
    }
    int selected;
    if (!sides.isEmpty()) {
      double currentYaw = previous.yaw() % 360 + 90 + (Math.random() - .5) * 30;
      sides.sort(
          Comparator.comparingDouble(
              i -> {
                int[] side = ScaffoldPlacementSearch.NEIGHBORS[i];
                double angle = Math.toDegrees(Math.atan2(side[2], side[0])) % 360;
                return Math.abs(MathHelper.wrapAngleTo180_double(angle - currentYaw));
              }));
      selected = sides.get(sides.size() > 1 && Math.random() < .25 ? 1 : 0);
    } else selected = cells.solid(chosen.x, chosen.y - 1, chosen.z) ? 4 : 5;
    int[] side = ScaffoldPlacementSearch.NEIGHBORS[selected];
    BlockPos support = target.add(side[0], side[1], side[2]);
    EnumFacing face = FACES[selected];
    Vec3 hit =
        new Vec3(support)
            .addVector(
                .5 + face.getFrontOffsetX() * .5,
                .5 + face.getFrontOffsetY() * .5,
                .5 + face.getFrontOffsetZ() * .5);
    return new Placement(target, support, face, hit, 4.5);
  }

  static Vec3 hit(Minecraft game, Placement plan, Rotation rotation) {
    double minimum = .1, maximum = .9;
    double x = minimum + Math.random() * .8,
        y = minimum + Math.random() * .8,
        z = minimum + Math.random() * .8;
    Vec3 eye = game.thePlayer.getPositionEyes(1), direction = Placement.direction(rotation);
    double reach = game.playerController.getBlockReachDistance();
    MovingObjectPosition ray =
        game.theWorld.rayTraceBlocks(
            eye,
            eye.addVector(
                direction.xCoord * reach, direction.yCoord * reach, direction.zCoord * reach),
            false,
            false,
            true);
    if (ray != null
        && ray.hitVec != null
        && plan.support.equals(ray.getBlockPos())
        && ray.sideHit == plan.face) {
      x =
          MathHelper.clamp_double(
              ray.hitVec.xCoord - plan.support.getX() + (Math.random() - .5) * .06,
              minimum,
              maximum);
      y =
          MathHelper.clamp_double(
              ray.hitVec.yCoord - plan.support.getY() + (Math.random() - .5) * .06,
              minimum,
              maximum);
      z =
          MathHelper.clamp_double(
              ray.hitVec.zCoord - plan.support.getZ() + (Math.random() - .5) * .06,
              minimum,
              maximum);
    }
    return new Vec3(plan.support)
        .addVector(
            plan.face.getAxis() == EnumFacing.Axis.X ? (plan.face == EnumFacing.EAST ? 1 : 0) : x,
            plan.face.getAxis() == EnumFacing.Axis.Y ? (plan.face == EnumFacing.UP ? 1 : 0) : y,
            plan.face.getAxis() == EnumFacing.Axis.Z ? (plan.face == EnumFacing.SOUTH ? 1 : 0) : z);
  }

  /** A queued turn needs room for movement and mouse-step rounding, not a grazing seam. */
  static Rotation stableAim(Minecraft game, Placement plan, Rotation desired) {
    perihelion.integration.minecraft189.control.PlayerRotationAccess access =
        (perihelion.integration.minecraft189.control.PlayerRotationAccess) game.thePlayer;
    Rotation origin =
        Rotation.serverUnwrapped(
            access.perihelion$reportedYaw(), access.perihelion$reportedPitch());
    Rotation rounded =
        RotationDynamics.quantize(desired, origin, game.gameSettings.mouseSensitivity);
    Vec3 eye = game.thePlayer.getPositionEyes(1), d = Placement.direction(rounded);
    MovingObjectPosition hit =
        game.theWorld.rayTraceBlocks(
            eye, eye.addVector(d.xCoord * 4.5, d.yCoord * 4.5, d.zCoord * 4.5), false, false, true);
    net.minecraft.block.Block block = game.theWorld.getBlockState(plan.support).getBlock();
    block.setBlockBoundsBasedOnState(game.theWorld, plan.support);
    AxisAlignedBB b = block.getSelectedBoundingBox(game.theWorld, plan.support);
    if (b == null) return desired;
    if (hit != null
        && hit.hitVec != null
        && plan.support.equals(hit.getBlockPos())
        && hit.sideHit == plan.face) {
      double[] p = {hit.hitVec.xCoord, hit.hitVec.yCoord, hit.hitVec.zCoord},
          lo = {b.minX, b.minY, b.minZ},
          hi = {b.maxX, b.maxY, b.maxZ};
      boolean inside = true;
      for (int i = 0; i < 3; i++) {
        if (i == plan.face.getAxis().ordinal()) continue;
        double margin = Math.min(.08, (hi[i] - lo[i]) * .2);
        if (p[i] < lo[i] + margin || p[i] > hi[i] - margin) inside = false;
      }
      if (inside) return rounded;
    }
    Vec3 center =
        new Vec3(
            plan.face == EnumFacing.WEST
                ? b.minX
                : plan.face == EnumFacing.EAST ? b.maxX : (b.minX + b.maxX) / 2,
            plan.face == EnumFacing.DOWN
                ? b.minY
                : plan.face == EnumFacing.UP ? b.maxY : (b.minY + b.maxY) / 2,
            plan.face == EnumFacing.NORTH
                ? b.minZ
                : plan.face == EnumFacing.SOUTH ? b.maxZ : (b.minZ + b.maxZ) / 2);
    Rotation target = GameActions.lookAt(game.thePlayer, center);
    target =
        RotationDynamics.quantize(
            Rotation.serverUnwrapped(
                origin.yaw() + MathHelper.wrapAngleTo180_float(target.yaw() - origin.yaw()),
                target.pitch()),
            origin,
            game.gameSettings.mouseSensitivity);
    return plan.ray(game, target, 4.5, true) ? target : desired;
  }
}
