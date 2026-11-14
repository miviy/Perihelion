package perihelion.integration.minecraft189.hud;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import perihelion.core.command.ClientCommand;
import perihelion.core.event.ClientTickEvent;
import perihelion.core.event.HudRenderEvent;
import perihelion.core.event.InputEvent;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.*;
import perihelion.core.setting.ColorValue;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.event.ChatHudEvent;

/** Native settings/lifecycle owner. No HUD listeners or entity caches survive deactivation. */
public final class HudModule extends ClientModule {
  public enum PlayerColumn {
    DISTANCE("Dist.", "Distance", 30),
    HEALTH("HP", "Health", 25),
    TEAM("Team", "Team", 26),
    RANK("Rank", "Rank", 30),
    STARS("Stars", "BedWars stars", 30),
    ENCOUNTERS("Seen", "Encounters", 25),
    KD("K/D", "K/D", 18),
    WS("WS", "Win streak", 19),
    FKD("FK/D", "FK/D", 21),
    WL("W/L", "W/L", 19),
    BBL("BB/L", "Beds broken/lost", 25),
    KILLS("Kills", "Kills", 28),
    FINALS("Finals", "Final kills", 28),
    BEDS("Beds", "Beds broken", 28),
    WINS("Wins", "Wins", 28),
    TAGS("Tags", "Provider tags", 55),
    NICK("Nick", "Nick detection", 28),
    SESSION("Session", "Server session", 40),
    LEVEL("Level", "Network level", 30),
    DAILY_FKD("D FK/D", "Daily FK/D", 38),
    DAILY_WL("D W/L", "Daily W/L", 38),
    DAILY_STARS("D Stars", "Daily stars", 38),
    DAILY_BBL("D BB/L", "Daily beds broken/lost", 38),
    DAILY_KD("D K/D", "Daily K/D", 38),
    WEEKLY_FKD("W FK/D", "Weekly FK/D", 38),
    WEEKLY_WL("W W/L", "Weekly W/L", 38),
    WEEKLY_STARS("W Stars", "Weekly stars", 38),
    WEEKLY_BBL("W BB/L", "Weekly beds broken/lost", 38),
    WEEKLY_KD("W K/D", "Weekly K/D", 38),
    MONTHLY_FKD("M FK/D", "Monthly FK/D", 38),
    MONTHLY_WL("M W/L", "Monthly W/L", 38),
    MONTHLY_STARS("M Stars", "Monthly stars", 38),
    MONTHLY_BBL("M BB/L", "Monthly beds broken/lost", 38),
    MONTHLY_KD("M K/D", "Monthly K/D", 38),
    PING("Ping", "Ping", 32),
    OBSERVED("Online time*", "Observed time", 55),
    SEEN("Seen", "Last seen", 35);
    final String heading, label;
    final int width;

    PlayerColumn(String heading, String label, int width) {
      this.heading = heading;
      this.label = label;
      this.width = width;
    }
  }

  public enum LogoDesign {
    TEXT,
    EMBLEM,
    EMBLEM_AND_NAME
  }

  public enum Element {
    WATERMARK("Logo", 0, 0, true),
    TAB_GUI("Tab GUI", 0, 0, true),
    MODULES("Array list", 1, 0, true),
    INFO("Coordinates", 0, 1, true),
    TARGET("Target card", .5, .60, true),
    ARMOR("Armor", .5, .90, true),
    EFFECTS("Potion effects", 0, .22, true),
    KEYS("Keystrokes", 0, .7, false),
    PLAYERS("Nearby players", 0, 0, false),
    NOTIFICATIONS("Notifications", 1, .82, false);

    final String label;
    final double x, y;
    final boolean shown;

    Element(String label, double x, double y, boolean shown) {
      this.label = label;
      this.x = x;
      this.y = y;
      this.shown = shown;
    }

    String id() {
      return name().toLowerCase(java.util.Locale.ROOT);
    }
  }

