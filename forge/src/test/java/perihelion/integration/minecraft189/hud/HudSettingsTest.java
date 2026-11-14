package perihelion.integration.minecraft189.hud;

import static org.junit.Assert.*;

import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import perihelion.core.diagnostics.ClientLog;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleState;
import perihelion.core.setting.ColorValue;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.gui.widget.SettingControl;
import perihelion.integration.minecraft189.gui.widget.SettingSection;

public class HudSettingsTest {
  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private ClientRuntime runtime() {
    Path path = folder.getRoot().toPath().resolve("client.json");
    ClientRuntime r =
        new ClientRuntime(
            path,
            ClientLog.NONE,
            client -> client.modules().register(new HudModule(client, () -> null)));
    r.start();
    return r;
  }

  @Test
  public void arbitraryDragCoordinatesRoundTripThroughWorkingConfig() {
    ClientRuntime r = runtime();
    HudModule hud = (HudModule) r.modules().require("hud").module();
    hud.move(HudModule.Element.TARGET, 81, 117, 427, 240, 184, 51);
    double x = hud.xs.get(HudModule.Element.TARGET).get(),
        y = hud.ys.get(HudModule.Element.TARGET).get();
    r.modules().setSetting("hud", hud.accent, ColorValue.rgb(10, 20, 30));
    r.modules().setSetting("hud", hud.shown.get(HudModule.Element.KEYS), true);
    java.util.Set<HudModule.PlayerColumn> columns =
        java.util.EnumSet.of(
            HudModule.PlayerColumn.HEALTH,
            HudModule.PlayerColumn.PING,
            HudModule.PlayerColumn.OBSERVED);
    r.modules().setSetting("hud", hud.playerColumns, columns);
    columns.clear();
    r.modules().setSetting("hud", hud.hypixelKeyFile, "config/local-hypixel-key.txt");
    r.modules().setSetting("hud", hud.hypixelGame, HypixelStats.Game.SKYWARS);
    assertTrue(hud.allowChatMove.get());
    r.modules().setSetting("hud", hud.allowChatMove, false);
    r.modules().setEnabled("hud", true);
    r.stop();
    ClientRuntime reloaded = runtime();
    try {
      HudModule next = (HudModule) reloaded.modules().require("hud").module();
      assertEquals(x, next.xs.get(HudModule.Element.TARGET).get(), 0);
      assertEquals(y, next.ys.get(HudModule.Element.TARGET).get(), 0);
      assertEquals(ColorValue.rgb(10, 20, 30), next.accent.get());
      assertTrue(next.shown.get(HudModule.Element.KEYS).get());
      assertEquals(
          java.util.EnumSet.of(
              HudModule.PlayerColumn.HEALTH,
              HudModule.PlayerColumn.PING,
              HudModule.PlayerColumn.OBSERVED),
          next.playerColumns.get());
      assertEquals("config/local-hypixel-key.txt", next.hypixelKeyFile.get());
      assertEquals(HypixelStats.Game.SKYWARS, next.hypixelGame.get());
      assertFalse(next.allowChatMove.get());
      assertEquals(ModuleState.WAITING_FOR_WORLD, reloaded.modules().require("hud").state());
      assertEquals(0, reloaded.clientEvents().subscriptionCount());
    } finally {
      reloaded.stop();
    }
  }

  @Test
  public void logoAndTabGuiHaveIndependentVisibilityAndSavedPositions() {
    ClientRuntime r = runtime();
    HudModule hud = (HudModule) r.modules().require("hud").module();
    hud.move(HudModule.Element.TAB_GUI, 71, 83, 427, 240, 65, 55);
    double x = hud.xs.get(HudModule.Element.TAB_GUI).get();
    r.modules().setSetting("hud", hud.shown.get(HudModule.Element.WATERMARK), false);
    assertTrue(hud.shown.get(HudModule.Element.TAB_GUI).get());
    assertEquals(0, hud.xs.get(HudModule.Element.WATERMARK).get(), 0);
    r.stop();
    ClientRuntime next = runtime();
    try {
      HudModule loaded = (HudModule) next.modules().require("hud").module();
      assertEquals(x, loaded.xs.get(HudModule.Element.TAB_GUI).get(), 0);
      assertFalse(loaded.shown.get(HudModule.Element.WATERMARK).get());
      assertTrue(loaded.shown.get(HudModule.Element.TAB_GUI).get());
    } finally {
      next.stop();
    }
  }

