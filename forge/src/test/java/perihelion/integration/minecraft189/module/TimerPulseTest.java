package perihelion.integration.minecraft189.module;

import static org.junit.Assert.*;

import org.junit.Test;

public class TimerPulseTest {
  @Test
  public void pulsePreservesBothBoundariesAndTheResetUpdate() {
    TimerPulse pulse = new TimerPulse(0);
    assertEquals(2, pulse.speed(0, true, 0.5f, 2), 0);
    assertEquals(2, pulse.speed(99_999_999L, true, 0.5f, 2), 0);
    assertEquals(0.5, pulse.speed(100_000_000L, true, 0.5f, 2), 0);
    assertEquals(0.5, pulse.speed(200_000_000L, true, 0.5f, 2), 0);
    assertEquals(0.5, pulse.speed(201_000_000L, true, 0.5f, 2), 0);
    assertEquals(2, pulse.speed(202_000_000L, true, 0.5f, 2), 0);
  }

  @Test
  public void constantModeDoesNotRestartTheSourcesPulseClock() {
    TimerPulse pulse = new TimerPulse(0);
    assertEquals(3, pulse.speed(5_000_000_000L, false, 3, 2), 0);
    assertEquals(3, pulse.speed(5_050_000_000L, true, 3, 2), 0);
    assertEquals(2, pulse.speed(5_060_000_000L, true, 3, 2), 0);
  }

  @Test
  public void activationResumesCurrentOutputWithoutResettingPhase() {
    TimerPulse pulse = new TimerPulse(0);
    pulse.speed(90_000_000L, true, .5f, 2);
    pulse.resume(1);
    assertEquals(.5f, pulse.speed(150_000_000L, true, .5f, 2), 0);
    pulse.resume(1);
    assertEquals(1, pulse.speed(201_000_000L, true, .5f, 2), 0);
    assertEquals(2, pulse.speed(202_000_000L, true, .5f, 2), 0);
  }
}
