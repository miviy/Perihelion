package perihelion.integration.minecraft189.hud;

import static org.junit.Assert.*;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import perihelion.core.diagnostics.ClientLog;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.setting.Setting;

/** The memoized overlay values must always follow the settings they were derived from. */
public class OverlaySettingsCacheTest {
  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private ClientRuntime runtime() {
    Path path = folder.getRoot().toPath().resolve("client.json");
    ClientRuntime r =
        new ClientRuntime(
            path,
            ClientLog.NONE,
            client -> client.modules().register(new HudModule(client, () -> null)));
    r.start();
    return r;
  }

  @Test
  public void hiddenNamesFollowTheSetting() {
    ClientRuntime r = runtime();
    try {
      HudModule hud = (HudModule) r.modules().require("hud").module();
      OverlaySettings overlay = hud.overlay;
      UUID id = UUID.randomUUID();
      assertFalse(overlay.hidden(id, "Alice"));

      r.modules().setSetting("hud", overlay.hidden, "alice, Bob");
      assertTrue(overlay.hidden(id, "ALICE"));
      assertTrue(overlay.hidden(id, "bob"));
      assertFalse(overlay.hidden(id, "Carol"));

      overlay.hide(hud, "Carol");
      assertTrue(overlay.hidden(id, "carol"));
      assertTrue(overlay.hidden(id, "Alice"));

      r.modules().setSetting("hud", overlay.hidden, "");
      assertFalse(overlay.hidden(id, "alice"));
      assertFalse(overlay.hidden(id, "carol"));

      overlay.removed.add(id);
      assertTrue(overlay.hidden(id, "Anyone"));
      assertFalse(overlay.hidden(UUID.randomUUID(), "Anyone"));
    } finally {
      r.stop();
    }
  }

  @Test
  public void optionsAreReusedUntilAnInputChanges() throws Exception {
    ClientRuntime r = runtime();
    try {
      HudModule hud = (HudModule) r.modules().require("hud").module();
      OverlaySettings overlay = hud.overlay;
      // Absolute paths keep path resolution independent of the game directory.
      for (Setting<String> key : overlay.keys.values())
        r.modules().setSetting("hud", key, folder.newFile().getAbsolutePath());

      OverlayLookup.Options first = overlay.options();
      assertSame(first, overlay.options());
      assertEquals(overlay.providers.get(), first.providers);
      assertEquals(overlay.keys.keySet(), first.keys.keySet());

      boolean sessions = !overlay.sessions.get();
      r.modules().setSetting("hud", overlay.sessions, sessions);
      OverlayLookup.Options second = overlay.options();
      assertNotSame(first, second);
      assertEquals(sessions, second.sessions);
      assertSame(second, overlay.options());

      r.modules()
          .setSetting("hud", overlay.providers, EnumSet.of(OverlayLookup.Provider.HYPIXEL));
      OverlayLookup.Options third = overlay.options();
      assertNotSame(second, third);
      assertEquals(EnumSet.of(OverlayLookup.Provider.HYPIXEL), third.providers);

      Path replacement = folder.newFile().toPath().toAbsolutePath();
      r.modules().setSetting("hud", hud.hypixelKeyFile, replacement.toString());
      OverlayLookup.Options fourth = overlay.options();
      assertNotSame(third, fourth);
      assertEquals(replacement, fourth.keys.get(OverlayLookup.Provider.HYPIXEL));

      r.modules().setSetting("hud", hud.hypixelKeyFile, "");
      assertFalse(overlay.options().keys.containsKey(OverlayLookup.Provider.HYPIXEL));
      assertSame(overlay.options(), overlay.options());
    } finally {
      r.stop();
    }
  }
}
