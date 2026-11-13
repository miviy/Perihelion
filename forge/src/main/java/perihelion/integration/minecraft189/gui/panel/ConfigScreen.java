package perihelion.integration.minecraft189.gui.panel;

import java.io.IOException;
import java.util.Collections;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.integration.minecraft189.gui.ClickGuiModule;
import perihelion.integration.minecraft189.gui.GuiTheme;
import perihelion.integration.minecraft189.gui.font.GuiFonts;
import perihelion.integration.minecraft189.gui.modern.ModernTheme;
import perihelion.integration.minecraft189.gui.widget.UiCanvas;
import perihelion.integration.minecraft189.render.RenderState;
import perihelion.integration.minecraft189.render.RenderStyle;

/** A dedicated preset screen. Parent menu state and native config operations stay separate. */
public final class ConfigScreen extends GuiScreen {
  private final ClientRuntime runtime;
  private final GuiScreen parent;
  private final ClickGuiModule preferences;
  private final ConfigPanel configs;
  private final GuiFonts fonts = new GuiFonts();
  private final UiCanvas canvas = new UiCanvas();
  private final RenderState state = new RenderState();
  private final UtilityLayout utilities = new UtilityLayout();
  private UtilityLayout.Box configWindow;
  private float scale = 1, viewWidth, viewHeight, doneX, doneY, doneWidth, dragX, dragY;
  private boolean dragging, initialized, previousRepeat;

  public ConfigScreen(ClientRuntime runtime, GuiScreen parent) {
    this.runtime = runtime;
    this.parent = parent;
    preferences = GuiTheme.preferences(runtime);
    configs = new ConfigPanel(runtime);
  }

  @Override
  public void initGui() {
    if (!initialized) {
      initialized = true;
      previousRepeat = Keyboard.areRepeatEventsEnabled();
      utilities.load(preferences.toolsLayout().get());
      configs.refresh();
    }
    Keyboard.enableRepeatEvents(true);
    runtime.input().setAllowed(false, Collections.emptyList());
    dragging = false;
    geometry();
  }

  private void geometry() {
    try (RenderState ignored = state.open()) {
      float framebufferScale = new ScaledResolution(mc).getScaleFactor();
      float next = preferences.scale.get().floatValue();
      float desired, desiredHeight;
      if (preferences.modern()) {
        // The pixel-hinted face changes metrics with the final menu scale. Measure and fit
        // together, keeping the largest measured bounds so rounding never clips either pane.
        float measuredWidth = 0, measuredHeight = 0;
        // Three measurements plus the final face stay within the eight-atlas owner cache.
        for (int pass = 0; pass < 3; pass++) {
          GuiTheme.configure(canvas, fonts, preferences, framebufferScale * next);
          measuredWidth = Math.max(measuredWidth, configs.preferredWidth(canvas));
          measuredHeight = Math.max(measuredHeight, configs.preferredHeight(canvas));
          float fit =
              Math.min(
                  preferences.scale.get().floatValue(),
                  Math.min(width / (measuredWidth + 12), height / (measuredHeight + 28)));
          if (fit >= next) break;
          next = fit;
        }
        GuiTheme.configure(canvas, fonts, preferences, framebufferScale * next);
        desired = configs.preferredWidth(canvas);
        desiredHeight = configs.preferredHeight(canvas);
      } else {
        GuiTheme.configure(canvas, fonts, preferences, framebufferScale);
        desired = configs.preferredWidth(canvas);
        desiredHeight = configs.preferredHeight(canvas);
        next =
            Math.min(
                preferences.scale.get().floatValue(),
                Math.min(width / (desired + 12), height / (desiredHeight + 28)));
      }
      if (next != scale || viewWidth != width / next || viewHeight != height / next)
        dragging = false;
      scale = next;
      viewWidth = width / scale;
      viewHeight = height / scale;
      canvas.pixelScale = framebufferScale * scale;
      if (!dragging) utilities.resizeEditor("configs-editor", viewWidth, viewHeight);
      configWindow =
          utilities.box(
              "configs-editor",
              Math.max(6, (viewWidth - desired) / 2),
              Math.max(20, (viewHeight - desiredHeight) / 2));
      if (!dragging)
        configWindow.recover(
            viewWidth,
            viewHeight,
            desired,
            configWindow.collapsed ? GuiTheme.HEADER : desiredHeight);
      if (canvas.modern) {
        configWindow.x = ModernTheme.snap(canvas, configWindow.x);
        configWindow.y = ModernTheme.snap(canvas, configWindow.y);
      }
      doneWidth = Math.max(32, canvas.font.width("Done") + 8);
      doneX = configWindow.x + desired - doneWidth;
      doneY = Math.max(2, configWindow.y - 16);
      if (canvas.modern) {
        doneX = ModernTheme.snap(canvas, doneX);
        doneY = ModernTheme.snap(canvas, doneY);
        doneWidth = ModernTheme.snap(canvas, doneWidth);
      }
    }
  }

