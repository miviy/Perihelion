package perihelion.integration.minecraft189.module.gameplay;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.util.*;
import perihelion.core.control.*;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleCategory;
import perihelion.core.module.ModuleContext;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.control.PlayerGroundAccess;
import perihelion.integration.minecraft189.control.PlayerRotationAccess;
import perihelion.integration.minecraft189.event.*;
import perihelion.integration.minecraft189.network.PacketBufferScope;
import perihelion.integration.minecraft189.network.RawHookBridge;

final class AdditionalMovementModules {
  private AdditionalMovementModules() {}

  static final class NoSlow extends GameplayModule {
    enum Mode {
      VANILLA,
      NCP,
      BLINK,
      GROUND,
      INVALID
    }

    private final Setting<Mode> mode;
    private final Setting<Boolean> food, potion, sword, bow;

    NoSlow(ClientRuntime r) {
      super(
          r,
          "noslow",
          "No Slow",
          "Item slowdown with Vanilla, NCP and OpenB4 Blink/Ground/Invalid modes.",
          ModuleCategory.MOVEMENT);
      mode = mode("mode", "Mode", Mode.VANILLA, Mode.class);
      food = bool("food-items", "Food Items", true);
      potion = bool("potion-items", "Potion Items", true);
      sword = bool("sword-items", "Sword Items", true);
      bow = bool("bow-items", "Bow Items", true);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      B4NoSlowState b4 = new B4NoSlowState();
      ControlRequest<Boolean> jump = c.controls().key(InputKey.JUMP, 35);
      float[] factor = {1};
      PacketBufferScope blink =
          new PacketBufferScope(runtime, c, (packet, outbound) -> outbound, () -> Long.MAX_VALUE);
      blink.collecting(false);
      Runnable reset =
          () -> {
            b4.reset();
            jump.clear();
            factor[0] = 1;
            blink.collecting(false);
            blink.flush(true);
          };
      c.observe(mode, (s, before, after) -> reset.run());
      c.observe(food, (s, before, after) -> reset.run());
      c.observe(potion, (s, before, after) -> reset.run());
      c.observe(bow, (s, before, after) -> reset.run());
      c.observe(sword, (s, before, after) -> reset.run());
      c.onCleanup(b4::reset);
      c.listen(
          ClientSendFactEvent.class,
          1000,
          false,
          e -> {
            if (e.action() == ClientSendFactEvent.Action.USE_ITEM
                && mode.get() == Mode.GROUND
                && b4Affected(e.stack())) {
              EntityPlayerSP player = mc().thePlayer;
              int air =
                  perihelion.integration.minecraft189.HookBridge.gameControls().walkingAirTicks();
              double height = B4MovementSupport.heightAboveGround(mc());
              boolean close = height != -1 && height < .2;
              b4.placement(player.onGround, air, close);
            }
          });
      c.listen(
          ItemSlowdownEvent.class,
          e -> {
            ItemStack stack = mc().thePlayer.getHeldItem();
            if (stack == null || !mc().thePlayer.isUsingItem()) return;
            if (mode.get() == Mode.VANILLA || mode.get() == Mode.NCP) {
              if (selected(stack)) e.cancel();
            } else if (stack.getItem() instanceof ItemSword ? sword.get() : b4Affected(stack)) {
              e.factor(factor[0]);
            }
          });
      c.listen(
          PlayerControlEvent.class,
          e -> {
            if (e != PlayerControlEvent.BEFORE_INPUT) return;
            Mode current = mode.get();
            if (current == Mode.VANILLA || current == Mode.NCP) return;
            EntityPlayerSP p = mc().thePlayer;
            // B4's PacketUtils window survives enabling and mode/category changes.
            int actions =
                perihelion.integration.minecraft189.HookBridge.gameControls().clientActionMask();
            boolean using =
                p.isUsingItem()
                    && (actions & 5) == 0
                    && perihelion.integration.minecraft189.HookBridge.gameControls()
                            .attemptedServerSlot(p.inventory.currentItem)
                        == p.inventory.currentItem
                    && b4Affected(p.getHeldItem());
            B4NoSlowState.Kind kind = B4NoSlowState.Kind.valueOf(current.name());
            boolean before = b4.blinking();
            factor[0] = b4.tick(using, p.onGround, kind);
            if (before != b4.blinking()) {
              blink.collecting(b4.blinking() && !mc().isSingleplayer());
              if (!b4.blinking()) blink.flush(true);
            }
            if (current == Mode.GROUND && b4.suppressJump()) jump.set(false);
            else jump.clear();
            if (current == Mode.INVALID
                && using
                && (actions & 14) == 0
                && p.ticksExisted % 3 == 0) {
              GameActions.send(
                  hypixel()
                      ? new C08PacketPlayerBlockPlacement(
                          new BlockPos(-1, -1, -1), 1, null, 0, 0, 0)
                      : new C08PacketPlayerBlockPlacement(p.getHeldItem()));
            }
          });
      c.listen(
          PlayerControlEvent.class,
          -100,
          false,
          e -> {
            if (e == PlayerControlEvent.BEFORE_ACTIONS
                && mode.get() == Mode.NCP
                && mc().thePlayer.isUsingItem()
                && selected(mc().thePlayer.getHeldItem()))
              GameActions.send(
                  new C07PacketPlayerDigging(
                      C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                      BlockPos.ORIGIN,
                      EnumFacing.DOWN));
          });
    }

