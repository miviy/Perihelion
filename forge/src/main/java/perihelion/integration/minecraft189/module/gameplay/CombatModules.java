package perihelion.integration.minecraft189.module.gameplay;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.potion.Potion;
import perihelion.core.control.ControlRequest;
import perihelion.core.control.InputKey;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleCategory;
import perihelion.core.module.ModuleContext;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.event.*;
import perihelion.integration.minecraft189.network.RawHookBridge;

final class CombatModules {
  private CombatModules() {}

  static final class Criticals extends GameplayModule {
    enum Mode {
      LEGIT,
      PACKET,
      MOSPIXEL,
      NCP
    }

    private final Setting<Mode> mode;
    private final Setting<Boolean> ground;

    Criticals(ClientRuntime r) {
      super(
          r,
          "criticals",
          "Criticals",
          "Pre-attack critical position sequences.",
          ModuleCategory.COMBAT);
      mode = mode("mode", "Mode", Mode.PACKET, Mode.class);
      ground = bool("on-ground-check", "On Ground Check", true);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      int[] ticks = {0};
      c.listen(
          PlayerControlEvent.class,
          e -> {
            if (e == PlayerControlEvent.BEFORE_MOTION && mode.get() == Mode.NCP) ticks[0]++;
          });
      c.listen(
          PlayerAttackEvent.class,
          -10,
          false,
          e -> {
            EntityPlayerSP p = mc().thePlayer;
            if (mode.get() == Mode.LEGIT) return;
            if (ground.get() && !p.onGround) return;
            double[] offsets;
            if (mode.get() == Mode.NCP) {
              if (ticks[0] <= 6) return;
              ticks[0] = 0;
              p.onCriticalHit(e.target());
              if (!p.onGround) return;
              offsets = new double[] {0.00001, 0.000008, 0.0000002};
            } else if (mode.get() == Mode.MOSPIXEL) {
              double base = 0.04 + Math.random() * 0.04;
              offsets =
                  new double[] {
                    base, base * 0.3 + Math.random() * 0.005, 0.0005 + Math.random() * 0.001, 0
                  };
            } else offsets = new double[] {0.0625, 0};
            for (double offset : offsets) {
              C03PacketPlayer packet =
                  new C03PacketPlayer.C04PacketPlayerPosition(
                      p.posX, p.posY + offset, p.posZ, false);
              if (mode.get() == Mode.PACKET) GameActions.send(packet);
              else RawHookBridge.replay(() -> GameActions.send(packet));
            }
          });
    }
  }

  static final class Velocity extends GameplayModule {
    enum Mode {
      LEGIT,
      INTAVE,
      CANCEL,
      CUSTOM
    }

    enum Intave {
      INTAVE_LATEST,
      INTAVE_13
    }

    private final AuraModule aura;
    private final Setting<Mode> mode;
    private final Setting<Intave> intave;
    private final Setting<Boolean> fire, sprint, reduce, whenReady, jumpEnabled, jumpSprint;
    private final Setting<Integer> attacks, reduceTicks;
    private final Setting<Double> x, y, z;

