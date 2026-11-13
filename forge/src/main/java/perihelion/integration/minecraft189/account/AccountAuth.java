package perihelion.integration.minecraft189.account;

import static perihelion.integration.minecraft189.account.AccountHttp.*;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.*;

/** Explicit auth protocol adapter. It never writes Minecraft's session or logs tokens. */
public final class AccountAuth implements AutoCloseable {
  static final String MSA = "00000000402b5328", AZURE = "42a60a84-599d-44b2-a7c6-b00cdef1d6a2";
  static final String TOKEN = "https://login.live.com/oauth20_token.srf";
  static final String AUTHORIZE = "https://login.live.com/oauth20_authorize.srf";
  static final String DESKTOP = "https://login.live.com/oauth20_desktop.srf";
  static final String CALLBACK = "http://localhost:25575/callback";
  static final String SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";
  public static final String PROFILE = "https://api.minecraftservices.com/minecraft/profile";
  private final AccountHttp http;
  private volatile HttpServer callback;

  public AccountAuth(AccountHttp http) {
    this.http = http;
  }

  public Account access(String token) throws Exception {
    if (token == null || token.trim().isEmpty())
      throw new IOException("Paste a Minecraft access token.");
    JsonObject profile = http.json("GET", PROFILE, bearer(token.trim()), null, false);
    Account a = new Account();
    a.kind = "token";
    a.access = token.trim();
    a.name = required(profile, "name");
    a.uuid = required(profile, "id");
    a.validation = "Valid";
    if (!a.name.matches("[A-Za-z0-9_]{3,16}")
        || !a.uuid.replace("-", "").matches("[0-9a-fA-F]{32}"))
      throw new IOException("Service returned an invalid Minecraft profile.");
    return a;
  }

  public Account login(Account a, Consumer<Account> rotated) throws Exception {
    if ("offline".equals(a.kind)) return Account.offline(a.name);
    if (!a.access.isEmpty()) {
      try {
        Account result = access(a.access);
        result.refresh = a.refresh;
        result.kind = a.kind;
        return result;
      } catch (IOException error) {
        // Refresh only a refused token; transient failures must not consume a refresh token.
        if (a.refresh.isEmpty() || !error.getMessage().contains("credentials")) throw error;
      }
    }
    return refresh(a.refresh, a.kind, rotated);
  }

  public Account refresh(String token, String preferred, Consumer<Account> rotated)
      throws Exception {
    if (token.trim().isEmpty()) throw new IOException("This account needs to be added again.");
    boolean azure = "ms".equals(preferred);
    for (int attempt = 0; attempt < 2; attempt++, azure = !azure) {
      String client = azure ? AZURE : MSA;
      Reply reply =
          http.send(
              "POST",
              TOKEN,
              Collections.emptyMap(),
              form(
                      "client_id",
                      client,
                      "grant_type",
                      "refresh_token",
                      "refresh_token",
                      token.trim(),
                      "redirect_uri",
                      azure ? CALLBACK : DESKTOP,
                      "scope",
                      azure ? "XboxLive.signin XboxLive.offline_access" : SCOPE)
                  .getBytes(StandardCharsets.UTF_8),
              "application/x-www-form-urlencoded");
      if (reply.status < 200 || reply.status >= 300) {
        if (reply.status == 400 && attempt == 0) continue;
        throw failure(reply.status);
      }
      JsonObject pair = reply.json();
      String replacement = text(pair, "refresh_token");
      Account update = new Account();
      update.refresh = replacement.isEmpty() ? token.trim() : replacement;
      update.kind = azure ? "ms" : "msa";
      // Persist the rotated token before subsequent Xbox/profile requests can fail or be cancelled.
      rotated.accept(update);
      Account result = xbox(required(pair, "access_token"), azure);
      result.refresh = update.refresh;
      result.kind = update.kind;
      return result;
    }
    throw new IOException("Microsoft refused this refresh token.");
  }

