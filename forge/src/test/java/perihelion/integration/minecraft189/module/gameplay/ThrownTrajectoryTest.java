package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;

import org.junit.Test;

public class ThrownTrajectoryTest {
  @Test
  public void solvedFlightMatchesIndependentClosedFormForBothDragsAndTargetHeights() {
    for (double drag : new double[] {.99, .92}) {
      for (double distance : new double[] {3.1, 4, 5, 6}) {
        for (double dy : new double[] {-.8, 0, .8}) {
          double[] shot = ThrownTrajectory.solvePitch(distance, dy, drag, .03);
          assertNotNull(shot);
          double pitch = Math.toRadians(shot[0]);
          double travel = ProjectilePhysics.travelFactor(shot[1], drag);
          assertEquals(distance, Math.cos(pitch) * 1.5 * travel, .002);
          assertEquals(
              dy,
              -Math.sin(pitch) * 1.5 * travel - ProjectilePhysics.drop(shot[1], drag, .03),
              .002);
        }
      }
    }
  }

  @Test
  public void launchInterpolationPrecedesDragAndGravity() {
    double[] first = ThrownTrajectory.simulate(0, .75, .99, .03);
    assertEquals(0, first[0], 0);
    assertEquals(.5, first[1], 0);
    double[] second = ThrownTrajectory.simulate(0, 2.2425, .99, .03);
    assertEquals(-.015, second[0], 1.0E-12);
    assertEquals(1.5, second[1], 1.0E-12);
  }

  @Test
  public void lowArcSolutionActuallyCrossesTargetHeightForBothDrags() {
    for (double drag : new double[] {.99, .92}) {
      for (double distance : new double[] {3.01, 4, 5, 6}) {
        for (double dy : new double[] {-1, -.36, .2, 1}) {
          double[] solved = ThrownTrajectory.solvePitch(distance, dy, drag, .03);
          assertNotNull("reachable arc", solved);
          double[] reached = ThrownTrajectory.simulate(solved[0], distance, drag, .03);
          assertNotNull(reached);
          assertEquals(dy, reached[0], .002);
          assertEquals(solved[1], reached[1], .002);
        }
      }
    }
  }

  @Test
  public void rodHasMoreFlightTimeAndHigherArcThanThrownItemAtItsMaximumRange() {
    double[] thrown = ThrownTrajectory.solvePitch(6, 0, .99, .03);
    double[] rod = ThrownTrajectory.solvePitch(6, 0, .92, .03);
    assertNotNull(thrown);
    assertNotNull(rod);
    assertTrue(rod[1] > thrown[1]);
    assertTrue(rod[0] < thrown[0]);
    assertNull(ThrownTrajectory.solvePitch(1000, 0, .99, .03));
    assertNull(ThrownTrajectory.solvePitch(5, 1000, .92, .03));
  }
}
