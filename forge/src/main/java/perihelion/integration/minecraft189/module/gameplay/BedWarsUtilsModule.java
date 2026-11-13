// SPDX-License-Identifier: AGPL-3.0-only
// Adapted from Yuri 1cb86ad94ce1f01aa9b715577163c8ecc47b7def; Copyright (c) 2026 Unlegit Inc.
package perihelion.integration.minecraft189.module.gameplay;

import java.util.*;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemPotion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.IInventory;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ChatComponentText;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StringUtils;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.*;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.event.*;

final class BedWarsUtilsModule extends GameplayModule {

  private final Setting<Boolean> walkToGen = bool("walk-to-gen", "Walk To Gen", true);
  private final Setting<Boolean> notifySweats = bool("notify-sweats", "Notify Sweats", true);
  private final Setting<Boolean> notifyEvents = bool("notify-events", "Notify Events", true);
  private final Setting<Boolean> swords = bool("notify-sword", "Notify Sword", true);
  private final Setting<Boolean> includeStone =
      bool("include-stone-swords", "Include Stone Swords", false).visibleWhen(swords::get);
  private final Setting<Boolean> armor = bool("notify-armor", "Notify Armor", true);
  private final Setting<Boolean> b4Alerts = bool("b4-alerts", "B4 Threat Alerts",
      "OpenB4's enabled-by-default threat alerts, two-second interval and Magic Milk timer.", false);
  private final Setting<Boolean> b4Shop = bool("b4-shop", "B4 Shop Controls",
      "OpenB4 instant shop clicks and mouse-wheel resource deposit/withdraw in BedWars.", false);
  private final B4BedWarsState b4 = new B4BedWarsState();
  private static final int MILK_LINE = 0x504234;
  private static final Set<String> SHOPS = new HashSet<>(Arrays.asList(
      "Quick Buy", "Blocks", "Melee", "Armor", "Tools", "Ranged", "Potions", "Utility", "Rotating Items", "Purchase Enemy Tracker"));

  private final Collection<EntityPlayer> ironSword = new HashSet<>();
  private final Collection<EntityPlayer> diamondSword = new HashSet<>();
  private final Collection<EntityPlayer> stoneSword = new HashSet<>();
  private final Collection<EntityPlayer> diamondArmor = new HashSet<>();
  private final Collection<EntityPlayer> chainArmor = new HashSet<>();
  private final Collection<EntityPlayer> ironArmor = new HashSet<>();
  private final Collection<UUID> warnedSweatPlayers = new HashSet<>();
  private final Set<String> notifiedEvents = new HashSet<>();

  private boolean check = false;
  private boolean gameStarted = false;
  private long gameStartTime = 0;
  private int walkTicks = 0;
  private static final int MAX_WALK_TICKS = 400;

  private static final class TimerEntry {
    final String label;
    final int atSeconds;

    TimerEntry(String label, int atSeconds) {
      this.label = label;
      this.atSeconds = atSeconds;
    }
  }

  private static final List<TimerEntry> BEDWARS_TIMERS =
      Arrays.asList(
          new TimerEntry("Diamond II", 5 * 60 + 22),
          new TimerEntry("Emerald II", 11 * 60 + 22),
          new TimerEntry("Diamond III", 17 * 60 + 22),
          new TimerEntry("Emerald III", 23 * 60 + 22),
          new TimerEntry("Bed gone", 29 * 60 + 22),
          new TimerEntry("Sudden Death", 39 * 60 + 22),
          new TimerEntry("Game End", 49 * 60 + 22));

  private perihelion.core.control.ControlRequest<Boolean> back;

  BedWarsUtilsModule(ClientRuntime r) {
    super(
        r,
        "bedwarsutility",
        "BedWars Utility",
        "Equipment warnings, match events and generator movement.",
        ModuleCategory.MISC);
  }

  @Override
  protected void onEnable(ModuleContext c) {
    back = c.controls().key(perihelion.core.control.InputKey.BACK, 15);
    resetState();
    c.onCleanup(this::resetState);
    c.listen(ClientPacketEvent.class, this::packet);
    c.listen(ItemUseFinishedEvent.class, e -> {
      if (b4Alerts.get() && b4Game() && e.item() != null && e.item().getItem() == Items.milk_bucket)
        b4.milkFinished();
    });
    c.listen(GuiMouseEvent.class, this::shop);
    c.listen(
        PlayerControlEvent.class,
        e -> {
          if (e == PlayerControlEvent.BEFORE_INPUT) pre();
        });
    c.observe(
        walkToGen,
        (setting, before, after) -> {
          if (!after) back.clear();
        });
    c.observe(b4Alerts, (setting, before, after) -> resetB4());
  }

