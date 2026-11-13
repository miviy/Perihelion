package perihelion.integration.minecraft189.hud;

import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import perihelion.core.setting.Setting;

/**
 * Uses the existing HUD editor/config validation. Only paths, never API credentials, are settings.
 */
final class OverlaySettings {
  enum Source {
    NEARBY,
    TAB_LIST
  }

  enum Sort {
    DISTANCE,
    STARS,
    FKDR,
    WINSTREAK,
    ENCOUNTERS,
    JOIN_ORDER,
    NAME
  }

  final Setting<Boolean> automatic, includeSelf, sessions, descending, highlights, bedwarsOnly;
  final Setting<Source> source;
  final Setting<Sort> sort;
  final Setting<OverlayProfile.Mode> mode;
  final Setting<Set<OverlayLookup.Provider>> providers;
  final Setting<String> hidden;
  final Setting<Double> minFkdr;
  final Setting<Integer> minStars;
  final Map<OverlayLookup.Provider, Setting<String>> keys =
      new EnumMap<>(OverlayLookup.Provider.class);
  final List<Setting<?>> settings = new ArrayList<>();
  final Set<UUID> removed = new HashSet<>();
  private final BooleanSupplier visible;
  final Setting<Boolean> partyDetector,
      removeFinals,
      nickAlerts,
      tagAlerts,
      dodgeWarning,
      autoWho,
      autoParty,
      whoOnly,
      gameResults;
  final Setting<Double> dodgeThreshold;

  OverlaySettings(HudModule hud, Consumer<Setting<?>> register) {
    Runnable guard = hud.runtime.tasks()::checkThread;
    visible = () -> hud.shown.get(HudModule.Element.PLAYERS).get();
    automatic =
        add(
            Setting.bool(
                "overlay-auto-stats",
                "Automatic player stats",
                "Look up displayed Hypixel players using the selected providers.",
                true,
                guard),
            register);
    source =
        add(
            Setting.enumeration(
                "overlay-source",
                "Player source",
                "Tab list includes players outside loaded entity range on Hypixel.",
                Source.TAB_LIST,
                Source.class,
                guard),
            register);
    mode =
        add(
            Setting.enumeration(
                "overlay-bedwars-mode",
                "BedWars stats mode",
                "Overall, solos, doubles, threes, fours or 4v4 statistics.",
                OverlayProfile.Mode.OVERALL,
                OverlayProfile.Mode.class,
                guard),
            register);
    sort =
        add(
            Setting.enumeration(
                "overlay-sort",
                "Player sorting",
                "Pinned players come first. Missing statistics stay last.",
                Sort.FKDR,
                Sort.class,
                guard),
            register);
    descending =
        add(
            Setting.bool(
                "overlay-descending",
                "Highest stats first",
                "Descending numeric statistics; distance remains nearest first.",
                true,
                guard),
            register);
    includeSelf =
        add(
            Setting.bool(
                "overlay-include-self",
                "Show yourself",
                "Include your own player row.",
                false,
                guard),
            register);
    bedwarsOnly =
        add(
            Setting.bool(
                "overlay-bedwars-only",
                "BedWars only",
                "Only run automatic lookups in a BedWars scoreboard, avoiding main-lobby NPCs.",
                true,
                guard),
            register);
    sessions =
        add(
            Setting.bool(
                "overlay-sessions",
                "Daily / weekly / monthly stats",
                "Fetch Bordic session changes in batches of ten; requires a Bordic key file.",
                true,
                guard),
            register);
    highlights =
        add(
            Setting.bool(
                "overlay-highlights",
                "Nick and tag highlights",
                "Highlight suspected nicks and provider-tagged rows.",
                true,
                guard),
            register);
    providers =
        add(
            Setting.multiSelect(
                "overlay-providers",
                "Stats providers",
                "Abyss, Prism and Bordic public stats; optional Hypixel fallback, Urchin and Seraph"
                    + " tags.",
                EnumSet.allOf(OverlayLookup.Provider.class),
                OverlayLookup.Provider.class,
                guard),
            register);
    for (OverlayLookup.Provider provider :
        Arrays.asList(
            OverlayLookup.Provider.BORDIC,
            OverlayLookup.Provider.URCHIN,
            OverlayLookup.Provider.SERAPH))
      keys.put(
          provider,
          add(
              Setting.string(
                  "overlay-" + provider.name().toLowerCase(Locale.ROOT) + "-key-file",
                  provider + " key file",
                  "Local text file containing your own API key, relative to the Minecraft folder.",
                  "config/perihelion/" + provider.name().toLowerCase(Locale.ROOT) + "-api-key.txt",
                  1024,
                  guard),
              register));
    keys.put(OverlayLookup.Provider.HYPIXEL, hud.hypixelKeyFile);
    minFkdr =
        add(
            Setting.decimal(
                "overlay-min-fkdr",
                "Minimum FKDR",
                "Zero disables this filter. Stars and FKDR conditions match either threshold.",
                0,
                0,
                100000,
                guard),
            register);
    minStars =
        add(
            Setting.integer(
                "overlay-min-stars",
                "Minimum stars",
                "Zero disables this filter. Unknown stats remain visible.",
                0,
                0,
                1000000,
                guard),
            register);
    partyDetector =
        add(
            Setting.bool(
                "overlay-party-detector",
                "Possible party groups",
                "Highlight players joining within one second in pregame; timing is only a hint.",
                true,
                guard),
            register);
    removeFinals =
        add(
            Setting.bool(
                "overlay-remove-finals",
                "Remove final-killed players",
                "Hide victims of server FINAL KILL messages until the next game.",
                false,
                guard),
            register);
    nickAlerts =
        add(
            Setting.bool(
                "overlay-nick-alerts",
                "Nick alerts",
                "Local chat notice once per suspected nick.",
                false,
                guard),
            register);
    tagAlerts =
        add(
            Setting.bool(
                "overlay-tag-alerts",
                "Tag reason alerts",
                "Local chat notice with attributed provider tags.",
                false,
                guard),
            register);
    dodgeWarning =
        add(
            Setting.bool(
                "overlay-dodge-warning",
                "Lobby FKDR warning",
                "Show a local pregame warning when known lobby stats exceed the threshold.",
                false,
                guard),
            register);
    dodgeThreshold =
        add(
            Setting.decimal(
                "overlay-dodge-threshold",
                "Lobby FKDR threshold",
                "Average of known overall or selected-mode FKDR values; unavailable profiles are"
                    + " excluded.",
                3,
                0,
                100000,
                guard),
            register);
    autoWho =
        add(
            Setting.bool(
                "overlay-auto-who",
                "Automatic /who",
                "Send /who once after joining BedWars pregame.",
                false,
                guard),
            register);
    autoParty =
        add(
            Setting.bool(
                "overlay-auto-party",
                "Automatic /party list",
                "Request your party list once in pregame to highlight your party.",
                false,
                guard),
            register);
    whoOnly =
        add(
            Setting.bool(
                "overlay-who-only",
                "Use /who roster",
                "Filter the tab roster to the latest ONLINE list when available.",
                false,
                guard),
            register);
    gameResults =
        add(
            Setting.bool(
                "overlay-game-results",
                "Game result stats",
                "Show locally observed kills/finals/beds for your party when the game ends.",
                false,
                guard),
            register);
    hidden =
        add(
            Setting.string(
                "overlay-hidden",
                "Hidden players",
                "Comma-separated names. Right-click a row to hide; clear here or use .hud unhide.",
                "",
                2048,
                guard),
            register);
  }