  private Account xbox(String access, boolean azure) throws Exception {
    JsonObject properties = new JsonObject();
    properties.addProperty("AuthMethod", "RPS");
    properties.addProperty("SiteName", "user.auth.xboxlive.com");
    properties.addProperty("RpsTicket", (azure ? "d=" : "t=") + access);
    JsonObject body = envelope(properties, "http://auth.xboxlive.com");
    JsonObject xbl = post("https://user.auth.xboxlive.com/user/authenticate", body);
    properties = new JsonObject();
    properties.addProperty("SandboxId", "RETAIL");
    JsonArray tokens = new JsonArray();
    tokens.add(new JsonPrimitive(required(xbl, "Token")));
    properties.add("UserTokens", tokens);
    JsonObject xsts =
        post(
            "https://xsts.auth.xboxlive.com/xsts/authorize",
            envelope(properties, "rp://api.minecraftservices.com/"));
    String hash;
    try {
      hash =
          required(
              xsts.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject(),
              "uhs");
    } catch (RuntimeException e) {
      throw new IOException("Xbox did not return a profile. Check your Xbox account setup.");
    }
    return identity("XBL3.0 x=" + hash + ";" + required(xsts, "Token"));
  }

  private Account identity(String identity) throws Exception {
    JsonObject body = new JsonObject();
    body.addProperty("identityToken", identity);
    body.addProperty("ensureLegacyEnabled", true);
    return access(
        required(
            post("https://api.minecraftservices.com/authentication/login_with_xbox", body),
            "access_token"));
  }

  private JsonObject post(String url, JsonObject body) throws Exception {
    return http.json("POST", url, Collections.emptyMap(), body.toString(), false);
  }

  private static JsonObject envelope(JsonObject properties, String relyingParty) {
    JsonObject body = new JsonObject();
    body.add("Properties", properties);
    body.addProperty("RelyingParty", relyingParty);
    body.addProperty("TokenType", "JWT");
    return body;
  }

  public static Map<String, String> bearer(String token) {
    return Collections.singletonMap("Authorization", "Bearer " + token);
  }

  public Account device(Consumer<String> status, Consumer<String> open, Consumer<Account> rotated)
      throws Exception {
    JsonObject code =
        http.json(
            "POST",
            "https://login.live.com/oauth20_connect.srf",
            Collections.emptyMap(),
            form("client_id", MSA, "scope", SCOPE, "response_type", "device_code"),
            true);
    String user = required(code, "user_code"), secret = required(code, "device_code");
    String url = "https://www.microsoft.com/link?otc=" + encode(user);
    status.accept("Microsoft code: " + user + " | Complete sign-in in your browser.");
    open.accept(url);
    long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(number(code, "expires_in", 900));
    int interval = Math.max(1, number(code, "interval", 5));
    while (System.nanoTime() < end) {
      Thread.sleep(interval * 1000L);
      Reply reply =
          http.send(
              "POST",
              TOKEN,
              Collections.emptyMap(),
              form(
                      "client_id",
                      MSA,
                      "grant_type",
                      "urn:ietf:params:oauth:grant-type:device_code",
                      "device_code",
                      secret)
                  .getBytes(StandardCharsets.UTF_8),
              "application/x-www-form-urlencoded");
      JsonObject pair = reply.json();
      String error = text(pair, "error");
      if ("authorization_pending".equals(error)) continue;
      if ("slow_down".equals(error)) {
        interval = Math.min(60, interval + 5);
        continue;
      }
      if (reply.status < 200 || reply.status >= 300)
        throw new IOException("Microsoft sign-in was declined or expired. Start again.");
      return pair(pair, false, rotated);
    }
    throw new IOException("Microsoft sign-in code expired. Start again.");
  }

