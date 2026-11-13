package perihelion.integration.minecraft189.gui;

import static org.junit.Assert.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import perihelion.core.diagnostics.ClientLog;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.setting.ColorValue;

public class GuiAppearanceTest {
  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private ClientRuntime runtime() {
    ClientRuntime runtime =
        new ClientRuntime(
            folder.getRoot().toPath().resolve("client.json"),
            ClientLog.NONE,
            r -> r.modules().register(new ClickGuiModule(r.tasks()::checkThread, () -> {})));
    runtime.start();
    return runtime;
  }

  @Test
  public void styleSwitchPreservesAdjustFontColorsAndLayouts() {
    ClientRuntime r = runtime();
    try {
      ClickGuiModule s = (ClickGuiModule) r.modules().require("clickgui").module();
      r.modules().setSetting("clickgui", s.visuals, ClickGuiModule.Visuals.OLD);
      assertEquals(ClickGuiModule.ClientStyle.ADJUST, GuiTheme.style(r));
      assertTrue(s.clientStyle.isVisible());
      r.modules().setSetting("clickgui", s.font, ClickGuiModule.Font.MONO);
      r.modules().setSetting("clickgui", s.accent, ColorValue.rgb(90, 160, 210));
      r.modules().setSetting("clickgui", s.layout, "[{\"category\":\"MISC\"}]");
      String layout = s.layout.get(), utilities = s.utilityLayout.get();
      r.modules().setSetting("clickgui", s.clientStyle, ClickGuiModule.ClientStyle.RAVEN);
      assertSame(s.ravenLayout, s.panelLayout());
      assertEquals("[]", s.panelLayout().get());
      assertEquals(ClickGuiModule.ClientStyle.RAVEN, GuiTheme.style(r));
      assertEquals(ClickGuiModule.Font.MINECRAFT, GuiTheme.font(r));
      assertEquals(ClickGuiModule.Font.MONO, s.font.get());
      assertFalse(s.font.isVisible());
      assertEquals(ColorValue.rgb(90, 160, 210), s.accent.get());
      assertEquals(layout, s.layout.get());
      assertEquals(utilities, s.utilityLayout.get());
      r.modules().setSetting("clickgui", s.clientStyle, ClickGuiModule.ClientStyle.ADJUST);
      assertSame(s.layout, s.panelLayout());
      assertEquals(layout, s.panelLayout().get());
      assertEquals(ClickGuiModule.Font.MONO, GuiTheme.font(r));
      assertTrue(s.font.isVisible());
    } finally {
      r.stop();
    }
  }

  @Test
  public void ravenStylePersistsWithoutOverwritingTheAdjustFont() {
    ClientRuntime first = runtime();
    ClickGuiModule s = (ClickGuiModule) first.modules().require("clickgui").module();
    first.modules().setSetting("clickgui", s.visuals, ClickGuiModule.Visuals.OLD);
    first.modules().setSetting("clickgui", s.font, ClickGuiModule.Font.SERIF);
    first.modules().setSetting("clickgui", s.ravenLayout, "[{\"category\":\"COMBAT\"}]");
    first.modules().setSetting("clickgui", s.clientStyle, ClickGuiModule.ClientStyle.RAVEN);
    first.stop();
    ClientRuntime second = runtime();
    try {
      ClickGuiModule restored = (ClickGuiModule) second.modules().require("clickgui").module();
      assertEquals(ClickGuiModule.ClientStyle.RAVEN, restored.clientStyle.get());
      assertEquals(ClickGuiModule.Font.SERIF, restored.font.get());
      assertEquals("[{\"category\":\"COMBAT\"}]", restored.panelLayout().get());
      assertEquals(ClickGuiModule.Font.MINECRAFT, GuiTheme.font(second));
      second
          .modules()
          .setSetting("clickgui", restored.clientStyle, ClickGuiModule.ClientStyle.ADJUST);
      assertEquals(ClickGuiModule.Font.SERIF, GuiTheme.font(second));
      assertEquals("[]", restored.panelLayout().get());
    } finally {
      second.stop();
    }
  }

