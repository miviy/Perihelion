package perihelion.integration.minecraft189.module.gameplay;

import java.util.EnumMap;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import perihelion.core.control.ControlRequest;
import perihelion.core.control.InputKey;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleCategory;
import perihelion.core.module.ModuleContext;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.control.BlockBreakAccess;
import perihelion.integration.minecraft189.event.PlayerControlEvent;

final class InventoryModules {
  private InventoryModules() {}

  static final class FastBreak extends GameplayModule {
    private final Setting<Integer> speed, delay;
    private final Setting<perihelion.integration.minecraft189.module.B4BuildingPolicy.Mining> mode;
    private final Setting<Double> multiplier;

    FastBreak(ClientRuntime r) {
      super(
          r,
          "fastbreak",
          "Fast Break",
          "Shorten block hit delay and seed mining progress.",
          ModuleCategory.WORLD);
      speed = integer("speed", "Speed", 15, 1, 100);
      delay = integer("delay", "Delay", 0, 0, 4);
      mode =
          mode(
              "progress-mode",
              "Progress Mode",
              perihelion.integration.minecraft189.module.B4BuildingPolicy.Mining.NATIVE,
              perihelion.integration.minecraft189.module.B4BuildingPolicy.Mining.class);
      speed.visibleWhen(
          () ->
              mode.get()
                  == perihelion.integration.minecraft189.module.B4BuildingPolicy.Mining.NATIVE);
      multiplier =
          number("break-multiplier", "Break Multiplier", 1, 1, 2, .02)
              .visibleWhen(
                  () ->
                      mode.get()
                          != perihelion.integration.minecraft189.module.B4BuildingPolicy.Mining
                              .NATIVE);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      float[] previous = {0};
      c.observe(mode, (s, b, a) -> previous[0] = 0);
      c.listen(
          PlayerControlEvent.class,
          e -> {
            if (e != PlayerControlEvent.BEFORE_INPUT) return;
            if (mc().playerController.isInCreativeMode()) return;
            boolean nativeMode =
                mode.get()
                    == perihelion.integration.minecraft189.module.B4BuildingPolicy.Mining.NATIVE;
            boolean blockHit =
                mc().objectMouseOver != null
                    && mc().objectMouseOver.typeOfHit
                        == MovingObjectPosition.MovingObjectType.BLOCK;
            if (nativeMode && !blockHit) return;
            BlockBreakAccess access = (BlockBreakAccess) mc().playerController;
            access.perihelion$hitDelay(
                Math.min(access.perihelion$hitDelay(), delay.get() + (nativeMode ? 1 : 0)));
            if (nativeMode && access.perihelion$hitting())
              access.perihelion$damage(
                  Math.max(access.perihelion$damage(), 0.3F * (speed.get().floatValue() / 100.0F)));
            else if (!nativeMode) {
              if (multiplier.get() <= 1) return;
              if (!mc().gameSettings.keyBindAttack.isKeyDown()) {
                previous[0] = 0;
                return;
              }
              float damage = access.perihelion$damage();
              if (mode.get()
                      == perihelion.integration.minecraft189.module.B4BuildingPolicy.Mining
                          .INCREMENT
                  && !blockHit) {
                previous[0] = damage;
                return;
              }
              float hardness =
                  blockHit
                      ? perihelion.integration.minecraft189.module.B4BuildingItems.breakSpeed(
                          mc(),
                          mc().theWorld
                              .getBlockState(mc().objectMouseOver.getBlockPos())
                              .getBlock())
                      : 0;
              access.perihelion$damage(
                  perihelion.integration.minecraft189.module.B4BuildingPolicy.mining(
                      mode.get(), damage, previous[0], hardness, multiplier.get()));
              // B4 compares the next tick against the unmodified damage sampled this tick.
              previous[0] = damage;
            }
          });
    }
  }

  static final class InvMove extends GameplayModule {
    InvMove(ClientRuntime r) {
      super(
          r,
          "invmove",
          "Inv Move",
          "Physical movement keys in container screens.",
          ModuleCategory.MOVEMENT);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      InputKey[] keys = {
        InputKey.FORWARD,
        InputKey.BACK,
        InputKey.LEFT,
        InputKey.RIGHT,
        InputKey.JUMP,
        InputKey.SPRINT
      };
      EnumMap<InputKey, ControlRequest<Boolean>> claims = new EnumMap<>(InputKey.class);
      for (InputKey key : keys) claims.put(key, c.controls().key(key, 10));
      c.listen(
          PlayerControlEvent.class,
          e -> {
            if (e != PlayerControlEvent.BEFORE_INPUT) return;
            boolean eligible =
                mc().currentScreen instanceof GuiContainer
                    && !(mc().currentScreen instanceof GuiContainerCreative)
                    && mc().thePlayer.isEntityAlive();
            KeyBinding[] bindings = {
              mc().gameSettings.keyBindForward,
              mc().gameSettings.keyBindBack,
              mc().gameSettings.keyBindLeft,
              mc().gameSettings.keyBindRight,
              mc().gameSettings.keyBindJump,
              mc().gameSettings.keyBindSprint
            };
            for (int i = 0; i < keys.length; i++) {
              if (eligible) claims.get(keys[i]).setForTicks(GameSettings.isKeyDown(bindings[i]), 1);
              else claims.get(keys[i]).clear();
            }
          });
    }
  }

