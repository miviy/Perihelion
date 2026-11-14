package perihelion.integration.minecraft189.gui;

import static org.junit.Assert.*;

import org.junit.Test;
import perihelion.core.module.ModuleCategory;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.gui.panel.PanelLayout;
import perihelion.integration.minecraft189.gui.panel.UtilityLayout;

public class GuiLogicTest {
  @Test
  public void editorTransitionsNeverRescaleMenuTabsOrOtherEditors() {
    UtilityLayout menu = new UtilityLayout();
    menu.resize(407, 229);
    menu.box("hud", 257, 7);
    menu.box("configs", 359, 7);
    for (int visit = 0; visit < 20; visit++) {
      UtilityLayout config = new UtilityLayout();
      config.load(menu.encode());
      config.resizeEditor("configs-editor", 570, 320);
      config.box("configs-editor", 38, 12);
      UtilityLayout hud = new UtilityLayout();
      hud.load(config.encode());
      hud.resizeEditor("hud-editor", 427, 240);
      hud.box("hud-editor", 140, 7);
      menu.load(hud.encode());
      menu.resize(407, 229);
      assertEquals(257, menu.box("hud", 0, 0).x, 0);
      assertEquals(359, menu.box("configs", 0, 0).x, 0);
      assertEquals(7, menu.box("hud", 0, 0).y, 0);
      assertEquals(38, menu.box("configs-editor", 0, 0).x, 0);
      assertEquals(140, menu.box("hud-editor", 0, 0).x, 0);
    }
    menu.resizeEditor("configs-editor", 1140, 640);
    assertEquals(76, menu.box("configs-editor", 0, 0).x, .001);
    assertEquals(257, menu.box("hud", 0, 0).x, 0);
    assertEquals(140, menu.box("hud-editor", 0, 0).x, 0);
  }

  @Test
  public void repeatedDrawsKeepDraggedUtilityCoordinatesExact() {
    UtilityLayout layout = new UtilityLayout();
    float width = 398 / 1.05f, height = 265 / 1.05f;
    layout.resize(width, height);
    UtilityLayout.Box window = layout.box("config-window", 38, 12);
    window.collapsed = true;
    for (int frame = 0; frame < 1000; frame++) layout.resize(width, height);
    assertEquals(38, window.x, 0);
    assertEquals(12, window.y, 0);
    assertTrue(window.collapsed);
    layout.resize(width * 2, height * 2);
    assertEquals(76, window.x, .001f);
    assertEquals(24, window.y, .001f);
  }

  @Test
  public void utilityGeometryPersistsAndRecoversOnResize() {
    UtilityLayout layout = new UtilityLayout();
    UtilityLayout.Box hud = layout.box("hud", 15, 18);
    hud.collapsed = true;
    UtilityLayout.Box configs = layout.box("configs", 290, 230);
    UtilityLayout restored = new UtilityLayout();
    restored.load(layout.encode());
    assertTrue(restored.box("hud", 0, 0).collapsed);
    assertEquals(15, restored.box("hud", 0, 0).x, 0);
    configs = restored.box("configs", 0, 0);
    configs.recover(240, 160, 40, 14);
    assertEquals(198, configs.x, 0);
    assertEquals(144, configs.y, 0);
    restored.load("broken");
    assertEquals(8, restored.box("hud", 8, 7).x, 0);
    restored.load("{\"hud\":{\"x\":1e999,\"y\":0}}");
    assertEquals(8, restored.box("hud", 8, 7).x, 0);
  }

