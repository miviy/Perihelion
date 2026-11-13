package perihelion.integration.minecraft189.module.gameplay;

import java.util.*;
import net.minecraft.block.BlockChest;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.tileentity.*;
import net.minecraft.util.*;
import perihelion.core.control.*;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.*;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.*;

final class ConvenienceModules {
  private ConvenienceModules() {}

  static final class AutoEat extends GameplayModule {
    private final Setting<Integer> health, hunger, cooldown;
    private final Setting<Boolean> gapples, golden, inventory;

    AutoEat(ClientRuntime r) {
      super(
          r,
          "autoeat",
          "Auto Eat",
          "Eat useful food with owned hotbar selection and bounded inventory refill.",
          ModuleCategory.PLAYER);
      health = integer("health", "Health Threshold", 15, 1, 20);
      hunger = integer("hunger", "Hunger Threshold", 15, 1, 20);
      gapples = bool("prefer-gapples", "Prefer Gapples", true);
      golden = bool("prefer-golden", "Prefer Golden Food", true);
      inventory = bool("check-inventory", "Refill Empty Hotbar Slot", false);
      cooldown = integer("cooldown", "Cooldown ms", 1000, 250, 5000);
    }

    private double score(ItemStack stack) {
      if (stack == null || stack.stackSize <= 0 || !(stack.getItem() instanceof ItemFood))
        return -1;
      Item item = stack.getItem();
      if (item == Items.rotten_flesh
          || item == Items.spider_eye
          || item == Items.poisonous_potato
          || item == Items.chicken
          || item == Items.fish && stack.getMetadata() == 3) return -1;
      boolean apple = item instanceof ItemAppleGold;
      if (!mc().thePlayer.canEat(apple)) return -1;
      boolean healing =
          apple
              && mc().thePlayer.getHealth() <= health.get()
              && !mc().thePlayer.isPotionActive(net.minecraft.potion.Potion.regeneration);
      boolean hungry =
          mc().thePlayer.getFoodStats().getFoodLevel() <= hunger.get()
              && mc().thePlayer.getFoodStats().needFood();
      if (!healing && !hungry) return -1;
      ItemFood food = (ItemFood) item;
      return food.getHealAmount(stack) * 2
          + food.getSaturationModifier(stack) * 4
          + (healing ? 100 : 0)
          + (apple && gapples.get() ? 15 : 0)
          + (golden.get() && (apple || item == Items.golden_carrot) ? 10 : 0);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      ControlRequest<HotbarSelection> slot = c.controls().hotbar(80);
      ControlRequest<Boolean> use = c.controls().key(InputKey.USE_ITEM, 80);
      net.minecraft.client.entity.EntityPlayerSP player = mc().thePlayer;
      net.minecraft.client.multiplayer.WorldClient world = mc().theWorld;
      ItemStack[] eating = {null};
      int[] selected = {-1}, ticks = {0};
      long[] next = {0};
      Runnable stop =
          () -> {
            if (eating[0] != null
                && mc().thePlayer == player
                && mc().theWorld == world
                && player.getItemInUse() == eating[0])
              mc().playerController.onStoppedUsingItem(player);
            eating[0] = null;
            selected[0] = -1;
            ticks[0] = 0;
            slot.clear();
            use.clear();
            next[0] = now() + cooldown.get();
          };
      c.onCleanup(stop);
      c.listen(
          PlayerAttackEvent.class,
          80,
          false,
          e -> {
            if (eating[0] != null && slot.isWinning()) e.cancel();
          });
      c.listen(
          PlayerControlEvent.class,
          e -> {
            if (e == PlayerControlEvent.BEFORE_INPUT) {
              if (mc().currentScreen != null
                  || !mc().inGameHasFocus
                  || player.isDead
                  || active("freecam")
                  || active("scaffold")
                  || active("blockin")
                  || active("beddefender")
                  || HookBridge.gameControls().physicalKeyDown(InputKey.USE_ITEM)) {
                if (selected[0] >= 0) stop.run();
                return;
              }
              if (selected[0] >= 0) {
                ItemStack held = player.inventory.getStackInSlot(selected[0]);
                if (++ticks[0] > 45
                    || held == null
                    || eating[0] != null && (held != eating[0] || !player.isUsingItem())) {
                  stop.run();
                  return;
                }
                slot.setForTicks(HotbarSelection.visible(selected[0]), 1);
                use.setForTicks(true, 1);
                return;
              }
              if (now() < next[0] || player.isUsingItem()) return;
              int best = -1;
              double bestScore = -1;
              for (int i = 0; i < 9; i++) {
                double value = score(player.inventory.getStackInSlot(i));
                if (value > bestScore) {
                  bestScore = value;
                  best = i;
                }
              }
              if (best < 0
                  && inventory.get()
                  && player.openContainer == player.inventoryContainer) {
                int empty = -1;
                for (int i = 0; i < 9; i++)
                  if (player.inventory.getStackInSlot(i) == null) {
                    empty = i;
                    break;
                  }
                if (empty >= 0) {
                  for (int i = 9; i < 36; i++) {
                    double value = score(player.inventory.getStackInSlot(i));
                    if (value > bestScore) {
                      bestScore = value;
                      best = i;
                    }
                  }
                  if (best >= 9) {
                    mc().playerController
                        .windowClick(player.inventoryContainer.windowId, best, empty, 2, player);
                    next[0] = now() + 500;
                    return; // Re-evaluate authoritative inventory on a later tick.
                  }
                }
              }
              if (best >= 0 && best < 9) {
                selected[0] = best;
                ticks[0] = 0;
                slot.setForTicks(HotbarSelection.visible(best), 1);
                use.setForTicks(true, 1);
              }
            } else if (e == PlayerControlEvent.BEFORE_ACTIONS && selected[0] >= 0) {
              if (!slot.isWinning() || !use.isWinning()) {
                stop.run();
                return;
              }
              if (eating[0] == null)
                HookBridge.gameControls()
                    .withHotbar(
                        slot,
                        stack -> {
                          if (score(stack) >= 0) {
                            mc().playerController.sendUseItem(player, world, stack);
                            // The controller return value reports stack changes, not whether eating
                            // started.
                            eating[0] = player.getItemInUse();
                          }
                        });
            }
          });
    }
  }

