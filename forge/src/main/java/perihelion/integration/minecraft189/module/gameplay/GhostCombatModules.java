// SPDX-License-Identifier: AGPL-3.0-only
// Adapted from Yuri 1cb86ad94ce1f01aa9b715577163c8ecc47b7def; Copyright (c) 2026 Unlegit Inc.
package perihelion.integration.minecraft189.module.gameplay;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.util.MovingObjectPosition;
import perihelion.core.control.ControlRequest;
import perihelion.core.control.InputKey;
import perihelion.core.event.ClientTickEvent;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleCategory;
import perihelion.core.module.ModuleContext;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.*;

final class GhostCombatModules {
  private GhostCombatModules() {}

  static final class Reach extends GameplayModule {
    enum Activation { WEAPON, MOVING, SPRINTING }
    private final Setting<Double> min, max;
    private final Setting<java.util.Set<Activation>> activation;

    Reach(ClientRuntime runtime) {
      super(runtime, "reach", "Reach", "Randomize entity selection reach.", ModuleCategory.COMBAT);
      min = number("min-range", "Min Range", 3, 3, 6, .01);
      max = number("max-range", "Max Range", 3, 3, 6, .01);
      activation = setting(Setting.multiSelect("activation", "Only While",
          "Require every selected condition for extra reach.", java.util.EnumSet.noneOf(Activation.class), Activation.class, guard));
    }

    @Override
    protected void onEnable(ModuleContext context) {
      context.listen(
          MouseOverEvent.class,
          event -> {
              java.util.Set<Activation> gates = activation.get();
              if (gates.contains(Activation.WEAPON)
                      && (mc().thePlayer.getHeldItem() == null || !(mc().thePlayer.getHeldItem().getItem() instanceof ItemSword))
                  || gates.contains(Activation.MOVING) && mc().thePlayer.movementInput.moveForward == 0 && mc().thePlayer.movementInput.moveStrafe == 0
                  || gates.contains(Activation.SPRINTING) && !mc().thePlayer.isSprinting()) return;
              event.range(
                  Math.min(min.get(), max.get())
                      + Math.random() * Math.abs(max.get() - min.get()));
          });
      context.listen(RightClickEvent.class, RightClickEvent::requestNormalRangeRay);
    }
  }

  static final class HitboxExpand extends GameplayModule {
    private final Setting<Double> amount;

    HitboxExpand(ClientRuntime runtime) {
      super(
          runtime,
          "hitboxexpand",
          "Hitbox Expand",
          "Expand selection boxes without changing entities.",
          ModuleCategory.COMBAT);
      amount = number("amount", "Amount", 0, 0, 6, .1);
    }

    @Override
    protected void onEnable(ModuleContext context) {
      context.listen(MouseOverEvent.class, event -> event.expand(amount.get().floatValue()));
      context.listen(RightClickEvent.class, RightClickEvent::requestNormalRangeRay);
    }
  }

  static final class KeepSprint extends GameplayModule {
    private final Setting<Double> motion, chance;

    KeepSprint(ClientRuntime runtime) {
      super(
          runtime,
          "keepsprint",
          "Keep Sprint",
          "Control slowdown and sprint continuation after attacks.",
          ModuleCategory.COMBAT);
      motion = number("attack-motion", "Attack Motion", 1, 0, 1, .05);
      chance = number("chance", "Chance", 100, 0, 100, 1);
    }

    @Override
    protected void onEnable(ModuleContext context) {
      context.listen(
          HitSlowdownEvent.class,
          event -> {
            if (chance.get() == 100 || Math.random() < chance.get() / 100) {
              event.factor(motion.get());
              event.sprint(true);
            }
          });
    }
  }

  private abstract static class ClickModule extends GameplayModule {
    protected final Setting<Double> min, max;
    protected final Setting<Boolean> weapons;
    protected long lastClick = -1;

    ClickModule(ClientRuntime runtime, String id, String name, String description) {
      super(runtime, id, name, description, ModuleCategory.COMBAT);
      min = number("min-cps", "Min CPS", 8, 1, 25, .5);
      max = number("max-cps", "Max CPS", 14, 1, 25, .5);
      weapons = bool("weapons-only", "Weapons Only", false);
    }

    protected abstract boolean eligible();

    @Override
    protected void onEnable(ModuleContext context) {
      context.listen(
          ClientTickEvent.class,
          event -> {
            if (event != ClientTickEvent.START
                || mc().thePlayer == null
                || mc().theWorld == null
                || mc().currentScreen != null
                || !mc().inGameHasFocus
                || !eligible()) return;
            if (weapons.get()
                && (mc().thePlayer.getHeldItem() == null
                    || !(mc().thePlayer.getHeldItem().getItem() instanceof ItemSword
                        || mc().thePlayer.getHeldItem().getItem() instanceof ItemTool))) return;
            ThreadLocalRandom random = ThreadLocalRandom.current();
            int cps =
                GhostClickTiming.cps(
                    min.get(), max.get(), random.nextGaussian(), random.nextDouble(-.4, .4));
            if (!GhostClickTiming.due(now(), lastClick, cps)) return;
            // Exactly one queued vanilla press, matching Yuri. Do not also hold Attack or call
            // clickMouse.
            KeyBinding.onTick(mc().gameSettings.keyBindAttack.getKeyCode());
            lastClick = now();
          });
    }
  }

