package perihelion.integration.minecraft189.hud;

import static org.junit.Assert.*;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

public class OverlayTest {
  @Rule public TemporaryFolder folder = new TemporaryFolder();
  private final UUID id = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

  private String profile(String fields) {
    return "{\"success\":true,\"player\":{\"uuid\":\""
        + id
        + "\",\"networkExp\":0,\"stats\":{\"Bedwars\":{"
        + fields
        + "}}}}";
  }

  private OverlayLookup.Options options(OverlayLookup.Provider... providers) {
    return new OverlayLookup.Options(
        new LinkedHashSet<>(Arrays.asList(providers)), Collections.emptyMap(), false);
  }

  @Test
  public void modesMissingValuesAndZeroAreDistinct() {
    OverlayProfile p =
        OverlayProfile.parse(
            id,
            profile(
                "\"Experience\":487000,\"final_kills_bedwars\":24,\"final_deaths_bedwars\":4,\"eight_two_final_kills_bedwars\":9,\"eight_two_final_deaths_bedwars\":0,\"winstreak\":0,\"kills_bedwars\":null,\"wins_bedwars\":-1,\"losses_bedwars\":1.5"),
            "abyss");
    assertEquals("6.00", p.ratio(OverlayProfile.Mode.OVERALL, "final_kills", "final_deaths"));
    assertEquals("9.00", p.ratio(OverlayProfile.Mode.DOUBLES, "final_kills", "final_deaths"));
    assertEquals("--", p.ratio(OverlayProfile.Mode.SOLO, "final_kills", "final_deaths"));
    assertEquals("--", p.count("kills"));
    assertEquals("--", p.count("wins"));
    assertEquals("--", p.count("losses"));
    assertEquals("0", p.count("winstreak"));
    assertEquals("100", p.count("stars"));
    assertEquals("1", p.column(OverlayProfile.Mode.OVERALL, HudModule.PlayerColumn.LEVEL, 0));
  }

  @Test
  public void starThresholdsMatchPrestigeProgression() {
    long[] xp = {0, 499, 500, 1499, 1500, 3500, 7000, 11999, 12000, 486999, 487000, 487500};
    long[] levels = {0, 0, 1, 1, 2, 3, 4, 4, 5, 99, 100, 101};
    for (int i = 0; i < xp.length; i++) assertEquals(levels[i], OverlayProfile.level(xp[i]));
  }

  @Test
  public void rejectsWrongIdentitiesAndMalformedFields() {
    for (String body :
        Arrays.asList(
            "broken",
            "[]",
            "{\"success\":true,\"player\":null}",
            profile("").replace(id.toString(), UUID.randomUUID().toString()))) {
      try {
        OverlayProfile.parse(id, body, "prism");
        fail("Invalid profile accepted");
      } catch (RuntimeException expected) {
      }
    }
    OverlayProfile p =
        OverlayProfile.parse(id, profile("\"winstreak\":\"10\",\"Experience\":1e100"), "prism");
    assertEquals("--", p.count("winstreak"));
    assertEquals("--", p.count("stars"));
  }

  @Test
  public void fallbackFillsMissingFieldsWithoutOverwritingValidPrimary() {
    OverlayProfile a = OverlayProfile.parse(id, profile("\"wins_bedwars\":1"), "abyss");
    OverlayProfile b =
        OverlayProfile.parse(id, profile("\"wins_bedwars\":99,\"kills_bedwars\":5"), "bordic");
    OverlayProfile merged = a.merge(b);
    assertEquals("1", merged.count("wins"));
    assertEquals("5", merged.count("kills"));
    assertEquals("abyss+bordic", merged.provider);
    try {
      merged.counts.put("wins", 9L);
      fail();
    } catch (UnsupportedOperationException expected) {
    }
  }

