package perihelion.integration.minecraft189.module.gameplay;

import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.util.*;
import perihelion.core.control.*;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.*;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.*;

final class BuildingModules {
  private BuildingModules() {}

  static final class BlockIn extends GameplayModule {
    private final Setting<Double> speed;

    BlockIn(ClientRuntime r) {
      super(
          r,
          "blockin",
          "Block In",
          "Place a two-block enclosure and roof around the player.",
          ModuleCategory.WORLD);
      speed = number("rotation-speed", "Rotation Speed", 5, 0.5, 10, 0.5);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      ControlRequest<HotbarSelection> slot = c.controls().hotbar(105);
      ControlRequest<Rotation> rotation = c.controls().rotation(105);
      Placement[] plan = {null};
      Rotation[] aim = {Rotation.server(mc().thePlayer.rotationYaw, mc().thePlayer.rotationPitch)};
      long[] last = {0};
      GameActions.movementFix(c, rotation, () -> true);
      c.listen(
          PlayerControlEvent.class,
          e -> {
            if (e == PlayerControlEvent.BEFORE_INPUT) {
              plan[0] = null;
              BlockPos base = new BlockPos(mc().thePlayer);
              for (int[] offset :
                  new int[][] {
                    {1, 0, 0},
                    {-1, 0, 0},
                    {0, 0, 1},
                    {0, 0, -1},
                    {1, 1, 0},
                    {-1, 1, 0},
                    {0, 1, 1},
                    {0, 1, -1},
                    {0, 2, 0}
                  }) {
                plan[0] = Placement.at(mc(), base.add(offset[0], offset[1], offset[2]), 4.5, false);
                if (plan[0] != null) break;
              }
              int found = GameActions.hotbar(mc(), InventoryPolicy::buildingBlock);
              if (plan[0] == null || found < 0) {
                c.requestDisable();
                return;
              }
              slot.setForTicks(HotbarSelection.spoof(found), 1);
              aim[0] =
                  Placement.smooth(
                      aim[0], GameActions.lookAt(mc().thePlayer, plan[0].hit), speed.get());
              rotation.setForTicks(aim[0], 1);
            } else if (e == PlayerControlEvent.BEFORE_ACTIONS
                && plan[0] != null
                && now() - last[0] >= 50) {
              GameActions.previousAction(
                  rotation,
                  previous -> {
                    if (plan[0].ray(mc(), previous, 4.5)
                        && plan[0].place(mc(), slot, rotation, true, true, true)) last[0] = now();
                  });
            }
          });
    }
  }

  static final class BedDefender extends GameplayModule {
    private final Setting<Double> range, speed, tolerance;
    private final Setting<Integer> delay;
    private final Setting<Boolean> swing, moveFix;

