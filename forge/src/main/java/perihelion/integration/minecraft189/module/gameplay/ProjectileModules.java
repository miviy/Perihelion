// SPDX-License-Identifier: AGPL-3.0-only
// Adapted from Yuri 1cb86ad94ce1f01aa9b715577163c8ecc47b7def; Copyright (c) 2026 Unlegit Inc.
package perihelion.integration.minecraft189.module.gameplay;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import perihelion.core.control.*;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleCategory;
import perihelion.core.module.ModuleContext;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.*;

final class ProjectileModules {
  private ProjectileModules() {}

  /** Yuri 1.3.1 bow trajectory and target retention, owned by the native view controls. */
  static final class BowAimbot extends GameplayModule {
    private static final int MAX_TICKS = 120;
    private static final double UNREACHED = -1.0E9;
    private final Setting<Integer> range;
    private final Setting<Boolean> throughWalls, predict;
    private EntityPlayer target;
    private ControlRequest<Rotation> view;

    BowAimbot(ClientRuntime r, AuraModule aura) {
      super(
          r,
          "bowaimbot",
          "Bow Aimbot",
          "Aim a drawn bow using drag, gravity and motion prediction.",
          ModuleCategory.COMBAT);
      range = integer("range", "Range", 40, 5, 100);
      throughWalls = bool("through-walls", "Through Walls", true);
      predict = bool("predict", "Predict", true);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      view = c.controls().rotation(70);
      target = null;
      c.onCleanup(
          () -> {
            target = null;
            view.clear();
          });
      c.listen(
          PlayerControlEvent.class,
          e -> {
            if (e != PlayerControlEvent.BEFORE_INPUT) return;
            view.clear();
            if (mc().currentScreen != null
                || !mc().inGameHasFocus
                || mc().thePlayer.isDead
                || active("freecam")
                || active("scaffold")) {
              target = null;
              return;
            }
            if (target != null && !isValid(target)) target = null;
            double speed = arrowSpeed();
            if (speed <= 0) return;
            if (target == null) target = acquire(speed);
            if (target == null) return;
            double[] angles = solve(target, speed);
            if (angles != null) applyRotation((float) angles[0], (float) angles[1]);
          });
    }

    private boolean isValid(EntityPlayer player) {
      if (player == null
          || player == mc().thePlayer
          || player.isDead
          || player.getHealth() <= 0.0f
          || player.isInvisible()) {
        return false;
      }

      if (!mc().theWorld.playerEntities.contains(player)) {
        return false;
      }

      if (((TargetingModules.AntiBot) runtime.modules().require("antibot").module()).bot(player)) {
        return false;
      }

      return mc().thePlayer.getDistanceToEntity(player) <= range.get();
    }

    private EntityPlayer acquire(double speed) {
      EntityPlayer best = null;
      double bestDistance = Double.MAX_VALUE;

      for (EntityPlayer player : mc().theWorld.playerEntities) {
        if (!isValid(player)) {
          continue;
        }

        double distance = mc().thePlayer.getDistanceToEntity(player);
        if (distance >= bestDistance) {
          continue;
        }

        if (solve(player, speed) == null) {
          continue;
        }

        best = player;
        bestDistance = distance;
      }

      return best;
    }

    private void applyRotation(float yaw, float pitch) {
      view.setForTicks(Rotation.view(yaw, MathHelper.clamp_float(pitch, -90, 90)), 1);
    }

    private double arrowSpeed() {
      ItemStack held = mc().thePlayer.getHeldItem();
      if (held == null || !(held.getItem() instanceof ItemBow) || !mc().thePlayer.isUsingItem()) {
        return 0.0;
      }

      float charge = mc().thePlayer.getItemInUseDuration() / 20.0f;
      charge = (charge * charge + charge * 2.0f) / 3.0f;

      if (charge < 0.1f) {
        return 0.0;
      }
      if (charge > 1.0f) {
        charge = 1.0f;
      }

      return charge * 3.0;
    }