    Velocity(ClientRuntime r, AuraModule aura) {
      super(
          r,
          "velocity",
          "Velocity",
          "Control knockback with independent Legit attacks, sprint jumps and packet modes.",
          ModuleCategory.COMBAT);
      this.aura = aura;
      mode =
          setting(
              Setting.enumeration(
                  "mode",
                  "Mode",
                  "Legit: nearby attacks and sprint jumps. Intave: attack slowdown. Cancel/Custom:"
                      + " packet knockback changes.",
                  Mode.CANCEL,
                  Mode.class,
                  guard));
      fire =
          bool(
              "ignore-on-fire",
              "Ignore On Fire",
              "Leave Cancel/Custom packet knockback unchanged while burning.",
              true);
      x =
          number("velocity-x-modifier", "Velocity X Modifier", 0, -5, 5, 1)
              .visibleWhen(() -> mode.get() == Mode.CUSTOM);
      y =
          number("velocity-y-modifier", "Velocity Y Modifier", 1, -5, 5, 1)
              .visibleWhen(() -> mode.get() == Mode.CUSTOM);
      z =
          number("velocity-z-modifier", "Velocity Z Modifier", 0, -5, 5, 1)
              .visibleWhen(() -> mode.get() == Mode.CUSTOM);
      intave =
          mode("intave-mode", "Intave Mode", Intave.INTAVE_LATEST, Intave.class)
              .visibleWhen(() -> mode.get() == Mode.INTAVE);
      reduceTicks =
          integer("reduce-ticks", "Reduce Ticks", 2, 1, 3)
              .visibleWhen(() -> mode.get() == Mode.LEGIT);
      jumpEnabled =
          bool(
                  "jump-reset",
                  "Jump Reset",
                  "Try one normal jump on grounded knockback. The standalone Jump Reset takes"
                      + " precedence.",
                  true)
              .visibleWhen(() -> false);
      jumpSprint =
          bool(
                  "jump-only-sprinting",
                  "Jump Only Sprinting",
                  "Require sprinting for the embedded jump reset.",
                  true)
              .visibleWhen(() -> false);
      reduce =
          bool(
                  "reduce",
                  "Attack Reduction",
                  "Requires enabled Aura and a valid target within 3 blocks. Disable for jump input"
                      + " only.",
                  true)
              .visibleWhen(() -> false);
      sprint =
          bool(
                  "only-sprinting",
                  "Attacks Only Sprinting",
                  "Require sprinting for Aura reduction attacks; independent of jump settings.",
                  true)
              .visibleWhen(() -> false);
      whenReady =
          bool(
                  "reduce-when-can-attack",
                  "Respect Aura Delay",
                  "Submit reduction attacks only when Aura's attack delay has elapsed.",
                  true)
              .visibleWhen(() -> false);
      attacks =
          setting(
                  Setting.integer(
                      "attack-times",
                      "Attack Attempts",
                      "Input ticks with an Aura reduction opportunity after knockback; blocked"
                          + " attempts still expire.",
                      1,
                      1,
                      5,
                      guard))
              .visibleWhen(() -> false);
    }

    boolean acceptsJumpReset() {
      return mode.get() == Mode.LEGIT || mode.get() == Mode.INTAVE;
    }

    Setting<Mode> knockbackMode() {
      return mode;
    }