  @Test
  public void streakFallbackRequiresIdentityAndSupportsAllModes() {
    OverlayProfile p =
        OverlayProfile.parse(id, profile("\"winstreak\":0,\"eight_one_winstreak\":2"), "abyss");
    JsonObject reply =
        OverlayProfile.json(
            "{\"success\":true,\"data\":{\"uuid\":\""
                + id
                + "\",\"winstreak\":8,\"eight_one_winstreak\":7,\"eight_two_winstreak\":4}}");
    p = p.winstreak(reply, id);
    assertEquals("8", p.count("winstreak"));
    assertEquals("2", p.count(OverlayProfile.Mode.SOLO, "winstreak"));
    assertEquals("4", p.count(OverlayProfile.Mode.DOUBLES, "winstreak"));
    assertSame(p, p.winstreak(reply, UUID.randomUUID()));
    reply.getAsJsonObject("data").addProperty("estimated", true);
    OverlayProfile estimated = OverlayProfile.empty("", false).winstreak(reply, id);
    assertEquals("~8", estimated.column(OverlayProfile.Mode.OVERALL, HudModule.PlayerColumn.WS, 0));
  }

  @Test
  public void sessionRatiosAndStarDeltasNeverInventMissingCounts() {
    OverlayProfile p =
        OverlayProfile.empty("", false)
            .session(
                OverlayProfile.json(
                    "{\"success\":true,\"delta\":{\"final_kills_bedwars\":8,\"final_deaths_bedwars\":2,\"wins_bedwars\":0,\"losses_bedwars\":0},\"historical\":{\"value\":{\"Experience\":7000}},\"current\":{\"value\":{\"Experience\":12000,\"kills_bedwars\":9}}}"),
                OverlayProfile.Period.DAILY);
    assertEquals(
        "4.00", p.column(OverlayProfile.Mode.OVERALL, HudModule.PlayerColumn.DAILY_FKD, 0));
    assertEquals("0.00", p.column(OverlayProfile.Mode.OVERALL, HudModule.PlayerColumn.DAILY_WL, 0));
    assertEquals("1", p.column(OverlayProfile.Mode.OVERALL, HudModule.PlayerColumn.DAILY_STARS, 0));
    assertEquals("--", p.column(OverlayProfile.Mode.OVERALL, HudModule.PlayerColumn.DAILY_KD, 0));
    assertEquals("--", p.column(OverlayProfile.Mode.OVERALL, HudModule.PlayerColumn.WEEKLY_FKD, 0));
    assertEquals("9", p.count("kills"));
  }

  @Test
  public void credentialsDeduplicationExpiryAndPublicationAreClientOwned() {
    ManualWorker worker = new ManualWorker();
    AtomicLong time = new AtomicLong(1000);
    OverlayLookup lookup = new OverlayLookup(worker, transport(), time::get);
    OverlayLookup.Options options = options(OverlayLookup.Provider.ABYSS);
    lookup.request(id, "Notch", options);
    lookup.request(id, "Notch", options);
    assertEquals(1, lookup.pendingCount());
    assertEquals(1, worker.queue.size());
    worker.run();
    assertNull(lookup.get(id));
    lookup.tick(options);
    assertNotNull(lookup.get(id));
    lookup.request(id, "Notch", options);
    assertTrue(worker.queue.isEmpty());
    time.addAndGet(600001);
    lookup.request(id, "Notch", options);
    assertEquals(1, worker.queue.size());
    lookup.invalidate();
    worker.run();
    lookup.tick(options);
    assertNull(lookup.get(id));
    lookup.request(id, "Notch", options);
    worker.run();
    lookup.close();
    lookup.tick(options);
    assertNull(lookup.get(id));
    assertTrue(worker.stopped);
  }