  @Test
  public void resetOnlyChangesLayoutAndClampsOffscreenDrag() {
    ClientRuntime r = runtime();
    try {
      HudModule hud = (HudModule) r.modules().require("hud").module();
      r.modules().setSetting("hud", hud.opacity, .3);
      hud.move(HudModule.Element.WATERMARK, -100, 9999, 427, 240, 162, 23);
      assertEquals(0, hud.xs.get(HudModule.Element.WATERMARK).get(), 0);
      assertEquals(1, hud.ys.get(HudModule.Element.WATERMARK).get(), 0);
      r.commands().execute("hud reset", line -> {});
      assertEquals(0, hud.ys.get(HudModule.Element.WATERMARK).get(), 0);
      assertEquals(.3, hud.opacity.get(), 0);
    } finally {
      r.stop();
    }
  }

  @Test
  public void usefulLayoutSettingsPersistWithoutAppearanceModesOrDuplicateControls() {
    ClientRuntime r = runtime();
    HudModule hud = (HudModule) r.modules().require("hud").module();
    for (HudModule.Element element : HudModule.Element.values()) {
      HudElementStyle style = hud.styles.get(element);
      assertTrue(hud.options(element).contains(style.font));
      assertTrue(hud.options(element).contains(style.background));
      assertTrue(hud.options(element).contains(style.blur));
      assertFalse(style.blur.get());
      assertTrue(style.font.isVisible());
    }
    assertFalse(hud.settings().containsKey("mode"));
    assertTrue(hud.moduleRows.get());
    assertEquals(Integer.valueOf(1), hud.styles.get(HudModule.Element.MODULES).radius.get());
    assertTrue(hud.options(HudModule.Element.MODULES).contains(hud.moduleBorder));
    r.modules().setSetting("hud", hud.moduleAlignment, HudAppearance.Alignment.LEFT);
    r.modules().setSetting("hud", hud.moduleSort, HudAppearance.Sort.ALPHABETICAL);
    r.modules().setSetting("hud", hud.moduleSpacing, 4);
    r.modules().setSetting("hud", hud.accent, ColorValue.rgb(12, 34, 56));
    r.stop();
    ClientRuntime next = runtime();
    try {
      HudModule loaded = (HudModule) next.modules().require("hud").module();
      assertEquals(HudAppearance.Alignment.LEFT, loaded.moduleAlignment.get());
      assertEquals(HudAppearance.Sort.ALPHABETICAL, loaded.moduleSort.get());
      assertEquals(Integer.valueOf(4), loaded.moduleSpacing.get());
      assertEquals(ColorValue.rgb(12, 34, 56), loaded.accent.get());
      loaded.resetLayout();
      assertEquals(Integer.valueOf(4), loaded.moduleSpacing.get());
    } finally {
      next.stop();
    }
  }

  @Test
  public void logoDesignIsReachableAndPersistsWithoutChangingExistingDefaults() {
    ClientRuntime r = runtime();
    HudModule hud = (HudModule) r.modules().require("hud").module();
    assertEquals(HudModule.LogoDesign.TEXT, hud.logoDesign.get());
    assertTrue(hud.options(HudModule.Element.WATERMARK).contains(hud.logoDesign));
    r.modules().setSetting("hud", hud.logoDesign, HudModule.LogoDesign.EMBLEM);
    assertFalse(hud.watermarkInfo.isVisible());
    r.modules().setSetting("hud", hud.logoDesign, HudModule.LogoDesign.EMBLEM_AND_NAME);
    assertTrue(hud.watermarkInfo.isVisible());
    r.modules().setSetting("hud", hud.watermarkInfo, true);
    hud.resetLayout();
    assertEquals(HudModule.LogoDesign.EMBLEM_AND_NAME, hud.logoDesign.get());
    r.stop();
    ClientRuntime next = runtime();
    try {
      HudModule loaded = (HudModule) next.modules().require("hud").module();
      assertEquals(HudModule.LogoDesign.EMBLEM_AND_NAME, loaded.logoDesign.get());
      assertTrue(loaded.watermarkInfo.get());
    } finally {
      next.stop();
    }
  }

