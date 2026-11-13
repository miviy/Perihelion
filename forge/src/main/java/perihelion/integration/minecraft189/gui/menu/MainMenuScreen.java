package perihelion.integration.minecraft189.gui.menu;

import java.io.IOException;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import org.lwjgl.input.Keyboard;
import perihelion.integration.minecraft189.account.AccountService;
import perihelion.integration.minecraft189.gui.ClickGuiModule;
import perihelion.integration.minecraft189.gui.GuiTheme;
import perihelion.integration.minecraft189.gui.font.GuiFonts;
import perihelion.integration.minecraft189.gui.widget.UiCanvas;
import perihelion.integration.minecraft189.render.RenderState;
import perihelion.integration.minecraft189.render.RenderStyle;

/** Native title navigation with a screen-owned animated black hole and soft button surfaces. */
public final class MainMenuScreen extends GuiScreen {
  private static final int ACCOUNTS = 0x5041;
  private static final int CLIENT_SETTINGS = 0x5042;
  private final AccountService accounts;
  private final Consumer<GuiScreen> openMods;
  private final Consumer<GuiScreen> openGui;
  private final GuiFonts fonts = new GuiFonts();
  private final UiCanvas canvas = new UiCanvas();
  private final RenderState state = new RenderState();
  private final BlackHoleBackground background = new BlackHoleBackground();
  private final long animationStart = System.nanoTime();
  private final MenuMotion motion = new MenuMotion();
  private final MenuWordmark wordmark = new MenuWordmark();
  private int left, top, selected = -1;

  public MainMenuScreen(
      AccountService accounts, Consumer<GuiScreen> openMods, Consumer<GuiScreen> openGui) {
    this.accounts = accounts;
    this.openMods = openMods;
    this.openGui = openGui;
  }

  @Override
  public void initGui() {
    // Do not initialize vanilla's unused panorama textures or Realms notification overlay.
    buttonList.clear();
    mc.gameSettings.showDebugInfo = false;
    selected = -1;
    left = width >= 400 ? Math.round(width * .11f) : (width - 176) / 2;
    top = Math.max(10, (height - 200) / 2);
    int y = top + 50;
    add(1, left, y, 176, 26, "Singleplayer", true);
    add(2, left, y + 32, 176, 26, "Multiplayer", false);
    add(ACCOUNTS, left, y + 64, 85, 22, "Accounts", false);
    add(6, left + 91, y + 64, 85, 22, "Mods", false);
    add(0, left, y + 92, 85, 22, "Options", false);
    add(4, left + 91, y + 92, 85, 22, "Quit", false);
    add(CLIENT_SETTINGS, left, y + 120, 176, 22, "Client Settings", false);
    add(5, width - 74, height - 23, 62, 16, "Language", false);
  }

  private void add(int id, int x, int y, int width, int height, String label, boolean primary) {
    buttonList.add(new MenuButton(id, x, y, width, height, label, primary));
  }

  @Override
  protected void actionPerformed(GuiButton button) throws IOException {
    if (!button.enabled) return;
    selected = -1;
    switch (button.id) {
      case 1:
        mc.displayGuiScreen(new GuiSelectWorld(this));
        break;
      case 2:
        mc.displayGuiScreen(new GuiMultiplayer(this));
        break;
      case ACCOUNTS:
        accounts.open(this);
        break;
      case CLIENT_SETTINGS:
        mc.displayGuiScreen(new ClientSettingsScreen(accounts.runtime, this, openGui));
        break;
      case 6:
        openMods.accept(this);
        break;
      case 0:
        mc.displayGuiScreen(new GuiOptions(this, mc.gameSettings));
        break;
      case 5:
        mc.displayGuiScreen(new GuiLanguage(this, mc.gameSettings, mc.getLanguageManager()));
        break;
      case 4:
        mc.shutdown();
        break;
      default:
        break;
    }
  }

  @Override
  public void drawScreen(int mouseX, int mouseY, float partialTicks) {
    long now = System.nanoTime();
    motion.update(mouseX, mouseY, width, height);
    try (RenderState ignored = state.open()) {
      RenderStyle.text();
      canvas.pixelScale = mc.displayWidth / (float) width;
      if (GuiTheme.modern(accounts.runtime))
        GuiTheme.configure(
            canvas, fonts, GuiTheme.preferences(accounts.runtime), canvas.pixelScale);
      else {
        canvas.modern = false;
        canvas.font = fonts.get(ClickGuiModule.Font.SANS);
      }
      background.render(
          canvas,
          width,
          height,
          (now - animationStart) / 1_000_000_000f,
          motion.x,
          motion.y,
          false);
      wordmark.drawCentered(left + 88, top + 36, (now - animationStart) / 1_000_000_000f);
      for (GuiButton button : buttonList) button.drawButton(mc, mouseX, mouseY);
    }
  }

  @Override
  protected void keyTyped(char typedChar, int keyCode) throws IOException {
    if (keyCode == Keyboard.KEY_TAB || keyCode == Keyboard.KEY_DOWN || keyCode == Keyboard.KEY_UP) {
      boolean backwards =
          keyCode == Keyboard.KEY_UP || (keyCode == Keyboard.KEY_TAB && isShiftKeyDown());
      selected =
          selected < 0
              ? (backwards ? buttonList.size() - 1 : 0)
              : (selected + (backwards ? -1 : 1) + buttonList.size()) % buttonList.size();
    } else if ((keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER)
        && selected >= 0) {
      GuiButton button = buttonList.get(selected);
      button.playPressSound(mc.getSoundHandler());
      actionPerformed(button);
    }
  }

  @Override
  protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
    selected = -1;
    super.mouseClicked(mouseX, mouseY, mouseButton);
  }

  @Override
  public void onGuiClosed() {
    try {
      background.close();
      wordmark.close();
    } finally {
      fonts.close();
    }
  }

  private final class MenuButton extends GuiButton {
    private final boolean primary;
    private float hoverAmount;

    MenuButton(int id, int x, int y, int width, int height, String label, boolean primary) {
      super(id, x, y, width, height, label);
      this.primary = primary;
    }

    @Override
    public void drawButton(Minecraft game, int mouseX, int mouseY) {
      if (!visible) return;
      hovered =
          mouseX >= xPosition
              && mouseY >= yPosition
              && mouseX < xPosition + width
              && mouseY < yPosition + height;
      boolean active = enabled && (hovered || (selected >= 0 && buttonList.get(selected) == this));
      hoverAmount += ((active ? 1 : 0) - hoverAmount) * (1 - (float) Math.exp(-motion.delta * 14));
      MenuSurface.button(
          canvas, xPosition, yPosition, width, height, primary, enabled, hoverAmount);
      GuiFonts.Face face = canvas.modern ? canvas.font : fonts.menu();
      face.drawCentered(
          displayString,
          xPosition,
          yPosition,
          width,
          height,
          !enabled ? MenuSurface.MUTED : MenuSurface.blend(0xffd4d7de, 0xfff0eae0, hoverAmount));
    }
  }
}
