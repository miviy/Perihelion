package perihelion.bootstrap.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.GuiModList;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import perihelion.integration.minecraft189.account.AccountService;
import perihelion.integration.minecraft189.gui.menu.MainMenuScreen;
import perihelion.integration.minecraft189.gui.menu.MenuTheme;

/** Runtime-owned title-screen replacement; Forge's mod list stays at the loader boundary. */
final class ForgeMainMenuAdapter implements AutoCloseable {
  private final AccountService accounts;
  private final MenuTheme theme;
  private final perihelion.integration.minecraft189.gui.ClickGuiService gui;

  ForgeMainMenuAdapter(
      AccountService accounts, perihelion.integration.minecraft189.gui.ClickGuiService gui) {
    this.accounts = accounts;
    this.gui = gui;
    theme = new MenuTheme(accounts.runtime);
    MinecraftForge.EVENT_BUS.register(this);
    accounts.runtime.own(this);
  }

  @SubscribeEvent
  public void opened(GuiOpenEvent event) {
    if (accounts.runtime.isRunning()
        && event.gui != null
        && event.gui.getClass() == GuiMainMenu.class) {
      event.gui =
          new MainMenuScreen(
              accounts,
              parent -> Minecraft.getMinecraft().displayGuiScreen(new GuiModList(parent)),
              gui::open);
    }
    theme.opened(event.gui, Minecraft.getMinecraft().currentScreen);
  }

  @SubscribeEvent
  public void drawing(GuiScreenEvent.DrawScreenEvent.Pre event) {
    theme.beginFrame(event.gui, event.mouseX, event.mouseY);
  }

  @Override
  public void close() {
    MinecraftForge.EVENT_BUS.unregister(this);
    theme.close();
  }
}
