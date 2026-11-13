package perihelion.integration.minecraft189.module.gameplay;

import java.util.*;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.network.play.server.S03PacketTimeUpdate;
import net.minecraft.tileentity.*;
import net.minecraft.util.*;
import org.lwjgl.opengl.GL11;
import perihelion.core.control.Rotation;
import perihelion.core.event.WorldRenderEvent;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.*;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.event.*;
import perihelion.integration.minecraft189.render.*;

final class WorldVisualModules {
  private WorldVisualModules() {}

  static final class Ambience extends GameplayModule {
    private final Setting<Boolean> real;
    private final Setting<Integer> time;

    Ambience(ClientRuntime r) {
      super(
          r,
          "ambience",
          "Ambience",
          "Override local world time or follow the local clock.",
          ModuleCategory.RENDER);
      real = bool("real-world-time", "Real World Time", false);
      time = integer("time", "Time", 6000, 0, 24000);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      net.minecraft.client.multiplayer.WorldClient world = mc().theWorld;
      AmbienceTimeState serverTime =
          new AmbienceTimeState(
              world.getWorldTime(), world.getGameRules().getBoolean("doDaylightCycle"), now());
      c.listen(
          ClientPacketEvent.class,
          e -> {
            if (mc().theWorld == world && e.packet() instanceof S03PacketTimeUpdate) {
              serverTime.packet(((S03PacketTimeUpdate) e.packet()).getWorldTime(), now());
              e.cancel();
            }
          });
      c.listen(
          PlayerControlEvent.class,
          e -> {
            if (e == PlayerControlEvent.BEFORE_INPUT && mc().theWorld == world) {
              java.time.LocalTime clock = java.time.LocalTime.now();
              mc().theWorld
                  .setWorldTime(
                      real.get()
                          ? ((clock.getHour() * 60L + clock.getMinute()) * 24000 / 1440 + 18000)
                              % 24000
                          : time.get());
            }
          });
      c.onCleanup(
          () -> {
            if (mc().theWorld == world) {
              world.setWorldTime(serverTime.restoredTime(now()));
              // Restore the rule explicitly: negative zero cannot encode a frozen
              // time of zero through WorldClient.setWorldTime's sign convention.
              world
                  .getGameRules()
                  .setOrCreateGameRule("doDaylightCycle", Boolean.toString(serverTime.daylight()));
            }
          });
    }
  }

  static final class BlockESP extends GameplayModule {
    private final Setting<Boolean> chests, ender, beds, walls, filled, outline, clientColor;
    private final Setting<Integer> range;
    private final Setting<Double> alpha, rate, lineWidth;