  static final class TriggerBot extends ClickModule {
    TriggerBot(ClientRuntime runtime) {
      super(
          runtime,
          "triggerbot",
          "Trigger Bot",
          "Attack crosshair entities at a humanized click rate.");
    }

    @Override
    protected boolean eligible() {
      return mc().objectMouseOver != null
          && mc().objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY;
    }

    @Override
    protected void onEnable(ModuleContext context) {
      lastClick = -1;
      super.onEnable(context);
    }
  }

  static final class AutoClicker extends ClickModule {
    enum Mode { NATIVE, B4_LEFT, B4_RIGHT, B4_BOTH }
    private final Setting<Boolean> breakBlocks;
    private final Setting<Mode> mode;
    private final Setting<Double> inventoryCps;
    private B4AutoClicker b4;

    AutoClicker(ClientRuntime runtime) {
      super(runtime, "autoclicker", "Auto Clicker", "Gaussian repeat clicks while holding Attack.");
      breakBlocks = bool("break-blocks", "Break Blocks", false);
      mode = mode("click-mode", "Click Mode", Mode.NATIVE, Mode.class);
      inventoryCps = number("inventory-cps", "Inventory CPS", 0, 0, 20, .5)
          .visibleWhen(() -> mode.get() != Mode.NATIVE);
      breakBlocks.visibleWhen(() -> mode.get() == Mode.NATIVE);
    }

    @Override
    protected boolean eligible() {
      return mode.get() == Mode.NATIVE
          && HookBridge.gameControls().physicalKeyDown(InputKey.ATTACK)
          && !(breakBlocks.get()
              && mc().objectMouseOver != null
              && mc().objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK);
    }

    @Override
    protected void onEnable(ModuleContext context) {
      lastClick = -1;
      super.onEnable(context);
      b4 = new B4AutoClicker(this, context, mode, min, max, weapons, inventoryCps);
      context.onCleanup(() -> b4 = null);
    }
  }

  static final class BlockHit extends GameplayModule {
    enum Mode { AFTER_ATTACK, B4_GUARD }
    private final Setting<Double> range, chance, delay, combo;
    private final Setting<Mode> mode;

    BlockHit(ClientRuntime runtime) {
      super(
          runtime,
          "blockhit",
          "Block Hit",
          "Block a held sword between manual attacks.",
          ModuleCategory.COMBAT);
      range = number("range", "Range", 3, 1, 6, .05);
      chance = number("block-chance", "Block Chance", 100, 0, 100, 1);
      delay = number("block-delay", "Block Delay", 30, 1, 300, 1);
      combo = number("combo-delay", "Combo Delay", 30, 1, 300, 1);
      mode = mode("block-mode", "Block Mode", Mode.AFTER_ATTACK, Mode.class);
      chance.visibleWhen(() -> mode.get() == Mode.AFTER_ATTACK);
      delay.visibleWhen(() -> mode.get() == Mode.AFTER_ATTACK);
      combo.visibleWhen(() -> mode.get() == Mode.AFTER_ATTACK);
    }

    @Override
    protected void onEnable(ModuleContext context) {
      ActionWindow packets = new ActionWindow(context);
      ControlRequest<Boolean> use = context.controls().key(InputKey.USE_ITEM, 45);
      new B4ManualBlock(this, context, mode, range);
      boolean[] pending = {false}, blocking = {false};
      long[] wait = {0}, started = {0};
      context.observe(mode, (setting, before, after) -> {
        pending[0] = blocking[0] = false;
        wait[0] = started[0] = 0;
        use.clear();
      });
      context.listen(
          PlayerAttackEvent.class,
          100,
          false,
          event -> {
            if (mode.get() != Mode.AFTER_ATTACK || blocking[0]
                || mc().thePlayer == null
                || event.target() == null
                || mc().thePlayer.getHeldItem() == null
                || !(mc().thePlayer.getHeldItem().getItem() instanceof ItemSword)
                || mc().thePlayer.getDistanceToEntity(event.target()) > range.get()
                || Math.random() > chance.get() / 100) return;
            pending[0] = true;
            wait[0] = now();
          });
      context.listen(
          PlayerControlEvent.class,
          event -> {
            if (event != PlayerControlEvent.BEFORE_MOTION || !mc().inGameHasFocus) return;
            if (mode.get() != Mode.AFTER_ATTACK) {
              pending[0] = blocking[0] = false;
              use.clear();
              return;
            }
            if (pending[0]
                && now() - wait[0]
                    >= ThreadLocalRandom.current().nextDouble(combo.get(), combo.get() + .01)
                && !packets.bad()) {
              pending[0] = false;
              if (HookBridge.gameControls().physicalKeyDown(InputKey.FORWARD)) {
                blocking[0] = true;
                started[0] = now();
                use.set(true);
              }
            }
            if (blocking[0]
                && now() - started[0]
                    >= ThreadLocalRandom.current().nextDouble(delay.get(), delay.get() + .01)
                && !packets.bad()) {
              blocking[0] = false;
              use.clear();
            }
          });
    }
  }
}
