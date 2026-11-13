package perihelion.integration.minecraft189.account;

import static org.junit.Assert.*;

import com.google.gson.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import net.minecraft.util.Session;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import perihelion.core.diagnostics.ClientLog;
import perihelion.core.lifecycle.ClientRuntime;

public class AccountServiceTest {
  @Rule public TemporaryFolder folder = new TemporaryFolder();
  private ClientRuntime runtime;
  private AccountService service;
  private AccountAuthTest.Fake http;

  @Before
  public void setup() throws Exception {
    Path root = folder.newFolder().toPath();
    runtime = new ClientRuntime(root.resolve("client.json"), ClientLog.NONE, r -> {});
    runtime.start();
    http = new AccountAuthTest.Fake();
    service =
        new AccountService(
            runtime,
            root.resolve("accounts"),
            http,
            new Session("Launcher", "00000000000000000000000000000001", "0", "legacy"));
    long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (!service.ready() && System.nanoTime() < end) Thread.sleep(1);
    assertTrue(service.storageStatus(), service.ready());
  }

  @After
  public void close() {
    runtime.stop();
  }

  private void done(AccountService.Job job) throws Exception {
    assertNotNull(job);
    long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (!job.done && System.nanoTime() < end) Thread.sleep(1);
    assertTrue(job.status, job.done);
  }

  private <T> T run(AccountService.Work<T> work) throws Exception {
    AtomicReference<T> result = new AtomicReference<>();
    AccountService.Job job = service.submit("test", work, result::set);
    done(job);
    runtime.tasks().drain();
    assertNotNull(job.status, result.get());
    return result.get();
  }

  @Test
  public void cancellationSuppressesACompletedButUnpublishedResult() throws Exception {
    AtomicBoolean applied = new AtomicBoolean();
    AccountService.Job job = service.submit("test", j -> true, value -> applied.set(true));
    done(job);
    service.cancel(job);
    runtime.tasks().drain();
    assertFalse(applied.get());
  }

  @Test
  public void failedCheckRetainsTheAccountAndReportsItsValidationState() throws Exception {
    Account account = Account.offline("Example");
    account.kind = "token";
    account.access = "expired";
    run(j -> service.add(account));
    http.reply(401, "{}");
    AccountService.Job job = service.login(account, false);
    done(job);
    runtime.tasks().drain();
    assertEquals("Sign-in failed", service.book.get(account.id).validation);
    assertEquals(1, service.book.snapshot().size());
  }

  @Test
  public void invalidInputFailuresDoNotEchoCredentialMaterial() throws Exception {
    AccountService.Job job =
        service.submit(
            "test",
            j -> {
              throw new IllegalArgumentException("secret-refresh-token");
            },
            ignored -> fail());
    done(job);
    assertFalse(job.status.contains("secret-refresh-token"));
  }

  @Test
  public void cookieRotationSurvivesALaterXboxFailure() throws Exception {
    http.replies.add(
        new AccountHttp.Reply(302, "", AccountAuth.DESKTOP + "?code=code", Collections.emptyMap()));
    http.reply(200, "{\"access_token\":\"msa\",\"refresh_token\":\"cookie-rotation\"}");
    http.reply(503, "{}");
    AccountService.Job job =
        service.submit(
            "cookie", j -> service.importCookies("MSAAUTH=synthetic"), ignored -> fail());
    done(job);
    http.reply(400, "{}");
    http.reply(429, "{}");
    job =
        service.submit("retry", j -> service.importCookies("MSAAUTH=synthetic"), ignored -> fail());
    done(job);
    assertTrue(http.calls.get(3).contains("refresh_token=cookie-rotation"));
  }

  @Test
  public void cancelledWorkerDoesNotLeaveTheServicePermanentlyBusy() throws Exception {
    CountDownLatch entered = new CountDownLatch(1);
    AccountService.Job first =
        service.submit(
            "test",
            j -> {
              entered.countDown();
              new CountDownLatch(1).await();
              return true;
            },
            ignored -> fail("cancelled result applied"));
    assertTrue(entered.await(2, TimeUnit.SECONDS));
    service.cancel(first);
    assertFalse(service.busy());
    assertEquals(Boolean.TRUE, run(j -> true));
  }

