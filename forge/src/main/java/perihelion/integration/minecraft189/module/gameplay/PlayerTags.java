package perihelion.integration.minecraft189.module.gameplay;

import perihelion.core.lifecycle.ClientRuntime;

/** The player board edits the same friend list as the native Friends module. */
public final class PlayerTags {
  private PlayerTags() {}

  public static boolean whitelisted(ClientRuntime runtime, String name) {
    return friends(runtime).friend(name);
  }

  public static void toggleWhitelist(ClientRuntime runtime, String name) {
    friends(runtime).toggle(name);
  }

  private static TargetingModules.Friends friends(ClientRuntime runtime) {
    return (TargetingModules.Friends) runtime.modules().require("friends").module();
  }
}