  @Test
  public void utilityWindowsHaveIndependentPersistentStyleLayouts() {
    ClientRuntime first = runtime();
    ClickGuiModule s = (ClickGuiModule) first.modules().require("clickgui").module();
    first.modules().setSetting("clickgui", s.visuals, ClickGuiModule.Visuals.OLD);
    first.modules().setSetting("clickgui", s.utilityLayout, "{\"adjust\":true}");
    first.modules().setSetting("clickgui", s.clientStyle, ClickGuiModule.ClientStyle.RAVEN);
    assertSame(s.ravenUtilityLayout, s.toolsLayout());
    first.modules().setSetting("clickgui", s.toolsLayout(), "{\"raven\":true}");
    first.stop();
    ClientRuntime restored = runtime();
    try {
      ClickGuiModule next = (ClickGuiModule) restored.modules().require("clickgui").module();
      assertEquals("{\"raven\":true}", next.toolsLayout().get());
      restored
          .modules()
          .setSetting("clickgui", next.clientStyle, ClickGuiModule.ClientStyle.ADJUST);
      assertEquals("{\"adjust\":true}", next.toolsLayout().get());
      assertEquals("{\"raven\":true}", next.ravenUtilityLayout.get());
    } finally {
      restored.stop();
    }
  }

  @Test
  public void visualGenerationPersistsAndRestoresThePreviousStyleWithoutChangingItsSettings() {
    ClientRuntime first = runtime();
    ClickGuiModule s = (ClickGuiModule) first.modules().require("clickgui").module();
    assertEquals(ClickGuiModule.Visuals.NEW, s.visuals.get());
    first.modules().setSetting("clickgui", s.font, ClickGuiModule.Font.MONO);
    first.modules().setSetting("clickgui", s.clientStyle, ClickGuiModule.ClientStyle.RAVEN);
    first.modules().setSetting("clickgui", s.ravenLayout, "[{\"category\":\"MISC\"}]");
    first.modules().setSetting("clickgui", s.ravenUtilityLayout, "{\"old\":true}");
    first.modules().setSetting("clickgui", s.modernUtilityLayout, "{\"new\":true}");
    assertEquals(ClickGuiModule.Font.ROBOTO, GuiTheme.font(first));
    assertFalse(s.font.isVisible());
    assertSame(s.modernUtilityLayout, s.toolsLayout());
    first.modules().setSetting("clickgui", s.visuals, ClickGuiModule.Visuals.OLD);
    assertEquals(ClickGuiModule.Font.MINECRAFT, GuiTheme.font(first));
    first.stop();
    ClientRuntime second = runtime();
    try {
      ClickGuiModule restored = (ClickGuiModule) second.modules().require("clickgui").module();
      assertEquals(ClickGuiModule.Visuals.OLD, restored.visuals.get());
      assertEquals(ClickGuiModule.ClientStyle.RAVEN, GuiTheme.style(second));
      assertEquals("{\"old\":true}", restored.toolsLayout().get());
      second.modules().setSetting("clickgui", restored.visuals, ClickGuiModule.Visuals.NEW);
      assertEquals("{\"new\":true}", restored.toolsLayout().get());
      assertEquals("[{\"category\":\"MISC\"}]", restored.ravenLayout.get());
      assertEquals(ClickGuiModule.Font.ROBOTO, GuiTheme.font(second));
      assertFalse(restored.clientStyle.isVisible());
    } finally {
      second.stop();
    }
    ClientRuntime third = runtime();
    try {
      ClickGuiModule restored = (ClickGuiModule) third.modules().require("clickgui").module();
      assertEquals(ClickGuiModule.Visuals.NEW, restored.visuals.get());
      assertEquals(ClickGuiModule.ClientStyle.RAVEN, restored.clientStyle.get());
      assertEquals("{\"new\":true}", restored.toolsLayout().get());
    } finally {
      third.stop();
    }
  }

