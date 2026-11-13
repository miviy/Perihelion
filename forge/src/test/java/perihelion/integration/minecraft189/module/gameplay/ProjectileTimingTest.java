package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;

import org.junit.Test;

public class ProjectileTimingTest {
  @Test
  public void distanceDelayUsesExactDonorInclusiveBoundaries() {
    assertEquals(Long.MAX_VALUE, ProjectileTiming.delayFor(3));
    assertEquals(550, ProjectileTiming.delayFor(3.0001));
    assertEquals(550, ProjectileTiming.delayFor(5));
    assertEquals(450, ProjectileTiming.delayFor(5.0001));
    assertEquals(450, ProjectileTiming.delayFor(6));
    assertEquals(350, ProjectileTiming.delayFor(6.0001));
    assertEquals(350, ProjectileTiming.delayFor(7));
    assertEquals(250, ProjectileTiming.delayFor(7.0001));
  }

  @Test
  public void holdDurationIsTwoTicksForThrownItemsAndFourForRod() {
    assertEquals(2, ProjectileTiming.storeTicks(false));
    assertEquals(4, ProjectileTiming.storeTicks(true));
  }

  @Test
  public void rotationThresholdWrapsYawAndIncludesExactlyOneAndAHalfDegrees() {
    assertFalse(ProjectileTiming.needsRotation(-179, 1.5F, 179.5F, 0));
    assertTrue(ProjectileTiming.needsRotation(-178.99F, 0, 179.5F, 0));
    assertTrue(ProjectileTiming.needsRotation(0, 1.5001F, 0, 0));
    assertFalse(ProjectileTiming.needsRotation(360, 0, 0, 0));
  }
}