    BedDefender(ClientRuntime r) {
      super(
          r,
          "beddefender",
          "Bed Defender",
          "Cover a nearby bed with an eight-block wool shell.",
          ModuleCategory.WORLD);
      range = number("range", "Range", 5, 3, 7, 0.1);
      delay = integer("place-delay", "Place Delay", 80, 0, 300);
      speed = number("speed", "Speed", 100, 30, 100, 1);
      tolerance = number("rotation-tolerance", "Rotation Tolerance", 35, 5, 180, 1);
      swing = bool("swing", "Swing", true);
      moveFix = bool("move-fix", "Move Fix", true);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      ControlRequest<HotbarSelection> slot = c.controls().hotbar(105);
      ControlRequest<Rotation> rotation = c.controls().rotation(105);
      ControlRequest<Boolean> sneak = c.controls().key(InputKey.SNEAK, 105);
      boolean[] attempted = {false};
      BlockPos[] bed = {null};
      Placement[] plan = {null};
      long[] last = {0};
      GameActions.movementFix(c, rotation, moveFix::get);
      c.listen(
          PlayerControlEvent.class,
          e -> {
            if (e == PlayerControlEvent.BEFORE_INPUT) {
              plan[0] = null;
              attempted[0] = false;
              if (mc().currentScreen != null) {
                slot.clear();
                rotation.clear();
                sneak.clear();
                return;
              }
              if (bed[0] == null || mc().theWorld.getBlockState(bed[0]).getBlock() != Blocks.bed)
                bed[0] = BedDefensePlan.nearest(mc());
              if (bed[0] == null) {
                slot.clear();
                rotation.clear();
                sneak.clear();
                return;
              }
              IBlockState state = mc().theWorld.getBlockState(bed[0]);
              EnumFacing facing = state.getValue(BlockBed.FACING);
              boolean unfinished = false;
              boolean roof = false;
              double turn = Double.MAX_VALUE;
              perihelion.integration.minecraft189.control.PlayerRotationAccess reported =
                  (perihelion.integration.minecraft189.control.PlayerRotationAccess) mc().thePlayer;
              Rotation lastReported =
                  Rotation.serverUnwrapped(
                      reported.perihelion$reportedYaw(), reported.perihelion$reportedPitch());
              for (BlockPos target : BedDefensePlan.shell(bed[0], facing)) {
                if (!BedDefensePlan.replaceable(mc(), target)) continue;
                unfinished = true;
                if (!mc().theWorld
                    .checkNoEntityCollision(new AxisAlignedBB(target, target.add(1, 1, 1))))
                  continue;
                boolean candidateRoof = target.getY() > bed[0].getY();
                // Finish reachable sides first; a blocked side must not stall the whole shell.
                if (candidateRoof && plan[0] != null && !roof) continue;
                Placement candidate =
                    BedDefensePlan.at(
                        mc(),
                        target,
                        candidateRoof,
                        Math.min(range.get(), mc().playerController.getBlockReachDistance()),
                        lastReported);
                if (candidate == null) continue;
                double candidateTurn =
                    BedDefensePlan.turnCost(
                        lastReported,
                        BedDefensePlan.angle(mc().thePlayer.getPositionEyes(1), candidate.hit));
                if (plan[0] == null || candidateTurn < turn) {
                  plan[0] = candidate;
                  roof = candidateRoof;
                  turn = candidateTurn;
                }
              }
              if (!unfinished) {
                c.requestDisable();
                return;
              }
              int wool =
                  GameActions.hotbar(
                      mc(),
                      s ->
                          s.getItem() instanceof ItemBlock
                              && ((ItemBlock) s.getItem()).getBlock() == Blocks.wool);
              if (plan[0] == null || wool < 0) {
                slot.clear();
                rotation.clear();
                sneak.clear();
                return;
              }
              sneak.setForTicks(roof, 1);
              slot.setForTicks(HotbarSelection.spoof(wool), 1);
              Rotation next =
                  RotationDynamics.quantize(
                      RotationDynamics.step(
                          lastReported,
                          BedDefensePlan.angle(mc().thePlayer.getPositionEyes(1), plan[0].hit),
                          speed.get() * 18),
                      lastReported,
                      mc().gameSettings.mouseSensitivity);
              rotation.setForTicks(next, 1);
            } else if ((e == PlayerControlEvent.BEFORE_ACTIONS
                    || e == PlayerControlEvent.AFTER_MOTION)
                && plan[0] != null
                && !attempted[0]
                && BedDefensePlan.replaceable(mc(), plan[0].target)
                && now() - last[0] >= delay.get()) {
              java.util.function.Consumer<Rotation> place =
                  submitted -> {
                    Rotation goal =
                        BedDefensePlan.angle(mc().thePlayer.getPositionEyes(1), plan[0].hit);
                    if (Math.abs(MathHelper.wrapAngleTo180_float(submitted.yaw() - goal.yaw()))
                            <= tolerance.get()
                        && Math.abs(submitted.pitch() - goal.pitch()) <= tolerance.get()
                        && plan[0].ray(mc(), submitted, plan[0].range)
                        && slot.isWinning()
                        && (!(mc().theWorld.getBlockState(plan[0].support).getBlock()
                                instanceof BlockBed)
                            || sneak.isWinning())) {
                      // Yuri measures delay from the valid attempt, including a rejected click.
                      attempted[0] = true;
                      last[0] = now();
                      Runnable click =
                          () ->
                              plan[0].place(
                                  mc(),
                                  slot,
                                  rotation,
                                  swing.get(),
                                  true,
                                  e == PlayerControlEvent.BEFORE_ACTIONS);
                      if (mc().theWorld.getBlockState(plan[0].support).getBlock()
                          instanceof BlockBed) HookBridge.gameControls().withSneaking(click);
                      else click.run();
                    }
                  };
              if (e == PlayerControlEvent.BEFORE_ACTIONS)
                GameActions.previousAction(rotation, place);
              else if (HookBridge.gameControls().isRotationApplied(rotation))
                place.accept(rotation.value().get());
            }
          });
    }
  }

