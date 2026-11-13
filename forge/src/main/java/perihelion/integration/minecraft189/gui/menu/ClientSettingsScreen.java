package perihelion.integration.minecraft189.gui.menu;

import java.io.IOException;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.integration.minecraft189.gui.ClickGuiModule;
import perihelion.integration.minecraft189.gui.GuiTheme;
import perihelion.integration.minecraft189.gui.font.GuiFonts;
import perihelion.integration.minecraft189.gui.modern.ModernTheme;
import perihelion.integration.minecraft189.gui.widget.UiCanvas;
import perihelion.integration.minecraft189.render.RenderState;
import perihelion.integration.minecraft189.render.RenderStyle;

/** The title's small, keyboard-accessible entry point to both visual generations. */
public final class ClientSettingsScreen extends GuiScreen {
  private final ClientRuntime runtime;
  private final GuiScreen parent;
  private final Consumer<GuiScreen> openGui;
  private final ClickGuiModule preferences;
  private final GuiFonts fonts = new GuiFonts();
  private final UiCanvas canvas = new UiCanvas();
  private final RenderState state = new RenderState();
  private final BlackHoleBackground background = new BlackHoleBackground();
  private final MenuMotion motion = new MenuMotion();
  private final long started = System.nanoTime();
  private int left, top, panelWidth, selected = -1;

  public ClientSettingsScreen(
      ClientRuntime runtime, GuiScreen parent, Consumer<GuiScreen> openGui) {
    this.runtime = runtime;
    this.parent = parent;
    this.openGui = openGui;
    preferences = GuiTheme.preferences(runtime);
  }

  @Override
  public void initGui() {
    panelWidth = Math.min(320, width - 16);
    left = (width - panelWidth) / 2;
    top = Math.max(4, (height - 218) / 2);
    int half = (panelWidth - 40) / 2;
    buttonList.clear();
    buttonList.add(new Button(0, left + 16, top + 49, panelWidth - 32, 26, "Open GUI"));
    buttonList.add(new Button(1, left + 16, top + 111, half, 28, "Old visuals"));
    buttonList.add(new Button(2, left + 24 + half, top + 111, half, 28, "New visuals"));
    buttonList.add(new Button(3, left + 16, top + 176, panelWidth - 32, 24, "Done"));
  }

  public void choose(ClickGuiModule.Visuals visuals) {
    runtime.modules().setSetting("clickgui", preferences.visuals, visuals);
  }

  @Override
  protected void actionPerformed(GuiButton button) {
    if (!runtime.isRunning()) return;
    if (button.id == 0) openGui.accept(this);
    else if (button.id == 1) choose(ClickGuiModule.Visuals.OLD);
    else if (button.id == 2) choose(ClickGuiModule.Visuals.NEW);
    else if (button.id == 3) mc.displayGuiScreen(parent);
  }

  @Override
  public void drawScreen(int mouseX, int mouseY, float partialTicks) {
    motion.update(mouseX, mouseY, width, height);
    try (RenderState ignored = state.open()) {
      RenderStyle.text();
      canvas.pixelScale = mc.displayWidth / (float) width;
      if (preferences.modern()) GuiTheme.configure(canvas, fonts, preferences, canvas.pixelScale);
      else {
        canvas.modern = false;
        canvas.font = fonts.get(ClickGuiModule.Font.SANS);
      }
      background.render(
          canvas,
          width,
          height,
          (System.nanoTime() - started) / 1_000_000_000f,
          motion.x,
          motion.y,
          true);
      if (canvas.modern) ModernTheme.surface(canvas, left, top, panelWidth, 216, ModernTheme.PANEL);
      else MenuSurface.rounded(canvas, left, top, panelWidth, 216, 6, 0xf216191e);
      canvas.text("Client Settings", left + 16, top + 16, MenuSurface.TEXT);
      canvas.label(
          "GUI and HUD appearance.",
          left + 16,
          top + 30,
          panelWidth - 32,
          canvas.modern ? canvas.muted : MenuSurface.MUTED);
      canvas.text("Appearance", left + 16, top + 91, MenuSurface.TEXT);
      canvas.label(
          preferences.modern()
              ? "New visuals selected. Changes save automatically."
              : "Old visuals selected. Your previous style is restored.",
          left + 16,
          top + 150,
          panelWidth - 32,
          0xffa5a6ab);
      for (GuiButton button : buttonList) button.drawButton(mc, mouseX, mouseY);
    }
  }

  @Override
  protected void keyTyped(char character, int code) throws IOException {
    if (code == Keyboard.KEY_ESCAPE) mc.displayGuiScreen(parent);
    else if (code == Keyboard.KEY_TAB || code == Keyboard.KEY_DOWN || code == Keyboard.KEY_UP) {
      boolean back = code == Keyboard.KEY_UP || code == Keyboard.KEY_TAB && isShiftKeyDown();
      selected =
          selected < 0
              ? (back ? buttonList.size() - 1 : 0)
              : (selected + (back ? -1 : 1) + buttonList.size()) % buttonList.size();
    } else if ((code == Keyboard.KEY_RETURN || code == Keyboard.KEY_NUMPADENTER) && selected >= 0)
      actionPerformed(buttonList.get(selected));
  }

  @Override
  protected void mouseClicked(int x, int y, int button) throws IOException {
    selected = -1;
    super.mouseClicked(x, y, button);
  }

  @Override
  public void onGuiClosed() {
    background.close();
    fonts.close();
  }

  private final class Button extends GuiButton {
    Button(int id, int x, int y, int w, int h, String label) {
      super(id, x, y, w, h, label);
    }

    @Override
    public void drawButton(Minecraft game, int mx, int my) {
      hovered = UiCanvas.inside(mx, my, xPosition, yPosition, width, height);
      boolean chosen = id == (preferences.modern() ? 2 : 1);
      boolean focus = selected >= 0 && buttonList.get(selected) == this;
      MenuSurface.button(
          canvas, xPosition, yPosition, width, height, chosen, true, hovered || focus ? 1 : 0);
      (canvas.modern ? canvas.font : fonts.menu())
          .drawCentered(
              displayString,
              xPosition,
              yPosition,
              width,
              height,
              chosen || hovered || focus ? MenuSurface.TEXT : 0xffb9bdc5);
    }
  }
}