    private boolean hypixel() {
      return B4GameContext.onHypixel(mc());
    }

    private boolean b4Affected(ItemStack stack) {
      if (stack == null) return false;
      Item item = stack.getItem();
      if (food.get() && item instanceof ItemFood
          || potion.get()
              && (item instanceof ItemPotion && !ItemPotion.isSplash(stack.getMetadata())
                  || item instanceof ItemBucketMilk)) return true;
      return bow.get()
          && item instanceof ItemBow
          && (mc().thePlayer.inventory.hasItem(Items.arrow)
              || mc().thePlayer.capabilities.isCreativeMode)
          && !stack.getDisplayName().startsWith("\u00a7aKit Select");
    }

    private boolean selected(ItemStack stack) {
      if (stack == null) return false;
      Item item = stack.getItem();
      return food.get() && item instanceof ItemFood
          || potion.get() && item instanceof ItemPotion
          || sword.get() && item instanceof ItemSword
          || bow.get() && item instanceof ItemBow;
    }
  }

  static final class Phase extends GameplayModule {
    enum Mode {
      NORMAL,
      POLAR
    }

    private final Setting<Mode> mode;

    Phase(ClientRuntime r) {
      super(
          r,
          "phase",
          "Phase",
          "Collision nudges and Polar ground offsets.",
          ModuleCategory.MOVEMENT);
      mode = mode("mode", "Mode", Mode.NORMAL, Mode.class);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      boolean[] phasing = {false};
      c.listen(
          MotionPacketEvent.class,
          e -> {
            EntityPlayerSP p = mc().thePlayer;
            double yaw = Math.toRadians(p.rotationYaw), x = Math.sin(yaw), z = Math.cos(yaw);
            phasing[0] = false;
            if (mode.get() == Mode.NORMAL) {
              if (p.isCollidedHorizontally) {
                p.setPosition(p.posX - x * 0.005, p.posY, p.posZ + z * 0.005);
                phasing[0] = true;
              } else if (GameActions.insideBlock(mc())) {
                RawHookBridge.replay(
                    () ->
                        GameActions.send(
                            new C03PacketPlayer.C04PacketPlayerPosition(
                                p.posX - x * 1.5, p.posY, p.posZ + z * 1.5, false)));
                p.motionX *= 0.3;
                p.motionZ *= 0.3;
                phasing[0] = true;
              }
            } else if (mc().gameSettings.keyBindJump.isKeyDown()
                && GameActions.blockNear(mc(), 2)
                && p.posX == p.prevPosX
                && p.posZ == p.prevPosZ
                && p.motionY < 0) {
              double ground = p.posY - GameActions.groundBlockY(mc());
              if (ground < 1.26) {
                p.motionY -= 0.091F;
                if (ground < 1.1) e.grounded(true);
              }
            }
            if (p.isSneaking()) {
              if (mc().gameSettings.keyBindForward.isKeyDown())
                p.setPosition(p.posX - x * 0.00001, p.posY, p.posZ + z * 0.00001);
              if (mc().gameSettings.keyBindBack.isKeyDown())
                p.setPosition(p.posX + x * 0.00001, p.posY, p.posZ - z * 0.00001);
              if (mc().gameSettings.keyBindLeft.isKeyDown())
                p.setPosition(p.posX + x * 0.00001, p.posY, p.posZ);
              if (mc().gameSettings.keyBindRight.isKeyDown())
                p.setPosition(p.posX, p.posY, p.posZ - x * 0.00001);
            }
          });
      c.listen(
          BlockCollisionEvent.class,
          e -> {
            if (mode.get() == Mode.NORMAL
                && phasing[0]
                && e.block() instanceof net.minecraft.block.BlockAir
                && e.position().getY() < mc().thePlayer.posY)
              e.box(
                  new AxisAlignedBB(-15, -1, -15, 15, 1, 15)
                      .offset(e.position().getX(), e.position().getY(), e.position().getZ()));
          });
    }
  }

