package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;

import org.junit.Test;

public class SwingPredictionTest {
  @Test
  public void learnsWrapsAndProjectsWithoutWaitingForAnAttackDelay() {
    SwingPrediction model = new SwingPrediction();
    for (int tick = 1; tick <= 12; tick++) model.update((tick % 6) / 6f, 5, 1, 1, 0);
    assertEquals(1, model.samples());
    for (int tick = 13; tick <= 16; tick++) assertFalse(model.update(0, 5, 1, 1, 0));
    assertTrue(model.update(0, 5, 1, 1, 0));
    assertTrue(model.update(0, 5, 1, 1, 0));
    assertTrue(model.update(0, 5, 1, 1, 0));
    assertFalse(model.update(0, 5, 1, 1, 0));
  }

  @Test
  public void capsHistoryAndResetsForTargetChanges() {
    SwingPrediction model = new SwingPrediction();
    for (int tick = 0; tick < 120; tick++) model.update((tick % 6) / 6f, 10, 3, 3, 1.5);
    assertEquals(10, model.samples());
    model.update(0, 2, 3, 3, 1.5);
    assertEquals(2, model.samples());
    model.reset();
    assertEquals(0, model.samples());
    assertTrue(model.update(0, 5, 3, 3, 1.5));
  }

  @Test
  public void longIdleAndSingleWrapDoNotInventSamples() {
    SwingPrediction model = new SwingPrediction();
    for (int i = 0; i < 1000; i++) assertTrue(model.update(.9f, 5, 0, 0, 0));
    assertTrue(model.update(0, 5, 0, 0, 0));
    for (int i = 0; i < 50; i++) model.update(.9f, 5, 0, 0, 0);
    model.update(0, 5, 0, 0, 0);
    assertEquals(0, model.samples());
  }
}