  @Test
  public void saturationTimeoutAndExceptionsCannotStrandPending() {
    ManualWorker worker = new ManualWorker();
    AtomicLong time = new AtomicLong(1000);
    OverlayLookup lookup =
        new OverlayLookup(
            worker,
            new OverlayLookup.Transport() {
              public OverlayProfile fetch(UUID who, String name, OverlayLookup.Options o) {
                throw new IllegalArgumentException("SECRET");
              }

              public Map<UUID, OverlayProfile> sessions(List<UUID> ids, OverlayLookup.Options o) {
                return Collections.emptyMap();
              }
            },
            time::get);
    OverlayLookup.Options options = options();
    for (int i = 0; i < 80; i++) lookup.request(UUID.randomUUID(), "Player", options);
    assertEquals(64, lookup.pendingCount());
    time.addAndGet(90001);
    lookup.tick(options);
    assertEquals(0, lookup.pendingCount());
    lookup.request(id, "Notch", options);
    while (!worker.queue.isEmpty()) worker.run();
    lookup.tick(options);
    assertFalse(lookup.get(id).message.contains("SECRET"));
    lookup.close();
  }

  @Test
  public void sessionBatchesAreBoundedAndLateRepliesAreDiscarded() throws Exception {
    ManualWorker worker = new ManualWorker();
    AtomicLong time = new AtomicLong(1000);
    List<Integer> batches = new ArrayList<>();
    OverlayLookup lookup =
        new OverlayLookup(
            worker,
            new OverlayLookup.Transport() {
              public OverlayProfile fetch(UUID who, String name, OverlayLookup.Options o) {
                return OverlayProfile.empty("", false);
              }

              public Map<UUID, OverlayProfile> sessions(List<UUID> ids, OverlayLookup.Options o) {
                batches.add(ids.size());
                return Collections.singletonMap(
                    ids.get(0), OverlayProfile.empty("sessions", false));
              }
            },
            time::get);
    Map<OverlayLookup.Provider, Path> keys = new EnumMap<>(OverlayLookup.Provider.class);
    keys.put(OverlayLookup.Provider.BORDIC, folder.newFile().toPath());
    OverlayLookup.Options options =
        new OverlayLookup.Options(EnumSet.of(OverlayLookup.Provider.BORDIC), keys, true);
    for (int i = 0; i < 23; i++) lookup.request(UUID.randomUUID(), "Player", options);
    while (!worker.queue.isEmpty()) worker.run();
    lookup.tick(options);
    worker.run();
    lookup.tick(options);
    worker.run();
    lookup.tick(options);
    worker.run();
    lookup.tick(options);
    assertEquals(Arrays.asList(10, 10, 3), batches);
    assertTrue(worker.queue.isEmpty());
    lookup.invalidate();
    lookup.close();
  }

  @Test
  public void repeatedPresenceDoesNotInflateEncounters() {
    OverlayEncounters tracker = new OverlayEncounters();
    tracker.update(Arrays.asList(id, id), 1000);
    tracker.update(Collections.singleton(id), 2000);
    assertEquals(1, tracker.get(id).encounters);
    tracker.update(Collections.emptyList(), 3000);
    tracker.update(Collections.singleton(id), 4000);
    assertEquals(2, tracker.get(id).encounters);
    assertEquals(4000, tracker.get(id).joined);
    tracker.update(Collections.emptyList(), 5000);
    tracker.update(Collections.singleton(id), 1805001);
    assertEquals(1, tracker.get(id).encounters);
    tracker.clear();
    assertNull(tracker.get(id));
  }

  @Test
  public void providerFailuresAreNotNicksAndWrongUuidFallsThrough() {
    List<String> hosts = new ArrayList<>();
    OverlayHttp http =
        new OverlayHttp(
            url -> {
              hosts.add(url.getHost());
              return new Connection(
                  url,
                  200,
                  url.getHost().contains("prism")
                      ? profile("").replace(id.toString(), UUID.randomUUID().toString())
                      : profile("\"kills_bedwars\":12"));
            },
            () -> 1000);
    OverlayProfile p =
        http.fetch(
            id, "Notch", options(OverlayLookup.Provider.PRISM, OverlayLookup.Provider.BORDIC));
    assertEquals("12", p.count("kills"));
    assertEquals("bordic", p.provider);
    assertFalse(p.nicked);
    assertTrue(p.message.contains("identity"));
    http.close();
    p =
        new OverlayHttp(url -> new Connection(url, 503, ""), () -> 1000)
            .fetch(
                id, "Notch", options(OverlayLookup.Provider.ABYSS, OverlayLookup.Provider.PRISM));
    assertFalse(p.nicked);
    assertTrue(p.provider.isEmpty());
  }

