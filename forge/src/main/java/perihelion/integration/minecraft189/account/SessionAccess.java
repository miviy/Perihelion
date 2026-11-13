package perihelion.integration.minecraft189.account;

import net.minecraft.util.Session;

/** Client-thread-only session boundary implemented by the Minecraft mixin. */
public interface SessionAccess {
  void perihelion$session(Session session);
}
