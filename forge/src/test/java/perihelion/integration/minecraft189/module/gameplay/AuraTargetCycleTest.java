package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class AuraTargetCycleTest {
  @Test
  public void switchUsesFiftyMillisecondTicksAndTracksCurrentIdAfterListChanges() {
    AuraTargetCycle cycle = new AuraTargetCycle();
    assertEquals(0, cycle.select(Arrays.asList(2, 5, 9), -1, 100, 2));
    assertEquals(0, cycle.select(Arrays.asList(2, 5, 9), 2, 199, 2));
    assertEquals(1, cycle.select(Arrays.asList(2, 5, 9), 2, 200, 2));
    assertEquals(2, cycle.select(Arrays.asList(1, 2, 5, 9), 5, 250, 2));
    assertEquals(3, cycle.select(Arrays.asList(1, 2, 5, 9), 5, 300, 2));
  }

  @Test
  public void zeroDelaySwitchesEachPassButReacquisitionStartsItsTimerAgain() {
    AuraTargetCycle cycle = new AuraTargetCycle();
    assertEquals(0, cycle.select(Arrays.asList(2, 5), -1, 100, 0));
    assertEquals(1, cycle.select(Arrays.asList(2, 5), 2, 100, 0));
    assertEquals(-1, cycle.select(Collections.emptyList(), 5, 100, 0));
    assertEquals(0, cycle.select(Arrays.asList(2, 5), -1, 200, 2));
    assertEquals(0, cycle.select(Arrays.asList(2, 5), 2, 299, 2));
    assertEquals(1, cycle.select(Arrays.asList(2, 5), 2, 300, 2));
  }
}
