package perihelion.integration.minecraft189.gui.widget;

import perihelion.integration.minecraft189.gui.GuiTheme;
import perihelion.integration.minecraft189.gui.modern.ModernControls;
import perihelion.integration.minecraft189.gui.modern.ModernTheme;

/** The same module row presentation for module entries, HUD elements and setting groups. */
public final class ModuleRowPainter {
  private ModuleRowPainter() {}

  public static void draw(
      UiCanvas c,
      String label,
      float x,
      float y,
      float width,
      float height,
      float enabled,
      float expanded,
      float hover,
      boolean faulted,
      boolean options,
      float opacity) {
    if (c.modern) {
      if (enabled > 0) ModernTheme.band(c, x, y, width, height, enabled * opacity);
      if (hover > 0) c.rect(x, y, width, height, alpha(0x11ffffff, hover * opacity));
      c.label(
          label,
          x + 4,
          c.font.centeredY(y, height),
          width - 17,
          alpha(faulted ? 0xffe89191 : c.text, opacity));
      if (options)
        ModernControls.disclosure(
            c, x + width - 7, y + height / 2, expanded, alpha(c.text, opacity));
    } else if (c.classic) {
      if (hover > 0) c.rect(x + 1, y, width - 2, height, alpha(c.hover, hover));
      c.center(
          label,
          x + 3,
          c.font.centeredY(y, height),
          width - 6,
          faulted ? 0xffff5050 : enabled > 0 ? c.enabled : c.text);
    } else {
      if (enabled > 0) c.rect(x, y, width, height, GuiTheme.enabledRow(c));
      else if (expanded > 0) c.rect(x, y, width, height, GuiTheme.alpha(.04, 0xffffff));
      if (hover > 0) c.rect(x, y, width, height, GuiTheme.alpha(.07 * hover, 0xffffff));
      int color =
          faulted ? 0xffdc8580 : expanded > 0 ? c.selectedText : enabled > 0 ? c.enabled : c.text;
      if (faulted) c.rect(x, y + 5, 1, 6, 0xffd86e65);
      c.label(label, x + 3, c.font.centeredY(y, height), width - 12, color);
      if (options)
        c.text(expanded > 0 ? "-" : "+", x + width - 6, c.font.centeredY(y, height), c.muted);
    }
  }

  private static int alpha(int color, float opacity) {
    return Math.round((color >>> 24) * Math.max(0, Math.min(1, opacity))) << 24 | color & 0xffffff;
  }
}
