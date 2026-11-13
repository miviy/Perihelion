package perihelion.integration.minecraft189.control;

import static org.junit.Assert.*;

import org.junit.Test;

public class GroundTickHistoryTest {
  @Test
  public void distinguishesPreUpdateFromLaterStrafeAndMotion() {
    GroundTickHistory h = new GroundTickHistory();
    h.advance(false);
    assertEquals(0, h.beforeAir());
    assertEquals(1, h.air());
    h.advance(false);
    assertEquals(1, h.beforeAir());
    assertEquals(2, h.air());
  }

  @Test
  public void landingKeepsPreviousAirCountForPreAndResetsCurrentAir() {
    GroundTickHistory h = new GroundTickHistory();
    for (int i = 0; i < 22; i++) h.advance(false);
    h.advance(true);
    assertEquals(22, h.beforeAir());
    assertEquals(0, h.air());
    assertEquals(0, h.beforeGround());
    assertEquals(1, h.ground());
    h.advance(true);
    assertEquals(1, h.beforeGround());
    assertEquals(2, h.ground());
  }

  @Test
  public void takeoffKeepsTheCompletedGroundRunOnlyForPre() {
    GroundTickHistory h = new GroundTickHistory();
    for (int i = 0; i < 5; i++) h.advance(true);
    h.advance(false);
    assertEquals(5, h.beforeGround());
    assertEquals(0, h.ground());
    assertEquals(1, h.air());
  }

  @Test
  public void separatePlayersNeverShareTimingState() {
    GroundTickHistory first = new GroundTickHistory(), second = new GroundTickHistory();
    for (int i = 0; i < 59; i++) first.advance(false);
    assertEquals(0, second.air());
    second.advance(true);
    assertEquals(59, first.air());
    assertEquals(1, second.ground());
  }
}
