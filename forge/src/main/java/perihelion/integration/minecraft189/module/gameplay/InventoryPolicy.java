package perihelion.integration.minecraft189.module.gameplay;

import java.util.*;
import net.minecraft.block.*;
import net.minecraft.enchantment.*;
import net.minecraft.init.*;
import net.minecraft.item.*;
import net.minecraft.potion.PotionEffect;

/** Shared item classification, independent of inventory mutation and module activation. */
final class InventoryPolicy {
  enum Role {
    WEAPON,
    PICKAXE,
    AXE,
    SHOVEL,
    BOW,
    BLOCK,
    GAPPLE,
    POTION,
    CLUTCH,
    PROJECTILE
  }

  private InventoryPolicy() {}

  static int breakerTool(net.minecraft.entity.player.InventoryPlayer inventory, Block block) {
    int best = -1;
    float bestSpeed = 1;
    for (int i = 0; i < 9; i++) {
      ItemStack stack = inventory.getStackInSlot(i);
      if (stack == null) continue;
      float value = stack.getStrVsBlock(block);
      if (value > 1) {
        int efficiency =
            EnchantmentHelper.getEnchantmentLevel(Enchantment.efficiency.effectId, stack);
        if (efficiency > 0) value += efficiency * efficiency + 1;
      }
      if (stack.getItem() instanceof ItemShears
          && (block instanceof BlockBed
              || block.getMaterial() == net.minecraft.block.material.Material.cloth))
        value = Math.max(value, 5);
      if (value > bestSpeed) {
        bestSpeed = value;
        best = i;
      }
    }
    if (best < 0 && block instanceof BlockBed)
      for (int i = 0; i < 9; i++) {
        ItemStack stack = inventory.getStackInSlot(i);
        if (stack != null
            && (stack.getItem() instanceof ItemShears
                || stack.getItem() instanceof ItemAxe
                || stack.getItem() instanceof ItemPickaxe)) return i;
      }
    return best;
  }

  static boolean buildingBlock(ItemStack stack) {
    if (stack == null || stack.stackSize < 1 || !(stack.getItem() instanceof ItemBlock))
      return false;
    Block block = ((ItemBlock) stack.getItem()).getBlock();
    return (block.isFullBlock()
            || block instanceof BlockGlass
            || block instanceof BlockStainedGlass)
        && !(block instanceof BlockContainer
            || block instanceof BlockFalling
            || block instanceof BlockTNT
            || block instanceof BlockSlime)
        && block != Blocks.crafting_table
        && block != Blocks.noteblock;
  }

  static boolean protectedItem(ItemStack stack) {
    String name = stack.getDisplayName().toLowerCase(Locale.ROOT);
    for (String token : PROTECTED_NAMES) if (name.contains(token)) return true;
    return false;
  }

  private static final String[] PROTECTED_NAMES = {
    "selector",
    "tracking compass",
    "(right click)",
    "tienda ",
    "perfil",
    "salir",
    "shop",
    "collectibles",
    "game",
    "profil",
    "lobby",
    "show all",
    "hub",
    "friends only",
    "cofre",
    "(click",
    "teleport",
    "play",
    "exit",
    "hide all",
    "jeux",
    "gadget",
    " (activ",
    "emote",
    "amis",
    "bountique",
    "choisir",
    "choose ",
    "recipe book",
    "click derecho",
    "todos",
    "teletransportador",
    "configuraci",
    "jugar de nuevo"
  };

  static boolean goodPotion(ItemStack stack) {
    if (stack == null || !(stack.getItem() instanceof ItemPotion)) return false;
    List<PotionEffect> effects = ((ItemPotion) stack.getItem()).getEffects(stack);
    if (effects == null || effects.isEmpty()) return false;
    for (PotionEffect effect : effects) {
      int id = effect.getPotionID();
      if (id < 0
          || id >= net.minecraft.potion.Potion.potionTypes.length
          || net.minecraft.potion.Potion.potionTypes[id] == null
          || net.minecraft.potion.Potion.potionTypes[id].isBadEffect()) return false;
    }
    return true;
  }

