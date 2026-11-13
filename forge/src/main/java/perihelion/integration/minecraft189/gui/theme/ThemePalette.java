package perihelion.integration.minecraft189.gui.theme;

import java.util.Objects;

/** Immutable, static accent stops. Neutral surfaces and label colors are owned by the UI theme. */
public final class ThemePalette {
  public static final ThemePalette DEFAULT = of(0xffef883b);
  private static final double[] LINEAR = linearChannels();
  private static final double PANEL_LUMINANCE = luminance(0xff242322);
  private static final double MINIMUM_ACCENT_LUMINANCE = 4.5 * (PANEL_LUMINANCE + .05) - .05;
  private static final double MAXIMUM_FILL_LUMINANCE = (luminance(0xfff2f2f2) + .05) / 4.5 - .05;
  private final int[] colors;

  private ThemePalette(int[] colors) {
    Objects.requireNonNull(colors, "colors");
    if (colors.length < 1 || colors.length > 3)
      throw new IllegalArgumentException("A theme uses one, two or three colors");
    this.colors = colors.clone();
    for (int i = 0; i < this.colors.length; i++) this.colors[i] |= 0xff000000;
  }

  public static ThemePalette of(int... colors) {
    return new ThemePalette(colors);
  }

  public int size() {
    return colors.length;
  }

  /** Position runs from zero to one; the middle stop in a three-color palette is exactly .5. */
  public int color(float position) {
    if (colors.length == 1) return colors[0];
    if (Float.isNaN(position) || position <= 0) return colors[0];
    if (position >= 1) return colors[colors.length - 1];
    float offset = position * (colors.length - 1);
    int left = (int) offset;
    return blend(colors[left], colors[left + 1], offset - left);
  }

  /**
   * Accent text and ticks retain 4.5:1 contrast against the graphite panel, even for dark customs.
   */
  public int accent(float position) {
    int color = color(position);
    if (luminance(color) >= MINIMUM_ACCENT_LUMINANCE) return color;
    int low = 0, high = 255;
    // Eight integer steps find the least white lift needed. The lookup avoids per-frame powers.
    while (low < high) {
      int middle = (low + high) >>> 1;
      if (luminance(lift(color, middle)) >= MINIMUM_ACCENT_LUMINANCE) high = middle;
      else low = middle + 1;
    }
    return lift(color, high);
  }

  /**
   * Quiet enabled-row fills keep the UI's near-white labels readable, even with pale custom stops.
   */
  public int fill(float position) {
    int color = shade(color(position), 209); // Approximately .82 of the stored accent.
    if (luminance(color) <= MAXIMUM_FILL_LUMINANCE) return color;
    int low = 0, high = 255;
    while (low < high) {
      int middle = (low + high) >>> 1;
      if (luminance(shade(color, 255 - middle)) <= MAXIMUM_FILL_LUMINANCE) high = middle;
      else low = middle + 1;
    }
    return shade(color, 255 - high);
  }

  private static int blend(int first, int second, float amount) {
    return 0xff000000
        | Math.round((first >> 16 & 255) + ((second >> 16 & 255) - (first >> 16 & 255)) * amount)
            << 16
        | Math.round((first >> 8 & 255) + ((second >> 8 & 255) - (first >> 8 & 255)) * amount) << 8
        | Math.round((first & 255) + ((second & 255) - (first & 255)) * amount);
  }

  private static int lift(int color, int amount) {
    int red = color >> 16 & 255, green = color >> 8 & 255, blue = color & 255;
    return 0xff000000
        | (red + ((255 - red) * amount + 127) / 255) << 16
        | (green + ((255 - green) * amount + 127) / 255) << 8
        | blue + ((255 - blue) * amount + 127) / 255;
  }

  private static int shade(int color, int amount) {
    return 0xff000000
        | (((color >> 16 & 255) * amount + 127) / 255) << 16
        | (((color >> 8 & 255) * amount + 127) / 255) << 8
        | ((color & 255) * amount + 127) / 255;
  }

  private static double luminance(int color) {
    return .2126 * LINEAR[color >> 16 & 255]
        + .7152 * LINEAR[color >> 8 & 255]
        + .0722 * LINEAR[color & 255];
  }

  private static double[] linearChannels() {
    double[] values = new double[256];
    for (int i = 0; i < values.length; i++) {
      double channel = i / 255.0;
      values[i] = channel <= .04045 ? channel / 12.92 : Math.pow((channel + .055) / 1.055, 2.4);
    }
    return values;
  }
}
