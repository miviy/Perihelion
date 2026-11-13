package perihelion.integration.minecraft189.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import perihelion.core.setting.ColorValue;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.gui.ClickGuiModule;
import perihelion.integration.minecraft189.gui.GuiTheme;

/** Independent presentation settings; values live in the existing HUD config and presets. */
final class HudElementStyle {
  enum Font {
    CLIENT,
    MINECRAFT,
    SANS,
    SERIF,
    MONO,
    INTER,
    ROBOTO,
    ROBOTO_CONDENSED,
    OPEN_SANS,
    LATO,
    MONTSERRAT,
    POPPINS,
    MONASPACE,
    NERD_MONO
  }

  enum Colors {
    CLIENT,
    SOLID,
    GRADIENT,
    RAINBOW
  }

  enum Background {
    AUTO,
    NONE,
    SOLID,
    GRADIENT
  }

  enum Direction {
    HORIZONTAL,
    VERTICAL
  }

  final List<Setting<?>> settings = new ArrayList<>();
  final Setting<Font> font;
  final Setting<Double> scale, opacity, shadowOpacity;
  final Setting<Boolean> bold, shadow, blur, panelShadow;
  final Setting<Integer> padding, radius, blurStrength, shadowSpread;
  final Setting<Colors> colors;
  final Setting<Background> background;
  final Setting<Direction> direction;
  final Setting<ColorValue> color, secondary, backgroundColor, backgroundSecondary;

  HudElementStyle(
      HudModule hud, HudModule.Element element, Function<Setting<?>, Setting<?>> register) {
    Runnable guard = hud.runtime.tasks()::checkThread;
    String id = element.id() + "-";
    font =
        add(
            register,
            Setting.enumeration(
                id + "font",
                "Font",
                "Client follows the menu font; other choices apply only to this element.",
                Font.CLIENT,
                Font.class,
                guard));
    bold =
        add(
            register,
            Setting.bool(id + "bold", "Bold", "Bold text for an independent font.", false, guard)
                .visibleWhen(() -> font.get() != Font.CLIENT));
    scale =
        add(
            register,
            Setting.decimal(
                id + "scale",
                "Size",
                "Scale this element and its drag bounds together.",
                1,
                .65,
                1.75,
                .05,
                guard));
    shadow =
        add(
            register,
            Setting.bool(
                id + "shadow", "Text shadow", "A small shadow behind text.", false, guard));
    colors =
        add(
            register,
            Setting.enumeration(
                id + "colors",
                "Colors",
                "Follow the client, use a solid color, or color rows with a gradient or rainbow.",
                Colors.CLIENT,
                Colors.class,
                guard));
    color =
        add(
            register,
            Setting.color(
                    id + "color",
                    "Text color",
                    "First text and accent color.",
                    ColorValue.rgb(239, 136, 59),
                    guard)
                .visibleWhen(
                    () -> colors.get() == Colors.SOLID || colors.get() == Colors.GRADIENT));
    secondary =
        add(
            register,
            Setting.color(
                    id + "secondary",
                    "Second text color",
                    "Last color in the text gradient.",
                    ColorValue.rgb(245, 211, 168),
                    guard)
                .visibleWhen(() -> colors.get() == Colors.GRADIENT));
    background =
        add(
            register,
            Setting.enumeration(
                id + "background",
                "Background",
                "Auto follows the element's native background; None keeps only its content.",
                element == HudModule.Element.MODULES || element == HudModule.Element.WATERMARK
                    ? Background.SOLID
                    : Background.AUTO,
                Background.class,
                guard));
    backgroundColor =
        add(
            register,
            Setting.color(
                    id + "background-color",
                    "Color",
                    "Background tint with native alpha support.",
                    ColorValue.rgb(10, 10, 12),
                    guard)
                .visibleWhen(
                    () ->
                        background.get() == Background.SOLID
                            || background.get() == Background.GRADIENT));
    backgroundSecondary =
        add(
            register,
            Setting.color(
                    id + "background-secondary",
                    "Second background",
                    "Second background gradient color.",
                    ColorValue.rgb(47, 42, 37),
                    guard)
                .visibleWhen(() -> background.get() == Background.GRADIENT));
    direction =
        add(
            register,
            Setting.enumeration(
                    id + "gradient-direction",
                    "Gradient direction",
                    "Direction of the background gradient.",
                    Direction.VERTICAL,
                    Direction.class,
                    guard)
                .visibleWhen(() -> background.get() == Background.GRADIENT));
    opacity =
        add(
            register,
            Setting.decimal(
                    id + "opacity",
                    "Opacity",
                    "Opacity of this element's background.",
                    element == HudModule.Element.MODULES || element == HudModule.Element.WATERMARK
                        ? .3
                        : .45,
                    0,
                    1,
                    .05,
                    guard)
                .visibleWhen(() -> background.get() != Background.NONE));
    padding =
        add(
            register,
            Setting.integer(
                    id + "padding",
                    "Outer inset",
                    "Optional extra inset beyond the normal text padding.",
                    element == HudModule.Element.WATERMARK ? 3 : 0,
                    0,
                    10,
                    guard)
                .visibleWhen(() -> element != HudModule.Element.TAB_GUI));
    radius =
        add(
            register,
            Setting.integer(
                id + "radius",
                "Corner radius",
                "Corner radius, including the blur mask. Zero is square.",
                element == HudModule.Element.MODULES ? 1 : 2,
                0,
                10,
                guard));
    blur =
        add(
            register,
            Setting.bool(
                id + "blur",
                "Blur",
                "Blur the world behind this element, independently of its background opacity.",
                false,
                guard));
    blurStrength =
        add(
            register,
            Setting.integer(
                    id + "blur-strength",
                    "Blur strength",
                    "Radius of the background blur.",
                    5,
                    1,
                    16,
                    guard)
                .visibleWhen(blur::get));
    panelShadow =
        add(
            register,
            Setting.bool(
                id + "panel-shadow",
                "Panel shadow",
                "A soft shadow outside this element's background.",
                false,
                guard));
    shadowOpacity =
        add(
            register,
            Setting.decimal(
                id + "shadow-opacity",
                "Shadow opacity",
                "Darkness of the panel shadow.",
                .15,
                0,
                1,
                .05,
                guard));
    shadowSpread =
        add(
            register,
            Setting.integer(
                id + "shadow-spread",
                "Shadow spread",
                "Distance the soft shadow extends outside the panel.",
                3,
                1,
                16,
                guard));
    for (Setting<?> setting : settings)
      setting.visibleWhen(() -> hud.customizing == element && visible(setting, element));
  }