  @Test
  public void defaultUtilityTabsStayApartAtFractionalScaleAndSavedTabsResize() {
    UtilityLayout layout = new UtilityLayout();
    layout.resize(427, 240);
    UtilityLayout.Box hud = layout.box("hud", 277, 7), configs = layout.box("configs", 379, 7);
    layout.resize(427 / 1.05f, 240 / 1.05f);
    float width = 427 / 1.05f;
    assertEquals(width - 150, hud.x, .01f);
    assertEquals(width - 48, configs.x, .01f);
    assertEquals(7, hud.y, 0);
    assertEquals(7, configs.y, 0);
    assertTrue(hud.x + 40 <= width - 107);
    assertTrue(width - 107 + 54 <= configs.x);
    hud.x = 24;
    hud.y = 70;
    UtilityLayout saved = new UtilityLayout();
    saved.load(layout.encode());
    saved.resize(854, 480);
    assertEquals(24 * 854 / width, saved.box("hud", 0, 0).x, .01f);
    assertEquals(70 * 480 / (240 / 1.05f), saved.box("hud", 0, 0).y, .01f);
    assertEquals(7, saved.box("configs", 0, 0).y, 0);
  }

  @Test
  public void dockedTabsRepairSmallTopInsetDriftWhileDraggedTabsKeepTheirPosition() {
    UtilityLayout layout = new UtilityLayout();
    layout.resize(427, 240);
    UtilityLayout.Box hud = layout.box("hud", 277, 8.25f);
    UtilityLayout.Box configs = layout.box("configs", 379, 40);
    layout.resize(854, 480);
    assertEquals(7, hud.y, 0);
    assertEquals(80, configs.y, 0);
    assertEquals(854 - 150, hud.x, 0);
    assertEquals(854 - 48, configs.x, 0);
  }

  @Test
  public void hiddenSearchAllowsPanelsToUseTheTopOfTheViewport() {
    PanelLayout layout = new PanelLayout();
    layout.reset(427, 240, 2);
    PanelLayout.Panel panel = layout.find(ModuleCategory.RENDER);
    panel.y = 2;
    panel.recover(427, 240, 2);
    assertEquals(2, panel.y, 0);
    panel.recover(427, 240, 27);
    assertEquals(27, panel.y, 0);
  }

  @Test
  public void layoutRoundtripPreservesPositionsCollapseAndOrder() {
    PanelLayout layout = new PanelLayout();
    layout.reset(854, 480, 47);
    PanelLayout.Panel panel = layout.find(ModuleCategory.RENDER);
    panel.x = 180;
    panel.y = 80;
    panel.collapsed = true;
    layout.front(panel);
    PanelLayout restored = new PanelLayout();
    restored.load(layout.encode(), 854, 480, 47);
    PanelLayout.Panel result = restored.find(ModuleCategory.RENDER);
    assertEquals(180, result.x, 0);
    assertEquals(80, result.y, 0);
    assertTrue(result.collapsed);
    assertSame(result, restored.panels().get(restored.panels().size() - 1));
  }

  @Test
  public void disclosuresRoundtripIndependentlyFromValuesAndResetWithLayout() {
    PanelLayout layout = new PanelLayout();
    layout.reset(854, 480, 27);
    PanelLayout.Panel panel = layout.find(ModuleCategory.RENDER);
    panel.expandedRows.add("hud");
    panel.expandedRows.add("hud/section:Array list");
    panel.expandedRows.add("hud/section:Array list/section:Text");
    panel.expandedRows.add("hud/modules-font");
    PanelLayout restored = new PanelLayout();
    restored.load(layout.encode(), 854, 480, 27);
    assertEquals(panel.expandedRows, restored.find(ModuleCategory.RENDER).expandedRows);
    assertTrue(restored.find(ModuleCategory.COMBAT).expandedRows.isEmpty());
    restored.reset(854, 480, 27);
    assertTrue(restored.find(ModuleCategory.RENDER).expandedRows.isEmpty());
    restored.load(
        "[{\"category\":\"RENDER\",\"x\":20,\"y\":35,\"collapsed\":false,"
            + "\"expanded-rows\":[null,{},7,\"hud\"]}]",
        854,
        480,
        27);
    assertEquals(
        java.util.Collections.singleton("hud"), restored.find(ModuleCategory.RENDER).expandedRows);
    restored.load("[]", 854, 480, 27);
    assertTrue(restored.find(ModuleCategory.RENDER).expandedRows.isEmpty());
  }

