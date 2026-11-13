package perihelion.integration.minecraft189.account;

import com.google.gson.*;
import java.awt.Desktop;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.Session;
import perihelion.core.command.ClientCommand;
import perihelion.core.diagnostics.Failures;
import perihelion.core.lifecycle.ClientRuntime;

/** Runtime-owned, serial account work; only client-queue callbacks may change a session. */
public final class AccountService implements AutoCloseable {
  public interface Work<T> {
    T run(Job job) throws Exception;
  }

  public static final class Job {
    public volatile String status;
    public volatile String notice = "";
    public volatile boolean done, cancelled;
    private volatile Future<?> future;

    private Job(String status) {
      this.status = status;
    }

    public void check() throws InterruptedException {
      if (cancelled || Thread.currentThread().isInterrupted()) throw new InterruptedException();
    }
  }

  public final ClientRuntime runtime;
  public final AccountBook book = new AccountBook();
  public final AccountHttp http;
  public final AccountAuth auth;
  public final AccountStores stores;
  public final AccountProfiles profiles;
  public final LocalAccountSkin localSkin = new LocalAccountSkin();
  public final perihelion.integration.minecraft189.gui.font.GuiFonts menuFonts =
      new perihelion.integration.minecraft189.gui.font.GuiFonts();
  private final AccountVault vault;
  private final ThreadPoolExecutor worker;
  private final Gson gson = new Gson();
  private JsonObject privateData = new JsonObject();
  private volatile boolean ready, closed;
  private volatile boolean interruptedSignIn;
  private volatile String storageStatus = "Loading accounts...";
  private volatile Job active;
  private final ThreadLocal<Job> executing = new ThreadLocal<>();
  public final Session launcher;

  public AccountService(ClientRuntime runtime, Path directory) {
    this(runtime, directory, new AccountHttp());
  }

  public AccountService(ClientRuntime runtime, Path directory, AccountHttp http) {
    this(runtime, directory, http, Minecraft.getMinecraft().getSession());
  }

  AccountService(ClientRuntime runtime, Path directory, AccountHttp http, Session initialSession) {
    this.runtime = runtime;
    this.http = http;
    auth = new AccountAuth(http);
    vault = new AccountVault(directory);
    launcher = initialSession;
    worker =
        new ThreadPoolExecutor(
            1,
            1,
            0,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<Runnable>(8),
            r -> {
              Thread t = new Thread(r, "Perihelion accounts");
              t.setDaemon(true);
              return t;
            });
    stores = new AccountStores(this);
    profiles = new AccountProfiles(this);
    worker.execute(
        () -> {
          try {
            privateData = vault.load();
            Account[] loaded =
                privateData.has("accounts")
                    ? gson.fromJson(privateData.get("accounts"), Account[].class)
                    : new Account[0];
            book.load(Arrays.asList(loaded));
            interruptedSignIn = !AccountHttp.text(data("pendingSignIn"), "refresh").isEmpty();
            AccountProfiles.Skin skin = profiles.local();
            if (skin != null) {
              java.awt.image.BufferedImage image = skin.image();
              runtime
                  .tasks()
                  .execute(
                      () -> {
                        if (!closed && runtime.isRunning()) localSkin.apply(image, skin.slim);
                      });
            }
            ready = true;
            storageStatus = "";
          } catch (Exception error) {
            storageStatus =
                "Account storage could not be opened. Restore the vault and key from backup.";
          }
        });
    runtime
        .commands()
        .register(
            new ClientCommand() {
              public String name() {
                return "accounts";
              }

              public String usage() {
                return "accounts";
              }

              public void execute(List<String> args, Consumer<String> output) {
                if (!args.isEmpty()) throw new IllegalArgumentException("Use .accounts");
                open(Minecraft.getMinecraft().currentScreen);
              }
            });
    runtime.own(this);
  }

  public void open(GuiScreen parent) {
    runtime.tasks().checkThread();
    if (runtime.isRunning())
      Minecraft.getMinecraft().displayGuiScreen(new AccountScreen(this, parent));
  }

  public boolean ready() {
    return ready && !closed;
  }

  public String storageStatus() {
    return storageStatus;
  }