    private double[] solve(EntityLivingBase entity, double speed) {
      double[] result = solveFor(center(entity, 0.0), speed);

      if (result == null) {
        return null;
      }

      if (!predict.get()) {
        return result;
      }

      for (int i = 0; i < 3; i++) {
        double[] next = solveFor(center(entity, result[2]), speed);
        if (next == null) {
          break;
        }
        result = next;
      }

      return result;
    }

    private Vec3 center(EntityLivingBase entity, double ticks) {
      AxisAlignedBB box = entity.getEntityBoundingBox();
      double dx = (entity.posX - entity.lastTickPosX) * ticks;
      double dz = (entity.posZ - entity.lastTickPosZ) * ticks;

      return new Vec3(
          (box.minX + box.maxX) / 2.0 + dx,
          (box.minY + box.maxY) / 2.0,
          (box.minZ + box.maxZ) / 2.0 + dz);
    }

    private double[] solveFor(Vec3 goal, double speed) {
      Vec3 eyes = mc().thePlayer.getPositionEyes(1.0f);

      double yaw =
          Math.toDegrees(Math.atan2(goal.zCoord - eyes.zCoord, goal.xCoord - eyes.xCoord)) - 90.0;
      double startX = eyes.xCoord;
      double startZ = eyes.zCoord;

      for (int i = 0; i < 3; i++) {
        double yawRad = Math.toRadians(yaw);
        startX = eyes.xCoord - Math.cos(yawRad) * 0.16;
        startZ = eyes.zCoord - Math.sin(yawRad) * 0.16;
        yaw = Math.toDegrees(Math.atan2(goal.zCoord - startZ, goal.xCoord - startX)) - 90.0;
      }

      double yawRad = Math.toRadians(yaw);
      startX = eyes.xCoord - Math.cos(yawRad) * 0.16;
      startZ = eyes.zCoord - Math.sin(yawRad) * 0.16;

      double startY = eyes.yCoord - 0.1;
      double dirX = -Math.sin(yawRad);
      double dirZ = Math.cos(yawRad);
      double hDist = (goal.xCoord - startX) * dirX + (goal.zCoord - startZ) * dirZ;

      if (hDist < 0.5) {
        return null;
      }

      double prevPitch = 90.0;
      double prevError = trajectory(prevPitch, speed, hDist, startY, goal.yCoord)[0];

      for (double pitch = 89.0; pitch >= -90.0; pitch -= 1.0) {
        double[] current = trajectory(pitch, speed, hDist, startY, goal.yCoord);

        if (prevError < 0.0 && current[0] >= 0.0) {
          double low = pitch;
          double high = prevPitch;
          double ticks = current[1];

          for (int i = 0; i < 24; i++) {
            double mid = (low + high) / 2.0;
            double[] sample = trajectory(mid, speed, hDist, startY, goal.yCoord);
            if (sample[0] >= 0.0) {
              low = mid;
              ticks = sample[1];
            } else {
              high = mid;
            }
          }

          if (!throughWalls.get()
              && mc().theWorld.rayTraceBlocks(eyes, goal, false, true, false) != null) {
            return null;
          }

          return new double[] {yaw, low, ticks};
        }

        prevPitch = pitch;
        prevError = current[0];
      }

      return null;
    }

    private double[] trajectory(
        double pitch, double speed, double hDist, double startY, double goalY) {
      double pitchRad = Math.toRadians(pitch);
      double hv = Math.cos(pitchRad) * speed;
      double vy = -Math.sin(pitchRad) * speed;
      double h = 0.0;
      double y = startY;

      for (int tick = 1; tick <= MAX_TICKS; tick++) {
        double prevH = h;
        double prevY = y;

        h += hv;
        y += vy;

        hv *= 0.99;
        vy *= 0.99;
        vy -= 0.05;

        if (h >= hDist) {
          double t = (hDist - prevH) / (h - prevH);
          double yAt = prevY + (y - prevY) * t;
          return new double[] {yAt - goalY, tick - 1 + t};
        }

        if (y < goalY - 40.0 && vy < 0.0) {
          break;
        }
      }

      return new double[] {UNREACHED, 0.0};
    }
  }

