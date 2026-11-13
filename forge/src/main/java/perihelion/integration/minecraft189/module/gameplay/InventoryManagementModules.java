package perihelion.integration.minecraft189.module.gameplay;

import static perihelion.integration.minecraft189.module.gameplay.InventoryPolicy.Role;

import java.util.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.*;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.item.*;
import net.minecraft.network.play.client.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockPos;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.*;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.event.PlayerControlEvent;

final class InventoryManagementModules {
  private InventoryManagementModules() {}

  static final class Manager extends GameplayModule {
    private final Setting<Boolean> instant,
        invOnly,
        packets,
        still,
        blocks,
        archery,
        arrows,
        food,
        shears;
    private final Setting<Boolean> dropItems,
        dropInferior,
        dropJunk,
        dropUtilityBlocks,
        dropBadPotions,
        protectNamed;
    private final Setting<String> protectedSlots, protectedItems;
    private final InventoryProtection protection = new InventoryProtection();
    private final boolean[] protectedState = new boolean[45];
    private final Setting<Integer> delay, startDelay;
    private GuiInventory inventoryScreen;
    private long inventoryOpenedAt;
    // Yuri's TimerUtils starts at construction and survives ordinary activation toggles.
    private long lastAction = now();
    private final Map<net.minecraft.network.Packet<?>, Boolean> handshakes =
        Collections.synchronizedMap(new WeakHashMap<net.minecraft.network.Packet<?>, Boolean>());

    boolean isHandshake(net.minecraft.network.Packet<?> packet) {
      return handshakes.containsKey(packet);
    }

    private void handshake(net.minecraft.network.Packet<?> packet) {
      handshakes.put(packet, true);
      GameActions.send(packet);
    }

    private final Map<Role, Setting<Boolean>> manage = new EnumMap<>(Role.class);
    private final Map<Role, Setting<Integer>> destinations = new EnumMap<>(Role.class);

    Manager(ClientRuntime r) {
      super(
          r,
          "manager",
          "Manager",
          "Sort the hotbar and discard inferior equipment and unwanted items.",
          ModuleCategory.PLAYER);
      instant = bool("instant", "Instant", false);
      invOnly = bool("inv-only", "Inv Only", true);
      packets = bool("send-inv-packets", "Send Inv Packets", true);
      still = bool("not-moving", "Not Moving", false);
      blocks = bool("swap-blocks", "Swap Blocks", true);
      archery = bool("drop-archery", "Drop Archery", false);
      arrows = bool("move-arrows", "Move Arrows", true);
      food = bool("drop-food", "Drop Food", false);
      shears = bool("drop-shears", "Drop Shears", false);
      dropItems = bool("drop-items", "Allow Item Dropping", false);
      dropInferior =
          bool("drop-inferior", "Drop Inferior Equipment", true).visibleWhen(dropItems::get);
      dropJunk = bool("drop-junk", "Drop Miscellaneous Items", false).visibleWhen(dropItems::get);
      dropUtilityBlocks =
          bool("drop-utility-blocks", "Drop Non-Building Blocks", false)
              .visibleWhen(dropItems::get);
      dropBadPotions =
          bool("drop-bad-potions", "Drop Harmful Potions", false).visibleWhen(dropItems::get);
      protectNamed = bool("protect-named", "Protect Named / Lore Items", true);
      protectedSlots =
          text(
              "protected-slots",
              "Protected Slots",
              "Comma-separated slots: 1-9 hotbar, 10-36 inventory.",
              "",
              160);
      protectedItems =
          text(
              "protected-items",
              "Protected Items",
              "Comma-separated item IDs, e.g. minecraft:tnt,minecraft:compass.",
              "",
              1024);
      startDelay =
          setting(
              Setting.integer(
                  "start-delay",
                  "Start Delay (ms)",
                  "Wait after opening the inventory before sorting; Instant skips this delay.",
                  120,
                  0,
                  500,
                  10,
                  guard));
      delay = integer("delay", "Delay (ms)", 120, 0, 300);
      for (Role role : Role.values()) {
        String id = role.name().toLowerCase(Locale.ROOT);
        manage.put(role, bool("manage-" + id, "Manage " + id, role != Role.PROJECTILE));
        destinations.put(
            role, integer(id + "-slot", id + " Slot", Math.min(9, role.ordinal() + 1), 1, 9));
      }
    }