  static final class Refill extends GameplayModule {
    private final Setting<Boolean> close;
    // Yuri keeps the click delay across ordinary activation toggles.
    private long lastClicked;

    Refill(ClientRuntime r) {
      super(
          r,
          "refill",
          "Refill",
          "Move reserve potions into empty hotbar slots.",
          ModuleCategory.PLAYER);
      close = bool("auto-close", "Auto Close", true);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      c.listen(
          PlayerControlEvent.class,
          e -> {
            if (e != PlayerControlEvent.BEFORE_INPUT) return;
            if (!(mc().currentScreen instanceof GuiInventory)
                || mc().thePlayer.inventory.getItemStack() != null) return;
            int empty = -1;
            for (int i = 0; i < 9; i++)
              if (mc().thePlayer.inventory.getStackInSlot(i) == null) {
                empty = i;
                break;
              }
            if (empty < 0) {
              if (close.get()) mc().thePlayer.closeScreen();
              return;
            }
            // Closing a full hotbar is independent of the next shift-click deadline.
            if (now() - lastClicked <= 50) return;
            for (int i = 9; i < 36; i++) {
              ItemStack stack = mc().thePlayer.inventory.getStackInSlot(i);
              if (stack != null
                  && stack.getItem() instanceof ItemPotion
                  && ItemPotion.isSplash(stack.getMetadata())) {
                // Yuri 1.3.1 refills splash potions with one shift-click per delay.
                mc().playerController
                    .windowClick(
                        mc().thePlayer.inventoryContainer.windowId, i, 0, 1, mc().thePlayer);
                lastClicked = now();
                return;
              }
            }
          });
    }
  }

  static final class AutoArmor extends GameplayModule {
    private final Setting<Integer> delay;
    private final Setting<Boolean> still, inventory;
    // Match the donor's construction-time timer without restarting it on enable.
    private long lastAction = now();

    AutoArmor(ClientRuntime r) {
      super(
          r,
          "autoarmor",
          "Auto Armor",
          "Equip the strongest available armor piece.",
          ModuleCategory.PLAYER);
      delay = integer("delay", "Delay (ms)", 150, 0, 300);
      still = bool("stop-when-moving", "Stop when moving", false);
      inventory = bool("inventory-only", "Inventory only", false);
    }

    @Override
    protected void onEnable(ModuleContext c) {
      c.listen(
          PlayerControlEvent.class,
          e -> {
            if (e != PlayerControlEvent.BEFORE_MOTION) return;
            if (mc().thePlayer.openContainer != mc().thePlayer.inventoryContainer) {
              lastAction = now();
              return;
            }
            if (inventory.get() && !(mc().currentScreen instanceof GuiInventory)
                || still.get() && GameActions.moving(mc().thePlayer)
                || mc().thePlayer.inventory.getItemStack() != null
                || now() - lastAction < delay.get()) return;
            for (int armorSlot = 5; armorSlot < 9; armorSlot++) {
              ItemStack worn = mc().thePlayer.inventoryContainer.getSlot(armorSlot).getStack();
              int best = -1, score = protection(worn);
              for (int slot = 9; slot < 45; slot++) {
                ItemStack candidate = mc().thePlayer.inventoryContainer.getSlot(slot).getStack();
                if (candidate != null
                    && candidate.getItem() instanceof ItemArmor
                    && ((ItemArmor) candidate.getItem()).armorType == armorSlot - 5
                    && protection(candidate) > score) {
                  best = slot;
                  score = protection(candidate);
                }
              }
              if (best < 0) continue;
              // One transaction per delay; retain donor's drop-old-then-equip
              // behavior.
              mc().playerController
                  .windowClick(
                      mc().thePlayer.inventoryContainer.windowId,
                      worn == null ? best : armorSlot,
                      worn == null ? 0 : 1,
                      worn == null ? 1 : 4,
                      mc().thePlayer);
              lastAction = now();
              return;
            }
          });
    }

    private int protection(ItemStack stack) {
      return stack != null && stack.getItem() instanceof ItemArmor
          ? ((ItemArmor) stack.getItem()).damageReduceAmount
              + EnchantmentHelper.getEnchantmentLevel(Enchantment.protection.effectId, stack)
          : -1;
    }
  }
}