    BlockESP(ClientRuntime r) {
      super(
          r,
          "blockesp",
          "Block ESP",
          "Highlight chests, ender chests, and beds.",
          ModuleCategory.RENDER);
      chests = bool("chests", "Chests", true);
      ender = bool("ender-chests", "Ender Chests", true);
      beds = bool("beds", "Beds", true);
      walls = bool("through-walls", "Through Walls", true);
      filled = bool("filled", "Filled", true);
      outline = bool("outline", "Outline", true);
      clientColor = bool("client-color", "Client Color", false);
      lineWidth = number("line-width", "Line Width", 1.5, .5, 5, .5).visibleWhen(outline::get);
      range = integer("bed-range", "Bed Range", 15, 2, 30);
      alpha = number("alpha", "Alpha", 0.3, 0.1, 1, 0.05);
      rate = number("bed-update-rate", "Bed Update Rate", 0.4, 0.1, 3, 0.1);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      Set<BlockPos> positions = new HashSet<>(), found = new HashSet<>();
      ScanCursor scan = new ScanCursor(range.get(), range.get());
      BlockPos[] origin = {mc().thePlayer.getPosition()};
      int[] remaining = {0};
      long[] next = {0};
      Runnable reset =
          () -> {
            positions.clear();
            found.clear();
            remaining[0] = 0;
            next[0] = 0;
          };
      c.observe(range, (s, before, after) -> reset.run());
      c.observe(beds, (s, before, after) -> reset.run());
      RenderState state = new RenderState();
      c.listen(
          PlayerControlEvent.class,
          e -> {
            if (e != PlayerControlEvent.BEFORE_INPUT || !beds.get()) return;
            positions.removeIf(pos -> mc().theWorld.getBlockState(pos).getBlock() != Blocks.bed);
            if (origin[0].distanceSq(mc().thePlayer.getPosition()) > 64) reset.run();
            if (remaining[0] == 0) {
              if (now() < next[0]) return;
              origin[0] = mc().thePlayer.getPosition();
              scan.reset(range.get(), range.get());
              remaining[0] = scan.volume();
              found.clear();
            }
            // Even a maximum-range initial scan cannot consume an entire tick.
            for (int work = 0; work < 4096 && remaining[0] > 0; work++, remaining[0]--) {
              BlockPos pos = origin[0].add(scan.x(), scan.centeredY(), scan.z());
              scan.advance();
              if (pos.getY() >= 0
                  && pos.getY() < 256
                  && mc().theWorld.isBlockLoaded(pos)
                  && mc().theWorld.getBlockState(pos).getBlock() == Blocks.bed) {
                if (mc().theWorld.getBlockState(pos).getValue(net.minecraft.block.BlockBed.PART)
                    == net.minecraft.block.BlockBed.EnumPartType.FOOT) {
                  found.add(pos);
                  positions.add(pos);
                }
              }
            }
            if (remaining[0] == 0) {
              positions.retainAll(found);
              next[0] = now() + (long) (rate.get() * 1000);
            }
          });
      c.listen(
          WorldRenderEvent.class,
          e -> {
            try (RenderState ignored = state.open()) {
              RenderStyle.lines(
                  walls.get(),
                  1,
                  ((int) (alpha.get() * 255) << 24) | (VisualDraw.ACCENT & 0xffffff));
              VisualDraw.worldOrigin(mc());
              for (TileEntity tile : mc().theWorld.loadedTileEntityList)
                if (chests.get() && tile instanceof TileEntityChest
                    || ender.get() && tile instanceof TileEntityEnderChest) draw(tile.getPos());
              if (beds.get())
                for (BlockPos pos : positions)
                  if (mc().theWorld.getBlockState(pos).getBlock() == Blocks.bed) draw(pos);
            }
          });
    }

    private void draw(BlockPos pos) {
      AxisAlignedBB bounds = bounds(pos);
      if (bounds == null) return;
      net.minecraft.block.Block block = mc().theWorld.getBlockState(pos).getBlock();
      int color = color(block);
      if (filled.get()) {
        RenderStyle.lines(
            walls.get(),
            lineWidth.get().floatValue(),
            ((int) (alpha.get() * 255) << 24) | (color & 0xffffff));
        VisualDraw.fill(bounds.expand(.002, .002, .002));
      }
      if (outline.get()) {
        RenderStyle.lines(walls.get(), lineWidth.get().floatValue(), color);
        VisualDraw.box(bounds.expand(.002, .002, .002));
      }
    }

    private int color(net.minecraft.block.Block block) {
      if (clientColor.get())
        return 0xff000000
            | (((perihelion.core.setting.ColorValue)
                        runtime.modules().require("hud").module().settings().get("accent").get())
                    .resolvedArgb(System.currentTimeMillis())
                & 0xffffff);
      return block == Blocks.bed
          ? 0xffff3232
          : block == Blocks.ender_chest ? 0xffb432ff : 0xffffaa00;
    }

    private AxisAlignedBB bounds(BlockPos pos) {
      net.minecraft.block.state.IBlockState state = mc().theWorld.getBlockState(pos);
      net.minecraft.block.Block block = state.getBlock();
      if (block == Blocks.bed) {
        if (state.getValue(net.minecraft.block.BlockBed.PART)
            != net.minecraft.block.BlockBed.EnumPartType.FOOT) return null;
        BlockPos head = pos.offset(state.getValue(net.minecraft.block.BlockBed.FACING));
        if (mc().theWorld.getBlockState(head).getBlock() != Blocks.bed) return null;
        return new AxisAlignedBB(
            Math.min(pos.getX(), head.getX()),
            pos.getY(),
            Math.min(pos.getZ(), head.getZ()),
            Math.max(pos.getX(), head.getX()) + 1,
            pos.getY() + .5625,
            Math.max(pos.getZ(), head.getZ()) + 1);
      }
      if (block instanceof net.minecraft.block.BlockChest) {
        for (EnumFacing side : new EnumFacing[] {EnumFacing.WEST, EnumFacing.NORTH})
          if (mc().theWorld.getBlockState(pos.offset(side)).getBlock() == block) return null;
        int dx = mc().theWorld.getBlockState(pos.east()).getBlock() == block ? 1 : 0;
        int dz = dx == 0 && mc().theWorld.getBlockState(pos.south()).getBlock() == block ? 1 : 0;
        return new AxisAlignedBB(
            pos.getX() + .0625,
            pos.getY(),
            pos.getZ() + .0625,
            pos.getX() + .9375 + dx,
            pos.getY() + .875,
            pos.getZ() + .9375 + dz);
      }
      return block.getSelectedBoundingBox(mc().theWorld, pos);
    }
  }

