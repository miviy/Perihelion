package perihelion.integration.minecraft189.hud;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Fixed provider destinations, bounded IO, no redirects or raw response/error logging. */
final class OverlayHttp implements OverlayLookup.Transport {
  interface Connections {
    HttpURLConnection open(URL url) throws IOException;
  }

  private final Connections connections;
  private final LongSupplier clock;
  private final Set<HttpURLConnection> active = new HashSet<>();
  private final Map<String, Long> blocked = new ConcurrentHashMap<>();
  private final String prismId = UUID.randomUUID().toString().replace("-", "");
  private boolean closed;
  private volatile long revision;

  OverlayHttp() {
    this(url -> (HttpURLConnection) url.openConnection(), System::currentTimeMillis);
  }

  OverlayHttp(Connections connections, LongSupplier clock) {
    this.connections = connections;
    this.clock = clock;
  }

  static final class Reply {
    final int status;
    final JsonObject body;

    Reply(int status, JsonObject body) {
      this.status = status;
      this.body = body;
    }
  }

  public OverlayProfile fetch(UUID id, String name, OverlayLookup.Options options) {
    String uuid = id.toString().replace("-", "");
    OverlayProfile profile = OverlayProfile.empty("", id.version() == 3);
    int missingProfiles = 0;
    List<String> issues = new ArrayList<>();
    for (OverlayLookup.Provider provider :
        Arrays.asList(
            OverlayLookup.Provider.ABYSS,
            OverlayLookup.Provider.PRISM,
            OverlayLookup.Provider.BORDIC,
            OverlayLookup.Provider.HYPIXEL)) {
      if (!options.providers.contains(provider) || Thread.currentThread().isInterrupted()) continue;
      String key = key(options.keys.get(provider));
      if (provider == OverlayLookup.Provider.HYPIXEL && key.isEmpty()) {
        issues.add("Hypixel key required");
        continue;
      }
      String url;
      Map<String, String> headers = new HashMap<>();
      switch (provider) {
        case ABYSS:
          // The donor's public endpoint only serves HTTP. No credentials are ever sent here.
          url = "http://api.abyssoverlay.com/player?uuid=" + uuid;
          headers.put("User-Agent", "node-ao/2.0.3");
          break;
        case PRISM:
          url = "https://flashlight.prismoverlay.com/v1/playerdata?uuid=" + uuid;
          headers.put("X-User-Id", prismId);
          headers.put("X-Prism-Version", "v1.11.0");
          break;
        case BORDIC:
          url =
              "https://bordic.xyz/api/v2/resources/cache/hypixel?uuid="
                  + uuid
                  + (key.isEmpty() ? "" : "&key=" + encode(key));
          break;
        default:
          url = "https://api.hypixel.net/v2/player?uuid=" + uuid;
          headers.put("API-Key", key);
          break;
      }
      Reply reply = request(url, headers, null);
      String source = provider.name().toLowerCase(Locale.ROOT);
      if (reply.status == 200 && OverlayProfile.yes(reply.body, "success")) {
        if (reply.body.has("player") && reply.body.get("player").isJsonNull()) missingProfiles++;
        else
          try {
            profile = profile.merge(OverlayProfile.parse(id, reply.body.toString(), source));
          } catch (RuntimeException invalid) {
            issues.add(source + " profile identity/response invalid");
          }
      } else issues.add(source + " " + status(reply));
      // The official endpoint is a fallback; avoid spending key quota when cached fields are
      // complete.
      if (provider == OverlayLookup.Provider.BORDIC && complete(profile)) break;
    }
    if (options.providers.contains(OverlayLookup.Provider.BORDIC)
        && !Thread.currentThread().isInterrupted()) {
      Reply streak =
          request(
              "https://bordic.xyz/api/v2/resources/winstreak?uuid=" + uuid,
              Collections.emptyMap(),
              null);
      if (streak.status == 200 && streak.body != null) profile = profile.winstreak(streak.body, id);
      else issues.add("Bordic streak " + status(streak));
    }
    List<OverlayProfile.Tag> tags = new ArrayList<>();
    int tagProviders = 0, tagsAvailable = 0;
    for (OverlayLookup.Provider provider :
        Arrays.asList(OverlayLookup.Provider.URCHIN, OverlayLookup.Provider.SERAPH))
      if (options.providers.contains(provider)) tagProviders++;
    for (OverlayLookup.Provider provider :
        Arrays.asList(OverlayLookup.Provider.URCHIN, OverlayLookup.Provider.SERAPH)) {
      if (!options.providers.contains(provider) || Thread.currentThread().isInterrupted()) continue;
      String key = key(options.keys.get(provider));
      if (key.isEmpty()) {
        issues.add(provider + " key required");
        continue;
      }
      Reply reply =
          provider == OverlayLookup.Provider.URCHIN
              ? request(
                  "https://api.urchin.gg/v3/player/tags?player=" + uuid,
                  Collections.singletonMap("X-API-Key", key),
                  null)
              : request(
                  "https://api.seraph.si/" + uuid + "/blacklist",
                  Collections.singletonMap("seraph-api-key", key),
                  null);
      if (reply.status != 200 || reply.body == null) {
        issues.add(provider + " " + status(reply));
        continue;
      }
      if (provider == OverlayLookup.Provider.URCHIN) {
        if (!reply.body.has("tags") || !reply.body.get("tags").isJsonArray()) {
          issues.add("Urchin response invalid");
          continue;
        }
        tagsAvailable++;
        for (JsonElement element : reply.body.getAsJsonArray("tags")) {
          if (!element.isJsonObject() || tags.size() >= 16) continue;
          JsonObject tag = element.getAsJsonObject();
          String type = OverlayProfile.string(tag, "tag_type");
          if (type.isEmpty()) type = OverlayProfile.string(tag, "type");
          String reason = OverlayProfile.string(tag, "reason");
          if (type.isEmpty()
              || type.equalsIgnoreCase("notice")
              || reason.toLowerCase(Locale.ROOT).contains("api is deprecated")) continue;
          tags.add(new OverlayProfile.Tag("Urchin", type, reason));
        }
      } else if (OverlayProfile.yes(reply.body, "success")) {
        tagsAvailable++;
        JsonObject tag =
            OverlayProfile.object(OverlayProfile.object(reply.body, "data"), "blacklist");
        if (OverlayProfile.yes(tag, "tagged")) {
          String type = OverlayProfile.string(tag, "report_type"),
              reason = OverlayProfile.string(tag, "reason");
          if (reason.isEmpty()) reason = OverlayProfile.string(tag, "tooltip");
          tags.add(new OverlayProfile.Tag("Seraph", type.isEmpty() ? "Blacklisted" : type, reason));
        }
      } else issues.add("Seraph response invalid");
    }
    return new OverlayProfile(
        profile.counts,
        profile.periods,
        tags,
        profile.provider,
        profile.rank,
        issues.isEmpty()
            ? (profile.provider.isEmpty() ? "Stats unavailable" : "")
            : String.join("; ", issues),
        profile.nicked || profile.provider.isEmpty() && missingProfiles >= 2,
        tagProviders > 0 && tagsAvailable == tagProviders);
  }

