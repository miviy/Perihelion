package perihelion.integration.minecraft189.gui.raven;

import java.util.List;
import java.util.Locale;
import perihelion.core.setting.ColorValue;
import perihelion.core.setting.DoubleRange;
import perihelion.core.setting.NumericConstraints;
import perihelion.integration.minecraft189.gui.GuiValues;
import perihelion.integration.minecraft189.gui.widget.SettingControl;
import perihelion.integration.minecraft189.gui.widget.SettingControl.PaintView;
import perihelion.integration.minecraft189.gui.widget.UiCanvas;

/** Entire Raven setting paint path. Measured rows and input stay owned by SettingControl. */
public final class RavenControls implements SettingControl.Painter {
  public static final RavenControls INSTANCE = new RavenControls();

  private RavenControls() {}

  @Override
  public void paint(UiCanvas c, PaintView v) {
    switch (v.setting().metadata().kind()) {
      case BOOLEAN:
        toggle(c, v);
        return;
      case INTEGER:
      case DECIMAL:
        number(c, v);
        return;
      case ENUM:
      case MULTI_SELECT:
        choices(c, v);
        return;
      case RANGE:
        range(c, v);
        return;
      case COLOR:
        color(c, v);
        return;
      default:
        text(c, v);
    }
  }

  private static void lines(
      UiCanvas c, PaintView v, List<String> lines, float x, float y, int color) {
    for (int i = 0; i < lines.size(); i++) c.text(lines.get(i), x, y + i * v.lineHeight(), color);
  }

  private static void label(UiCanvas c, PaintView v, int color) {
    float band = v.headerHeight() - v.valueHeight() - (v.labelLines().size() - 1) * v.lineHeight();
    lines(c, v, v.labelLines(), v.x() + 3, c.font.centeredY(v.y(), band), color);
  }

  private static void checkbox(UiCanvas c, float x, float y, boolean selected, int color) {
    c.outline(x, y, 5, 5, selected ? color : 0xff808080);
    if (selected) c.rect(x + 1, y + 1, 3, 3, color);
  }

  private static void toggle(UiCanvas c, PaintView v) {
    boolean selected = Boolean.TRUE.equals(v.setting().get());
    float band = v.headerHeight() - (v.labelLines().size() - 1) * v.lineHeight();
    lines(
        c,
        v,
        v.labelLines(),
        v.x() + 3,
        c.font.centeredY(v.y(), band),
        selected ? RavenTheme.CHECKED : c.text);
  }

  private static boolean edit(UiCanvas c, PaintView v, String label, float rowY) {
    if (!v.editing(rowY)) return false;
    if (rowY == v.y()) v.drawLabel(c);
    else if (!label.equals("Hex:"))
      c.label(
          label + ": ",
          v.x() + 3,
          c.font.centeredY(rowY, 10),
          Math.max(0, v.editingX() - v.x() - 3),
          0xffffffff);
    return true;
  }

  /** OpenB4's low rounded track and changing hue, with native font metrics. */
  private static void slider(
      UiCanvas c, String name, String value, float x, float y, float width, double fraction) {
    String text = name.isEmpty() ? value : name + ": " + value;
    c.label(text, x + 3, c.font.centeredY(y, 10), width - 6, 0xffffffff);
    int track = 0xff444447;
    RavenTheme.rounded(c, x + 3, y + 11, width - 6, 3, 1.5f, track, track, track);
    float filled = (width - 6) * (float) Math.max(0, Math.min(1, fraction));
    if (filled <= 0) return;
    int fill = java.awt.Color.HSBtoRGB(System.currentTimeMillis() % 11000L / 11000f, .75f, .9f);
    RavenTheme.rounded(c, x + 3, y + 11, filled, 3, 1.5f, fill, fill, fill);
  }

  private static void number(UiCanvas c, PaintView v) {
    if (v.editing(v.y())) {
      if (v.stackedValue()) label(c, v, c.text);
      else
        c.text(
            v.label() + ": ",
            v.x() + 3,
            c.font.centeredY(v.y() + v.headerHeight() - SettingControl.ROW, 10),
            0xffffffff);
      return;
    }
    NumericConstraints n = v.setting().metadata().numeric().get();
    double value = ((Number) v.setting().get()).doubleValue();
    if (v.stackedValue()) label(c, v, c.text);
    slider(
        c,
        v.stackedValue() ? "" : v.label(),
        GuiValues.number(value),
        v.x(),
        v.y() + v.headerHeight() - SettingControl.ROW,
        v.width(),
        n.maximum() == n.minimum() ? 0 : (value - n.minimum()) / (n.maximum() - n.minimum()));
  }

  private static void title(UiCanvas c, PaintView v) {
    label(c, v, c.text);
    c.text(
        v.expanded() ? "-" : "+",
        v.x() + v.width() - 9,
        c.font.centeredY(v.y(), v.headerHeight()),
        c.accent);
  }

