package perihelion.integration.minecraft189.module.gameplay;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import perihelion.core.control.ControlRequest;
import perihelion.core.control.HotbarSelection;
import perihelion.core.control.InputKey;
import perihelion.core.control.Rotation;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleCategory;
import perihelion.core.module.ModuleContext;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.control.GameplayAccess;
import perihelion.integration.minecraft189.event.*;

final class RecoveryModules {
  private RecoveryModules() {}

  static void useDown(
      net.minecraft.client.Minecraft game,
      ControlRequest<HotbarSelection> slot,
      ControlRequest<Rotation> rotation) {
    if (game.currentScreen != null
        || game.thePlayer.isDead
        || !perihelion.integration.minecraft189.HookBridge.gameControls()
            .isPreviousRotationApplied(rotation)) return;
    perihelion.integration.minecraft189.HookBridge.gameControls()
        .withHotbar(
            slot,
            stack -> {
              if (stack == null
                  || stack.stackSize <= 0
                  || stack.getItem() != Items.water_bucket
                      && stack.getItem() != Item.getItemFromBlock(Blocks.web)) return;
              perihelion.integration.minecraft189.HookBridge.gameControls()
                  .withPreviousRotation(
                      rotation,
                      () -> {
                        MovingObjectPosition previous = game.objectMouseOver;
                        try {
                          net.minecraft.util.Vec3 direction =
                              Placement.direction(rotation.value().get());
                          double reach = game.playerController.getBlockReachDistance();
                          game.objectMouseOver =
                              game.theWorld.rayTraceBlocks(
                                  game.thePlayer.getPositionEyes(1),
                                  game.thePlayer
                                      .getPositionEyes(1)
                                      .addVector(
                                          direction.xCoord * reach,
                                          direction.yCoord * reach,
                                          direction.zCoord * reach),
                                  false,
                                  false,
                                  true);
                          ((GameplayAccess) game).perihelion$rightClickMouse();
                        } finally {
                          game.objectMouseOver = previous;
                        }
                      });
            });
  }

  static final class AutoApple extends GameplayModule {
    private final Setting<Integer> health, delay, timeout;

    AutoApple(ClientRuntime r) {
      super(
          r,
          "autoapple",
          "Auto Apple",
          "Eat a hotbar golden apple at low health.",
          ModuleCategory.PLAYER);
      health = integer("health", "Health", 15, 1, 20);
      delay = integer("delay", "Delay", 50, 0, 100);
      timeout = integer("max-eat-ticks", "Max Eat Ticks", 40, 20, 60);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      ControlRequest<HotbarSelection> slot = c.controls().hotbar(90);
      ControlRequest<Boolean> use = c.controls().key(InputKey.USE_ITEM, 90);
      int[] attackTicks = {0}, ground = {0}, eating = {-1}, selected = {-1};
      boolean[] started = {false};
      ItemStack[] apple = {null};
      long[] next = {0};
      Runnable stop =
          () -> {
            eating[0] = selected[0] = -1;
            started[0] = false;
            apple[0] = null;
            slot.clear();
            use.clear();
          };
      java.util.function.BooleanSupplier ownsApple =
          () ->
              slot.isWinning()
                  && selected[0] >= 0
                  && apple[0] != null
                  && apple[0].stackSize > 0
                  && apple[0].getItem() instanceof ItemAppleGold
                  && mc().thePlayer.inventory.getStackInSlot(selected[0]) == apple[0];
      c.onCleanup(stop);
      c.listen(
          PlayerAttackEvent.class,
          90,
          false,
          e -> {
            if (eating[0] >= 0) e.cancel();
            attackTicks[0] = 0;
          });
      c.listen(
          PlayerControlEvent.class,
          90,
          false,
          e -> {
            if (e != PlayerControlEvent.BEFORE_INPUT) return;
            if (active("scaffold")) {
              stop.run();
              return;
            }
            attackTicks[0] = mc().currentScreen != null ? 0 : attackTicks[0] + 1;
            ground[0] = mc().thePlayer.onGround ? ground[0] + 1 : 0;
            if (eating[0] >= 0) {
              if (!ownsApple.getAsBoolean()) {
                stop.run();
                return;
              }
              eating[0]++;
              if (mc().thePlayer.isUsingItem()) started[0] = true;
              if (eating[0] >= timeout.get() || started[0] && !mc().thePlayer.isUsingItem()) {
                stop.run();
                next[0] = now() + delay.get() * 10L;
              }
              return;
            }
            if (ground[0] <= 1
                || attackTicks[0] < 10
                || now() < next[0]
                || mc().thePlayer.getHealth() > health.get()) return;
            int found = GameActions.hotbar(mc(), stack -> stack.getItem() instanceof ItemAppleGold);
            if (found < 0) return;
            selected[0] = found;
            apple[0] = mc().thePlayer.inventory.getStackInSlot(found);
            slot.set(HotbarSelection.visible(found));
            if (!ownsApple.getAsBoolean()) {
              stop.run();
              return;
            }
            use.set(true);
            eating[0] = 0;
            started[0] = false;
          });
      // Lower-priority BEFORE_INPUT callbacks can take the hotbar after our first check.
      // Retire use before vanilla gets its next item-use opportunity with that winning slot.
      c.listen(
          PlayerControlEvent.class,
          -1000,
          false,
          e -> {
            if (e == PlayerControlEvent.BEFORE_ACTIONS
                && eating[0] >= 0
                && !ownsApple.getAsBoolean()) stop.run();
          });
    }
  }

