package perihelion.integration.minecraft189.hud;

import static org.junit.Assert.*;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import perihelion.core.diagnostics.ClientLog;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleState;
import perihelion.core.setting.Setting;

public class TargetHudSettingsTest {
  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private ClientRuntime runtime() {
    ClientRuntime runtime =
        new ClientRuntime(
            folder.getRoot().toPath().resolve("client.json"),
            ClientLog.NONE,
            client -> {
              HudModule hud = new HudModule(client, () -> null);
              client.modules().register(hud);
              client.modules().register(new TargetHudModule(hud));
            });
    runtime.start();
    return runtime;
  }

  @Test
  public void targetMenuKeepsAppearanceControlsInOnePlace() {
    ClientRuntime runtime = runtime();
    try {
      HudModule hud = (HudModule) runtime.modules().require("hud").module();
      TargetHudModule target = (TargetHudModule) runtime.modules().require("targethud").module();
      java.util.List<perihelion.integration.minecraft189.gui.widget.SettingControl> controls =
          target.controls(null, () -> {}, 17);
      assertEquals(6, controls.stream().filter(c -> c.isVisible()).count());
      for (Setting<?> setting : hud.styles.get(HudModule.Element.TARGET).settings)
        assertEquals(1, controls.stream().filter(c -> c.setting == setting).count());
      assertTrue(
          HudSettingsControls.create(hud, null, () -> {}).stream()
              .noneMatch(c -> c.setting.id().startsWith("target-")));
    } finally {
      runtime.stop();
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  public void delayAndTogglePersistIndependentlyWithoutMovingTheSavedCard() {
    ClientRuntime runtime = runtime();
    HudModule hud = (HudModule) runtime.modules().require("hud").module();
    TargetHudModule target = (TargetHudModule) runtime.modules().require("targethud").module();
    Setting<Double> delay = (Setting<Double>) target.settings().get("hide-delay");
    assertEquals(6, target.settings().size());
    assertEquals(33, TargetHudModule.Design.values().length);
    assertEquals(3, delay.get(), 0);
    runtime.modules().setSetting("targethud", delay, 1.7);
    Setting<TargetHudModule.Design> design =
        (Setting<TargetHudModule.Design>) target.settings().get("design");
    Setting<TargetHudModule.Theme> theme =
        (Setting<TargetHudModule.Theme>) target.settings().get("theme");
    runtime.modules().setSetting("targethud", design, TargetHudModule.Design.MYAU_TENACITY);
    runtime.modules().setSetting("targethud", theme, TargetHudModule.Theme.CUSTOM);
    hud.move(HudModule.Element.TARGET, 81, 117, 427, 240, 184, 51);
    double x = hud.xs.get(HudModule.Element.TARGET).get();
    double y = hud.ys.get(HudModule.Element.TARGET).get();
    runtime.modules().setEnabled("targethud", true);
    assertFalse(runtime.modules().require("hud").requestedEnabled());
    assertTrue(hud.elementEnabled(HudModule.Element.TARGET));
    assertEquals(ModuleState.WAITING_FOR_WORLD, runtime.modules().require("targethud").state());
    runtime.stop();

    ClientRuntime reloaded = runtime();
    try {
      HudModule next = (HudModule) reloaded.modules().require("hud").module();
      TargetHudModule loadedTarget =
          (TargetHudModule) reloaded.modules().require("targethud").module();
      assertEquals(TargetHudModule.Design.MYAU_TENACITY, loadedTarget.design());
      assertEquals(TargetHudModule.Theme.CUSTOM, loadedTarget.theme());
      assertEquals(
          1.7,
          (Double)
              reloaded.modules().require("targethud").module().settings().get("hide-delay").get(),
          0);
      assertTrue(reloaded.modules().require("targethud").requestedEnabled());
      assertFalse(reloaded.modules().require("hud").requestedEnabled());
      assertEquals(x, next.xs.get(HudModule.Element.TARGET).get(), 0);
      assertEquals(y, next.ys.get(HudModule.Element.TARGET).get(), 0);
      next.toggleElement(HudModule.Element.TARGET);
      assertFalse(reloaded.modules().require("targethud").requestedEnabled());
    } finally {
      reloaded.stop();
    }
  }
}