  static final class Breadcrumbs extends GameplayModule {
    Breadcrumbs(ClientRuntime r) {
      super(
          r,
          "breadcrumbs",
          "Breadcrumbs",
          "Draw a fading one-second movement trail.",
          ModuleCategory.RENDER);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      Deque<TrailPoint> points = new ArrayDeque<>();
      RenderState state = new RenderState();
      c.listen(
          WorldRenderEvent.class,
          e -> {
            long time = now();
            Vec3 position = VisualDraw.position(mc().thePlayer, e.partialTicks());
            if (points.isEmpty()
                || points.peekLast().position.squareDistanceTo(position) > 0.000001)
              points.addLast(new TrailPoint(position, time));
            while (!points.isEmpty()
                && (time - points.peekFirst().time > 1000 || points.size() > 1000))
              points.removeFirst();
            try (RenderState ignored = state.open()) {
              RenderStyle.lines(true, 2, VisualDraw.ACCENT);
              VisualDraw.worldOrigin(mc());
              GL11.glBegin(GL11.GL_LINE_STRIP);
              for (TrailPoint p : points) {
                GL11.glColor4f(0.68f, 0.55f, 1, Math.max(0, 0.9f * (1 - (time - p.time) / 1000f)));
                VisualDraw.vertex(p.position.addVector(0, 0.01, 0));
              }
              GL11.glEnd();
            }
          });
    }

    private static final class TrailPoint {
      final Vec3 position;
      final long time;

      TrailPoint(Vec3 p, long t) {
        position = p;
        time = t;
      }
    }
  }

  static final class Trajectories extends GameplayModule {
    private final List<Vec3> points = new ArrayList<>();