  final ClientRuntime runtime;
  final Supplier<EntityLivingBase> target;
  final EnumMap<Element, Setting<Boolean>> shown = new EnumMap<>(Element.class);
  final EnumMap<Element, Setting<Double>> xs = new EnumMap<>(Element.class),
      ys = new EnumMap<>(Element.class);
  final EnumMap<Element, HudElementStyle> styles = new EnumMap<>(Element.class);
  final Setting<HudAppearance.Border> moduleBorder;
  final Setting<HudAppearance.Suffix> moduleSuffix;
  final Setting<Boolean> moduleLowercase, moduleRows;
  final Setting<Integer> modulePadding;
  final String version = clientVersion();
  final Setting<Boolean> watermarkVersion, watermarkClock;
  final Setting<LogoDesign> logoDesign;
  final Setting<HudAppearance.Alignment> moduleAlignment;
  final Setting<HudAppearance.Sort> moduleSort;
  final Setting<Integer> moduleSpacing;
  final Setting<ColorValue> accent, secondary;
  final Setting<Double> opacity;
  final Setting<Boolean> shadow, hideDebug, hideScreens, suffixes, hideVisuals, watermarkInfo;
  final Setting<Boolean> allowChatMove;
  final Setting<Integer> playerLimit, playerRange;
  final Setting<Set<PlayerColumn>> playerColumns;
  final Setting<String> hypixelKeyFile;
  final Setting<HypixelStats.Game> hypixelGame;
  HypixelLookup stats;
  OverlayLookup overlayLookup;
  final OverlaySettings overlay;
  final OverlayEncounters encounters = new OverlayEncounters();
  private HudRenderer renderer;
  OverlayTracker tracker;
  private String encounterServer = "";
  private HudChatInteraction chat;

