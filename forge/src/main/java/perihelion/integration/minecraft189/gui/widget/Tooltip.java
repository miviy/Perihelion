package perihelion.integration.minecraft189.gui.widget;

import java.util.ArrayList;
import java.util.List;
import perihelion.integration.minecraft189.gui.font.GuiFonts;
import perihelion.integration.minecraft189.gui.menu.MenuSurface;
import perihelion.integration.minecraft189.gui.modern.ModernTheme;
import perihelion.integration.minecraft189.gui.raven.RavenTheme;

/** A quiet, delayed description card owned by its frontend. */
public final class Tooltip {
  private static final long DELAY = 2_000_000_000L;
  private static final float PADDING = 7, MARGIN = 4;
  private String text = "";
  private long since;
  private float anchorX, anchorY, wrapWidth = -1;
  private GuiFonts.Face font;
  private final List<String> lines = new ArrayList<>();

  public void clear() {
    text = "";
    since = 0;
    lines.clear();
    wrapWidth = -1;
  }

  /** Monotonic dwell time; traversing a row or changing descriptions starts a fresh delay. */
  boolean ready(String next, float mx, float my, long now) {
    if (next == null || next.trim().isEmpty()) {
      clear();
      return false;
    }
    if (!next.equals(text) || Math.abs(mx - anchorX) > 4 || Math.abs(my - anchorY) > 4) {
      text = next;
      since = now;
      anchorX = mx;
      anchorY = my;
      lines.clear();
    }
    return now - since >= DELAY;
  }

  public boolean draw(UiCanvas c, String next, float mx, float my, float width, float height) {
    if (font != c.font) {
      clear();
      font = c.font;
    }
    if (!ready(next, mx, my, System.nanoTime())) return false;
    if (c.classic) {
      RavenTheme.tooltip(c, text, mx, my, width, height);
      return true;
    }
    float available = Math.min(220, width - MARGIN * 2 - PADDING * 2);
    float lineHeight = Math.max(11, font.height() + 3);
    int capacity = (int) ((height - MARGIN * 2 - PADDING * 2) / lineHeight);
    if (available < 1 || capacity < 1) return false;
    if (lines.isEmpty() || available != wrapWidth) {
      lines.clear();
      lines.addAll(UiCanvas.wrap(font, text, available));
      wrapWidth = available;
    }
    int count = Math.min(capacity, lines.size());
    List<String> visible = lines.subList(0, count);
    float contentWidth = 0;
    for (String line : visible) contentWidth = Math.max(contentWidth, font.width(line));
    float w = Math.min(available, contentWidth) + PADDING * 2;
    float h = count * lineHeight - 3 + PADDING * 2;
    float x = mx + 10;
    if (x + w > width - MARGIN) x = mx - w - 10;
    x = Math.max(MARGIN, Math.min(x, width - w - MARGIN));
    float y = my + 14;
    if (y + h > height - MARGIN) y = my - h - 10;
    y = Math.max(MARGIN, Math.min(y, height - h - MARGIN));
    x = ModernTheme.snap(c, x);
    y = ModernTheme.snap(c, y);
    float pixel = 1 / Math.max(.01f, c.pixelScale);
    float radius = c.modern ? Math.min(5, c.cornerRadius) : 3;
    int border = c.modern ? ModernTheme.BORDER : MenuSurface.blend(c.header, c.text, .22f);
    int fill = c.modern ? ModernTheme.HEADER : 0xff191b1e;
    MenuSurface.rounded(c, x, y + pixel, w, h, radius, 0x35000000);
    MenuSurface.rounded(c, x, y, w, h, radius, border);
    MenuSurface.rounded(
        c, x + pixel, y + pixel, w - pixel * 2, h - pixel * 2, Math.max(0, radius - pixel), fill);
    for (int i = 0; i < count; i++) {
      String line = visible.get(i);
      if (i == count - 1 && lines.size() > count) line = c.fit(line + "...", w - PADDING * 2);
      c.text(line, x + PADDING, y + PADDING + i * lineHeight, c.text);
    }
    return true;
  }
}
