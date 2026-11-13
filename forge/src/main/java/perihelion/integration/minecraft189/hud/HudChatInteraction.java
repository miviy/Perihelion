package perihelion.integration.minecraft189.hud;

import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.input.Keyboard;
import perihelion.integration.minecraft189.event.ChatHudEvent;
import perihelion.integration.minecraft189.hud.HudModule.Element;
import perihelion.integration.minecraft189.module.gameplay.PlayerSpectator;
import perihelion.integration.minecraft189.module.gameplay.PlayerTags;

/** Interaction state belongs to one active HUD and one chat screen, never to global mouse state. */
final class HudChatInteraction implements AutoCloseable {
  private final HudModule hud;
  private final HudRenderer renderer;
  private final Minecraft mc = Minecraft.getMinecraft();
  private final Object world = mc.theWorld, player = mc.thePlayer;
  private final long generation;
  private final HudDragState drag = new HudDragState();
  private final HudRenderer.Box popup = new HudRenderer.Box();
  private GuiChat screen;
  private int width, height, scroll;

  private enum View {
    ACTIONS,
    STATS,
    DETAILS,
    VIOLATIONS
  }

  private View view = View.ACTIONS;
  private String message = "";
  private HypixelLookup.Result requestedStats;
  private HudRenderer.PlayerRow selected;

  HudChatInteraction(HudModule hud, HudRenderer renderer) {
    this.hud = hud;
    this.renderer = renderer;
    generation = hud.runtime.tasks().generation();
  }

  void handle(ChatHudEvent event) {
    if (event.kind == ChatHudEvent.Kind.CLOSE) {
      if (screen == event.screen) {
        commit();
        clear();
      }
      return;
    }
    if (!validWorld()) {
      clear();
      return;
    }
    if (event.kind == ChatHudEvent.Kind.OPEN) {
      clear();
      attach(event);
      return;
    }
    if (mc.currentScreen != event.screen) return;
    if (screen != event.screen || width != event.width || height != event.height) {
      clear();
      attach(event);
    }
    if (mc.gameSettings.hideGUI || hud.hideDebug.get() && mc.gameSettings.showDebugInfo) {
      cancelDrag();
      popup.visible = false;
      selected = null;
      return;
    }
    switch (event.kind) {
      case DRAW:
        drag.move(event.x, event.y);
        renderer.render(width, height, false, drag.element, drag.x, drag.y);
        drawPopup(event.x, event.y);
        break;
      case MOUSE:
        mouse(event);
        break;
      case KEY:
        if (event.pressed && event.key == Keyboard.KEY_ESCAPE && popup.visible) {
          popup.visible = false;
          selected = null;
          event.cancel();
        }
        break;
      default:
        break;
    }
  }

  private boolean validWorld() {
    return hud.runtime.isRunning()
        && mc.theWorld == world
        && mc.thePlayer == player
        && world != null
        && player != null
        && hud.runtime.tasks().generation() == generation;
  }

  private void attach(ChatHudEvent event) {
    screen = event.screen;
    width = event.width;
    height = event.height;
  }

  private void mouse(ChatHudEvent event) {
    if (drag.element != null) {
      drag.move(event.x, event.y);
      if (event.button == 0 && !event.pressed) commit();
      event.cancel();
      return;
    }
    if (popup.contains(event.x, event.y)) {
      if (event.wheel != 0) {
        int rows = menuRows().size();
        scroll =
            Math.max(
                0,
                Math.min(
                    Math.max(0, rows - Math.max(1, (popup.h - 76) / 12)),
                    scroll + (event.wheel > 0 ? -1 : 1)));
        event.cancel();
        return;
      }
      if (event.button < 0 || !event.pressed) return;
      if (event.y < popup.y + 14 && event.x >= popup.x + popup.w - 18 || event.button == 1) {
        popup.visible = false;
        selected = null;
      } else if (event.button == 0 && event.y >= popup.y + 76) {
        int row = scroll + (event.y - popup.y - 76) / 12;
        if (row < menuRows().size()) action(row);
      }
      event.cancel();
      return;
    }
    if (event.y < height - 14 && renderer.scrollPlayers(event.x, event.y, event.wheel)) {
      event.cancel();
      return;
    }
    if (event.button < 0 || !event.pressed) return;
    popup.visible = false;
    selected = null;
    // Chat's text entry remains an ordinary vanilla control even if a saved HUD overlaps it.
    if (event.y >= height - 14) return;
    Element[] elements = Element.values();
    for (int i = elements.length - 1; i >= 0; i--) {
      Element element = elements[i];
      HudRenderer.Box box = renderer.boxes.get(element);
      if (!box.contains(event.x, event.y)) continue;
      if (event.button == 0) {
        drag.begin(
            element,
            event.x,
            event.y,
            box.anchorX,
            box.anchorY,
            width,
            height,
            box.anchorW,
            box.anchorH);
        event.cancel();
      } else if (event.button == 1 && element == Element.PLAYERS) {
        selected = renderer.playerAt(event.x, event.y);
        if (selected == null) return;
        view = View.ACTIONS;
        message = "";
        requestedStats = null;
        scroll = 0;
        popup.w = Math.max(1, Math.min(190, width - 4));
        popup.h = Math.max(77, Math.min(76 + 12 * menuRows().size(), height - 18));
        popup.x = Math.max(0, Math.min(Math.max(0, width - popup.w - 2), event.x + 3));
        popup.y = Math.max(0, Math.min(Math.max(0, height - popup.h - 16), event.y + 3));
        popup.visible = true;
        event.cancel();
      }
      return;
    }
  }

