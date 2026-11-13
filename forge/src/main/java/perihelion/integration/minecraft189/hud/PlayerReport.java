package perihelion.integration.minecraft189.hud;

import java.util.Locale;

/** Opens Hypixel's report selection GUI. It does not select a reason or submit a report. */
final class PlayerReport {
  static boolean hypixel(String address) {
    if (address == null) return false;
    String host = address.toLowerCase(Locale.ROOT).trim();
    int port = host.indexOf(':');
    if (port >= 0) host = host.substring(0, port);
    return host.equals("hypixel.net") || host.endsWith(".hypixel.net");
  }

  static String command(boolean onHypixel, String username) {
    return onHypixel && username != null && username.matches("[A-Za-z0-9_]{1,16}")
        ? "/report " + username
        : null;
  }
}