  static final class AutoProjectile extends GameplayModule {
    enum Mode {
      CLIENT,
      SERVER
    }

    private final AuraModule aura;
    private final Setting<Double> range;
    private final Setting<Mode> mode;
    private final Setting<Boolean> lineOfSight;
    private volatile boolean busy;
    private ThrowSequence sequence;

    AutoProjectile(ClientRuntime r, AuraModule aura) {
      super(
          r,
          "autoprojectile",
          "Auto Projectile",
          "Predict and throw a hotbar projectile at the Aura target.",
          ModuleCategory.COMBAT);
      this.aura = aura;
      range = number("target-range", "Target Range", 10, 4, 15, 0.5);
      mode = mode("swap-mode", "Swap Mode", Mode.SERVER, Mode.class);
      lineOfSight = bool("line-of-sight", "Line Of Sight", true);
    }

    boolean busy() {
      return busy;
    }

    @Override
    protected void onEnable(ModuleContext c) {
      ThrowSequence sequence = this.sequence = new ThrowSequence(c);
      c.onCleanup(
          () -> {
            sequence.forceReset();
            this.sequence = null;
          });
      c.observe(mode, (setting, before, after) -> sequence.reset());
      c.listen(ClientSendFactEvent.class, sequence::fact);
      c.listenNetwork(PacketSendEvent.class, 100, false, sequence::packet);
      c.listen(
          PlayerAttackEvent.class,
          95,
          false,
          e -> {
            if (busy) e.cancel();
          });
      c.listen(
          PlayerControlEvent.class,
          95,
          false,
          e -> {
            if (e == PlayerControlEvent.BEFORE_INPUT) sequence.prepare();
            if (e == PlayerControlEvent.BEFORE_ACTIONS) {
              sequence.validatePending();
              GameActions.previousAction(sequence.rotation, previous -> sequence.throwItem());
            }
            if (e == PlayerControlEvent.BEFORE_MOTION) sequence.validatePending();
          });
      GameActions.movementFix(
          c, sequence.rotation, () -> sequence.stage != 0 && sequence.heldAim != null);
    }

    private final class ThrowSequence {
      private final ControlRequest<HotbarSelection> slot;
      private final ControlRequest<Rotation> rotation;
      private final ControlRequest<Boolean> use;
      private final ProjectilePacketGate gate = new ProjectilePacketGate();
      // Interception observes raw transport independently. Recording a release fact first must
      // not make that same remote packet look like a redundant second release to this gate.
      private final ProjectilePacketGate transport = new ProjectilePacketGate();
      private final ThreadLocal<Boolean> ownSend = new ThreadLocal<>();
      private long lastThrow;
      private int stage, ticks, restore, cooldown, selected = -1;
      private EntityLivingBase target;
      private Item item;
      private Rotation heldAim, alignedAim, currentAim;

      ThrowSequence(ModuleContext c) {
        slot = c.controls().hotbar(95);
        rotation = c.controls().rotation(95);
        use = c.controls().key(InputKey.USE_ITEM, 95);
        gate.initialBlocking(mc().thePlayer.isBlocking());
        transport.initialBlocking(mc().thePlayer.isBlocking());
      }

      private void own(Runnable action) {
        ownSend.set(true);
        try {
          action.run();
        } finally {
          ownSend.remove();
        }
      }

