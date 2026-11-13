package perihelion.integration.minecraft189.module.gameplay;

import net.minecraft.block.BlockLiquid;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.potion.Potion;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import perihelion.core.control.ControlRequest;
import perihelion.core.control.InputKey;
import perihelion.core.control.Rotation;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleCategory;
import perihelion.core.module.ModuleContext;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.control.PlayerGroundAccess;
import perihelion.integration.minecraft189.event.*;

final class MovementModules {
  private MovementModules() {}

  static final class Flight extends GameplayModule {
    enum Mode {
      MOTION,
      NCP
    }

    private final Setting<Mode> mode;
    private final Setting<Double> speed;

    Flight(ClientRuntime r) {
      super(r, "flight", "Flight", "Motion and NCP flight.", ModuleCategory.MOVEMENT);
      mode = mode("mode", "Mode", Mode.MOTION, Mode.class);
      speed =
          number("motion-speed", "Motion Speed", 0.9, 0.1, 5, 0.1)
              .visibleWhen(() -> mode.get() == Mode.MOTION);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      c.listen(
          StrafeEvent.class,
          e -> {
            EntityPlayerSP p = mc().thePlayer;
            GameActions.strafeSpeed(
                e,
                p,
                mode.get() == Mode.MOTION ? speed.get() : GameActions.baseSpeed(p),
                mode.get() == Mode.MOTION ? 0 : Math.random() / 2000);
            if (mode.get() == Mode.MOTION)
              p.motionY =
                  mc().gameSettings.keyBindJump.isKeyDown()
                      ? 0.5
                      : mc().gameSettings.keyBindSneak.isKeyDown() ? -0.5 : 0;
          });
      c.listen(
          MotionPacketEvent.class,
          e -> {
            if (mode.get() == Mode.NCP) {
              e.y(
                  e.y()
                      + 1E-5
                      + (mc().thePlayer.ticksExisted % 2 == 0 ? 1 : -1)
                          * (1E-10 + Math.random() * (1E-5 - 1E-10)));
              mc().thePlayer.motionY = 0;
            }
          });
    }
  }

  static final class Speed extends GameplayModule {
    enum Mode {
      VANILLA,
      LEGIT,
      POLAR,
      INTAVE,
      NCP,
      LIBRECRAFT
    }

    private final Setting<Mode> mode;
    private final Setting<Double> speed;
    private final AuraModule aura;
    private int air, ticks, setback;
    private java.util.function.BooleanSupplier buildingRoutine = () -> false;

    void buildingRoutine(java.util.function.BooleanSupplier busy) {
      buildingRoutine = java.util.Objects.requireNonNull(busy);
    }

