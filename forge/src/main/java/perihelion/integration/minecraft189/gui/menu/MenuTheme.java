package perihelion.integration.minecraft189.gui.menu;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Mouse;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.integration.minecraft189.gui.ClickGuiModule;
import perihelion.integration.minecraft189.gui.GuiTheme;
import perihelion.integration.minecraft189.gui.font.GuiFonts;
import perihelion.integration.minecraft189.gui.modern.ModernTheme;
import perihelion.integration.minecraft189.gui.widget.InlineTextFieldView;
import perihelion.integration.minecraft189.gui.widget.UiCanvas;
import perihelion.integration.minecraft189.render.RenderState;
import perihelion.integration.minecraft189.render.RenderStyle;

/** Runtime-owned presentation for native screens descended from the title. No input is replaced. */
public final class MenuTheme implements AutoCloseable {
  private static MenuTheme current;
  private final ClientRuntime runtime;
  private final Map<GuiScreen, Boolean> screens = new WeakHashMap<>();
  private final Map<GuiButton, Hover> hovers = new WeakHashMap<>();
  private final Map<GuiTextField, InlineTextFieldView> fields = new WeakHashMap<>();
  private final GuiFonts fonts = new GuiFonts();
  private final UiCanvas canvas = new UiCanvas();
  private final RenderState state = new RenderState();
  private final BlackHoleBackground backdrop = new BlackHoleBackground();
  private final MenuMotion motion = new MenuMotion();
  private final long started = System.nanoTime();
  private GuiScreen frameScreen;
  private boolean backgroundDrawn, capturing;
  private int knobLeft, knobRight;

  private static final class Hover {
    long last = System.nanoTime();
    float amount;
  }

  public MenuTheme(ClientRuntime runtime) {
    this.runtime = runtime;
    current = this;
  }

  public void opened(GuiScreen next, GuiScreen previous) {
    if (next instanceof MainMenuScreen) screens.put(next, true);
    else if (next != null && screens.containsKey(previous) && candidate(next))
      screens.put(next, true);
    frameScreen = null;
    backgroundDrawn = false;
    if (next == null || !screens.containsKey(next)) releaseGpu();
  }

  private static boolean candidate(GuiScreen screen) {
    String name = screen.getClass().getName();
    return name.startsWith("net.minecraft.client.")
        || name.startsWith("net.minecraftforge.")
        || name.startsWith("perihelion.integration.minecraft189.account.");
  }

  public static boolean active(GuiScreen screen) {
    return current != null
        && current.runtime.isRunning()
        && screen != null
        && Minecraft.getMinecraft().theWorld == null
        && current.screens.containsKey(screen);
  }

  private static boolean nativeScreen() {
    if (current == null) return false;
    GuiScreen screen = Minecraft.getMinecraft().currentScreen;
    return active(screen) && !(screen instanceof MainMenuScreen);
  }

  public void beginFrame(GuiScreen screen, int mouseX, int mouseY) {
    if (!active(screen)) return;
    frameScreen = screen;
    backgroundDrawn = false;
    motion.update(mouseX, mouseY, screen.width, screen.height);
  }

  public static boolean background(GuiScreen screen) {
    if (!active(screen) || screen instanceof MainMenuScreen) return false;
    MenuTheme theme = current;
    if (theme.frameScreen != screen) {
      Minecraft game = Minecraft.getMinecraft();
      theme.beginFrame(
          screen,
          Mouse.getX() * screen.width / Math.max(1, game.displayWidth),
          screen.height - 1 - Mouse.getY() * screen.height / Math.max(1, game.displayHeight));
    }
    if (theme.backgroundDrawn) return true;
    try (RenderState ignored = theme.state.open()) {
      theme.prepare();
      theme.backdrop.render(
          theme.canvas,
          screen.width,
          screen.height,
          (System.nanoTime() - theme.started) / 1_000_000_000f,
          theme.motion.x,
          theme.motion.y,
          true);
    }
    theme.backgroundDrawn = true;
    return true;
  }

  private void prepare() {
    RenderStyle.text();
    Minecraft game = Minecraft.getMinecraft();
    canvas.pixelScale = new ScaledResolution(game).getScaleFactor();
    if (GuiTheme.modern(runtime))
      GuiTheme.configure(canvas, fonts, GuiTheme.preferences(runtime), canvas.pixelScale);
    else {
      canvas.modern = false;
      canvas.font = fonts.get(ClickGuiModule.Font.SANS);
    }
  }

