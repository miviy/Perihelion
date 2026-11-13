package perihelion.integration.minecraft189.module.gameplay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.potion.Potion;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import perihelion.core.control.ControlRequest;
import perihelion.core.control.Rotation;

final class GameActions {
  private GameActions() {}

  static int closeWindowId(net.minecraft.network.play.client.C0DPacketCloseWindow packet)
      throws java.io.IOException {
    net.minecraft.network.PacketBuffer buffer =
        new net.minecraft.network.PacketBuffer(io.netty.buffer.Unpooled.buffer(1));
    try {
      packet.writePacketData(buffer);
      return buffer.readUnsignedByte();
    } finally {
      buffer.release();
    }
  }

  static int movementEntityId(net.minecraft.network.play.server.S14PacketEntity packet)
      throws java.io.IOException {
    net.minecraft.network.PacketBuffer buffer =
        new net.minecraft.network.PacketBuffer(io.netty.buffer.Unpooled.buffer(16));
    try {
      packet.writePacketData(buffer);
      return buffer.readVarIntFromBuffer();
    } finally {
      buffer.release();
    }
  }

  static void send(Packet<?> packet) {
    boolean interaction =
        packet instanceof net.minecraft.network.play.client.C02PacketUseEntity
            || packet instanceof net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
    if (packet instanceof net.minecraft.network.play.client.C07PacketPlayerDigging) {
      net.minecraft.network.play.client.C07PacketPlayerDigging.Action action =
          ((net.minecraft.network.play.client.C07PacketPlayerDigging) packet).getStatus();
      interaction |=
          action
                  == net.minecraft.network.play.client.C07PacketPlayerDigging.Action
                      .START_DESTROY_BLOCK
              || action
                  == net.minecraft.network.play.client.C07PacketPlayerDigging.Action
                      .STOP_DESTROY_BLOCK;
    }
    if (interaction) {
      perihelion.integration.minecraft189.event.LocalInteractionEvent event =
          new perihelion.integration.minecraft189.event.LocalInteractionEvent();
      perihelion.integration.minecraft189.HookBridge.clientEvent(event);
      if (event.isCancelled()) return;
    }
    Minecraft.getMinecraft().getNetHandler().addToSendQueue(packet);
  }

  static boolean moving(EntityPlayerSP p) {
    return p.movementInput.moveForward != 0 || p.movementInput.moveStrafe != 0;
  }

  static boolean previousAction(
      ControlRequest<Rotation> request, java.util.function.Consumer<Rotation> action) {
    return perihelion.integration.minecraft189.HookBridge.gameControls()
        .withPreviousAim(request, action);
  }

  static void movementFix(
      perihelion.core.module.ModuleContext context,
      ControlRequest<Rotation> request,
      java.util.function.BooleanSupplier enabled) {
    movementFix(context, request, enabled, false);
  }

  static void movementFix(
      perihelion.core.module.ModuleContext context,
      ControlRequest<Rotation> request,
      java.util.function.BooleanSupplier enabled,
      boolean b4) {
    context.listen(
        perihelion.integration.minecraft189.event.MovementInputEvent.class,
        e -> {
          Rotation rotation = request.value().orElse(null);
          if (!enabled.getAsBoolean() || rotation == null || !request.isWinning()) return;
          float[] input =
              b4
                  ? perihelion.integration.minecraft189.module.B4MovementCorrection.align(
                      e.strafe(),
                      e.forward(),
                      Minecraft.getMinecraft().thePlayer.rotationYaw,
                      rotation.yaw())
                  : perihelion.integration.minecraft189.module.YuriMovementCorrection.align(
                      e.strafe(),
                      e.forward(),
                      Minecraft.getMinecraft().thePlayer.rotationYaw,
                      rotation.yaw());
          e.input(input[0], input[1]);
        });
    context.listen(
        perihelion.integration.minecraft189.event.StrafeEvent.class,
        e -> {
          Rotation rotation = request.value().orElse(null);
          if (!enabled.getAsBoolean() || rotation == null || !request.isWinning()) return;
          e.yaw(rotation.yaw());
        });
    context.listen(
        perihelion.integration.minecraft189.event.MovementYawEvent.class,
        e -> {
          Rotation rotation = request.value().orElse(null);
          if (enabled.getAsBoolean() && rotation != null && request.isWinning())
            e.yaw(rotation.yaw());
        });
  }