    @Override
    protected void onEnable(ModuleContext c) {
      c.onCleanup(handshakes::clear);
      inventoryScreen = null;
      c.onCleanup(() -> inventoryScreen = null);
      c.listen(
          PlayerControlEvent.class,
          e -> {
            if (e != PlayerControlEvent.BEFORE_MOTION) return;
            long time = now();
            GuiInventory current =
                mc().currentScreen instanceof GuiInventory
                    ? (GuiInventory) mc().currentScreen
                    : null;
            if (current != inventoryScreen) {
              inventoryScreen = current;
              inventoryOpenedAt = time;
            }
            if (mc().thePlayer.openContainer != mc().thePlayer.inventoryContainer
                || mc().thePlayer.inventory.getItemStack() != null
                || mc().thePlayer.isUsingItem()) return;
            if (invOnly.get() && !(mc().currentScreen instanceof GuiInventory)
                || still.get() && GameActions.moving(mc().thePlayer)) return;
            if (!(mc().currentScreen == null
                || mc().currentScreen instanceof GuiChat
                || mc().currentScreen instanceof GuiInventory
                || mc().currentScreen instanceof GuiIngameMenu)) return;
            if (!instant.get()
                && (time - lastAction < delay.get()
                    || inventoryScreen != null && time - inventoryOpenedAt < startDelay.get()))
              return;
            // Bounded even if a server rejects transactions or two roles share one
            // destination.
            for (int budget = instant.get() ? 45 : 1; budget > 0; budget--) {
              if (!action()) break;
              lastAction = now();
            }
          });
    }

    private ItemStack stack(int slot) {
      return mc().thePlayer.inventoryContainer.getSlot(slot).getStack();
    }

    private void click(int slot, int button, int mode) {
      boolean silent = packets.get() && !(mc().currentScreen instanceof GuiInventory);
      if (silent)
        handshake(
            new C16PacketClientStatus(C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT));
      try {
        mc().playerController.windowClick(0, slot, button, mode, mc().thePlayer);
      } finally {
        if (silent) handshake(new C0DPacketCloseWindow(0));
      }
    }

    private boolean protectedSlot(int slot) {
      return protectedState[slot];
    }

    private boolean action() {
      protection.configure(protectedSlots.get(), protectedItems.get());
      ItemStack[] contents = new ItemStack[45];
      for (int i = 5; i < 45; i++) {
        contents[i] = stack(i);
        protectedState[i] = protection.protects(i, contents[i], protectNamed.get());
      }
      InventoryAnalysis analysis = new InventoryAnalysis(contents);
      Set<Integer> reserved = new HashSet<>();
      for (Role role : Role.values()) {
        if (!manage.get(role).get()
            || role == Role.BLOCK && !blocks.get()
            || role == Role.BOW && archery.get()
            || role == Role.GAPPLE && food.get()) continue;
        int destination = destinations.get(role).get() + 35;
        if (!reserved.add(destination) || protectedSlot(destination)) continue;
        int best = destination;
        double score = analysis.score(best, role);
        for (int i = 9; i < 45; i++) {
          if (reserved.contains(i) && i != destination || protectedSlot(i)) continue;
          double value = analysis.score(i, role);
          if (value > score) {
            best = i;
            score = value;
          }
        }
        if (score >= 0 && best != destination) {
          click(best, destination - 36, 2);
          return true;
        }
      }
      for (int i = 9; i < 45; i++)
        if (dropItems.get() && stack(i) != null && !protectedSlot(i) && discard(i, analysis)) {
          click(i, 1, 4);
          return true;
        }
      if (arrows.get() && !archery.get() && protectedSlots.get().trim().isEmpty())
        for (int i = 36; i < 45; i++)
          if (!protectedSlot(i) && stack(i) != null && stack(i).getItem() == Items.arrow) {
            for (int j = 9; j < 36; j++)
              if (stack(j) == null) {
                click(i, 0, 1);
                return true;
              }
          }
      return false;
    }

