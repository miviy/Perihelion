package perihelion.integration.minecraft189.gui.modern;

import java.util.List;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import perihelion.core.setting.NumericConstraints;
import perihelion.integration.minecraft189.gui.GuiValues;
import perihelion.integration.minecraft189.gui.widget.SettingControl;
import perihelion.integration.minecraft189.gui.widget.UiCanvas;

/** Compact dropdown paint over the existing, measured setting editors. */
public final class ModernControls implements SettingControl.Painter {
  public static final ModernControls INSTANCE = new ModernControls();
  public static final float CHECKBOX_SIZE = 10;

  @Override
  public void paint(UiCanvas c, SettingControl.PaintView v) {
    float x = v.x(), y = v.y(), w = v.width(), h = v.headerHeight();
    switch (v.setting().metadata().kind()) {
      case BOOLEAN:
        label(c, v);
        checkboxRow(c, x + w, y, h, (Boolean) v.setting().get());
        break;
      case INTEGER:
      case DECIMAL:
        NumericConstraints n = v.setting().metadata().numeric().get();
        double value = ((Number) v.setting().get()).doubleValue();
        float f =
            n.maximum() == n.minimum()
                ? 0
                : (float) ((value - n.minimum()) / (n.maximum() - n.minimum()));
        float barY = y + h - 14;
        c.rect(x, barY, w, 14, 0xff1b1a19);
        c.rect(x, barY, w * Math.max(0, Math.min(1, f)), 14, c.selection);
        if (v.editing(y)) {
          c.label(
              v.label(), x + 3, c.font.centeredY(y, 12), Math.max(0, v.editingX() - x - 6), c.text);
        } else {
          if (v.stackedValue()) label(c, v);
          String number = GuiValues.number(value);
          c.label(
              v.stackedValue() ? "" : v.label(),
              x + 3,
              c.font.centeredY(barY, 14),
              w - c.font.width(number) - 9,
              c.text);
          c.text(number, x + w - c.font.width(number) - 3, c.font.centeredY(barY, 14), c.text);
        }
        break;
      case ENUM:
      case MULTI_SELECT:
        label(c, v);
        if (v.stackedValue())
          lines(
              c,
              v.valueLines(),
              x + 3,
              c.font.centeredY(y + h - v.valueHeight(), v.lineHeight()),
              v.lineHeight(),
              c.muted);
        else
          c.text(
              v.summary(), x + w - c.font.width(v.summary()) - 15, c.font.centeredY(y, h), c.muted);
        ModernAssets.arrow(c, x + w - 7, y + h / 2, v.expanded() ? 1 : 0, c.text);
        if (v.expanded()) {
          float row = y + h;
          c.rect(x, row, w, v.height() - h, 0xff191817);
          for (int i = 0; i < v.optionCount(); i++) {
            float oh = v.optionHeight(i);
            boolean selected = v.optionSelected(i);
            if (selected) c.rect(x, row, w, oh, c.selection);
            lines(
                c,
                v.optionLines(i),
                x + 6,
                c.font.centeredY(row, oh - (v.optionLines(i).size() - 1) * v.lineHeight()),
                v.lineHeight(),
                selected ? c.selectedText : c.muted);
            checkboxRow(c, x + w, row, oh, selected);
            row += oh;
          }
        }
        break;
      default:
        v.defaultPaint(c);
    }
  }

  /** Geometry rather than a font character: the tick stays legible with every selected family. */
  public static void checkbox(UiCanvas c, float x, float y, boolean selected) {
    checkbox(c, x, y, CHECKBOX_SIZE, selected);
  }

  public static void checkboxRow(
      UiCanvas c, float right, float top, float height, boolean selected) {
    checkbox(c, right - 4 - CHECKBOX_SIZE, top + (height - CHECKBOX_SIZE) / 2, selected);
  }

  public static void disclosure(UiCanvas c, float x, float y, boolean expanded) {
    ModernAssets.arrow(c, x, y, expanded ? 1 : 0, c.muted);
  }

  public static void disclosure(UiCanvas c, float x, float y, float expanded, int color) {
    ModernAssets.arrow(c, x, y, expanded, color);
  }

  public static void checkbox(UiCanvas c, float x, float y, float logicalSize, boolean selected) {
    float pixels = Math.max(.01f, c.pixelScale);
    x = Math.round(x * pixels) / pixels;
    y = Math.round(y * pixels) / pixels;
    float size = Math.max(4, Math.round(logicalSize * pixels)) / pixels;
    c.rect(x, y, size, size, 0xff171716);
    c.outline(x, y, size, size, 0xff343230);
    if (!selected) return;
    c.texture(false);
    GlStateManager.disableTexture2D();
    int color = 0xffffffff;
    GlStateManager.resetColor();
    GlStateManager.color(
        (color >> 16 & 255) / 255f,
        (color >> 8 & 255) / 255f,
        (color & 255) / 255f,
        (color >>> 24) / 255f);
    float thickness = Math.max(1, Math.round(1.15f * pixels)) / pixels;
    GL11.glBegin(GL11.GL_QUADS);
    segment(x + size * .14f, y + size * .48f, x + size * .40f, y + size * .74f, thickness);
    segment(x + size * .40f, y + size * .74f, x + size * .88f, y + size * .22f, thickness);
    GL11.glEnd();
  }

  private static void segment(float x1, float y1, float x2, float y2, float thickness) {
    float dx = x2 - x1, dy = y2 - y1;
    float length = (float) Math.sqrt(dx * dx + dy * dy);
    float px = -dy / length * thickness / 2, py = dx / length * thickness / 2;
    GL11.glVertex2f(x1 + px, y1 + py);
    GL11.glVertex2f(x1 - px, y1 - py);
    GL11.glVertex2f(x2 - px, y2 - py);
    GL11.glVertex2f(x2 + px, y2 + py);
  }

  private static void label(UiCanvas c, SettingControl.PaintView v) {
    float band = v.headerHeight() - v.valueHeight() - (v.labelLines().size() - 1) * v.lineHeight();
    lines(c, v.labelLines(), v.x() + 3, c.font.centeredY(v.y(), band), v.lineHeight(), c.text);
  }

  private static void lines(
      UiCanvas c, List<String> values, float x, float y, float line, int color) {
    for (int i = 0; i < values.size(); i++) c.text(values.get(i), x, y + i * line, color);
  }
}