  @Test
  public void independentFontsBlurAndGradientsPersistWithoutChangingOtherElements() {
    ClientRuntime r = runtime();
    HudModule hud = (HudModule) r.modules().require("hud").module();
    HudElementStyle array = hud.styles.get(HudModule.Element.MODULES);
    r.modules().setSetting("hud", array.font, HudElementStyle.Font.INTER);
    r.modules().setSetting("hud", array.scale, 1.25);
    r.modules().setSetting("hud", array.blur, true);
    r.modules().setSetting("hud", array.blurStrength, 9);
    r.modules().setSetting("hud", array.panelShadow, true);
    r.modules().setSetting("hud", array.shadowOpacity, .4);
    r.modules().setSetting("hud", array.shadowSpread, 10);
    r.modules().setSetting("hud", array.background, HudElementStyle.Background.GRADIENT);
    r.modules().setSetting("hud", array.backgroundColor, ColorValue.rgba(20, 30, 40, 100));
    r.modules().setSetting("hud", array.opacity, .5);
    r.modules().setSetting("hud", hud.moduleLowercase, true);
    r.modules().setSetting("hud", hud.moduleSuffix, HudAppearance.Suffix.BRACKETS);
    assertEquals(50, array.background(0, false) >>> 24);
    assertFalse(hud.styles.get(HudModule.Element.TARGET).blur.get());
    hud.resetLayout();
    r.stop();
    ClientRuntime next = runtime();
    try {
      HudModule loaded = (HudModule) next.modules().require("hud").module();
      HudElementStyle style = loaded.styles.get(HudModule.Element.MODULES);
      assertEquals(HudElementStyle.Font.INTER, style.font.get());
      assertEquals(1.25, style.scale.get(), 0);
      assertTrue(style.blur.get());
      assertEquals(Integer.valueOf(9), style.blurStrength.get());
      assertTrue(style.panelShadow.get());
      assertEquals(.4, style.shadowOpacity.get(), 0);
      assertEquals(Integer.valueOf(10), style.shadowSpread.get());
      assertEquals(HudElementStyle.Background.GRADIENT, style.background.get());
      assertTrue(loaded.moduleLowercase.get());
      assertEquals(HudAppearance.Suffix.BRACKETS, loaded.moduleSuffix.get());
      assertEquals(
          HudElementStyle.Font.CLIENT, loaded.styles.get(HudModule.Element.TARGET).font.get());
    } finally {
      next.stop();
    }
  }

  @Test
  public void groupedMenuOwnsEverySettingOnceAndStartsEachElementWithItsToggle() {
    ClientRuntime r = runtime();
    try {
      HudModule hud = (HudModule) r.modules().require("hud").module();
      java.util.List<SettingControl> controls = HudSettingsControls.create(hud, null, () -> {});
      java.util.Set<Setting<?>> visited =
          java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
      for (SettingControl control : controls) {
        if (hud.settings().containsValue(control.setting))
          assertTrue("Duplicate setting: " + control.setting.id(), visited.add(control.setting));
      }
      assertEquals(new java.util.HashSet<>(hud.settings().values()), visited);
      int found = 0;
      for (int i = 0; i < controls.size() - 1; i++) {
        SettingControl control = controls.get(i);
        for (HudModule.Element element : HudModule.Element.values()) {
          if (!(control instanceof SettingSection)
              || !control.setting.displayName().equals(element.label)) continue;
          found++;
          SettingSection section = (SettingSection) control;
          SettingControl toggle = controls.get(i + 1);
          assertSame(hud.shown.get(element), toggle.setting);
          assertFalse(toggle.isVisible());
          section.press(0, 0, 1);
          assertTrue(toggle.isVisible());
          boolean before = hud.shown.get(element).get();
          toggle.press(0, 0, 0);
          assertEquals(!before, hud.shown.get(element).get());
          section.press(0, 0, 1);
          assertFalse(toggle.isVisible());
          assertEquals(!before, hud.shown.get(element).get());
        }
      }
      assertEquals(HudModule.Element.values().length, found);
    } finally {
      r.stop();
    }
  }

  @Test
  public void disclosureStateIsScreenOwnedAndDisabledElementsCanBeConfigured() {
    ClientRuntime r = runtime();
    try {
      HudModule hud = (HudModule) r.modules().require("hud").module();
      java.util.List<SettingControl> first = HudSettingsControls.create(hud, null, () -> {});
      java.util.List<SettingControl> second = HudSettingsControls.create(hud, null, () -> {});
      SettingSection players = null;
      for (SettingControl control : first)
        if (control instanceof SettingSection
            && control.setting.displayName().equals("Nearby players"))
          players = (SettingSection) control;
      assertNotNull(players);
      assertFalse(hud.shown.get(HudModule.Element.PLAYERS).get());
      players.open(true);
      SettingControl rows =
          first.stream().filter(c -> c.setting == hud.playerLimit).findFirst().get();
      assertTrue(rows.isVisible());
      assertFalse(
          second.stream().filter(c -> c.setting == hud.playerLimit).findFirst().get().isVisible());
      assertTrue(hud.styles.get(HudModule.Element.KEYS).font.isVisible());
      assertFalse(hud.moduleSuffix.isVisible() && !hud.suffixes.get());
      r.modules().setSetting("hud", hud.suffixes, false);
      assertFalse(hud.moduleSuffix.isVisible());
      assertFalse(hud.overlay.dodgeThreshold.isVisible());
      r.modules().setSetting("hud", hud.overlay.dodgeWarning, true);
      assertTrue(hud.overlay.dodgeThreshold.isVisible());
    } finally {
      r.stop();
    }
  }

