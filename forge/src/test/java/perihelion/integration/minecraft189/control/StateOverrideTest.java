package perihelion.integration.minecraft189.control;

import static org.junit.Assert.*;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class StateOverrideTest {
  private final StateOverride<Integer> override = new StateOverride<>();
  private final AtomicInteger actual = new AtomicInteger(2);

  private void set(Integer value) {
    override.apply(Optional.ofNullable(value), actual::get, actual::set);
  }

  @Test
  public void committedSelectionSurvivesReleaseAndBecomesAHigherOwnersBaseline() {
    set(4);
    override.commit(actual::get);
    set(6);
    set(null);
    assertEquals(4, actual.get());
    set(null);
    assertEquals(4, actual.get());
  }

  @Test
  public void commitCannotEraseAManualSelectionMadeAfterOurWrite() {
    set(4);
    actual.set(7);
    override.commit(actual::get);
    set(null);
    assertEquals(7, actual.get());
    set(6);
    set(null);
    assertEquals(7, actual.get());
  }

  @Test
  public void switchingWinnersKeepsOriginalBaselineUntilAllYield() {
    set(4);
    set(6);
    set(4);
    assertEquals(4, actual.get());
    set(null);
    assertEquals(2, actual.get());
    actual.set(1);
    set(null);
    assertEquals(1, actual.get());
  }

  @Test
  public void manualChangesBecomeTheBaselineOnNextApplication() {
    set(4);
    actual.set(8);
    set(6);
    assertEquals(6, actual.get());
    set(null);
    assertEquals(8, actual.get());
  }

  @Test
  public void releaseDoesNotOverwriteAnExternalWrite() {
    set(4);
    actual.set(7);
    set(null);
    assertEquals(7, actual.get());
    set(5);
    set(null);
    assertEquals(7, actual.get());
  }

  @Test
  public void idleDoesNotWriteAndEachSessionCapturesAFreshBaseline() {
    AtomicInteger writes = new AtomicInteger();
    override.apply(Optional.empty(), actual::get, value -> writes.incrementAndGet());
    assertEquals(0, writes.get());
    set(4);
    set(null);
    actual.set(9);
    set(3);
    set(null);
    assertEquals(9, actual.get());
  }
}
