package perihelion.integration.minecraft189.hud;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** One activation, one bounded worker, explicit lookups only, client-thread cache publication. */
final class HypixelLookup implements AutoCloseable {
  static final class Result {
    final HypixelStats stats;
    final String message;
    final long retryMillis;

    Result(HypixelStats stats, String message, long retryMillis) {
      this.stats = stats;
      this.message = message;
      this.retryMillis = retryMillis;
    }
  }

  static final class Entry {
    final Result result;
    final long expires;

    Entry(Result result, long expires) {
      this.result = result;
      this.expires = expires;
    }
  }

  interface Transport extends AutoCloseable {
    Result fetch(UUID id, Path keyFile);

    default void close() {}
  }

  private final Consumer<Runnable> dispatch;
  private final ExecutorService worker;
  private final Transport transport;
  private final LongSupplier clock;
  private final LinkedHashMap<UUID, Entry> cache =
      new LinkedHashMap<UUID, Entry>(64, .75f, true) {
        protected boolean removeEldestEntry(Map.Entry<UUID, Entry> e) {
          return size() > 64;
        }
      };
  private volatile boolean closed;
  private boolean busy;
  private long revision, blockedUntil;

  HypixelLookup(Consumer<Runnable> dispatch) {
    this(
        dispatch,
        Executors.newSingleThreadExecutor(
            r -> {
              Thread thread = new Thread(r, "Perihelion-Hypixel");
              thread.setDaemon(true);
              return thread;
            }),
        new HttpTransport(),
        System::currentTimeMillis);
  }

  HypixelLookup(
      Consumer<Runnable> dispatch,
      ExecutorService worker,
      Transport transport,
      LongSupplier clock) {
    this.dispatch = dispatch;
    this.worker = worker;
    this.transport = transport;
    this.clock = clock;
  }

  Result get(UUID id) {
    Entry entry = cache.get(id);
    if (entry != null && entry.expires > clock.getAsLong()) return entry.result;
    cache.remove(id);
    return null;
  }

  Result request(UUID id, Path keyFile) {
    Result existing = get(id);
    if (existing != null) return existing;
    if (closed) return new Result(null, "Stats lookup is closed", 0);
    if (clock.getAsLong() < blockedUntil)
      return new Result(null, "API limit reached. Try again shortly", 0);
    if (busy) return new Result(null, "Another lookup is loading. Try again shortly", 0);
    Result loading = new Result(null, "Loading Hypixel stats...", 0);
    cache.put(id, new Entry(loading, Long.MAX_VALUE));
    busy = true;
    long ticket = revision;
    worker.execute(
        () -> {
          Result result;
          try {
            result = transport.fetch(id, keyFile);
          } catch (RuntimeException failure) {
            result = new Result(null, "Stats response could not be read", 0);
          }
          final Result answer = result;
          dispatch.accept(
              () -> {
                if (closed) return;
                busy = false;
                if (ticket != revision) return;
                long now = clock.getAsLong();
                blockedUntil = now + Math.max(1000, answer.retryMillis);
                cache.put(id, new Entry(answer, now + (answer.stats == null ? 10_000 : 300_000)));
              });
        });
    return loading;
  }

  void invalidate() {
    revision++;
    cache.clear();
    blockedUntil = 0;
  }

  public void close() {
    closed = true;
    revision++;
    cache.clear();
    worker.shutdownNow();
    transport.close();
  }

  static String readKey(Path path) throws IOException {
    if (!Files.isRegularFile(path) || Files.size(path) > 4096)
      throw new IOException("Key file unavailable");
    byte[] bytes;
    try (InputStream in = Files.newInputStream(path)) {
      bytes = readBounded(in, 4096);
    }
    String key = new String(bytes, StandardCharsets.UTF_8).trim();
    if (key.isEmpty() || key.length() > 256 || !key.matches("[A-Za-z0-9_-]+"))
      throw new IOException("Invalid key file");
    return key;
  }

  static byte[] readBounded(InputStream in, int limit) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[4096];
    int n;
    while ((n = in.read(buffer)) != -1) {
      if (out.size() + n > limit) throw new IOException("Response too large");
      out.write(buffer, 0, n);
    }
    return out.toByteArray();
  }

  static final class HttpTransport implements Transport {
    interface ConnectionFactory {
      HttpURLConnection open(URL url) throws IOException;
    }

    private final ConnectionFactory connections;
    private volatile HttpURLConnection connection;
    private volatile boolean closed;

    HttpTransport() {
      this(url -> (HttpURLConnection) url.openConnection());
    }

    HttpTransport(ConnectionFactory connections) {
      this.connections = connections;
    }

    public Result fetch(UUID id, Path keyFile) {
      String key;
      try {
        key = readKey(keyFile);
      } catch (IOException | SecurityException error) {
        return new Result(null, "Set Hypixel key file in HUD settings", 0);
      }
      HttpURLConnection request = null;
      try {
        request =
            connections.open(
                new URL(
                    "https://api.hypixel.net/v2/player?uuid=" + id.toString().replace("-", "")));
        connection = request;
        if (closed) return new Result(null, "Stats lookup is closed", 0);
        request.setInstanceFollowRedirects(false);
        request.setConnectTimeout(5000);
        request.setReadTimeout(5000);
        request.setRequestProperty("API-Key", key);
        request.setRequestProperty("Accept", "application/json");
        request.setRequestProperty("User-Agent", "Perihelion/0.1");
        int status = request.getResponseCode();
        long retry =
            status == 429 || "0".equals(request.getHeaderField("RateLimit-Remaining"))
                ? retry(request.getHeaderField("RateLimit-Reset"))
                : 0;
        if (status == 403) return new Result(null, "Hypixel rejected the API key", 60_000);
        if (status == 429) return new Result(null, "Hypixel API rate limit reached", retry);
        if (status != 200)
          return new Result(null, "Hypixel API unavailable (HTTP " + status + ")", 10_000);
        String body;
        try (InputStream in = request.getInputStream()) {
          body = new String(readBounded(in, 2 * 1024 * 1024), StandardCharsets.UTF_8);
        }
        try {
          return new Result(HypixelStats.parse(id, body), "", retry);
        } catch (RuntimeException invalid) {
          return new Result(null, "Profile unavailable or response invalid", retry);
        }
      } catch (IOException | SecurityException error) {
        return new Result(null, "Could not reach Hypixel API", 5000);
      } finally {
        if (request != null) request.disconnect();
        connection = null;
      }
    }

    static long retry(String seconds) {
      try {
        return Math.min(300, Math.max(1, Long.parseLong(seconds))) * 1000;
      } catch (RuntimeException error) {
        return 60_000;
      }
    }

    public void close() {
      closed = true;
      HttpURLConnection current = connection;
      if (current != null) current.disconnect();
    }
  }
}