  private void drawPopup(int x, int y) {
    if (!popup.visible) return;
    HudRenderer.PlayerRow current = renderer.player(selected.id);
    if (current != null) selected = current;
    List<String> rows = menuRows();
    String title = selected.name;
    popup.h = Math.max(77, Math.min(76 + 12 * rows.size(), height - 18));
    popup.y = Math.max(0, Math.min(popup.y, height - popup.h - 16));
    scroll = Math.min(scroll, Math.max(0, rows.size() - Math.max(1, (popup.h - 76) / 12)));
    int hover = popup.contains(x, y) && y >= popup.y + 76 ? scroll + (y - popup.y - 76) / 12 : -1;
    HypixelLookup.Result profile = hud.stats == null ? null : hud.stats.get(selected.id);
    renderer.popup(selected, profile == null ? null : profile.stats, rows, popup, scroll, hover);
  }

  private PlayerSpectator spectator() {
    return (PlayerSpectator) hud.runtime.modules().require("freecam").module();
  }

  private List<String> menuRows() {
    List<String> rows = new ArrayList<>();
    if (selected == null) return rows;
    if (view == View.ACTIONS) {
      rows.add("Tags");
      rows.add(
          (PlayerTags.whitelisted(hud.runtime, selected.username) ? "[+] " : "[-] ") + "Whitelist");
      rows.add((renderer.tagged(selected.id) ? "[+] " : "[-] ") + "Target");
      rows.add(renderer.pinned(selected.id) ? "Unpin player" : "Pin player to top");
      rows.add("Analysis");
      rows.add(selected.id.equals(spectator().spectating()) ? "Stop spectating" : "Spectate");
      rows.add("Violations");
      rows.add("Player details");
      rows.add("Hypixel");
      rows.add("Statistics");
      rows.add(hypixel() ? "Report" : "Report (Hypixel only)");
      rows.add("Hide player");
      if (!message.isEmpty()) rows.add(message);
    } else if (view == View.DETAILS) {
      rows.add("< Back to player actions");
      rows.addAll(detailRows());
    } else if (view == View.VIOLATIONS) {
      rows.add("< Back to player actions");
      rows.add("Violation data unavailable");
      OverlayProfile extra = selected.profile;
      if (extra == null || extra.tags.isEmpty())
        rows.add(
            extra != null && extra.tagsAvailable
                ? "Providers returned no tags"
                : "Tag data unavailable; configure provider keys");
      else
        for (OverlayProfile.Tag tag : extra.tags) {
          rows.add(tag.source + ": " + tag.type);
          if (!tag.reason.isEmpty()) rows.add(tag.reason);
        }
    } else {
      rows.add("< Back to player actions");
      rows.add(
          hud.hypixelGame.get() == HypixelStats.Game.BEDWARS
              ? "Bed Wars - overall"
              : "SkyWars - overall");
      if (selected.profile != null && hud.hypixelGame.get() == HypixelStats.Game.BEDWARS) {
        OverlayProfile profile = selected.profile;
        rows.add("Sources: " + (profile.provider.isEmpty() ? "unavailable" : profile.provider));
        rows.add("BedWars mode: " + hud.overlay.mode.get());
        for (HudModule.PlayerColumn column :
            new HudModule.PlayerColumn[] {
              HudModule.PlayerColumn.STARS, HudModule.PlayerColumn.KD, HudModule.PlayerColumn.FKD,
              HudModule.PlayerColumn.WL, HudModule.PlayerColumn.BBL, HudModule.PlayerColumn.WS,
              HudModule.PlayerColumn.SESSION, HudModule.PlayerColumn.LEVEL,
                  HudModule.PlayerColumn.NICK
            })
          rows.add(
              column.label
                  + ": "
                  + profile.column(hud.overlay.mode.get(), column, System.currentTimeMillis()));
        for (java.util.Map.Entry<String, String> entry : profile.periods.entrySet())
          rows.add(entry.getKey() + ": " + entry.getValue());
        for (OverlayProfile.Tag tag : profile.tags)
          rows.add(tag.source + ": " + tag.type + " " + tag.reason);
        if (!profile.message.isEmpty()) rows.add(profile.message);
        return rows;
      }
      HypixelLookup.Result result = hud.stats.get(selected.id);
      if (result == null) result = requestedStats;
      if (result == null) rows.add("Select View Hypixel stats to load");
      else if (result.stats == null) rows.add(result.message);
      else {
        HypixelStats stats = result.stats;
        HypixelStats.Game game = hud.hypixelGame.get();
        rows.add(
            "Kills / deaths: " + stats.count(game, "kills") + " / " + stats.count(game, "deaths"));
        rows.add("K/D: " + stats.ratio(game, "kills", "deaths"));
        rows.add(
            "Final kills / deaths: "
                + stats.count(game, "final_kills")
                + " / "
                + stats.count(game, "final_deaths"));
        rows.add("FK/D: " + stats.ratio(game, "final_kills", "final_deaths"));
        rows.add(
            "Wins / losses: " + stats.count(game, "wins") + " / " + stats.count(game, "losses"));
        rows.add("W/L: " + stats.ratio(game, "wins", "losses"));
        rows.add("Win streak: " + stats.count(game, "winstreak"));
        rows.add("Unavailable fields show --");
      }
    }
    return rows;
  }

