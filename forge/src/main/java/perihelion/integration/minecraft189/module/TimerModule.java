package perihelion.integration.minecraft189.module;

import perihelion.core.control.ControlRequest;
import perihelion.core.event.ClientTickEvent;
import perihelion.core.module.ClientModule;
import perihelion.core.module.ModuleCategory;
import perihelion.core.module.ModuleContext;
import perihelion.core.module.ModuleInfo;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.event.PlayerControlEvent;

public final class TimerModule extends ClientModule {
  public enum Mode {
    CONSTANT,
    PULSE
  }

  private final Setting<Mode> mode;
  private final Setting<Double> time1, time2;
  private final TimerPulse pulse = new TimerPulse(System.nanoTime());

  public TimerModule(Runnable guard) {
    super(
        new ModuleInfo(
            "timer",
            "Timer",
            "Change client tick speed with constant or 100 ms pulse timing.",
            ModuleCategory.MOVEMENT,
            true));
    mode =
        setting(
            Setting.enumeration(
                "mode", "Mode", "Timer pattern.", Mode.CONSTANT, Mode.class, guard));
    time1 =
        setting(
            Setting.decimal(
                "time-amount", "Time Amount", "Primary speed multiplier.", 1, 0.1, 5, 0.1, guard));
    time2 =
        setting(
            Setting.decimal(
                    "time-amount-2",
                    "Time Amount 2",
                    "First 100 ms of each pulse.",
                    1,
                    0.1,
                    5,
                    0.1,
                    guard)
                .visibleWhen(() -> mode.get() == Mode.PULSE));
  }

  @Override
  protected void onEnable(ModuleContext context) {
    ControlRequest<Float> timer = context.controls().timerSpeed(10);
    pulse.resume(
        ((perihelion.integration.minecraft189.control.MinecraftTimerAccess)
                net.minecraft.client.Minecraft.getMinecraft())
            .perihelion$timer()
            .timerSpeed);
    Runnable update =
        () ->
            timer.setForTicks(
                pulse.speed(
                    System.nanoTime(),
                    mode.get() == Mode.PULSE,
                    time1.get().floatValue(),
                    time2.get().floatValue()),
                1);
    context.listen(
        PlayerControlEvent.class,
        event -> {
          if (event == PlayerControlEvent.BEFORE_INPUT) update.run();
        });
    context.listen(
        ClientTickEvent.class,
        event -> {
          if (event == ClientTickEvent.START
              && net.minecraft.client.Minecraft.getMinecraft().thePlayer != null
              && net.minecraft.client.Minecraft.getMinecraft().thePlayer.isRiding()) update.run();
        });
  }
}