    private boolean discard(int slot, InventoryAnalysis analysis) {
      ItemStack s = stack(slot);
      Item item = s.getItem();
      if (InventoryPolicy.protectedItem(s)) return false;
      if (item instanceof ItemArmor)
        return dropInferior.get()
            && analysis.bestArmor[((ItemArmor) item).armorType] > analysis.armor[slot];
      for (Role role : new Role[] {Role.WEAPON, Role.PICKAXE, Role.AXE, Role.SHOVEL, Role.BOW}) {
        double value = analysis.score(slot, role);
        if (value < 0) continue;
        if (!manage.get(role).get()) return false;
        return role == Role.BOW && archery.get()
            || dropInferior.get() && analysis.best[role.ordinal()] > value;
      }
      if (item instanceof ItemPotion)
        return manage.get(Role.POTION).get()
            && dropBadPotions.get()
            && !InventoryPolicy.goodPotion(s);
      if (item == Items.water_bucket
          || InventoryPolicy.score(s, Role.CLUTCH) >= 0
          || InventoryPolicy.score(s, Role.PROJECTILE) >= 0) return false;
      if (item == Items.arrow) return archery.get();
      if (item instanceof ItemFood) return food.get() && !(item instanceof ItemAppleGold);
      if (item instanceof ItemShears) return shears.get();
      if (item instanceof ItemBlock)
        return manage.get(Role.BLOCK).get()
            && dropUtilityBlocks.get()
            && !InventoryPolicy.buildingBlock(s);
      if (!dropJunk.get()) return false;
      if (item == Items.wheat || item == Items.spawn_egg || item == Items.glass_bottle) return true;
      String name = item.getUnlocalizedName().toLowerCase(Locale.ROOT);
      for (String token :
          new String[] {
            "stick",
            "string",
            "cake",
            "mushroom",
            "flint",
            "compass",
            "dyepowder",
            "feather",
            "bucket",
            "chest",
            "snow",
            "fish",
            "enchant",
            "exp",
            "anvil",
            "torch",
            "seeds",
            "leather",
            "reeds",
            "skull",
            "record",
            "piston"
          }) if (name.contains(token)) return true;
      return false;
    }
  }

  static final class Stealer extends GameplayModule {
    private final StealerTiming timing = new StealerTiming(now());
    private final Setting<Boolean> upgrades;
    private final Setting<Integer> blockLimit, projectileLimit, arrowLimit;
    private final Setting<Boolean> instant, trash, close, distance, position, miss, autoDisable;
    private final Setting<Integer> start,
        min,
        max,
        distanceScale,
        positionScale,
        chance,
        recoveryMin,
        recoveryMax;