  static double speed(EntityPlayerSP p) {
    return Math.hypot(p.motionX, p.motionZ);
  }

  /** Strafe-event speed replaces acceleration while retaining current analog/diagonal inputs. */
  static void strafeSpeed(
      perihelion.integration.minecraft189.event.StrafeEvent event,
      EntityPlayerSP player,
      double speed,
      double retainedMotion) {
    event.friction((float) (event.forward() != 0 && event.strafe() != 0 ? speed * 0.98F : speed));
    player.motionX *= retainedMotion;
    player.motionZ *= retainedMotion;
  }

  static double direction(EntityPlayerSP p) {
    perihelion.integration.minecraft189.event.MovementYawEvent event =
        new perihelion.integration.minecraft189.event.MovementYawEvent(p.rotationYaw);
    perihelion.integration.minecraft189.HookBridge.clientEvent(event);
    return direction(event.yaw(), p.movementInput.moveForward, p.movementInput.moveStrafe);
  }

  static double direction(float yaw, float forward, float strafe) {
    if (forward < 0) yaw += 180;
    float factor = forward < 0 ? -0.5F : forward > 0 ? 0.5F : 1;
    if (strafe > 0) yaw -= 90 * factor;
    if (strafe < 0) yaw += 90 * factor;
    return Math.toRadians(yaw);
  }

  static void strafe(EntityPlayerSP p, double speed) {
    if (!moving(p)) {
      p.motionX = 0;
      p.motionZ = 0;
      return;
    }
    double yaw = direction(p);
    p.motionX = -Math.sin(yaw) * speed;
    p.motionZ = Math.cos(yaw) * speed;
  }

  static void fixMovement(
      perihelion.integration.minecraft189.event.StrafeEvent e, float viewYaw, float serverYaw) {
    perihelion.integration.minecraft189.control.MovementCorrection.align(e, viewYaw, serverYaw);
  }

  static boolean rotationApplied(perihelion.core.control.ControlRequest<Rotation> request) {
    perihelion.integration.minecraft189.control.MinecraftControls controls =
        perihelion.integration.minecraft189.HookBridge.gameControls();
    return controls != null && controls.isRotationApplied(request);
  }

  static double baseSpeed(EntityPlayerSP p) {
    return baseSpeed(
        p.isPotionActive(Potion.moveSpeed)
            ? p.getActivePotionEffect(Potion.moveSpeed).getAmplifier() + 1
            : 0);
  }

  static double baseSpeed(int speedLevel) {
    return .2873 * (1 + .16 * speedLevel);
  }

  static boolean liquid(EntityPlayerSP p) {
    return p.isInWater() || p.isInLava();
  }

  static boolean blockUnder(Minecraft game) {
    EntityPlayerSP p = game.thePlayer;
    for (double offset = 0; offset <= p.posY + p.getEyeHeight(); offset += 2) {
      if (!game.theWorld
          .getCollidingBoundingBoxes(p, p.getEntityBoundingBox().offset(0, -offset, 0))
          .isEmpty()) return true;
    }
    return false;
  }

  static Rotation lookAt(EntityPlayerSP p, Vec3 point) {
    double x = point.xCoord - p.posX,
        y = point.yCoord - p.posY - p.getEyeHeight(),
        z = point.zCoord - p.posZ;
    return Rotation.server(
        (float) Math.toDegrees(Math.atan2(z, x)) - 90,
        (float) -Math.toDegrees(Math.atan2(y, Math.hypot(x, z))));
  }