    Trajectories(ClientRuntime r) {
      super(
          r,
          "trajectories",
          "Trajectories",
          "Predict projectile flight and the first block or entity impact.",
          ModuleCategory.RENDER);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      RenderState state = new RenderState();
      points.clear();
      c.onCleanup(points::clear);
      final Vec3[] cachedImpact = {null};
      final int[] cachedTick = {Integer.MIN_VALUE}, cachedMetadata = {0};
      final Item[] cachedItem = {null};
      final Rotation[] cachedAim = {null};
      final Vec3[] cachedPosition = {null};
      final double[] cachedUseDuration = {0};
      c.listen(
          WorldRenderEvent.class,
          e -> {
            ItemStack stack = mc().thePlayer.getHeldItem();
            if (stack == null) {
              cachedItem[0] = null;
              points.clear();
              return;
            }
            Item item = stack.getItem();
            if (item instanceof ItemBow && !mc().thePlayer.isUsingItem()) {
              points.clear();
              cachedTick[0] = Integer.MIN_VALUE;
              return;
            }
            Rotation aim =
                runtime
                    .controls()
                    .rotation()
                    .orElse(
                        Rotation.server(mc().thePlayer.rotationYaw, mc().thePlayer.rotationPitch));
            Vec3 position = VisualDraw.position(mc().thePlayer, e.partialTicks());
            double useDuration =
                item instanceof ItemBow
                    ? mc().thePlayer.getItemInUseDuration() + e.partialTicks()
                    : 0;
            if (cachedTick[0] != mc().thePlayer.ticksExisted
                || cachedItem[0] != item
                || cachedMetadata[0] != stack.getMetadata()
                || cachedAim[0] == null
                || cachedAim[0].yaw() != aim.yaw()
                || cachedAim[0].pitch() != aim.pitch()
                || cachedPosition[0].squareDistanceTo(position) != 0
                || cachedUseDuration[0] != useDuration) {
              cachedTick[0] = mc().thePlayer.ticksExisted;
              cachedItem[0] = item;
              cachedMetadata[0] = stack.getMetadata();
              cachedAim[0] = aim;
              cachedPosition[0] = position;
              cachedUseDuration[0] = useDuration;
              points.clear();
              cachedImpact[0] = null;
              double speed = 1.5, gravity, size = 0.25, drag = 0.99;
              if (item instanceof ItemBow) {
                if (!mc().thePlayer.isUsingItem()) return;
                speed = ProjectilePhysics.bowSpeed(useDuration);
                if (speed < .3) return;
                gravity = ProjectilePhysics.ARROW_GRAVITY;
                drag = ProjectilePhysics.ARROW_DRAG;
                size = 0.3;
              } else if (item instanceof ItemFishingRod) {
                gravity = 0.04;
                drag = 0.92;
              } else if (item instanceof ItemPotion && ItemPotion.isSplash(stack.getMetadata())) {
                gravity = 0.05;
                speed = 0.5;
              } else if (item instanceof ItemSnowball
                  || item instanceof ItemEnderPearl
                  || item instanceof ItemEgg) gravity = 0.03;
              else return;
              double yaw = Math.toRadians(aim.yaw());
              Vec3 dir = Placement.direction(aim);
              Vec3
                  p =
                      position.addVector(
                          -Math.cos(yaw) * 0.16,
                          mc().thePlayer.getEyeHeight() - 0.1,
                          -Math.sin(yaw) * 0.16),
                  motion = new Vec3(dir.xCoord * speed, dir.yCoord * speed, dir.zCoord * speed),
                  impact = null;
              points.add(p);
              for (int step = 0; step < 300 && p.yCoord > 0; step++) {
                Vec3 next = p.add(motion);
                MovingObjectPosition hit =
                    mc().theWorld.rayTraceBlocks(p, next, false, true, false);
                Vec3 nearest = hit == null ? null : hit.hitVec;
                double distance = nearest == null ? Double.MAX_VALUE : p.squareDistanceTo(nearest);
                AxisAlignedBB sweep =
                    new AxisAlignedBB(
                            p.xCoord - size,
                            p.yCoord - size,
                            p.zCoord - size,
                            p.xCoord + size,
                            p.yCoord + size,
                            p.zCoord + size)
                        .addCoord(motion.xCoord, motion.yCoord, motion.zCoord)
                        .expand(1, 1, 1);
                for (Entity entity :
                    mc().theWorld.getEntitiesWithinAABBExcludingEntity(mc().thePlayer, sweep)) {
                  if (!entity.canBeCollidedWith()) continue;
                  MovingObjectPosition entityHit =
                      entity
                          .getEntityBoundingBox()
                          .expand(size, size, size)
                          .calculateIntercept(p, next);
                  if (entityHit != null && p.squareDistanceTo(entityHit.hitVec) < distance) {
                    nearest = entityHit.hitVec;
                    distance = p.squareDistanceTo(nearest);
                  }
                }
                if (nearest != null) {
                  impact = nearest;
                  points.add(impact);
                  break;
                }
                p = next;
                points.add(p);
                double friction =
                    mc().theWorld.getBlockState(new BlockPos(p)).getBlock().getMaterial()
                            == Material.water
                        ? 0.6
                        : drag;
                motion =
                    new Vec3(
                        motion.xCoord * friction,
                        motion.yCoord * friction - gravity,
                        motion.zCoord * friction);
              }
              cachedImpact[0] = impact;
            }
            if (points.isEmpty()) return;
            Vec3 impact = cachedImpact[0];
            try (RenderState ignored = state.open()) {
              RenderStyle.lines(true, 2.25f, VisualDraw.ACCENT);
              VisualDraw.worldOrigin(mc());
              GL11.glBegin(GL11.GL_LINE_STRIP);
              for (int i = 0; i < points.size(); i++) {
                GL11.glColor4f(
                    0.68f, 0.55f, 1, (235 - 195f * i / Math.max(1, points.size() - 1)) / 255);
                VisualDraw.vertex(points.get(i));
              }
              GL11.glEnd();
              if (impact != null) {
                RenderStyle.color(0x32ad8cff);
                VisualDraw.fill(
                    new AxisAlignedBB(
                        impact.xCoord - 0.28,
                        impact.yCoord - 0.28,
                        impact.zCoord - 0.28,
                        impact.xCoord + 0.28,
                        impact.yCoord + 0.28,
                        impact.zCoord + 0.28));
                RenderStyle.color(0xbead8cff);
                VisualDraw.fill(
                    new AxisAlignedBB(
                        impact.xCoord - 0.14,
                        impact.yCoord - 0.14,
                        impact.zCoord - 0.14,
                        impact.xCoord + 0.14,
                        impact.yCoord + 0.14,
                        impact.zCoord + 0.14));
              }
            }
          });
    }
  }
}