  static BlockPos nearestBed(
      net.minecraft.client.Minecraft game, double range, BlockPos own, boolean footOnly) {
    return findBed(game, range, pos -> own != null && pos.distanceSq(own) <= 2.25, footOnly);
  }

  private static BlockPos findBed(
      net.minecraft.client.Minecraft game,
      double range,
      java.util.function.Predicate<BlockPos> excluded,
      boolean footOnly) {
    BlockPos best = null, origin = new BlockPos(game.thePlayer);
    double distance = range * range;
    int limit = (int) Math.ceil(range);
    for (int x = -limit; x <= limit; x++)
      for (int y = -limit; y <= limit; y++)
        for (int z = -limit; z <= limit; z++) {
          BlockPos pos = origin.add(x, y, z);
          double d = game.thePlayer.getDistanceSqToCenter(pos);
          if (d >= distance || !game.theWorld.isBlockLoaded(pos)) continue;
          IBlockState state = game.theWorld.getBlockState(pos);
          if (state.getBlock() != Blocks.bed
              || footOnly && state.getValue(BlockBed.PART) != BlockBed.EnumPartType.FOOT
              || excluded.test(pos)) continue;
          best = pos;
          distance = d;
        }
    return best;
  }

  static final class Breaker extends GameplayModule {
    enum Mode {
      VANILLA,
      HYPIXEL,
      LEGIT,
      INSTANT,
      SWAP,
      VISUAL
    }

    private final Setting<Mode> mode;
    private final Setting<Double> range, breakSpeed, fov, rate;
    private final Setting<Boolean> swing, moveFix, whitelist, autoTool;
    private final Setting<Boolean> breakNearBlock,
        onlyVisible,
        sendAnimations,
        silentSwing,
        outline,
        disableEffects,
        disableRightClick,
        ignoreSlow,
        groundSpoof,
        maximizeSpeed,
        skipVerification,
        allowAura,
        prioritizeAura,
        cancelKnockback,
        disableBhop;
    private final AuraModule aura;
    private final HomeBedTracker homeBeds;
    private boolean taskActive, actionWindow;
    private BreakerPlan preview;

    boolean activeTask() {
      return taskActive;
    }

    boolean blocksAura() {
      return taskActive && (!allowAura.get() || mode.get() == Mode.SWAP && actionWindow);
    }

    boolean pauseSpeed() {
      return taskActive && disableBhop.get();
    }