  static final class LongJump extends GameplayModule {
    enum FireballMode {
      LEGIT,
      LAG,
      B4_AUTO
    }

    private final Setting<Boolean> fireball;
    private final Setting<FireballMode> mode;
    private final Setting<Integer> delay;
    private final Setting<Double> b4Boost, b4Vertical;

    LongJump(ClientRuntime r) {
      super(
          r,
          "longjump",
          "Long Jump",
          "Fireball or Mospixel jump sequence.",
          ModuleCategory.MOVEMENT);
      fireball = bool("fireball", "Fireball", true);
      mode =
          mode("fireball-mode", "Fireball Mode", FireballMode.LEGIT, FireballMode.class)
              .visibleWhen(fireball::get);
      delay =
          integer("delay", "Delay (ms)", 400, 50, 2000)
              .visibleWhen(() -> fireball.get() && mode.get() == FireballMode.LAG);
      b4Boost =
          number("b4-horizontal-boost", "Horizontal Boost", 0, 0, 4, .05)
              .visibleWhen(() -> fireball.get() && mode.get() == FireballMode.B4_AUTO);
      b4Vertical =
          number("b4-vertical-motion", "Vertical Motion", 0, 0, 1, .02)
              .visibleWhen(() -> fireball.get() && mode.get() == FireballMode.B4_AUTO);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      if (fireball.get() && mode.get() == FireballMode.B4_AUTO) {
        b4(c);
        return;
      }
      ControlRequest<HotbarSelection> slot = c.controls().hotbar(100);
      ControlRequest<Rotation> rotation = c.controls().rotation(100);
      GameActions.movementFix(c, rotation, fireball::get);
      int[] ticks = {0}, air = {0};
      boolean[] canDisable = {false};
      EntityPlayerSP p = mc().thePlayer;
      PacketBufferScope buffer =
          new PacketBufferScope(
              runtime,
              c,
              (packet, outbound) ->
                  fireball.get()
                      && mode.get() == FireballMode.LAG
                      && (packet instanceof C03PacketPlayer
                          || PacketModules.regular(packet)
                          || PacketModules.velocity(packet)
                          || PacketModules.teleport(packet)
                          || PacketModules.entity(packet)),
              () -> delay.get());
      Runnable reset =
          () -> {
            slot.clear();
            rotation.clear();
            ticks[0] = air[0] = 0;
            canDisable[0] = false;
            // A mode switch finishes the old ordered sequence on its live connection.
            buffer.collecting(false);
            buffer.flush(true);
            buffer.collecting(true);
          };
      c.observe(
          fireball,
          (s, before, after) -> {
            reset.run();
            // The activation's listeners are selected once; entering B4 needs its own state
            // machine.
            if (after && mode.get() == FireballMode.B4_AUTO) c.requestDisable();
          });
      c.observe(
          mode,
          (s, before, after) -> {
            reset.run();
            if (before == FireballMode.B4_AUTO || after == FireballMode.B4_AUTO) c.requestDisable();
          });
      c.onCleanup(
          () -> {
            if (!fireball.get() && mc().thePlayer == p) p.motionX = p.motionZ = 0;
          });
      c.listenNetwork(
          PacketReceiveEvent.class,
          0,
          false,
          e -> {
            if (e.packet() instanceof S08PacketPlayerPosLook)
              c.deferForWorld(() -> canDisable[0] = false);
          });
      c.listen(
          PlayerControlEvent.class,
          e -> {
            if (e == PlayerControlEvent.BEFORE_INPUT) {
              ticks[0]++;
              if (!fireball.get()) return;
              int found = GameActions.hotbar(mc(), stack -> stack.getItem() == Items.fire_charge);
              if (found < 0) {
                c.requestDisable();
                return;
              }
              if (((PlayerGroundAccess) p).perihelion$groundTicks().beforeGround() == 1)
                p.motionX = p.motionZ = 0;
              slot.set(HotbarSelection.visible(found));
              // The donor smooths from its last submitted global rotation, including a handoff
              // from another module. A losing native request must not advance an unseen turn.
              PlayerRotationAccess reported = (PlayerRotationAccess) p;
              rotation.set(
                  RotationDynamics.smooth(
                      mc(),
                      Rotation.serverUnwrapped(
                          reported.perihelion$reportedYaw(), reported.perihelion$reportedPitch()),
                      Rotation.serverUnwrapped(p.rotationYaw, 90),
                      10));
            } else if (e == PlayerControlEvent.BEFORE_ACTIONS && fireball.get()) {
              if (ticks[0] == 2 && slot.isWinning())
                GameActions.previousAction(
                    rotation,
                    previous ->
                        perihelion.integration.minecraft189.HookBridge.gameControls()
                            .withHotbar(
                                slot,
                                stack -> {
                                  if (stack != null && stack.getItem() == Items.fire_charge)
                                    GameActions.send(new C08PacketPlayerBlockPlacement(stack));
                                }));
              if (ticks[0] >= (mode.get() == FireballMode.LEGIT ? 5 : 40)) c.requestDisable();
            }
          });
      c.listen(
          StrafeEvent.class,
          e -> {
            air[0] = ((PlayerGroundAccess) p).perihelion$groundTicks().air();
            if (p.onGround && !canDisable[0]) p.jump();
            if (fireball.get()) return;
            if (air[0] > 21 && air[0] < 59) {
              p.motionY = -0.2F;
              canDisable[0] = true;
            }
            if (air[0] == 1) {
              canDisable[0] = true;
              GameActions.strafeSpeed(e, p, 1.5F, 0);
            }
            if (canDisable[0] && p.onGround) c.requestDisable();
          });
    }