  private void action(int row) {
    if (view != View.ACTIONS) {
      if (row == 0) {
        view = View.ACTIONS;
        scroll = 0;
      }
      return;
    }
    switch (row) {
      case 1:
        PlayerTags.toggleWhitelist(hud.runtime, selected.username);
        break;
      case 2:
        renderer.toggleTarget(selected.id);
        break;
      case 6:
        view = View.VIOLATIONS;
        scroll = 0;
        break;
      case 5:
        if (selected.id.equals(spectator().spectating())) {
          hud.runtime.modules().setEnabled("freecam", false);
          message = "Returned to your camera";
        } else if (spectator().spectate(selected.id)) {
          message = "Close chat to fly. Freecam returns you";
        } else message = "Player is no longer loaded";
        break;
      case 10:
        EntityPlayer live = mc.theWorld.getPlayerEntityByUUID(selected.id);
        String command = PlayerReport.command(hypixel(), live == null ? null : live.getName());
        if (command == null) message = "Report requires Hypixel and a loaded player";
        else {
          popup.visible = false;
          mc.thePlayer.sendChatMessage(command);
        }
        break;
      case 3:
        renderer.togglePin(selected.id);
        break;
      case 11:
        hud.overlay.hide(hud, selected.username);
        popup.visible = false;
        break;
      case 9:
        if (hud.overlayLookup != null && OverlaySettings.hypixel())
          hud.overlayLookup.request(selected.id, selected.username, hud.overlay.options());
        try {
          Path file = Paths.get(hud.hypixelKeyFile.get());
          if (!file.isAbsolute()) file = mc.mcDataDir.toPath().resolve(file);
          requestedStats = hud.stats.request(selected.id, file);
        } catch (InvalidPathException invalid) {
          requestedStats = new HypixelLookup.Result(null, "Invalid Hypixel key file path", 0);
        }
        view = View.STATS;
        scroll = 0;
        break;
      case 7:
        view = View.DETAILS;
        scroll = 0;
        break;
      default:
        break;
    }
  }

  private boolean hypixel() {
    return !mc.isSingleplayer()
        && mc.getCurrentServerData() != null
        && PlayerReport.hypixel(mc.getCurrentServerData().serverIP);
  }

  private List<String> detailRows() {
    List<String> rows = new ArrayList<>();
    if (selected == null) return rows;
    rows.add("Health: " + selected.healthPoints + " (" + selected.health + ")");
    rows.add("Team: " + (selected.team.isEmpty() ? "--" : selected.team));
    rows.add("Distance: " + selected.distance + " blocks");
    rows.add("Ping: " + (selected.ping < 0 ? "--" : selected.ping + " ms"));
    rows.add("Armor: " + (selected.armor < 0 ? "--" : selected.armor + " / 20"));
    rows.add("Held: " + selected.held);
    if (hud.tracker != null) {
      String name = selected.username.toLowerCase(java.util.Locale.ROOT);
      if (hud.tracker.party.contains(name)) rows.add("In your party (party list)");
      else if (hud.tracker.possibleParty.contains(name)) rows.add("Possible group (join timing)");
    }
    rows.add("Encounters: " + selected.encounters);
    rows.add(
        "Observed: "
            + HudLayout.duration((selected.seenAt - selected.observedAt) / 1_000_000_000L));
    long age = Math.max(0, (System.nanoTime() - selected.seenAt) / 1_000_000_000L);
    rows.add("Seen: " + (age == 0 ? "now" : age + "s ago"));
    return rows;
  }

  private void commit() {
    if (drag.element != null && validWorld())
      hud.move(
          drag.element, drag.x, drag.y, drag.width, drag.height, drag.boxWidth, drag.boxHeight);
    drag.cancel();
  }

  void cancelDrag() {
    drag.cancel();
  }

  void statsInvalidated() {
    requestedStats = null;
  }

  void elementHidden(Element element) {
    if (drag.element == element) cancelDrag();
    if (element == Element.PLAYERS) {
      popup.visible = false;
      selected = null;
    }
  }

  private void clear() {
    drag.cancel();
    popup.visible = false;
    selected = null;
    screen = null;
    scroll = 0;
    requestedStats = null;
    message = "";
  }

  public void close() {
    clear();
  }
}
