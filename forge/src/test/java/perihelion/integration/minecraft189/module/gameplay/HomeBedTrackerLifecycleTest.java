package perihelion.integration.minecraft189.module.gameplay;

import static org.junit.Assert.*;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import perihelion.core.context.ConnectionContext;
import perihelion.core.diagnostics.ClientLog;
import perihelion.core.lifecycle.ClientRuntime;

public class HomeBedTrackerLifecycleTest {
  @Rule public TemporaryFolder folder = new TemporaryFolder();

  @Test public void runtimeOwnsObserversAcrossWorldChangesAndClosesThemOnStop() {
    ClientRuntime runtime = new ClientRuntime(folder.getRoot().toPath().resolve("client.json"),
        ClientLog.NONE, HomeBedTracker::new);
    runtime.start();
    try {
      assertTrue(runtime.isRunning());
      assertEquals(3, runtime.clientEvents().subscriptionCount());
      runtime.updateWorld(true, new ConnectionContext(ConnectionContext.Kind.SINGLEPLAYER, ""), true);
      runtime.updateWorld(false, ConnectionContext.DISCONNECTED, true);
      assertEquals(3, runtime.clientEvents().subscriptionCount());
    } finally { runtime.stop(); }
    assertEquals(0, runtime.clientEvents().subscriptionCount());
    assertEquals(0, runtime.networkEvents().subscriptionCount());
    runtime.stop();
    assertEquals(0, runtime.clientEvents().subscriptionCount());
  }
}
