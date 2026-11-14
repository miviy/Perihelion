package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.lwjgl.input.Keyboard;
import perihelion.core.diagnostics.ClientLog;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.integration.minecraft189.account.*;
import perihelion.integration.minecraft189.gui.*;
import perihelion.integration.minecraft189.gui.font.GuiFonts;
import perihelion.integration.minecraft189.gui.widget.UiCanvas;
import perihelion.integration.minecraft189.render.RenderState;

/** Packaged, transformed account UI/session checks. Synthetic identities; no external requests. */
public final class AccountHookProbe {
  private static int checks;

  public static void title(ClientRuntime production, Object game) throws Exception {
    checks = 0;
    Class<?> screenType = Class.forName("net.minecraft.client.gui.GuiScreen");
    Field current = field(game.getClass(), "currentScreen", "field_71462_r");
    Method display =
        method(game.getClass(), new Class<?>[] {screenType}, "displayGuiScreen", "func_147108_a");
    Object original = current.get(game);
    Object originalSession = method(game.getClass(), "getSession", "func_110432_I").invoke(game);
    boolean repeat = Keyboard.areRepeatEventsEnabled();
    AccountService[] holder = new AccountService[1];
    Path path = Paths.get(System.getProperty("perihelion.smoke.output"), "account-fixture");
    ClientRuntime runtime =
        new ClientRuntime(
            path.resolve("client.json"),
            ClientLog.NONE,
            r -> {
              new ClickGuiService(r);
              holder[0] = new AccountService(r, path.resolve("private"));
            });
    runtime.start();
    AccountService service = holder[0];
    try {
      long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while (!service.ready() && System.nanoTime() < end) Thread.sleep(1);
      check(service.ready(), "account vault opened");
      boolean entry = false;
      for (Object button :
          (List<?>) field(screenType, "buttonList", "field_146292_n").get(original))
        if (button instanceof AccountMenuButton
            || field(button.getClass(), "id", "field_146127_k").getInt(button) == 0x5041)
          entry = true;
      check(entry, "native main menu exposes themed Accounts entry");
      for (int i = 0; i < 15; i++) service.book.upsert(Account.offline("Example_" + i));
      Account first = service.book.snapshot().get(0);
      service.book.pin(first.id);
      AccountService.Job login = service.login(first, true);
      waitJob(login, runtime);
      Object changed = method(game.getClass(), "getSession", "func_110432_I").invoke(game);
      check(changed != originalSession, "offline login writes the transformed session");
      check(
          method(changed.getClass(), "getUsername", "func_111285_a")
              .invoke(changed)
              .equals(first.name),
          "offline session has selected identity");
      Object properties = field(game.getClass(), "profileProperties", "field_181038_N").get(game);
      check(
          (Boolean) method(properties.getClass(), "isEmpty").invoke(properties),
          "switch clears previous profile properties");
      Class<?> sessionType = Class.forName("net.minecraft.util.Session");
      method(SessionAccess.class, new Class<?>[] {sessionType}, "perihelion$session")
          .invoke(game, originalSession);
      login = service.login(first, true);
      waitDone(login);
      service.cancel(login);
      runtime.tasks().drain();
      check(
          method(game.getClass(), "getSession", "func_110432_I").invoke(game) == originalSession,
          "close/cancel suppresses late session switch");
      runtime.commands().execute("accounts", s -> {});
      Object menu = current.get(game);
      draw(menu);
      check(
          menu.getClass().getSimpleName().equals("AccountScreen"),
          "accounts command opens native screen");
      ClickGuiModule preferences = (ClickGuiModule) runtime.modules().require("clickgui").module();
      runtime.modules().setSetting("clickgui", preferences.visuals, ClickGuiModule.Visuals.OLD);
      for (ClickGuiModule.Font font : ClickGuiModule.Font.values())
        for (double scale : new double[] {1, 1.05, 1.5}) {
          runtime.modules().setSetting("clickgui", preferences.font, font);
          runtime.modules().setSetting("clickgui", preferences.scale, scale);
          draw(menu);
          UiCanvas canvas = (UiCanvas) field(menu.getClass(), "canvas").get(menu);
          GuiFonts fonts = (GuiFonts) field(menu.getClass(), "fonts").get(menu);
          try (RenderState ignored = new RenderState().open()) {
            GuiFonts.Face selected = canvas.font;
            if (selected instanceof perihelion.integration.minecraft189.gui.menu.MenuFontScale) {
              perihelion.integration.minecraft189.gui.menu.MenuFontScale scaled =
                  (perihelion.integration.minecraft189.gui.menu.MenuFontScale) selected;
              check(
                  Math.abs(scaled.width("Accounts") - scaled.source.width("Accounts") * 1.2f)
                      < .01f,
                  "account form enlarges selected-font drawing and measurement together");
              selected = scaled.source;
            }
            check(
                selected
                    == (font == ClickGuiModule.Font.MINECRAFT
                        ? fonts.minecraft(GuiTheme.bold(preferences), canvas.pixelScale)
                        : fonts.get(font, GuiTheme.bold(preferences))),
                "account screen uses selected font");
          }
          bounds(menu);
        }
      runtime.modules().setSetting("clickgui", preferences.font, ClickGuiModule.Font.SANS);
      runtime.modules().setSetting("clickgui", preferences.scale, 1.0);
      runtime.modules().setSetting("clickgui", preferences.customTextStyle, true);
      runtime
          .modules()
          .setSetting(
              "clickgui",
              preferences.accent,
              perihelion.core.setting.ColorValue.rgba(66, 146, 124, 255));
      draw(menu);
      check(
          ((UiCanvas) field(menu.getClass(), "canvas").get(menu)).accent == 0xff42927c,
          "account screen follows custom client accent");
      runtime.modules().setSetting("clickgui", preferences.visuals, ClickGuiModule.Visuals.NEW);
      draw(menu);
      check(
          ((UiCanvas) field(menu.getClass(), "canvas").get(menu)).accent == 0xffef883b,
          "New account screen follows the fixed client palette");
      runtime.modules().setSetting("clickgui", preferences.customTextStyle, false);
      draw(menu);
      HudChatProbe.captureWorld(game, "accounts-list.png");
      click(menu, 25, 85, 1);
      draw(menu);
      check(
          field(menu.getClass(), "context").getBoolean(menu), "right-click opens account actions");
      click(menu, 300, 30, 0);
      draw(menu);
      method(menu.getClass(), new Class<?>[] {char.class, int.class}, "type")
          .invoke(menu, '\0', Keyboard.KEY_END);
      draw(menu);
      check(
          field(menu.getClass(), "scroll").getInt(menu) > 0,
          "keyboard selection reveals scrolled row");
      method(menu.getClass(), new Class<?>[] {char.class, int.class}, "type")
          .invoke(menu, '\0', Keyboard.KEY_RETURN);
      AccountService.Job pending = service.active();
      waitJob(pending, runtime); // offline-only fixture
      method(SessionAccess.class, new Class<?>[] {sessionType}, "perihelion$session")
          .invoke(game, originalSession);
      draw(menu);
      method(menu.getClass(), new Class<?>[] {char.class, int.class}, "type")
          .invoke(menu, '\0', Keyboard.KEY_DELETE);
      draw(menu);
      check(
          field(menu.getClass(), "confirmation").get(menu) != null,
          "removal requires confirmation");
      click(menu, 160, 248, 0);
      waitJob(service.active(), runtime);
      check(service.book.snapshot().size() == 14, "confirmed removal updates the vault");
      draw(menu);
      click(menu, 365, 255, 0);
      draw(menu);
      click(menu, 310, 228, 0);
      waitJob(service.active(), runtime);
      check(service.book.snapshot().size() == 15, "one-step undo restores account");
      draw(menu);
      click(menu, 205, 255, 0);
      Object add = current.get(game);
      draw(add);
      check(
          add.getClass().getSimpleName().equals("AccountAddScreen"),
          "Add opens a focused sign-in screen");
      for (int i = 0; i < 6; i++) {
        click(add, 40, 50 + i * 28, 0);
        draw(add);
        bounds(add);
      }
      HudChatProbe.captureWorld(game, "accounts-add.png");
      escape(add);
      check(current.get(game) == menu, "Add returns to same account list");
      draw(menu);
      click(menu, 365, 255, 0);
      draw(menu);
      click(menu, 310, 180, 0);
      Object profiles = current.get(game);
      draw(profiles);
      check(
          profiles.getClass().getSimpleName().equals("AccountProfileScreen"), "profile tools open");
      for (int i = 0; i < 4; i++) {
        click(profiles, 35, 50 + i * 28, 0);
        if (service.active() != null) waitJob(service.active(), runtime);
        draw(profiles);
        bounds(profiles);
      }
      // A tiny local PNG exercises actual texture upload and cleanup.
      BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
      ByteArrayOutputStream png = new ByteArrayOutputStream();
      ImageIO.write(image, "png", png);
      AccountProfiles.Skin skin = new AccountProfiles.Skin(png.toByteArray(), true, "Synthetic");
      method(profiles.getClass(), new Class<?>[] {AccountProfiles.Skin.class}, "preview")
          .invoke(profiles, skin);
      draw(profiles);
      Object texture = field(profiles.getClass(), "preview").get(profiles);
      check(texture != null, "preview texture uploads");
      HudChatProbe.captureWorld(game, "accounts-profile.png");
      escape(profiles);
      check(
          field(profiles.getClass(), "preview").get(profiles) == null,
          "preview closes without retaining texture");
      draw(menu);
      click(menu, 365, 255, 0);
      draw(menu);
      click(menu, 310, 204, 0);
      Object stores = current.get(game);
      draw(stores);
      check(
          stores.getClass().getSimpleName().equals("AccountStoreScreen"),
          "stores use secondary screen");
      for (int i = 0; i < 3; i++) {
        click(stores, 40 + i * 144, 50, 0);
        draw(stores);
        bounds(stores);
      }
      HudChatProbe.captureWorld(game, "accounts-stores.png");
      escape(stores);
      draw(menu);
      escape(menu);
      check(current.get(game) == original, "Done/Escape returns to original menu");
      check(
          ((Map<?, ?>)
                  field(field(menu.getClass(), "fonts").get(menu).getClass(), "atlases")
                      .get(field(menu.getClass(), "fonts").get(menu)))
              .isEmpty(),
          "closed account screen releases font atlases");
    } finally {
      method(
              SessionAccess.class,
              new Class<?>[] {Class.forName("net.minecraft.util.Session")},
              "perihelion$session")
          .invoke(game, originalSession);
      display.invoke(game, original);
      runtime.stop();
      check(Keyboard.areRepeatEventsEnabled() == repeat, "keyboard repeat restored");
      check(runtime.tasks().pendingCount() == 0, "account task queue cleared");
    }
    System.out.println("PERIHELION_SMOKE ACCOUNTS PASS checks=" + checks);
  }

