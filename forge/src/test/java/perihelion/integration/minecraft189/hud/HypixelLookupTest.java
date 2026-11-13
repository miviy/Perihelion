package perihelion.integration.minecraft189.hud;

import static org.junit.Assert.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

public class HypixelLookupTest {
  @Rule public TemporaryFolder folder = new TemporaryFolder();
  private final UUID id = UUID.fromString("00000000-0000-0000-0000-000000000123");

  private String body(String fields) {
    return "{\"success\":true,\"player\":{\"uuid\":\""
        + id.toString().replace("-", "")
        + "\",\"stats\":{\"Bedwars\":{"
        + fields
        + "}}}}";
  }

  @Test
  public void firstLoginIsOptionalAndRejectsInvalidTimestamps() {
    assertEquals(0, HypixelStats.parse(id, body("")).firstLogin);
    String prefix = "{\"success\":true,\"player\":{\"uuid\":\"" + id + "\",\"firstLogin\":";
    assertEquals(1600000000000L, HypixelStats.parse(id, prefix + "1600000000000}}").firstLogin);
    for (String invalid : Arrays.asList("-1", "1.2", "\"123\"", "1e100", "null"))
      assertEquals(0, HypixelStats.parse(id, prefix + invalid + "}}").firstLogin);
  }

  @Test
  public void parsesOverallStatsWithoutInventingMissingFields() {
    HypixelStats stats =
        HypixelStats.parse(
            id,
            body(
                "\"kills_bedwars\":20,\"deaths_bedwars\":4,\"final_kills_bedwars\":12,\"final_deaths_bedwars\":0,\"wins_bedwars\":9,\"losses_bedwars\":3"));
    assertEquals("5.00", stats.column(HypixelStats.Game.BEDWARS, HudModule.PlayerColumn.KD));
    assertEquals("12.00", stats.column(HypixelStats.Game.BEDWARS, HudModule.PlayerColumn.FKD));
    assertEquals("3.00", stats.column(HypixelStats.Game.BEDWARS, HudModule.PlayerColumn.WL));
    assertEquals("--", stats.column(HypixelStats.Game.BEDWARS, HudModule.PlayerColumn.WS));
    assertEquals("--", stats.column(HypixelStats.Game.SKYWARS, HudModule.PlayerColumn.KD));
  }

  @Test
  public void rejectsOtherUuidInvalidCountsAndMalformedResponses() {
    for (String invalid :
        Arrays.asList(
            "not JSON",
            "{\"success\":false}",
            "{\"success\":true,\"player\":null}",
            body("").replace("000123", "000456"))) {
      try {
        HypixelStats.parse(id, invalid);
        fail("Invalid response accepted");
      } catch (RuntimeException expected) {
      }
    }
    HypixelStats stats =
        HypixelStats.parse(
            id,
            body(
                "\"kills_bedwars\":-2,\"deaths_bedwars\":1.5,\"wins_bedwars\":\"10\",\"winstreak\":0"));
    assertEquals("--", stats.count(HypixelStats.Game.BEDWARS, "kills"));
    assertEquals("--", stats.count(HypixelStats.Game.BEDWARS, "deaths"));
    assertEquals("--", stats.count(HypixelStats.Game.BEDWARS, "wins"));
    assertEquals("0", stats.count(HypixelStats.Game.BEDWARS, "winstreak"));
  }

  @Test
  public void publishesOnClientQueueDeduplicatesAndExpires() {
    ManualExecutor worker = new ManualExecutor();
    Deque<Runnable> client = new ArrayDeque<>();
    AtomicLong time = new AtomicLong(1000);
    int[] calls = {0};
    HypixelLookup lookup =
        new HypixelLookup(
            client::add,
            worker,
            (who, key) -> {
              calls[0]++;
              return new HypixelLookup.Result(HypixelStats.parse(who, body("")), "", 0);
            },
            time::get);
    try {
      HypixelLookup.Result first = lookup.request(id, Paths.get("unused"));
      assertSame(first, lookup.request(id, Paths.get("unused")));
      assertEquals(1, worker.queue.size());
      worker.run();
      assertEquals(1, calls[0]);
      assertNull(lookup.get(id).stats);
      client.remove().run();
      assertNotNull(lookup.get(id).stats);
      assertSame(lookup.get(id), lookup.request(id, Paths.get("unused")));
      time.addAndGet(300001);
      assertNull(lookup.get(id));
      lookup.request(id, Paths.get("unused"));
      assertEquals(1, worker.queue.size());
    } finally {
      lookup.close();
    }
  }