  static double enchant(ItemStack s, Enchantment e) {
    return EnchantmentHelper.getEnchantmentLevel(e.effectId, s);
  }

  static double score(ItemStack s, Role role) {
    if (s == null || protectedItem(s)) return -1;
    return scoreUnprotected(s, role);
  }

  static double scoreUnprotected(ItemStack s, Role role) {
    Item i = s.getItem();
    switch (role) {
      case WEAPON:
        return i instanceof ItemSword
            ? ((ItemSword) i).getDamageVsEntity()
                + enchant(s, Enchantment.sharpness) * 1.25
                + enchant(s, Enchantment.fireAspect) * 0.1
            : -1;
      case PICKAXE:
      case AXE:
      case SHOVEL:
        if (!(role == Role.PICKAXE && i instanceof ItemPickaxe
            || role == Role.AXE && i instanceof ItemAxe
            || role == Role.SHOVEL && i instanceof ItemSpade)) return -1;
        return s.getStrVsBlock(
                role == Role.PICKAXE ? Blocks.stone : role == Role.AXE ? Blocks.log : Blocks.dirt)
            - (i.getUnlocalizedName().contains("Gold") || i.getUnlocalizedName().contains("gold")
                ? 5
                : 0)
            + enchant(s, Enchantment.efficiency) * 0.0075
            + enchant(s, Enchantment.unbreaking) * 0.01;
      case BOW:
        return i instanceof ItemBow
            ? enchant(s, Enchantment.power)
                + enchant(s, Enchantment.flame) * 0.5
                + enchant(s, Enchantment.unbreaking) * 0.1
            : -1;
      case BLOCK:
        return buildingBlock(s) ? s.stackSize : -1;
      case GAPPLE:
        return i instanceof ItemAppleGold ? s.stackSize : -1;
      case POTION:
        return goodPotion(s) ? 1 : -1;
      case CLUTCH:
        return i == Items.water_bucket || i == Item.getItemFromBlock(Blocks.web) ? 1 : -1;
      case PROJECTILE:
        return i == Items.snowball || i == Items.egg ? 2 : i == Items.fishing_rod ? 1 : -1;
      default:
        return -1;
    }
  }

  static double armor(ItemStack s) {
    if (s == null || !(s.getItem() instanceof ItemArmor)) return -1;
    double base = ((ItemArmor) s.getItem()).damageReduceAmount;
    return base
        + (100 - base) * enchant(s, Enchantment.protection) * 0.0075
        + enchant(s, Enchantment.blastProtection) * 0.01
        + enchant(s, Enchantment.fireProtection) * 0.01
        + enchant(s, Enchantment.thorns) * 0.01
        + enchant(s, Enchantment.unbreaking) * 0.01
        + enchant(s, Enchantment.featherFalling) * 0.01;
  }

  static boolean steal(ItemStack s) {
    Item i = s.getItem();
    if (i instanceof ItemPotion) return goodPotion(s);
    if (i instanceof ItemBlock) {
      Block b = ((ItemBlock) i).getBlock();
      return !(b instanceof BlockTNT || b instanceof BlockSlime || b instanceof BlockFalling);
    }
    if (i instanceof ItemSword
        || i instanceof ItemArmor
        || i instanceof ItemTool
        || i instanceof ItemFood && i != Items.spider_eye
        || i instanceof ItemSkull
        || s.getDisplayName().contains("§")
        || protectedItem(s)) return true;
    return i == Items.ender_pearl
        || i == Items.iron_ingot
        || i == Items.snowball
        || i == Items.gold_ingot
        || i == Items.redstone
        || i == Items.diamond
        || i == Items.emerald
        || i == Items.quartz
        || i == Items.bow
        || i == Items.arrow
        || i == Items.fishing_rod
        || i == Items.egg
        || i == Items.water_bucket
        || i == Items.lava_bucket;
  }
}