  @Test
  public void importRotationSurvivesFailureAndNextAttemptUsesTheReplacement() throws Exception {
    http.reply(200, "{\"access_token\":\"msa\",\"refresh_token\":\"rotated-secret\"}");
    http.reply(503, "{}");
    AccountService.Job failed =
        service.submit("test", j -> service.importRefresh("original-secret"), ignored -> fail());
    done(failed);
    assertTrue(failed.status.contains("503"));
    http.reply(400, "{\"error\":\"invalid_grant\"}");
    http.reply(400, "{\"error\":\"invalid_grant\"}");
    AccountService.Job retry =
        service.submit("retry", j -> service.importRefresh("original-secret"), ignored -> fail());
    done(retry);
    assertTrue(http.calls.get(2).contains("refresh_token=rotated-secret"));
  }

  @Test
  public void partialPurchasesRemainRecoverableAndAreNeverAutomaticallyReplayed() throws Exception {
    AccountStores.Catalog catalog = catalog(AccountStores.Provider.NICEALTS);
    AccountStores.Product p = product();
    http.reply(200, "{\"items\":[\"eyJ.synthetic-access-token\"]}");
    http.reply(503, "{}");
    AccountService.Job purchase =
        service.submit("buy", j -> service.stores.purchase(catalog, p, 2, j), ignored -> fail());
    done(purchase);
    assertTrue(purchase.notice.contains("before buying again"));
    assertEquals(2, http.calls.size());
    JsonObject journal = service.data("deliveries");
    assertEquals(1, journal.entrySet().size());
    JsonObject delivery = journal.entrySet().iterator().next().getValue().getAsJsonObject();
    assertEquals(1, delivery.getAsJsonArray("items").size());
    http.reply(200, AccountAuthTest.PROFILE);
    run(j -> service.stores.importDelivery(catalog.provider, delivery, false, j));
    assertEquals(1, service.book.snapshot().size());
    String id = service.book.snapshot().get(0).id;
    run(
        j -> {
          service.book.remove(id);
          service.save();
          return true;
        });
    int requests = http.calls.size();
    run(j -> service.stores.importDelivery(catalog.provider, delivery, false, j));
    assertTrue(service.book.snapshot().isEmpty());
    assertEquals(requests, http.calls.size());
    http.reply(200, AccountAuthTest.PROFILE);
    run(j -> service.stores.importDelivery(catalog.provider, delivery, true, j));
    assertEquals(1, service.book.snapshot().size());
  }

  @Test
  public void selectingOneDeliveryItemDoesNotTruncateTheRecoveryJournal() throws Exception {
    JsonObject complete =
        new JsonParser()
            .parse(
                "{\"id\":\"order\",\"kind\":\"access\",\"items\":[\"eyJ.first\",\"eyJ.second\"]}")
            .getAsJsonObject();
    JsonObject subset =
        new JsonParser()
            .parse("{\"id\":\"order\",\"kind\":\"access\",\"items\":[\"eyJ.first\"]}")
            .getAsJsonObject();
    http.reply(200, AccountAuthTest.PROFILE);
    run(
        j ->
            service.stores.importSelection(
                AccountStores.Provider.NICEALTS, complete, subset, false, j));
    assertEquals(
        2,
        service
            .data("deliveries")
            .getAsJsonObject("NICEALTS:order")
            .getAsJsonArray("items")
            .size());
  }