  @Test
  public void headersLimitsRedirectsAndRateResetAreHonored() throws Exception {
    Path key = folder.newFile().toPath();
    Files.write(key, "fixture-secret-key".getBytes(StandardCharsets.UTF_8));
    Map<OverlayLookup.Provider, Path> keys = new EnumMap<>(OverlayLookup.Provider.class);
    keys.put(OverlayLookup.Provider.HYPIXEL, key);
    OverlayLookup.Options options =
        new OverlayLookup.Options(EnumSet.of(OverlayLookup.Provider.HYPIXEL), keys, false);
    AtomicLong clock = new AtomicLong(1000);
    List<Connection> requests = new ArrayList<>();
    OverlayHttp http =
        new OverlayHttp(
            url -> {
              assertEquals("https", url.getProtocol());
              assertFalse(url.toString().contains("fixture-secret"));
              Connection c = new Connection(url, 429, "");
              requests.add(c);
              return c;
            },
            clock::get);
    OverlayProfile result = http.fetch(id, "Notch", options);
    assertFalse(result.message.contains("fixture-secret"));
    assertEquals("fixture-secret-key", requests.get(0).getRequestProperty("API-Key"));
    assertFalse(requests.get(0).getInstanceFollowRedirects());
    assertEquals(3000, requests.get(0).getConnectTimeout());
    assertTrue(requests.get(0).disconnected);
    assertFalse(requests.get(0).read);
    http.fetch(UUID.randomUUID(), "Player", options);
    assertEquals(1, requests.size());
    clock.addAndGet(12001);
    http.fetch(id, "Notch", options);
    assertEquals(2, requests.size());
    http.reset();
    http.fetch(id, "Notch", options);
    assertEquals(3, requests.size());
    http.close();
    for (int status : new int[] {302, 401, 403, 502}) {
      Connection c = new Connection(new URL("https://example.test"), status, profile(""));
      OverlayProfile p = new OverlayHttp(url -> c, () -> 1000).fetch(id, "Notch", options);
      assertFalse(c.read);
      assertTrue(c.disconnected);
      assertTrue(p.provider.isEmpty());
    }
  }

  @Test
  public void tagsAndBulkSessionIdentityAreAttributedToProviders() throws Exception {
    Path key = folder.newFile().toPath();
    Files.write(key, "fixture-key".getBytes(StandardCharsets.UTF_8));
    Map<OverlayLookup.Provider, Path> keys = new EnumMap<>(OverlayLookup.Provider.class);
    for (OverlayLookup.Provider p : OverlayLookup.Provider.values()) keys.put(p, key);
    OverlayLookup.Options options =
        new OverlayLookup.Options(
            EnumSet.of(
                OverlayLookup.Provider.URCHIN,
                OverlayLookup.Provider.SERAPH,
                OverlayLookup.Provider.BORDIC),
            keys,
            true);
    OverlayHttp http =
        new OverlayHttp(
            url -> {
              String body = profile("");
              if (url.getHost().equals("api.urchin.gg"))
                body = "{\"tags\":[{\"tag_type\":\"sniper\",\"reason\":\"reported\\nreason\"}]}";
              if (url.getHost().equals("api.seraph.si"))
                body =
                    "{\"success\":true,\"data\":{\"blacklist\":{\"tagged\":true,\"report_type\":\"cheating\",\"reason\":\"provider"
                        + " claim\"}}}";
              if (url.getHost().equals("api.bordic.xyz"))
                body =
                    "{\"success\":true,\"sessions\":[{\"uuid\":\""
                        + id
                        + "\",\"success\":true,\"delta\":{\"wins_bedwars\":8,\"losses_bedwars\":2}},{\"uuid\":\""
                        + UUID.randomUUID()
                        + "\",\"success\":true}]}";
              return new Connection(url, 200, body);
            },
            () -> 1000);
    OverlayProfile p = http.fetch(id, "Notch", options);
    assertEquals(2, p.tags.size());
    assertTrue(p.tagsAvailable);
    assertEquals("Urchin", p.tags.get(0).source);
    assertEquals("reported reason", p.tags.get(0).reason);
    Map<UUID, OverlayProfile> sessions = http.sessions(Collections.singletonList(id), options);
    assertEquals(1, sessions.size());
    assertEquals("4.00", sessions.get(id).periods.get("monthly-wl"));
    http.close();
  }

