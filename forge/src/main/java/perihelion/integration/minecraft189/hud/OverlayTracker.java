package perihelion.integration.minecraft189.hud;

import java.util.*;
import java.util.regex.*;

/**
 * Bounded, client-thread chat evidence. Join timing marks possible groups, never confirmed parties.
 */
final class OverlayTracker {
  private static final Pattern JOIN =
      Pattern.compile("^(\\w{1,16}) has joined \\((\\d+)/(\\d+)\\)!$");
  private static final Pattern ALT_JOIN = Pattern.compile("^\\+ \\((\\d+)/(\\d+)\\) (\\w{1,16})$");
  private static final Pattern KILL =
      Pattern.compile(
          "^([A-Za-z0-9_]{1,16}) .*(?:by|to|for) ([A-Za-z0-9_]{1,16})(?:'s Golem)?[.!]?$",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern BED =
      Pattern.compile("^BED DESTRUCTION > .+ Bed .+ by ([A-Za-z0-9_]{1,16})!*$");
  final Set<String> party = new LinkedHashSet<>(),
      finalKilled = new LinkedHashSet<>(),
      who = new LinkedHashSet<>(),
      possibleParty = new LinkedHashSet<>();
  final Map<String, int[]> combat = new LinkedHashMap<>();
  private final List<String> joins = new ArrayList<>();
  private long firstJoin;
  boolean playing, pregame, whoSent, partySent, warned, resultSent;
  long entered, playingSince;
  private boolean readingParty;

  void message(String raw, long now) {
    if (raw == null || raw.length() > 4096) return;
    String text = raw.replaceAll("\\u00a7.", "").trim();
    if (text.startsWith("ONLINE:")) {
      who.clear();
      who.addAll(OverlaySettings.names(text.substring(7)));
    }
    if (text.startsWith("Party Leader:")) {
      party.clear();
      readingParty = true;
    }
    if (text.equals("You are not currently in a party.")) {
      party.clear();
      readingParty = false;
    }
    if (text.startsWith("-----")) readingParty = false;
    if (readingParty
        && (text.startsWith("Party Leader:")
            || text.startsWith("Party Members:")
            || text.startsWith("Party Moderators:"))) {
      String members =
          text.substring(text.indexOf(':') + 1).replaceAll("\\[[^]]*]", "").replaceAll("[●•]", " ");
      for (String member : members.split("\\s+"))
        if (party.size() < 64 && member.matches("[A-Za-z0-9_]{1,16}"))
          party.add(member.toLowerCase(Locale.ROOT));
    }
    Matcher join = JOIN.matcher(text), alt = ALT_JOIN.matcher(text);
    String name = join.matches() ? join.group(1) : alt.matches() ? alt.group(3) : null;
    if (name != null && pregame) {
      if (now - firstJoin > 1000) joins.clear();
      if (joins.isEmpty()) firstJoin = now;
      if (joins.size() < 4 && !joins.contains(name.toLowerCase(Locale.ROOT)))
        joins.add(name.toLowerCase(Locale.ROOT));
      if (joins.size() >= 2 && possibleParty.size() < 64) possibleParty.addAll(joins);
    }
    if (text.contains("Protect your bed and destroy the enemy beds.")
        || text.contains("Goodluck with your BedWars Game")) {
      playing = true;
      playingSince = now;
      pregame = false;
      combat.clear();
      finalKilled.clear();
      resultSent = false;
    }
    if (text.equals("The game starts in 1 second!")) {
      pregame = false;
      joins.clear();
    }
    if (playing && text.endsWith("FINAL KILL!")) {
      String victim = text.substring(0, text.indexOf(' '));
      if (victim.matches("[A-Za-z0-9_]{1,16}") && finalKilled.size() < 256)
        finalKilled.add(victim.toLowerCase(Locale.ROOT));
    }
    if (!playing || text.contains(":")) return;
    boolean isFinal = text.endsWith("FINAL KILL!");
    Matcher kill = KILL.matcher(text.replace(" FINAL KILL!", "").trim()), bed = BED.matcher(text);
    if (bed.matches()) count(bed.group(1))[4]++;
    else if (kill.matches()) {
      count(kill.group(1))[isFinal ? 3 : 1]++;
      count(kill.group(2))[isFinal ? 2 : 0]++;
    }
  }

  private int[] count(String name) {
    String key = name.toLowerCase(Locale.ROOT);
    if (combat.size() >= 256 && !combat.containsKey(key)) return new int[5];
    return combat.computeIfAbsent(key, ignored -> new int[5]);
  }

  static boolean pregame(Collection<String> sidebar) {
    for (String line : sidebar)
      if (line.equals("Waiting...") || line.startsWith("Starting in")) return true;
    return false;
  }

  static boolean game(Collection<String> sidebar) {
    for (String line : sidebar)
      if (line.matches("[RGBYWPAS] (?:Red|Blue|Green|Yellow|White|Pink|Aqua|Gray):.*")) return true;
    return false;
  }
}
