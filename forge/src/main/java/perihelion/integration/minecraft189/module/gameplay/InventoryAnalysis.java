package perihelion.integration.minecraft189.module.gameplay;

import java.util.Arrays;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;

/** Scores a single inventory state once. Rebuild after every successful inventory mutation. */
final class InventoryAnalysis {
  private static final InventoryPolicy.Role[] ROLES = InventoryPolicy.Role.values();
  final double[][] scores;
  final double[] best = new double[ROLES.length];
  final double[] armor;
  final double[] bestArmor = {-1, -1, -1, -1};

  InventoryAnalysis(ItemStack[] stacks) {
    scores = new double[stacks.length][best.length];
    armor = new double[stacks.length];
    Arrays.fill(best, -1);
    Arrays.fill(armor, -1);
    for (int i = 0; i < stacks.length; i++) {
      Arrays.fill(scores[i], -1);
      ItemStack stack = stacks[i];
      if (stack == null || stack.stackSize <= 0) continue;
      if (stack.getItem() instanceof ItemArmor) {
        armor[i] = InventoryPolicy.armor(stack);
        int type = ((ItemArmor) stack.getItem()).armorType;
        bestArmor[type] = Math.max(bestArmor[type], armor[i]);
      }
      if (InventoryPolicy.protectedItem(stack)) continue;
      for (InventoryPolicy.Role role : ROLES) {
        double value = InventoryPolicy.scoreUnprotected(stack, role);
        scores[i][role.ordinal()] = value;
        best[role.ordinal()] = Math.max(best[role.ordinal()], value);
      }
    }
  }

  double score(int slot, InventoryPolicy.Role role) {
    return scores[slot][role.ordinal()];
  }
}