  public HudModule(ClientRuntime runtime, Supplier<EntityLivingBase> target) {
    super(
        new ModuleInfo(
            "hud",
            "HUD",
            "Expand each HUD element to enable it and edit its content, appearance and position.",
            ModuleCategory.RENDER,
            true));
    this.runtime = runtime;
    this.target = target;
    Runnable guard = runtime.tasks()::checkThread;
    for (Element element : Element.values()) {
      shown.put(
          element,
          setting(
              Setting.bool(
                  element.id(),
                  element.label,
                  "Show " + element.label.toLowerCase(java.util.Locale.ROOT) + ".",
                  element.shown,
                  guard)));
      xs.put(
          element,
          setting(
              Setting.decimal(
                  element.id() + "-x",
                  "Horizontal",
                  "Screen position: 0 is left, 1 is right. Includes this element's width.",
                  element.x,
                  0,
                  1,
                  guard)));
      ys.put(
          element,
          setting(
              Setting.decimal(
                  element.id() + "-y",
                  "Vertical",
                  "Screen position: 0 is top, 1 is bottom. Includes this element's height.",
                  element.y,
                  0,
                  1,
                  guard)));
    }
    moduleAlignment =
        setting(
            Setting.enumeration(
                "module-alignment",
                "Alignment",
                "Align array-list rows within the draggable element.",
                HudAppearance.Alignment.RIGHT,
                HudAppearance.Alignment.class,
                guard));
    moduleSort =
        setting(
            Setting.enumeration(
                "module-sort",
                "Sort",
                "Sort by text width or module name.",
                HudAppearance.Sort.WIDTH,
                HudAppearance.Sort.class,
                guard));
    moduleSpacing =
        setting(
            Setting.integer(
                "module-spacing",
                "Row spacing",
                "Additional pixels between module labels.",
                0,
                0,
                12,
                guard));
    accent =
        setting(
            Setting.color(
                "accent",
                "Accent",
                "Primary accent; supports the native color editor.",
                ColorValue.rgb(143, 87, 191),
                guard));
    secondary =
        setting(
            Setting.color(
                "secondary",
                "Secondary accent",
                "Second end of the accent gradient.",
                ColorValue.rgb(218, 133, 111),
                guard));
    opacity =
        setting(
            Setting.decimal(
                "opacity",
                "Panel opacity",
                "Translucent panel backgrounds.",
                .65,
                0,
                1,
                .05,
                guard));
    logoDesign =
        setting(
            Setting.enumeration(
                "logo-design",
                "Logo design",
                "Choose the client name, the emblem, or the emblem with its custom wordmark.",
                LogoDesign.TEXT,
                LogoDesign.class,
                guard));
    watermarkInfo =
        setting(
            Setting.bool(
                    "watermark-info",
                    "FPS and ping",
                    "Show FPS and ping with the client name.",
                    false,
                    guard)
                .visibleWhen(
                    () ->
                        perihelion.integration.minecraft189.gui.GuiTheme.modern(runtime)
                            || logoDesign.get() != LogoDesign.EMBLEM));
    shadow =
        setting(Setting.bool("shadow", "Text shadow", "Soft shadow behind HUD text.", true, guard));
    hideDebug =
        setting(
            Setting.bool(
                "hide-debug", "Hide with F3", "Keep the debug display clear.", true, guard));
    hideScreens =
        setting(
            Setting.bool(
                "hide-screens",
                "Hide in menus",
                "Hide in other game menus. Chat keeps the HUD visible and interactive.",
                true,
                guard));
    allowChatMove =
        setting(
            Setting.bool(
                "allow-moving-in-chat",
                "Allow moving in chat",
                "Drag HUD elements while chat is open. Player actions and chat input stay"
                    + " available.",
                true,
                guard));
    suffixes =
        setting(
            Setting.bool(
                "suffixes", "Show modes", "Show the module's primary mode in gray.", true, guard));
    hideVisuals =
        setting(
            Setting.bool(
                "hide-visuals",
                "Hide visual modules",
                "Exclude Render and Client modules from the active list.",
                false,
                guard));
    playerLimit =
        setting(
            Setting.integer(
                "player-limit", "Visible rows", "Maximum player rows.", 8, 1, 32, guard));
    playerRange =
        setting(
            Setting.integer(
                "player-range",
                "Range (blocks)",
                "Loaded players only, measured in blocks.",
                64,
                8,
                128,
                guard));
    playerColumns =
        setting(
            Setting.multiSelect(
                "player-columns",
                "Columns",
                "Choose the board columns here. Right-click a player in chat for actions;"
                    + " scroll the table header for columns and rows for players.",
                EnumSet.of(
                    PlayerColumn.DISTANCE,
                    PlayerColumn.HEALTH,
                    PlayerColumn.TEAM,
                    PlayerColumn.KD,
                    PlayerColumn.WS,
                    PlayerColumn.FKD,
                    PlayerColumn.WL,
                    PlayerColumn.OBSERVED,
                    PlayerColumn.SEEN),
                PlayerColumn.class,
                guard));
    hypixelKeyFile =
        setting(
            Setting.string(
                "hypixel-key-file",
                "Hypixel key file",
                "Path to a text file containing your API key, relative to the Minecraft folder."
                    + " The key itself is never saved in presets.",
                "config/perihelion/hypixel-api-key.txt",
                1024,
                guard));
    hypixelGame =
        setting(
            Setting.enumeration(
                "hypixel-game",
                "Stats game",
                "Game used for cached K/D, FK/D, W/L and streak columns.",
                HypixelStats.Game.BEDWARS,
                HypixelStats.Game.class,
                guard));
    for (Element element : Element.values())
      styles.put(element, new HudElementStyle(this, element, value -> setting(value)));
    moduleBorder =
        setting(
            Setting.enumeration(
                "module-border",
                "Accent border",
                "Optional accent edges; None keeps a clean text list.",
                HudAppearance.Border.NONE,
                HudAppearance.Border.class,
                guard));
    moduleSuffix =
        setting(
            Setting.enumeration(
                "module-suffix",
                "Mode separator",
                "How module modes are separated from their names.",
                HudAppearance.Suffix.SPACE,
                HudAppearance.Suffix.class,
                guard));
    moduleLowercase =
        setting(
            Setting.bool(
                "module-lowercase",
                "Lowercase",
                "Use lowercase module names and modes.",
                false,
                guard));
    moduleRows =
        setting(
            Setting.bool(
                "module-row-backgrounds",
                "Fit background to text",
                "A connected stepped silhouette around module names; disable for one rectangular"
                    + " panel.",
                true,
                guard));
    modulePadding =
        setting(
            Setting.integer(
                "module-padding",
                "Row padding",
                "Space on each side of an Array list row.",
                2,
                1,
                12,
                guard));
    watermarkVersion =
        setting(
            Setting.bool(
                    "watermark-version",
                    "Version",
                    "Append the packaged client version to the logo.",
                    true,
                    guard)
                .visibleWhen(() -> logoDesign.get() != LogoDesign.EMBLEM));
    watermarkClock =
        setting(
            Setting.bool(
                    "watermark-clock", "Clock", "Append the local time to the logo.", false, guard)
                .visibleWhen(() -> logoDesign.get() != LogoDesign.EMBLEM));
    overlay = new OverlaySettings(this, value -> setting(value));
    moduleSuffix.visibleWhen(suffixes::get);
    for (Setting<?> appearance : new Setting<?>[] {accent, secondary, opacity, shadow})
      appearance.visibleWhen(
          () -> !perihelion.integration.minecraft189.gui.GuiTheme.modern(runtime));
    runtime
        .commands()
        .register(
            new ClientCommand() {
              public String name() {
                return "hud";
              }

              public String usage() {
                return "hud [edit|on|off|reset|refresh|unhide]";
              }

              public void execute(List<String> args, Consumer<String> output) {
                String action =
                    args.isEmpty() ? "edit" : args.get(0).toLowerCase(java.util.Locale.ROOT);
                if (args.size() > 1)
                  throw new IllegalArgumentException("Use .hud [edit|on|off|reset|refresh|unhide]");
                switch (action) {
                  case "edit":
                    if (!openEditor(null))
                      throw new IllegalArgumentException("Join a world to edit the HUD.");
                    break;
                  case "on":
                    runtime.modules().setEnabled("hud", true);
                    output.accept("HUD enabled.");
                    break;
                  case "off":
                    runtime.modules().setEnabled("hud", false);
                    output.accept("HUD disabled.");
                    break;
                  case "refresh":
                    if (overlayLookup != null) overlayLookup.invalidate();
                    if (stats != null) stats.invalidate();
                    output.accept("Player statistics refreshed.");
                    break;
                  case "unhide":
                    runtime.modules().setSetting("hud", overlay.hidden, "");
                    overlay.removed.clear();
                    output.accept("All players unhidden.");
                    break;
                  case "reset":
                    resetLayout();
                    output.accept("HUD positions reset.");
                    break;
                  default:
                    throw new IllegalArgumentException(
                        "Use .hud [edit|on|off|reset|refresh|unhide]");
                }
              }
            });
  }

