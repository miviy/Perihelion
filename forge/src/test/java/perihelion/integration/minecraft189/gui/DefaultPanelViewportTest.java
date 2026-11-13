package perihelion.integration.minecraft189.gui;

import static org.junit.Assert.*;

import org.junit.Test;
import perihelion.core.module.ModuleCategory;
import perihelion.integration.minecraft189.gui.panel.PanelLayout;

public class DefaultPanelViewportTest {
  @Test
  public void expandedSettingsGrowPastDefaultBudgetUntilTheSharedCap() {
    PanelLayout model = new PanelLayout();
    model.reset(854, 480, 30);
    PanelLayout.Panel panel = model.find(ModuleCategory.COMBAT);
    panel.y = 40;
    panel.limitBody(40, 480);
    assertEquals(40, panel.bodyHeight(480, 100, false, false), .001);
    assertEquals(100, panel.bodyHeight(480, 100, false, true), .001);
    assertEquals(480 * .64f, panel.bodyHeight(480, 900, false, true), .001);
    panel.y = 390;
    assertEquals(480 - 390 - PanelLayout.HEADER - 6, panel.bodyHeight(480, 900, false, true), .001);
    panel.scroll(900, 900, panel.bodyHeight(480, 900, false, true));
    assertEquals(900 - (480 - 390 - PanelLayout.HEADER - 6), panel.scroll, .001);
    panel.collapsed = true;
    assertEquals(0, panel.bodyHeight(480, 900, false, true), 0);
  }

  @Test
  public void boundedDefaultRowsSurvivePersistenceAndScaleWithViewport() {
    PanelLayout model = new PanelLayout();
    model.reset(427, 240, 30);
    PanelLayout.Panel panel = model.find(ModuleCategory.COMBAT);
    panel.limitBody(40, 240);
    assertEquals(40, panel.bodyHeight(240, 200, false), .001);
    PanelLayout restored = new PanelLayout();
    restored.load(model.encode(), 427, 240, 30);
    assertEquals(40, restored.find(ModuleCategory.COMBAT).bodyHeight(240, 200, false), .001);
    assertEquals(80, restored.find(ModuleCategory.COMBAT).bodyHeight(480, 200, false), .001);
    restored.find(ModuleCategory.COMBAT).collapsed = true;
    assertEquals(0, restored.find(ModuleCategory.COMBAT).bodyHeight(240, 200, false), 0);
  }

  @Test
  public void movingAPanelRestoresItsFreeFloatingViewport() {
    PanelLayout model = new PanelLayout();
    model.reset(427, 240, 30);
    PanelLayout.Panel panel = model.find(ModuleCategory.COMBAT);
    panel.limitBody(40, 240);
    panel.x += 5;
    assertTrue(panel.bodyHeight(240, 200, false) > 40);
    assertFalse(model.encode().contains("body-fraction"));
    PanelLayout restored = new PanelLayout();
    restored.load(model.encode(), 427, 240, 30);
    assertTrue(restored.find(ModuleCategory.COMBAT).bodyHeight(240, 200, false) > 40);
  }
}