  @Test
  public void credentialsChangeAndCloseDiscardLateReplies() {
    ManualExecutor worker = new ManualExecutor();
    Deque<Runnable> client = new ArrayDeque<>();
    boolean[] closed = {false};
    HypixelLookup.Transport transport =
        new HypixelLookup.Transport() {
          public HypixelLookup.Result fetch(UUID who, Path key) {
            return new HypixelLookup.Result(null, "old key failure", 0);
          }

          public void close() {
            closed[0] = true;
          }
        };
    HypixelLookup lookup = new HypixelLookup(client::add, worker, transport, () -> 1000);
    lookup.request(id, Paths.get("unused"));
    worker.run();
    lookup.invalidate();
    client.remove().run();
    assertNull(lookup.get(id));
    lookup.request(id, Paths.get("unused"));
    worker.run();
    lookup.close();
    client.remove().run();
    assertNull(lookup.get(id));
    assertTrue(closed[0]);
    assertTrue(worker.isShutdown());
  }

  @Test
  public void limitsToOnePendingLookupAndHonorsRateBackoff() {
    ManualExecutor worker = new ManualExecutor();
    Deque<Runnable> client = new ArrayDeque<>();
    AtomicLong time = new AtomicLong(1000);
    HypixelLookup lookup =
        new HypixelLookup(
            client::add,
            worker,
            (who, key) -> new HypixelLookup.Result(null, "rate limited", 60000),
            time::get);
    try {
      lookup.request(id, Paths.get("unused"));
      assertTrue(
          lookup
              .request(UUID.randomUUID(), Paths.get("unused"))
              .message
              .contains("Another lookup"));
      assertEquals(1, worker.queue.size());
      worker.run();
      client.remove().run();
      assertTrue(
          lookup.request(UUID.randomUUID(), Paths.get("unused")).message.contains("API limit"));
      time.addAndGet(60001);
      lookup.request(UUID.randomUUID(), Paths.get("unused"));
      assertEquals(1, worker.queue.size());
    } finally {
      lookup.close();
    }
  }

  @Test
  public void cacheIsBoundedAndTransportFailuresDoNotStrandLoading() {
    ManualExecutor worker = new ManualExecutor();
    Deque<Runnable> client = new ArrayDeque<>();
    AtomicLong time = new AtomicLong(1000);
    int[] calls = {0};
    HypixelLookup lookup =
        new HypixelLookup(
            client::add,
            worker,
            (who, key) -> {
              if (calls[0]++ == 0) throw new IllegalArgumentException("fixture");
              return new HypixelLookup.Result(
                  HypixelStats.parse(
                      who,
                      body("")
                          .replace(
                              id.toString().replace("-", ""), who.toString().replace("-", ""))),
                  "",
                  0);
            },
            time::get);
    try {
      List<UUID> ids = new ArrayList<>();
      for (int i = 0; i < 70; i++) {
        UUID who = UUID.randomUUID();
        ids.add(who);
        lookup.request(who, Paths.get("unused"));
        worker.run();
        client.remove().run();
        if (i == 0) assertEquals("Stats response could not be read", lookup.get(who).message);
        time.addAndGet(1001);
      }
      assertNull(lookup.get(ids.get(0)));
      assertNull(lookup.get(ids.get(5)));
      assertNotNull(lookup.get(ids.get(6)).stats);
      assertNotNull(lookup.get(ids.get(69)).stats);
    } finally {
      lookup.close();
    }
  }