  @Test
  public void absentCredentialsMalformedBodiesAndOversizedResponsesStayUnavailable() {
    int[] calls = {0};
    OverlayHttp http =
        new OverlayHttp(
            url -> {
              calls[0]++;
              return new Connection(url, 200, profile(""));
            },
            () -> 1000);
    assertTrue(
        http.fetch(
                id,
                "Notch",
                options(
                    OverlayLookup.Provider.HYPIXEL,
                    OverlayLookup.Provider.URCHIN,
                    OverlayLookup.Provider.SERAPH))
            .provider
            .isEmpty());
    assertEquals(0, calls[0]);
    http.close();
    for (String body :
        Arrays.asList("broken", String.join("", Collections.nCopies(2 * 1024 * 1024 + 1, "x")))) {
      OverlayProfile p =
          new OverlayHttp(url -> new Connection(url, 200, body), () -> 1000)
              .fetch(id, "Notch", options(OverlayLookup.Provider.PRISM));
      assertTrue(p.provider.isEmpty());
      assertFalse(p.nicked);
    }
  }

  private OverlayLookup.Transport transport() {
    return new OverlayLookup.Transport() {
      public OverlayProfile fetch(UUID who, String name, OverlayLookup.Options o) {
        return OverlayProfile.parse(who, profile(""), "abyss");
      }

      public Map<UUID, OverlayProfile> sessions(List<UUID> ids, OverlayLookup.Options o) {
        return Collections.emptyMap();
      }
    };
  }

  @Test
  public void partialTagOutageCannotClaimPlayerIsClear() throws Exception {
    Path file = folder.newFile().toPath();
    Files.write(file, "fixture-key".getBytes(StandardCharsets.UTF_8));
    Map<OverlayLookup.Provider, Path> keys = new EnumMap<>(OverlayLookup.Provider.class);
    keys.put(OverlayLookup.Provider.URCHIN, file);
    keys.put(OverlayLookup.Provider.SERAPH, file);
    OverlayLookup.Options o =
        new OverlayLookup.Options(
            EnumSet.of(OverlayLookup.Provider.URCHIN, OverlayLookup.Provider.SERAPH), keys, false);
    try (OverlayHttp http =
        new OverlayHttp(
            url ->
                new Connection(
                    url, url.getHost().equals("api.urchin.gg") ? 200 : 502, "{\"tags\":[]}"),
            () -> 1000)) {
      OverlayProfile p = http.fetch(id, "Notch", o);
      assertFalse(p.tagsAvailable);
      assertEquals("--", p.column(OverlayProfile.Mode.OVERALL, HudModule.PlayerColumn.TAGS, 0));
      assertTrue(p.message.contains("502"));
    }
  }