  @Test
  public void corruptAndOffscreenLayoutsRecoverAndResetRestoresPanels() {
    PanelLayout layout = new PanelLayout();
    layout.load("broken", 320, 240, 27);
    assertEquals(ModuleCategory.values().length, layout.panels().size());
    PanelLayout.Panel p = layout.find(ModuleCategory.CLIENT);
    p.x = 9000;
    p.y = -500;
    p.collapsed = true;
    layout.load(layout.encode(), 320, 240, 27);
    p = layout.find(ModuleCategory.CLIENT);
    assertTrue(p.x >= 0 && p.x + PanelLayout.WIDTH <= 320);
    assertTrue(p.y >= 27 && p.y < 240);
    layout.reset(320, 240, 27);
    for (PanelLayout.Panel panel : layout.panels()) {
      assertFalse(panel.collapsed);
    }
  }

  @Test
  public void removedExploitPanelDoesNotDiscardOtherSavedPanelGeometry() {
    PanelLayout layout = new PanelLayout();
    layout.load(
        "[{\"category\":\"EXPLOITS\",\"x\":20,\"y\":35,\"collapsed\":false},"
            + "{\"category\":\"RENDER\",\"x\":180,\"y\":80,\"collapsed\":true}]",
        854,
        480,
        27);
    assertEquals(7, layout.panels().size());
    PanelLayout.Panel render = layout.find(ModuleCategory.RENDER);
    assertEquals(180, render.x, 0);
    assertEquals(80, render.y, 0);
    assertTrue(render.collapsed);
    assertFalse(layout.encode().contains("EXPLOITS"));
  }

  @Test
  public void scrollIsBoundedAndOnlyChangesItsOwnPanel() {
    PanelLayout layout = new PanelLayout();
    layout.reset(854, 480, 47);
    PanelLayout.Panel p = layout.find(ModuleCategory.CLIENT);
    p.scroll(999, 400, 100);
    assertEquals(300, p.scroll, 0);
    p.scroll(-999, 400, 100);
    assertEquals(0, p.scroll, 0);
    assertEquals(0, layout.find(ModuleCategory.RENDER).scroll, 0);
    p.collapsed = true;
    assertEquals(0, p.bodyHeight(480, 100, false), 0);
    assertTrue(p.bodyHeight(480, 100, true) > 0);
  }

  @Test
  public void sliderSnapsFromMinimumAndNeverExceedsAnOffGridMaximum() {
    Setting<Double> value = Setting.decimal("n", "N", "", 0.1, 0.1, 0.95, 0.2, () -> {});
    assertEquals(0.1, GuiValues.snap(value.metadata().numeric().get(), -2), 0);
    assertEquals(0.9, GuiValues.snap(value.metadata().numeric().get(), 2), 0);
    for (int i = 0; i <= 100; i++)
      value.validate(GuiValues.snap(value.metadata().numeric().get(), i / 100.0));
  }

  @Test
  public void visibleLabelsUseNormalCaseWithoutChangingStoredNames() {
    assertEquals("Click gui", GuiValues.readable("ClickGUI"));
    assertEquals("No fov", GuiValues.readable("NoFOV"));
    assertEquals("Enabled modules", GuiValues.readable("Enabled modules"));
    assertEquals("Sans serif", GuiValues.readable("SANS_SERIF"));
    assertEquals("Through walls", GuiValues.readable("THROUGH_WALLS"));
    assertEquals("Anti fireball", GuiValues.readable("AntiFireball"));
    assertEquals("Ncp", GuiValues.readable("NCP"));
  }

  @Test
  public void searchAcceptsMultipleTermsAndNameOrIdSeparators() {
    String search = "Jump Reset jumpreset Nearby Player only-sprinting Intave Latest";
    assertTrue(GuiValues.matches(search, "jumpreset"));
    assertTrue(GuiValues.matches(search, "jump reset nearby"));
    assertTrue(GuiValues.matches(search, "ONLY-SPRINTING"));
    assertTrue(GuiValues.matches(search, "intave_latest"));
    assertFalse(GuiValues.matches(search, "jump reset missing"));
    assertTrue(GuiValues.matches(search, " "));
  }
}