    Breaker(ClientRuntime r, AuraModule aura, HomeBedTracker homeBeds) {
      super(
          r,
          "breaker",
          "Breaker",
          "Break nearby beds and peel reachable cover with material-aware tool selection.",
          ModuleCategory.WORLD);
      this.aura = aura;
      this.homeBeds = homeBeds;
      mode = mode("mode", "Mode", Mode.LEGIT, Mode.class);
      range = number("range", "Range", 4.5, 1, 8, 0.1);
      breakSpeed = number("break-speed", "Break Speed", 1, 1, 1.8, .01);
      fov = number("fov", "FOV", 360, 20, 360, 5);
      rate = number("rate", "Rate (seconds)", .2, .05, 3, .05);
      swing =
          bool("visual-swing", "Visual Swing", true).visibleWhen(() -> mode.get() != Mode.VISUAL);
      moveFix = bool("move-fix", "Move Fix", true);
      whitelist =
          bool(
              "whitelist",
              "Whitelist",
              "Protect the home bed found near spawn. Wait until its location is known.",
              true);
      autoTool = bool("auto-tool", "Auto Tool", true);
      breakNearBlock =
          bool(
              "break-near-block",
              "Break Near Block",
              "Peel the fastest reachable route through cover.",
              true);
      onlyVisible =
          bool("only-while-visible", "Only While Visible", "Select exposed beds only.", false);
      sendAnimations = bool("send-animations", "Send Animations", true);
      silentSwing = bool("silent-swing", "Silent Swing", false);
      outline = bool("render-block-outline", "Render Block Outline", true);
      disableEffects = bool("disable-break-effects", "Disable Break Effects", false);
      disableRightClick = bool("disable-right-click", "Disable Right Click", true);
      ignoreSlow =
          bool(
              "ignore-slow",
              "Ignore Slow",
              "Ignore fatigue, water and airborne mining penalties, as in B4.",
              false);
      groundSpoof = bool("ground-spoof", "Ground Spoof", false);
      maximizeSpeed =
          bool(
              "maximize-speed",
              "Maximize Speed",
              "Use B4's two-tick quick-break countdown instead of four.",
              false);
      skipVerification =
          bool(
              "skip-break-verification",
              "Skip Break Verification",
              "Predict a completed break instead of waiting for the server.",
              false);
      allowAura = bool("allow-kill-aura", "Allow KillAura", false);
      prioritizeAura = bool("prioritize-kill-aura", "Prioritize KillAura", false);
      cancelKnockback = bool("cancel-knockback", "Cancel Knockback", false);
      disableBhop =
          bool("disable-bhop", "Disable Bhop", "Pause Perihelion Speed while mining.", false);
      for (Setting<?> setting :
          new Setting<?>[] {
            breakSpeed,
            autoTool,
            breakNearBlock,
            onlyVisible,
            outline,
            sendAnimations,
            silentSwing,
            disableEffects,
            disableRightClick,
            ignoreSlow,
            groundSpoof,
            maximizeSpeed,
            skipVerification,
            allowAura,
            prioritizeAura,
            cancelKnockback,
            disableBhop,
            moveFix
          }) setting.visibleWhen(() -> mode.get() != Mode.VISUAL);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      Session session = new Session(c);
      c.onCleanup(session::abort);
      c.observe(
          mode,
          (s, before, after) -> {
            session.abort();
            session.scanCounter = session.cooldown = 0;
          });
      c.observe(range, (s, before, after) -> session.abort());
      c.observe(whitelist, (s, before, after) -> session.abort());
      c.observe(autoTool, (s, before, after) -> session.abort());
      for (Setting<?> setting :
          new Setting<?>[] {
            breakSpeed,
            fov,
            rate,
            breakNearBlock,
            onlyVisible,
            ignoreSlow,
            maximizeSpeed,
            skipVerification,
            prioritizeAura,
            allowAura,
            cancelKnockback,
            disableRightClick
          })
        c.observe(
            setting,
            (s, before, after) -> {
              session.abort();
              session.scanCounter = 0;
            });
      BreakerVisual visual = new BreakerVisual();
      c.onCleanup(visual::clear);
      c.listen(
          perihelion.core.event.WorldRenderEvent.class,
          e -> {
            BreakerPlan shown = mode.get() == Mode.VISUAL ? preview : session.plan;
            visual.world(
                mc(),
                session.allowed() && (outline.get() || mode.get() == Mode.VISUAL) ? shown : null,
                mode.get() == Mode.VISUAL);
          });
      c.listen(
          perihelion.core.event.HudRenderEvent.class,
          e -> {
            if (mode.get() == Mode.VISUAL && session.allowed()) visual.hud(mc(), preview, e);
          });
      c.listen(
          MotionPacketEvent.class,
          e -> {
            if (groundSpoof.get() && session.finishingThisTick() && session.rotation.isWinning())
              e.grounded(true);
          });
      c.listenNetwork(
          PacketReceiveEvent.class,
          100,
          false,
          e -> {
            if (!session.suppressKnockback || e.source() != session.connection) return;
            if (e.packet() instanceof net.minecraft.network.play.server.S27PacketExplosion
                || e.packet() instanceof net.minecraft.network.play.server.S12PacketEntityVelocity
                    && ((net.minecraft.network.play.server.S12PacketEntityVelocity) e.packet())
                            .getEntityID()
                        == session.entityId) e.cancel();
          });
      c.listenNetwork(
          PacketSendEvent.class,
          1000,
          false,
          e -> {
            if (!session.blockForeignDig
                || e.source() != session.connection
                || session.ownedDig.contains(e.packet())) return;
            if (session.suppressUse
                && (e.packet()
                    instanceof net.minecraft.network.play.client.C08PacketPlayerBlockPlacement)) {
              e.cancel();
              return;
            }
            if (!(e.packet() instanceof C07PacketPlayerDigging)) return;
            C07PacketPlayerDigging.Action action =
                ((C07PacketPlayerDigging) e.packet()).getStatus();
            if (action == C07PacketPlayerDigging.Action.START_DESTROY_BLOCK
                || action == C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK
                || action == C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK) e.cancel();
          });
      c.listen(
          ClientPacketEvent.class,
          e -> {
            if (mode.get() == Mode.VISUAL
                && (e.packet() instanceof net.minecraft.network.play.server.S23PacketBlockChange
                    || e.packet()
                        instanceof net.minecraft.network.play.server.S22PacketMultiBlockChange)) {
              preview = null;
              session.previewScanned = false;
              session.scanCounter = 0;
              return;
            }
            if (session.plan == null) return;
            if (e.packet() instanceof net.minecraft.network.play.server.S23PacketBlockChange) {
              if (((net.minecraft.network.play.server.S23PacketBlockChange) e.packet())
                  .getBlockPosition()
                  .equals(session.plan.target)) session.corrected();
            } else if (e.packet()
                instanceof net.minecraft.network.play.server.S22PacketMultiBlockChange) {
              for (net.minecraft.network.play.server.S22PacketMultiBlockChange.BlockUpdateData
                  update :
                      ((net.minecraft.network.play.server.S22PacketMultiBlockChange) e.packet())
                          .getChangedBlocks())
                if (session.plan != null && update.getPos().equals(session.plan.target))
                  session.corrected();
            }
          });
      GameActions.movementFix(c, session.rotation, moveFix::get);
      c.listen(PlayerControlEvent.class, session::control);
    }