    Speed(ClientRuntime r, AuraModule aura) {
      super(r, "speed", "Speed", "Yuri movement speed modes.", ModuleCategory.MOVEMENT);
      this.aura = aura;
      mode = mode("mode", "Mode", Mode.VANILLA, Mode.class);
      speed =
          number("speed", "Speed", 0.9, 0.1, 5, 0.1).visibleWhen(() -> mode.get() == Mode.VANILLA);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      air = ticks = setback = 0;
      EntityPlayerSP original = mc().thePlayer;
      float jumpFactor = original.jumpMovementFactor;
      ControlRequest<Float> timer = c.controls().timerSpeed(20);
      ControlRequest<Rotation> rotation = c.controls().rotation(20);
      ControlRequest<Boolean> jump = c.controls().key(InputKey.JUMP, 20);
      Rotation[] aim = {Rotation.server(original.rotationYaw, original.rotationPitch)};
      GameActions.movementFix(c, rotation, () -> mode.get() == Mode.LEGIT);
      c.onCleanup(
          () -> {
            if (mc().thePlayer == original) original.jumpMovementFactor = jumpFactor;
          });
      c.observe(
          mode,
          (s, before, after) -> {
            air = ticks = setback = 0;
            timer.clear();
            rotation.clear();
            jump.clear();
            original.jumpMovementFactor = jumpFactor;
          });
      c.listenNetwork(
          PacketReceiveEvent.class,
          0,
          false,
          e -> {
            if (e.packet() instanceof S08PacketPlayerPosLook && mode.get() == Mode.NCP)
              c.deferForWorld(
                  () -> {
                    if (mode.get() == Mode.NCP && setback == 0) setback = 60;
                  });
          });
      c.listen(
          PlayerControlEvent.class,
          e -> {
            if (e != PlayerControlEvent.BEFORE_INPUT) return;
            EntityPlayerSP p = mc().thePlayer;
            air = ((PlayerGroundAccess) p).perihelion$groundTicks().beforeAir();
            ticks++;
            if (setback > 0) setback--;
            timer.clear();
            jump.clear();
            if (buildingRoutine.getAsBoolean()) {
              rotation.clear();
              p.jumpMovementFactor = jumpFactor;
              return;
            }
            if (mode.get() == Mode.LEGIT && legitGround(p)) {
              aim[0] =
                  RotationDynamics.smooth(
                      mc(),
                      aim[0],
                      Rotation.server(RotationDynamics.movementYaw(mc()), p.rotationPitch),
                      10);
              rotation.setForTicks(aim[0], 1);
            } else rotation.clear();
            if (mode.get() == Mode.INTAVE) {
              if (air == 1) {
                p.motionX *= 1.005;
                p.motionZ *= 1.005;
                p.motionX *= 1.0045;
                p.motionZ *= 1.0045;
              } else if (air >= 2 && air <= 6) {
                p.motionX *= 1.011;
                p.motionZ *= 1.001;
              }
              timer.setForTicks(1.0075F, 1);
            } else if (mode.get() == Mode.NCP
                && setback == 0
                && GameActions.moving(p)
                && !p.isInWater()) {
              if (p.onGround && !mc().gameSettings.keyBindJump.isKeyDown()) p.jump();
              double yaw = GameActions.direction(p);
              if (air == 2) {
                double speed = p.isPotionActive(Potion.moveSpeed) ? 0.48F : 0.3222259F;
                p.motionX = -net.minecraft.util.MathHelper.sin((float) yaw) * speed;
                p.motionZ = net.minecraft.util.MathHelper.cos((float) yaw) * speed;
              } else if (air == 8 || air == 9) p.motionY -= 0.07F;
              else {
                // Yuri's no-argument strafe samples the norm again after assigning X.
                p.motionX =
                    -net.minecraft.util.MathHelper.sin((float) yaw)
                        * Math.hypot(p.motionX, p.motionZ);
                p.motionZ =
                    net.minecraft.util.MathHelper.cos((float) yaw)
                        * Math.hypot(p.motionX, p.motionZ);
              }
            } else if (mode.get() == Mode.LIBRECRAFT) {
              if (ticks == 4) p.motionY = -0.09800000190734864;
              Rotation targetRotation = runtime.controls().rotation().orElse(null);
              boolean straight =
                  targetRotation == null
                      ? Math.abs(p.moveStrafing) < 0.1
                      : Math.abs(
                              net.minecraft.util.MathHelper.wrapAngleTo180_float(
                                  (float) Math.toDegrees(GameActions.direction(p))
                                      - targetRotation.yaw()))
                          < 45;
              p.jumpMovementFactor = straight ? 0.026499F : 0.0244F;
              if (GameActions.speed(p) < 0.215 && !p.onGround) GameActions.strafe(p, 0.215);
              if (p.onGround && GameActions.moving(p)) {
                ticks = 0;
                jump.setForTicks(false, 1);
                p.jump();
                if (!p.isAirBorne) return;
                timer.setForTicks(1.25F, 1);
                GameActions.strafe(p, p.isPotionActive(Potion.moveSpeed) ? 0.5 : 0.425);
              } else if (!GameActions.moving(p)) {
                timer.clear();
                p.motionX = p.motionZ = 0;
              }
              if (p.hurtTime > 6 && active("aura") && aura.target() != null)
                GameActions.strafe(p, 0.455);
              if (GameActions.moving(p) && !p.onGround) GameActions.strafe(p, GameActions.speed(p));
            }
          });
      c.listen(
          StrafeEvent.class,
          e -> {
            if (buildingRoutine.getAsBoolean()) return;
            EntityPlayerSP p = mc().thePlayer;
            if (mode.get() == Mode.NCP || mode.get() == Mode.LIBRECRAFT) return;
            if (mode.get() == Mode.LEGIT) {
              if (legitGround(p)) p.jump();
              return;
            }
            if (GameActions.moving(p)
                && p.onGround
                && (mode.get() == Mode.VANILLA
                    || (!mc().gameSettings.keyBindJump.isKeyDown()
                        && !GameActions.liquid(p)
                        && !((perihelion.integration.minecraft189.control.EntityStateAccess) p)
                            .perihelion$inWeb()))) p.jump();
            if (mode.get() == Mode.VANILLA) {
              GameActions.strafeSpeed(e, p, speed.get().floatValue(), 0);
            }
            if (mode.get() == Mode.POLAR)
              e.friction(
                  e.friction()
                      * (mc().gameSettings.keyBindLeft.isKeyDown()
                              || mc().gameSettings.keyBindRight.isKeyDown()
                          ? 1.017F
                          : 1.033F));
          });
    }

