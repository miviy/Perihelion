package perihelion.integration.minecraft189.gui.modern;

import perihelion.integration.minecraft189.gui.menu.MenuSurface;
import perihelion.integration.minecraft189.gui.theme.ThemePalette;
import perihelion.integration.minecraft189.gui.widget.UiCanvas;

/** Neutral New surfaces and shared client accent palette. */
public final class ModernTheme {
  public static final int PANEL = 0xfa242322, HEADER = 0xff191817, SETTINGS = 0xff201f1e;
  public static final int TEXT = 0xfff2f2f2, MUTED = 0xffc6c1bb;
  public static final int ACCENT = 0xffef883b, ROW_ENABLED = 0xffc46d32;
  public static final int SELECTION = 0x30ef883b, HOVER = 0x10ffffff;
  public static final int BORDER = 0xff49443f, INPUT = 0xff191817;

  private ModernTheme() {}

  public static float snap(UiCanvas c, float value) {
    float pixels = Math.max(.01f, c.pixelScale);
    return Math.round(value * pixels) / pixels;
  }

  public static void palette(UiCanvas c) {
    palette(c, c.theme);
  }

  public static void palette(UiCanvas c, ThemePalette palette) {
    c.theme = palette == null ? ThemePalette.DEFAULT : palette;
    c.panel = PANEL;
    c.header = c.headerBottom = HEADER;
    c.text = c.enabled = c.selectedText = c.sectionText = TEXT;
    c.muted = MUTED;
    c.accent = c.theme.accent(0);
    c.selection = 0x24000000 | c.accent & 0xffffff;
    c.hover = HOVER;
    c.activeButton = MenuSurface.blend(HEADER, c.accent, .18f);
    c.textShadow = false;
  }

  /** One restrained gradient follows the same stops on every enabled module row. */
  public static void band(UiCanvas c, float x, float y, float w, float h, float opacity) {
    int alpha = Math.round(Math.max(0, Math.min(1, opacity)) * 255) << 24;
    int left = alpha | c.rowAccentAt(0) & 0xffffff;
    int right = alpha | c.rowAccentAt(1) & 0xffffff;
    if (c.theme.size() == 3) {
      int middle = alpha | c.rowAccentAt(.5f) & 0xffffff;
      c.gradient(x, y, w / 2, h, left, middle, middle, left);
      c.gradient(x + w / 2, y, w - w / 2, h, middle, right, right, middle);
    } else c.gradient(x, y, w, h, left, right, right, left);
  }

  public static void surface(UiCanvas c, float x, float y, float w, float h, int color) {
    float right = snap(c, x + w), bottom = snap(c, y + h);
    x = snap(c, x);
    y = snap(c, y);
    w = right - x;
    h = bottom - y;
    MenuSurface.rounded(c, x, y, w, h, Math.min(c.cornerRadius, h / 2), color);
  }

  public static void input(UiCanvas c, float x, float y, float w, float h, boolean focused) {
    float right = snap(c, x + w), bottom = snap(c, y + h);
    x = snap(c, x);
    y = snap(c, y);
    w = right - x;
    h = bottom - y;
    float line = 1 / Math.max(.01f, c.pixelScale);
    surface(c, x, y, w, h, focused ? c.accent : BORDER);
    MenuSurface.rounded(
        c,
        x + line,
        y + line,
        w - line * 2,
        h - line * 2,
        Math.max(0, Math.min(c.cornerRadius, h / 2) - line),
        INPUT);
  }

  public static void button(
      UiCanvas c,
      float x,
      float y,
      float w,
      float h,
      boolean active,
      boolean enabled,
      boolean hover) {
    button(c, x, y, w, h, active, enabled, hover ? 1 : 0);
  }

  public static void button(
      UiCanvas c,
      float x,
      float y,
      float w,
      float h,
      boolean active,
      boolean enabled,
      float hover) {
    active = active && enabled;
    hover = enabled ? Math.max(0, Math.min(1, hover)) : 0;
    int base = active ? c.activeButton : HEADER;
    surface(c, x, y, w, h, MenuSurface.blend(base, c.accent, hover * .12f));
  }

  public static void checkbox(UiCanvas c, float x, float y, boolean checked) {
    ModernControls.checkbox(c, x, y, checked);
  }

  public static void checkboxRow(
      UiCanvas c, float right, float top, float height, boolean checked) {
    ModernControls.checkboxRow(c, right, top, height, checked);
  }

  public static void checkbox(UiCanvas c, float x, float y, float size, boolean checked) {
    ModernControls.checkbox(c, x, y, size, checked);
  }
}