    Stealer(ClientRuntime r) {
      super(
          r,
          "stealer",
          "Stealer",
          "Transfer useful chest items with configurable timing.",
          ModuleCategory.PLAYER);
      upgrades = bool("smart-looting", "Prioritize Upgrades", true);
      blockLimit = integer("block-limit", "Block Target", 256, 0, 2304).visibleWhen(upgrades::get);
      projectileLimit =
          integer("projectile-limit", "Projectile Target", 64, 0, 2304).visibleWhen(upgrades::get);
      arrowLimit = integer("arrow-limit", "Arrow Target", 64, 0, 2304).visibleWhen(upgrades::get);
      autoDisable = bool("auto-disable", "Auto Disable", false);
      instant = bool("instant", "Instant", false);
      trash = bool("steal-trash-items", "Steal Trash Items", false);
      close = bool("auto-close", "Auto Close", true);
      start = integer("steal-delay", "Steal Delay", 50, 0, 1000);
      min = integer("min-delay", "Min Delay", 5, 0, 1000);
      max = integer("max-delay", "Max Delay", 5, 0, 1000);
      distance = bool("distance-scaling", "Distance Scaling", false);
      position = bool("position-scaling", "Position Scaling", false);
      miss = bool("miss-click", "Miss Click", false);
      distanceScale = integer("distance-multiplier", "Distance Multiplier", 15, 0, 100);
      positionScale = integer("position-multiplier", "Position Multiplier", 5, 0, 50);
      chance = integer("miss-click-chance", "Miss Click Chance", 15, 0, 100);
      recoveryMin = integer("miss-recovery-min", "Miss Recovery Min", 80, 0, 1000);
      recoveryMax = integer("miss-recovery-max", "Miss Recovery Max", 220, 0, 1000);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      if (stopAfterWorldChange(c, autoDisable.get())) return;
      timing.clearCandidate();
      c.onCleanup(timing::clearCandidate);
      ContainerChest[] previous = {null};
      long[] startDue = {0}, retryDue = {0}, missDue = {0};
      boolean[] got = {false}, missed = {false};
      int[] ticks = {0};
      c.listen(
          PlayerControlEvent.class,
          e -> {
            if (e != PlayerControlEvent.BEFORE_INPUT) return;
            if (!(mc().currentScreen instanceof GuiChest)
                || !(mc().thePlayer.openContainer instanceof ContainerChest)
                || mc().thePlayer.ticksExisted <= 60) {
              previous[0] = null;
              timing.clearCandidate();
              return;
            }
            ContainerChest chest = (ContainerChest) mc().thePlayer.openContainer;
            String name =
                chest
                    .getLowerChestInventory()
                    .getDisplayName()
                    .getUnformattedText()
                    .toLowerCase(Locale.ROOT);
            for (String token :
                new String[] {
                  "menu",
                  "selector",
                  "game",
                  "shop",
                  "server",
                  "teleport",
                  "lobby",
                  "hub",
                  "profile",
                  "setting",
                  "play",
                  "vault",
                  "collectible",
                  "bountique",
                  "choisir",
                  "choose",
                  "recipe"
                }) if (name.contains(token)) return;
            if (previous[0] != chest) {
              previous[0] = chest;
              startDue[0] = now() + start.get();
              retryDue[0] = missDue[0] = 0;
              got[0] = missed[0] = false;
              ticks[0] = 0;
              timing.clearCandidate();
            }
            ticks[0]++;
            if (mc().thePlayer.inventory.getItemStack() != null
                || !instant.get() && (now() < startDue[0] || now() < retryDue[0])) return;
            for (int budget = instant.get() ? chest.getLowerChestInventory().getSizeInventory() : 1;
                budget > 0;
                budget--) {
              int found = -1, size = chest.getLowerChestInventory().getSizeInventory();
              ItemStack[] contents = new ItemStack[40];
              System.arraycopy(mc().thePlayer.inventory.mainInventory, 0, contents, 0, 36);
              System.arraycopy(mc().thePlayer.inventory.armorInventory, 0, contents, 36, 4);
              LootPolicy policy = new LootPolicy(contents);
              double priority = -1;
              boolean waitingForSpace = false;
              for (int i = 0; i < size; i++) {
                ItemStack candidate = chest.getLowerChestInventory().getStackInSlot(i);
                if (candidate == null) continue;
                double value =
                    trash.get()
                        ? 1
                        : upgrades.get()
                            ? policy.priority(
                                candidate,
                                blockLimit.get(),
                                projectileLimit.get(),
                                arrowLimit.get())
                            : InventoryPolicy.steal(candidate) ? 1 : -1;
                if (value < 0) continue;
                if (!policy.fits(candidate)) {
                  waitingForSpace = true;
                  continue;
                }
                if (value > priority) {
                  priority = value;
                  found = i;
                }
              }
              if (found < 0 && waitingForSpace) {
                timing.clearCandidate();
                missed[0] = false;
                missDue[0] = 0;
                retryDue[0] = now() + 250;
                return;
              }
              if (found < 0) {
                timing.clearCandidate();
                missed[0] = false;
                missDue[0] = 0;
                if (got[0] && close.get() && (instant.get() || ticks[0] > 3))
                  mc().thePlayer.closeScreen();
                return;
              }
              if (!instant.get()) {
                if (timing.select(found)) {
                  missed[0] = false;
                  missDue[0] = 0;
                }
                if (timing.needsDelay())
                  timing.decide(
                      StealerTiming.dynamicDelay(
                          random(min.get(), max.get()),
                          chestDistance(chest),
                          distance.get(),
                          distanceScale.get(),
                          position.get(),
                          positionScale.get(),
                          found));
                if (miss.get() && !missed[0]) {
                  missed[0] = true;
                  if (Math.random() * 100 < chance.get())
                    for (int i = 0; i < size; i++)
                      if (i != found && chest.getLowerChestInventory().getStackInSlot(i) == null) {
                        mc().playerController.windowClick(chest.windowId, i, 0, 1, mc().thePlayer);
                        missDue[0] = now() + random(recoveryMin.get(), recoveryMax.get());
                        return;
                      }
                }
                if (now() < missDue[0] || !timing.ready(now())) return;
              }
              ItemStack result =
                  mc().playerController.windowClick(chest.windowId, found, 0, 1, mc().thePlayer);
              if (result == null) {
                retryDue[0] = now() + 250;
                return;
              } // Full inventory: don't spin or close over untransferred stacks.
              got[0] = true;
              missed[0] = false;
              missDue[0] = 0;
              if (!instant.get()) timing.transferred(now());
            }
          });
    }

    private double chestDistance(ContainerChest chest) {
      if (!(chest.getLowerChestInventory() instanceof TileEntity)) return 0;
      BlockPos pos = ((TileEntity) chest.getLowerChestInventory()).getPos();
      return mc().thePlayer.getDistance(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
    }

    private long random(int a, int b) {
      return Math.min(a, b) + (long) (Math.random() * (Math.abs(a - b) + 1));
    }
  }
}