    @Override
    protected void onEnable(ModuleContext c) {
      LegitVelocity legit =
          new LegitVelocity(this, aura, c, reduceTicks, () -> mode.get() == Mode.LEGIT);
      c.observe(mode, (s, before, after) -> legit.reset());
      int[] pending = {0};
      KnockbackJumpState jump = new KnockbackJumpState();
      boolean[] slowed = {false};
      ControlRequest<Boolean> key = c.controls().key(InputKey.JUMP, 50);
      Runnable reset =
          () -> {
            pending[0] = 0;
            jump.clear();
            slowed[0] = false;
            key.clear();
          };
      c.observe(mode, (s, before, after) -> reset.run());
      c.observe(intave, (s, before, after) -> reset.run());
      c.observe(fire, (s, before, after) -> reset.run());
      c.observe(jumpEnabled, (s, before, after) -> reset.run());
      c.observe(jumpSprint, (s, before, after) -> reset.run());
      c.observe(reduce, (s, before, after) -> reset.run());
      c.observe(sprint, (s, before, after) -> reset.run());
      c.observe(whenReady, (s, before, after) -> reset.run());
      c.observe(attacks, (s, before, after) -> reset.run());
      c.listen(
          ClientPacketEvent.class,
          e -> {
            if (!(e.packet() instanceof S12PacketEntityVelocity)) return;
            S12PacketEntityVelocity packet = (S12PacketEntityVelocity) e.packet();
            EntityPlayerSP p = mc().thePlayer;
            if (packet.getEntityID() != p.getEntityId()) return;
            if (fire.get() && p.isBurning()) {
              reset.run();
              return;
            }
            if (mode.get() == Mode.LEGIT) return;
            if (mode.get() == Mode.CANCEL) e.cancel();
            if (mode.get() == Mode.CUSTOM) {
              p.setVelocity(
                  (int) (packet.getMotionX() * x.get()) / 8000.0,
                  (int) (packet.getMotionY() * y.get()) / 8000.0,
                  (int) (packet.getMotionZ() * z.get()) / 8000.0);
              e.cancel();
            }
          });
      c.listen(HitSlowdownEvent.class, e -> slowed[0] = true);
      c.listen(
          PlayerAttackEvent.class,
          -20,
          false,
          e -> {
            EntityPlayerSP p = mc().thePlayer;
            if (mode.get() != Mode.INTAVE || p.hurtTime <= 0) return;
            // Yuri applies the liquid/web restriction only to Intave 13. Ignore On Fire
            // belongs to Cancel/Custom packet handling, not either Intave attack mode.
            if (intave.get() == Intave.INTAVE_13
                && (e.target() == null
                    || GameActions.liquid(p)
                    || ((perihelion.integration.minecraft189.control.EntityStateAccess) p)
                        .perihelion$inWeb())) return;
            double scale =
                intave.get() == Intave.INTAVE_13
                    ? p.onGround ? 0.52 : 0.8
                    : !slowed[0] && p.isSprinting() ? 0.6 : 1;
            p.motionX *= scale;
            p.motionZ *= scale;
            if (intave.get() == Intave.INTAVE_LATEST) {
              if (!slowed[0] && p.isSprinting()) p.setSprinting(false);
              slowed[0] = false;
            }
          });
      c.listen(
          PlayerControlEvent.class,
          e -> {
            // Legit owns its own activation-scoped prediction and jump requests.
          });
    }
  }

  static final class HitSelect extends GameplayModule {
    private final Setting<Integer> pause, minimumCombo;
    private final Setting<Boolean> lowHealth,
        fakeSwing,
        forceFirst,
        critical,
        jumping,
        resetSwap,
        resetDamage;

    HitSelect(ClientRuntime r) {
      super(
          r,
          "hitselect",
          "Hit Select",
          "Hold attacks for clean hits and combos.",
          ModuleCategory.COMBAT);
      pause = setting(Setting.integer("pause", "Pause (ms)", "Pause (ms)", 0, 0, 500, 50, guard));
      minimumCombo = integer("only-in-combo", "Only In Combo", 0, 0, 10);
      lowHealth = bool("disable-on-low-health", "Disable On Low Health", true);
      fakeSwing = bool("fake-swing", "Fake Swing", false);
      forceFirst = bool("force-first-hit", "Force First Hit", false);
      critical = bool("only-critical-hits", "Only Critical Hits", false);
      jumping = bool("only-while-jumping", "Only While Jumping", false);
      resetSwap = bool("reset-combo-on-swap", "Reset Combo On Swap", false);
      resetDamage = bool("reset-on-damage", "Reset On Damage", false);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      HitSelectState state = new HitSelectState();
      c.listen(
          PlayerControlEvent.class,
          e -> {
            if (e != PlayerControlEvent.BEFORE_INPUT) return;
            EntityPlayerSP player = mc().thePlayer;
            net.minecraft.entity.Entity tracked = mc().theWorld.getEntityByID(state.lastTarget());
            EntityLivingBase target =
                tracked instanceof EntityLivingBase ? (EntityLivingBase) tracked : null;
            state.tick(
                now(),
                player.inventory.currentItem,
                player.hurtTime,
                player.maxHurtTime,
                target == null ? 0 : target.hurtTime,
                target == null ? 0 : target.maxHurtTime,
                target != null,
                minimumCombo.get() > 0,
                resetSwap.get(),
                resetDamage.get());
          });
      c.listen(
          PlayerAttackEvent.class,
          100,
          false,
          e -> {
            EntityLivingBase target = e.target();
            if (target == null) return;
            EntityPlayerSP player = mc().thePlayer;
            boolean criticalHold =
                critical.get()
                    && !player.onGround
                    && player.fallDistance == 0
                    && !player.isInWater()
                    && player.ridingEntity == null
                    && !player.isPotionActive(Potion.blindness)
                    && !player.isOnLadder();
            boolean hold =
                state.hold(
                    now(),
                    target.getEntityId(),
                    target.hurtTime,
                    pause.get(),
                    minimumCombo.get(),
                    lowHealth.get()
                        && !player.capabilities.isCreativeMode
                        && player.getHealth() <= 4,
                    jumping.get()
                        && !perihelion.integration.minecraft189.HookBridge.gameControls()
                            .physicalKeyDown(InputKey.JUMP),
                    criticalHold,
                    forceFirst.get());
            if (hold) {
              e.cancel();
              if (fakeSwing.get()) {
                player.isSwingInProgress = false;
                player.swingItem();
              }
            } else state.attacked(target.getEntityId(), now(), minimumCombo.get() > 0);
          });
    }
  }