  @Test
  public void newFontAndBoldPersistIndependentlyOfOldAppearance() {
    ClientRuntime first = runtime();
    ClickGuiModule settings = (ClickGuiModule) first.modules().require("clickgui").module();
    first.modules().setSetting("clickgui", settings.font, ClickGuiModule.Font.SERIF);
    for (perihelion.core.setting.Setting<?> selector :
        new perihelion.core.setting.Setting<?>[] {settings.font, settings.modernFont})
      assertNotNull(
          perihelion.integration.minecraft189.gui.widget.SettingControl.create(
              first.modules(),
              "clickgui",
              selector,
              (label, initial, left, top, width, submit) -> {}));

    for (ClickGuiModule.Font choice : ClickGuiModule.Font.values()) {
      first.modules().setSetting("clickgui", settings.modernFont, choice);
      assertEquals(choice, GuiTheme.font(first));
      first.modules().setSetting("clickgui", settings.bold, true);
      assertTrue(GuiTheme.bold(settings));
      first.modules().setSetting("clickgui", settings.bold, false);
      assertFalse(GuiTheme.bold(settings));
    }
    first.modules().setSetting("clickgui", settings.modernFont, ClickGuiModule.Font.NERD_MONO);
    first.modules().setSetting("clickgui", settings.bold, true);
    assertEquals("Bold", settings.bold.displayName());
    assertTrue(settings.modernFont.isVisible());
    assertTrue(settings.bold.isVisible());
    assertEquals(6, settings.settings().values().stream().filter(s -> s.isVisible()).count());
    first.stop();
    ClientRuntime second = runtime();
    try {
      ClickGuiModule restored = (ClickGuiModule) second.modules().require("clickgui").module();
      assertEquals(ClickGuiModule.Font.NERD_MONO, GuiTheme.font(second));
      assertTrue(GuiTheme.bold(restored));
      second.modules().setSetting("clickgui", restored.visuals, ClickGuiModule.Visuals.OLD);
      assertEquals(ClickGuiModule.Font.SERIF, GuiTheme.font(second));
      assertFalse(GuiTheme.bold(restored));
      assertFalse(restored.modernFont.isVisible());
      assertFalse(restored.bold.isVisible());
    } finally {
      second.stop();
    }
  }

  @Test
  public void cornerRadiusPersistsAndIsOnlyVisibleInNewVisuals() {
    ClientRuntime first = runtime();
    ClickGuiModule s = (ClickGuiModule) first.modules().require("clickgui").module();
    assertEquals(5, s.cornerRadius.get(), 0);
    first.modules().setSetting("clickgui", s.cornerRadius, 10.0);
    assertEquals(0, s.cornerRadius.metadata().numeric().get().minimum(), 0);
    assertEquals(10, s.cornerRadius.metadata().numeric().get().maximum(), 0);
    first.stop();
    ClientRuntime second = runtime();
    try {
      ClickGuiModule restored = (ClickGuiModule) second.modules().require("clickgui").module();
      assertEquals(10, restored.cornerRadius.get(), 0);
      assertTrue(restored.cornerRadius.isVisible());
      second.modules().setSetting("clickgui", restored.visuals, ClickGuiModule.Visuals.OLD);
      assertFalse(restored.cornerRadius.isVisible());
      second.modules().setSetting("clickgui", restored.visuals, ClickGuiModule.Visuals.NEW);
      second.modules().setSetting("clickgui", restored.cornerRadius, 0.0);
      assertEquals(0, restored.cornerRadius.get(), 0);
    } finally {
      second.stop();
    }
  }

  @Test
  public void enabledRowsUseALightThemeTintWithoutDependingOnCustomTextStyle() {
    perihelion.integration.minecraft189.gui.widget.UiCanvas canvas =
        new perihelion.integration.minecraft189.gui.widget.UiCanvas();
    canvas.accent = 0xff427d9e;
    assertEquals(0x29427d9e, GuiTheme.enabledRow(canvas));
    canvas.accent = 0xffb87b52;
    assertEquals(0x29b87b52, GuiTheme.enabledRow(canvas));
  }