    private boolean legitGround(EntityPlayerSP p) {
      return mc().currentScreen == null
          && !active("freecam")
          && GameActions.moving(p)
          && p.onGround
          && !mc().gameSettings.keyBindJump.isKeyDown()
          && !GameActions.liquid(p)
          && !((perihelion.integration.minecraft189.control.EntityStateAccess) p)
              .perihelion$inWeb();
    }
  }

  static final class Step extends GameplayModule {
    enum Mode {
      VANILLA,
      NCP
    }

    private final Setting<Mode> mode;
    private final Setting<Double> height;

    Step(ClientRuntime r) {
      super(r, "step", "Step", "Step over obstacles.", ModuleCategory.MOVEMENT);
      mode = mode("mode", "Mode", Mode.VANILLA, Mode.class);
      height =
          number("height", "Height", 1, 1, 10, 0.1).visibleWhen(() -> mode.get() == Mode.VANILLA);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      EntityPlayerSP p = mc().thePlayer;
      float original = p.stepHeight;
      boolean[] stepping = {false};
      c.onCleanup(
          () -> {
            if (mc().thePlayer == p) p.stepHeight = original;
          });
      c.listen(
          PlayerControlEvent.class,
          e -> {
            if (e != PlayerControlEvent.BEFORE_MOTION) return;
            p.stepHeight =
                mode.get() == Mode.VANILLA && p.onGround && !GameActions.liquid(p)
                    ? height.get().floatValue()
                    : original;
            if (mode.get() != Mode.NCP) return;
            if (p.onGround && p.isCollidedHorizontally && !p.isPotionActive(Potion.jump)) {
              p.jump();
              p.motionX = p.motionZ = 0;
              stepping[0] = true;
            }
            if (((PlayerGroundAccess) p).perihelion$groundTicks().air() == 3 && stepping[0]) {
              p.motionY = ((p.motionY - 0.08) * 0.98F - 0.08) * 0.98F;
              GameActions.strafe(p, GameActions.baseSpeed(p) * 0.6 - Math.random() / 100 - 0.05);
              stepping[0] = false;
            }
          });
    }
  }

  static final class WaterWalk extends GameplayModule {
    enum Mode {
      VANILLA,
      NCP
    }

    private final Setting<Mode> mode;

    WaterWalk(ClientRuntime r) {
      super(
          r,
          "waterwalk",
          "Water Walk",
          "Liquid surface collision and NCP offsets.",
          ModuleCategory.MOVEMENT);
      mode = mode("mode", "Mode", Mode.VANILLA, Mode.class);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      c.listen(
          BlockCollisionEvent.class,
          e -> {
            if (e.block() instanceof BlockLiquid && !mc().gameSettings.keyBindSneak.isKeyDown()) {
              BlockPos p = e.position();
              e.box(new AxisAlignedBB(p, p.add(1, 1, 1)));
            }
          });
      c.listen(
          MotionPacketEvent.class,
          e -> {
            if (mode.get() == Mode.NCP
                && mc().thePlayer.ticksExisted % 2 == 0
                && GameActions.onLiquid(mc())) e.y(e.y() - 0.015625);
          });
    }
  }

  static final class SafeWalk extends GameplayModule {
    enum Mode {
      LEGACY,
      EDGE,
      SNEAK
    }

    enum Tolerance {
      LOW(.05),
      MEDIUM(.15),
      HIGH(.25),
      VERY_HIGH(.3);
      final double value;

      Tolerance(double value) {
        this.value = value;
      }
    }

    private final Setting<Mode> mode;
    private final Setting<Boolean> blocks;
    private final Setting<Double> pitch;
    private final Setting<Tolerance> tolerance;