  private void resetState() {
    resetB4();
    if (back != null) back.clear();
    this.diamondSword.clear();
    this.ironSword.clear();
    this.stoneSword.clear();
    this.chainArmor.clear();
    this.ironArmor.clear();
    this.diamondArmor.clear();
    this.warnedSweatPlayers.clear();
    this.notifiedEvents.clear();
    this.check = false;
    this.gameStarted = false;
    this.gameStartTime = 0;
    this.walkTicks = 0;
  }

  private void packet(ClientPacketEvent event) {
    if (mc().thePlayer == null) return;
    if (b4Alerts.get() && b4Game() && event.packet() instanceof S23PacketBlockChange) {
      S23PacketBlockChange p = (S23PacketBlockChange) event.packet();
      if (p.getBlockState().getBlock() == Blocks.obsidian && nextToBed(p.getBlockPosition())) {
        double nearest = 36;
        EntityPlayer placer = null;
        for (EntityPlayer other : mc().theWorld.playerEntities) {
          if (other == mc().thePlayer || other.deathTime != 0) continue;
          double distance = other.getDistanceSq(p.getBlockPosition());
          if (distance < nearest) { nearest = distance; placer = other; }
        }
        if (placer != null && b4.permit(placer.getUniqueID(), B4BedWarsState.Alert.OBSIDIAN_PLACED, now()))
          b4Alert(placer, B4BedWarsState.Alert.OBSIDIAN_PLACED);
      }
    }
    if (!(event.packet() instanceof S02PacketChat)) return;

    S02PacketChat packetChat = (S02PacketChat) event.packet();
    String chatComponent = packetChat.getChatComponent().getUnformattedText();
    String strippedMessage = StringUtils.stripControlCodes(chatComponent);
    if (b4Alerts.get() && B4BedWarsState.gameStart(strippedMessage, packetChat.getType())) resetB4();
    strippedMessage = strippedMessage.trim();

    if (strippedMessage.contains("Protect your bed and destroy the enemy beds.")
        || strippedMessage.contains("Goodluck with your BedWars Game")
        || strippedMessage.contains("The game has started!")) {

      notify("BedWars Utility", EnumChatFormatting.GREEN + "Game has started!");
      mc().thePlayer.playSound("note.pling", 1.0f, 1.0f);
      check = true;
      gameStarted = true;
      gameStartTime = now();
      walkTicks = 0;
      notifiedEvents.clear();
      if (walkToGen.get()) {
        back.set(true);
      }
    }
  }

