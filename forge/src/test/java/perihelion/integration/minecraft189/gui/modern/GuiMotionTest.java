package perihelion.integration.minecraft189.gui.modern;

import static org.junit.Assert.*;

import org.junit.Test;

public class GuiMotionTest {
  private float advanceHeight(int frames, float seconds) {
    float height = 0;
    for (int i = 0; i < frames; i++)
      height = GuiMotion.height(height, 100, GuiMotion.weight(seconds / frames, 18));
    return height;
  }

  @Test
  public void equalElapsedTimeKeepsRowsAlignedAcrossRefreshRates() {
    assertEquals(advanceHeight(12, .2f), advanceHeight(24, .2f), .0001f);
    assertEquals(advanceHeight(24, .2f), advanceHeight(48, .2f), .0001f);
  }

  @Test
  public void aLongFrameCannotTeleportAnOpeningPanel() {
    float normal = GuiMotion.weight(1 / 60f, 18);
    float stalled = GuiMotion.weight(3, 18);
    assertTrue(stalled > normal);
    assertTrue(stalled < .65f);
    assertEquals(0, GuiMotion.weight(0, 18), 0);
    assertEquals(0, GuiMotion.weight(-1, 18), 0);
  }

  @Test
  public void reversingAnExpansionNeverOvershootsOrLeavesAnInvisibleHeight() {
    float weight = GuiMotion.weight(1 / 60f, 18), height = 0;
    for (int i = 0; i < 6; i++) height = GuiMotion.height(height, 200, weight);
    assertTrue(height > 0 && height < 200);
    for (int i = 0; i < 120; i++) {
      float next = GuiMotion.height(height, 0, weight);
      assertTrue(next >= 0 && next <= height);
      height = next;
    }
    assertEquals(0, height, 0);
  }

  @Test
  public void openingAndToggleValuesSettleSoIdleFramesStopChanging() {
    float weight = GuiMotion.weight(1 / 120f, 24), opening = 0;
    for (int i = 0; i < 240; i++) opening = GuiMotion.approach(opening, 1, weight);
    assertEquals(1, opening, 0);
    for (int i = 0; i < 240; i++) opening = GuiMotion.approach(opening, 0, weight);
    assertEquals(0, opening, 0);
  }

  @Test
  public void changingContentHeightTracksTheNewTargetWithoutCrossingIt() {
    float weight = GuiMotion.weight(1 / 60f, 18), height = 80;
    for (int i = 0; i < 120; i++) {
      float next = GuiMotion.height(height, 17, weight);
      assertTrue(next >= 17 && next <= height);
      height = next;
    }
    assertEquals(17, height, 0);
  }
}
