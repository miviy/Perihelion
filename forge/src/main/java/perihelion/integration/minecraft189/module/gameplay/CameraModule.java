package perihelion.integration.minecraft189.module.gameplay;

import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.*;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.event.*;

final class CameraModule extends GameplayModule {
  private final Setting<Double> x, y, z, scale;
  private final Setting<Integer> slowdown;
  private final Setting<Boolean> hurt, fire, blind, boss, bright, nausea, haste;
  private final Setting<Double> fireAlpha, fireOffset;
  private final Setting<Integer> fireRed, fireGreen, fireBlue;

  CameraModule(ClientRuntime r) {
    super(
        r,
        "camera",
        "Camera",
        "Adjust the hand and suppress selected camera overlays.",
        ModuleCategory.RENDER);
    x = number("x", "X", 0, -2, 2, 0.05);
    y = number("y", "Y", 0, -2, 2, 0.05);
    z = number("z", "Z", 0, -2, 2, 0.05);
    scale = number("scale", "Scale", 1, 0.1, 2, 0.1);
    slowdown = integer("slowdown", "Slowdown", 1, 1, 15);
    hurt = bool("no-hurt-camera", "No Hurt Camera", true);
    fire = bool("no-fire-overlay", "No Fire Overlay", true);
    blind = bool("no-blindness", "No Blindness", true);
    boss = bool("no-boss-bar", "No Boss Bar", true);
    haste = bool("no-haste-swing", "No Haste Swing", true);
    nausea = bool("no-nausea", "No Nausea", false);
    fireAlpha = number("fire-alpha", "Fire Alpha", .9, 0, 1, .05).visibleWhen(() -> !fire.get());
    fireOffset = number("fire-offset", "Fire Offset", 0, -2, 2, .05).visibleWhen(() -> !fire.get());
    fireRed = integer("fire-red", "Fire Red", 255, 0, 255).visibleWhen(() -> !fire.get());
    fireGreen = integer("fire-green", "Fire Green", 255, 0, 255).visibleWhen(() -> !fire.get());
    fireBlue = integer("fire-blue", "Fire Blue", 255, 0, 255).visibleWhen(() -> !fire.get());
    bright = bool("full-bright", "Full Bright", false);
  }

  @Override
  protected void onEnable(ModuleContext c) {
    c.listen(
        FireAppearanceEvent.class,
        e -> {
          e.red = fireRed.get() / 255f;
          e.green = fireGreen.get() / 255f;
          e.blue = fireBlue.get() / 255f;
          e.alpha = fireAlpha.get().floatValue();
          e.offset = fireOffset.get().floatValue();
        });
    c.listen(
        HandTransformEvent.class,
        e -> {
          e.x = x.get();
          e.y = y.get();
          e.z = z.get();
          e.scale = scale.get();
        });
    c.listen(
        SwingDurationEvent.class,
        e -> {
          int duration = haste.get() ? 6 : e.ticks();
          e.ticks(duration * slowdown.get());
        });
    c.listen(
        OverlayEffectEvent.class,
        e -> {
          if (e.kind() == OverlayEffectEvent.Kind.FIRE && fire.get()
              || e.kind() == OverlayEffectEvent.Kind.BOSS_BAR && boss.get()) e.cancel();
        });
    c.listen(
        VisualEffectEvent.class,
        e -> {
          if (e.kind() == VisualEffectEvent.Kind.NAUSEA && nausea.get()
              || e.kind() == VisualEffectEvent.Kind.HURT_CAMERA && hurt.get()
              || e.kind() == VisualEffectEvent.Kind.BLINDNESS && blind.get()
              || e.kind() == VisualEffectEvent.Kind.GAMMA && bright.get()) e.cancel();
        });
  }
}
