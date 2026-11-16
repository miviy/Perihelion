package perihelion.integration.minecraft189.hud;

import com.google.gson.*;
import java.util.*;

/** Immutable provider snapshot. Missing, private and malformed fields never become zero. */
final class OverlayProfile {
  enum Mode {
    OVERALL(""),
    SOLO("eight_one_"),
    DOUBLES("eight_two_"),
    THREES("four_three_"),
    FOURS("four_four_"),
    FOUR_V_FOUR("two_four_");
    final String prefix;

    Mode(String prefix) {
      this.prefix = prefix;
    }
  }

  enum Period {
    DAILY,
    WEEKLY,
    MONTHLY
  }

  static final class Tag {
    final String source, type, reason;

    Tag(String source, String type, String reason) {
      this.source = source;
      this.type = clean(type);
      this.reason = clean(reason);
    }
  }

  final Map<String, Long> counts;
  final Map<String, String> periods;
  final List<Tag> tags;
  final String provider, rank, message;
  final boolean nicked, tagsAvailable;

  OverlayProfile(
      Map<String, Long> counts,
      Map<String, String> periods,
      List<Tag> tags,
      String provider,
      String rank,
      String message,
      boolean nicked,
      boolean tagsAvailable) {
    this.counts = Collections.unmodifiableMap(new LinkedHashMap<>(counts));
    this.periods = Collections.unmodifiableMap(new LinkedHashMap<>(periods));
    this.tags = Collections.unmodifiableList(new ArrayList<>(tags));
    this.provider = provider;
    this.rank = clean(rank);
    this.message = message;
    this.nicked = nicked;
    this.tagsAvailable = tagsAvailable;
  }

  static OverlayProfile empty(String message, boolean nicked) {
    return new OverlayProfile(
        Collections.emptyMap(),
        Collections.emptyMap(),
        Collections.emptyList(),
        "",
        "",
        message,
        nicked,
        false);
  }

  static OverlayProfile parse(UUID id, String body, String provider) {
    JsonObject root = json(body);
    if (!yes(root, "success")) throw new IllegalArgumentException("Unsuccessful profile");
    JsonObject player = object(root, "player");
    if (player == null || !sameId(id, string(player, "uuid")))
      throw new IllegalArgumentException("Profile identity unavailable or mismatched");
    JsonObject stats = object(player, "stats"), bw = object(stats, "Bedwars");
    Map<String, Long> counts = bedwars(bw);
    Long stars = number(object(player, "achievements"), "bedwars_level");
    Long experience = number(bw, "Experience");
    if (experience == null) experience = number(bw, "experience");
    if (stars != null) counts.put("stars", stars);
    else if (experience != null) counts.put("stars", level(experience));
    for (String key : Arrays.asList("firstLogin", "lastLogin", "lastLogout", "networkExp")) {
      Long value = number(player, key);
      if (value != null) counts.put(key, value);
    }
    String rank = string(player, "prefix");
    if (rank.isEmpty())
      for (String key :
          Arrays.asList("rank", "monthlyPackageRank", "newPackageRank", "packageRank")) {
        String value = string(player, key);
        if (!value.isEmpty() && !Arrays.asList("NONE", "NORMAL").contains(value)) {
          rank = value.replace("SUPERSTAR", "MVP++").replace("_PLUS", "+");
          break;
        }
      }
    return new OverlayProfile(
        counts, Collections.emptyMap(), Collections.emptyList(), provider, rank, "", false, false);
  }

  static Map<String, Long> bedwars(JsonObject bw) {
    Map<String, Long> values = new LinkedHashMap<>();
    for (Mode mode : Mode.values())
      for (String field :
          Arrays.asList(
              "kills",
              "deaths",
              "final_kills",
              "final_deaths",
              "wins",
              "losses",
              "beds_broken",
              "beds_lost",
              "winstreak")) {
        String key = mode.prefix + field;
        Long value = number(bw, key + (field.equals("winstreak") ? "" : "_bedwars"));
        if (value != null) values.put(key, value);
      }
    return values;
  }

