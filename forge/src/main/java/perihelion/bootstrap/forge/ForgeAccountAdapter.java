package perihelion.bootstrap.forge;

import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import perihelion.integration.minecraft189.account.*;

/** Loader-owned menu wiring and observed ban status, without donor event infrastructure. */
final class ForgeAccountAdapter implements AutoCloseable {
  private static final int BUTTON = 0x5041;
  private final AccountService accounts;
  private String host = "", uuid = "";

  ForgeAccountAdapter(AccountService accounts) {
    this.accounts = accounts;
    MinecraftForge.EVENT_BUS.register(this);
    accounts.runtime.own(this);
  }

  @SubscribeEvent
  public void menu(GuiScreenEvent.InitGuiEvent.Post event) {
    if (!accounts.runtime.isRunning()
        || event.gui instanceof perihelion.integration.minecraft189.gui.menu.MainMenuScreen
        || !(event.gui instanceof GuiMainMenu
            || event.gui instanceof GuiMultiplayer
            || event.gui instanceof GuiSelectWorld)) return;
    for (GuiButton button : event.buttonList) if (button.id == BUTTON) return;
    int x = Math.max(4, event.gui.width - 90), y = 6;
    for (int attempt = 0; attempt < 12; attempt++) {
      boolean overlap = false;
      for (GuiButton button : event.buttonList)
        if (button.visible
            && x < button.xPosition + button.width
            && x + 84 > button.xPosition
            && y < button.yPosition + button.height
            && y + 18 > button.yPosition) {
          overlap = true;
          break;
        }
      if (!overlap) {
        event.buttonList.add(new AccountMenuButton(accounts, BUTTON, x, y));
        return;
      }
      y += 22;
    }
  }

  @SubscribeEvent
  public void click(GuiScreenEvent.ActionPerformedEvent.Pre event) {
    if (event.button instanceof AccountMenuButton && accounts.runtime.isRunning()) {
      event.setCanceled(true);
      accounts.open(event.gui);
    }
  }

  @SubscribeEvent
  public void opened(GuiOpenEvent event) {
    Minecraft game = Minecraft.getMinecraft();
    if (event.gui instanceof GuiConnecting) {
      ServerData server = game.getCurrentServerData();
      host = server == null ? "" : server.serverIP;
      uuid = game.getSession().getPlayerID();
    }
    if (event.gui instanceof GuiDisconnected
        && AccountBanStatus.hypixel(host)
        && uuid.equals(game.getSession().getPlayerID())) {
      try {
        Field field =
            ReflectionHelper.findField(GuiDisconnected.class, "message", "field_146304_f");
        field.setAccessible(true);
        long until =
            AccountBanStatus.unban(
                ((IChatComponent) field.get(event.gui)).getUnformattedText(),
                System.currentTimeMillis());
        if (until != Long.MIN_VALUE) accounts.recordBan(uuid, until);
      } catch (ReflectiveOperationException ignored) {
      }
    }
    if (event.gui instanceof GuiMainMenu) {
      host = "";
      uuid = "";
    }
  }

  @SubscribeEvent
  public void joined(WorldEvent.Load event) {
    if (!event.world.isRemote) return;
    Minecraft game = Minecraft.getMinecraft();
    if (game.isIntegratedServerRunning()) {
      host = "";
      uuid = "";
      return;
    }
    ServerData server = game.getCurrentServerData();
    if (server != null && AccountBanStatus.hypixel(server.serverIP))
      accounts.recordBan(game.getSession().getPlayerID(), 0);
  }

  @Override
  public void close() {
    MinecraftForge.EVENT_BUS.unregister(this);
    host = "";
    uuid = "";
  }
}