      void fact(ClientSendFactEvent event) {
        ProjectilePacketGate.Kind kind = ProjectilePacketGate.Kind.OTHER;
        switch (event.action()) {
          case WALK:
            kind = ProjectilePacketGate.Kind.WALK;
            break;
          case SLOT:
            kind = ProjectilePacketGate.Kind.SLOT;
            break;
          case ATTACK:
          case USE_ENTITY:
            kind = ProjectilePacketGate.Kind.ATTACK;
            break;
          case INVENTORY:
            kind = ProjectilePacketGate.Kind.INVENTORY;
            break;
          case RELEASE_USE_ITEM:
            kind = ProjectilePacketGate.Kind.SWORD_RELEASE;
            break;
          case DIG:
            kind = ProjectilePacketGate.Kind.BLOCK;
            break;
          case PLACE:
          case USE_ITEM:
            ItemStack stack = event.stack();
            kind =
                stack != null && stack.getItem() instanceof ItemSword
                    ? ProjectilePacketGate.Kind.SWORD_USE
                    : ProjectilePacketGate.Kind.BLOCK;
            break;
          default:
            break;
        }
        gate.accept(kind, Boolean.TRUE.equals(ownSend.get()));
      }

      private void suppress(boolean value) {
        gate.suppress(value);
        transport.suppress(value);
      }

      void packet(PacketSendEvent event) {
        net.minecraft.network.Packet<?> packet = event.packet();
        ProjectilePacketGate.Kind kind = ProjectilePacketGate.Kind.OTHER;
        if (packet instanceof net.minecraft.network.play.client.C03PacketPlayer)
          kind = ProjectilePacketGate.Kind.WALK;
        else if (packet instanceof net.minecraft.network.play.client.C09PacketHeldItemChange)
          kind = ProjectilePacketGate.Kind.SLOT;
        else if (packet instanceof net.minecraft.network.play.client.C02PacketUseEntity)
          kind = ProjectilePacketGate.Kind.ATTACK;
        else if (packet instanceof net.minecraft.network.play.client.C0EPacketClickWindow
            || packet instanceof net.minecraft.network.play.client.C0DPacketCloseWindow
            || packet instanceof net.minecraft.network.play.client.C16PacketClientStatus
                && ((net.minecraft.network.play.client.C16PacketClientStatus) packet).getStatus()
                    == net.minecraft.network.play.client.C16PacketClientStatus.EnumState
                        .OPEN_INVENTORY_ACHIEVEMENT) kind = ProjectilePacketGate.Kind.INVENTORY;
        else if (packet
            instanceof net.minecraft.network.play.client.C08PacketPlayerBlockPlacement) {
          ItemStack stack =
              ((net.minecraft.network.play.client.C08PacketPlayerBlockPlacement) packet).getStack();
          kind =
              stack != null && stack.getItem() instanceof ItemSword
                  ? ProjectilePacketGate.Kind.SWORD_USE
                  : ProjectilePacketGate.Kind.BLOCK;
        } else if (packet instanceof net.minecraft.network.play.client.C07PacketPlayerDigging) {
          boolean release =
              ((net.minecraft.network.play.client.C07PacketPlayerDigging) packet).getStatus()
                  == net.minecraft.network.play.client.C07PacketPlayerDigging.Action
                      .RELEASE_USE_ITEM;
          kind =
              release ? ProjectilePacketGate.Kind.SWORD_RELEASE : ProjectilePacketGate.Kind.BLOCK;
        }
        if (!transport.accept(kind, Boolean.TRUE.equals(ownSend.get()))) event.cancel();
      }

      void reset() {
        slot.clear();
        rotation.clear();
        use.clear();
        stage = ticks = restore = cooldown = 0;
        selected = -1;
        target = null;
        item = null;
        heldAim = null;
        alignedAim = null;
        currentAim = null;
        busy = false;
        suppress(false);
      }

      void forceReset() {
        reset();
        gate.reset();
        transport.reset();
      }

      private boolean valid() {
        if (target == null
            || target.isDead
            || target.getHealth() <= 0
            || mc().theWorld.getEntityByID(target.getEntityId()) != target) return false;
        ItemStack stack = selected < 0 ? null : mc().thePlayer.inventory.getStackInSlot(selected);
        return stack != null && stack.stackSize > 0 && stack.getItem() == item;
      }