  public static boolean button(GuiButton button, int mouseX, int mouseY, Runnable nativeDrag) {
    if (!nativeScreen()) return false;
    if (!button.visible) return true;
    MenuTheme theme = current;
    boolean over =
        button.enabled
            && UiCanvas.inside(
                mouseX, mouseY, button.xPosition, button.yPosition, button.width, button.height);
    Hover hover = theme.hovers.computeIfAbsent(button, ignored -> new Hover());
    long now = System.nanoTime();
    float elapsed = Math.max(0, Math.min(.05f, (now - hover.last) / 1_000_000_000f));
    hover.last = now;
    hover.amount += ((over ? 1 : 0) - hover.amount) * (1 - (float) Math.exp(-elapsed * 14));
    try (RenderState ignored = theme.state.open()) {
      theme.prepare();
      MenuSurface.button(
          theme.canvas,
          button.xPosition,
          button.yPosition,
          button.width,
          button.height,
          false,
          button.enabled,
          hover.amount);
      theme.knobLeft = Integer.MAX_VALUE;
      theme.knobRight = Integer.MIN_VALUE;
      theme.capturing = true;
      try {
        nativeDrag.run();
      } finally {
        theme.capturing = false;
      }
      RenderStyle.text();
      boolean slider = theme.knobRight > theme.knobLeft;
      GuiFonts.Face face = theme.canvas.modern ? theme.canvas.font : theme.fonts.menu();
      theme.canvas.font = face;
      String label =
          theme.canvas.fit(
              theme.canvas.modern
                  ? button.displayString
                  : EnumChatFormatting.getTextWithoutFormattingCodes(button.displayString),
              button.width - 8);
      int color =
          button.packedFGColour != 0
              ? button.packedFGColour
              : button.enabled
                  ? (theme.canvas.modern
                      ? ModernTheme.TEXT
                      : MenuSurface.blend(0xffd4d7de, 0xfff0eae0, hover.amount))
                  : (theme.canvas.modern ? ModernTheme.MUTED : MenuSurface.MUTED);
      if ((color >>> 24) == 0) color |= 0xff000000;
      float labelHeight = slider ? button.height - 5 : button.height;
      face.drawCentered(
          label, button.xPosition, button.yPosition, button.width, labelHeight, color);
      if (slider) {
        float track = button.yPosition + button.height - 4;
        float center = (theme.knobLeft + theme.knobRight) / 2f;
        theme.canvas.hairline(button.xPosition + 4, track, button.width - 8, 0xff494641);
        theme.canvas.hairline(
            button.xPosition + 4,
            track,
            Math.max(0, center - button.xPosition - 4),
            theme.canvas.modern ? theme.canvas.accent : 0xff9f8e73);
        MenuSurface.rounded(
            theme.canvas,
            center - 2,
            track - 1.5f,
            4,
            4,
            2,
            theme.canvas.modern ? theme.canvas.accent : 0xffd0c4ae);
      }
    }
    return true;
  }

  /**
   * Suppress only texture quads emitted by a native button's drag callback; its logic still runs.
   */
  public static boolean sliderTexture(int x, int y, int width, int height) {
    if (current == null || !current.capturing || !nativeScreen()) return false;
    current.knobLeft = Math.min(current.knobLeft, x);
    current.knobRight = Math.max(current.knobRight, x + width);
    return true;
  }

  public static boolean inputSurface(int left, int top, int right, int bottom, boolean focused) {
    if (!nativeScreen()) return false;
    try (RenderState ignored = current.state.open()) {
      current.prepare();
      if (current.canvas.modern) {
        ModernTheme.input(current.canvas, left, top, right - left, bottom - top, focused);
        return true;
      }
      MenuSurface.rounded(
          current.canvas,
          left,
          top,
          right - left,
          bottom - top,
          4,
          focused ? 0xff9c8b71 : 0xff3c4047);
      float line = 1 / Math.max(1, current.canvas.pixelScale);
      MenuSurface.rounded(
          current.canvas,
          left + line,
          top + line,
          right - left - line * 2,
          bottom - top - line * 2,
          4 - line,
          0xff11151c);
    }
    return true;
  }