  @Override
  public void drawScreen(int x, int y, float partialTicks) {
    if (!runtime.isRunning()) {
      mc.displayGuiScreen(null);
      return;
    }
    geometry();
    float mx = x / scale, my = y / scale;
    try (RenderState ignored = state.open()) {
      RenderStyle.text();
      GlStateManager.scale(scale, scale, 1);
      GuiTheme.configure(canvas, fonts, preferences, canvas.pixelScale);
      canvas.rect(
          0,
          0,
          viewWidth,
          viewHeight,
          GuiTheme.alpha(preferences.background.get(), canvas.modern ? 0x11100f : 0x090b0e));
      configs.draw(canvas, viewWidth, viewHeight, mx, my, configWindow);
      GuiTheme.button(
          canvas,
          "Done",
          doneX,
          doneY,
          doneWidth,
          12,
          false,
          true,
          UiCanvas.inside(mx, my, doneX, doneY, doneWidth, 12));
    }
  }

  public void click(float mx, float my, int button) {
    geometry();
    if (button == 0 && UiCanvas.inside(mx, my, doneX, doneY, doneWidth, 12)) {
      mc.displayGuiScreen(parent);
      return;
    }
    if (UiCanvas.inside(mx, my, configWindow.x, configWindow.y, configs.width(), GuiTheme.HEADER)) {
      configs.blur();
      if (button == 1) {
        configWindow.collapsed = !configWindow.collapsed;
        persist();
      } else if (button == 0) {
        dragging = true;
        dragX = mx - configWindow.x;
        dragY = my - configWindow.y;
      }
    } else if (!configWindow.collapsed) configs.click(canvas, mx, my, button);
  }

  public void drag(float mx, float my) {
    if (dragging) {
      configWindow.x = mx - dragX;
      configWindow.y = my - dragY;
    }
  }

  public void release() {
    if (dragging) {
      dragging = false;
      geometry();
      persist();
    }
  }

  private void persist() {
    if (runtime.isRunning())
      runtime.modules().setSetting("clickgui", preferences.toolsLayout(), utilities.encode());
  }

  public void type(char character, int code) {
    if (code == Keyboard.KEY_ESCAPE) {
      mc.displayGuiScreen(parent);
    } else configs.type(character, code);
  }

  public void scroll(float mx, float my, float delta) {
    if (!configWindow.collapsed && configs.contains(mx, my, configWindow)) configs.scroll(delta);
  }

  @Override
  protected void mouseClicked(int x, int y, int button) throws IOException {
    click(x / scale, y / scale, button);
  }

  @Override
  protected void mouseClickMove(int x, int y, int button, long elapsed) {
    if (button == 0) drag(x / scale, y / scale);
  }

  @Override
  protected void mouseReleased(int x, int y, int button) {
    if (button == 0) release();
  }

  @Override
  protected void keyTyped(char character, int code) throws IOException {
    type(character, code);
  }

  @Override
  public void handleMouseInput() throws IOException {
    super.handleMouseInput();
    int wheel = Mouse.getEventDWheel();
    if (wheel != 0)
      scroll(
          Mouse.getEventX() * (float) width / mc.displayWidth / scale,
          (height - Mouse.getEventY() * (float) height / mc.displayHeight - 1) / scale,
          wheel > 0 ? -24 : 24);
  }

  @Override
  public void updateScreen() {
    if (!runtime.isRunning()) mc.displayGuiScreen(null);
    else configs.update();
  }

  @Override
  public void onGuiClosed() {
    release();
    persist();
    configs.blur();
    fonts.close();
    KeyBinding.unPressAllKeys();
    Keyboard.enableRepeatEvents(previousRepeat);
  }

  @Override
  public boolean doesGuiPauseGame() {
    return false;
  }
}
