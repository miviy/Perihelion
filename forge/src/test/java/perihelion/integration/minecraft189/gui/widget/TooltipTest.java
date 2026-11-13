package perihelion.integration.minecraft189.gui.widget;

import static org.junit.Assert.*;

import org.junit.Test;

public class TooltipTest {
  @Test
  public void requiresTwoSecondsOfHovering() {
    Tooltip tooltip = new Tooltip();
    assertFalse(tooltip.ready("Description", 20, 30, 0));
    assertFalse(tooltip.ready("Description", 20, 30, 1_999_999_999L));
    assertTrue(tooltip.ready("Description", 20, 30, 2_000_000_000L));
  }

  @Test
  public void movingAcrossRowsOrChangingDescriptionsRestartsTheDelay() {
    Tooltip tooltip = new Tooltip();
    tooltip.ready("Description", 20, 30, 0);
    assertFalse(tooltip.ready("Description", 40, 30, 2_000_000_000L));
    assertTrue(tooltip.ready("Description", 40, 30, 4_000_000_000L));
    assertFalse(tooltip.ready("Another setting", 40, 30, 4_100_000_000L));
    assertTrue(tooltip.ready("Another setting", 40, 30, 6_100_000_000L));
  }

  @Test
  public void smallPointerJitterDoesNotRestartTheDelay() {
    Tooltip tooltip = new Tooltip();
    tooltip.ready("Description", 20, 30, 0);
    assertTrue(tooltip.ready("Description", 22, 32, 2_000_000_000L));
  }

  @Test
  public void leavingOrInteractingRequiresAFreshHover() {
    Tooltip tooltip = new Tooltip();
    tooltip.ready("Description", 20, 30, 0);
    assertFalse(tooltip.ready(null, 20, 30, 2_000_000_000L));
    assertFalse(tooltip.ready("Description", 20, 30, 2_100_000_000L));
    tooltip.clear();
    assertFalse(tooltip.ready("Description", 20, 30, 5_000_000_000L));
  }
}