    /** One activation owns the target, progress, tool and packet lifetime. */
    private final class Session {
      final ControlRequest<HotbarSelection> slot;
      final ControlRequest<Rotation> rotation;
      final ControlRequest<Boolean> attack, use;
      final net.minecraft.world.World world = mc().theWorld;
      final net.minecraft.client.entity.EntityPlayerSP player = mc().thePlayer;
      final int entityId = player.getEntityId();
      final net.minecraft.network.NetworkManager connection =
          mc().getNetHandler().getNetworkManager();
      long revision = homeBeds.revision();
      BreakerPlan plan;
      IBlockState state;
      net.minecraft.item.ItemStack toolStack;
      int tool = -1, cooldown, scanCounter, quickRemaining = -1, awaiting;
      float damage;
      boolean started, attempted, stopped, previewScanned;
      volatile boolean blockForeignDig, suppressKnockback, suppressUse;
      final Set<net.minecraft.network.Packet<?>> ownedDig =
          java.util.Collections.newSetFromMap(
              new java.util.concurrent.ConcurrentHashMap<
                  net.minecraft.network.Packet<?>, Boolean>());
      EnumFacing face = EnumFacing.UP;

      Session(ModuleContext c) {
        slot = c.controls().hotbar(85);
        rotation = c.controls().rotation(85);
        attack = c.controls().key(InputKey.ATTACK, 85);
        use = c.controls().key(InputKey.USE_ITEM, 85);
      }

      boolean current() {
        return mc().theWorld == world
            && mc().thePlayer == player
            && mc().getNetHandler() != null
            && mc().getNetHandler().getNetworkManager() == connection
            && connection.isChannelOpen();
      }

