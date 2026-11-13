package perihelion.integration.minecraft189.module.gameplay;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.item.*;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import perihelion.core.control.ControlRequest;
import perihelion.core.control.InputKey;
import perihelion.core.control.Rotation;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleCategory;
import perihelion.core.module.ModuleContext;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.event.*;

final class PlayerModules {
  private PlayerModules() {}

  static final class Regen extends GameplayModule {
    private final Setting<Integer> health, packets;

    Regen(ClientRuntime r) {
      super(
          r,
          "regen",
          "Regen",
          "Send additional player updates below a health threshold.",
          ModuleCategory.PLAYER);
      health = integer("minimum-health", "Minimum Health", 15, 1, 20);
      packets = integer("speed", "Speed", 20, 1, 100);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      c.listen(
          PlayerControlEvent.class,
          e -> {
            if (e == PlayerControlEvent.BEFORE_MOTION && mc().thePlayer.getHealth() < health.get())
              updates(mc().thePlayer, packets.get());
          });
    }
  }

  static void updates(EntityPlayerSP p, int count) {
    for (int i = 0; i < count; i++)
      GameActions.send(
          new C03PacketPlayer.C06PacketPlayerPosLook(
              p.posX, p.posY, p.posZ, p.rotationYaw, p.rotationPitch, p.onGround));
  }

  static final class FastUse extends GameplayModule {
    private final Setting<Integer> speed;
    private final Setting<Boolean> food, potion, bow;

    FastUse(ClientRuntime r) {
      super(r, "fastuse", "Fast Use", "Accelerate selected held-item use.", ModuleCategory.PLAYER);
      speed = integer("speed", "Speed", 15, 1, 100);
      food = bool("food-items", "Food Items", true);
      potion = bool("potion-items", "Potion Items", true);
      bow = bool("bow-items", "Bow Items", true);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      c.listen(
          PlayerControlEvent.class,
          e -> {
            EntityPlayerSP p = mc().thePlayer;
            if (e != PlayerControlEvent.BEFORE_MOTION
                || p.getHeldItem() == null
                || !p.isUsingItem()
                || p.getItemInUseCount() != 31) return;
            Item item = p.getHeldItem().getItem();
            if (food.get() && item instanceof ItemFood
                || potion.get() && item instanceof ItemPotion
                || bow.get() && item instanceof ItemBow) updates(p, speed.get() + 1);
          });
    }
  }

  static final class AntiVoid extends GameplayModule {
    enum Mode {
      VANILLA,
      NCP
    }

    private final Setting<Mode> mode;
    private final Setting<Integer> distance;

    AntiVoid(ClientRuntime r) {
      super(r, "antivoid", "Anti Void", "Yuri void-fall responses.", ModuleCategory.MOVEMENT);
      mode = mode("mode", "Mode", Mode.VANILLA, Mode.class);
      distance = integer("distance", "Distance", 5, 1, 10);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      c.listen(
          MotionPacketEvent.class,
          e -> {
            EntityPlayerSP p = mc().thePlayer;
            if (p.fallDistance <= distance.get() || GameActions.blockUnder(mc())) return;
            if (mode.get() == Mode.VANILLA)
              GameActions.send(new C03PacketPlayer.C04PacketPlayerPosition());
            else if (p.posY + p.motionY < Math.floor(p.posY)) {
              p.motionY = Math.floor(p.posY) - p.posY;
              if (p.motionY == 0) {
                p.onGround = true;
                e.grounded(true);
              }
            }
          });
    }
  }

  static final class Eagle extends GameplayModule {
    private final Setting<Integer> delay;
    private final Setting<Boolean> block, direction, sneak;
    private final EagleTiming timing = new EagleTiming();

    Eagle(ClientRuntime r) {
      super(
          r, "eagle", "Eagle", "Sneak at an edge with a delayed release.", ModuleCategory.MOVEMENT);
      delay = integer("delay", "Delay (ms)", 50, 0, 200);
      block = bool("block-check", "Block Check", true);
      direction = bool("direction-check", "Direction Check", false);
      sneak = bool("sneak-check", "Sneak Check", true);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      ControlRequest<Boolean> key = c.controls().key(InputKey.SNEAK, 30);
      c.listen(
          PlayerControlEvent.class,
          e -> {
            if (e != PlayerControlEvent.BEFORE_INPUT) return;
            EntityPlayerSP p = mc().thePlayer;
            boolean physical =
                net.minecraft.client.settings.GameSettings.isKeyDown(
                    mc().gameSettings.keyBindSneak);
            if (sneak.get() && !physical) {
              key.clear();
              return;
            }
            boolean eligible =
                !block.get()
                    || p.getHeldItem() != null
                        && p.getHeldItem().getItem() instanceof ItemBlock
                        && !direction.get()
                    || p.moveForward < 0;
            if (!eligible) {
              timing.release();
              key.setForTicks(sneak.get() ? false : physical, 1);
              return;
            }
            if (mc().theWorld.isAirBlock(new BlockPos(p.posX, p.posY - 1, p.posZ)) && p.onGround) {
              key.setForTicks(true, 1);
              timing.edge();
            } else if (p.onGround) {
              key.setForTicks(
                  timing.hold(now(), delay.get(), Math.random()) || !sneak.get() && physical, 1);
            } else key.setForTicks(timing.held() || physical, 1);
          });
    }
  }