  @Test
  public void elementResetsPreserveOtherElementsContentAndPosition() {
    ClientRuntime r = runtime();
    try {
      HudModule hud = (HudModule) r.modules().require("hud").module();
      HudElementStyle logo = hud.styles.get(HudModule.Element.WATERMARK);
      HudElementStyle target = hud.styles.get(HudModule.Element.TARGET);
      r.modules().setSetting("hud", logo.scale, 1.5);
      r.modules().setSetting("hud", target.scale, 1.25);
      r.modules().setSetting("hud", hud.watermarkClock, true);
      r.modules().setSetting("hud", hud.xs.get(HudModule.Element.WATERMARK), .4);
      r.modules().setSetting("hud", hud.xs.get(HudModule.Element.TARGET), .7);
      hud.resetAppearance(HudModule.Element.WATERMARK);
      assertEquals(1, logo.scale.get(), 0);
      assertEquals(1.25, target.scale.get(), 0);
      assertTrue(hud.watermarkClock.get());
      assertEquals(.4, hud.xs.get(HudModule.Element.WATERMARK).get(), 0);
      hud.resetPosition(HudModule.Element.WATERMARK);
      assertEquals(0, hud.xs.get(HudModule.Element.WATERMARK).get(), 0);
      assertEquals(.7, hud.xs.get(HudModule.Element.TARGET).get(), 0);
    } finally {
      r.stop();
    }
  }

  @Test
  public void gradientPreservesColorPickerAlpha() {
    assertEquals(0x607f007f, HudAppearance.blend(0x40ff0000, 0x800000ff, .5f));
    assertEquals(0x40ff0000, HudAppearance.blend(0x40ff0000, 0x800000ff, -1));
    assertEquals(0x800000ff, HudAppearance.blend(0x40ff0000, 0x800000ff, 2));
  }

  @Test
  public void overlayFiltersAndHiddenPlayersRemainConfigurableAndPersist() {
    ClientRuntime r = runtime();
    try {
      HudModule hud = (HudModule) r.modules().require("hud").module();
      r.modules().setSetting("hud", hud.overlay.minFkdr, 3.0);
      r.modules().setSetting("hud", hud.overlay.minStars, 100);
      assertTrue(hud.overlay.matches(null));
      assertTrue(hud.overlay.matches(OverlayProfile.empty("unavailable", false)));
      java.util.Map<String, Long> counts = new java.util.HashMap<>();
      counts.put("stars", 5L);
      counts.put("final_kills", 3L);
      counts.put("final_deaths", 2L);
      OverlayProfile p =
          new OverlayProfile(
              counts,
              java.util.Collections.emptyMap(),
              java.util.Collections.emptyList(),
              "fixture",
              "",
              "",
              false,
              false);
      assertFalse(hud.overlay.matches(p));
      counts.put("stars", 101L);
      assertTrue(
          hud.overlay.matches(
              new OverlayProfile(
                  counts,
                  java.util.Collections.emptyMap(),
                  java.util.Collections.emptyList(),
                  "fixture",
                  "",
                  "",
                  false,
                  false)));
      hud.overlay.hide(hud, "SomePlayer");
      assertTrue(hud.overlay.hidden(java.util.UUID.randomUUID(), "SOMEPLAYER"));
      r.modules().setSetting("hud", hud.overlay.mode, OverlayProfile.Mode.FOURS);
    } finally {
      r.stop();
    }
    ClientRuntime next = runtime();
    try {
      HudModule hud = (HudModule) next.modules().require("hud").module();
      assertTrue(hud.overlay.hidden(java.util.UUID.randomUUID(), "someplayer"));
      assertEquals(OverlayProfile.Mode.FOURS, hud.overlay.mode.get());
      next.commands().execute("hud unhide", line -> {});
      assertTrue(hud.overlay.hidden.get().isEmpty());
    } finally {
      next.stop();
    }
  }
}