  private void pre() {
    if (mc().thePlayer == null || mc().theWorld == null) return;
    if (b4Alerts.get()) b4Pre();

    if (notifySweats.get() && mc().getNetHandler() != null) {
      for (NetworkPlayerInfo info : mc().getNetHandler().getPlayerInfoMap()) {
        if (info == null || info.getGameProfile() == null) continue;

        UUID uuid = info.getGameProfile().getId();
        if (uuid == null || warnedSweatPlayers.contains(uuid)) continue;

        String name = info.getGameProfile().getName();
        if (name == null || name.equalsIgnoreCase(mc().thePlayer.getName())) continue;

        String displayName =
            info.getDisplayName() != null ? info.getDisplayName().getFormattedText() : name;
        String unformattedDisplayName = StringUtils.stripControlCodes(displayName);

        boolean isMvpPlusPlus = unformattedDisplayName.contains("[MVP++]");
        boolean isShortName = name.length() <= 3;

        if (isMvpPlusPlus || isShortName) {
          warnedSweatPlayers.add(uuid);
          StringBuilder reason = new StringBuilder();
          if (isMvpPlusPlus) reason.append("MVP++");
          if (isShortName) {
            if (reason.length() > 0) reason.append(" & ");
            reason.append("Short Name (").append(name.length()).append(" chars)");
          }

          notify(
              "BedWars Utility",
              EnumChatFormatting.GOLD
                  + "Sweat Warning: "
                  + EnumChatFormatting.RED
                  + name
                  + EnumChatFormatting.WHITE
                  + " ("
                  + reason.toString()
                  + ")");
        }
      }
    }

    if (gameStarted && notifyEvents.get()) {
      long elapsedSeconds = (now() - gameStartTime) / 1000L;
      for (TimerEntry timer : BEDWARS_TIMERS) {
        long remaining = Math.max(0, timer.atSeconds - elapsedSeconds);
        if (remaining <= 0 && !notifiedEvents.contains(timer.label)) {
          notifiedEvents.add(timer.label);
          mc().thePlayer.playSound("note.pling", 1.0f, 1.0f);
          notify(
              "BedWars Utility",
              EnumChatFormatting.GOLD
                  + "Event: "
                  + EnumChatFormatting.WHITE
                  + timer.label
                  + EnumChatFormatting.GREEN
                  + " has started!");
        }
      }
    }

    if (!check) return;

    if (walkToGen.get() && mc().gameSettings.keyBindBack.isKeyDown()) {
      walkTicks++;
      boolean manualInput =
          mc().gameSettings.keyBindForward.isKeyDown()
              || mc().gameSettings.keyBindLeft.isKeyDown()
              || mc().gameSettings.keyBindRight.isKeyDown()
              || mc().gameSettings.keyBindJump.isKeyDown();

      if (mc().thePlayer.isCollidedHorizontally || walkTicks >= MAX_WALK_TICKS || manualInput) {
        back.clear();
        walkTicks = 0;
      }
    }

    for (final EntityPlayer entity : mc().theWorld.playerEntities) {
      if (entity == mc().thePlayer) continue;

      if (this.swords.get() && entity.getHeldItem() != null) {
        final Item heldItem = entity.getHeldItem().getItem();

        if (heldItem instanceof ItemSword) {
          final String type = ((ItemSword) heldItem).getToolMaterialName().toLowerCase();

          if (type.contains("iron")) {
            if (this.ironSword.add(entity)) {
              notify(
                  "BedWars Utility",
                  "Player "
                      + EnumChatFormatting.RED
                      + entity.getDisplayName().getFormattedText()
                      + EnumChatFormatting.WHITE
                      + " has an "
                      + EnumChatFormatting.AQUA
                      + "Iron Sword");
            }
          } else if (type.contains("emerald") || type.contains("diamond")) {
            if (this.diamondSword.add(entity)) {
              notify(
                  "BedWars Utility",
                  "Player "
                      + EnumChatFormatting.RED
                      + entity.getDisplayName().getFormattedText()
                      + EnumChatFormatting.WHITE
                      + " has a "
                      + EnumChatFormatting.AQUA
                      + "Diamond Sword");
            }
          } else if (type.contains("stone")) {
            if (this.stoneSword.add(entity)) {
              if (this.includeStone.get()) {
                notify(
                    "BedWars Utility",
                    "Player "
                        + EnumChatFormatting.RED
                        + entity.getDisplayName().getFormattedText()
                        + EnumChatFormatting.WHITE
                        + " has a "
                        + EnumChatFormatting.AQUA
                        + "Stone Sword");
              }
            }
          } else if (type.contains("wood")) {
            this.stoneSword.remove(entity);
            this.ironSword.remove(entity);
            this.diamondSword.remove(entity);
          }
        }
      }

      if (this.armor.get()) {
        for (int slot = 0; slot < 4; slot++) {
          ItemStack armorPiece = entity.getCurrentArmor(slot);
          if (armorPiece != null && armorPiece.getItem() instanceof ItemArmor) {
            ItemArmor armorItem = (ItemArmor) armorPiece.getItem();
            ItemArmor.ArmorMaterial material = armorItem.getArmorMaterial();

            if (material == ItemArmor.ArmorMaterial.CHAIN) {
              if (chainArmor.add(entity)) {
                notify(
                    "BedWars Utility",
                    "Player "
                        + EnumChatFormatting.RED
                        + entity.getDisplayName().getFormattedText()
                        + EnumChatFormatting.WHITE
                        + " has "
                        + EnumChatFormatting.LIGHT_PURPLE
                        + "Chain Armor");
              }
              break;
            } else if (material == ItemArmor.ArmorMaterial.IRON) {
              if (this.ironArmor.add(entity)) {
                notify(
                    "BedWars Utility",
                    "Player "
                        + EnumChatFormatting.RED
                        + entity.getDisplayName().getFormattedText()
                        + EnumChatFormatting.WHITE
                        + " has "
                        + EnumChatFormatting.LIGHT_PURPLE
                        + "Iron Armor");
              }
              break;
            } else if (material == ItemArmor.ArmorMaterial.DIAMOND) {
              if (this.diamondArmor.add(entity) && !b4DiamondCovered(entity)) {
                notify(
                    "BedWars Utility",
                    "Player "
                        + EnumChatFormatting.RED
                        + entity.getDisplayName().getFormattedText()
                        + EnumChatFormatting.WHITE
                        + " has "
                        + EnumChatFormatting.LIGHT_PURPLE
                        + "Diamond Armor");
              }
              break;
            } else if (material == ItemArmor.ArmorMaterial.LEATHER) {
              this.diamondArmor.remove(entity);
              this.ironArmor.remove(entity);
              this.chainArmor.remove(entity);
            }
          }
        }
      }
    }
  }

