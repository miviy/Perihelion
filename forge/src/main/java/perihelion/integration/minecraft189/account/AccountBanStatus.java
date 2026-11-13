package perihelion.integration.minecraft189.account;

import java.util.Locale;
import java.util.regex.*;

/** Records observed Hypixel results; an unobserved account never claims a clean ban check. */
public final class AccountBanStatus {
  private AccountBanStatus() {}

  public static boolean hypixel(String address) {
    String host = address.toLowerCase(Locale.ROOT).split(":", 2)[0];
    return host.equals("hypixel.net")
        || host.endsWith(".hypixel.net")
        || host.equals("hypixel.io")
        || host.endsWith(".hypixel.io");
  }

  public static long unban(String message, long now) {
    String text = message.replaceAll("\u00a7.", "").split("\n\n", 2)[0].toLowerCase(Locale.ROOT);
    if (text.contains("you are permanently banned")
        || text.contains("your account has been blocked.")) return -1;
    Matcher duration =
        Pattern.compile("(?:temporarily banned for|temporarily blocked for) (.*?) from this server")
            .matcher(text);
    if (!duration.find()) return Long.MIN_VALUE;
    Matcher units = Pattern.compile("(\\d+)\\s*([dhms])").matcher(duration.group(1));
    long total = 0;
    try {
      while (units.find()) {
        long multiplier =
            units.group(2).equals("d")
                ? 86400000
                : units.group(2).equals("h") ? 3600000 : units.group(2).equals("m") ? 60000 : 1000;
        total =
            Math.addExact(total, Math.multiplyExact(Long.parseLong(units.group(1)), multiplier));
      }
      return total > 0 ? Math.addExact(now, total) : Long.MIN_VALUE;
    } catch (ArithmeticException | NumberFormatException error) {
      return Long.MIN_VALUE;
    }
  }
}
