package perihelion.integration.minecraft189.account;

import static org.junit.Assert.*;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public class AccountAuthTest {
  static final String PROFILE =
      "{\"name\":\"Example\",\"id\":\"00000000000000000000000000000123\"}";

  static class Fake extends AccountHttp {
    final Deque<Reply> replies = new ArrayDeque<>();
    final List<String> calls = new ArrayList<>();

    void reply(int code, String body) {
      replies.add(new Reply(code, body, null, Collections.emptyMap()));
    }

    @Override
    public Reply send(
        String method, String url, Map<String, String> headers, byte[] body, String type)
        throws Exception {
      calls.add(
          method
              + " "
              + url
              + " "
              + (body == null ? "" : new String(body, StandardCharsets.UTF_8)));
      if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
      if (replies.isEmpty()) throw new AssertionError("Unexpected request: " + method + " " + url);
      return replies.remove();
    }
  }

  private void chain(Fake http) {
    http.reply(200, "{\"Token\":\"xbl\"}");
    http.reply(200, "{\"Token\":\"xsts\",\"DisplayClaims\":{\"xui\":[{\"uhs\":\"hash\"}]}}");
    http.reply(200, "{\"access_token\":\"minecraft\"}");
    http.reply(200, PROFILE);
  }

  @Test
  public void refreshUsesMsaTicketAndPersistsRotationBeforeXbox() throws Exception {
    Fake http = new Fake();
    http.reply(200, "{\"access_token\":\"msa-access\",\"refresh_token\":\"rotated\"}");
    chain(http);
    AtomicReference<String> rotated = new AtomicReference<>();
    Account a =
        new AccountAuth(http)
            .refresh(
                "original",
                "msa",
                tokens -> {
                  assertEquals(1, http.calls.size());
                  rotated.set(tokens.refresh);
                });
    assertEquals("rotated", rotated.get());
    assertEquals("rotated", a.refresh);
    assertEquals("Example", a.name);
    assertTrue(http.calls.get(1).contains("t=msa-access"));
  }

  @Test
  public void mismatchedClientFallsBackToAzureWithDifferentTicket() throws Exception {
    Fake http = new Fake();
    http.reply(400, "{\"error\":\"invalid_grant\"}");
    http.reply(200, "{\"access_token\":\"azure-access\",\"refresh_token\":\"new\"}");
    chain(http);
    Account a = new AccountAuth(http).refresh("original", "msa", tokens -> {});
    assertEquals("ms", a.kind);
    assertTrue(http.calls.get(1).contains(AccountAuth.AZURE));
    assertTrue(http.calls.get(2).contains("d=azure-access"));
  }

  @Test
  public void transientRefreshFailuresDoNotConsumeAnotherClientToken() {
    Fake http = new Fake();
    http.reply(429, "{\"error\":\"rate limit secret-token\"}");
    Exception e =
        assertThrows(
            IOException.class, () -> new AccountAuth(http).refresh("original", "msa", a -> {}));
    assertEquals(1, http.calls.size());
    assertFalse(e.getMessage().contains("secret-token"));
  }

  @Test
  public void laterXboxFailureDoesNotLoseRotatedToken() {
    Fake http = new Fake();
    http.reply(200, "{\"access_token\":\"msa\",\"refresh_token\":\"rotated\"}");
    http.reply(503, "{}");
    AtomicReference<String> saved = new AtomicReference<>();
    assertThrows(
        IOException.class,
        () -> new AccountAuth(http).refresh("original", "msa", a -> saved.set(a.refresh)));
    assertEquals("rotated", saved.get());
    assertEquals(2, http.calls.size());
  }

  @Test
  public void validStoredTokenAvoidsRefreshAndTransientProfileFailureDoesNotRefresh()
      throws Exception {
    Fake http = new Fake();
    http.reply(200, PROFILE);
    Account a = new Account();
    a.kind = "msa";
    a.access = "cached";
    a.refresh = "renew";
    assertEquals("renew", new AccountAuth(http).login(a, ignored -> {}).refresh);
    assertEquals(1, http.calls.size());
    http.reply(500, "{}");
    assertThrows(IOException.class, () -> new AccountAuth(http).login(a, ignored -> {}));
    assertEquals(2, http.calls.size());
  }

  @Test
  public void labeledMixedDeliveriesExtractOnlyRefreshTokens() {
    String refresh = "M.abcdefghijklmnopqrstuvwxyz123456";
    assertEquals(
        Collections.singletonList(refresh),
        AccountAuth.refreshTokens(
            "mctoken: eyJ.access.token | refreshtoken: "
                + refresh
                + "\nemail@example.test:password:"
                + refresh));
    assertTrue(AccountAuth.refreshTokens("eyJ.this_is_a_minecraft_access_token").isEmpty());
    assertTrue(AccountAuth.refreshTokens("tiny").isEmpty());
  }

  @Test
  public void cookieJarRespectsDomainsPathsExpiryAndRedirectAllowlist() throws Exception {
    AccountCookies jar =
        AccountCookies.parse(
            "# Netscape HTTP Cookie File\n"
                + "#HttpOnly_.login.live.com\tTRUE\t/auth\tTRUE\t0\tMSAAUTH\tprivate\n"
                + ".login.live.com\tTRUE\t/\tTRUE\t1\told\texpired");
    assertEquals(
        "MSAAUTH=private",
        jar.headers(URI.create("https://login.live.com/auth/next")).get("Cookie"));
    assertEquals("", jar.headers(URI.create("https://login.live.com/other")).get("Cookie"));
    assertEquals(
        "", jar.headers(URI.create("https://login.microsoftonline.com/auth")).get("Cookie"));
    assertFalse(AccountCookies.allowed(URI.create("https://login.live.com.evil.test")));
    assertFalse(AccountCookies.allowed(URI.create("http://login.live.com")));
    assertFalse(AccountCookies.allowed(URI.create("https://login.live.com:444")));
    assertThrows(IOException.class, () -> jar.headers(URI.create("https://evil.test")));
  }

  @Test
  public void cookieRedirectCannotExfiltrateCookies() {
    Fake http = new Fake();
    http.replies.add(
        new AccountHttp.Reply(302, "", "https://evil.test/capture", Collections.emptyMap()));
    assertThrows(
        IOException.class, () -> new AccountAuth(http).cookies("MSAAUTH=private", a -> {}));
    assertEquals(1, http.calls.size());
  }

  @Test
  public void cookieAuthorizationCodeRunsTheNormalMsaChain() throws Exception {
    Fake http = new Fake();
    http.replies.add(
        new AccountHttp.Reply(302, "", AccountAuth.DESKTOP + "?code=code", Collections.emptyMap()));
    http.reply(200, "{\"access_token\":\"msa-access\",\"refresh_token\":\"renew\"}");
    chain(http);
    Account a = new AccountAuth(http).cookies("MSAAUTH=private", tokens -> {});
    assertEquals("renew", a.refresh);
    assertEquals("msa", a.kind);
  }

  @Test
  public void malformedProfilesAndOversizedResponsesAreRejected() {
    Fake http = new Fake();
    http.reply(200, "{\"name\":\"Other\",\"id\":\"malformed\"}");
    assertThrows(IOException.class, () -> new AccountAuth(http).access("token"));
    assertThrows(
        IOException.class, () -> AccountHttp.read(new ByteArrayInputStream(new byte[11]), 10));
  }
}