  OverlayProfile merge(OverlayProfile other) {
    if (other == null) return this;
    Map<String, Long> values = new LinkedHashMap<>(counts);
    other.counts.forEach(values::putIfAbsent);
    Map<String, String> sessions = new LinkedHashMap<>(periods);
    sessions.putAll(other.periods);
    List<Tag> all = new ArrayList<>(tags);
    all.addAll(other.tags);
    String sources =
        provider.isEmpty()
            ? other.provider
            : other.provider.isEmpty() || provider.contains(other.provider)
                ? provider
                : provider + "+" + other.provider;
    return new OverlayProfile(
        values,
        sessions,
        all,
        sources,
        rank.isEmpty() ? other.rank : rank,
        message.isEmpty() ? other.message : message,
        nicked || other.nicked,
        tagsAvailable || other.tagsAvailable);
  }

  OverlayProfile winstreak(JsonObject root, UUID id) {
    if (!yes(root, "success")) return this;
    JsonObject data = object(root, "data");
    if (!sameId(id, string(data, "uuid"))) return this;
    Map<String, Long> values = new LinkedHashMap<>(counts);
    for (Mode mode : Mode.values()) {
      String key = mode.prefix + "winstreak";
      Long value = number(data, key);
      if (value != null && (!values.containsKey(key) || values.get(key) == 0)) {
        values.put(key, value);
        values.put(key + "-estimated", yes(data, "estimated") ? 1L : 0L);
      }
    }
    return new OverlayProfile(
        values, periods, tags, provider, rank, message, nicked, tagsAvailable);
  }

  OverlayProfile session(JsonObject session, Period period) {
    if (!yes(session, "success")) return this;
    JsonObject delta = object(session, "delta");
    Map<String, String> values = new LinkedHashMap<>(periods);
    Map<String, Long> counts = bedwars(delta);
    String prefix = period.name().toLowerCase(Locale.ROOT) + "-";
    for (String[] ratio :
        new String[][] {
          {"fkd", "final_kills", "final_deaths"},
          {"wl", "wins", "losses"},
          {"bbl", "beds_broken", "beds_lost"},
          {"kd", "kills", "deaths"}
        }) values.put(prefix + ratio[0], ratio(counts.get(ratio[1]), counts.get(ratio[2])));
    JsonObject current = object(object(session, "current"), "value"),
        historical = object(object(session, "historical"), "value");
    Long a = number(current, "Experience"), b = number(historical, "Experience");
    if (a == null) a = number(current, "experience");
    if (b == null) b = number(historical, "experience");
    if (a != null && b != null && a >= b)
      values.put(prefix + "stars", String.valueOf(level(a) - level(b)));
    Map<String, Long> lifetime = new LinkedHashMap<>(this.counts);
    bedwars(current).forEach(lifetime::putIfAbsent);
    if (a != null) lifetime.putIfAbsent("stars", level(a));
    return new OverlayProfile(
        lifetime, values, tags, provider, rank, message, nicked, tagsAvailable);
  }

  String count(Mode mode, String key) {
    return count(mode.prefix + key);
  }

  String count(String key) {
    Long value = counts.get(key);
    return value == null ? "--" : value.toString();
  }

  String ratio(Mode mode, String a, String b) {
    return ratio(counts.get(mode.prefix + a), counts.get(mode.prefix + b));
  }

  double numeric(Mode mode, String key) {
    Long value = counts.get(mode.prefix + key);
    return value == null ? -1 : value;
  }

  double fkdr(Mode mode) {
    double a = numeric(mode, "final_kills"), b = numeric(mode, "final_deaths");
    return a < 0 || b < 0 ? -1 : a / Math.max(1, b);
  }

