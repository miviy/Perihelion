package perihelion.integration.minecraft189.gui.font;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GLContext;
import perihelion.integration.minecraft189.gui.ClickGuiModule;
import perihelion.integration.minecraft189.gui.widget.UiCanvas;
import perihelion.integration.minecraft189.render.*;

/**
 * Runtime-owned 4x atlases. Editor/HUD faces use logical JRE fonts; menu labels share the logo's
 * original vector outlines.
 */
public final class GuiFonts implements AutoCloseable {
  private static final int CELL = 64, ATLAS = 1024;
  private static final float GLYPH_SCALE = 0.25f;
  // Downsampling bitmap glyphs to 3/4 drops rows and gives strokes uneven widths.
  public static final float MINECRAFT_SCALE = 1f;

  public interface Face {
    float width(String text);

    float height();

    /** Visible cap height for compact HUD leading; full line metrics remain available. */
    default float inkHeight() {
      return height();
    }

    default float centeredY(float top, float height) {
      return top + (height - height()) / 2;
    }

    void draw(String text, float x, float y, int color);

    default void draw(String text, float x, float y, int color, UiCanvas.TextureMode textureMode) {
      if (textureMode != null) textureMode.texture(true);
      draw(text, x, y, color);
    }

    default void draw(
        String text,
        float x,
        float y,
        int color,
        boolean shadow,
        UiCanvas.TextureMode textureMode) {
      if (shadow && (color >>> 24) != 0)
        draw(text, x + 1, y + 1, color & 0xff000000 | (color & 0xfcfcfc) >> 2, textureMode);
      draw(text, x, y, color, textureMode);
    }

    default void drawCentered(String text, float x, float y, float width, float height, int color) {
      draw(text, x + (width - width(text)) / 2, centeredY(y, height), color);
    }
  }

