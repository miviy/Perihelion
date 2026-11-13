package perihelion.integration.minecraft189.account;

import com.google.gson.*;
import java.io.IOException;
import java.net.*;
import java.util.*;

/** Per-host cookie jar. No imported cookie is ever sent to an unrelated host. */
public final class AccountCookies {
  private static final String[] HOSTS = {
    "live.com",
    "microsoftonline.com",
    "microsoft.com",
    "xboxlive.com",
    "minecraft.net",
    "msauth.net",
    "msftauth.net"
  };

  private static final class Entry {
    String host, path, name, value;
    boolean domain, secure;
    long expires;
  }

  private final List<Entry> entries = new ArrayList<>();

  public static boolean allowed(URI uri) {
    if (!"https".equalsIgnoreCase(uri.getScheme())
        || uri.getUserInfo() != null
        || uri.getHost() == null
        || uri.getPort() != -1 && uri.getPort() != 443) return false;
    String host = uri.getHost().toLowerCase(Locale.ROOT);
    for (String base : HOSTS) if (host.equals(base) || host.endsWith("." + base)) return true;
    return false;
  }

  public static AccountCookies parse(String input) throws IOException {
    if (input == null || input.length() > AccountVault.MAX_BYTES)
      throw new IOException("Cookie export is too large.");
    AccountCookies jar = new AccountCookies();
    String trimmed = input.trim();
    try {
      if (trimmed.startsWith("[")) {
        for (JsonElement item : new JsonParser().parse(trimmed).getAsJsonArray()) {
          JsonObject j = item.getAsJsonObject();
          Entry e = new Entry();
          e.host = AccountHttp.text(j, "domain");
          e.domain = e.host.startsWith(".");
          e.path = AccountHttp.text(j, "path");
          e.name = AccountHttp.text(j, "name");
          e.value = AccountHttp.text(j, "value");
          e.secure = true;
          e.expires = j.has("expirationDate") ? j.get("expirationDate").getAsLong() : 0;
          jar.add(e);
        }
      } else if (trimmed.contains("\t")) {
        for (String line : trimmed.split("[\\r\\n]+")) {
          if (line.startsWith("#HttpOnly_")) line = line.substring(10);
          if (line.startsWith("#")) continue;
          String[] fields = line.split("\t", 7);
          if (fields.length != 7) continue;
          Entry e = new Entry();
          e.host = fields[0];
          e.domain = Boolean.parseBoolean(fields[1]);
          e.path = fields[2];
          e.secure = Boolean.parseBoolean(fields[3]);
          e.expires = Long.parseLong(fields[4]);
          e.name = fields[5];
          e.value = fields[6];
          jar.add(e);
        }
      } else {
        for (String field : trimmed.replaceFirst("(?i)^Cookie:\\s*", "").split(";")) {
          String[] parts = field.trim().split("=", 2);
          if (parts.length != 2) continue;
          Entry e = new Entry();
          e.host = "login.live.com";
          e.path = "/";
          e.name = parts[0];
          e.value = parts[1];
          e.secure = true;
          jar.add(e);
        }
      }
    } catch (RuntimeException e) {
      throw new IOException("Invalid cookie export.");
    }
    if (jar.entries.isEmpty()) throw new IOException("No usable Microsoft cookies in this export.");
    return jar;
  }

  private void add(Entry e) {
    e.host = e.host.replaceFirst("^\\.", "").toLowerCase(Locale.ROOT);
    if (e.path == null || e.path.isEmpty()) e.path = "/";
    if (!allowed(URI.create("https://" + e.host))
        || e.name.isEmpty()
        || e.name.matches(".*[\\r\\n;=].*")
        || e.value.matches(".*[\\r\\n].*")) return;
    entries.removeIf(
        old -> old.host.equals(e.host) && old.path.equals(e.path) && old.name.equals(e.name));
    entries.add(e);
  }

  public Map<String, String> headers(URI uri) throws IOException {
    if (!allowed(uri)) throw new IOException("Untrusted cookie destination.");
    String host = uri.getHost().toLowerCase(Locale.ROOT), path = uri.getPath();
    List<Entry> matching = new ArrayList<>();
    for (Entry e : entries)
      if ((host.equals(e.host) || e.domain && host.endsWith("." + e.host))
          && (e.expires <= 0 || e.expires > System.currentTimeMillis() / 1000)
          && (path.equals(e.path) || path.startsWith(e.path.endsWith("/") ? e.path : e.path + "/")))
        matching.add(e);
    matching.sort((a, b) -> Integer.compare(b.path.length(), a.path.length()));
    StringJoiner value = new StringJoiner("; ");
    for (Entry e : matching) value.add(e.name + "=" + e.value);
    return Collections.singletonMap("Cookie", value.toString());
  }

  public void capture(URI uri, Map<String, List<String>> headers) {
    for (Map.Entry<String, List<String>> h : headers.entrySet())
      if ("Set-Cookie".equalsIgnoreCase(h.getKey()))
        for (String value : h.getValue())
          try {
            for (HttpCookie c : HttpCookie.parse(value)) {
              Entry e = new Entry();
              e.host = c.getDomain() == null ? uri.getHost() : c.getDomain();
              String normalized = e.host.replaceFirst("^\\.", "");
              if (!uri.getHost().equals(normalized) && !uri.getHost().endsWith("." + normalized))
                continue;
              e.domain = c.getDomain() != null;
              e.path = c.getPath() == null ? "/" : c.getPath();
              e.name = c.getName();
              e.value = c.getValue();
              e.secure = c.getSecure();
              e.expires = c.getMaxAge() < 0 ? 0 : System.currentTimeMillis() / 1000 + c.getMaxAge();
              add(e);
            }
          } catch (IllegalArgumentException ignored) {
          }
  }
}