      void abort() {
        BreakerPlan retired = plan;
        boolean wasStarted = started;
        plan = null;
        preview = null;
        previewScanned = false;
        state = null;
        toolStack = null;
        tool = -1;
        damage = 0;
        started = false;
        stopped = false;
        quickRemaining = -1;
        awaiting = 0;
        taskActive = actionWindow = false;
        blockForeignDig = false;
        suppressKnockback = suppressUse = false;
        slot.clear();
        rotation.clear();
        attack.clear();
        use.clear();
        // Retire first: packet callbacks may synchronously disable or reset this activation.
        if (retired != null && current()) {
          if (wasStarted)
            sendDig(C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK, retired.target);
          world.sendBlockBreakProgress(player.getEntityId(), retired.target, -1);
        }
      }

      boolean allowed() {
        return current()
            && mc().currentScreen == null
            && !player.isDead
            && !player.isSpectator()
            && (mode.get() == Mode.VISUAL || player.capabilities.allowEdit)
            && !active("freecam")
            && !active("scaffold")
            && !active("blockin")
            && (mode.get() == Mode.VISUAL
                || !prioritizeAura.get()
                || !active("aura")
                || aura.target() == null)
            && (!whitelist.get() || homeBeds.acquired() && revision == homeBeds.revision());
      }

      boolean protectedBed() {
        return whitelist.get()
            && plan != null
            && (homeBeds.protects(plan.bed) || homeBeds.protects(plan.target));
      }

      double reach() {
        return range.get();
      }

      Rotation reported() {
        perihelion.integration.minecraft189.control.PlayerRotationAccess access =
            (perihelion.integration.minecraft189.control.PlayerRotationAccess) player;
        return Rotation.serverUnwrapped(
            access.perihelion$reportedYaw(), access.perihelion$reportedPitch());
      }

      void corrected() {
        boolean completed = stopped;
        abort();
        if (completed) cooldown = 1;
      }

      BreakerPlan find(boolean visual) {
        return BreakerPlan.find(
            mc(),
            reach(),
            visual || mode.get() != Mode.VANILLA && breakNearBlock.get() && !onlyVisible.get(),
            visual || autoTool.get(),
            pos -> whitelist.get() && homeBeds.protects(pos),
            reported(),
            fov.get(),
            !visual && ignoreSlow.get(),
            visual ? 1 : breakSpeed.get(),
            !visual && directBed());
      }

      boolean directBed() {
        return mode.get() != Mode.VANILLA
            && mode.get() != Mode.VISUAL
            && !breakNearBlock.get()
            && !onlyVisible.get();
      }

