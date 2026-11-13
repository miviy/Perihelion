package perihelion.integration.minecraft189.module;

import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemEgg;
import net.minecraft.item.ItemSnowball;
import net.minecraft.item.ItemStack;
import perihelion.core.control.ControlRequest;
import perihelion.core.control.HotbarSelection;
import perihelion.core.module.ClientModule;
import perihelion.core.module.ModuleCategory;
import perihelion.core.module.ModuleContext;
import perihelion.core.module.ModuleInfo;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.control.ItemUseDelayAccess;
import perihelion.integration.minecraft189.event.PlayerControlEvent;

public final class FastPlaceModule extends ClientModule {
  public enum BlockRefill {
    OFF,
    SAME_TYPE,
    ANY_BLOCK
  }

  private final Setting<Integer> delay;
  private final Setting<Boolean> blocks, projectiles, validate, skipObsidian, skipInteractable;
  private final Setting<BlockRefill> refill;

  public FastPlaceModule(Runnable guard) {
    super(
        new ModuleInfo(
            "fastplace",
            "FastPlace",
            "Reduce block, egg and snowball use delay.",
            ModuleCategory.WORLD,
            true));
    delay =
        setting(
            Setting.integer(
                "delay",
                "Delay",
                "Maximum remaining right-click cooldown in ticks.",
                1,
                0,
                3,
                guard));
    blocks =
        setting(
            Setting.bool(
                "allow-blocks", "Allow Blocks", "Reduce delay for block items.", true, guard));
    projectiles =
        setting(
            Setting.bool(
                "allow-projectiles",
                "Allow Projectiles",
                "Reduce delay for eggs and snowballs.",
                true,
                guard));
    validate =
        setting(
            Setting.bool(
                "validate-placement",
                "Validate Placement",
                "Only accelerate blocks with a reachable, valid placement face.",
                false,
                guard));
    skipObsidian =
        setting(
            Setting.bool(
                "skip-obsidian",
                "Skip Obsidian",
                "Keep normal timing for obsidian.",
                false,
                guard));
    skipInteractable =
        setting(
            Setting.bool(
                "skip-interactable",
                "Skip Interactable",
                "Keep normal timing for containers, doors and other interactive block items.",
                false,
                guard));
    refill =
        setting(
            Setting.enumeration(
                "block-refill",
                "Block Refill",
                "Select a larger hotbar stack when the current building stack reaches one block.",
                BlockRefill.OFF,
                BlockRefill.class,
                guard));
  }

  @Override
  protected void onEnable(ModuleContext context) {
    ControlRequest<HotbarSelection> selection = context.controls().hotbar(15);
    int[] previousSlot = {-1};
    boolean[] previousBlocks = {false};
    context.observe(
        refill,
        (s, b, a) -> {
          selection.clear();
          previousSlot[0] = -1;
          previousBlocks[0] = false;
        });
    context.listen(
        PlayerControlEvent.class,
        event -> {
          if (event != PlayerControlEvent.BEFORE_MOTION) return;
          Minecraft game = Minecraft.getMinecraft();
          selection.clear();
          if (game.thePlayer == null
              || game.theWorld == null
              || game.currentScreen != null
              || !game.inGameHasFocus
              || game.thePlayer.isDead) {
            previousSlot[0] = -1;
            previousBlocks[0] = false;
            return;
          }
          ItemStack held = game.thePlayer.getHeldItem();
          int current = game.thePlayer.inventory.currentItem;
          boolean building = B4BuildingItems.blocks(held);
          if (refill.get() != BlockRefill.OFF
              && blocks.get()
              && previousBlocks[0]
              && current == previousSlot[0]
              && (!building || held.stackSize <= 1)) {
            int[] counts = new int[9];
            boolean[] candidates = new boolean[9];
            for (int i = 0; i < 9; i++) {
              ItemStack stack = game.thePlayer.inventory.getStackInSlot(i);
              candidates[i] =
                  B4BuildingItems.blocks(stack)
                      && (!building
                          || refill.get() != BlockRefill.SAME_TYPE
                          || stack.getItem() == held.getItem());
              counts[i] = stack == null ? 0 : stack.stackSize;
            }
            int best =
                B4BuildingPolicy.blockStack(
                    current, building ? held.stackSize : -1, counts, candidates);
            if (best >= 0) selection.setForTicks(HotbarSelection.persistent(best), 1);
          }
          previousSlot[0] = current;
          previousBlocks[0] = building;
          if (held == null) return;
          Item item = held.getItem();
          if ((blocks.get() && item instanceof ItemBlock && eligible(game, held))
              || (projectiles.get() && (item instanceof ItemEgg || item instanceof ItemSnowball))) {
            ItemUseDelayAccess access = (ItemUseDelayAccess) game;
            access.perihelion$itemUseDelay(Math.min(access.perihelion$itemUseDelay(), delay.get()));
          }
        });
  }

  private boolean eligible(Minecraft game, ItemStack held) {
    ItemBlock item = (ItemBlock) held.getItem();
    net.minecraft.block.Block block = item.getBlock();
    if (skipObsidian.get() && block == net.minecraft.init.Blocks.obsidian) return false;
    if (skipInteractable.get()
        && (block instanceof net.minecraft.block.BlockContainer
            || block instanceof net.minecraft.block.BlockDoor
            || block instanceof net.minecraft.block.BlockTrapDoor
            || block instanceof net.minecraft.block.BlockFenceGate
            || block instanceof net.minecraft.block.BlockButton
            || block instanceof net.minecraft.block.BlockLever
            || block instanceof net.minecraft.block.BlockWorkbench
            || block instanceof net.minecraft.block.BlockAnvil
            || block instanceof net.minecraft.block.BlockBed)) return false;
    if (!validate.get()) return true;
    net.minecraft.util.MovingObjectPosition hit =
        game.thePlayer.rayTrace(game.playerController.getBlockReachDistance(), 1);
    return hit != null
        && hit.typeOfHit == net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK
        && item.canPlaceBlockOnSide(
            game.theWorld, hit.getBlockPos(), hit.sideHit, game.thePlayer, held);
  }
}