      void validatePending() {
        if ((stage == 1 || stage == 2)
            && (!valid()
                || mc().currentScreen != null
                || !mc().inGameHasFocus
                || mc().thePlayer.isDead
                || mc().thePlayer.getHealth() <= 0
                || mc().thePlayer.isSpectator()
                || active("scaffold")
                || active("freecam"))) reset();
      }

      void prepare() {
        if (cooldown > 0) {
          cooldown--;
          suppress(cooldown > 0 || stage != 0);
        }
        ItemStack held = mc().thePlayer.getHeldItem();
        boolean swordSelected = held != null && held.getItem() instanceof ItemSword;
        if (mc().currentScreen != null
            || !mc().inGameHasFocus
            || mc().thePlayer.isDead
            || mc().thePlayer.getHealth() <= 0
            || mc().thePlayer.isSpectator()
            || active("scaffold")
            || active("freecam")
            || mc().thePlayer.isUsingItem() && !swordSelected) {
          reset();
          return;
        }
        if (stage == 3) {
          if (heldAim != null) aim();
          if (--restore <= 0 || ++ticks >= 12) {
            reset();
            cooldown = 1;
            // Aura is restored now; only sword packet suppression lasts through this tick.
            suppress(true);
          }
          return;
        }
        if (stage != 0) {
          if (!valid() || ++ticks >= 12) {
            reset();
            return;
          }
          stage = 2;
          aim();
          return;
        }
        EntityLivingBase candidate = aura.target();
        if (!active("aura") || candidate == null || !gate.canThrow()) return;
        double distance = mc().thePlayer.getDistanceToEntity(candidate);
        long delay = ProjectileTiming.delayFor(distance);
        if (distance <= 3
            || distance >= Math.max(aura.seekRange(), 4)
            || distance > range.get()
            || candidate.hurtTime >= 3
            || now() - lastThrow < delay) return;
        selected = projectileSlot(distance);
        if (selected < 0) return;
        target = candidate;
        item = mc().thePlayer.inventory.getStackInSlot(selected).getItem();
        Rotation solved = solveRotations(candidate, item);
        if (solved == null) {
          target = null;
          item = null;
          selected = -1;
          return;
        }
        heldAim =
            ProjectileTiming.needsRotation(
                    solved.yaw(),
                    solved.pitch(),
                    mc().thePlayer.rotationYaw,
                    mc().thePlayer.rotationPitch)
                ? solved
                : null;
        alignedAim = Rotation.server(mc().thePlayer.rotationYaw, mc().thePlayer.rotationPitch);
        boolean release = gate.serverBlocking();
        boolean[] auraReleased = {false};
        own(() -> auraReleased[0] = aura.releaseForItemRoutine());
        if (release && !auraReleased[0]) {
          own(
              () ->
                  GameActions.send(
                      new net.minecraft.network.play.client.C07PacketPlayerDigging(
                          net.minecraft.network.play.client.C07PacketPlayerDigging.Action
                              .RELEASE_USE_ITEM,
                          net.minecraft.util.BlockPos.ORIGIN,
                          net.minecraft.util.EnumFacing.DOWN)));
        }
        if (release) {
          gate.released();
          transport.released();
          mc().thePlayer.stopUsingItem();
        }
        lastThrow = now();
        ticks = 0;
        busy = true;
        suppress(true);
        use.set(false);
        // A native PRE throw needs this request's preceding submitted walking aim even
        // for Yuri's already-aligned immediate path. Submit it before waiting once.
        stage = 1;
        aim();
      }

      private int projectileSlot(double distance) {
        int rod = -1;
        for (int i = 0; i < 9; i++) {
          ItemStack stack = mc().thePlayer.inventory.getStackInSlot(i);
          if (stack == null || stack.stackSize <= 0) continue;
          Item candidate = stack.getItem();
          if (candidate instanceof ItemSnowball || candidate instanceof ItemEgg) return i;
          if (candidate instanceof ItemFishingRod && rod < 0 && distance <= 6) rod = i;
        }
        return rod;
      }