  public Account browser(Consumer<String> status, Consumer<String> open, Consumer<Account> rotated)
      throws Exception {
    byte[] entropy = new byte[32];
    new SecureRandom().nextBytes(entropy);
    String state = Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
    String verifier = UUID.randomUUID().toString() + UUID.randomUUID().toString();
    String challenge =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<String> result = new AtomicReference<>();
    HttpServer server =
        HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 25575), 0);
    callback = server;
    server.createContext(
        "/callback",
        exchange -> {
          Map<String, String> params = parameters(exchange.getRequestURI().getRawQuery());
          boolean valid =
              exchange.getRequestURI().getPath().equals("/callback")
                  && exchange.getRequestMethod().equals("GET")
                  && state.equals(params.get("state"));
          byte[] message =
              (valid
                      ? "Perihelion sign-in received. You may close this window."
                      : "Invalid sign-in callback.")
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
          exchange.getResponseHeaders().set("Cache-Control", "no-store");
          exchange.sendResponseHeaders(valid ? 200 : 400, message.length);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(message);
          }
          if (valid) {
            result.set(params.get("code"));
            latch.countDown();
          }
        });
    try {
      server.start();
      status.accept("Complete Microsoft sign-in in your browser.");
      open.accept(
          AUTHORIZE
              + "?"
              + form(
                  "client_id",
                  AZURE,
                  "response_type",
                  "code",
                  "redirect_uri",
                  CALLBACK,
                  "scope",
                  "XboxLive.signin XboxLive.offline_access",
                  "state",
                  state,
                  "prompt",
                  "select_account",
                  "code_challenge",
                  challenge,
                  "code_challenge_method",
                  "S256"));
      if (!latch.await(5, TimeUnit.MINUTES) || result.get() == null)
        throw new IOException("Browser sign-in expired or was declined.");
      JsonObject pair =
          http.json(
              "POST",
              TOKEN,
              Collections.emptyMap(),
              form(
                  "client_id",
                  AZURE,
                  "grant_type",
                  "authorization_code",
                  "code",
                  result.get(),
                  "redirect_uri",
                  CALLBACK,
                  "code_verifier",
                  verifier),
              true);
      return pair(pair, true, rotated);
    } finally {
      server.stop(0);
      if (callback == server) callback = null;
    }
  }

  private Account pair(JsonObject pair, boolean azure, Consumer<Account> rotated) throws Exception {
    Account tokens = new Account();
    tokens.refresh = text(pair, "refresh_token");
    tokens.kind = azure ? "ms" : "msa";
    rotated.accept(tokens);
    Account a = xbox(required(pair, "access_token"), azure);
    a.refresh = tokens.refresh;
    a.kind = tokens.kind;
    return a;
  }

  public Account cookies(String input, Consumer<Account> rotated) throws Exception {
    for (int attempt = 0; attempt < 4; attempt++) {
      boolean implicit = attempt % 2 == 1;
      AccountCookies jar = AccountCookies.parse(input);
      String url =
          AUTHORIZE
              + "?"
              + form(
                  "client_id",
                  MSA,
                  "response_type",
                  implicit ? "token" : "code",
                  "scope",
                  SCOPE,
                  "redirect_uri",
                  DESKTOP)
              + (attempt < 2 ? "&prompt=none" : "");
      String post = null;
      for (int hop = 0; hop < 20; hop++) {
        URI target = URI.create(url);
        if (url.startsWith(DESKTOP + "?") || url.startsWith(DESKTOP + "#")) {
          Map<String, String> params =
              parameters(
                  target.getRawFragment() == null ? target.getRawQuery() : target.getRawFragment());
          if (params.containsKey("code"))
            return pair(
                http.json(
                    "POST",
                    TOKEN,
                    Collections.emptyMap(),
                    form(
                        "client_id",
                        MSA,
                        "grant_type",
                        "authorization_code",
                        "code",
                        params.get("code"),
                        "redirect_uri",
                        DESKTOP,
                        "scope",
                        SCOPE),
                    true),
                false,
                rotated);
          if (params.containsKey("access_token")) {
            JsonObject pair = new JsonObject();
            pair.addProperty("access_token", params.get("access_token"));
            pair.addProperty("refresh_token", params.getOrDefault("refresh_token", ""));
            return pair(pair, false, rotated);
          }
          break;
        }
        if (!AccountCookies.allowed(target))
          throw new IOException("Microsoft redirected outside the sign-in hosts.");
        Reply reply =
            http.send(
                post == null ? "GET" : "POST",
                url,
                jar.headers(target),
                post == null ? null : post.getBytes(StandardCharsets.UTF_8),
                "application/x-www-form-urlencoded");
        jar.capture(target, reply.headers);
        post = null;
        if (reply.location != null) {
          url = target.resolve(reply.location.replace(" ", "%20")).toString();
          continue;
        }
        Matcher form =
            Pattern.compile("(?is)<form\\b[^>]*action=[\"']([^\"']+)[\"'][^>]*>(.*?)</form>")
                .matcher(reply.body);
        if (form.find() && reply.body.toLowerCase(Locale.ROOT).contains("submit(")) {
          url = target.resolve(html(form.group(1))).toString();
          Matcher fields = Pattern.compile("(?is)<input\\b[^>]*>").matcher(form.group(2));
          List<String> values = new ArrayList<>();
          while (fields.find()) {
            String tag = fields.group();
            String name = attribute(tag, "name");
            if (!name.isEmpty()) {
              values.add(name);
              values.add(attribute(tag, "value"));
            }
          }
          post = form(values.toArray(new String[0]));
          continue;
        }
        Matcher redirect =
            Pattern.compile(
                    "(?i)(?:location(?:\\.href)?\\s*=|location\\.replace\\()\\s*[\"']([^\"']+)")
                .matcher(reply.body);
        if (redirect.find()) {
          url = target.resolve(html(redirect.group(1))).toString();
          continue;
        }
        break;
      }
    }
    throw new IOException(
        "Cookies did not complete Microsoft sign-in. Export fresh cookies or use device sign-in.");
  }

  private static String attribute(String tag, String key) {
    Matcher m = Pattern.compile("(?i)\\b" + key + "=[\"']([^\"']*)[\"']").matcher(tag);
    return m.find() ? html(m.group(1)) : "";
  }

  private static String html(String s) {
    return s.replace("&amp;", "&")
        .replace("&#39;", "'")
        .replace("&quot;", "\"")
        .replace("&lt;", "<")
        .replace("&gt;", ">");
  }

  static int number(JsonObject j, String key, int fallback) {
    try {
      return Math.min(1800, Math.max(1, j.get(key).getAsInt()));
    } catch (RuntimeException e) {
      return fallback;
    }
  }

  public static Map<String, String> parameters(String query) {
    Map<String, String> result = new HashMap<>();
    if (query != null)
      for (String field : query.split("&")) {
        String[] kv = field.split("=", 2);
        try {
          if (kv.length == 2)
            result.put(URLDecoder.decode(kv[0], "UTF-8"), URLDecoder.decode(kv[1], "UTF-8"));
        } catch (Exception ignored) {
        }
      }
    return result;
  }

  public static List<String> refreshTokens(String input) {
    LinkedHashSet<String> tokens = new LinkedHashSet<>();
    if (input == null || input.length() > AccountVault.MAX_BYTES) return new ArrayList<>();
    for (String line : input.split("[\\r\\n]+")) {
      Matcher labeled = Pattern.compile("(?i)refreshtoken\\s*:\\s*([^|\\s]+)").matcher(line);
      if (labeled.find()) {
        tokens.add(strip(labeled.group(1)));
        continue;
      }
      for (String part : line.split("[\\s,;]+")) {
        String best = "";
        for (String segment : part.split(":")) {
          String s = strip(segment);
          if (s.length() >= 20
              && !s.contains("@")
              && (!best.startsWith("M.") && s.length() > best.length() || s.startsWith("M.")))
            best = s;
        }
        if (!best.isEmpty() && !best.startsWith("eyJ") && !best.equals("refreshtoken"))
          tokens.add(best);
      }
      if (tokens.size() >= AccountBook.LIMIT) break;
    }
    return new ArrayList<>(tokens);
  }

  private static String strip(String s) {
    return s.trim().replaceAll("^[\"']+|[\"']+$", "");
  }

  @Override
  public void close() {
    HttpServer s = callback;
    if (s != null) s.stop(0);
    callback = null;
  }
}
