package perihelion.integration.minecraft189.module;

import net.minecraft.block.*;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/**
 * OpenB4's building-item and interaction predicates, kept separate from native inventory policy.
 */
public final class B4BuildingItems {
  private B4BuildingItems() {}

  public static boolean blocks(ItemStack stack) {
    if (stack == null || stack.stackSize < 1 || !(stack.getItem() instanceof ItemBlock))
      return false;
    Block b = ((ItemBlock) stack.getItem()).getBlock();
    return b != Blocks.pumpkin
        && (b == Blocks.glass
            || b.isFullBlock()
                && b != Blocks.gravel
                && b != Blocks.sand
                && b != Blocks.soul_sand
                && b != Blocks.tnt
                && b != Blocks.crafting_table
                && b != Blocks.furnace
                && b != Blocks.dispenser
                && b != Blocks.dropper
                && b != Blocks.noteblock
                && b != Blocks.command_block);
  }

  public static boolean interactable(Block b) {
    return b instanceof BlockContainer
        || b == Blocks.crafting_table
        || b == Blocks.anvil
        || b == Blocks.bed
        || b == Blocks.lever
        || b instanceof BlockTrapDoor
        || b instanceof BlockFence
        || b instanceof BlockFenceGate
        || b instanceof BlockDoor && b != Blocks.iron_door;
  }

  public static float breakSpeed(net.minecraft.client.Minecraft game, Block block) {
    net.minecraft.client.entity.EntityPlayerSP p = game.thePlayer;
    float hardness = block.getBlockHardness(game.theWorld, p.getPosition());
    if (hardness < 0) return 0;
    perihelion.integration.minecraft189.control.MinecraftControls controls =
        perihelion.integration.minecraft189.HookBridge.gameControls();
    int selected = controls == null ? p.inventory.currentItem : controls.serverSlot(p.inventory.currentItem);
    ItemStack stack = p.inventory.getStackInSlot(selected);
    float speed = stack == null ? 1 : stack.getItem().getStrVsBlock(stack, block);
    int efficiency =
        stack == null
            ? 0
            : net.minecraft.enchantment.EnchantmentHelper.getEnchantmentLevel(
                net.minecraft.enchantment.Enchantment.efficiency.effectId, stack);
    if (speed > 1 && efficiency > 0) speed += efficiency * efficiency + 1;
    if (p.isPotionActive(net.minecraft.potion.Potion.digSpeed))
      speed *=
          1
              + (p.getActivePotionEffect(net.minecraft.potion.Potion.digSpeed).getAmplifier() + 1)
                  * .2F;
    if (p.isPotionActive(net.minecraft.potion.Potion.digSlowdown)) {
      int level = p.getActivePotionEffect(net.minecraft.potion.Potion.digSlowdown).getAmplifier();
      speed *= level == 0 ? .3F : level == 1 ? .09F : level == 2 ? .0027F : 8.1E-4F;
    }
    if (p.isInsideOfMaterial(net.minecraft.block.material.Material.water)
        && !net.minecraft.enchantment.EnchantmentHelper.getAquaAffinityModifier(p)) speed /= 5;
    if (!p.onGround) speed /= 5;
    boolean harvest =
        block.getMaterial().isToolNotRequired() || stack != null && stack.canHarvestBlock(block);
    return speed / hardness / (harvest ? 30 : 100);
  }
}
