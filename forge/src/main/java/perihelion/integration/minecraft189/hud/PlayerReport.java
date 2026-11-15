package perihelion.integration.minecraft189.hud;

import java.util.Locale;
import java.util.regex.Pattern;

/** Opens Hypixel's report selection GUI. It does not select a reason or submit a report. */
final class PlayerReport {
  // Roster snapshots validate every tab-list name several times a second; compile once.
  private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

  /** A syntactically valid Minecraft username; null is invalid. */
  static boolean validUsername(String username) {
    return username != null && USERNAME.matcher(username).matches();
  }

  static boolean hypixel(String address) {
    if (address == null) return false;
    String host = address.toLowerCase(Locale.ROOT).trim();
    int port = host.indexOf(':');
    if (port >= 0) host = host.substring(0, port);
    return host.equals("hypixel.net") || host.endsWith(".hypixel.net");
  }

  static String command(boolean onHypixel, String username) {
    return onHypixel && validUsername(username) ? "/report " + username : null;
  }
}