  static final class TargetStrafe extends GameplayModule {
    private final AuraModule aura;
    private final Setting<Double> range;
    private final Setting<Boolean> jump;

    TargetStrafe(ClientRuntime r, AuraModule aura) {
      super(
          r,
          "targetstrafe",
          "Target Strafe",
          "Orbit the current Aura target while moving.",
          ModuleCategory.MOVEMENT);
      this.aura = aura;
      range = number("range", "Range", 1, 0.2, 6, 0.1);
      jump = bool("hold-jump", "Hold Jump", false);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      TargetStrafePolicy.State state = new TargetStrafePolicy.State();
      c.listen(
          PlayerControlEvent.class,
          0,
          false,
          event -> {
            if (event != PlayerControlEvent.BEFORE_INPUT) return;
            EntityLivingBase target = aura.target();
            EntityPlayerSP p = mc().thePlayer;
            boolean enabled =
                active("aura")
                    && target != null
                    && !active("scaffold")
                    && mc().gameSettings.keyBindForward.isKeyDown()
                    && (active("flight") || active("speed"))
                    && (!jump.get() || mc().gameSettings.keyBindJump.isKeyDown());
            if (!enabled) {
              state.enabled = false;
              return;
            }
            boolean support =
                TargetStrafePolicy.fullBlockBelow(
                    offset ->
                        mc().theWorld
                            .getBlockState(
                                new net.minecraft.util.BlockPos(p.posX, p.posY - offset, p.posZ))
                            .getBlock()
                            .isFullBlock());
            if (state.collision(p.isCollidedHorizontally || !support) && GameActions.moving(p)) {
              // Yuri reprojects existing motion once at the collision edge, before switching
              // to the new orbit yaw. Resolve the native equivalent of its prior movementYaw.
              MovementYawEvent movement = new MovementYawEvent(p.rotationYaw);
              perihelion.integration.minecraft189.HookBridge.clientEvent(movement);
              double heading = GameActions.direction(movement.yaw(), p.moveForward, p.moveStrafing);
              double[] motion = TargetStrafePolicy.collisionMotion(p.motionX, p.motionZ, heading);
              p.motionX = motion[0];
              p.motionZ = motion[1];
            }
            state.yaw =
                TargetStrafePolicy.orbitYaw(
                    p.posX,
                    p.posZ,
                    target.posX,
                    target.posZ,
                    state.left,
                    range.get() + Math.random() / 100f);
            state.enabled = true;
          });
      c.listen(StrafeEvent.class, -30, true, state::apply);
      c.listen(MovementYawEvent.class, -30, false, state::apply);
    }
  }
}