  static final class AntiFireball extends GameplayModule {
    private final FireballResponseState state = new FireballResponseState(now());

    AntiFireball(ClientRuntime r) {
      super(r, "antifireball", "Anti Fireball", "Attack nearby fireballs.", ModuleCategory.COMBAT);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      ControlRequest<Rotation> rotation = c.controls().rotation(80);
      state.world(runtime.tasks().generation());
      Entity[] pending = {null};
      Rotation[] aim = {null};
      GameActions.movementFix(c, rotation, () -> true);
      c.listen(
          PlayerControlEvent.class,
          e -> {
            if (e == PlayerControlEvent.BEFORE_ACTIONS) {
              Entity target = pending[0];
              pending[0] = null;
              if (target != null
                  && mc().currentScreen == null
                  && !mc().thePlayer.isDead
                  && !perihelion.integration.minecraft189.HookBridge.gameControls()
                      .outgoingFacts()
                      .bad()
                  && firstFireball() == target
                  && (mc().thePlayer.getHeldItem() == null
                      || !(mc().thePlayer.getHeldItem().getItem() instanceof ItemFireball))
                  && !target.isDead
                  && mc().theWorld.getEntityByID(target.getEntityId()) == target
                  && mc().thePlayer.getDistanceToEntity(target) <= 3) {
                GameActions.previousAction(
                    rotation,
                    previous -> {
                      Vec3 eyes = mc().thePlayer.getPositionEyes(1),
                          direction = Placement.direction(previous);
                      net.minecraft.util.MovingObjectPosition hit =
                          target
                              .getEntityBoundingBox()
                              .expand(
                                  target.getCollisionBorderSize(),
                                  target.getCollisionBorderSize(),
                                  target.getCollisionBorderSize())
                              .calculateIntercept(
                                  eyes,
                                  eyes.addVector(
                                      direction.xCoord * 3,
                                      direction.yCoord * 3,
                                      direction.zCoord * 3));
                      if (hit == null) return;
                      GameActions.send(new C0APacketAnimation());
                      GameActions.send(
                          new C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK));
                      state.record(target.getUniqueID());
                    });
              }
              return;
            }
            if (e != PlayerControlEvent.BEFORE_INPUT) return;
            Rotation origin =
                aim[0] == null
                    ? runtime
                        .controls()
                        .rotation()
                        .orElse(
                            Rotation.server(
                                mc().thePlayer.rotationYaw, mc().thePlayer.rotationPitch))
                    : aim[0];
            rotation.clear();
            pending[0] = null;
            aim[0] = null;
            if (mc().currentScreen != null
                || mc().thePlayer.isDead
                || active("freecam")
                || !state.ready(now())
                || perihelion.integration.minecraft189.HookBridge.gameControls()
                    .outgoingFacts()
                    .bad()) return;
            if (mc().thePlayer.getHeldItem() != null
                && mc().thePlayer.getHeldItem().getItem() instanceof ItemFireball) return;
            Entity entity = firstFireball();
            if (state.observeNearby(entity != null))
              mc().thePlayer
                  .addChatMessage(
                      new net.minecraft.util.ChatComponentText("[Perihelion] Fireball detected!"));
            if (entity != null
                && !entity.isDead
                && mc().theWorld.getEntityByID(entity.getEntityId()) == entity
                && mc().thePlayer.getDistanceToEntity(entity) <= 3
                && !state.attacked(entity.getUniqueID())) {
              double height =
                  Math.max(
                      0,
                      Math.min(
                          mc().thePlayer.posY - entity.posY + mc().thePlayer.getEyeHeight(),
                          (entity.getEntityBoundingBox().maxY - entity.getEntityBoundingBox().minY)
                              * .9));
              aim[0] =
                  RotationDynamics.smooth(
                      mc(),
                      origin,
                      GameActions.yuriLookAt(
                          mc().thePlayer, entity.getPositionVector().addVector(0, height, 0)),
                      10);
              rotation.setForTicks(aim[0], 1);
              if (!rotation.isWinning()) return;
              pending[0] = entity;
            }
          });
    }

    private Entity firstFireball() {
      for (Entity entity : mc().theWorld.loadedEntityList)
        if (entity instanceof EntityFireball && mc().thePlayer.getDistanceToEntity(entity) < 12)
          return entity;
      return null;
    }
  }
}