      void control(PlayerControlEvent event) {
        if (revision != homeBeds.revision()) {
          abort();
          revision = homeBeds.revision();
          cooldown = scanCounter = 0;
        }
        if (!allowed() || protectedBed()) {
          abort();
          return;
        }
        if (mode.get() == Mode.VISUAL) {
          if (event == PlayerControlEvent.BEFORE_INPUT) {
            boolean stale =
                preview != null
                    && preview.route.stream()
                        .anyMatch(step -> !world.getBlockState(step.pos).equals(step.state));
            if (stale || !previewScanned || ++scanCounter >= BreakerTiming.scanTicks(rate.get())) {
              preview = find(true);
              previewScanned = true;
              scanCounter = 0;
            }
          }
          return;
        }
        if (event == PlayerControlEvent.BEFORE_INPUT) {
          attempted = false;
          actionWindow = false;
          slot.clear();
          rotation.clear();
          attack.clear();
          use.clear();
          if (cooldown > 0) {
            cooldown--;
            return;
          }
          if (stopped) {
            if (!world.getBlockState(plan.target).equals(state)) {
              corrected();
              // This input pass is already the idle update after STOP.
              cooldown = 0;
            } else if (++awaiting >= 8) corrected();
            return;
          }
          if (plan != null
              && (world.getBlockState(plan.bed).getBlock() != Blocks.bed
                  || !world.getBlockState(plan.target).equals(state))) abort();
          if (plan != null) plan = visibleOrAbort(plan);
          if (plan != null
              && breakNearBlock.get()
              && world.getBlockState(plan.target).getBlock() != Blocks.bed
              && BreakerPlan.exposed(mc(), plan, reach(), reported()) != null) abort();
          if (plan == null) {
            if (++scanCounter % BreakerTiming.scanTicks(rate.get()) != 0) return;
            plan = find(false);
            if (plan == null) return;
            state = world.getBlockState(plan.target);
          }
          int nextTool = BreakerMining.tool(mc(), plan.target, autoTool.get(), ignoreSlow.get());
          net.minecraft.item.ItemStack nextStack = player.inventory.getStackInSlot(nextTool);
          if (started
              && (tool != nextTool
                  || !net.minecraft.item.ItemStack.areItemStacksEqual(toolStack, nextStack))) {
            BreakerPlan next = plan;
            abort();
            plan = next;
            state = world.getBlockState(plan.target);
          }
          tool = nextTool;
          toolStack = nextStack == null ? null : nextStack.copy();
          taskActive = blockForeignDig = true;
          suppressKnockback = cancelKnockback.get();
          suppressUse = disableRightClick.get();
          attack.setForTicks(false, 1);
          if (suppressUse) use.setForTicks(false, 1);
          actionWindow =
              !started
                  || (mode.get() == Mode.INSTANT
                      ? quickRemaining <= 0
                      : mode.get() == Mode.SWAP
                          ? state.getBlock() == Blocks.bed
                              ? quickRemaining <= 1
                              : damage + progressStep() >= 1
                          : true);
          if (actionWindow) {
            // Swap keeps the original tool for START and swaps only for the finishing pulse.
            int selected =
                mode.get() == Mode.SWAP && !started && state.getBlock() != Blocks.bed
                    ? player.inventory.currentItem
                    : tool;
            slot.setForTicks(HotbarSelection.spoof(selected), 1);
            rotation.setForTicks(plan.angle, 1);
          }
        } else if (event == PlayerControlEvent.AFTER_MOTION
            && plan != null
            && !attempted
            && !stopped) {
          if (!attack.isWinning()) {
            abort();
            return;
          }
          if (!actionWindow) {
            attempted = true;
            if (quickRemaining > 0) quickRemaining--;
            else damage += progressStep();
            if (sendAnimations.get()) animate();
            if (quickRemaining < 0) cracks();
            return;
          }
          if (!slot.isWinning()
              || !rotation.isWinning()
              || !HookBridge.gameControls().isRotationApplied(rotation)) {
            abort();
            return;
          }
          MovingObjectPosition hit = BreakerPlan.trace(mc(), rotation.value().get(), reach());
          if (hit == null
              || !directBed() && !plan.target.equals(hit.getBlockPos())
              || !world.getBlockState(plan.target).equals(state)) {
            abort();
            return;
          }
          attempted = true;
          face = hit.sideHit;
          HookBridge.gameControls()
              .withRotation(
                  rotation, () -> HookBridge.gameControls().withHotbar(slot, stack -> dig()));
        }
      }

      float progressStep() {
        if (plan == null) return 0;
        float strength =
            BreakerMining.strength(
                mc(), plan.target, player.inventory.getStackInSlot(tool), ignoreSlow.get());
        if (mode.get() == Mode.SWAP && state.getBlock() != Blocks.bed) {
          strength = BreakerTiming.swapStep(strength, breakSpeed.get());
          if (ignoreSlow.get()) {
            Block block = state.getBlock();
            if (player.isPotionActive(net.minecraft.potion.Potion.digSlowdown)) {
              if (block == Blocks.end_stone && strength == .044444446f) strength *= .4f;
              else if ((block == Blocks.hardened_clay || block == Blocks.stained_hardened_clay)
                  && strength == .10666667f) strength *= .3f;
              else if ((block == Blocks.planks || block == Blocks.log || block == Blocks.log2)
                  && strength == .06666667f) strength *= .6f;
            } else if (block == Blocks.obsidian && strength == .012f) strength *= .999f;
          }
        }
        return strength;
      }

      boolean finishingThisTick() {
        return mode.get() != Mode.VISUAL
            && plan != null
            && !stopped
            && (quickRemaining == 0
                || quickRemaining < 0
                    && (mode.get() == Mode.SWAP
                            ? Math.max(damage, started ? 0 : initialSwapCredit()) + progressStep()
                            : BreakerTiming.legitProgress(damage, progressStep(), breakSpeed.get()))
                        >= 1);
      }

