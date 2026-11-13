package perihelion.integration.minecraft189.hud;

import com.google.gson.*;
import java.util.*;

/** Optional server fields stay unavailable when the API does not publish them. */
final class HypixelStats {
  enum Game {
    BEDWARS,
    SKYWARS
  }

  final Map<Game, Map<String, Long>> games;
  final long firstLogin;

  private HypixelStats(Map<Game, Map<String, Long>> games, long firstLogin) {
    this.games = games;
    this.firstLogin = firstLogin;
  }

  static HypixelStats parse(UUID requested, String body) {
    JsonObject root = new JsonParser().parse(body).getAsJsonObject();
    if (!root.has("success") || !root.get("success").getAsBoolean())
      throw new IllegalArgumentException("API request was unsuccessful");
    JsonObject player = object(root, "player");
    if (player == null) throw new IllegalArgumentException("No Hypixel profile for this player");
    if (!player.has("uuid")
        || !requested
            .toString()
            .replace("-", "")
            .equalsIgnoreCase(player.get("uuid").getAsString().replace("-", "")))
      throw new IllegalArgumentException("API returned a different player");
    JsonObject stats = object(player, "stats");
    Map<Game, Map<String, Long>> games = new EnumMap<>(Game.class);
    for (Game game : Game.values()) {
      JsonObject source =
          stats == null ? null : object(stats, game == Game.BEDWARS ? "Bedwars" : "SkyWars");
      Map<String, Long> values = new HashMap<>();
      String suffix = game == Game.BEDWARS ? "_bedwars" : "";
      for (String key :
          Arrays.asList(
              "kills", "deaths", "wins", "losses", "final_kills", "final_deaths", "winstreak")) {
        String field = key.equals("winstreak") ? key : key + suffix;
        if (source != null
            && source.has(field)
            && source.get(field).isJsonPrimitive()
            && source.get(field).getAsJsonPrimitive().isNumber()) {
          try {
            long value = source.get(field).getAsBigDecimal().longValueExact();
            if (value >= 0) values.put(key, value);
          } catch (ArithmeticException ignored) {
          }
        }
      }
      games.put(game, Collections.unmodifiableMap(values));
    }
    long firstLogin = 0;
    try {
      if (player.has("firstLogin")
          && player.get("firstLogin").isJsonPrimitive()
          && player.get("firstLogin").getAsJsonPrimitive().isNumber())
        firstLogin = Math.max(0, player.get("firstLogin").getAsBigDecimal().longValueExact());
    } catch (ArithmeticException ignored) {
    }
    return new HypixelStats(Collections.unmodifiableMap(games), firstLogin);
  }

  private static JsonObject object(JsonObject root, String key) {
    return root.has(key) && root.get(key).isJsonObject() ? root.getAsJsonObject(key) : null;
  }

  String count(Game game, String key) {
    Long value = games.get(game).get(key);
    return value == null ? "--" : value.toString();
  }

  String ratio(Game game, String numerator, String denominator) {
    Long a = games.get(game).get(numerator), b = games.get(game).get(denominator);
    return a == null || b == null
        ? "--"
        : String.format(Locale.ROOT, "%.2f", (double) a / Math.max(1, b));
  }

  String column(Game game, HudModule.PlayerColumn column) {
    switch (column) {
      case KD:
        return ratio(game, "kills", "deaths");
      case FKD:
        return ratio(game, "final_kills", "final_deaths");
      case WL:
        return ratio(game, "wins", "losses");
      case WS:
        return count(game, "winstreak");
      default:
        return "--";
    }
  }
}
