package perihelion.integration.minecraft189.module.gameplay;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.potion.Potion;
import net.minecraft.util.BlockPos;

/** B4-style tool/route scoring using actual-position hardness and vanilla slowdowns. */
final class BreakerMining {
  private BreakerMining() {}

  static int tool(Minecraft game, BlockPos pos, boolean automatic) {
    return tool(game, pos, automatic, false);
  }

  static int tool(Minecraft game, BlockPos pos, boolean automatic, boolean ignoreSlow) {
    int best = game.thePlayer.inventory.currentItem;
    if (!automatic) return best;
    float strength = strength(game, pos, game.thePlayer.inventory.getStackInSlot(best), ignoreSlow);
    for (int i = 0; i < 9; i++) {
      float next = strength(game, pos, game.thePlayer.inventory.getStackInSlot(i), ignoreSlow);
      if (next > strength) {
        best = i;
        strength = next;
      }
    }
    return best;
  }

  static float strength(Minecraft game, BlockPos pos, ItemStack stack) {
    return strength(game, pos, stack, false);
  }

  static float strength(Minecraft game, BlockPos pos, ItemStack stack, boolean ignoreSlow) {
    Block block = game.theWorld.getBlockState(pos).getBlock();
    float hardness = block.getBlockHardness(game.theWorld, pos);
    if (hardness < 0 || block.getMaterial().isLiquid()) return 0;
    if (game.playerController.isInCreativeMode())
      return stack != null && stack.getItem() instanceof ItemSword ? 0 : 1;
    if (hardness == 0) return 1;
    float speed = stack == null ? 1 : stack.getStrVsBlock(block);
    int efficiency =
        stack == null
            ? 0
            : EnchantmentHelper.getEnchantmentLevel(Enchantment.efficiency.effectId, stack);
    if (speed > 1 && efficiency > 0) speed += efficiency * efficiency + 1;
    if (game.thePlayer.isPotionActive(Potion.digSpeed))
      speed *= 1 + (game.thePlayer.getActivePotionEffect(Potion.digSpeed).getAmplifier() + 1) * .2f;
    if (!ignoreSlow && game.thePlayer.isPotionActive(Potion.digSlowdown)) {
      int level = game.thePlayer.getActivePotionEffect(Potion.digSlowdown).getAmplifier();
      speed *= level == 0 ? .3f : level == 1 ? .09f : level == 2 ? .0027f : .00081f;
    }
    if (!ignoreSlow
        && game.thePlayer.isInsideOfMaterial(Material.water)
        && !EnchantmentHelper.getAquaAffinityModifier(game.thePlayer)) speed /= 5;
    if (!ignoreSlow && !game.thePlayer.onGround) speed /= 5;
    boolean harvest =
        block.getMaterial().isToolNotRequired() || stack != null && stack.canHarvestBlock(block);
    return speed / hardness / (harvest ? 30 : 100);
  }
}