      private void aim() {
        Rotation previous =
            currentAim == null
                ? Rotation.server(mc().thePlayer.rotationYaw, mc().thePlayer.rotationPitch)
                : currentAim;
        currentAim =
            heldAim == null ? alignedAim : RotationDynamics.smooth(mc(), previous, heldAim, 10);
        rotation.setForTicks(currentAim, 1);
        use.set(false);
      }

      private Rotation solveRotations(EntityLivingBase entity, Item item) {
        boolean rod = item instanceof ItemFishingRod;
        double gravity = 0.03;
        double drag = rod ? 0.92 : 0.99;

        double eyeX = mc().thePlayer.posX;
        double eyeY = mc().thePlayer.posY + (double) mc().thePlayer.getEyeHeight() - 0.1;
        double eyeZ = mc().thePlayer.posZ;

        double velX = entity.posX - entity.lastTickPosX;
        double velZ = entity.posZ - entity.lastTickPosZ;
        double aimY = entity.posY + (double) entity.height * 0.7;

        double ticks = Math.max(1.0, (double) mc().thePlayer.getDistanceToEntity(entity) / 1.5);
        double tx = entity.posX;
        double tz = entity.posZ;
        double pitch = 0.0;

        for (int i = 0; i < 4; i++) {
          tx = entity.posX + velX * ticks;
          tz = entity.posZ + velZ * ticks;
          double dx = tx - eyeX;
          double dz = tz - eyeZ;
          double horiz = Math.sqrt(dx * dx + dz * dz);
          double[] sol = ThrownTrajectory.solvePitch(horiz, aimY - eyeY, drag, gravity);
          if (sol == null) return null;
          pitch = sol[0];
          ticks = Math.min(sol[1], 30.0);
        }

        if (lineOfSight.get()) {
          Vec3 from = new Vec3(eyeX, eyeY + 0.1, eyeZ);
          Vec3 to = new Vec3(tx, aimY, tz);
          if (mc().theWorld.rayTraceBlocks(from, to) != null) return null;
        }

        double dx = tx - eyeX;
        double dz = tz - eyeZ;
        float yaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        return Rotation.server(
            mc().thePlayer.rotationYaw
                + MathHelper.wrapAngleTo180_float(yaw - mc().thePlayer.rotationYaw),
            (float) pitch);
      }

      void throwItem() {
        if (stage != 2) return;
        if (!valid()
            || mc().currentScreen != null
            || active("scaffold")
            || active("freecam")
            || !use.isWinning()
            || !HookBridge.gameControls().isPreviousRotationApplied(rotation)) {
          reset();
          return;
        }
        // Yuri selects only when performing the throw, after the aiming/unblock wait.
        slot.set(
            mode.get() == Mode.SERVER
                ? HotbarSelection.spoof(selected)
                : HotbarSelection.visible(selected));
        if (!slot.isWinning()) {
          reset();
          return;
        }
        boolean[] sent = {false};
        own(
            () ->
                HookBridge.gameControls()
                    .withHotbar(
                        slot,
                        stack -> {
                          if (stack != null && stack.stackSize > 0 && stack.getItem() == item)
                            HookBridge.gameControls()
                                .withPreviousRotation(
                                    rotation,
                                    () -> {
                                      // Reports local stack changes, not packet emission.
                                      mc().playerController
                                          .sendUseItem(mc().thePlayer, mc().theWorld, stack);
                                      sent[0] = true;
                                    });
                        }));
        if (!sent[0]) {
          reset();
          return;
        }
        mc().entityRenderer.itemRenderer.resetEquippedProgress2();
        restore = ProjectileTiming.storeTicks(item instanceof ItemFishingRod);
        lastThrow = now();
        ticks = 0;
        stage = 3;
        if (heldAim == null) rotation.clear();
      }
    }
  }
}