  private boolean complete(OverlayProfile p) {
    for (String key :
        Arrays.asList("kills", "deaths", "final_kills", "final_deaths", "wins", "losses", "stars"))
      if (!p.counts.containsKey(key)) return false;
    return !p.rank.isEmpty();
  }

  public Map<UUID, OverlayProfile> sessions(List<UUID> ids, OverlayLookup.Options options) {
    String key = key(options.keys.get(OverlayLookup.Provider.BORDIC));
    if (key.isEmpty() || ids.isEmpty()) return Collections.emptyMap();
    JsonObject payload = new JsonObject();
    JsonArray uuids = new JsonArray();
    for (UUID id : ids) uuids.add(new JsonPrimitive(id.toString().replace("-", "")));
    payload.add("uuids", uuids);
    Map<UUID, OverlayProfile> profiles = new LinkedHashMap<>();
    for (OverlayProfile.Period period : OverlayProfile.Period.values()) {
      if (Thread.currentThread().isInterrupted()) break;
      Reply reply =
          request(
              "https://api.bordic.xyz/v4/sessions/"
                  + period.name().toLowerCase(Locale.ROOT)
                  + "?key="
                  + encode(key),
              Collections.emptyMap(),
              payload.toString());
      if (reply.status != 200
          || !OverlayProfile.yes(reply.body, "success")
          || !reply.body.has("sessions")
          || !reply.body.get("sessions").isJsonArray()) continue;
      for (JsonElement element : reply.body.getAsJsonArray("sessions")) {
        if (!element.isJsonObject()) continue;
        JsonObject session = element.getAsJsonObject();
        for (UUID id : ids)
          if (OverlayProfile.sameId(id, OverlayProfile.string(session, "uuid")))
            profiles.put(
                id,
                profiles
                    .getOrDefault(id, OverlayProfile.empty("", false))
                    .session(session, period));
      }
    }
    return profiles;
  }

