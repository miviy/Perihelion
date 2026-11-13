package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import perihelion.core.diagnostics.ClientLog;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.module.BuiltinModules;

public class FriendsLookupTest {
  @Rule public TemporaryFolder folder = new TemporaryFolder();

  @Test
  @SuppressWarnings("unchecked")
  public void cachedNamesFollowSettingsChangesAndKeepCaseInsensitiveMembership() {
    ClientRuntime runtime =
        new ClientRuntime(
            folder.getRoot().toPath().resolve("client.json"),
            ClientLog.NONE,
            BuiltinModules::register);
    runtime.start();
    try {
      TargetingModules.Friends friends =
          (TargetingModules.Friends) runtime.modules().require("friends").module();
      Setting<String> names = (Setting<String>) friends.settings().get("names");
      runtime.modules().setSetting("friends", names, "Alice, BOB, ,alice");
      for (int i = 0; i < 100; i++) {
        assertTrue(friends.friend("aLiCe"));
        assertTrue(friends.friend("bob"));
        assertFalse(friends.friend("eve"));
      }
      runtime.modules().setSetting("friends", names, "Eve");
      assertFalse(friends.friend("Alice"));
      assertTrue(friends.friend("EVE"));
      runtime.modules().setSetting("friends", names, "");
      assertFalse(friends.friend("eve"));
    } finally {
      runtime.stop();
    }
  }
}
