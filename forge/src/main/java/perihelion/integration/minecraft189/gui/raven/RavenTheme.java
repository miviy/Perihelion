package perihelion.integration.minecraft189.gui.raven;

import java.util.List;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import perihelion.integration.minecraft189.gui.ClickGuiModule;
import perihelion.integration.minecraft189.gui.font.GuiFonts;
import perihelion.integration.minecraft189.gui.widget.UiCanvas;

/** Raven's own Minecraft palette, rounded category frames and utility chrome. */
public final class RavenTheme {
  public static final int CLOSED_LEFT = 0xff516395, CLOSED_RIGHT = 0xff614385;
  public static final int ENABLED = 0xff189aff, DISABLED = 0xffc0c0c0;
  public static final int CHECKED = 0xff14ff00;
  private static final float HEADER = 13;

  private RavenTheme() {}

  public static void configure(
      UiCanvas c, GuiFonts fonts, ClickGuiModule preferences, float pixels) {
    long now = System.currentTimeMillis();
    boolean custom = preferences != null && preferences.customTextStyle.get();
    boolean bold = custom && preferences.textWeight.get() == ClickGuiModule.TextWeight.BOLD;
    c.classic = true;
    c.textShadow = true;
    c.settingPainter = RavenControls.INSTANCE;
    c.pixelScale = pixels;
    c.font = fonts.minecraft(bold, pixels);
    c.sectionFont = fonts.minecraft(bold || custom && preferences.boldSections.get(), pixels);
    c.text = custom ? preferences.text.get().resolvedArgb(now) : DISABLED;
    c.enabled = custom ? preferences.enabled.get().resolvedArgb(now) : ENABLED;
    c.sectionText = custom ? preferences.sectionText.get().resolvedArgb(now) : 0xffdcdcdc;
    c.selectedText = custom ? preferences.selectionText.get().resolvedArgb(now) : 0xffffffff;
    c.accent = custom ? preferences.accent.get().resolvedArgb(now) : ENABLED;
    c.muted = custom ? preferences.mutedText.get().resolvedArgb(now) : 0xff999999;
    c.panel = (preferences == null ? 110 : preferences.ravenPanelAlpha.get()) << 24;
    c.header = c.panel;
    c.headerBottom = c.panel;
    c.selection = 0;
    c.hover = 0x5a000000;
    c.activeButton = 0;
  }

  /** Open/hovered categories have the donor's two offset rainbow outline colors. */
  public static void frame(UiCanvas c, float x, float y, float w, float h, boolean active) {
    rounded(
        c,
        x,
        y,
        w,
        h,
        5,
        c.panel,
        active ? rainbow(0, 130) : CLOSED_LEFT,
        active ? rainbow(700, 130) : CLOSED_RIGHT);
  }

  public static void header(UiCanvas c, String title, float x, float y, float width) {
    boolean shadow = c.textShadow;
    try {
      c.textShadow = false;
      c.section(
          title,
          x + 3,
          (c.sectionFont == null ? c.font : c.sectionFont).centeredY(y, HEADER),
          width - 6,
          true);
    } finally {
      c.textShadow = shadow;
    }
  }

  public static void button(
      UiCanvas c,
      String title,
      float x,
      float y,
      float w,
      float h,
      boolean active,
      boolean enabled,
      boolean hover) {
    boolean highlighted = enabled && (active || hover);
    rounded(
        c,
        x,
        y,
        w,
        h,
        3,
        highlighted ? 0x98000000 : c.panel,
        highlighted ? rainbow(0, 170) : CLOSED_LEFT,
        highlighted ? rainbow(700, 170) : CLOSED_RIGHT);
    c.center(
        title,
        x + 4,
        c.font.centeredY(y, h),
        w - 8,
        !enabled ? c.muted : active ? c.enabled : c.text);
  }

  public static void tooltip(
      UiCanvas c, String text, float mx, float my, float viewportWidth, float viewportHeight) {
    if (text == null || text.isEmpty()) return;
    float available = Math.max(1, Math.min(200, viewportWidth - 14));
    List<String> lines = UiCanvas.wrap(c.font, text, available);
    float width = 0;
    for (String line : lines) width = Math.max(width, c.font.width(line));
    width += 8;
    float lineHeight = Math.max(10, c.font.height() + 2);
    float height = lines.size() * lineHeight + 6;
    float x = Math.max(2, Math.min(mx + 9, viewportWidth - width - 2));
    float y = Math.max(2, Math.min(my + 9, viewportHeight - height - 2));
    rounded(c, x, y, width, height, 3, 0xe6000000, CLOSED_LEFT, CLOSED_RIGHT);
    for (int i = 0; i < lines.size(); i++)
      c.text(lines.get(i), x + 4, y + 3 + i * lineHeight, c.text);
  }

  static int rainbow(long offset, int alpha) {
    long phase = Math.floorMod(System.currentTimeMillis() + offset, 7500L);
    return alpha << 24 | java.awt.Color.HSBtoRGB(phase / 7500f, .75f, 1) & 0xffffff;
  }

  /** Native geometry; Raven owns its silhouette rather than decorating an Adjust rectangle. */
  static void rounded(
      UiCanvas c, float x, float y, float w, float h, float radius, int fill, int left, int right) {
    if (w <= 0 || h <= 0) return;
    float r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
    float border = Math.min(1 / Math.max(.01f, c.pixelScale), Math.min(w, h) / 2);
    GlStateManager.disableTexture2D();
    GlStateManager.shadeModel(GL11.GL_SMOOTH);
    GL11.glBegin(GL11.GL_TRIANGLE_FAN);
    try {
      color(fill);
      GL11.glVertex2f(x + w / 2, y + h / 2);
      for (int i = 0; i <= 28; i++) {
        float[] point = cornerPoint(i % 28, x, y, w, h, r);
        GL11.glVertex2f(point[0], point[1]);
      }
    } finally {
      GL11.glEnd();
    }
    GL11.glBegin(GL11.GL_QUAD_STRIP);
    try {
      for (int i = 0; i <= 28; i++) {
        float[] outer = cornerPoint(i % 28, x, y, w, h, r);
        float[] inner =
            cornerPoint(
                i % 28,
                x + border,
                y + border,
                w - border * 2,
                h - border * 2,
                Math.max(0, r - border));
        color(blend(left, right, (outer[0] - x) / w));
        GL11.glVertex2f(outer[0], outer[1]);
        GL11.glVertex2f(inner[0], inner[1]);
      }
    } finally {
      GL11.glEnd();
      GlStateManager.shadeModel(GL11.GL_FLAT);
    }
  }

  private static float[] cornerPoint(int index, float x, float y, float w, float h, float r) {
    int corner = index / 7;
    double angle = Math.toRadians(-90 + corner * 90 + index % 7 * 15);
    float cx = corner == 0 || corner == 1 ? x + w - r : x + r;
    float cy = corner == 0 || corner == 3 ? y + r : y + h - r;
    return new float[] {cx + (float) Math.cos(angle) * r, cy + (float) Math.sin(angle) * r};
  }

  private static int blend(int left, int right, float fraction) {
    float f = Math.max(0, Math.min(1, fraction));
    int result = 0;
    for (int shift = 0; shift <= 24; shift += 8) {
      int a = left >>> shift & 255, b = right >>> shift & 255;
      result |= Math.round(a + (b - a) * f) << shift;
    }
    return result;
  }

  private static void color(int color) {
    GL11.glColor4f(
        (color >> 16 & 255) / 255f,
        (color >> 8 & 255) / 255f,
        (color & 255) / 255f,
        (color >>> 24) / 255f);
  }
}