  private boolean visible(Setting<?> value, HudModule.Element element) {
    if (value == bold) return font.get() != Font.CLIENT;
    if (value == color) return colors.get() == Colors.SOLID || colors.get() == Colors.GRADIENT;
    if (value == secondary) return colors.get() == Colors.GRADIENT;
    if (value == backgroundColor)
      return background.get() == Background.SOLID || background.get() == Background.GRADIENT;
    if (value == backgroundSecondary || value == direction)
      return background.get() == Background.GRADIENT;
    if (value == opacity) return background.get() != Background.NONE;
    if (value == padding) return element != HudModule.Element.TAB_GUI;
    if (value == blurStrength) return blur.get();
    if (value == shadowOpacity || value == shadowSpread) return panelShadow.get();
    return true;
  }

  boolean textOption(Setting<?> value) {
    return value == font
        || value == bold
        || value == scale
        || value == shadow
        || value == colors
        || value == color
        || value == secondary;
  }

  @SuppressWarnings("unchecked")
  private <T> Setting<T> add(Function<Setting<?>, Setting<?>> register, Setting<T> value) {
    Setting<T> registered = (Setting<T>) register.apply(value);
    settings.add(registered);
    return registered;
  }

  ClickGuiModule.Font font(HudModule hud) {
    return font.get() == Font.CLIENT
        ? GuiTheme.font(hud.runtime)
        : ClickGuiModule.Font.valueOf(font.get().name());
  }

  int color(HudModule hud, long now, float position, int client) {
    switch (colors.get()) {
      case SOLID:
        return color.get().resolvedArgb(now);
      case GRADIENT:
        return HudAppearance.blend(
            color.get().resolvedArgb(now), secondary.get().resolvedArgb(now), position);
      case RAINBOW:
        return 0xff000000
            | java.awt.Color.HSBtoRGB((now % 8000) / 8000f + position * .18f, .55f, .95f)
                & 0xffffff;
      default:
        return client;
    }
  }

  int background(long now, boolean second) {
    int color = (second ? backgroundSecondary : backgroundColor).get().resolvedArgb(now);
    return (Math.round((color >>> 24) * opacity.get().floatValue()) << 24) | color & 0xffffff;
  }
}