  static final class AutoPot extends GameplayModule {
    private final Setting<Integer> health, delay, timeout;
    private int attackTicks;
    private long nextThrowAt;
    private boolean busy;

    AutoPot(ClientRuntime r) {
      super(
          r,
          "autopot",
          "Auto Pot",
          "Aim down and throw a useful splash potion.",
          ModuleCategory.PLAYER);
      health = integer("health", "Health", 15, 1, 20);
      delay = integer("delay", "Delay", 0, 0, 100);
      timeout = integer("max-aim-ticks", "Max Aim Ticks", 10, 2, 30);
    }

    private boolean useful(ItemStack stack) {
      if (!(stack.getItem() instanceof ItemPotion) || !ItemPotion.isSplash(stack.getMetadata()))
        return false;
      java.util.List<PotionEffect> effects = ((ItemPotion) stack.getItem()).getEffects(stack);
      if (effects == null || effects.isEmpty()) return false;
      if (!InventoryPolicy.goodPotion(stack)) return false;
      // Yuri decides from the first listed effect, with jump added to its good-potion IDs.
      PotionEffect effect = effects.get(0);
      int id = effect.getPotionID();
      switch (id) {
        case 1:
        case 3:
        case 5:
        case 6:
        case 8:
        case 10:
        case 11:
        case 12:
        case 13:
        case 14:
        case 21:
        case 22:
        case 23:
          break;
        default:
          return false;
      }
      if ((id == Potion.heal.id || id == Potion.regeneration.id)
          && mc().thePlayer.getHealth() > health.get()) return false;
      PotionEffect current = mc().thePlayer.getActivePotionEffect(Potion.potionTypes[id]);
      return current == null || current.getDuration() == 0;
    }

    boolean busy() {
      return busy;
    }

