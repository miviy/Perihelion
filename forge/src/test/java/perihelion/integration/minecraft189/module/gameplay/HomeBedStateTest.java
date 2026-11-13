package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;

import org.junit.Test;

public class HomeBedStateTest {
  private HomeBedState.Position p(int x, int y, int z) {
    return new HomeBedState.Position(x, y, z);
  }

  @Test public void delayedAcquisitionKeepsTheInitialSearchAnchor() {
    HomeBedState state = new HomeBedState();
    HomeBedState.Position spawn = p(0, 64, 0);
    state.anchor(spawn);
    state.anchor(p(100, 64, 100));
    assertSame(spawn, state.anchor());
    assertFalse(state.capture(p(100, 64, 100), p(100, 64, 101)));
    assertTrue(state.capture(p(2, 64, 2), p(2, 64, 3)));
  }

  @Test public void knownHomeSurvivesMovementAndCannotBeReplacedWithinTheMatch() {
    HomeBedState state = new HomeBedState();
    state.anchor(p(0, 64, 0));
    HomeBedState.Position home = p(2, 64, 2);
    assertTrue(state.capture(home, p(2, 64, 3)));
    long revision = state.revision();
    state.anchor(p(100, 64, 100));
    assertFalse(state.capture(p(8, 64, 8), p(8, 64, 9)));
    assertSame(home, state.foot());
    assertEquals(revision, state.revision());
  }

  @Test public void resetClearsHomeAndAnchorAndAllowsTheNextMatch() {
    HomeBedState state = new HomeBedState();
    state.anchor(p(0, 64, 0));
    state.capture(p(2, 64, 2), p(2, 64, 3));
    long revision = state.revision();
    state.reset();
    assertFalse(state.acquired());
    assertNull(state.anchor());
    assertNull(state.foot());
    assertTrue(state.revision() > revision);
    assertFalse(state.protects(p(2, 64, 2)));
    state.anchor(p(100, 64, 100));
    assertTrue(state.capture(p(101, 64, 100), p(101, 64, 101)));
  }

  @Test public void protectsBothHalvesAndNearbyCoverWithoutExcludingDistantBeds() {
    HomeBedState state = new HomeBedState();
    state.anchor(p(0, 64, 0));
    state.capture(p(2, 64, 2), p(2, 64, 3));
    assertTrue(state.protects(p(2, 64, 2)));
    assertTrue(state.protects(p(2, 64, 3)));
    assertTrue(state.protects(p(2, 65, 3)));
    assertFalse(state.protects(p(6, 64, 3)));
  }

  @Test public void acquisitionRequiresAnAnchorAndCoherentHorizontalHalves() {
    HomeBedState state = new HomeBedState();
    assertFalse(state.capture(p(2, 64, 2), p(2, 64, 3)));
    state.anchor(p(0, 64, 0));
    assertFalse(state.capture(p(2, 64, 2), null));
    assertFalse(state.capture(p(2, 64, 2), p(2, 65, 2)));
    assertFalse(state.capture(p(2, 64, 2), p(2, 64, 4)));
    assertTrue(state.capture(p(2, 64, 2), p(2, 64, 3)));
  }
}