  public static void world(ClientRuntime runtime, Object game) throws Exception {
    int count = 0;
    Path path = Paths.get(System.getProperty("perihelion.smoke.output"), "account-world");
    ClientRuntime isolated =
        new ClientRuntime(path.resolve("client.json"), ClientLog.NONE, r -> {});
    isolated.start();
    AccountService service = new AccountService(isolated, path.resolve("private"));
    try {
      try {
        service.switchSession(Account.offline("OtherUser"));
        throw new AssertionError("World session switch accepted");
      } catch (IllegalStateException expected) {
        count++;
      }
      BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
      service.localSkin.apply(image, true);
      Object player = field(game.getClass(), "thePlayer", "field_71439_g").get(game);
      check(
          method(player.getClass(), "getLocationSkin", "func_110306_p")
              .invoke(player)
              .equals(method(LocalAccountSkin.class, "location").invoke(null)),
          "local skin hook uses owned texture");
      count++;
      check(
          method(player.getClass(), "getSkinType", "func_175154_l").invoke(player).equals("slim"),
          "local skin hook applies slim model");
      count++;
    } finally {
      isolated.stop();
    }
    check(
        method(LocalAccountSkin.class, "location").invoke(null) == null,
        "local skin texture retired");
    System.out.println("PERIHELION_SMOKE ACCOUNT_WORLD PASS checks=" + (count + 1));
  }