    @Override
    protected void onEnable(ModuleContext c) {
      ControlRequest<HotbarSelection> slot = c.controls().hotbar(100);
      ControlRequest<Rotation> rotation = c.controls().rotation(100);
      int[] aimTicks = {0}, selected = {-1};
      Rotation[] aim = {null};
      Runnable reset =
          () -> {
            slot.clear();
            rotation.clear();
            selected[0] = -1;
            aimTicks[0] = 0;
            aim[0] = null;
            busy = false;
          };
      c.onCleanup(reset);
      // Yuri's TRADITIONAL correction changes strafe/jump yaw without remapping input.
      c.listen(
          StrafeEvent.class,
          e -> {
            Rotation value = rotation.value().orElse(null);
            if (busy && value != null && rotation.isWinning()) e.yaw(value.yaw());
          });
      c.listen(
          MovementYawEvent.class,
          e -> {
            Rotation value = rotation.value().orElse(null);
            if (e.jump() && busy && value != null && rotation.isWinning()) e.yaw(value.yaw());
          });
      c.listen(
          PlayerAttackEvent.class,
          95,
          false,
          e -> {
            attackTicks = 0;
            if (selected[0] >= 0) e.cancel();
          });
      c.listen(
          PlayerControlEvent.class,
          100,
          false,
          e -> {
            if (e == PlayerControlEvent.BEFORE_INPUT)
              attackTicks = mc().currentScreen != null ? 0 : attackTicks + 1;
            if (mc().currentScreen != null
                || !mc().inGameHasFocus
                || mc().thePlayer.isDead
                || mc().thePlayer.getHealth() <= 0
                || mc().thePlayer.isUsingItem()
                || active("freecam")
                || active("scaffold")) {
              reset.run();
              return;
            }
            if (e == PlayerControlEvent.BEFORE_INPUT) {
              if (selected[0] < 0) {
                int ground =
                    ((perihelion.integration.minecraft189.control.PlayerGroundAccess)
                            mc().thePlayer)
                        .perihelion$groundTicks()
                        .beforeGround();
                if (ground > 1 && attackTicks >= 10 && now() >= nextThrowAt) {
                  selected[0] = GameActions.hotbar(mc(), this::useful);
                  if (selected[0] >= 0) {
                    busy = true;
                    aim[0] =
                        runtime
                            .controls()
                            .rotation()
                            .orElse(
                                Rotation.server(
                                    mc().thePlayer.rotationYaw, mc().thePlayer.rotationPitch));
                  }
                }
                // Starting the routine and handling its aim are separate source updates.
                return;
              }
              ItemStack pending = mc().thePlayer.inventory.getStackInSlot(selected[0]);
              if (pending == null || pending.stackSize <= 0 || !useful(pending)) {
                reset.run();
                return;
              }
              aimTicks[0]++;
              aim[0] =
                  RotationDynamics.smooth(
                      mc(), aim[0], Rotation.server(mc().thePlayer.rotationYaw - 180, 90), 10);
              rotation.setForTicks(aim[0], 1);
            } else if (e == PlayerControlEvent.BEFORE_ACTIONS && selected[0] >= 0) {
              GameActions.previousAction(
                  rotation,
                  previous -> {
                    if (previous.pitch() <= 85) return;
                    // Select only when throwing; keep the original item throughout aiming.
                    slot.set(HotbarSelection.persistent(selected[0]));
                    perihelion.integration.minecraft189.HookBridge.gameControls()
                        .withHotbar(
                            slot,
                            held -> {
                              if (held == null || held.stackSize <= 0 || !useful(held)) return;
                              GameActions.send(new C08PacketPlayerBlockPlacement(held));
                              nextThrowAt = now() + delay.get() * 10L;
                              reset.run();
                            });
                  });
              if (aimTicks[0] >= timeout.get()) reset.run();
            }
          });
    }
  }

  static final class AutoExtinguish extends GameplayModule {
    private final AuraModule aura;

    AutoExtinguish(ClientRuntime r, AuraModule aura) {
      super(
          r,
          "autoextinguish",
          "Auto Extinguish",
          "Place hotbar water while burning.",
          ModuleCategory.PLAYER);
      this.aura = aura;
    }

    @Override
    protected void onEnable(ModuleContext c) {
      ControlRequest<HotbarSelection> slot = c.controls().hotbar(110);
      ControlRequest<Rotation> rotation = c.controls().rotation(110);
      int[] ticks = {-1};
      Rotation[] aim = {null};
      GameActions.movementFix(c, rotation, () -> ticks[0] >= 0);
      c.listen(
          PlayerControlEvent.class,
          110,
          false,
          e -> {
            if (e == PlayerControlEvent.BEFORE_INPUT) {
              if (mc().currentScreen != null
                  || mc().thePlayer.isDead
                  || active("freecam")
                  || active("scaffold")
                  || !mc().thePlayer.isBurning()
                  || mc().thePlayer.isUsingItem()
                  || aura.target() != null) {
                slot.clear();
                rotation.clear();
                ticks[0] = -1;
                aim[0] = null;
                return;
              }
              if (ticks[0] < 0) {
                if (mc().theWorld
                        .getBlockState(
                            new BlockPos(
                                mc().thePlayer.posX, mc().thePlayer.posY, mc().thePlayer.posZ))
                        .getBlock()
                    instanceof net.minecraft.block.BlockLiquid) return;
                int found =
                    GameActions.hotbar(mc(), stack -> stack.getItem() == Items.water_bucket);
                if (found < 0) return;
                slot.set(HotbarSelection.visible(found));
                ticks[0] = 0;
                aim[0] =
                    runtime
                        .controls()
                        .rotation()
                        .orElse(
                            Rotation.server(
                                mc().thePlayer.rotationYaw, mc().thePlayer.rotationPitch));
              }
              aim[0] =
                  RotationDynamics.smooth(
                      mc(), aim[0], Rotation.server(mc().thePlayer.rotationYaw, 90), 10);
              rotation.setForTicks(aim[0], 1);
              ticks[0]++;
            } else if (e == PlayerControlEvent.BEFORE_ACTIONS) {
              if (ticks[0] == 7 && slot.isWinning())
                GameActions.previousAction(rotation, previous -> useDown(mc(), slot, rotation));
              if (ticks[0] >= 10) {
                slot.clear();
                rotation.clear();
                ticks[0] = -1;
                aim[0] = null;
              }
            }
          });
    }
  }

