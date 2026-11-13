package perihelion.integration.minecraft189.module.gameplay;

import net.minecraft.init.Items;
import net.minecraft.item.*;

/** Per-action chest ordering against the live inventory. Negative means leave it alone. */
final class LootPolicy {
  private static final InventoryPolicy.Role[] EQUIPMENT = {
    InventoryPolicy.Role.WEAPON,
    InventoryPolicy.Role.PICKAXE,
    InventoryPolicy.Role.AXE,
    InventoryPolicy.Role.SHOVEL,
    InventoryPolicy.Role.BOW
  };
  private final ItemStack[] inventory;
  private final InventoryAnalysis analysis;
  private final int blockCount, projectileCount, arrowCount;

  LootPolicy(ItemStack[] inventory) {
    this.inventory = inventory;
    analysis = new InventoryAnalysis(inventory);
    int blocks = 0, projectiles = 0, arrows = 0;
    for (ItemStack stack : inventory) {
      if (stack == null) continue;
      Item item = stack.getItem();
      if (item instanceof ItemBlock) blocks += stack.stackSize;
      if (item == Items.snowball || item == Items.egg) projectiles += stack.stackSize;
      if (item == Items.arrow) arrows += stack.stackSize;
    }
    blockCount = blocks;
    projectileCount = projectiles;
    arrowCount = arrows;
  }

  double priority(ItemStack stack, int blocks, int projectiles, int arrows) {
    if (stack == null || stack.stackSize <= 0 || !InventoryPolicy.steal(stack)) return -1;
    Item item = stack.getItem();
    if (item instanceof ItemArmor) {
      double improvement =
          InventoryPolicy.armor(stack) - analysis.bestArmor[((ItemArmor) item).armorType];
      return 200 + Math.max(0, improvement);
    }
    for (InventoryPolicy.Role role : EQUIPMENT) {
      double score = InventoryPolicy.score(stack, role);
      if (score >= 0) return score > analysis.best[role.ordinal()] ? 100 + score : -1;
    }
    if (item instanceof ItemBlock && blockCount >= blocks) return -1;
    if ((item == Items.snowball || item == Items.egg) && projectileCount >= projectiles) return -1;
    if (item == Items.arrow && arrowCount >= arrows) return -1;
    return 1;
  }

  boolean fits(ItemStack stack) {
    // Main inventory only: armor slots cannot receive shift-clicked chest items.
    for (int i = 0; i < Math.min(36, inventory.length); i++) {
      ItemStack held = inventory[i];
      if (held == null
          || ItemStack.areItemsEqual(held, stack)
              && ItemStack.areItemStackTagsEqual(held, stack)
              && held.stackSize < Math.min(64, held.getMaxStackSize())) return true;
    }
    return false;
  }
}
