package perihelion.integration.minecraft189.module.gameplay;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.util.*;
import perihelion.core.control.*;
import perihelion.integration.minecraft189.HookBridge;

/** A placement plan is recalculated from the current world and committed under winning claims. */
final class Placement {
  final BlockPos target, support;
  final EnumFacing face;
  final Vec3 hit;
  final double range;

  Placement(BlockPos target, BlockPos support, EnumFacing face, Vec3 hit, double range) {
    this.target = target;
    this.support = support;
    this.face = face;
    this.hit = hit;
    this.range = range;
  }

  static boolean replaceable(Minecraft game, BlockPos pos) {
    return game.theWorld.getBlockState(pos).getBlock().isReplaceable(game.theWorld, pos);
  }

  static Placement at(Minecraft game, BlockPos pos, double range, boolean allowBed) {
    if (!replaceable(game, pos)) return null;
    Placement best = null;
    double distance = Double.MAX_VALUE;
    for (EnumFacing offset :
        new EnumFacing[] {
          EnumFacing.NORTH,
          EnumFacing.SOUTH,
          EnumFacing.WEST,
          EnumFacing.EAST,
          EnumFacing.DOWN,
          EnumFacing.UP
        }) {
      BlockPos support = pos.offset(offset);
      Block block = game.theWorld.getBlockState(support).getBlock();
      if (replaceable(game, support)
          || block.getMaterial().isLiquid()
          || !allowBed && block instanceof net.minecraft.block.BlockBed) continue;
      EnumFacing face = offset.getOpposite();
      Vec3 hit =
          new Vec3(support)
              .addVector(
                  0.5 + face.getFrontOffsetX() * 0.5,
                  0.5 + face.getFrontOffsetY() * 0.5,
                  0.5 + face.getFrontOffsetZ() * 0.5);
      double d = game.thePlayer.getPositionEyes(1).distanceTo(hit);
      if (d <= range && d < distance) {
        best = new Placement(pos, support, face, hit, range);
        distance = d;
      }
    }
    return best;
  }

  static Placement nearby(Minecraft game, BlockPos desired, Integer plane) {
    Placement best = null;
    double score = Double.MAX_VALUE;
    for (int x = -3; x <= 3; x++)
      for (int y = -2; y <= 0; y++)
        for (int z = -3; z <= 3; z++) {
          BlockPos pos = desired.add(x, y, z);
          if (plane != null && pos.getY() != plane) continue;
          double value = pos.distanceSq(desired);
          if (value >= score) continue;
          Placement candidate = at(game, pos, 4.5, false);
          if (candidate != null) {
            best = candidate;
            score = value;
          }
        }
    return best;
  }

  static Rotation smooth(Rotation previous, Rotation target, double speed) {
    float step = (float) speed * 18;
    return Rotation.server(
        previous.yaw()
            + MathHelper.clamp_float(
                MathHelper.wrapAngleTo180_float(target.yaw() - previous.yaw()), -step, step),
        previous.pitch() + MathHelper.clamp_float(target.pitch() - previous.pitch(), -step, step));
  }

