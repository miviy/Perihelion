package perihelion.integration.minecraft189.gui.theme;

import perihelion.core.module.ClientModule;
import perihelion.core.module.ModuleCategory;
import perihelion.core.module.ModuleContext;
import perihelion.core.module.ModuleInfo;
import perihelion.core.setting.ColorValue;
import perihelion.core.setting.Setting;

/** Client-thread-owned presentation preferences. Selecting a palette applies it immediately. */
public final class ThemeModule extends ClientModule {
  public enum Preset {
    EMBER(ThemePalette.DEFAULT),
    COPPER(0xffce8b66, 0xffe0b089),
    SUNSET(0xffff9865, 0xffed6b8a, 0xffb085db),
    AURORA(0xff7bdfa6, 0xff70d5ca, 0xff76afe2),
    OCEAN(0xff55a7e0, 0xff5cdbd3),
    GLACIER(0xff95c9ee, 0xffd4e5f3),
    FOREST(0xff79b68f, 0xffb3cc8b),
    SAGE(0xffa8baa1, 0xffd0cba6),
    DUNE(0xffcba675, 0xffe4cdaa),
    COFFEE(0xffbd9478, 0xffdfc2a3),
    ROSE(0xffdf8d9c, 0xffe7b3c2),
    CHERRY(0xffe86c78, 0xffe6a083),
    CORAL(0xffee8b75, 0xffffb77a),
    LAVENDER(0xffa79cde, 0xffd4b9e3),
    IRIS(0xff8d8fdf, 0xffba8ada),
    ARCTIC(0xff8cd7df, 0xffacd9ef, 0xffc2c8ef),
    COBALT(0xff759ce8, 0xff79c1e4),
    HONEY(0xffe1b45b, 0xfff1cf85),
    GRAPHITE(0xffa0a8b1, 0xffd3d7dc),
    PEARL(0xffe4dfd4, 0xfff4eee6),
    CUSTOM((ThemePalette) null);

    private final ThemePalette palette;

    Preset(int... colors) {
      this(ThemePalette.of(colors));
    }

    Preset(ThemePalette palette) {
      this.palette = palette;
    }

    /** Custom colors are supplied by the module's ordinary persisted color settings. */
    public ThemePalette palette() {
      if (palette == null) throw new IllegalStateException("Custom uses the saved color settings");
      return palette;
    }
  }

  public enum ColorCount {
    ONE(1),
    TWO(2),
    THREE(3);
    private final int count;

    ColorCount(int count) {
      this.count = count;
    }

    public int count() {
      return count;
    }
  }

  public final Setting<Preset> preset;
  public final Setting<ColorCount> colors;
  public final Setting<ColorValue> color1, color2, color3;
  private ThemePalette customPalette;
  private ColorCount cachedCount;
  private int cachedFirst, cachedSecond, cachedThird;

  public ThemeModule(Runnable guard) {
    super(
        new ModuleInfo(
            "themes",
            "Themes",
            "Client accent colors. Selections apply immediately.",
            ModuleCategory.MISC,
            false));
    preset =
        setting(
            Setting.enumeration(
                "preset",
                "Theme",
                "Choose a ready-made palette or Custom.",
                Preset.EMBER,
                Preset.class,
                guard));
    colors =
        setting(
            Setting.enumeration(
                    "colors",
                    "Colors",
                    "Use one, two or three accent colors.",
                    ColorCount.TWO,
                    ColorCount.class,
                    guard)
                .visibleWhen(this::custom));
    color1 =
        setting(
            Setting.color(
                    "color-1",
                    "Color 1",
                    "First accent color.",
                    ColorValue.rgb(239, 136, 59),
                    guard)
                .visibleWhen(this::custom));
    color2 =
        setting(
            Setting.color(
                    "color-2",
                    "Color 2",
                    "Second accent color.",
                    ColorValue.rgb(237, 107, 138),
                    guard)
                .visibleWhen(() -> custom() && colors.get().count() >= 2));
    color3 =
        setting(
            Setting.color(
                    "color-3",
                    "Color 3",
                    "Third accent color.",
                    ColorValue.rgb(176, 133, 219),
                    guard)
                .visibleWhen(() -> custom() && colors.get() == ColorCount.THREE));
  }

  private boolean custom() {
    return preset.get() == Preset.CUSTOM;
  }

  /** Reuses the immutable palette across frames and while hidden custom settings are unchanged. */
  public ThemePalette palette() {
    Preset selected = preset.get();
    if (selected != Preset.CUSTOM) return selected.palette();
    ColorCount count = colors.get();
    int first = color1.get().argb() | 0xff000000;
    int second = count.count() >= 2 ? color2.get().argb() | 0xff000000 : 0;
    int third = count == ColorCount.THREE ? color3.get().argb() | 0xff000000 : 0;
    if (customPalette == null
        || cachedCount != count
        || cachedFirst != first
        || cachedSecond != second
        || cachedThird != third) {
      customPalette =
          count == ColorCount.ONE
              ? ThemePalette.of(first)
              : count == ColorCount.TWO
                  ? ThemePalette.of(first, second)
                  : ThemePalette.of(first, second, third);
      cachedCount = count;
      cachedFirst = first;
      cachedSecond = second;
      cachedThird = third;
    }
    return customPalette;
  }

  @Override
  protected void onEnable(ModuleContext context) {
    // Presentation preferences are always available and never retain an active module scope.
    context.requestDisable();
  }
}