  private final HudText vanilla = new HudText();
  private final EnumMap<ClickGuiModule.Font, Atlas> atlases =
      new EnumMap<>(ClickGuiModule.Font.class);
  private final EnumMap<ClickGuiModule.Font, Atlas> boldAtlases =
      new EnumMap<>(ClickGuiModule.Font.class);
  private final Face minecraft = new NativeFace(false);
  private final Face minecraftBold = new NativeFace(true);
  private final LinkedHashMap<Long, Face> nativeFaces =
      new LinkedHashMap<Long, Face>(32, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Face> eldest) {
          return size() > 32;
        }
      };
  private Atlas menuFace;
  private final LinkedHashMap<PixelKey, PixelFace> pixelFaces = new LinkedHashMap<>(8, .75f, true);
  private final LinkedHashMap<String, List<FormattedFontRuns.Run>> formattedRuns =
      new LinkedHashMap<String, List<FormattedFontRuns.Run>>(64, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<FormattedFontRuns.Run>> eldest) {
          return size() > 256;
        }
      };
  private static final int MAX_PIXEL_ATLASES = 8, MAX_PIXEL_FACES = 32;
  private static final long MAX_PIXEL_ATLAS_BYTES = 96L * 1024 * 1024;
  private final LinkedHashMap<RasterKey, PixelAtlas> pixelAtlases =
      new LinkedHashMap<>(8, .75f, true);
  private final LinkedHashMap<RasterKey, PixelAtlas> pixelMetrics =
      new LinkedHashMap<>(32, .75f, true);
  private long residentPixelBytes;
  private int pixelTextureLimit;
  private boolean pixelNpot;

  /** Menu labels share the logo's geometry without changing selected HUD/editor faces. */
  public Face menu() {
    if (menuFace == null) menuFace = new Atlas(ClickGuiModule.Font.SANS, false, true);
    return menuFace;
  }

  /** Keep the chosen native bold weight across vanilla color/reset codes as well. */
  public static String forceBold(String text) {
    StringBuilder result = new StringBuilder(text.length() + 2).append("\u00a7l");
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      result.append(c);
      if (c == '\u00a7' && i + 1 < text.length()) {
        char code = text.charAt(++i);
        result.append(code);
        if ("0123456789abcdefr".indexOf(Character.toLowerCase(code)) >= 0) result.append("\u00a7l");
      }
    }
    return result.toString();
  }

  private final class NativeFace implements Face {
    private final boolean bold;
    private final float scale, pixels;

    NativeFace(boolean bold) {
      this(bold, 1);
    }

    NativeFace(boolean bold, float pixels) {
      this.bold = bold;
      this.pixels = pixels;
      scale = minecraftScale(pixels);
    }

    public float width(String text) {
      return vanilla.width(bold ? forceBold(text) : text) * scale;
    }

    public float height() {
      return vanilla.height() * scale;
    }

    public float inkHeight() {
      return (vanilla.height() - 2) * scale;
    }

    public float centeredY(float top, float height) {
      // Vanilla's line height includes two empty rows below its visible glyphs.
      return top + (height - inkHeight()) / 2;
    }

    public void draw(String text, float x, float y, int color) {
      draw(text, x, y, color, false, null);
    }

    @Override
    public void draw(
        String text,
        float x,
        float y,
        int color,
        boolean shadow,
        UiCanvas.TextureMode textureMode) {
      if (textureMode != null) textureMode.texture(true);
      GlStateManager.pushMatrix();
      try {
        GlStateManager.translate(
            Math.round(x * pixels) / pixels, Math.round(y * pixels) / pixels, 0);
        GlStateManager.scale(scale, scale, 1);
        vanilla.drawInScope(bold ? forceBold(text) : text, 0, 0, color, shadow);
      } finally {
        GlStateManager.popMatrix();
      }
    }
  }

  /** Keep each bitmap texel equally wide at fractional menu/HUD scales. */
  public static float minecraftScale(float pixels) {
    if (!Float.isFinite(pixels) || pixels <= 0)
      throw new IllegalArgumentException("Minecraft pixel scale must be finite and positive");
    // Below one screen pixel, retain compact layouts rather than enlarging text past its rows.
    // Prefer the smaller size on exact half steps, keeping compact half-density HUD rows compact.
    return pixels < 1 ? 1 : (float) Math.ceil(pixels - .5f) / pixels;
  }

  public Face minecraft(boolean bold, float pixels) {
    float scale = minecraftScale(pixels);
    if (scale == 1 && pixels == 1) return bold ? minecraftBold : minecraft;
    long key = ((long) Float.floatToIntBits(pixels) << 1) | (bold ? 1 : 0);
    Face face = nativeFaces.get(key);
    if (face == null) {
      face = new NativeFace(bold, pixels);
      nativeFaces.put(key, face);
    }
    return face;
  }

  public Face get(ClickGuiModule.Font choice) {
    return get(choice, false);
  }

  public Face get(ClickGuiModule.Font choice, boolean bold) {
    if (choice == ClickGuiModule.Font.MINECRAFT) return bold ? minecraftBold : minecraft;
    EnumMap<ClickGuiModule.Font, Atlas> cache = bold ? boldAtlases : atlases;
    Atlas face = cache.get(choice);
    if (face == null) {
      face = new Atlas(choice, bold);
      cache.put(choice, face);
    }
    return face;
  }

  /**
   * New UI text rasterized once at its final framebuffer size. Call inside a RenderState scope;
   * pixels is the effective framebuffer pixels per local UI unit, including the menu transform. The
   * returned face already includes logicalSizeFactor, so do not wrap it in MenuFontScale.
   */
  public Face pixelFace(
      ClickGuiModule.Font choice, boolean bold, float logicalSizeFactor, float pixels) {
    if (choice == ClickGuiModule.Font.MINECRAFT) return minecraft(bold, pixels);
    int fontPixels = Math.min(PixelFontRaster.fontPixels(logicalSizeFactor, pixels), rasterLimit());
    PixelKey key = new PixelKey(choice, bold, false, fontPixels, pixels, logicalSizeFactor);
    return pixelFace(key);
  }

  private PixelFace pixelFace(PixelKey key) {
    PixelFace face = pixelFaces.get(key);
    if (face == null) face = new PixelFace(key, false);
    face.resident();
    return face;
  }

  private int rasterLimit() {
    if (pixelTextureLimit == 0) {
      pixelTextureLimit =
          Math.min(PixelFontRaster.MAX_ATLAS_EDGE, GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE));
      if (pixelTextureLimit < 128) throw new IllegalStateException("Unsupported GUI texture size");
      pixelNpot =
          GLContext.getCapabilities().OpenGL20
              || GLContext.getCapabilities().GL_ARB_texture_non_power_of_two;
    }
    return Math.max(8, Math.min(PixelFontRaster.MAX_RASTER_PIXELS, pixelTextureLimit / 14));
  }

  private PixelAtlas pixelAtlas(RasterKey key) {
    PixelAtlas atlas = pixelAtlases.get(key);
    if (atlas == null) atlas = pixelMetrics.get(key);
    if (atlas == null) atlas = new PixelAtlas(key);
    pixelMetrics.put(key, atlas);
    while (pixelMetrics.size() > MAX_PIXEL_FACES) {
      Iterator<RasterKey> iterator = pixelMetrics.keySet().iterator();
      iterator.next();
      iterator.remove();
    }
    return atlas;
  }

  private void reservePixelBytes(PixelAtlas incoming, long bytes) {
    while (pixelAtlases.size() > MAX_PIXEL_ATLASES
        || residentPixelBytes + bytes > MAX_PIXEL_ATLAS_BYTES) {
      Iterator<Map.Entry<RasterKey, PixelAtlas>> iterator = pixelAtlases.entrySet().iterator();
      PixelAtlas evicted = null;
      while (iterator.hasNext()) {
        PixelAtlas candidate = iterator.next().getValue();
        if (candidate == incoming) continue;
        evicted = candidate;
        iterator.remove();
        break;
      }
      if (evicted == null) throw new IllegalStateException("Font atlas exceeds GPU cache budget");
      evicted.close();
    }
  }

  private List<FormattedFontRuns.Run> formatted(String text) {
    List<FormattedFontRuns.Run> runs = formattedRuns.get(text);
    if (runs == null) {
      runs = FormattedFontRuns.parse(text);
      formattedRuns.put(text, runs);
    }
    return runs;
  }

  private static final class PixelKey {
    final ClickGuiModule.Font choice;
    final boolean bold, italic;
    final int size;
    final float pixels, factor;

    PixelKey(
        ClickGuiModule.Font choice,
        boolean bold,
        boolean italic,
        int size,
        float pixels,
        float factor) {
      this.choice = choice;
      this.bold = bold;
      this.italic = italic;
      this.size = size;
      this.pixels = pixels;
      this.factor = factor;
    }

    @Override
    public int hashCode() {
      int hash = choice.hashCode();
      hash = 31 * hash + (bold ? 1 : 0);
      hash = 31 * hash + (italic ? 1 : 0);
      hash = 31 * hash + size;
      hash = 31 * hash + Float.floatToIntBits(pixels);
      return 31 * hash + Float.floatToIntBits(factor);
    }

    @Override
    public boolean equals(Object value) {
      if (!(value instanceof PixelKey)) return false;
      PixelKey other = (PixelKey) value;
      return choice == other.choice
          && bold == other.bold
          && italic == other.italic
          && size == other.size
          && Float.floatToIntBits(pixels) == Float.floatToIntBits(other.pixels)
          && Float.floatToIntBits(factor) == Float.floatToIntBits(other.factor);
    }
  }

  /** Minecraft formatting data without choosing a font renderer or changing the text's family. */
  static final class FormattedFontRuns {
    private static final String CODES = "0123456789abcdefklmnor";
    private static final int[] COLORS = {
      0x000000, 0x0000aa, 0x00aa00, 0x00aaaa, 0xaa0000, 0xaa00aa, 0xffaa00, 0xaaaaaa,
      0x555555, 0x5555ff, 0x55ff55, 0x55ffff, 0xff5555, 0xff55ff, 0xffff55, 0xffffff
    };

    static final class Run {
      final String text;
      final int color;
      final boolean bold, italic, underline, strike, obfuscated;

      Run(
          String text,
          int color,
          boolean bold,
          boolean italic,
          boolean underline,
          boolean strike,
          boolean obfuscated) {
        this.text = text;
        this.color = color;
        this.bold = bold;
        this.italic = italic;
        this.underline = underline;
        this.strike = strike;
        this.obfuscated = obfuscated;
      }
    }

    static List<Run> parse(String text) {
      if (text.isEmpty()) return Collections.emptyList();
      List<Run> runs = new ArrayList<>();
      StringBuilder current = new StringBuilder();
      int color = -1;
      boolean bold = false, italic = false, underline = false, strike = false, obfuscated = false;
      for (int i = 0; i < text.length(); i++) {
        char c = text.charAt(i);
        if (c != '\u00a7' || i + 1 == text.length()) {
          current.append(c);
          continue;
        }
        if (current.length() > 0) {
          runs.add(new Run(current.toString(), color, bold, italic, underline, strike, obfuscated));
          current.setLength(0);
        }
        int code = CODES.indexOf(Character.toLowerCase(text.charAt(++i)));
        if (code < 16) {
          color = COLORS[code < 0 ? 15 : code];
          bold = italic = underline = strike = obfuscated = false;
        } else if (code == 16) obfuscated = true;
        else if (code == 17) bold = true;
        else if (code == 18) strike = true;
        else if (code == 19) underline = true;
        else if (code == 20) italic = true;
        else {
          color = -1;
          bold = italic = underline = strike = obfuscated = false;
        }
      }
      if (current.length() > 0)
        runs.add(new Run(current.toString(), color, bold, italic, underline, strike, obfuscated));
      return Collections.unmodifiableList(runs);
    }

    private FormattedFontRuns() {}
  }

  /** Raster identity excludes layout scale, so resizing reuses a still-correct glyph texture. */
  private static final class RasterKey {
    final ClickGuiModule.Font choice;
    final boolean bold, italic;
    final int size;

    RasterKey(PixelKey key) {
      choice = key.choice;
      bold = key.bold;
      italic = key.italic;
      size = key.size;
    }

    @Override
    public int hashCode() {
      return ((choice.hashCode() * 31 + (bold ? 1 : 0)) * 31 + (italic ? 1 : 0)) * 31 + size;
    }

    @Override
    public boolean equals(Object value) {
      if (!(value instanceof RasterKey)) return false;
      RasterKey other = (RasterKey) value;
      return choice == other.choice
          && bold == other.bold
          && italic == other.italic
          && size == other.size;
    }
  }

  private final class PixelAtlas {
    final RasterKey key;
    final int[] advances, widths, heights, glyphX, glyphY, offsetX, offsetY;
    final int atlasWidth, atlasHeight, height, ascent, inkTop, inkHeight, padding;
    int texture, textureWidth, textureHeight;
    long textureBytes;

    PixelAtlas(RasterKey key) {
      this.key = key;
      try (PixelFontRaster raster = raster(false)) {
        advances = raster.advances;
        widths = raster.widths;
        heights = raster.heights;
        glyphX = raster.glyphX;
        glyphY = raster.glyphY;
        offsetX = raster.offsetX;
        offsetY = raster.offsetY;
        atlasWidth = raster.atlasWidth;
        atlasHeight = raster.atlasHeight;
        height = raster.height;
        ascent = raster.ascent;
        inkTop = raster.inkTop;
        inkHeight = raster.inkHeight;
        padding = raster.padding;
      }
    }

    private PixelFontRaster raster(boolean paint) {
      Font font;
      try {
        font = BundledFonts.load(key.choice, key.bold, key.size);
      } catch (IllegalStateException failure) {
        org.apache.logging.log4j.LogManager.getLogger("Perihelion")
            .warn("GUI font unavailable: {}; using Sans", key.choice, failure);
        font = new Font(Font.SANS_SERIF, key.bold ? Font.BOLD : Font.PLAIN, key.size);
      }
      return new PixelFontRaster(
          key.italic ? font.deriveFont(font.getStyle() | Font.ITALIC) : font, paint);
    }

    /**
     * Old face references remain usable after cache eviction, without duplicate resident atlases.
     */
    PixelAtlas resident() {
      PixelAtlas cached = pixelAtlases.get(key);
      if (cached != null && cached.texture != 0) return cached;
      if (cached != null && cached != this) cached.close();
      pixelAtlases.put(key, this);
      if (texture != 0) return this;
      rasterLimit();
      textureWidth = pixelNpot ? atlasWidth : powerOfTwo(atlasWidth);
      textureHeight = pixelNpot ? atlasHeight : powerOfTwo(atlasHeight);
      if (textureWidth > pixelTextureLimit || textureHeight > pixelTextureLimit) {
        pixelAtlases.remove(key, this);
        throw new IllegalStateException("GUI font exceeds the hardware texture limit");
      }
      textureBytes = (long) textureWidth * textureHeight * 4;
      reservePixelBytes(this, textureBytes);
      try (PixelFontRaster raster = raster(true)) {
        upload(raster);
      } catch (RuntimeException | Error failure) {
        pixelAtlases.remove(key, this);
        throw failure;
      }
      return this;
    }

    private int powerOfTwo(int size) {
      int value = 1;
      while (value < size) value *= 2;
      return value;
    }

    private void upload(PixelFontRaster raster) {
      int allocated = TextureUtil.glGenTextures();
      if (allocated == 0) throw new IllegalStateException("Cannot allocate pixel font atlas");
      try {
        TextureUtil.allocateTexture(allocated, textureWidth, textureHeight);
        TextureUtil.uploadTexture(
            allocated,
            ((DataBufferInt) raster.image.getRaster().getDataBuffer()).getData(),
            raster.atlasWidth,
            raster.atlasHeight);
        GlStateManager.bindTexture(allocated);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        texture = allocated;
        residentPixelBytes += textureBytes;
      } catch (RuntimeException | Error failure) {
        GlStateManager.deleteTexture(allocated);
        throw failure;
      }
    }

    void close() {
      if (texture != 0) {
        GlStateManager.deleteTexture(texture);
        residentPixelBytes -= textureBytes;
      }
      texture = 0;
    }
  }

  private final class PixelFace implements Face {
    final PixelKey key;
    final float unit;
    PixelAtlas atlas;
    final Face fallback;
    private final PixelFace[] styleFaces = new PixelFace[4];
    private final LinkedHashMap<String, float[]> centeredLabels =
        new LinkedHashMap<String, float[]>(16, .75f, true) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<String, float[]> eldest) {
            return size() > 64;
          }
        };

    PixelFace(PixelKey key, boolean eagerUpload) {
      this.key = key;
      unit = PixelFontRaster.unit(key.factor, key.pixels, key.size);
      atlas = pixelAtlas(new RasterKey(key));
      fallback = key.bold ? minecraftBold : minecraft;
      if (eagerUpload) resident();
    }

    private void resident() {
      pixelFaces.put(key, this);
      while (pixelFaces.size() > MAX_PIXEL_FACES) {
        Iterator<PixelKey> iterator = pixelFaces.keySet().iterator();
        iterator.next();
        iterator.remove();
      }
      atlas = atlas.resident();
    }

    private boolean latin(int cp) {
      return cp >= 32 && cp < 256;
    }

    @Override
    public float width(String text) {
      text = perihelion.integration.minecraft189.HookBridge.displayText(text);
      if (text.indexOf('\u00a7') >= 0) {
        float width = 0;
        for (FormattedFontRuns.Run run : formatted(text)) {
          PixelFace face = styled(run);
          width += run.obfuscated ? face.obfuscatedWidth(run.text) : face.widthPlain(run.text);
        }
        return width;
      }
      return widthPlain(text);
    }

    private float widthPlain(String text) {
      float width = 0;
      for (int i = 0; i < text.length(); ) {
        int cp = text.codePointAt(i), next = i + Character.charCount(cp);
        width +=
            latin(cp)
                ? atlas.advances[cp] * unit
                : fallback.width(text.substring(i, next)) * key.factor;
        i = next;
      }
      return width;
    }

    private PixelFace styled(FormattedFontRuns.Run run) {
      boolean weight = key.bold || run.bold;
      if (weight == key.bold && run.italic == key.italic) return this;
      int variant = (weight ? 1 : 0) | (run.italic ? 2 : 0);
      if (styleFaces[variant] != null) {
        PixelFace cached = pixelFaces.get(styleFaces[variant].key);
        if (cached != null) styleFaces[variant] = cached;
        return styleFaces[variant];
      }
      PixelKey next =
          new PixelKey(key.choice, weight, run.italic, key.size, key.pixels, key.factor);
      PixelFace cached = pixelFaces.get(next);
      styleFaces[variant] = cached != null ? cached : new PixelFace(next, false);
      return styleFaces[variant];
    }

    private float obfuscatedWidth(String text) {
      return fallback.width("\u00a7k" + text) * key.factor;
    }

    @Override
    public float height() {
      return atlas.height * unit;
    }

    @Override
    public float inkHeight() {
      return atlas.inkHeight * unit;
    }

    @Override
    public float centeredY(float top, float height) {
      return top + (height - atlas.inkHeight * unit) / 2 - atlas.inkTop * unit;
    }

    /** Buttons center their own visible letters, including descenders and side bearings. */
    @Override
    public void drawCentered(String text, float x, float y, float width, float height, int color) {
      text = perihelion.integration.minecraft189.HookBridge.displayText(text);
      float[] ink = centeredInk(text);
      if (ink == null) {
        Face.super.drawCentered(text, x, y, width, height, color);
        return;
      }
      draw(
          text,
          x + (width - (ink[2] - ink[0])) / 2 - ink[0],
          y + (height - (ink[3] - ink[1])) / 2 - ink[1],
          color);
    }

    private float[] centeredInk(String text) {
      // Formatted/native fallback runs retain their established layout. Plain button captions
      // use the exact pixel bounds packed into the selected font's atlas.
      for (int i = 0; i < text.length(); i++)
        if (!latin(text.charAt(i)) || text.charAt(i) == '\u00a7') return null;
      float[] cached = centeredLabels.get(text);
      if (cached != null) return cached;
      float left = Float.POSITIVE_INFINITY, top = Float.POSITIVE_INFINITY;
      float right = Float.NEGATIVE_INFINITY, bottom = Float.NEGATIVE_INFINITY, cursor = 0;
      for (int i = 0; i < text.length(); i++) {
        int cp = text.charAt(i);
        if (!Character.isWhitespace(cp) && !Character.isSpaceChar(cp)) {
          left = Math.min(left, cursor + (atlas.offsetX[cp] + atlas.padding) * unit);
          top = Math.min(top, (atlas.offsetY[cp] + atlas.padding) * unit);
          right =
              Math.max(
                  right, cursor + (atlas.offsetX[cp] + atlas.widths[cp] - atlas.padding) * unit);
          bottom = Math.max(bottom, (atlas.offsetY[cp] + atlas.heights[cp] - atlas.padding) * unit);
        }
        cursor += atlas.advances[cp] * unit;
      }
      if (!Float.isFinite(left)) return null;
      float[] ink = {left, top, right, bottom};
      centeredLabels.put(text, ink);
      return ink;
    }

    @Override
    public void draw(String text, float x, float y, int color) {
      draw(text, x, y, color, null);
    }

    @Override
    public void draw(String text, float x, float y, int color, UiCanvas.TextureMode textureMode) {
      if (textureMode != null) textureMode.texture(true);
      if ((color >>> 24) == 0) return;
      text = perihelion.integration.minecraft189.HookBridge.displayText(text);
      try {
        if (text.indexOf('\u00a7') >= 0) {
          x = Math.round(x * key.pixels) / key.pixels;
          y = Math.round(y * key.pixels) / key.pixels;
          for (FormattedFontRuns.Run run : formatted(text)) {
            PixelFace face = styled(run);
            int ink = run.color < 0 ? color : color & 0xff000000 | run.color;
            float advance;
            if (textureMode != null) textureMode.texture(true);
            if (run.obfuscated) {
              face.fallback("\u00a7k" + run.text, x, y, ink);
              advance = face.obfuscatedWidth(run.text);
            } else {
              face.drawPlain(run.text, x, y, ink);
              advance = face.widthPlain(run.text);
            }
            face.decoration(run, x, y, advance, ink, textureMode);
            x += advance;
          }
        } else drawPlain(text, x, y, color);
      } finally {
        if (textureMode != null) textureMode.texture(true);
      }
    }

    private void drawPlain(String text, float x, float y, int color) {
      resident();
      x = Math.round(x * key.pixels) / key.pixels;
      y = Math.round(y * key.pixels) / key.pixels;
      int start = 0;
      for (int i = 0; i < text.length(); ) {
        int cp = text.codePointAt(i), next = i + Character.charCount(cp);
        if (!latin(cp)) {
          if (start < i) {
            String run = text.substring(start, i);
            drawLatin(run, x, y, color);
            x += widthPlain(run);
          }
          String glyph = text.substring(i, next);
          fallback(glyph, x, y, color);
          x += fallback.width(glyph) * key.factor;
          start = next;
        }
        i = next;
      }
      if (start < text.length()) drawLatin(text.substring(start), x, y, color);
    }

    private void decoration(
        FormattedFontRuns.Run run,
        float x,
        float y,
        float width,
        int ink,
        UiCanvas.TextureMode mode) {
      if ((!run.underline && !run.strike) || width <= 0) return;
      if (mode != null) mode.texture(false);
      GlStateManager.disableTexture2D();
      RenderStyle.color(ink);
      float line = 1 / key.pixels;
      GL11.glBegin(GL11.GL_QUADS);
      try {
        if (run.underline) decoration(x, y + (atlas.ascent + 1) * unit, width, line);
        if (run.strike)
          decoration(x, y + Math.round(atlas.ascent - atlas.inkHeight * .45f) * unit, width, line);
      } finally {
        GL11.glEnd();
        if (mode != null) mode.texture(true);
      }
    }

    private void decoration(float x, float y, float width, float height) {
      GL11.glVertex2f(x, y);
      GL11.glVertex2f(x, y + height);
      GL11.glVertex2f(x + width, y + height);
      GL11.glVertex2f(x + width, y);
    }

    private void fallback(String text, float x, float y, int color) {
      GlStateManager.pushMatrix();
      try {
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(key.factor, key.factor, 1);
        fallback.draw(text, 0, 0, color);
      } finally {
        GlStateManager.popMatrix();
      }
    }

    private void drawLatin(String text, float x, float y, int color) {
      RenderStyle.text();
      RenderStyle.color(color);
      GlStateManager.bindTexture(atlas.texture);
      GL11.glBegin(GL11.GL_QUADS);
      try {
        for (int i = 0; i < text.length(); i++) {
          int cp = text.charAt(i), w = atlas.widths[cp], h = atlas.heights[cp];
          float u = atlas.glyphX[cp] / (float) atlas.textureWidth;
          float v = atlas.glyphY[cp] / (float) atlas.textureHeight;
          float left = x + atlas.offsetX[cp] * unit, top = y + atlas.offsetY[cp] * unit;
          GL11.glTexCoord2f(u, v);
          GL11.glVertex2f(left, top);
          GL11.glTexCoord2f(u, v + h / (float) atlas.textureHeight);
          GL11.glVertex2f(left, top + h * unit);
          GL11.glTexCoord2f(
              u + w / (float) atlas.textureWidth, v + h / (float) atlas.textureHeight);
          GL11.glVertex2f(left + w * unit, top + h * unit);
          GL11.glTexCoord2f(u + w / (float) atlas.textureWidth, v);
          GL11.glVertex2f(left + w * unit, top);
          x += atlas.advances[cp] * unit;
        }
      } finally {
        GL11.glEnd();
      }
    }
  }

  private final class Atlas implements Face {
    // Owned directly: TextureManager retains deleted DynamicTextures and their CPU pixel arrays.
    int texture;
    final float[] advances = new float[256];
    final int height;
    final float inkTop, inkHeight;
    final Face fallback;
    final float[] labelInk;

    Atlas(ClickGuiModule.Font choice, boolean bold) {
      this(choice, bold, false);
    }

    Atlas(ClickGuiModule.Font choice, boolean bold, boolean menu) {
      labelInk = menu ? new float[256 * 4] : null;
      fallback = bold ? minecraftBold : minecraft;
      BufferedImage image = new BufferedImage(ATLAS, ATLAS, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = image.createGraphics();
      try {
        if (menu) g.setFont(new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, 32));
        else {
          try {
            g.setFont(BundledFonts.load(choice, bold, 28));
          } catch (IllegalStateException failure) {
            org.apache.logging.log4j.LogManager.getLogger("Perihelion")
                .warn("GUI font unavailable: {}; using Sans", choice, failure);
            g.setFont(new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, 28));
          }
        }
        g.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(
            RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        if (menu)
          g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(java.awt.Color.WHITE);
        FontMetrics metrics = g.getFontMetrics();
        height = menu ? CELL - 8 : Math.min(CELL - 8, metrics.getHeight() + 8);
        java.awt.geom.Rectangle2D ink =
            g.getFont().createGlyphVector(g.getFontRenderContext(), "H").getVisualBounds();
        inkTop = (metrics.getAscent() + (float) ink.getY()) * GLYPH_SCALE;
        inkHeight = (float) ink.getHeight() * GLYPH_SCALE;
        for (int c = 32; c < 256; c++) {
          if (menu) {
            // The logo's cap height is 80 units. Rasterize the very same outlines at 4x.
            Shape glyph = MenuLetterforms.glyph(c);
            AffineTransform shapeScale;
            if (glyph != null) {
              shapeScale = AffineTransform.getScaleInstance(.32, .32);
              advances[c] = ((float) glyph.getBounds2D().getWidth() + 13) * .32f;
            } else {
              // Accents and punctuation retain readable system-font coverage, normalized to H.
              glyph =
                  g.getFont()
                      .createGlyphVector(g.getFontRenderContext(), new char[] {(char) c})
                      .getOutline();
              double factor = 25.6 / ink.getHeight();
              shapeScale = new AffineTransform(factor, 0, 0, factor, 0, -ink.getY() * factor);
              advances[c] =
                  Math.min(
                      CELL - 8,
                      (float)
                              g.getFont()
                                  .getStringBounds(
                                      String.valueOf((char) c), g.getFontRenderContext())
                                  .getWidth()
                          * (float) factor);
            }
            Shape sized =
                AffineTransform.getTranslateInstance(0, 8)
                    .createTransformedShape(shapeScale.createTransformedShape(glyph));
            Rectangle2D bounds = sized.getBounds2D();
            if (!bounds.isEmpty()) {
              labelInk[c * 4] = (float) bounds.getMinX() * GLYPH_SCALE;
              labelInk[c * 4 + 1] = (float) bounds.getMinY() * GLYPH_SCALE;
              labelInk[c * 4 + 2] = (float) bounds.getMaxX() * GLYPH_SCALE;
              labelInk[c * 4 + 3] = (float) bounds.getMaxY() * GLYPH_SCALE;
            }
            g.fill(
                AffineTransform.getTranslateInstance(c % 16 * CELL + 4, c / 16 * CELL + 4)
                    .createTransformedShape(sized));
            continue;
          }
          advances[c] =
              Math.min(
                  CELL - 8,
                  (float)
                      g.getFont()
                          .getStringBounds(String.valueOf((char) c), g.getFontRenderContext())
                          .getWidth());
          g.drawString(
              String.valueOf((char) c), c % 16 * CELL + 4, c / 16 * CELL + 4 + metrics.getAscent());
        }
      } finally {
        g.dispose();
      }
      int allocated = TextureUtil.glGenTextures();
      if (allocated == 0) throw new IllegalStateException("Cannot allocate GUI font atlas");
      try {
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        FontAtlasPixels.whiteCoverage(pixels, ATLAS, ATLAS);
        TextureUtil.allocateTexture(allocated, ATLAS, ATLAS);
        // Use the full 4x raster. Lower-resolution mip levels softened small GUI text.
        // Upload synchronously through vanilla's staging buffer; retain no image/pixel array.
        TextureUtil.uploadTexture(allocated, pixels, ATLAS, ATLAS);
        GlStateManager.bindTexture(allocated);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        texture = allocated;
      } catch (RuntimeException | Error failure) {
        GlStateManager.deleteTexture(allocated);
        throw failure;
      } finally {
        image.flush();
      }
    }

    boolean latin(int codepoint) {
      return codepoint >= 32 && codepoint <= 255;
    }

    public float width(String text) {
      text = perihelion.integration.minecraft189.HookBridge.displayText(text);
      if (text.indexOf('\u00a7') >= 0) return fallback.width(text);
      float total = 0;
      for (int i = 0; i < text.length(); ) {
        int codepoint = text.codePointAt(i), next = i + Character.charCount(codepoint);
        total +=
            latin(codepoint)
                ? advances[codepoint] * GLYPH_SCALE
                : fallback.width(text.substring(i, next));
        i = next;
      }
      return total;
    }

    public float height() {
      return (height - 8) * GLYPH_SCALE;
    }

    public float centeredY(float top, float height) {
      return top + (height - inkHeight) / 2 - inkTop;
    }

    @Override
    public void drawCentered(String text, float x, float y, float width, float height, int color) {
      text = perihelion.integration.minecraft189.HookBridge.displayText(text);
      if (labelInk == null || text.indexOf('\u00a7') >= 0) {
        Face.super.drawCentered(text, x, y, width, height, color);
        return;
      }
      float left = Float.POSITIVE_INFINITY, top = Float.POSITIVE_INFINITY;
      float right = Float.NEGATIVE_INFINITY, bottom = Float.NEGATIVE_INFINITY, cursor = 0;
      for (int i = 0; i < text.length(); ) {
        int cp = text.codePointAt(i), next = i + Character.charCount(cp);
        if (latin(cp)) {
          if (labelInk[cp * 4 + 3] > labelInk[cp * 4 + 1]) {
            left = Math.min(left, cursor + labelInk[cp * 4]);
            top = Math.min(top, labelInk[cp * 4 + 1]);
            right = Math.max(right, cursor + labelInk[cp * 4 + 2]);
            bottom = Math.max(bottom, labelInk[cp * 4 + 3]);
          }
          cursor += advances[cp] * GLYPH_SCALE;
        } else {
          float advance = fallback.width(text.substring(i, next));
          left = Math.min(left, cursor);
          right = Math.max(right, cursor + advance);
          top = Math.min(top, 0);
          bottom = Math.max(bottom, fallback.height());
          cursor += advance;
        }
        i = next;
      }
      if (Float.isFinite(left))
        draw(
            text,
            x + (width - (right - left)) / 2 - left,
            y + (height - (bottom - top)) / 2 - top,
            color);
    }

    public void draw(String text, float x, float y, int color) {
      if ((color >>> 24) == 0) return;
      text = perihelion.integration.minecraft189.HookBridge.displayText(text);
      if (text.indexOf('\u00a7') >= 0) {
        fallback.draw(text, x, y, color);
        return;
      }
      int start = 0;
      for (int i = 0; i < text.length(); ) {
        int codepoint = text.codePointAt(i), next = i + Character.charCount(codepoint);
        if (!latin(codepoint)) {
          if (start < i) {
            String run = text.substring(start, i);
            drawLatin(run, x, y, color);
            x += width(run);
          }
          String glyph = text.substring(i, next);
          fallback.draw(glyph, x, y, color);
          x += fallback.width(glyph);
          start = next;
        }
        i = next;
      }
      if (start < text.length()) drawLatin(text.substring(start), x, y, color);
    }

    private void drawLatin(String text, float x, float y, int color) {
      // Atlas padding surrounds the ink; it is not part of the text's layout origin.
      x -= 4 * GLYPH_SCALE;
      y -= 4 * GLYPH_SCALE;
      RenderStyle.text();
      RenderStyle.color(color);
      GlStateManager.bindTexture(texture);
      GL11.glBegin(GL11.GL_QUADS);
      try {
        for (int i = 0; i < text.length(); i++) {
          int c = text.charAt(i), w = (int) Math.ceil(advances[c]) + 8;
          float u = (c % 16 * CELL) / (float) ATLAS, v = (c / 16 * CELL) / (float) ATLAS;
          float right = x + w * GLYPH_SCALE, bottom = y + height * GLYPH_SCALE;
          GL11.glTexCoord2f(u, v);
          GL11.glVertex2f(x, y);
          GL11.glTexCoord2f(u, v + height / (float) ATLAS);
          GL11.glVertex2f(x, bottom);
          GL11.glTexCoord2f(u + w / (float) ATLAS, v + height / (float) ATLAS);
          GL11.glVertex2f(right, bottom);
          GL11.glTexCoord2f(u + w / (float) ATLAS, v);
          GL11.glVertex2f(right, y);
          x += advances[c] * GLYPH_SCALE;
        }
      } finally {
        GL11.glEnd();
      }
    }

    void close() {
      if (texture != 0) GlStateManager.deleteTexture(texture);
      texture = 0;
    }
  }

  @Override
  public void close() {
    nativeFaces.clear();
    for (PixelAtlas atlas : pixelAtlases.values()) atlas.close();
    pixelAtlases.clear();
    pixelMetrics.clear();
    pixelFaces.clear();
    residentPixelBytes = 0;
    pixelTextureLimit = 0;
    formattedRuns.clear();
    if (menuFace != null) {
      menuFace.close();
      menuFace = null;
    }
    for (Atlas atlas : atlases.values()) atlas.close();
    atlases.clear();
    for (Atlas atlas : boldAtlases.values()) atlas.close();
    boldAtlases.clear();
  }
}
