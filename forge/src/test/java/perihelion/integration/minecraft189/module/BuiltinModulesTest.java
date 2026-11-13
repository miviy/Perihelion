package perihelion.integration.minecraft189.module;

import static org.junit.Assert.*;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import perihelion.core.context.ConnectionContext;
import perihelion.core.diagnostics.ClientLog;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleEntry;
import perihelion.core.module.ModuleState;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.event.VisualEffectEvent;

public class BuiltinModulesTest {
  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private ClientRuntime runtime() {
    ClientRuntime runtime =
        new ClientRuntime(
            folder.getRoot().toPath().resolve("client.json"),
            ClientLog.NONE,
            BuiltinModules::register);
    runtime.start();
    return runtime;
  }

  @Test
  public void allModulesAreWorldScopedAndDisabledByDefault() {
    ClientRuntime runtime = runtime();
    try {
      assertEquals(94, runtime.modules().entries().size());
      for (ModuleEntry entry : runtime.modules().entries()) {
        assertTrue(entry.info().requiresWorld());
        assertFalse(entry.requestedEnabled());
        runtime.modules().setEnabled(entry.info().id(), true);
        assertEquals(ModuleState.WAITING_FOR_WORLD, entry.state());
      }
      // The runtime-owned home-bed observer operates while Breaker is disabled/waiting.
      assertEquals(3, runtime.clientEvents().subscriptionCount());
    } finally {
      runtime.stop();
    }
  }

  @Test
  public void everyIncludedDonorModuleHasARegisteredNativeDefinition() throws Exception {
    java.nio.file.Path ledger = java.nio.file.Paths.get("../docs/yuri-modules.json");
    if (!java.nio.file.Files.isRegularFile(ledger))
      ledger = java.nio.file.Paths.get("docs/yuri-modules.json");
    com.google.gson.JsonObject document =
        new com.google.gson.JsonParser()
            .parse(
                new String(
                    java.nio.file.Files.readAllBytes(ledger),
                    java.nio.charset.StandardCharsets.UTF_8))
            .getAsJsonObject();
    ClientRuntime runtime = runtime();
    try {
      java.util.Set<String> ids = new java.util.HashSet<>();
      int excluded = 0;
      for (java.util.Map.Entry<String, com.google.gson.JsonElement> item : document.entrySet()) {
        com.google.gson.JsonObject entry = item.getValue().getAsJsonObject();
        if (entry.get("status").getAsString().equals("excluded-client-ui")) {
          excluded++;
          continue;
        }
        String id = entry.get("native").getAsString();
        assertNotNull(item.getKey(), runtime.modules().require(id));
        assertTrue(ids.add(id));
      }
      assertEquals(65, ids.size());
      assertEquals(21, excluded);
    } finally {
      runtime.stop();
    }
  }

  @Test
  public void visualListenersRetireAcrossDisableWorldLossAndRejoin() {
    ClientRuntime runtime = runtime();
    String[] ids = {"fullbright", "nofov", "nobob", "nohurtcam", "antiblind"};
    try {
      runtime.updateWorld(
          true, new ConnectionContext(ConnectionContext.Kind.SINGLEPLAYER, ""), true);
      for (String id : ids) runtime.modules().setEnabled(id, true);
      for (VisualEffectEvent.Kind kind : VisualEffectEvent.Kind.values()) {
        assertEquals(
            kind != VisualEffectEvent.Kind.NAUSEA,
            runtime.clientEvents().post(new VisualEffectEvent(kind)).isCancelled());
      }
      runtime.modules().setEnabled("nofov", false);
      assertFalse(
          runtime
              .clientEvents()
              .post(new VisualEffectEvent(VisualEffectEvent.Kind.DYNAMIC_FOV))
              .isCancelled());
      runtime.updateWorld(false, ConnectionContext.DISCONNECTED, true);
      assertEquals(3, runtime.clientEvents().subscriptionCount());
      runtime.updateWorld(
          true, new ConnectionContext(ConnectionContext.Kind.SINGLEPLAYER, ""), true);
      assertEquals(7, runtime.clientEvents().subscriptionCount());
      assertTrue(
          runtime
              .clientEvents()
              .post(new VisualEffectEvent(VisualEffectEvent.Kind.GAMMA))
              .isCancelled());
    } finally {
      runtime.stop();
    }
    assertEquals(0, runtime.clientEvents().subscriptionCount());
  }