  static final class NoFall extends GameplayModule {
    enum Mode {
      VANILLA,
      MLG,
      LIBRECRAFT
    }

    private final Setting<Mode> mode;
    private final Setting<Boolean> pickup;

    NoFall(ClientRuntime r) {
      super(
          r,
          "nofall",
          "No Fall",
          "Ground packets, landing sequence or an MLG item.",
          ModuleCategory.MOVEMENT);
      mode = mode("mode", "Mode", Mode.VANILLA, Mode.class);
      pickup =
          bool("pickup-water", "Pickup Water", false).visibleWhen(() -> mode.get() == Mode.MLG);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      boolean[] falling = {false};
      boolean[] groundPacket = {false};
      boolean[] mlg = {false};
      boolean[] pickingUp = {false};
      B4WaterPickupState water = new B4WaterPickupState();
      Rotation[] aim = {null};
      ControlRequest<HotbarSelection> slot = c.controls().hotbar(120);
      ControlRequest<Rotation> rotation = c.controls().rotation(120);
      GameActions.movementFix(c, rotation, () -> mode.get() == Mode.MLG);
      c.observe(
          mode,
          (s, before, after) -> {
            slot.clear();
            rotation.clear();
            aim[0] = null;
            mlg[0] = falling[0] = groundPacket[0] = false;
            water.reset();
            pickingUp[0] = false;
          });
      c.observe(
          pickup,
          (s, b, a) -> {
            water.reset();
            pickingUp[0] = false;
          });
      c.listen(
          PlayerControlEvent.class,
          e -> {
            if (e == PlayerControlEvent.BEFORE_INPUT) {
              pickingUp[0] = false;
              if (mc().currentScreen != null || mc().thePlayer.isDead || active("freecam")) {
                slot.clear();
                rotation.clear();
                falling[0] = false;
                groundPacket[0] = false;
                mlg[0] = false;
                aim[0] = null;
                water.reset();
                return;
              }
              groundPacket[0] = mode.get() == Mode.VANILLA && mc().thePlayer.fallDistance >= 3;
              if (mc().thePlayer.fallDistance > 3) falling[0] = true;
              if (mode.get() == Mode.MLG && pickup.get() && water.waiting()) {
                ItemStack held = mc().thePlayer.getHeldItem();
                boolean bucket =
                    held != null
                        && held.getItem() == Items.bucket
                        && mc().thePlayer.inventory.currentItem == water.slot();
                pickingUp[0] = water.ready(now(), mc().thePlayer.onGround, bucket);
                if (water.waiting()) {
                  // Hold the placement aim while airborne and through the landing use.
                  if (aim[0] != null) rotation.setForTicks(aim[0], 1);
                  if (pickingUp[0]) slot.setForTicks(HotbarSelection.persistent(water.slot()), 1);
                  return;
                }
              }
              if (mode.get() != Mode.MLG || mc().thePlayer.fallDistance <= 2.9F) {
                slot.clear();
                if (mode.get() == Mode.MLG && mlg[0]) {
                  aim[0] =
                      RotationDynamics.smooth(
                          mc(),
                          aim[0],
                          Rotation.server(mc().thePlayer.rotationYaw, mc().thePlayer.rotationPitch),
                          10);
                  rotation.setForTicks(aim[0], 1);
                  // Continue the donor manager's return-to-view smoothing while this
                  // activation owns the request, including a simultaneous camera turn.
                  mlg[0] =
                      Math.abs(
                                  net.minecraft.util.MathHelper.wrapAngleTo180_float(
                                      aim[0].yaw() - mc().thePlayer.rotationYaw))
                              >= 1
                          || Math.abs(aim[0].pitch() - mc().thePlayer.rotationPitch) >= 1;
                } else {
                  rotation.clear();
                  aim[0] = null;
                  mlg[0] = false;
                }
                return;
              }
              int found = GameActions.hotbar(mc(), stack -> stack.getItem() == Items.water_bucket);
              if (found < 0)
                found =
                    GameActions.hotbar(
                        mc(),
                        stack ->
                            stack.getItem()
                                == net.minecraft.item.Item.getItemFromBlock(Blocks.web));
              if (found >= 0) {
                slot.setForTicks(HotbarSelection.persistent(found), 1);
                if (aim[0] == null)
                  aim[0] =
                      runtime
                          .controls()
                          .rotation()
                          .orElse(
                              Rotation.server(
                                  mc().thePlayer.rotationYaw, mc().thePlayer.rotationPitch));
                aim[0] =
                    RotationDynamics.smooth(
                        mc(), aim[0], Rotation.server(mc().thePlayer.rotationYaw, 90), 10);
                rotation.setForTicks(aim[0], 1);
                mlg[0] = true;
              } else {
                slot.clear();
                rotation.clear();
                mlg[0] = false;
                aim[0] = null;
              }
            } else if (e == PlayerControlEvent.BEFORE_ACTIONS && pickingUp[0]) {
              GameActions.previousAction(
                  rotation,
                  previous -> {
                    pickupWater(mc(), slot, rotation, previous);
                  });
              // B4's first grounded update consumes the attempt even when its ray misses.
              // A competing native owner likewise skips the use instead of retaining aim.
              water.pickedUp();
            } else if (e == PlayerControlEvent.BEFORE_ACTIONS
                && mode.get() == Mode.MLG
                && slot.isWinning()
                && !mc().thePlayer.isInWater()
                && !mc().theWorld
                    .isMaterialInBB(
                        mc().thePlayer.getEntityBoundingBox(),
                        net.minecraft.block.material.Material.web)
                && !mc().theWorld
                    .isAirBlock(
                        new BlockPos(
                            mc().thePlayer.posX, mc().thePlayer.posY - 2, mc().thePlayer.posZ)))
              GameActions.previousAction(
                  rotation,
                  previous -> {
                    int selected = slot.value().get().slot();
                    ItemStack before = mc().thePlayer.inventory.getStackInSlot(selected);
                    boolean waterItem = before != null && before.getItem() == Items.water_bucket;
                    useDown(mc(), slot, rotation);
                    ItemStack after = mc().thePlayer.inventory.getStackInSlot(selected);
                    if (pickup.get()
                        && waterItem
                        && after != null
                        && after.getItem() == Items.bucket) water.placed(selected, now());
                  });
            else if (e == PlayerControlEvent.BEFORE_MOTION && groundPacket[0]) {
              groundPacket[0] = false;
              GameActions.send(new C03PacketPlayer(true));
            }
          });
      c.listen(
          ClientWalkPacketEvent.class,
          e -> {
            if (mode.get() != Mode.LIBRECRAFT
                || !falling[0]
                || !e.grounded()
                || !mc().thePlayer.onGround) return;
            falling[0] = false;
            mc().thePlayer.onGround = false;
            e.y(e.y() + 1);
            GameActions.send(
                new C03PacketPlayer.C04PacketPlayerPosition(e.x(), e.y() - 1.0784, e.z(), false));
            GameActions.send(
                new C03PacketPlayer.C04PacketPlayerPosition(e.x(), e.y() - 0.5, e.z(), true));
          });
    }