  /** Yuri RotationUtils.calculate uses the game's atan2 and float conversion constant. */
  static Rotation yuriLookAt(EntityPlayerSP p, Vec3 point) {
    return yuriAngle(
        point.xCoord - p.posX, point.yCoord - (p.posY + p.getEyeHeight()), point.zCoord - p.posZ);
  }

  static Rotation yuriAngle(double x, double y, double z) {
    float degrees = 180f / (float) Math.PI;
    return Rotation.server(
        (float) (MathHelper.atan2(z, x) * degrees) - 90f,
        (float) -(MathHelper.atan2(y, Math.hypot(x, z)) * degrees));
  }

  static Vec3 nearest(EntityPlayerSP p, Entity entity) {
    AxisAlignedBB b = entity.getEntityBoundingBox();
    return new Vec3(
        MathHelper.clamp_double(p.posX, b.minX, b.maxX),
        MathHelper.clamp_double(p.posY + p.getEyeHeight(), b.minY, b.maxY),
        MathHelper.clamp_double(p.posZ, b.minZ, b.maxZ));
  }

  static boolean onLiquid(Minecraft game) {
    AxisAlignedBB box = game.thePlayer.getEntityBoundingBox();
    int y = (int) box.offset(0, -.01, 0).minY;
    boolean liquid = false;
    for (int x = MathHelper.floor_double(box.minX); x < MathHelper.floor_double(box.maxX) + 1; x++)
      for (int z = MathHelper.floor_double(box.minZ);
          z < MathHelper.floor_double(box.maxZ) + 1;
          z++) {
        net.minecraft.block.Block block =
            game.theWorld.getBlockState(new BlockPos(x, y, z)).getBlock();
        if (block instanceof net.minecraft.block.BlockAir) continue;
        if (!(block instanceof net.minecraft.block.BlockLiquid)) return false;
        liquid = true;
      }
    return liquid;
  }

  static boolean insideBlock(Minecraft game) {
    if (game.thePlayer.ticksExisted < 5) return false;
    AxisAlignedBB box = game.thePlayer.getEntityBoundingBox();
    for (int x = MathHelper.floor_double(box.minX); x <= MathHelper.floor_double(box.maxX); x++)
      for (int y = MathHelper.floor_double(box.minY); y <= MathHelper.floor_double(box.maxY); y++)
        for (int z = MathHelper.floor_double(box.minZ);
            z <= MathHelper.floor_double(box.maxZ);
            z++) {
          BlockPos pos = new BlockPos(x, y, z);
          net.minecraft.block.state.IBlockState state = game.theWorld.getBlockState(pos);
          net.minecraft.block.Block block = state.getBlock();
          if (block instanceof net.minecraft.block.BlockAir) continue;
          AxisAlignedBB collision = block.getCollisionBoundingBox(game.theWorld, pos, state);
          if (collision != null && box.intersectsWith(collision)) return true;
        }
    return false;
  }

  static boolean blockNear(Minecraft game, int range) {
    for (int x = -range; x <= range; x++)
      for (int y = -range; y <= range; y++)
        for (int z = -range; z <= range; z++)
          if (!(game.theWorld
                  .getBlockState(
                      new BlockPos(
                          game.thePlayer.posX + x,
                          game.thePlayer.posY + y,
                          game.thePlayer.posZ + z))
                  .getBlock()
              instanceof net.minecraft.block.BlockAir)) return true;
    return false;
  }

  static int groundBlockY(Minecraft game) {
    BlockPos origin = new BlockPos(game.thePlayer);
    for (int i = 0; i < 50; i++) {
      BlockPos pos = origin.down(i);
      if (!(game.theWorld.getBlockState(pos).getBlock() instanceof net.minecraft.block.BlockAir))
        return pos.getY();
    }
    return 0;
  }

  static int hotbar(Minecraft game, java.util.function.Predicate<ItemStack> match) {
    for (int i = 0; i < 9; i++) {
      ItemStack stack = game.thePlayer.inventory.getStackInSlot(i);
      if (stack != null && match.test(stack)) return i;
    }
    return -1;
  }
}