  public boolean hasInterruptedSignIn() {
    return interruptedSignIn;
  }

  public boolean busy() {
    Job j = active;
    return j != null && !j.done;
  }

  public Job active() {
    return active;
  }

  public <T> Job submit(String label, Work<T> work, Consumer<T> completion) {
    runtime.tasks().checkThread();
    if (!ready() || busy()) return null;
    Job job = new Job(label);
    active = job;
    job.future =
        worker.submit(
            () -> {
              try {
                executing.set(job);
                job.check();
                T value = work.run(job);
                runtime
                    .tasks()
                    .execute(
                        () -> {
                          if (!closed && !job.cancelled && runtime.isRunning())
                            completion.accept(value);
                        });
                if (!job.cancelled) job.status = "Done.";
              } catch (Throwable failure) {
                Failures.rethrowFatal(failure);
                job.status =
                    job.cancelled
                            || failure instanceof InterruptedException
                            || failure instanceof InterruptedIOException
                        ? "Cancelled."
                        : failure instanceof IOException
                            ? failure.getMessage()
                            : failure instanceof IllegalArgumentException
                                ? "Invalid account input. Check the selected fields."
                                : "Account operation failed. Try again.";
              } finally {
                executing.remove();
                job.done = true;
              }
            });
    return job;
  }

  public void cancel(Job job) {
    runtime.tasks().checkThread();
    if (job == null) return;
    job.cancelled = true;
    if (job.done || job != active) return;
    job.status = "Cancelling...";
    auth.close();
    http.cancel();
    Future<?> f = job.future;
    if (f != null) f.cancel(true);
    job.done = true;
  }

  /** Called only on the serial worker, including rotated-token persistence. */
  public void save() throws Exception {
    privateData.addProperty("version", 1);
    privateData.add("accounts", gson.toJsonTree(book.snapshot()));
    vault.save(privateData);
  }

  JsonObject data(String key) {
    if (!privateData.has(key)) privateData.add(key, new JsonObject());
    return privateData.getAsJsonObject(key);
  }

  public Job persist(String label, Runnable edit) {
    return submit(
        label,
        j -> {
          edit.run();
          save();
          return true;
        },
        ignored -> {});
  }

  public Consumer<Account> rotation(Account existing) {
    return tokens -> {
      if (existing == null || tokens.refresh.isEmpty()) return;
      existing.refresh = tokens.refresh;
      existing.kind = tokens.kind;
      if (book.get(existing.id) != null) {
        book.upsert(existing);
        try {
          save();
        } catch (Exception e) {
          throw new IllegalStateException("Could not save the refreshed account.");
        }
      }
    };
  }

  public Account add(Account a) throws Exception {
    boolean fresh = true;
    for (Account old : book.snapshot())
      if (old.uuid.replace("-", "").equalsIgnoreCase(a.uuid.replace("-", ""))
          && "offline".equals(old.kind) == "offline".equals(a.kind)) fresh = false;
    Account saved = book.upsert(a);
    if (!a.refresh.isEmpty()
        && a.refresh.equals(AccountHttp.text(data("pendingSignIn"), "refresh"))) {
      data("pendingSignIn").remove("refresh");
      interruptedSignIn = false;
    }
    save();
    if (fresh && !a.access.isEmpty()) {
      try {
        profiles.applyDefault(a);
      } catch (Exception error) {
        Job job = executing.get();
        if (job != null) job.notice = "Account saved; the default skin could not be applied.";
      }
    }
    return saved;
  }

  public void captureNewRotation(Account tokens) {
    if (tokens.refresh.isEmpty()) return;
    data("pendingSignIn").addProperty("refresh", tokens.refresh);
    interruptedSignIn = true;
    data("pendingSignIn").addProperty("kind", tokens.kind);
    try {
      save();
    } catch (Exception e) {
      throw new IllegalStateException("Could not save the sign-in token.");
    }
  }

  public String pendingRefresh() {
    return AccountHttp.text(data("pendingSignIn"), "refresh");
  }