  @Test
  public void keyFileAndResponseReadsAreBoundedAndSecretsAreNotErrors() throws Exception {
    Path file = folder.newFile().toPath();
    Files.write(file, "  fixture-secret-key\n".getBytes(StandardCharsets.UTF_8));
    assertEquals("fixture-secret-key", HypixelLookup.readKey(file));
    Files.write(file, new byte[4097]);
    try {
      HypixelLookup.readKey(file);
      fail();
    } catch (IOException expected) {
    }
    try {
      HypixelLookup.readBounded(new ByteArrayInputStream(new byte[12]), 10);
      fail();
    } catch (IOException expected) {
    }
    HypixelLookup.Result result = new HypixelLookup.HttpTransport().fetch(id, file);
    assertNull(result.stats);
    assertFalse(result.message.contains("fixture-secret"));
    assertEquals(60000, HypixelLookup.HttpTransport.retry(null));
    assertEquals(300000, HypixelLookup.HttpTransport.retry("9223372036854775807"));
  }

  @Test
  public void httpTransportUsesOfficialTlsHeaderAndBoundsErrors() throws Exception {
    Path file = folder.newFile().toPath();
    Files.write(file, "fixture-secret-key".getBytes(StandardCharsets.UTF_8));
    for (int status : new int[] {200, 403, 429, 302, 503}) {
      StubConnection connection =
          new StubConnection(status, body("").getBytes(StandardCharsets.UTF_8));
      HypixelLookup.HttpTransport transport =
          new HypixelLookup.HttpTransport(
              url -> {
                assertEquals("https", url.getProtocol());
                assertEquals("api.hypixel.net", url.getHost());
                assertEquals("/v2/player", url.getPath());
                assertEquals("uuid=" + id.toString().replace("-", ""), url.getQuery());
                return connection;
              });
      HypixelLookup.Result result = transport.fetch(id, file);
      assertEquals("fixture-secret-key", connection.getRequestProperty("API-Key"));
      assertEquals(5000, connection.getConnectTimeout());
      assertEquals(5000, connection.getReadTimeout());
      assertFalse(connection.getInstanceFollowRedirects());
      assertTrue(connection.disconnected);
      assertFalse(result.message.contains("fixture-secret-key"));
      if (status == 200) {
        assertNotNull(result.stats);
        assertEquals(12000, result.retryMillis);
      } else {
        assertNull(result.stats);
        assertFalse(connection.bodyRead);
      }
      if (status == 403) assertTrue(result.message.contains("rejected"));
      if (status == 429) assertEquals(12000, result.retryMillis);
    }
    for (byte[] response :
        new byte[][] {"broken".getBytes(StandardCharsets.UTF_8), new byte[2 * 1024 * 1024 + 1]}) {
      StubConnection connection = new StubConnection(200, response);
      assertNull(new HypixelLookup.HttpTransport(url -> connection).fetch(id, file).stats);
      assertTrue(connection.disconnected);
    }
  }

  private static final class StubConnection extends HttpURLConnection {
    final int status;
    final byte[] body;
    boolean disconnected, bodyRead;

    StubConnection(int status, byte[] body) throws Exception {
      super(new URL("https://api.hypixel.net/"));
      this.status = status;
      this.body = body;
    }

    public void connect() {}

    public void disconnect() {
      disconnected = true;
    }

    public boolean usingProxy() {
      return false;
    }

    public int getResponseCode() {
      return status;
    }

    public String getHeaderField(String name) {
      return name.equals("RateLimit-Reset")
          ? "12"
          : name.equals("RateLimit-Remaining") ? "0" : null;
    }

    public InputStream getInputStream() {
      bodyRead = true;
      return new ByteArrayInputStream(body);
    }
  }

  private static final class ManualExecutor extends AbstractExecutorService {
    final Deque<Runnable> queue = new ArrayDeque<>();
    boolean stopped;

    void run() {
      queue.remove().run();
    }

    public void execute(Runnable task) {
      queue.add(task);
    }

    public void shutdown() {
      stopped = true;
    }

    public List<Runnable> shutdownNow() {
      stopped = true;
      List<Runnable> pending = new ArrayList<>(queue);
      queue.clear();
      return pending;
    }

    public boolean isShutdown() {
      return stopped;
    }

    public boolean isTerminated() {
      return stopped;
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) {
      return stopped;
    }
  }
}