  String column(Mode mode, HudModule.PlayerColumn column, long now) {
    String key = column.name().toLowerCase(Locale.ROOT).replace('_', '-');
    if (periods.containsKey(key)) return periods.get(key);
    switch (column) {
      case RANK:
        return rank.isEmpty() ? "--" : rank;
      case STARS:
        return count("stars");
      case KD:
        return ratio(mode, "kills", "deaths");
      case FKD:
        return ratio(mode, "final_kills", "final_deaths");
      case WL:
        return ratio(mode, "wins", "losses");
      case BBL:
        return ratio(mode, "beds_broken", "beds_lost");
      case WS:
        return (counts.getOrDefault(mode.prefix + "winstreak-estimated", 0L) > 0 ? "~" : "")
            + count(mode, "winstreak");
      case KILLS:
        return count(mode, "kills");
      case FINALS:
        return count(mode, "final_kills");
      case BEDS:
        return count(mode, "beds_broken");
      case WINS:
        return count(mode, "wins");
      case TAGS:
        return !tags.isEmpty()
            ? tags.get(0).type + (tags.size() > 1 ? "+" : "")
            : tagsAvailable ? "Clear" : "--";
      case NICK:
        return nicked ? "Nick?" : provider.isEmpty() ? "--" : "No";
      case LEVEL:
        Long xp = counts.get("networkExp");
        return xp == null
            ? "--"
            : String.valueOf((long) (Math.sqrt(2 * (double) xp + 30625) / 50 - 2.5));
      case SESSION:
        Long login = counts.get("lastLogin"), logout = counts.get("lastLogout");
        if (login == null || logout == null) return "--";
        if (login == 0) return "Private";
        if (logout > login + 10000) return "Offline";
        return HudLayout.duration(Math.max(0, (now - login) / 1000));
      default:
        return "--";
    }
  }

  static String ratio(Long a, Long b) {
    return a == null || b == null
        ? "--"
        : String.format(Locale.ROOT, "%.2f", (double) a / Math.max(1, b));
  }

  static long level(long xp) {
    long level = xp / 487000 * 100, remainder = xp % 487000;
    for (int threshold : new int[] {500, 1000, 2000, 3500}) {
      if (remainder < threshold) return level;
      remainder -= threshold;
      level++;
    }
    return level + remainder / 5000;
  }

  static JsonObject json(String text) {
    return new JsonParser().parse(text).getAsJsonObject();
  }

  static JsonObject object(JsonObject root, String key) {
    return root != null && root.has(key) && root.get(key).isJsonObject()
        ? root.getAsJsonObject(key)
        : null;
  }

  static Long number(JsonObject root, String key) {
    if (root == null
        || !root.has(key)
        || !root.get(key).isJsonPrimitive()
        || !root.get(key).getAsJsonPrimitive().isNumber()) return null;
    try {
      long value = root.get(key).getAsBigDecimal().longValueExact();
      return value >= 0 ? value : null;
    } catch (ArithmeticException invalid) {
      return null;
    }
  }

  static String string(JsonObject root, String key) {
    return root != null
            && root.has(key)
            && root.get(key).isJsonPrimitive()
            && root.get(key).getAsJsonPrimitive().isString()
        ? clean(root.get(key).getAsString())
        : "";
  }

  static boolean yes(JsonObject root, String key) {
    return root != null
        && root.has(key)
        && root.get(key).isJsonPrimitive()
        && root.get(key).getAsJsonPrimitive().isBoolean()
        && root.get(key).getAsBoolean();
  }

  static boolean sameId(UUID id, String returned) {
    return id.toString().replace("-", "").equalsIgnoreCase(returned.replace("-", ""));
  }

  // clean() runs for every sidebar line and provider field; Pattern is safe across the workers.
  private static final java.util.regex.Pattern FORMATTING =
          java.util.regex.Pattern.compile("\\u00a7."),
      CONTROL = java.util.regex.Pattern.compile("[\\p{Cntrl}]");

  static String clean(String value) {
    if (value == null) return "";
    String clean =
        CONTROL.matcher(FORMATTING.matcher(value).replaceAll("")).replaceAll(" ").trim();
    return clean.substring(0, Math.min(160, clean.length()));
  }
}