  private <T> Setting<T> add(Setting<T> setting, Consumer<Setting<?>> register) {
    setting.visibleWhen(visible);
    settings.add(setting);
    register.accept(setting);
    return setting;
  }

  OverlayLookup.Options options() {
    Map<OverlayLookup.Provider, Path> paths = new EnumMap<>(OverlayLookup.Provider.class);
    for (Map.Entry<OverlayLookup.Provider, Setting<String>> key : keys.entrySet()) {
      try {
        if (key.getValue().get().trim().isEmpty()) continue;
        Path path = Paths.get(key.getValue().get());
        paths.put(
            key.getKey(),
            path.isAbsolute() ? path : Minecraft.getMinecraft().mcDataDir.toPath().resolve(path));
      } catch (InvalidPathException invalid) {
        /* Transport reports unavailable credentials. */
      }
    }
    return new OverlayLookup.Options(providers.get(), paths, sessions.get());
  }

  static Set<String> names(String value) {
    Set<String> result = new LinkedHashSet<>();
    for (String name : value.split("[,\\s]+"))
      if (name.matches("[A-Za-z0-9_]{1,16}")) result.add(name.toLowerCase(Locale.ROOT));
    return result;
  }

  boolean hidden(UUID id, String name) {
    return removed.contains(id) || names(hidden.get()).contains(name.toLowerCase(Locale.ROOT));
  }

  void hide(HudModule hud, String name) {
    Set<String> names = names(hidden.get());
    names.add(name.toLowerCase(Locale.ROOT));
    hud.runtime.modules().setSetting("hud", hidden, String.join(",", names));
  }

  boolean matches(OverlayProfile profile) {
    if (profile == null || minFkdr.get() == 0 && minStars.get() == 0) return true;
    double fkd = profile.fkdr(mode.get());
    Long stars = profile.counts.get("stars");
    if (minFkdr.get() > 0 && fkd < 0 || minStars.get() > 0 && stars == null) return true;
    return minFkdr.get() > 0 && fkd >= minFkdr.get()
        || minStars.get() > 0 && stars != null && stars >= minStars.get();
  }

  static boolean hypixel() {
    Minecraft mc = Minecraft.getMinecraft();
    return !mc.isSingleplayer()
        && mc.getCurrentServerData() != null
        && PlayerReport.hypixel(mc.getCurrentServerData().serverIP);
  }

  List<String> sidebar() {
    Minecraft mc = Minecraft.getMinecraft();
    List<String> lines = new ArrayList<>();
    if (mc.theWorld == null) return lines;
    net.minecraft.scoreboard.Scoreboard board = mc.theWorld.getScoreboard();
    net.minecraft.scoreboard.ScoreObjective objective = board.getObjectiveInDisplaySlot(1);
    if (objective == null) return lines;
    for (net.minecraft.scoreboard.Score score : board.getSortedScores(objective)) {
      lines.add(
          OverlayProfile.clean(
              net.minecraft.scoreboard.ScorePlayerTeam.formatPlayerName(
                  board.getPlayersTeam(score.getPlayerName()), score.getPlayerName())));
      if (lines.size() == 32) break;
    }
    return lines;
  }

  boolean active() {
    Minecraft mc = Minecraft.getMinecraft();
    if (!hypixel() || mc.theWorld == null) return false;
    if (!bedwarsOnly.get()) return true;
    net.minecraft.scoreboard.ScoreObjective objective =
        mc.theWorld.getScoreboard().getObjectiveInDisplaySlot(1);
    return objective != null
        && OverlayProfile.clean(objective.getDisplayName())
            .toUpperCase(Locale.ROOT)
            .startsWith("BED WARS")
        && (OverlayTracker.pregame(sidebar()) || OverlayTracker.game(sidebar()));
  }
}