    private void b4(ModuleContext c) {
      if (stopAfterWorldChange(c, true)) return;
      EntityPlayerSP p = mc().thePlayer;
      int found = GameActions.hotbar(mc(), stack -> stack.getItem() == Items.fire_charge);
      if (found < 0 || p.getHealth() < 3 || B4MovementSupport.heightAboveGround(mc()) > 3) {
        c.requestDisable();
        return;
      }
      B4LongJumpState state = new B4LongJumpState();
      ControlRequest<HotbarSelection> slot = c.controls().hotbar(100);
      ControlRequest<Rotation> rotation = c.controls().rotation(100);
      ControlRequest<Boolean> jump = c.controls().key(InputKey.JUMP, 100);
      java.util.concurrent.atomic.AtomicInteger interactions =
          new java.util.concurrent.atomic.AtomicInteger();
      slot.set(HotbarSelection.visible(found));
      Rotation throwAim = Rotation.serverUnwrapped(p.rotationYaw, 90);
      rotation.set(throwAim);
      GameActions.movementFix(c, rotation, state::aiming, true);
      c.observe(mode, (s, before, after) -> c.requestDisable());
      c.observe(fireball, (s, before, after) -> c.requestDisable());
      c.observe(b4Boost, (s, before, after) -> c.requestDisable());
      c.observe(b4Vertical, (s, before, after) -> c.requestDisable());
      c.listen(
          ClientSendFactEvent.class,
          1000,
          false,
          e -> {
            if (e.action() == ClientSendFactEvent.Action.WALK) interactions.set(0);
            else if (e.action() == ClientSendFactEvent.Action.USE_ENTITY
                || e.action() == ClientSendFactEvent.Action.ATTACK
                || e.action() == ClientSendFactEvent.Action.DIG
                || e.action() == ClientSendFactEvent.Action.RELEASE_USE_ITEM
                || e.action() == ClientSendFactEvent.Action.USE_ITEM
                || e.action() == ClientSendFactEvent.Action.PLACE) interactions.set(1);
          });
      c.listen(
          ClientPacketEvent.class,
          100,
          false,
          e -> {
            if (!(e.packet() instanceof S27PacketExplosion)) return;
            S27PacketExplosion explosion = (S27PacketExplosion) e.packet();
            if (state.explosion(
                p.getDistanceSq(explosion.getX(), explosion.getY(), explosion.getZ()))) {
              e.cancel();
              slot.clear();
              perihelion.integration.minecraft189.HookBridge.gameControls().apply();
            }
          });
      c.listen(
          PlayerControlEvent.class,
          e -> {
            if (e == PlayerControlEvent.BEFORE_INPUT) {
              jump.clear();
              if (!state.advance() || state.timedOut(now())) {
                c.requestDisable();
                return;
              }
              if (state.jumpNow()) jump.set(true);
              if (state.boosting() && b4Vertical.get() > 0) p.motionY = b4Vertical.get();
              // Keep this immutable submitted aim until the throw. A fresh unsent request must not
              // act.
              if (state.aiming()) rotation.set(throwAim);
              else rotation.clear();
            } else if (e == PlayerControlEvent.BEFORE_MOTION && state.aiming()) {
              if (!state.throwNow()) return;
              int fireballSlot =
                  GameActions.hotbar(mc(), stack -> stack.getItem() == Items.fire_charge);
              if (fireballSlot < 0 || p.getHealth() < 3 || interactions.get() != 0) {
                c.requestDisable();
                return;
              }
              slot.set(HotbarSelection.visible(fireballSlot));
              boolean[] submitted = {false};
              boolean acted =
                  perihelion.integration.minecraft189.HookBridge.gameControls()
                      .withSubmittedMotionAim(
                          rotation,
                          aim ->
                              perihelion.integration.minecraft189.HookBridge.gameControls()
                                  .withHotbar(
                                      slot,
                                      stack -> {
                                        if (stack == null || stack.getItem() != Items.fire_charge)
                                          return;
                                        Vec3 eye = p.getPositionEyes(1);
                                        Vec3 direction =
                                            B4MovementSupport.direction(aim.yaw(), aim.pitch());
                                        Vec3 end =
                                            eye.addVector(
                                                direction.xCoord * 3,
                                                direction.yCoord * 3,
                                                direction.zCoord * 3);
                                        MovingObjectPosition hit =
                                            mc().theWorld
                                                .rayTraceBlocks(eye, end, false, false, false);
                                        MovingObjectPosition saved = mc().objectMouseOver;
                                        try {
                                          if (hit != null
                                              && hit.typeOfHit
                                                  == MovingObjectPosition.MovingObjectType.BLOCK)
                                            mc().objectMouseOver = hit;
                                          state.thrown(now());
                                          submitted[0] = true;
                                          ((perihelion.integration.minecraft189.control
                                                      .GameplayAccess)
                                                  mc())
                                              .perihelion$rightClickMouse();
                                        } finally {
                                          mc().objectMouseOver = saved;
                                        }
                                      }));
              if (!acted || !submitted[0]) c.requestDisable();
            } else if (e == PlayerControlEvent.BEFORE_MOTION
                && state.boosting()
                && b4Boost.get() > 0) {
              double speed =
                  b4Boost.get()
                      - java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 1.0e-4;
              double yaw = B4MovementSupport.movementRadians(p);
              // B4 MoveUtils.strafe also propels a stationary player in the facing direction.
              p.motionX = -Math.sin(yaw) * speed;
              p.motionZ = Math.cos(yaw) * speed;
            }
          });
    }
  }
}