  static Vec3 direction(Rotation rotation) {
    double yaw = Math.toRadians(rotation.yaw()), pitch = Math.toRadians(rotation.pitch());
    return new Vec3(
        -Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
  }

  boolean ray(Minecraft game, Rotation rotation, double range) {
    return ray(game, rotation, range, true);
  }

  boolean ray(Minecraft game, Rotation rotation, double range, boolean strict) {
    return matches(trace(game, rotation, range), strict);
  }

  private MovingObjectPosition trace(Minecraft game, Rotation rotation, double range) {
    Vec3 eye = game.thePlayer.getPositionEyes(1), vector = direction(rotation);
    return game.theWorld.rayTraceBlocks(
        eye,
        eye.addVector(vector.xCoord * range, vector.yCoord * range, vector.zCoord * range),
        false,
        false,
        true);
  }

  private boolean matches(MovingObjectPosition result, boolean strict) {
    return result != null
        && result.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
        && support.equals(result.getBlockPos())
        && (!strict || result.sideHit == face);
  }

  boolean place(
      Minecraft game,
      ControlRequest<HotbarSelection> slot,
      ControlRequest<Rotation> rotation,
      boolean swing) {
    return place(game, slot, rotation, swing, false);
  }

  boolean place(
      Minecraft game,
      ControlRequest<HotbarSelection> slot,
      ControlRequest<Rotation> rotation,
      boolean swing,
      boolean strict) {
    return place(game, slot, rotation, swing, strict, false);
  }

  boolean place(
      Minecraft game,
      ControlRequest<HotbarSelection> slot,
      ControlRequest<Rotation> rotation,
      boolean swing,
      boolean strict,
      boolean previous) {
    return place(game, slot, rotation, swing, strict, previous, false);
  }

  boolean placeInput(
      Minecraft game,
      ControlRequest<HotbarSelection> slot,
      ControlRequest<Rotation> rotation,
      boolean swing,
      boolean strict) {
    return place(game, slot, rotation, swing, strict, false, true);
  }

  private boolean place(
      Minecraft game,
      ControlRequest<HotbarSelection> slot,
      ControlRequest<Rotation> rotation,
      boolean swing,
      boolean strict,
      boolean previous,
      boolean preparedInput) {
    perihelion.integration.minecraft189.control.MinecraftControls controls =
        HookBridge.gameControls();
    if (game.currentScreen != null
        || game.thePlayer.isDead
        || game.thePlayer.getPositionEyes(1).distanceTo(hit) > range
        || !slot.isWinning()
        || controls == null
        || !(preparedInput
            ? controls.isInputRotationPrepared(rotation)
            : previous
                ? controls.isPreviousRotationApplied(rotation)
                : controls.isRotationApplied(rotation))
        || !replaceable(game, target)
        || replaceable(game, support)
        || HookBridge.gameControls() == null) return false;
    Rotation preparedAngle = rotation.value().orElse(null);
    MovingObjectPosition traced = strict ? trace(game, preparedAngle, range) : null;
    if (strict && !matches(traced, true)) return false;
    Vec3 click = strict ? traced.hitVec : hit;
    perihelion.integration.minecraft189.event.LocalInteractionEvent interaction =
        new perihelion.integration.minecraft189.event.LocalInteractionEvent();
    HookBridge.clientEvent(interaction);
    if (interaction.isCancelled()) return false;
    boolean[] placed = {false};
    HookBridge.gameControls()
        .withHotbar(
            slot,
            stack -> {
              if (preparedInput && rotation.value().orElse(null) != preparedAngle) return;
              if (stack != null
                  && stack.stackSize > 0
                  && stack.getItem() instanceof net.minecraft.item.ItemBlock)
                HookBridge.gameControls()
                    .withPlacementRotation(
                        rotation,
                        previous,
                        preparedInput,
                        () -> {
                          Runnable clickBlock =
                              () -> {
                                if (((net.minecraft.item.ItemBlock) stack.getItem())
                                        .canPlaceBlockOnSide(
                                            game.theWorld, support, face, game.thePlayer, stack)
                                    && game.playerController.onPlayerRightClick(
                                        game.thePlayer, game.theWorld, stack, support, face, click)
                                    // Container activation also returns true; it is not a
                                    // placement.
                                    && !replaceable(game, target)) {
                                  placed[0] = true;
                                  if (swing) game.thePlayer.swingItem();
                                  else
                                    GameActions.send(
                                        new net.minecraft.network.play.client.C0APacketAnimation());
                                  game.entityRenderer.itemRenderer.resetEquippedProgress();
                                }
                              };
                          if (game.theWorld.getBlockState(support).getBlock()
                              instanceof net.minecraft.block.BlockFence)
                            HookBridge.gameControls().withSneaking(clickBlock);
                          else clickBlock.run();
                        });
            });
    return placed[0];
  }
}
