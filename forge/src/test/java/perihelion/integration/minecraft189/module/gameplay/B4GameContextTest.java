package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class B4GameContextTest {
  @Test public void hypixelAndTitleChecksKeepPregameSeparateFromLiveRows() {
    List<String> lines = new ArrayList<>(Arrays.asList("\u00a7eBED WARS", "date", "", "", "", "R Red: \u2713 YOU"));
    assertEquals(B4GameContext.State.NOT_HYPIXEL, B4GameContext.state(false, lines));
    assertEquals(B4GameContext.State.BEDWARS_GAME_BED, B4GameContext.state(true, lines));
    lines.set(5, "R Red: 0 YOU");
    assertEquals(B4GameContext.State.BEDWARS_GAME_NO_BED, B4GameContext.state(true, lines));
    lines.set(5, "R Red: 0");
    lines.add(""); lines.add(""); lines.add("Mode: Doubles");
    assertEquals(B4GameContext.State.BEDWARS_PREGAME, B4GameContext.state(true, lines));
  }
  @Test public void lobbyAndPitRulesMatchTheDonorRowOrder() {
    assertEquals(B4GameContext.State.PIT, B4GameContext.state(true, Arrays.asList("THE HYPIXEL PIT", "  \u00a78L1")));
    assertEquals(B4GameContext.State.LOBBY, B4GameContext.state(true, Arrays.asList("BED WARS", "date  \u00a78L1")));
    assertEquals(B4GameContext.State.UNKNOWN, B4GameContext.state(true, Arrays.asList("BED WARS", "date", "R Red: \u2713 YOU")));
    assertEquals(B4GameContext.State.UNKNOWN, B4GameContext.state(true, null));
  }
}