  @Test
  public void accessFallbackKeepsTheRotatedRefreshTokenFromAMixedDelivery() throws Exception {
    JsonObject delivery =
        new JsonParser()
            .parse(
                "{\"id\":\"mixed\",\"kind\":\"refresh\",\"items\":[\"mctoken: eyJ.synthetic |"
                    + " refreshtoken: M.abcdefghijklmnopqrstuvwxyz123456\"]}")
            .getAsJsonObject();
    http.reply(200, "{\"access_token\":\"msa\",\"refresh_token\":\"latest-refresh\"}");
    http.reply(503, "{}");
    http.reply(200, AccountAuthTest.PROFILE);
    run(j -> service.stores.importDelivery(AccountStores.Provider.NICEALTS, delivery, false, j));
    assertEquals("latest-refresh", service.book.snapshot().get(0).refresh);
    assertEquals("Microsoft", service.book.snapshot().get(0).type());
  }

  @Test
  public void legacyHistoryWithoutAProductTypeCanImportABareCookie() throws Exception {
    JsonObject delivery =
        new JsonParser()
            .parse(
                "{\"id\":\"old-cookie\",\"kind\":\"auto\",\"items\":[\"opaque-cookie-value-abcdefghijklmnopqrstuvwxyz\"]}")
            .getAsJsonObject();
    http.reply(400, "{}");
    http.reply(400, "{}");
    http.replies.add(
        new AccountHttp.Reply(302, "", AccountAuth.DESKTOP + "?code=code", Collections.emptyMap()));
    http.reply(200, "{\"access_token\":\"msa\",\"refresh_token\":\"cookie-refresh\"}");
    http.reply(200, "{\"Token\":\"xbl\"}");
    http.reply(200, "{\"Token\":\"xsts\",\"DisplayClaims\":{\"xui\":[{\"uhs\":\"hash\"}]}}");
    http.reply(200, "{\"access_token\":\"minecraft\"}");
    http.reply(200, AccountAuthTest.PROFILE);
    run(j -> service.stores.importDelivery(AccountStores.Provider.LOCALTS, delivery, false, j));
    assertEquals("cookie-refresh", service.book.snapshot().get(0).refresh);
  }

  @Test
  public void fernanEnvelopeAndCooldownLimitAreHonored() throws Exception {
    http.reply(200, "{\"success\":true,\"data\":{\"username\":\"shop\",\"balance\":25}}");
    http.reply(
        200,
        "{\"success\":true,\"data\":{\"stock\":[{\"product_id\":7,\"product_name\":\"Cookies\",\"count\":30,\"price\":2,\"purchase_limit\":10}]}}");
    http.reply(
        200,
        "{\"success\":true,\"data\":{\"cooldowns\":[{\"product_id\":7,\"remaining_allowed\":2}]}}");
    AccountStores.Catalog catalog =
        run(j -> service.stores.connect(AccountStores.Provider.FERNAN, "key"));
    assertEquals("25", catalog.balance);
    assertEquals(2, catalog.products.get(0).limit);
    assertThrows(IllegalArgumentException.class, () -> catalog.products.get(0).total(3));
  }

  @Test
  public void minemenCustomPurchaseUsesChatModeAndSavesItsDelivery() throws Exception {
    http.reply(200, "{\"tokens\":[\"eyJ.custom\"]}");
    http.reply(200, AccountAuthTest.PROFILE);
    run(
        j ->
            service.stores.custom(
                catalog(AccountStores.Provider.NICEALTS), "eu.minemen.club", "1.8", false, j));
    assertTrue(http.calls.get(0).contains("\"ban_message\":\"chat\""));
    assertEquals(1, service.data("deliveries").entrySet().size());
  }

  private AccountStores.Catalog catalog(AccountStores.Provider p) {
    return new AccountStores.Catalog(
        p,
        "test-key",
        new JsonParser().parse("{\"username\":\"shop\",\"balance\":100}").getAsJsonObject(),
        Collections.emptyList());
  }

  private AccountStores.Product product() {
    AccountStores.Product p = new AccountStores.Product();
    p.id = "1";
    p.name = "Test";
    p.kind = "refresh";
    p.price = new BigDecimal("1");
    p.stock = 10;
    p.limit = 10;
    return p;
  }
}
