package perihelion.integration.minecraft189.gui.theme;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import perihelion.core.diagnostics.ClientLog;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.setting.ColorValue;

public class ThemeModuleTest {
  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private ClientRuntime runtime() {
    ClientRuntime runtime =
        new ClientRuntime(
            folder.getRoot().toPath().resolve("client.json"),
            ClientLog.NONE,
            r -> r.modules().register(new ThemeModule(r.tasks()::checkThread)));
    runtime.start();
    return runtime;
  }

  private ThemeModule theme(ClientRuntime runtime) {
    return (ThemeModule) runtime.modules().require("themes").module();
  }

  @Test
  public void twentyCuratedPresetsAreDistinctAndReuseTheirImmutablePalettes() {
    ThemeModule settings = new ThemeModule(() -> {});
    assertSame(ThemePalette.DEFAULT, settings.palette());
    Set<String> signatures = new HashSet<>();
    int count = 0;
    for (ThemeModule.Preset preset : ThemeModule.Preset.values()) {
      if (preset == ThemeModule.Preset.CUSTOM) continue;
      count++;
      settings.preset.set(preset);
      ThemePalette palette = settings.palette();
      assertSame(palette, settings.palette());
      assertSame(preset.palette(), palette);
      signatures.add(
          palette.size()
              + ":"
              + palette.color(0)
              + ":"
              + palette.color(.5f)
              + ":"
              + palette.color(1));
    }
    assertEquals(20, count);
    assertEquals(count, signatures.size());
    assertEquals(21, settings.preset.metadata().options().size());
  }

  @Test
  public void customVisibilityOnlyExposesTheChosenNumberOfColorPickers() {
    ThemeModule settings = new ThemeModule(() -> {});
    assertTrue(settings.preset.isVisible());
    assertFalse(settings.colors.isVisible());
    assertFalse(settings.color1.isVisible());
    assertFalse(settings.color2.isVisible());
    assertFalse(settings.color3.isVisible());
    settings.preset.set(ThemeModule.Preset.CUSTOM);
    for (ThemeModule.ColorCount count : ThemeModule.ColorCount.values()) {
      settings.colors.set(count);
      assertTrue(settings.colors.isVisible());
      assertTrue(settings.color1.isVisible());
      assertEquals(count.count() >= 2, settings.color2.isVisible());
      assertEquals(count == ThemeModule.ColorCount.THREE, settings.color3.isVisible());
      assertEquals(count.count(), settings.palette().size());
    }
    settings.preset.set(ThemeModule.Preset.OCEAN);
    assertFalse(settings.colors.isVisible());
    assertFalse(settings.color1.isVisible());
    assertFalse(settings.color2.isVisible());
    assertFalse(settings.color3.isVisible());
  }

  @Test
  public void customColorsSurvivePresetSwitchingAndRestart() {
    ClientRuntime first = runtime();
    ThemeModule settings = theme(first);
    first.modules().setSetting("themes", settings.preset, ThemeModule.Preset.CUSTOM);
    first.modules().setSetting("themes", settings.colors, ThemeModule.ColorCount.THREE);
    first.modules().setSetting("themes", settings.color1, ColorValue.rgb(12, 23, 34));
    first.modules().setSetting("themes", settings.color2, ColorValue.rgb(45, 56, 67));
    first.modules().setSetting("themes", settings.color3, ColorValue.rgb(78, 89, 90));
    ThemePalette saved = settings.palette();
    first.modules().setSetting("themes", settings.preset, ThemeModule.Preset.IRIS);
    assertSame(ThemeModule.Preset.IRIS.palette(), settings.palette());
    first.modules().setSetting("themes", settings.preset, ThemeModule.Preset.CUSTOM);
    assertSame(saved, settings.palette());
    first.stop();
    ClientRuntime second = runtime();
    try {
      ThemeModule restored = theme(second);
      assertEquals(ThemeModule.Preset.CUSTOM, restored.preset.get());
      assertEquals(ThemeModule.ColorCount.THREE, restored.colors.get());
      assertEquals(ColorValue.rgb(12, 23, 34), restored.color1.get());
      assertEquals(ColorValue.rgb(45, 56, 67), restored.color2.get());
      assertEquals(ColorValue.rgb(78, 89, 90), restored.color3.get());
      assertEquals(saved.color(0), restored.palette().color(0));
      assertEquals(saved.color(.5f), restored.palette().color(.5f));
      assertEquals(saved.color(1), restored.palette().color(1));
    } finally {
      second.stop();
    }
  }

  @Test
  public void unchangedVisibleColorsReuseCacheWhileInactiveChannelsRetainTheirSavedValues() {
    ThemeModule settings = new ThemeModule(() -> {});
    settings.preset.set(ThemeModule.Preset.CUSTOM);
    settings.colors.set(ThemeModule.ColorCount.ONE);
    ThemePalette palette = settings.palette();
    settings.color2.set(ColorValue.rgb(10, 20, 30));
    settings.color3.set(ColorValue.rgb(40, 50, 60));
    assertSame(palette, settings.palette());
    settings.color1.set(ColorValue.rgba(239, 136, 59, 0).withRainbow(true, 2));
    assertSame(
        "Opacity and rainbow metadata do not affect static client accents",
        palette,
        settings.palette());
    settings.colors.set(ThemeModule.ColorCount.THREE);
    ThemePalette changed = settings.palette();
    assertNotSame(palette, changed);
    assertSame(changed, settings.palette());
    assertEquals(0xff0a141e, changed.color(.5f));
    assertEquals(0xff28323c, changed.color(1));
    settings.color1.set(ColorValue.rgb(0, 1, 2));
    assertNotSame(changed, settings.palette());
  }

  @Test
  public void presentationPreferencesDoNotRetainAnEnabledGameplayScope() {
    ClientRuntime runtime = runtime();
    try {
      runtime.modules().setEnabled("themes", true);
      assertFalse(runtime.modules().require("themes").requestedEnabled());
      assertEquals(ThemePalette.DEFAULT, theme(runtime).palette());
    } finally {
      runtime.stop();
    }
  }
}