    SafeWalk(ClientRuntime r) {
      super(r, "safewalk", "Safe Walk", "Clamp movement at block edges.", ModuleCategory.MOVEMENT);
      mode = mode("mode", "Mode", Mode.LEGACY, Mode.class);
      blocks =
          bool("blocks-only", "Blocks Only", true).visibleWhen(() -> mode.get() != Mode.LEGACY);
      pitch =
          number("minimum-pitch", "Minimum Pitch", 65, 19, 85, 1)
              .visibleWhen(() -> mode.get() != Mode.LEGACY);
      tolerance =
          mode("edge-tolerance", "Edge Tolerance", Tolerance.MEDIUM, Tolerance.class)
              .visibleWhen(() -> mode.get() == Mode.SNEAK);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      ControlRequest<Boolean> sneak = c.controls().key(InputKey.SNEAK, 30);
      c.observe(mode, (s, b, a) -> sneak.clear());
      c.listen(
          PlayerControlEvent.class,
          e -> {
            if (e == PlayerControlEvent.BEFORE_INPUT) {
              sneak.clear();
              return;
            }
            // Read the effective keys after every module has submitted this input pass's claims.
            if (e != PlayerControlEvent.BEFORE_ACTIONS) return;
            if (mode.get() != Mode.SNEAK || !conditions()) return;
            EntityPlayerSP p = mc().thePlayer;
            net.minecraft.client.settings.GameSettings keys = mc().gameSettings;
            float forward =
                (keys.keyBindForward.isKeyDown() ? 1 : 0) - (keys.keyBindBack.isKeyDown() ? 1 : 0);
            float strafe =
                (keys.keyBindLeft.isKeyDown() ? 1 : 0) - (keys.keyBindRight.isKeyDown() ? 1 : 0);
            double[] motion =
                perihelion.integration.minecraft189.module.B4BuildingPolicy.projectedMotion(
                    p.motionX, p.motionZ, forward, strafe, p.getAIMoveSpeed(), p.rotationYaw);
            double nx = p.posX + motion[0], nz = p.posZ + motion[1], t = tolerance.get().value;
            int y = net.minecraft.util.MathHelper.floor_double(p.getEntityBoundingBox().minY - .01);
            boolean supported = false;
            for (double x : new double[] {nx - t, nx + t})
              for (double z : new double[] {nz - t, nz + t})
                supported |=
                    mc().theWorld
                        .getBlockState(new BlockPos(x, y, z))
                        .getBlock()
                        .getMaterial()
                        .isSolid();
            // An empty claim yields to the physical sneak key; do not force a release.
            if (!supported) sneak.setForTicks(true, 1);
          });
      c.listen(
          SafeWalkEvent.class,
          e -> {
            EntityPlayerSP p = mc().thePlayer;
            if (mode.get() == Mode.EDGE) {
              if (conditions()) e.cancel();
            } else if (mode.get() == Mode.LEGACY
                && !mc().gameSettings.keyBindJump.isKeyDown()
                && (!p.onGround
                    || mc().theWorld.isAirBlock(new BlockPos(p.posX, p.posY - 1, p.posZ))))
              e.cancel();
          });
    }

    private boolean conditions() {
      return mc().currentScreen == null
          && mc().inGameHasFocus
          && !active("freecam")
          && mc().thePlayer.onGround
          && (pitch.get() == 19 || mc().thePlayer.rotationPitch >= pitch.get())
          && (!blocks.get()
              || perihelion.integration.minecraft189.module.B4BuildingItems.blocks(
                  mc().thePlayer.getHeldItem()));
    }
  }

  static final class Stasis extends GameplayModule {
    private volatile boolean suppressPackets;

    Stasis(ClientRuntime r) {
      super(
          r,
          "stasis",
          "Stasis",
          "Freeze for 45 ticks, release for one tick.",
          ModuleCategory.MOVEMENT);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      EntityPlayerSP p = mc().thePlayer;
      double[] saved = {p.motionX, p.motionY, p.motionZ};
      int[] tick = {0};
      boolean[] release = {false};
      suppressPackets = p.hurtTime == 0;
      c.onCleanup(
          () -> {
            suppressPackets = false;
            if (mc().thePlayer == p) {
              p.motionX = saved[0];
              p.motionY = saved[1];
              p.motionZ = saved[2];
            }
          });
      c.listen(
          PlayerControlEvent.class,
          e -> {
            if (e != PlayerControlEvent.BEFORE_INPUT) return;
            if (++tick[0] >= (release[0] ? 1 : 45)) {
              tick[0] = 0;
              release[0] = !release[0];
              if (release[0]) {
                p.motionX = saved[0];
                p.motionY = saved[1];
                p.motionZ = saved[2];
              } else {
                saved[0] = p.motionX;
                saved[1] = p.motionY;
                saved[2] = p.motionZ;
              }
            }
            if (!release[0]) p.motionX = p.motionY = p.motionZ = 0;
            suppressPackets = !release[0] && p.hurtTime == 0;
            if (p.onGround) c.requestDisable();
          });
      c.listen(
          MoveEvent.class,
          e -> {
            if (!release[0]) {
              e.set(0, 0, 0);
              p.motionX = p.motionY = p.motionZ = 0;
            }
          });
      c.listen(
          StrafeEvent.class,
          e -> {
            if (!release[0]) e.cancel();
          });
      c.listenNetwork(
          PacketSendEvent.class,
          0,
          false,
          e -> {
            if (suppressPackets
                && e.packet() instanceof C03PacketPlayer
                && !(e.packet() instanceof C03PacketPlayer.C05PacketPlayerLook)) e.cancel();
          });
    }
  }
}
