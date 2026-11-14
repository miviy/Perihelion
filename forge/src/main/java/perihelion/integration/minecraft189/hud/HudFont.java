package perihelion.integration.minecraft189.hud;

import java.util.*;
import net.minecraft.client.renderer.GlStateManager;
import perihelion.integration.minecraft189.gui.ClickGuiModule;
import perihelion.integration.minecraft189.gui.font.GuiFonts;
import perihelion.integration.minecraft189.render.RenderState;

/** Small formatted-text adapter over the existing GUI atlas; disposed with its HUD owner. */
final class HudFont implements AutoCloseable {
  private static final float SCALE = 1f;
  private static final int[] COLORS = {
    0x000000, 0x0000aa, 0x00aa00, 0x00aaaa, 0xaa0000, 0xaa00aa, 0xffaa00, 0xaaaaaa, 0x555555,
    0x5555ff, 0x55ff55, 0x55ffff, 0xff5555, 0xff55ff, 0xffff55, 0xffffff
  };
  private final GuiFonts fonts;
  private final boolean ownsFonts;

  HudFont() {
    fonts = new GuiFonts();
    ownsFonts = true;
  }

  HudFont(HudFont owner) {
    fonts = owner.fonts;
    ownsFonts = false;
  }

  private final RenderState creation = new RenderState();
  private GuiFonts.Face face, boldFace;
  private ClickGuiModule.Font choice = ClickGuiModule.Font.SANS;

  private boolean bold, modern;
  private float rasterPixels = 1;

  boolean choice(ClickGuiModule.Font next, boolean nextBold) {
    if (choice == next && bold == nextBold) return false;
    choice = next;
    bold = nextBold;
    face = boldFace = null;
    return true;
  }

  /** Configure before measuring; a resolution change can alter hinted glyph advances. */
  boolean raster(float nextPixels, boolean nextModern) {
    if (!Float.isFinite(nextPixels) || nextPixels <= 0)
      throw new IllegalArgumentException("HUD pixel scale must be finite and positive");
    boolean changed = modern != nextModern || rasterPixels != nextPixels;
    modern = nextModern;
    rasterPixels = nextPixels;
    if (changed) face = boldFace = null;
    return changed;
  }

  private boolean closed;
  private final Map<String, List<Run>> runs =
      new LinkedHashMap<String, List<Run>>(64, .75f, true) {
        protected boolean removeEldestEntry(Map.Entry<String, List<Run>> entry) {
          return size() > 256;
        }
      };

  private GuiFonts.Face face() {
    if (closed) throw new IllegalStateException("HUD font is closed");
    if (face == null)
      try (RenderState ignored = creation.open()) {
        face =
            choice == ClickGuiModule.Font.MINECRAFT
                ? fonts.minecraft(bold, rasterPixels)
                : modern
                    ? fonts.pixelFace(choice, bold, 1.35f, rasterPixels)
                    : fonts.get(choice, bold);
      }
    return face;
  }

  private GuiFonts.Face face(Run run) {
    if (!modern || bold || !run.bold) return face();
    if (boldFace == null)
      try (RenderState ignored = creation.open()) {
        boldFace = fonts.pixelFace(choice, true, 1.35f, rasterPixels);
      }
    return boldFace;
  }

  int width(String text) {
    if (choice == ClickGuiModule.Font.MINECRAFT) {
      return (int) Math.ceil(face().width(text));
    }
    float width = 0;
    for (Run run : parts(text))
      width += face(run).width(run.text) + (!modern && run.bold && !bold ? .35f : 0);
    return (int) Math.ceil(width * SCALE);
  }

  int height() {
    return (int) Math.ceil((modern ? face().inkHeight() + 3 : face().height()) * SCALE);
  }

  float centeredY(float top, float height) {
    return face().centeredY(top / SCALE, height / SCALE) * SCALE;
  }

  String trim(String value, int width) {
    if (width(value) <= width) return value;
    StringBuilder out = new StringBuilder();
    int last = 0;
    for (int i = 0; i < value.length(); ) {
      if (value.charAt(i) == '\u00a7' && i + 1 < value.length()) {
        out.append(value, i, i + 2);
        i += 2;
        continue;
      }
      int end = i + Character.charCount(value.codePointAt(i));
      out.append(value, i, end);
      i = end;
      if (width(out + "...") > width) return out.substring(0, last) + "...";
      last = out.length();
    }
    return out.toString();
  }

  void draw(String text, float x, float y, int color, boolean shadow, float pixels) {
    if ((color >>> 24) == 0) return;
    face();
    x = Math.round(x * pixels) / pixels;
    y = Math.round(y * pixels) / pixels;
    if (choice == ClickGuiModule.Font.MINECRAFT) {
      face().draw(text, x, y, color, shadow, null);
      return;
    }
    GlStateManager.pushMatrix();
    try {
      GlStateManager.translate(x, y, 0);
      GlStateManager.scale(SCALE, SCALE, 1);
      float cursor = 0;
      for (Run run : parts(text)) {
        GuiFonts.Face font = face(run);
        int ink = run.color < 0 ? color : (color & 0xff000000) | run.color;
        float offset = modern ? 1 / pixels : .45f;
        if (shadow) font.draw(run.text, cursor + offset, offset, ((color >>> 24) * 2 / 3) << 24);
        font.draw(run.text, cursor, 0, ink);
        if (!modern && run.bold && !bold) font.draw(run.text, cursor + .35f, 0, ink);
        cursor += font.width(run.text) + (!modern && run.bold && !bold ? .35f : 0);
      }
    } finally {
      GlStateManager.popMatrix();
    }
  }

  private List<Run> parts(String text) {
    text = perihelion.integration.minecraft189.HookBridge.displayText(text);
    List<Run> cached = runs.get(text);
    if (cached != null) return cached;
    List<Run> result = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    int color = -1;
    boolean bold = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '\u00a7' && i + 1 < text.length()) {
        if (current.length() > 0) {
          result.add(new Run(current.toString(), color, bold));
          current.setLength(0);
        }
        char code = Character.toLowerCase(text.charAt(++i));
        int index = "0123456789abcdef".indexOf(code);
        if (index >= 0) {
          color = COLORS[index];
          bold = false;
        } else if (code == 'r') {
          color = -1;
          bold = false;
        } else if (code == 'l') bold = true;
      } else current.append(c);
    }
    if (current.length() > 0) result.add(new Run(current.toString(), color, bold));
    runs.put(text, result);
    return result;
  }

  @Override
  public void close() {
    if (!closed) {
      closed = true;
      if (ownsFonts) fonts.close();
      face = boldFace = null;
      runs.clear();
    }
  }

  private static final class Run {
    final String text;
    final int color;
    final boolean bold;

    Run(String text, int color, boolean bold) {
      this.text = text;
      this.color = color;
      this.bold = bold;
    }
  }
}