  private void notify(String module, String text) {
    mc().thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText("[Perihelion] " + text));
  }

  private void resetB4() {
    b4.reset();
    if (mc().ingameGUI != null) mc().ingameGUI.getChatGUI().deleteChatLine(MILK_LINE);
  }

  private boolean b4Game() {
    return mc().theWorld != null && B4GameContext.bedwars(mc());
  }

  private void b4Pre() {
    if (!b4Game()) {
      // A refreshed/temporarily absent scoreboard must not rearm purchase flags or lose milk.
      if (mc().ingameGUI != null) mc().ingameGUI.getChatGUI().deleteChatLine(MILK_LINE);
      return;
    }
    if (mc().thePlayer.ticksExisted % 3 == 0) {
      Set<Integer> seen = new HashSet<>();
      for (EntityPlayer p : mc().theWorld.playerEntities) {
        if (p == mc().thePlayer || p.deathTime != 0 || mc().getNetHandler() == null
            || mc().getNetHandler().getPlayerInfo(p.getUniqueID()) == null || !seen.add(p.getEntityId())) continue;
        for (B4BedWarsState.Alert alert : B4BedWarsState.Alert.values()) {
          // These two donor entries have no transition flag; placed obsidian is packet-driven.
          if (alert == B4BedWarsState.Alert.DIA_PICKAXE || alert == B4BedWarsState.Alert.OBSIDIAN_PLACED) continue;
          if (b4.transition(p.getEntityId(), p.getUniqueID(), alert, flag(p, alert), now())) b4Alert(p, alert);
        }
      }
    }
    int ticks = b4.milkTicks();
    if (ticks != 0) {
      String value = ticks > 0 ? String.format(Locale.ROOT, "\u00a7a%.1fs", ticks / 20.) : "\u00a7cFinished";
      mc().ingameGUI.getChatGUI().printChatMessageWithOptionalDeletion(
          new ChatComponentText("[Perihelion] Magic Milk: " + value), MILK_LINE);
      b4.tickMilk();
      if (b4.milkTicks() == 0) mc().ingameGUI.getChatGUI().deleteChatLine(MILK_LINE);
    }
  }

  private boolean teammate(EntityPlayer p) {
    return B4GameContext.sameTeam(mc(), p);
  }

  private boolean b4DiamondCovered(EntityPlayer p) {
    ItemStack legs = p.inventory.armorInventory[1];
    return b4Alerts.get() && b4Game() && p.deathTime == 0 && legs != null
        && legs.getItem() == Items.diamond_leggings && mc().getNetHandler() != null
        && mc().getNetHandler().getPlayerInfo(p.getUniqueID()) != null;
  }

  private boolean flag(EntityPlayer p, B4BedWarsState.Alert alert) {
    ItemStack held = p.getHeldItem(), legs = p.inventory.armorInventory[1];
    Item item = held == null ? null : held.getItem();
    switch (alert) {
      case BLOCK_ZAPPER: return item == Items.prismarine_shard;
      case BOW: return item == Items.bow && !held.isItemEnchanted();
      case BOW_POWER: return item == Items.bow && held.isItemEnchanted()
          && held.getEnchantmentTagList() != null && held.getEnchantmentTagList().tagCount() == 1;
      case BOW_PUNCH: return item == Items.bow && held.isItemEnchanted()
          && held.getEnchantmentTagList() != null && held.getEnchantmentTagList().tagCount() == 2;
      case CHARLIE_UNICORN: return item == Items.golden_horse_armor;
      case DIA_ARMOR: return legs != null && legs.getItem() == Items.diamond_leggings;
      case DREAM_DEFENDER: return item == Items.spawn_egg && held.getDisplayName().contains("Dream Defender");
      case ENCHANT_PROT: return !teammate(p) && legs != null && legs.isItemEnchanted();
      case ENCHANT_SHARPNESS: return !teammate(p) && item instanceof ItemSword && held.isItemEnchanted();
      case ENDERPEARL: return item == Items.ender_pearl;
      case FIREBALL: return item == Items.fire_charge;
      case ICE_BRIDGE: return item instanceof ItemBlock && ((ItemBlock) item).getBlock() == Blocks.ice;
      case INVISIBLE: return p.isInvisible() && p.ticksExisted >= 100
          && mc().thePlayer.inventory.armorInventory[1] != null && !p.getName().equals(mc().thePlayer.getName())
          && p.getDisplayName().getUnformattedText().contains(" ");
      case KB_STICK: return item == Items.stick && held.isItemEnchanted();
      case MAGIC_MILK: return item == Items.milk_bucket;
      case OBSIDIAN_HELD: return item instanceof ItemBlock && ((ItemBlock) item).getBlock() == Blocks.obsidian;
      case POTION_INVIS: case POTION_JUMP: case POTION_SPEED:
        if (item instanceof ItemPotion) {
          List<PotionEffect> effects = ((ItemPotion) item).getEffects(held);
          if (effects != null) for (PotionEffect effect : effects) {
            int id = effect.getPotionID();
            if (id == 14) return alert == B4BedWarsState.Alert.POTION_INVIS;
            if (id == 8) return alert == B4BedWarsState.Alert.POTION_JUMP;
            if (id == 1) return alert == B4BedWarsState.Alert.POTION_SPEED;
          }
        }
        return false;
      case SLEEPING_DUST: return item == Items.snowball && held.getDisplayName().contains("Sleeping Dust");
      case WATER_BUCKET: return item == Items.water_bucket;
      default: return false;
    }
  }

  private void b4Alert(EntityPlayer p, B4BedWarsState.Alert alert) {
    notify("BedWars Utility", p.getDisplayName().getUnformattedText() + " " + alert.text
        + String.format(Locale.ROOT, " (%.0fm)", p.getDistanceToEntity(mc().thePlayer)));
  }

  private boolean nextToBed(BlockPos pos) {
    for (EnumFacing face : EnumFacing.values())
      if (mc().theWorld.getBlockState(pos.offset(face)).getBlock() == Blocks.bed) return true;
    return false;
  }

  private void shop(GuiMouseEvent e) {
    if (!b4Shop.get() || !b4Game() || !(e.screen() instanceof GuiChest)
        || !(mc().thePlayer.openContainer instanceof ContainerChest)) return;
    ContainerChest container = (ContainerChest) mc().thePlayer.openContainer;
    IInventory chest = container.getLowerChestInventory();
    int size = chest.getSizeInventory();
    if (e.button() == 0 && e.down() && !GuiScreen.isShiftKeyDown() && SHOPS.contains(chest.getName())) {
      int left = (e.screen().width - 176) / 2, top = (e.screen().height - (114 + size / 9 * 18)) / 2;
      for (Slot slot : container.inventorySlots) {
        int x = e.x() - left, y = e.y() - top;
        if (x < slot.xDisplayPosition - 1 || x >= slot.xDisplayPosition + 17
            || y < slot.yDisplayPosition - 1 || y >= slot.yDisplayPosition + 17) continue;
        mc().playerController.windowClick(container.windowId, slot.slotNumber, 2, 3, mc().thePlayer);
        e.cancel();
        break;
      }
    }
    if (e.wheel() == 0 || size != 27) return;
    int start = e.wheel() > 0 ? size : 0, end = e.wheel() > 0 ? size + 36 : size;
    for (int i = start; i < end; i++) {
      ItemStack stack = container.getSlot(i).getStack();
      if (stack == null || stack.getItem() != Items.iron_ingot && stack.getItem() != Items.gold_ingot
          && stack.getItem() != Items.diamond && stack.getItem() != Items.emerald) continue;
      mc().playerController.windowClick(container.windowId, i, 0, 1, mc().thePlayer);
      break;
    }
  }
}