  static final class ChestAura extends GameplayModule {
    private final AuraModule aura;
    private final Setting<Double> range;
    private final Setting<Integer> delay;
    private final Setting<Boolean> walls, moveFix;

    ChestAura(ClientRuntime r, AuraModule aura) {
      super(
          r,
          "chestaura",
          "Chest Aura",
          "Open reachable chests and remember confirmed openings for this world.",
          ModuleCategory.WORLD);
      this.aura = aura;
      range = number("range", "Range", 4, 1, 4.5, .1);
      delay = integer("delay", "Open Delay ms", 500, 250, 3000);
      walls = bool("through-walls", "Through Walls", false);
      moveFix = bool("move-fix", "Move Fix", true);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      ControlRequest<Rotation> rotation = c.controls().rotation(45);
      ControlRequest<HotbarSelection> slot = c.controls().hotbar(45);
      Set<BlockPos> opened = new LinkedHashSet<>();
      Map<BlockPos, Long> retry = new LinkedHashMap<>();
      BlockPos[] target = {null}, pending = {null};
      long[] next = {0}, expires = {0};
      GameActions.movementFix(c, rotation, moveFix::get);
      c.listen(
          PlayerControlEvent.class,
          e -> {
            if (e == PlayerControlEvent.BEFORE_INPUT) {
              rotation.clear();
              slot.clear();
              target[0] = null;
              if (pending[0] != null) {
                if (mc().currentScreen instanceof GuiChest && now() <= expires[0]) {
                  remember(opened, pending[0]);
                  for (EnumFacing facing : EnumFacing.HORIZONTALS) {
                    BlockPos other = pending[0].offset(facing);
                    if (mc().theWorld.getBlockState(other).getBlock()
                        == mc().theWorld.getBlockState(pending[0]).getBlock())
                      remember(opened, other);
                  }
                  pending[0] = null;
                } else if (now() > expires[0]) pending[0] = null;
              }
              if (mc().currentScreen != null
                  || !mc().inGameHasFocus
                  || mc().thePlayer.isDead
                  || mc().thePlayer.isUsingItem()
                  || active("freecam")
                  || active("scaffold")
                  || active("blockin")
                  || active("beddefender")
                  || mc().thePlayer.isSneaking()
                  || HookBridge.gameControls().physicalKeyDown(InputKey.ATTACK)
                  || HookBridge.gameControls().physicalKeyDown(InputKey.USE_ITEM)
                  || aura.target() != null
                  || now() < next[0]
                  || pending[0] != null
                  || !HookBridge.gameControls().supportsServerRotation()) return;
              retry.entrySet().removeIf(entry -> now() >= entry.getValue());
              double best = Math.min(range.get(), mc().playerController.getBlockReachDistance());
              best *= best;
              // Loaded tile entities only: no chunk loads or volume scan.
              for (TileEntity tile : mc().theWorld.loadedTileEntityList) {
                if (!(tile instanceof TileEntityChest)
                    || ((TileEntityChest) tile).numPlayersUsing > 0) continue;
                BlockPos pos = tile.getPos();
                if (opened.contains(pos) || retry.containsKey(pos)) continue;
                Vec3 point = new Vec3(pos).addVector(.5, .5, .5);
                double d = mc().thePlayer.getPositionEyes(1).squareDistanceTo(point);
                if (d > best || !visible(pos)) continue;
                best = d;
                target[0] = pos;
              }
              if (target[0] != null) {
                rotation.setForTicks(
                    GameActions.lookAt(mc().thePlayer, new Vec3(target[0]).addVector(.5, .5, .5)),
                    1);
                slot.setForTicks(HotbarSelection.visible(mc().thePlayer.inventory.currentItem), 1);
              }
            } else if (e == PlayerControlEvent.BEFORE_ACTIONS
                && target[0] != null
                && slot.isWinning()
                && visible(target[0])
                && mc().theWorld.getBlockState(target[0]).getBlock() instanceof BlockChest) {
              GameActions.previousAction(
                  rotation,
                  previous -> {
                    BlockPos pos = target[0];
                    Vec3 point = new Vec3(pos).addVector(.5, .5, .5);
                    Vec3 eyes = mc().thePlayer.getPositionEyes(1),
                        direction = Placement.direction(previous);
                    double reach =
                        Math.min(range.get(), mc().playerController.getBlockReachDistance());
                    MovingObjectPosition hit =
                        mc().theWorld
                            .rayTraceBlocks(
                                eyes,
                                eyes.addVector(
                                    direction.xCoord * reach,
                                    direction.yCoord * reach,
                                    direction.zCoord * reach),
                                false,
                                true,
                                false);
                    if (!walls.get() && (hit == null || !pos.equals(hit.getBlockPos()))) return;
                    EnumFacing side =
                        hit != null && pos.equals(hit.getBlockPos()) ? hit.sideHit : EnumFacing.UP;
                    HookBridge.gameControls()
                        .withHotbar(
                            slot,
                            held -> {
                              if (mc().playerController
                                  .onPlayerRightClick(
                                      mc().thePlayer,
                                      mc().theWorld,
                                      held,
                                      pos,
                                      side,
                                      hit != null && pos.equals(hit.getBlockPos())
                                          ? hit.hitVec
                                          : point)) {
                                mc().thePlayer.swingItem();
                                pending[0] = pos;
                                expires[0] = now() + 1500;
                              }
                            });
                    retry.put(pos, now() + 3000);
                    if (retry.size() > 256) retry.remove(retry.keySet().iterator().next());
                    next[0] = now() + delay.get();
                    target[0] = null;
                  });
            }
          });
    }

    private boolean visible(BlockPos pos) {
      if (walls.get()) return true;
      MovingObjectPosition hit =
          mc().theWorld
              .rayTraceBlocks(
                  mc().thePlayer.getPositionEyes(1),
                  new Vec3(pos).addVector(.5, .5, .5),
                  false,
                  true,
                  false);
      return hit != null && pos.equals(hit.getBlockPos());
    }

    private static void remember(Set<BlockPos> set, BlockPos pos) {
      set.add(pos);
      if (set.size() > 256) set.remove(set.iterator().next());
    }
  }
}