  @Test
  public void defaultThemeKeepsCustomColorsAndBoldOptIn() {
    ClientRuntime r = runtime();
    try {
      ClickGuiModule settings = (ClickGuiModule) r.modules().require("clickgui").module();
      r.modules().setSetting("clickgui", settings.visuals, ClickGuiModule.Visuals.OLD);
      assertEquals(perihelion.core.module.ModuleCategory.MISC, settings.info().category());
      assertFalse(settings.customTextStyle.get());
      assertFalse(GuiTheme.bold(settings));
      assertFalse(settings.sectionText.isVisible());
      assertFalse(settings.textWeight.isVisible());
      r.modules().setSetting("clickgui", settings.textWeight, ClickGuiModule.TextWeight.BOLD);
      assertFalse(GuiTheme.bold(settings));
      r.modules().setSetting("clickgui", settings.customTextStyle, true);
      assertTrue(GuiTheme.bold(settings));
      assertTrue(settings.sectionText.isVisible());
    } finally {
      r.stop();
    }
  }

  @Test
  public void newVisualsKeepAFixedThemeAndAnIndependentDropdownLayout() {
    ClientRuntime r = runtime();
    try {
      ClickGuiModule s = (ClickGuiModule) r.modules().require("clickgui").module();
      r.modules().setSetting("clickgui", s.customTextStyle, true);
      r.modules().setSetting("clickgui", s.font, ClickGuiModule.Font.SERIF);
      r.modules().setSetting("clickgui", s.textWeight, ClickGuiModule.TextWeight.BOLD);
      r.modules().setSetting("clickgui", s.modernLayout, "[{\"category\":\"COMBAT\",\"x\":18}]");
      assertEquals(ClickGuiModule.Font.ROBOTO, GuiTheme.font(r));
      assertFalse(GuiTheme.bold(s));
      assertFalse(s.font.isVisible());
      assertFalse(s.customTextStyle.isVisible());
      assertFalse(s.accent.isVisible());
      assertSame(s.modernLayout, s.panelLayout());
      assertEquals("[]", s.layout.get());
      r.modules().setSetting("clickgui", s.visuals, ClickGuiModule.Visuals.OLD);
      assertEquals(ClickGuiModule.Font.SERIF, GuiTheme.font(r));
      assertTrue(GuiTheme.bold(s));
      assertTrue(s.accent.isVisible());
      assertSame(s.layout, s.panelLayout());
      assertTrue(s.modernLayout.get().contains("18"));
    } finally {
      r.stop();
    }
  }

  @Test
  public void independentColorsAndWeightsSurviveConfigReload() {
    ClientRuntime first = runtime();
    ClickGuiModule settings = (ClickGuiModule) first.modules().require("clickgui").module();
    first.modules().setSetting("clickgui", settings.customTextStyle, true);
    first.modules().setSetting("clickgui", settings.sectionText, ColorValue.rgb(255, 190, 90));
    first.modules().setSetting("clickgui", settings.selectionText, ColorValue.rgb(120, 240, 210));
    first
        .modules()
        .setSetting("clickgui", settings.selectionHighlight, ColorValue.rgba(80, 120, 220, 105));
    first.modules().setSetting("clickgui", settings.mutedText, ColorValue.rgb(150, 160, 180));
    first.modules().setSetting("clickgui", settings.textWeight, ClickGuiModule.TextWeight.BOLD);
    first.modules().setSetting("clickgui", settings.boldSections, true);
    ColorValue body = settings.text.get(), accent = settings.accent.get();
    first.stop();
    ClientRuntime restored = runtime();
    try {
      ClickGuiModule next = (ClickGuiModule) restored.modules().require("clickgui").module();
      assertEquals(ColorValue.rgb(255, 190, 90), next.sectionText.get());
      assertEquals(ColorValue.rgb(120, 240, 210), next.selectionText.get());
      assertEquals(ColorValue.rgba(80, 120, 220, 105), next.selectionHighlight.get());
      assertEquals(ColorValue.rgb(150, 160, 180), next.mutedText.get());
      assertEquals(ClickGuiModule.TextWeight.BOLD, next.textWeight.get());
      assertTrue(next.boldSections.get());
      assertTrue(next.customTextStyle.get());
      assertEquals(body, next.text.get());
      assertEquals(accent, next.accent.get());
    } finally {
      restored.stop();
    }
  }
}