  public static boolean listSurface(int left, int top, int right, int bottom, boolean mask) {
    if (!nativeScreen()) return false;
    try (RenderState ignored = current.state.open()) {
      current.prepare();
      current.canvas.rect(
          left,
          top,
          right - left,
          bottom - top,
          current.canvas.modern
              ? (mask ? ModernTheme.HEADER : 0xc0242322)
              : mask ? 0xff090d13 : 0x88070b11);
      if (mask)
        current.canvas.hairline(
            left, top, right - left, current.canvas.modern ? ModernTheme.BORDER : 0xff1b2028);
    }
    return true;
  }

  public static boolean text(String text, int x, int y, int color, boolean centered) {
    if (!nativeScreen() || text == null) return false;
    try (RenderState ignored = current.state.open()) {
      current.prepare();
      if (current.canvas.modern) {
        if ((color >>> 24) == 0) color |= 0xff000000;
        current.canvas.text(
            text,
            centered ? x - current.canvas.font.width(text) / 2 : x,
            current.canvas.font.centeredY(y, 9),
            color);
        return true;
      }
      float scale = 1.15f;
      if ((color >>> 24) == 0) color |= 0xff000000;
      MenuSurface.label(
          text,
          centered ? x - current.canvas.font.width(text) * scale / 2 : x,
          y + current.canvas.font.centeredY(0, 9 / scale) * scale,
          scale,
          current.canvas.font,
          color);
    }
    return true;
  }

  /** Paint the original input state with the same selected font as its surrounding menu. */
  public static boolean field(
      GuiTextField field,
      int x,
      int y,
      int w,
      int h,
      boolean background,
      boolean focused,
      boolean enabled) {
    if (!nativeScreen() || !GuiTheme.modern(current.runtime)) return false;
    if (!field.getVisible()) return true;
    try (RenderState ignored = current.state.open()) {
      current.prepare();
      if (background) ModernTheme.input(current.canvas, x - 1, y - 1, w + 2, h + 2, focused);
      current.canvas.text = enabled ? ModernTheme.TEXT : ModernTheme.MUTED;
      float padding = background ? 4 : 0;
      current
          .fields
          .computeIfAbsent(field, unused -> new InlineTextFieldView())
          .draw(
              current.canvas,
              field,
              x + padding,
              current.canvas.font.centeredY(y, h) - 1,
              w - padding * 2,
              0,
              focused);
    }
    return true;
  }

  public static void fieldClick(
      GuiTextField field,
      int x,
      int y,
      int w,
      int h,
      boolean background,
      boolean focused,
      int mouseX,
      int mouseY,
      int button,
      int previousCursor,
      int previousSelection) {
    if (!nativeScreen()
        || !GuiTheme.modern(current.runtime)
        || !focused
        || button != 0
        || !UiCanvas.inside(mouseX, mouseY, x, y, w, h)) return;
    try (RenderState ignored = current.state.open()) {
      current.prepare();
      float padding = background ? 4 : 0;
      // Vanilla already applied focus rules. Keep the viewport that was actually drawn,
      // rather than allowing its bitmap-font click calculation to scroll a long input.
      field.setCursorPosition(previousCursor);
      field.setSelectionPos(previousSelection);
      current
          .fields
          .computeIfAbsent(field, unused -> new InlineTextFieldView())
          .placeCursor(current.canvas, field, x + padding, w - padding * 2, mouseX);
    }
  }

  public static void accountPalette(UiCanvas canvas) {
    canvas.accent = 0xffa99475;
    canvas.panel = 0xef10151c;
    canvas.header = 0xff20242b;
    canvas.headerBottom = 0xff181d25;
    canvas.text = MenuSurface.TEXT;
    canvas.muted = MenuSurface.MUTED;
    canvas.hover = 0x252e3034;
    canvas.selection = 0x343e3a33;
    canvas.activeButton = 0xff302b24;
  }

  private void releaseGpu() {
    backdrop.close();
    fonts.close();
    hovers.clear();
    fields.clear();
  }

  @Override
  public void close() {
    releaseGpu();
    screens.clear();
    frameScreen = null;
    if (current == this) current = null;
  }
}
