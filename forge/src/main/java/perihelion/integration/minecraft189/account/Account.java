package perihelion.integration.minecraft189.account;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** A private vault record, deliberately separate from shareable module configuration. */
public final class Account {
  public String id = UUID.randomUUID().toString();
  public String name = "", uuid = "", kind = "offline", access = "", refresh = "";
  public boolean pinned;
  public long unban = Long.MIN_VALUE;
  public String validation = "Unchecked";

  public static Account offline(String name) {
    if (name == null || !name.matches("[A-Za-z0-9_]{3,16}"))
      throw new IllegalArgumentException(
          "Use a username with 3-16 letters, numbers or underscores.");
    Account a = new Account();
    a.name = name;
    a.uuid =
        UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8))
            .toString();
    a.validation = "Offline";
    return a;
  }

  public Account copy() {
    Account a = new Account();
    a.id = id;
    a.name = name;
    a.uuid = uuid;
    a.kind = kind;
    a.access = access;
    a.refresh = refresh;
    a.pinned = pinned;
    a.unban = unban;
    a.validation = validation;
    return a;
  }

  public String type() {
    return "offline".equals(kind) ? "Offline" : !refresh.isEmpty() ? "Microsoft" : "Access token";
  }

  public String ban(long now) {
    if ("offline".equals(kind)) return "Offline servers only";
    if (unban == Long.MIN_VALUE) return "Hypixel: not checked";
    if (unban < 0) return "Hypixel: permanent ban";
    if (unban == 0) return "Hypixel: last join succeeded";
    if (unban <= now) return "Hypixel: ban elapsed; recheck by joining";
    long seconds = (unban - now + 999) / 1000;
    return "Hypixel ban: "
        + (seconds >= 86400 ? seconds / 86400 + "d " : "")
        + seconds / 3600 % 24
        + "h "
        + seconds / 60 % 60
        + "m";
  }
}