  public Account importRefresh(String token) throws Exception {
    String key = recoveryKey(token);
    String recovered = AccountHttp.text(data("refreshRecovery"), key);
    return auth.refresh(
        recovered.isEmpty() ? token : recovered,
        "msa",
        tokens -> {
          data("refreshRecovery").addProperty(key, tokens.refresh);
          try {
            save();
          } catch (Exception error) {
            throw new IllegalStateException("Could not save a rotated import token.");
          }
        });
  }

  public Account importCookies(String input) throws Exception {
    String key = recoveryKey(input);
    Consumer<Account> rotated =
        tokens -> {
          if (!tokens.refresh.isEmpty()) data("cookieRecovery").addProperty(key, tokens.refresh);
          captureNewRotation(tokens);
        };
    String recovered = AccountHttp.text(data("cookieRecovery"), key);
    if (!recovered.isEmpty()) {
      try {
        return auth.refresh(recovered, "msa", rotated);
      } catch (IOException error) {
        if (!error.getMessage().contains("HTTP 400") && !error.getMessage().contains("credentials"))
          throw error;
      }
    }
    return auth.cookies(input, rotated);
  }

  private static String recoveryKey(String source) throws Exception {
    return Base64.getEncoder()
        .encodeToString(
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(source.getBytes(StandardCharsets.UTF_8)));
  }

  String recoveredToken(String source, boolean cookie) throws Exception {
    if (source.isEmpty()) return "";
    return AccountHttp.text(
        data(cookie ? "cookieRecovery" : "refreshRecovery"), recoveryKey(source));
  }

  public Account authenticate(Account a) throws Exception {
    try {
      Account result = auth.login(a, rotation(a));
      result.id = a.id;
      return add(result);
    } catch (IOException error) {
      if (error.getMessage() != null
          && error.getMessage().contains("credentials")
          && book.get(a.id) != null) {
        a.validation = "Sign-in failed";
        book.upsert(a);
        save();
      }
      throw error;
    }
  }

  public void switchSession(Account account) {
    runtime.tasks().checkThread();
    Minecraft game = Minecraft.getMinecraft();
    if (closed || game.theWorld != null || game.getNetHandler() != null)
      throw new IllegalStateException("Disconnect before switching accounts.");
    ((SessionAccess) game)
        .perihelion$session(
            new Session(
                account.name,
                account.uuid,
                account.access,
                "offline".equals(account.kind) ? "legacy" : "mojang"));
  }

  public Job login(Account a, boolean switchAccount) {
    return submit(
        switchAccount ? "Signing in..." : "Checking account...",
        j -> authenticate(a),
        result -> {
          if (!switchAccount) return;
          try {
            switchSession(result);
          } catch (IllegalStateException e) {
            Job job = active;
            if (job != null) job.status = e.getMessage();
          }
        });
  }

  public void recordBan(String uuid, long until) {
    if (!ready()) return;
    try {
      worker.execute(
          () -> {
            if (!closed)
              try {
                book.ban(uuid, until);
                save();
              } catch (Exception ignored) {
                storageStatus = "Could not save the observed ban status.";
              }
          });
    } catch (RejectedExecutionException ignored) {
    }
  }

  public Account launcherAccount() {
    if ("0".equals(launcher.getToken()) || launcher.getToken().isEmpty())
      return Account.offline(launcher.getUsername());
    Account a = new Account();
    a.name = launcher.getUsername();
    a.uuid = launcher.getPlayerID();
    a.access = launcher.getToken();
    a.kind = "launcher";
    return a;
  }

  public static String readText(String path) throws Exception {
    return new String(
        AccountVault.readBounded(Paths.get(path).toAbsolutePath().normalize()),
        StandardCharsets.UTF_8);
  }

  public void browser(String url, Job job) {
    if (job.cancelled || closed) return;
    try {
      if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create(url));
    } catch (Exception ignored) {
      job.status = "Open the sign-in page in your browser. " + job.status;
    }
  }

  @Override
  public void close() {
    closed = true;
    ready = false;
    auth.close();
    http.close();
    Job job = active;
    if (job != null) job.cancelled = true;
    worker.shutdownNow();
    localSkin.close();
    menuFonts.close();
    Minecraft game = Minecraft.getMinecraft();
    if (game != null && game.currentScreen instanceof AccountUi) game.displayGuiScreen(null);
  }
}
