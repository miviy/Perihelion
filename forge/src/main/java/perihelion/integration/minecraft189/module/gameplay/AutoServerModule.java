// SPDX-License-Identifier: AGPL-3.0-only
// Adapted from Yuri 1cb86ad; Copyright (c) 2026 Unlegit Inc.
package perihelion.integration.minecraft189.module.gameplay;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.gui.GuiScreenBook;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StringUtils;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.*;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.event.*;

final class AutoServerModule extends GameplayModule {

  private static final String[] REGISTER_KEYWORDS = {
    "/register",
    "/reg",
    "/registrar",
    "/зарег",
    "/rejestracja",
    "/cadastrar",
    "/kayit",
    "/enregistrer"
  };

  private static final String[] EXCUSES = {
    "%s is hacking, reported",
    "%s is definitely cheating",
    "reported %s for hacks, no way that was legit",
    "%s is using a hacked client",
    "%s you're cheating, reported",
    "how is %s not banned yet"
  };

  private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
  private static final long REPORT_DELAY_MS = 1500L;

  public enum Mode {
    REGISTER("Register"),
    AUTO_PLAY("Auto Play"),
    POLICIES("Auto Policies"),
    AUTO_REPORT("Auto Report"),
    AUTO_EXCUSE("Auto Excuse");

    private final String name;

