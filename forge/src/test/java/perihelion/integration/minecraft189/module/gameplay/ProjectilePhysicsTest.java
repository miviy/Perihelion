package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;

import org.junit.Test;

public class ProjectilePhysicsTest {
  @Test
  public void solverAgreesWithDiscreteVanillaFlightAndMovingTargets() {
    for (double charge : new double[] {10, 20})
      for (double distance : new double[] {5, 15, 35, 60}) {
        double speed = ProjectilePhysics.bowSpeed(charge);
        ProjectilePhysics.Shot shot = ProjectilePhysics.solve(distance, 1, 3, .1, .02, -.05, speed);
        if (shot == null) {
          assertTrue(distance > 15);
          continue;
        }
        double x = 0, y = 0, z = 0, vx = shot.x, vy = shot.y, vz = shot.z;
        for (double remaining = shot.ticks; remaining > 0; remaining -= 1) {
          double dt = Math.min(1, remaining);
          x += vx * dt;
          y += vy * dt;
          z += vz * dt;
          vx *= .99;
          vy = vy * .99 - .05;
          vz *= .99;
        }
        assertEquals(distance + .1 * shot.ticks, x, 1e-6);
        assertEquals(1 + .02 * shot.ticks, y, 1e-6);
        assertEquals(3 - .05 * shot.ticks, z, 1e-6);
        assertEquals(speed, Math.sqrt(shot.x * shot.x + shot.y * shot.y + shot.z * shot.z), 1e-4);
      }
  }

  @Test
  public void unreachableInvalidAndUnderchargedShotsAreRejected() {
    assertNull(ProjectilePhysics.solve(500, 100, 0, 0, 0, 0, 3));
    assertNull(ProjectilePhysics.solve(10, 0, 0, 0, 0, 0, 0));
    assertNull(ProjectilePhysics.solve(Double.NaN, 0, 0, 0, 0, 0, 3));
  }

  @Test
  public void weakerDrawRequiresMoreFlightTime() {
    ProjectilePhysics.Shot weak =
        ProjectilePhysics.solve(15, 0, 0, 0, 0, 0, ProjectilePhysics.bowSpeed(10));
    ProjectilePhysics.Shot full =
        ProjectilePhysics.solve(15, 0, 0, 0, 0, 0, ProjectilePhysics.bowSpeed(20));
    assertTrue(weak.ticks > full.ticks);
    assertEquals(3, ProjectilePhysics.bowSpeed(200), 0);
  }
}