  private Reply request(String address, Map<String, String> headers, String body) {
    HttpURLConnection connection = null;
    long ticket = revision;
    try {
      URL url = new URL(address);
      String host = url.getHost();
      if (blocked.getOrDefault(host, 0L) > clock.getAsLong()
          || Thread.currentThread().isInterrupted()) return new Reply(429, null);
      synchronized (active) {
        if (closed) return new Reply(0, null);
        connection = connections.open(url);
        active.add(connection);
      }
      connection.setInstanceFollowRedirects(false);
      connection.setConnectTimeout(3000);
      connection.setReadTimeout(5000);
      connection.setRequestProperty("Accept", "application/json");
      connection.setRequestProperty("User-Agent", "Perihelion/0.1");
      for (Map.Entry<String, String> header : headers.entrySet())
        connection.setRequestProperty(header.getKey(), header.getValue());
      if (body != null) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream out = connection.getOutputStream()) {
          out.write(bytes);
        }
      }
      int status = connection.getResponseCode();
      if (ticket == revision
          && (status == 429 || "0".equals(connection.getHeaderField("RateLimit-Remaining"))))
        block(
            host,
            ticket,
            clock.getAsLong()
                + HypixelLookup.HttpTransport.retry(
                    first(
                        connection.getHeaderField("Retry-After"),
                        connection.getHeaderField("RateLimit-Reset"))));
      else if (ticket == revision && (status == 401 || status == 403 || status >= 500))
        block(host, ticket, clock.getAsLong() + (status >= 500 ? 15000 : 60000));
      if (status != 200) return new Reply(status, null);
      try (InputStream in = connection.getInputStream()) {
        return new Reply(
            status,
            OverlayProfile.json(
                new String(
                    HypixelLookup.readBounded(in, 2 * 1024 * 1024), StandardCharsets.UTF_8)));
      }
    } catch (IOException | RuntimeException unavailable) {
      return new Reply(0, null);
    } finally {
      if (connection != null) {
        connection.disconnect();
        synchronized (active) {
          active.remove(connection);
        }
      }
    }
  }

  private static String first(String a, String b) {
    return a == null ? b : a;
  }

  private static String key(Path file) {
    if (file == null) return "";
    try {
      if (!Files.isRegularFile(file) || Files.size(file) > 4096) return "";
      String key;
      try (InputStream in = Files.newInputStream(file)) {
        key = new String(HypixelLookup.readBounded(in, 4096), StandardCharsets.UTF_8).trim();
      }
      // Provider tokens may contain JWT/base64 punctuation. Reject whitespace/control injection.
      return key.matches("[\\x21-\\x7e]{1,256}") ? key : "";
    } catch (IOException | RuntimeException unavailable) {
      return "";
    }
  }

  private static String encode(String value) {
    try {
      return URLEncoder.encode(value, "UTF-8");
    } catch (UnsupportedEncodingException impossible) {
      throw new AssertionError(impossible);
    }
  }

  private static String status(Reply reply) {
    return reply.status == 0 ? "unreachable/invalid response" : "HTTP " + reply.status;
  }

  public void reset() {
    synchronized (active) {
      revision++;
      blocked.clear();
    }
  }

  private void block(String host, long ticket, long until) {
    synchronized (active) {
      if (!closed && ticket == revision) blocked.put(host, until);
    }
  }

  public void close() {
    synchronized (active) {
      closed = true;
      for (HttpURLConnection connection : active) connection.disconnect();
      active.clear();
    }
    blocked.clear();
  }
}