  private static void choices(UiCanvas c, PaintView v) {
    title(c, v);
    if (v.stackedValue())
      lines(
          c,
          v,
          v.valueLines(),
          v.x() + 3,
          c.font.centeredY(v.y() + v.headerHeight() - v.valueHeight(), v.lineHeight()),
          c.enabled);
    else
      c.text(
          v.summary(),
          v.x() + v.width() - c.font.width(v.summary()) - 13,
          c.font.centeredY(v.y(), v.headerHeight()),
          c.enabled);
    if (!v.expanded()) return;
    float y = v.y() + v.headerHeight();
    for (int i = 0; i < v.optionCount(); i++) {
      float height = v.optionHeight(i);
      boolean selected = v.optionSelected(i);
      float band = height - (v.optionLines(i).size() - 1) * v.lineHeight();
      checkbox(c, v.x() + 3, y + (height - 5) / 2, selected, c.enabled);
      lines(
          c,
          v,
          v.optionLines(i),
          v.x() + 11,
          c.font.centeredY(y, band),
          selected ? c.enabled : c.text);
      y += height;
    }
  }

  private static void range(UiCanvas c, PaintView v) {
    if (!edit(c, v, v.label(), v.y())) title(c, v);
    if (!v.expanded()) return;
    DoubleRange range = (DoubleRange) v.setting().get();
    NumericConstraints n = v.setting().metadata().numeric().get();
    double span = n.maximum() - n.minimum();
    float y = v.y() + v.headerHeight();
    if (!edit(c, v, "Min", y))
      slider(
          c,
          "Min",
          GuiValues.number(range.lower()),
          v.x(),
          y,
          v.width(),
          span == 0 ? 0 : (range.lower() - n.minimum()) / span);
    if (!edit(c, v, "Max", y + 16))
      slider(
          c,
          "Max",
          GuiValues.number(range.upper()),
          v.x(),
          y + 16,
          v.width(),
          span == 0 ? 0 : (range.upper() - n.minimum()) / span);
  }

  private static void text(UiCanvas c, PaintView v) {
    if (v.editing(v.y())) {
      v.drawTextLabel(c);
      return;
    }
    float band = v.height() - (v.textLines().size() - 1) * v.lineHeight();
    lines(c, v, v.textLines(), v.x() + 3, c.font.centeredY(v.y(), band), c.text);
    c.hairline(v.x() + 3, v.y() + v.height() - 1, v.width() - 6, 0x70516395);
  }

  private static void color(UiCanvas c, PaintView v) {
    ColorValue color = (ColorValue) v.setting().get();
    if (!edit(c, v, v.label(), v.y())) {
      title(c, v);
      float swatchX = v.x() + v.width() - 25;
      float swatchY = v.y() + (v.headerHeight() - 7) / 2;
      c.rect(swatchX, swatchY, 9, 7, color.resolvedArgb(System.currentTimeMillis()));
      c.outline(swatchX, swatchY, 9, 7, 0xffaaaaaa);
    }
    if (!v.expanded()) return;
    float x = v.x(), width = v.width();
    float contentY = v.y() + v.headerHeight() - 16;
    float hue = v.hue();
    float[] hsv = java.awt.Color.RGBtoHSB(color.red(), color.green(), color.blue(), null);
    int hueColor = java.awt.Color.HSBtoRGB(hue, 1, 1);
    c.gradient(x + 3, contentY + 18, width - 6, 44, 0xffffffff, hueColor, hueColor, 0xffffffff);
    c.gradient(x + 3, contentY + 18, width - 6, 44, 0, 0, 0xff000000, 0xff000000);
    c.outline(x + 3, contentY + 18, width - 6, 44, 0xff808080);
    float px = x + 3 + hsv[1] * (width - 6), py = contentY + 18 + (1 - hsv[2]) * 44;
    c.rect(px - 2, py - 2, 4, 4, 0xff000000);
    c.outline(px - 2, py - 2, 4, 4, 0xffffffff);
    for (int i = 0; i < 6; i++)
      c.gradient(
          x + 3 + (width - 6) * i / 6,
          contentY + 65,
          (width - 6) / 6 + .2f,
          9,
          java.awt.Color.HSBtoRGB(i / 6f, 1, 1),
          java.awt.Color.HSBtoRGB((i + 1) / 6f, 1, 1),
          java.awt.Color.HSBtoRGB((i + 1) / 6f, 1, 1),
          java.awt.Color.HSBtoRGB(i / 6f, 1, 1));
    c.outline(x + 3, contentY + 65, width - 6, 9, 0xff808080);
    c.rect(x + 3 + hue * (width - 6), contentY + 64, 1, 11, 0xffffffff);
    int[] channels = {color.red(), color.green(), color.blue(), color.alpha()};
    String[] labels = {"Red", "Green", "Blue", "Alpha"};
    for (int i = 0; i < channels.length; i++) {
      float rowY = contentY + 78 + i * 16;
      if (!edit(c, v, labels[i], rowY))
        slider(c, labels[i], String.valueOf(channels[i]), x, rowY, width, channels[i] / 255.0);
    }
    int rainbowText = color.rainbow() ? RavenTheme.CHECKED : c.text;
    c.label("Rainbow", x + 3, c.font.centeredY(contentY + 142, 16), width - 13, rainbowText);
    checkbox(c, x + width - 8, contentY + 147, color.rainbow(), RavenTheme.CHECKED);
    if (!edit(c, v, "Speed", contentY + 158))
      slider(
          c,
          "Speed",
          GuiValues.number(color.rainbowSpeed()),
          x,
          contentY + 158,
          width,
          (color.rainbowSpeed() - .1) / 9.9);
    if (!edit(c, v, "Hex:", contentY + 176))
      c.label(
          color.toString().toLowerCase(Locale.ROOT),
          x + 3,
          c.font.centeredY(contentY + 176, 12),
          width - 6,
          c.text);
  }
}