  @Test
  public void providerTokensAllowPunctuationButRejectHeaderInjection() throws Exception {
    Path file = folder.newFile().toPath();
    String token = "fixture.jwt_token/base64+==";
    Files.write(file, token.getBytes(StandardCharsets.UTF_8));
    Map<OverlayLookup.Provider, Path> keys = new EnumMap<>(OverlayLookup.Provider.class);
    keys.put(OverlayLookup.Provider.URCHIN, file);
    OverlayLookup.Options o =
        new OverlayLookup.Options(EnumSet.of(OverlayLookup.Provider.URCHIN), keys, false);
    int[] requests = {0};
    OverlayHttp http =
        new OverlayHttp(
            url -> {
              requests[0]++;
              return new Connection(url, 200, "{\"tags\":[]}") {
                public int getResponseCode() {
                  assertEquals(token, getRequestProperty("X-API-Key"));
                  return super.getResponseCode();
                }
              };
            },
            () -> 1000);
    assertTrue(http.fetch(id, "Notch", o).tagsAvailable);
    Files.write(file, "fixture\r\nInjected: value".getBytes(StandardCharsets.UTF_8));
    assertFalse(http.fetch(id, "Notch", o).tagsAvailable);
    assertEquals(1, requests[0]);
    http.close();
  }

  @Test
  public void trackerUsesServerEvidenceAndBoundsCombatAndPartyData() {
    OverlayTracker tracker = new OverlayTracker();
    tracker.pregame = true;
    tracker.message("Alice has joined (1/16)!", 1000);
    tracker.message("Bob has joined (2/16)!", 1800);
    assertTrue(tracker.possibleParty.containsAll(Arrays.asList("alice", "bob")));
    tracker.message("Carol has joined (3/16)!", 4000);
    assertFalse(tracker.possibleParty.contains("carol"));
    tracker.message("Party Leader: [MVP+] Alice ●", 4001);
    tracker.message("Party Members: [VIP] Bob ● Carol ●", 4002);
    assertEquals(new LinkedHashSet<>(Arrays.asList("alice", "bob", "carol")), tracker.party);
    tracker.message("ONLINE: Alice, Bob, bad/name", 4003);
    assertEquals(new LinkedHashSet<>(Arrays.asList("alice", "bob")), tracker.who);
    tracker.message("Protect your bed and destroy the enemy beds.", 5000);
    tracker.message("Bob was killed by Alice. FINAL KILL!", 5001);
    tracker.message("BED DESTRUCTION > Blue Bed was destroyed by Alice!", 5002);
    assertTrue(tracker.finalKilled.contains("bob"));
    assertEquals(1, tracker.combat.get("alice")[2]);
    assertEquals(1, tracker.combat.get("bob")[3]);
    assertEquals(1, tracker.combat.get("alice")[4]);
    tracker.message("Alice: Bob was killed by Carol. FINAL KILL!", 5003);
    assertFalse(tracker.combat.containsKey("carol"));
    tracker.message("You are not currently in a party.", 5004);
    assertTrue(tracker.party.isEmpty());
    assertTrue(OverlayTracker.pregame(Arrays.asList("Starting in 10s")));
    assertTrue(OverlayTracker.game(Arrays.asList("R Red: ✓")));
    assertFalse(OverlayTracker.game(Arrays.asList("BED WARS", "Your Level: 4")));
  }

  private static class Connection extends HttpURLConnection {
    final int status;
    final byte[] body;
    boolean disconnected, read;
    final ByteArrayOutputStream output = new ByteArrayOutputStream();

    Connection(URL url, int status, String body) {
      super(url);
      this.status = status;
      this.body = body.getBytes(StandardCharsets.UTF_8);
    }

    public void connect() {}

    public boolean usingProxy() {
      return false;
    }

    public void disconnect() {
      disconnected = true;
    }

    public int getResponseCode() {
      return status;
    }

    public String getHeaderField(String key) {
      return key.equals("Retry-After") ? "12" : null;
    }

    public InputStream getInputStream() {
      read = true;
      return new ByteArrayInputStream(body);
    }

    public OutputStream getOutputStream() {
      return output;
    }
  }

  private static class ManualWorker extends AbstractExecutorService {
    final Deque<Runnable> queue = new ArrayDeque<>();
    boolean stopped;

    void run() {
      queue.remove().run();
    }

    public void execute(Runnable task) {
      if (stopped) throw new RejectedExecutionException();
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