    Mode(String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  private final Setting<java.util.Set<Mode>> modes =
      setting(
          Setting.multiSelect(
              "modes",
              "Modes",
              "Enabled server helpers.",
              java.util.EnumSet.of(Mode.REGISTER, Mode.AUTO_PLAY),
              Mode.class,
              guard));
  private final Setting<String> password =
      text("password", "Password", "Password for server registration.", "yuri420", 128)
          .visibleWhen(() -> modes.get().contains(Mode.REGISTER));
  private final Setting<Boolean> doublePassword =
      bool("double-password", "Double Password", true)
          .visibleWhen(() -> modes.get().contains(Mode.REGISTER));
  private final java.util.ArrayDeque<String> reports = new java.util.ArrayDeque<>();
  private long nextReport;

  AutoServerModule(ClientRuntime r) {
    super(
        r,
        "autoserver",
        "Auto Server",
        "Registration, replay, policy and report helpers.",
        ModuleCategory.MISC);
  }

  @Override
  protected void onEnable(ModuleContext c) {
    acceptedPolicies = false;
    reporting = false;
    reports.clear();
    nextReport = 0;
    c.onCleanup(
        () -> {
          reports.clear();
          reporting = false;
        });
    c.listen(ClientPacketEvent.class, this::packet);
    c.listen(
        PlayerControlEvent.class,
        e -> {
          if (e == PlayerControlEvent.BEFORE_INPUT && !reports.isEmpty()) {
            if (!modes.get().contains(Mode.AUTO_REPORT)) {
              reports.clear();
              reporting = false;
              return;
            }
            if (now() >= nextReport) {
              mc().thePlayer.sendChatMessage("/report " + reports.removeFirst() + " cheating");
              nextReport = now() + REPORT_DELAY_MS;
              reporting = !reports.isEmpty();
            }
          }
        });
  }

  private final Random random = new Random();
  private boolean acceptedPolicies = false;
  private volatile boolean reporting = false;

  private void packet(ClientPacketEvent event) {
    if (mc().thePlayer == null || !(event.packet() instanceof S02PacketChat)) return;

    S02PacketChat packetChat = (S02PacketChat) event.packet();

    if (modes.get().contains(Mode.REGISTER)) {
      handleRegister(packetChat);
    }

    if (modes.get().contains(Mode.AUTO_PLAY)) {
      handleAutoPlay(packetChat);
    }

    if (modes.get().contains(Mode.POLICIES)) {
      handlePolicies();
    }

    if (modes.get().contains(Mode.AUTO_REPORT)) {
      handleAutoReport(packetChat);
    }

    if (modes.get().contains(Mode.AUTO_EXCUSE)) {
      handleAutoExcuse(packetChat);
    }
  }

  private void handleRegister(S02PacketChat packetChat) {
    String chatComponent = packetChat.getChatComponent().getUnformattedText().toLowerCase();

    String passwordMessage;

    if (doublePassword.get()) {
      passwordMessage = password.get() + " " + password.get();
    } else {
      passwordMessage = password.get();
    }

    for (String keyword : REGISTER_KEYWORDS) {
      if (chatComponent.contains(keyword.toLowerCase())) {
        mc().thePlayer.sendChatMessage(keyword + " " + passwordMessage);
        notify("Auto Server", "Automatically registered!");
        break;
      }
    }
  }

  private void handlePolicies() {
    if (mc().getCurrentServerData() != null
        && mc().getCurrentServerData().serverIP.toLowerCase().contains("hypixel")) {
      if (mc().currentScreen instanceof GuiScreenBook) {
        if (!acceptedPolicies) {
          mc().thePlayer.sendChatMessage("/policies accept");
          acceptedPolicies = true;
          notify("Auto Server", "Policies accepted!");
        }
      } else {
        acceptedPolicies = false;
      }
    }
  }

  private void handleAutoPlay(S02PacketChat chat) {
    if (chat.isChat()) return;

    if (chat.getChatComponent().getUnformattedText().contains("You were spawned in Limbo.")) {
      mc().thePlayer.sendChatMessage("/lobby");
      notify("Auto Server", "Sent to limbo, returned to lobby!");
    }

    if (chat.getChatComponent().getFormattedText().contains("play again?")) {
      for (IChatComponent iChatComponent : chat.getChatComponent().getSiblings()) {
        for (String value : iChatComponent.toString().split("'")) {
          if (value.startsWith("/play") && !value.contains(".")) {
            mc().thePlayer.sendChatMessage(value);
            notify("Auto Server", "Started a new game!");
            break;
          }
        }
      }
    }
  }

  private void handleAutoReport(S02PacketChat chat) {
    if (reporting) return;

    String raw = chat.getChatComponent().getUnformattedText();

    if (!gameStarted(raw)) return;

    if (mc().getNetHandler() == null) return;

    String self = mc().thePlayer.getName();
    List<String> targets = new ArrayList<>();

    for (NetworkPlayerInfo info : new ArrayList<>(mc().getNetHandler().getPlayerInfoMap())) {
      String name = info.getGameProfile().getName();

      if (name == null
          || name.equalsIgnoreCase(self)
          || !PLAYER_NAME.matcher(name).matches()
          || targets.contains(name)) continue;

      targets.add(name);
    }

    if (targets.isEmpty()) return;

    reporting = true;

    for (String target : targets) {
      if (reports.size() >= 256) break;
      reports.addLast(target);
    }
    nextReport = now();

    notify("Auto Server", "Reporting " + targets.size() + " players!");
  }

  private void handleAutoExcuse(S02PacketChat chat) {
    String stripped =
        StringUtils.stripControlCodes(chat.getChatComponent().getUnformattedText()).trim();
    String self = mc().thePlayer.getName();

    if (!stripped.startsWith(self + " ")) return;

    Matcher matcher =
        Pattern.compile(
                "^"
                    + Pattern.quote(self)
                    + " (?:was|got) .*?\\b(?:by|from) ([A-Za-z0-9_]{1,16})\\b")
            .matcher(stripped);

    if (!matcher.find()) return;

    String killer = matcher.group(1);

    if (killer.equalsIgnoreCase(self)) return;

    mc().thePlayer.sendChatMessage(String.format(EXCUSES[random.nextInt(EXCUSES.length)], killer));
  }

  private void notify(String module, String text) {
    mc().thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText("[Perihelion] " + text));
  }

  private static boolean gameStarted(String raw) {
    String stripped = StringUtils.stripControlCodes(raw);

    if (stripped.startsWith(" ")
        && (stripped.contains("Protect your bed and destroy the enemy beds.")
            || stripped.contains("Goodluck with your BedWars Game"))) {
      return true;
    }

    switch (raw) {
      case "Cages opened! FIGHT!":
      case "§r§r§r                               §r§f§lSkyWars Duel§r":
      case "§r§eCages opened! §r§cFIGHT!§r":
        return true;
      default:
        break;
    }

    String trimmed = stripped.trim();

    return trimmed.equals("Cages opened! FIGHT!") || trimmed.equals("SkyWars Duel");
  }
}