    private static boolean pickupWater(
        net.minecraft.client.Minecraft game,
        ControlRequest<HotbarSelection> slot,
        ControlRequest<Rotation> rotation,
        Rotation previous) {
      if (!game.thePlayer.onGround
          || game.currentScreen != null
          || game.thePlayer.isDead
          || !slot.isWinning()) return false;
      net.minecraft.util.Vec3 eyes = game.thePlayer.getPositionEyes(1),
          direction = B4MovementSupport.direction(previous.yaw(), previous.pitch());
      double reach = game.playerController.getBlockReachDistance();
      MovingObjectPosition target =
          game.theWorld.rayTraceBlocks(
              eyes,
              eyes.addVector(
                  direction.xCoord * reach, direction.yCoord * reach, direction.zCoord * reach),
              false,
              false,
              true);
      if (target == null
          || target.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
          || target.sideHit != net.minecraft.util.EnumFacing.UP) return false;
      boolean[] used = {false};
      perihelion.integration.minecraft189.HookBridge.gameControls()
          .withHotbar(
              slot,
              stack -> {
                if (stack == null || stack.getItem() != Items.bucket) return;
                perihelion.integration.minecraft189.HookBridge.gameControls()
                    .withPreviousRotation(
                        rotation,
                        () -> {
                          // B4 Water Bucket calls both operations even when the support block
                          // accepts interaction. Minecraft.rightClickMouse skips item use in
                          // that case, preventing an empty bucket from collecting the water.
                          game.playerController.onPlayerRightClick(
                              game.thePlayer,
                              game.theWorld,
                              game.thePlayer.getHeldItem(),
                              target.getBlockPos(),
                              target.sideHit,
                              target.hitVec);
                          game.playerController.sendUseItem(
                              game.thePlayer, game.theWorld, game.thePlayer.getHeldItem());
                          used[0] = true;
                        });
              });
      return used[0];
    }
  }
}
