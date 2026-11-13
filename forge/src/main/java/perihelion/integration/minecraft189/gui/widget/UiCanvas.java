package perihelion.integration.minecraft189.gui.widget;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import perihelion.integration.minecraft189.gui.font.GuiFonts;
import perihelion.integration.minecraft189.gui.theme.ThemePalette;

/**
 * Primitive batch drawing inside the caller's one RenderState scope. Coordinates are
 * frontend-local.
 */
public final class UiCanvas {
  public GuiFonts.Face font, sectionFont;
  public int text, accent, enabled;
  public int sectionText = 0xffe5e5e5, selectedText = 0xffe5e5e5;
  public int panel, header, headerBottom, muted, hover, selection, activeButton;
  public float pixelScale = 1;
  public float cornerRadius = 4;
  public ThemePalette theme = ThemePalette.DEFAULT;

  public int accentAt(float position) {
    return modern ? theme.accent(position) : accent;
  }

  public int rowAccentAt(float position) {
    return modern ? theme.fill(position) : activeButton;
  }

  /** Optional screen-owned content mask; no shader state is retained by this canvas. */
  public interface TextureMode {
    void texture(boolean textured);
  }

  public TextureMode clip;

  public void texture(boolean textured) {
    if (clip != null) clip.texture(textured);
  }

  /** Frontend metadata; primitive drawing never chooses a client style. */
  public boolean classic;

  public boolean modern;

  public boolean textShadow;
  public SettingControl.Painter settingPainter;

  public void outline(float x, float y, float w, float h, int color) {
    hairline(x, y, w, color);
    hairline(x, y + h - 1 / Math.max(.01f, pixelScale), w, color);
    verticalHairline(x, y, h, color);
    verticalHairline(x + w - 1 / Math.max(.01f, pixelScale), y, h, color);
  }

  public void rect(float x, float y, float w, float h, int color) {
    gradient(x, y, w, h, color, color, color, color);
  }

  /** Draw a single framebuffer-pixel line, even at fractional GUI scales. */
  public void hairline(float x, float y, float width, int color) {
    float scale = Math.max(0.01f, pixelScale);
    float left = Math.round(x * scale) / scale, right = Math.round((x + width) * scale) / scale;
    float top = Math.round(y * scale) / scale;
    rect(left, top, Math.max(1 / scale, right - left), 1 / scale, color);
  }

  /** One framebuffer pixel wide, independent of GUI and menu scale. */
  public void verticalHairline(float x, float y, float height, int color) {
    float scale = Math.max(0.01f, pixelScale);
    float left = Math.round(x * scale) / scale, top = Math.round(y * scale) / scale;
    float bottom = Math.round((y + height) * scale) / scale;
    rect(left, top, 1 / scale, Math.max(1 / scale, bottom - top), color);
  }

  public void gradient(float x, float y, float w, float h, int tl, int tr, int br, int bl) {
    texture(false);
    GlStateManager.disableTexture2D();
    GlStateManager.shadeModel(GL11.GL_SMOOTH);
    GL11.glBegin(GL11.GL_QUADS);
    try {
      vertex(x, y, tl);
      vertex(x, y + h, bl);
      vertex(x + w, y + h, br);
      vertex(x + w, y, tr);
    } finally {
      GL11.glEnd();
      GlStateManager.shadeModel(GL11.GL_FLAT);
    }
  }

  private static void vertex(float x, float y, int c) {
    GL11.glColor4f(
        (c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f, (c >>> 24) / 255f);
    GL11.glVertex2f(x, y);
  }

  public void text(String label, float x, float y, int color) {
    texture(true);
    float pixels = Math.max(.01f, pixelScale);
    if (textShadow && (color >>> 24) != 0)
      font.draw(
          label,
          Math.round(x * pixels) / pixels + 1,
          Math.round(y * pixels) / pixels + 1,
          color & 0xff000000 | (color & 0xfcfcfc) >> 2,
          clip);
    font.draw(label, Math.round(x * pixels) / pixels, Math.round(y * pixels) / pixels, color, clip);
  }

  public String fit(String label, float width) {
    if (width <= 0) return "";
    if (font.width(label) <= width) return label;
    String tail = "...";
    if (font.width(tail) > width) return "";
    int end = 0;
    while (end < label.length()) {
      int next = end + Character.charCount(label.codePointAt(end));
      if (font.width(label.substring(0, next) + tail) > width) break;
      end = next;
    }
    return label.substring(0, end) + tail;
  }

  /** Complete text in measured lines; never removes characters or splits codepoints. */
  public static List<String> wrap(GuiFonts.Face face, String value, float width) {
    List<String> lines = new ArrayList<>();
    int start = 0;
    while (start < value.length()) {
      int end = start, wordEnd = start;
      while (end < value.length()) {
        int next = end + Character.charCount(value.codePointAt(end));
        if (end > start && face.width(value.substring(start, next)) > width) break;
        if (Character.isWhitespace(value.codePointAt(end))) wordEnd = next;
        end = next;
      }
      if (end < value.length() && wordEnd > start) end = wordEnd;
      lines.add(value.substring(start, end));
      start = end;
    }
    if (lines.isEmpty()) lines.add("");
    return lines;
  }

  public void center(String label, float x, float y, float width, int color) {
    String fitted = fit(label, width);
    text(fitted, x + (width - font.width(fitted)) / 2, y, color);
  }

  public void section(String label, float x, float y, float width, boolean centered) {
    GuiFonts.Face previous = font;
    try {
      if (sectionFont != null) font = sectionFont;
      if (centered) center(label, x, y, width, sectionText);
      else label(label, x, y, width, sectionText);
    } finally {
      font = previous;
    }
  }

  public void label(String label, float x, float y, float width, int color) {
    if (width <= 0 || label.isEmpty()) return;
    text(fit(label, width), x, y, color);
  }

  public void slider(String name, String value, float x, float y, float width, double fraction) {
    float valueWidth = font.width(value);
    float textY = font.centeredY(y, 12);
    label(name, x + 3, textY, width - valueWidth - 11, text);
    label(value, x + width - valueWidth - 3, textY, valueWidth, enabled);
    hairline(x + 3, y + 13, width - 6, 0xff484b4e);
    float filled = (width - 6) * (float) Math.max(0, Math.min(1, fraction));
    if (filled > 0) {
      hairline(x + 3, y + 13, filled, accent);
    }
  }

  public static boolean inside(float mx, float my, float x, float y, float w, float h) {
    return mx >= x && mx < x + w && my >= y && my < y + h;
  }
}
