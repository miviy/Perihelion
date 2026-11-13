package perihelion.integration.minecraft189.module;

import net.minecraft.client.Minecraft;
import perihelion.core.control.ControlRequest;
import perihelion.core.control.InputKey;
import perihelion.core.module.ClientModule;
import perihelion.core.module.ModuleCategory;
import perihelion.core.module.ModuleContext;
import perihelion.core.module.ModuleInfo;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.event.PlayerAttackEvent;
import perihelion.integration.minecraft189.event.PlayerControlEvent;

public final class WTapModule extends ClientModule {
  enum Mode {
    SPRINT_RESET,
    FORWARD_TAP
  }

  private final Setting<Mode> mode;
  private final Setting<Double> delay, duration;
  private final Setting<Integer> chance;

  public WTapModule(Runnable guard) {
    super(
        new ModuleInfo(
            "wtap",
            "WTap",
            "Reset sprint on attacks against recently hurt living targets.",
            ModuleCategory.COMBAT,
            true));
    mode =
        setting(
            Setting.enumeration(
                "mode",
                "Mode",
                "Sprint reset or a timed forward release.",
                Mode.SPRINT_RESET,
                Mode.class,
                guard));
    delay =
        setting(
                Setting.decimal(
                    "delay", "Delay", "Forward release delay in ticks.", 5.5, 0, 10, 0.5, guard))
            .visibleWhen(() -> mode.get() == Mode.FORWARD_TAP);
    duration =
        setting(
                Setting.decimal(
                    "duration",
                    "Duration",
                    "Forward release duration in ticks.",
                    1.5,
                    1,
                    5,
                    0.5,
                    guard))
            .visibleWhen(() -> mode.get() == Mode.FORWARD_TAP);
    chance =
        setting(
            Setting.integer(
                "chance",
                "Chance",
                "Percent chance at attack and at the subsequent reset update.",
                100,
                0,
                100,
                guard));
  }

  @Override
  protected void onEnable(ModuleContext context) {
    WTapCycle cycle = new WTapCycle();
    ForwardTapCycle forwardCycle = new ForwardTapCycle();
    ControlRequest<Boolean> forward = context.controls().key(InputKey.FORWARD, 100);
    ControlRequest<Boolean> key = context.controls().key(InputKey.SPRINT, 100);
    ControlRequest<Boolean> sprint = context.controls().sprinting(100);
    context.observe(
        mode,
        (setting, before, after) -> {
          cycle.clear();
          forwardCycle.clear();
          forward.clear();
          key.clear();
          sprint.clear();
        });
    context.listen(
        PlayerAttackEvent.class,
        event -> {
          Minecraft game = Minecraft.getMinecraft();
          if (game.thePlayer == null || game.currentScreen != null) return;
          if (mode.get() == Mode.FORWARD_TAP) {
            if (eligible(game)
                && game.thePlayer.isSprinting()
                && Math.random() * 100 < chance.get())
              forwardCycle.attack(
                  System.nanoTime() / 1_000_000L,
                  (long) (delay.get() * 50),
                  (long) (duration.get() * 50));
            return;
          }
          boolean armed =
              cycle.attack(
                  event.target().hurtTime,
                  game.thePlayer.isSprinting() || game.gameSettings.keyBindSprint.isKeyDown(),
                  chance.get(),
                  Math.random());
          if (armed) key.setForTicks(true, 1);
        });
    context.listen(
        PlayerControlEvent.class,
        event -> {
          if (event != PlayerControlEvent.BEFORE_INPUT) return;
          Minecraft game = Minecraft.getMinecraft();
          if (game.thePlayer == null || game.currentScreen != null || chance.get() == 0) {
            cycle.clear();
            forwardCycle.clear();
            forward.clear();
            key.clear();
            sprint.clear();
            return;
          }
          if (mode.get() == Mode.FORWARD_TAP) {
            if (forwardCycle.update(System.nanoTime() / 1_000_000L, eligible(game))) {
              forward.setForTicks(false, 1);
              sprint.setForTicks(false, 1);
            } else {
              forward.clear();
              sprint.clear();
            }
            return;
          }
          if (cycle.update(chance.get(), Math.random())) {
            key.setForTicks(false, 1);
            sprint.setForTicks(false, 1);
          }
        });
  }

  private boolean eligible(Minecraft game) {
    return game.inGameHasFocus
        && !game.thePlayer.isCollidedHorizontally
        && !game.thePlayer.isUsingItem()
        && (game.thePlayer.getFoodStats().getFoodLevel() > 6
            || game.thePlayer.capabilities.allowFlying)
        && !game.thePlayer.isPotionActive(net.minecraft.potion.Potion.blindness)
        && perihelion.integration.minecraft189.HookBridge.gameControls()
            .physicalKeyDown(InputKey.FORWARD);
  }
}
