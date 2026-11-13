package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import perihelion.core.context.ConnectionContext;
import perihelion.core.diagnostics.ClientLog;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.event.MouseOverEvent;
import perihelion.integration.minecraft189.event.RightClickEvent;

public class MouseSelectionLifecycleTest {
  @Rule public TemporaryFolder folder = new TemporaryFolder();

  @Test
  public void selectionRequiresTheUnchangedWinningRotationAndExplicitTickContext()
      throws Exception {
    perihelion.core.concurrent.ClientTaskQueue tasks =
        new perihelion.core.concurrent.ClientTaskQueue(Thread.currentThread(), ClientLog.NONE);
    perihelion.core.control.SharedControls controls =
        new perihelion.core.control.SharedControls(tasks);
    perihelion.core.event.EventOwner owner =
        new perihelion.core.event.EventOwner(
            "pick",
            failure -> {
              throw new AssertionError(failure);
            });
    owner.activate();
    java.util.List<AutoCloseable> claims = new java.util.ArrayList<>();
    perihelion.core.control.ControlRequest<perihelion.core.control.Rotation> request =
        controls.scope(owner, claims::add).rotation(1);
    try {
      request.set(perihelion.core.control.Rotation.serverUnwrapped(181, 0));
      MouseOverEvent tick = new MouseOverEvent(true);
      assertTrue(tick.tickSelection());
      assertFalse(new MouseOverEvent().tickSelection());
      tick.selection(null, request);
      assertTrue(tick.hasSelection());
      request.set(perihelion.core.control.Rotation.serverUnwrapped(182, 0));
      assertFalse(tick.hasSelection());
      tick.selection(null, request);
      request.clear();
      assertFalse(tick.hasSelection());
    } finally {
      for (AutoCloseable claim : claims) claim.close();
      owner.close();
      controls.close();
      tasks.close();
    }
  }

  @Test
  public void nativeRaySettingsRetireWithActivationAndResumeWithoutAccumulating() {
    ClientRuntime runtime =
        new ClientRuntime(
            folder.getRoot().toPath().resolve("client.json"),
            ClientLog.NONE,
            r -> {
              r.modules().register(new GhostCombatModules.Reach(r));
              r.modules().register(new GhostCombatModules.HitboxExpand(r));
            });
    runtime.start();
    try {
      runtime.updateWorld(
          true, new ConnectionContext(ConnectionContext.Kind.SINGLEPLAYER, ""), true);
      setting(runtime, "reach", "min-range", 4.0);
      setting(runtime, "reach", "max-range", 4.0);
      setting(runtime, "hitboxexpand", "amount", 2.0);
      runtime.modules().setEnabled("reach", true);
      runtime.modules().setEnabled("hitboxexpand", true);
      MouseOverEvent active = runtime.clientEvents().post(new MouseOverEvent());
      assertEquals(4, active.range(), 0);
      assertEquals(2, active.expand(), 0);
      assertTrue(runtime.clientEvents().post(new RightClickEvent()).normalRangeRay());
      runtime.modules().setEnabled("hitboxexpand", false);
      assertEquals(0, runtime.clientEvents().post(new MouseOverEvent()).expand(), 0);
      assertTrue(runtime.clientEvents().post(new RightClickEvent()).normalRangeRay());
      runtime.modules().setEnabled("reach", false);
      assertEquals(3, runtime.clientEvents().post(new MouseOverEvent()).range(), 0);
      assertFalse(runtime.clientEvents().post(new RightClickEvent()).normalRangeRay());
      runtime.modules().setEnabled("reach", true);
      runtime.modules().setEnabled("hitboxexpand", true);
      int listeners = runtime.clientEvents().subscriptionCount();
      runtime.updateWorld(false, ConnectionContext.DISCONNECTED, true);
      assertEquals(3, runtime.clientEvents().post(new MouseOverEvent()).range(), 0);
      assertFalse(runtime.clientEvents().post(new RightClickEvent()).normalRangeRay());
      runtime.updateWorld(
          true, new ConnectionContext(ConnectionContext.Kind.SINGLEPLAYER, ""), true);
      assertEquals(listeners, runtime.clientEvents().subscriptionCount());
      assertEquals(2, runtime.clientEvents().post(new MouseOverEvent()).expand(), 0);
    } finally {
      runtime.stop();
    }
    assertEquals(0, runtime.clientEvents().subscriptionCount());
  }

  @SuppressWarnings("unchecked")
  private static void setting(ClientRuntime runtime, String module, String id, double value) {
    Setting<Double> setting =
        (Setting<Double>) runtime.modules().require(module).module().settings().get(id);
    runtime.modules().setSetting(module, setting, value);
  }
}
