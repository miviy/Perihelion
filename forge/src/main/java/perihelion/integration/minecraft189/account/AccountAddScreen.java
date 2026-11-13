package perihelion.integration.minecraft189.account;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

/** One focused form per sign-in method, with explicit clipboard and bounded file imports. */
public final class AccountAddScreen extends AccountUi {
  private enum Method {
    DEVICE,
    BROWSER,
    REFRESH,
    OFFLINE,
    COOKIES,
    ACCESS
  }

  private static final String[] NAMES = {
    "Microsoft device",
    "Microsoft browser",
    "Refresh tokens",
    "Offline username",
    "Cookie export",
    "Minecraft token"
  };
  private Method method = Method.DEVICE;
  private Input value, path;

  public AccountAddScreen(AccountService service, GuiScreen parent) {
    super(service, parent);
    panelHeight = 272;
  }

  @Override
  protected String title() {
    return "Add account";
  }

  @Override
  protected void setup() {
    value = input(true, 32768);
    path = input(false, 1024);
  }

  @Override
  protected void content(float mx, float my) {
    for (int i = 0; i < NAMES.length; i++) {
      final Method next = Method.values()[i];
      navigation(
          NAMES[i],
          16,
          43 + i * 28,
          132,
          22,
          next == method,
          idle(),
          () -> {
            method = next;
            value.text("");
            path.text("");
            status = "";
          });
    }
    String[] descriptions = {
      "Sign in on Microsoft's website with a temporary code.",
      "Sign in with Microsoft in your browser.",
      "Import one refresh token, a pasted list, or a .txt file.",
      "Choose a username for offline-mode servers.",
      "Import a cookies.txt export, JSON, or a Cookie header.",
      "Add an access token. Expired tokens must be added again."
    };
    List<String> lines =
        perihelion.integration.minecraft189.gui.widget.UiCanvas.wrap(
            canvas.font, descriptions[method.ordinal()], 270);
    for (int i = 0; i < lines.size(); i++) label(lines.get(i), 164, 44 + i * 12, 270, canvas.text);
    if (method == Method.DEVICE || method == Method.BROWSER) {
      button("Start sign-in", 164, 117, 270, idle(), this::signIn);
      if (job != null && !job.done) {
        String text = job.status;
        label(text, 164, 153, 270, canvas.enabled);
        if (text.startsWith("Microsoft code: "))
          button(
              "Copy code",
              164,
              178,
              128,
              true,
              () -> setClipboardString(text.substring(16).split(" ")[0]));
      }
      if (service.hasInterruptedSignIn())
        button(
            "Resume saved sign-in",
            164,
            215,
            270,
            idle(),
            () ->
                start(
                    service.submit(
                        "Resuming sign-in...",
                        j -> {
                          String token = service.pendingRefresh();
                          if (token.isEmpty())
                            throw new IOException("No interrupted sign-in to resume.");
                          return service.add(service.importRefresh(token));
                        },
                        a -> result("Added " + a.name + "."))));
      return;
    }
    if (method == Method.OFFLINE) {
      // Username is ordinary text, rendered with the shared font instead of a secret field.
      field(path, "Username", 164, 111, 270);
      button("Add offline account", 164, 142, 270, idle(), this::addOffline);
      return;
    }
    field(
        value,
        method == Method.ACCESS
            ? "Minecraft access token"
            : method == Method.COOKIES ? "Cookie text" : "Refresh token",
        164,
        111,
        270);
    button("Add token", 164, 139, 131, idle(), () -> importText(value.text()));
    button("From clipboard", 303, 139, 131, idle(), () -> importText(getClipboardString()));
    field(
        path,
        method == Method.COOKIES ? "Cookie file or folder path" : "Text file path",
        164,
        175,
        270);
    button("Import file", 164, 203, 270, idle(), this::importFile);
  }

  private void signIn() {
    Method chosen = method;
    start(
        service.submit(
            "Starting Microsoft sign-in...",
            j -> {
              Account a =
                  chosen == Method.DEVICE
                      ? service.auth.device(
                          s -> j.status = s,
                          url -> service.browser(url, j),
                          service::captureNewRotation)
                      : service.auth.browser(
                          s -> j.status = s,
                          url -> service.browser(url, j),
                          service::captureNewRotation);
              return service.add(a);
            },
            a -> result("Added " + a.name + ". Choose Log in from Accounts.")));
  }

  private void addOffline() {
    String name = path.text().trim();
    start(
        service.submit(
            "Adding offline account...",
            j -> service.add(Account.offline(name)),
            a -> {
              result("Added " + a.name + ".");
              path.text("");
            }));
  }

  private void importText(String text) {
    Method chosen = method;
    start(service.submit("Importing accounts...", j -> importData(chosen, text, j), this::result));
  }

  private String importData(Method chosen, String text, AccountService.Job job) throws Exception {
    if (chosen == Method.REFRESH) {
      List<String> tokens = AccountAuth.refreshTokens(text);
      if (tokens.isEmpty()) throw new IOException("No refresh tokens found.");
      int added = 0, failed = 0;
      for (int i = 0; i < tokens.size(); i++) {
        job.check();
        job.status = "Importing " + (i + 1) + "/" + tokens.size() + "...";
        try {
          service.add(service.importRefresh(tokens.get(i)));
          added++;
        } catch (InterruptedException error) {
          throw error;
        } catch (Exception error) {
          failed++;
        }
      }
      return "Added " + added + " accounts; " + failed + " failed. No sessions were switched.";
    }
    Account a = chosen == Method.COOKIES ? service.importCookies(text) : service.auth.access(text);
    service.add(a);
    return "Added " + a.name + ".";
  }

  private void importFile() {
    String filename = path.text();
    Method chosen = method;
    start(
        service.submit(
            "Reading import...",
            j -> {
              Path file = Paths.get(filename).toAbsolutePath().normalize();
              if (chosen == Method.COOKIES && Files.isDirectory(file)) {
                int added = 0, failed = 0, count = 0;
                try (DirectoryStream<Path> files = Files.newDirectoryStream(file, "*.txt")) {
                  for (Path candidate : files) {
                    j.check();
                    if (++count > AccountBook.LIMIT) break;
                    try {
                      Account a =
                          service.importCookies(AccountService.readText(candidate.toString()));
                      service.add(a);
                      added++;
                    } catch (InterruptedException error) {
                      throw error;
                    } catch (Exception error) {
                      failed++;
                    }
                  }
                }
                return "Added " + added + " cookie accounts; " + failed + " failed.";
              }
              return importData(chosen, AccountService.readText(filename), j);
            },
            this::result));
  }

  @Override
  protected void shortcut(char c, int key) {
    if (key == Keyboard.KEY_RETURN && idle()) {
      if (method == Method.DEVICE || method == Method.BROWSER) signIn();
      else if (method == Method.OFFLINE) addOffline();
      else importText(value.text());
    }
  }
}