  @Test
  public void everyLatestMappedModuleIsRegisteredAndNewCombatDefaultsMatch() throws Exception {
    java.nio.file.Path path = java.nio.file.Paths.get("../docs/yuri-1.3.1-modules.json");
    if (!java.nio.file.Files.isRegularFile(path))
      path = java.nio.file.Paths.get("docs/yuri-1.3.1-modules.json");
    com.google.gson.JsonObject document =
        new com.google.gson.JsonParser()
            .parse(
                new String(
                    java.nio.file.Files.readAllBytes(path),
                    java.nio.charset.StandardCharsets.UTF_8))
            .getAsJsonObject();
    ClientRuntime runtime = runtime();
    try {
      int mapped = 0, excluded = 0, deferred = 0;
      for (java.util.Map.Entry<String, com.google.gson.JsonElement> row :
          document.getAsJsonObject("modules").entrySet()) {
        com.google.gson.JsonObject entry = row.getValue().getAsJsonObject();
        switch (entry.get("status").getAsString()) {
          case "native-implementation":
            assertNotNull(
                row.getKey(), runtime.modules().require(entry.get("native").getAsString()));
            mapped++;
            break;
          case "excluded-client-ui":
            excluded++;
            break;
          case "deferred":
            deferred++;
            break;
          default:
            fail(row.getKey());
        }
      }
      assertEquals(77, mapped);
      assertEquals(22, excluded);
      assertEquals(3, deferred);
      java.util.Map<String, Setting<?>> hit =
          runtime.modules().require("hitselect").module().settings();
      assertEquals(6, hit.size());
      assertEquals(3d, hit.get("on-hurt-time").get());
      assertEquals(3.5d, hit.get("search-range").get());
      assertEquals(true, hit.get("counter-hit").get());
      assertEquals(-5d, hit.get("trade-hurt-time").parse("-5"));
      try {
        hit.get("on-hurt-time").parse("11");
        fail("hurt-time upper bound");
      } catch (IllegalArgumentException expected) {
      }
      Setting<?> range =
          runtime.modules().require("autoprojectile").module().settings().get("target-range");
      assertEquals(10d, range.get());
      assertEquals(15d, range.parse("15"));
    } finally {
      runtime.stop();
    }
  }

  @Test
  public void velocityLatestReductionUsesBoundedTicksAndRetainsHiddenLegacyKeys() {
    ClientRuntime runtime = runtime();
    try {
      java.util.Map<String, Setting<?>> settings =
          runtime.modules().require("velocity").module().settings();
      runtime.commands().execute("set velocity mode legit", text -> {});
      assertEquals(true, settings.get("jump-reset").get());
      assertEquals(true, settings.get("jump-only-sprinting").get());
      assertFalse(settings.get("attack-times").isVisible());
      assertTrue(settings.get("reduce-ticks").isVisible());
      assertEquals(2, settings.get("reduce-ticks").get());
      runtime.commands().execute("set velocity reduce false", text -> {});
      for (String id : new String[] {"only-sprinting", "attack-times", "reduce-when-can-attack"})
        assertFalse(id, settings.get(id).isVisible());
      assertFalse(settings.get("jump-only-sprinting").isVisible());
      runtime.commands().execute("set velocity jump-reset false", text -> {});
      assertFalse(settings.get("jump-only-sprinting").isVisible());
      runtime.commands().execute("set velocity mode custom", text -> {});
      assertTrue(settings.get("velocity-x-modifier").isVisible());
      assertFalse(settings.get("jump-reset").isVisible());
      java.util.Map<String, Setting<?>> jump =
          runtime.modules().require("jumpreset").module().settings();
      assertEquals(100d, jump.get("chance").get());
      assertEquals(false, jump.get("only-sprinting").get());
      assertEquals(true, jump.get("require-nearby-player").get());
      assertFalse(runtime.modules().require("jumpreset").requestedEnabled());
      String search =
          perihelion.integration.minecraft189.gui.GuiValues.searchable(
              runtime.modules().require("velocity"));
      assertTrue(
          perihelion.integration.minecraft189.gui.GuiValues.matches(search, "velocity legit"));
      assertTrue(perihelion.integration.minecraft189.gui.GuiValues.matches(search, "reduce ticks"));
      assertTrue(
          perihelion.integration.minecraft189.gui.GuiValues.matches(search, "intave_latest"));
      assertFalse(
          perihelion.integration.minecraft189.gui.GuiValues.matches(
              search, "velocity missing-option"));
    } finally {
      runtime.stop();
    }
  }

  @Test
  public void timerRejectsFrozenAndNonFiniteSpeedsAndHasModeVisibility() {
    ClientRuntime runtime = runtime();
    try {
      Setting<?> first = runtime.modules().require("timer").module().settings().get("time-amount");
      for (String invalid : new String[] {"0", "-1", "NaN", "Infinity", "5.1"}) {
        try {
          first.parse(invalid);
          fail("Accepted " + invalid);
        } catch (IllegalArgumentException expected) {
        }
      }
      assertEquals(0.1, (Double) first.parse("0.1"), 0);
      Setting<?> second =
          runtime.modules().require("timer").module().settings().get("time-amount-2");
      assertFalse(second.isVisible());
      runtime.commands().execute("set timer mode pulse", text -> {});
      assertTrue(second.isVisible());
    } finally {
      runtime.stop();
    }
  }
}
