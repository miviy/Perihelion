package perihelion.integration.minecraft189.module.gameplay;

import java.util.UUID;

/** Client-thread entry point for starting the native detached camera beside a loaded player. */
public interface PlayerSpectator {
  boolean spectate(UUID player);

  UUID spectating();
}