      float initialSwapCredit() {
        float raw =
            BreakerMining.strength(
                mc(), plan.target, player.inventory.getStackInSlot(tool), ignoreSlow.get());
        return progressStep() == BreakerTiming.swapStep(raw, breakSpeed.get())
            ? BreakerTiming.credit(breakSpeed.get())
            : 0;
      }

      void animate() {
        if (swing.get() && !silentSwing.get()) player.swingItem();
        else GameActions.send(new net.minecraft.network.play.client.C0APacketAnimation());
      }

      void cracks() {
        if (plan != null)
          world.sendBlockBreakProgress(
              player.getEntityId(), plan.target, Math.min(9, (int) (damage * 10) - 1));
      }

      BreakerPlan visibleOrAbort(BreakerPlan previous) {
        BreakerPlan visible = BreakerPlan.visible(mc(), previous, reach(), reported(), directBed());
        if (visible == null) abort();
        return visible;
      }

      void dig() {
        BreakerPlan digging = plan;
        if (digging == null || !allowed() || protectedBed()) return;
        Block block = state.getBlock();
        float step = progressStep();
        if (mode.get() != Mode.SWAP
            && !ignoreSlow.get()
            && !mc().playerController.isInCreativeMode()
            && block.getBlockHardness(world, digging.target) > 0)
          step = block.getPlayerRelativeBlockHardness(player, world, digging.target);
        if (!(step > 0) || !Float.isFinite(step)) {
          abort();
          return;
        }
        if (!started) {
          animate();
          if (plan != digging || !allowed()) return;
          sendDig(C07PacketPlayerDigging.Action.START_DESTROY_BLOCK, digging.target);
          if (plan != digging || !allowed()) {
            if (current())
              sendDig(C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK, digging.target);
            abort();
            return;
          }
          started = true;
          block.onBlockClicked(world, digging.target, player);
          if (plan != digging || !current() || !world.getBlockState(digging.target).equals(state)) {
            abort();
            return;
          }
          damage = mode.get() == Mode.SWAP ? initialSwapCredit() : 0;
          if (mode.get() == Mode.INSTANT || mode.get() == Mode.SWAP && block == Blocks.bed) {
            quickRemaining =
                BreakerTiming.quickTicks(
                    maximizeSpeed.get(),
                    mode.get() == Mode.SWAP,
                    player.isPotionActive(net.minecraft.potion.Potion.digSlowdown));
            return;
          }
        }
        if (quickRemaining > 0) {
          quickRemaining--;
          if (sendAnimations.get()) animate();
          return;
        }
        if (quickRemaining < 0)
          damage =
              mode.get() == Mode.SWAP
                  ? damage + step
                  : BreakerTiming.legitProgress(damage, step, breakSpeed.get());
        if (quickRemaining == 0 || damage >= 1) {
          animate();
          if (plan != digging || !allowed()) return;
          sendDig(C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK, digging.target);
          if (plan != digging || !current()) return;
          if (!disableEffects.get())
            world.playAuxSFX(2001, digging.target, Block.getStateId(state));
          world.sendBlockBreakProgress(player.getEntityId(), digging.target, -1);
          started = false;
          if (skipVerification.get() || mc().playerController.isInCreativeMode()) {
            // Prediction is explicit. Normal operation waits for the server to remove the block.
            mc().playerController.onPlayerDestroyBlock(digging.target, face);
            abort();
            cooldown = 1;
          } else {
            stopped = true;
            awaiting = 0;
            actionWindow = false;
            slot.clear();
            rotation.clear();
            attack.clear();
            use.clear();
          }
        } else {
          if (sendAnimations.get()) animate();
          cracks();
        }
      }

      void sendDig(C07PacketPlayerDigging.Action action, BlockPos pos) {
        C07PacketPlayerDigging packet = new C07PacketPlayerDigging(action, pos, face);
        ownedDig.add(packet);
        try {
          GameActions.send(packet);
        } finally {
          ownedDig.remove(packet);
        }
      }
    }
  }
}