  private static String clientVersion() {
    try (java.io.InputStream stream = HudModule.class.getResourceAsStream("/mcmod.info")) {
      if (stream == null) return "";
      com.google.gson.JsonArray mods =
          new com.google.gson.JsonParser()
              .parse(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8))
              .getAsJsonArray();
      if (mods.size() == 0) return "";
      String version = mods.get(0).getAsJsonObject().get("version").getAsString();
      return version.matches("[A-Za-z0-9._+-]{1,32}") ? version : "";
    } catch (java.io.IOException | RuntimeException unavailable) {
      return "";
    }
  }

  @Override
  protected void onEnable(ModuleContext context) {
    stats = context.own(new HypixelLookup(action -> context.deferForWorld(action)));
    overlayLookup = context.own(new OverlayLookup());
    overlay.removed.clear();
    if (!runtime.connection().address().equals(encounterServer)) encounters.clear();
    encounterServer = runtime.connection().address();
    tracker = new OverlayTracker();
    renderer = new HudRenderer(this);
    chat = new HudChatInteraction(this, renderer);
    context.onCleanup(
        () -> {
          try {
            chat.close();
            renderer.close();
          } finally {
            chat = null;
            renderer = null;
            stats = null;
            overlayLookup = null;
            tracker = null;
            overlay.removed.clear();
          }
        });
    context.listen(
        ClientTickEvent.class,
        event -> {
          if (event == ClientTickEvent.END) {
            overlayLookup.tick(overlay.options());
            renderer.update(false);
          }
        });
    context.listen(InputEvent.class, event -> renderer.input(event));
    context.listen(ChatHudEvent.class, event -> chat.handle(event));
    context.listen(
        perihelion.integration.minecraft189.event.ClientPacketEvent.class,
        event -> {
          if (!overlay.active()
              || !(event.packet() instanceof net.minecraft.network.play.server.S02PacketChat))
            return;
          net.minecraft.network.play.server.S02PacketChat packet =
              (net.minecraft.network.play.server.S02PacketChat) event.packet();
          if (packet.getType() != 2)
            tracker.message(
                packet.getChatComponent().getUnformattedText(), System.currentTimeMillis());
        });
    perihelion.integration.minecraft189.gui.ClickGuiModule appearance =
        perihelion.integration.minecraft189.gui.GuiTheme.preferences(runtime);
    if (appearance != null) {
      context.observe(appearance.font, (setting, before, after) -> chat.cancelDrag());
      context.observe(appearance.visuals, (setting, before, after) -> chat.cancelDrag());
    }
    context.observe(playerColumns, (setting, before, after) -> chat.cancelDrag());
    context.observe(allowChatMove, (setting, before, after) -> chat.cancelDrag());
    context.observe(logoDesign, (setting, before, after) -> chat.cancelDrag());
    context.observe(moduleSpacing, (setting, before, after) -> chat.cancelDrag());
    context.observe(moduleSort, (setting, before, after) -> chat.cancelDrag());
    context.observe(suffixes, (setting, before, after) -> chat.cancelDrag());
    context.observe(hideVisuals, (setting, before, after) -> chat.cancelDrag());
    context.observe(
        hypixelKeyFile,
        (setting, before, after) -> {
          stats.invalidate();
          overlayLookup.invalidate();
          chat.statsInvalidated();
        });
    for (Setting<?> setting : overlay.settings)
      context.observe(
          setting,
          (changed, before, after) -> {
            if (changed == overlay.providers
                || overlay.keys.containsValue(changed)
                || changed == overlay.sessions) overlayLookup.invalidate();
            chat.statsInvalidated();
            chat.cancelDrag();
          });
    for (HudElementStyle style : styles.values())
      for (Setting<?> option : style.settings)
        context.observe(option, (changed, before, after) -> chat.cancelDrag());
    for (Setting<?> option :
        new Setting<?>[] {moduleBorder, moduleSuffix, moduleLowercase, moduleRows, modulePadding})
      context.observe(option, (changed, before, after) -> chat.cancelDrag());
    for (Element element : Element.values())
      context.observe(
          shown.get(element),
          (setting, before, after) -> {
            if (!after) chat.elementHidden(element);
          });
    context.listen(
        HudRenderEvent.class,
        event -> {
          Minecraft mc = Minecraft.getMinecraft();
          if (mc.currentScreen instanceof HudEditor
              || mc.currentScreen instanceof GuiChat
              || (hideScreens.get() && mc.currentScreen != null && !renderer.navigationFocused())
              || (hideDebug.get() && mc.gameSettings.showDebugInfo)) return;
          renderer.render(event.scaledWidth(), event.scaledHeight(), false, null, 0, 0);
        });
  }

  List<Setting<?>> options(Element element) {
    java.util.ArrayList<Setting<?>> result = new java.util.ArrayList<>();
    result.addAll(styles.get(element).settings);
    switch (element) {
      case WATERMARK:
        result.add(logoDesign);
        java.util.Collections.addAll(result, watermarkVersion, watermarkClock, watermarkInfo);
        break;
      case MODULES:
        java.util.Collections.addAll(
            result,
            moduleAlignment,
            moduleSort,
            moduleSpacing,
            modulePadding,
            moduleRows,
            moduleBorder,
            suffixes,
            moduleSuffix,
            moduleLowercase,
            hideVisuals);
        break;
      case PLAYERS:
        java.util.Collections.addAll(
            result, playerLimit, playerRange, playerColumns, hypixelGame, hypixelKeyFile);
        result.addAll(overlay.settings);
        break;
      default:
        break;
    }
    return result;
  }

  public boolean openEditor(GuiScreen parent) {
    return openEditor(parent, false);
  }

  public boolean openArrangement(GuiScreen parent) {
    return openEditor(parent, true);
  }

  private boolean openEditor(GuiScreen parent, boolean arrangeOnly) {
    runtime.tasks().checkThread();
    Minecraft mc = Minecraft.getMinecraft();
    if (!runtime.isRunning() || mc.theWorld == null || mc.thePlayer == null) return false;
    KeyBinding.unPressAllKeys();
    runtime.input().setAllowed(false, java.util.Collections.emptyList());
    mc.displayGuiScreen(new HudEditor(this, parent, arrangeOnly));
    return true;
  }

  public void resetLayout() {
    if (chat != null) chat.cancelDrag();
    for (Element e : Element.values()) resetPosition(e);
  }

  void resetPosition(Element element) {
    if (chat != null) chat.cancelDrag();
    runtime.modules().setSetting("hud", xs.get(element), element.x);
    runtime.modules().setSetting("hud", ys.get(element), element.y);
  }

  void resetAppearance(Element element) {
    for (Setting<?> setting : styles.get(element).settings) resetSetting(setting);
    if (element == Element.MODULES) resetSetting(moduleBorder);
  }

  private <T> void resetSetting(Setting<T> setting) {
    runtime.modules().setSetting("hud", setting, setting.defaultValue());
  }

  void move(Element e, int x, int y, int width, int height, int w, int h) {
    runtime.modules().setSetting("hud", xs.get(e), HudLayout.fraction(x, width, w));
    runtime.modules().setSetting("hud", ys.get(e), HudLayout.fraction(y, height, h));
  }
}