  private static void waitDone(AccountService.Job job) throws Exception {
    long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (job != null && !job.done && System.nanoTime() < end) Thread.sleep(1);
    check(job != null && job.done, "account operation completed");
  }

  private static void waitJob(AccountService.Job job, ClientRuntime runtime) throws Exception {
    waitDone(job);
    runtime.tasks().drain();
  }

  private static void click(Object screen, float x, float y, int button) throws Exception {
    method(screen.getClass(), new Class<?>[] {float.class, float.class, int.class}, "click")
        .invoke(screen, x, y, button);
  }

  private static void escape(Object screen) throws Exception {
    method(
            Class.forName("net.minecraft.client.gui.GuiScreen"),
            new Class<?>[] {char.class, int.class},
            "keyTyped",
            "func_73869_a")
        .invoke(screen, '\0', Keyboard.KEY_ESCAPE);
  }

  private static void draw(Object screen) throws Exception {
    method(
            screen.getClass(),
            new Class<?>[] {int.class, int.class, float.class},
            "drawScreen",
            "func_73863_a")
        .invoke(screen, -1, -1, 0f);
  }

  private static void bounds(Object screen) throws Exception {
    float w = field(screen.getClass(), "panelWidth").getFloat(screen),
        h = field(screen.getClass(), "panelHeight").getFloat(screen);
    for (Object action : (List<?>) field(screen.getClass(), "actions").get(screen)) {
      float x = field(action.getClass(), "x").getFloat(action),
          y = field(action.getClass(), "y").getFloat(action),
          aw = field(action.getClass(), "w").getFloat(action),
          ah = field(action.getClass(), "h").getFloat(action);
      check(x >= 0 && y >= 0 && x + aw <= w && y + ah <= h, "action stays inside compact panel");
    }
  }

  private static void check(boolean condition, String text) {
    checks++;
    if (!condition) throw new AssertionError(text);
  }
}
