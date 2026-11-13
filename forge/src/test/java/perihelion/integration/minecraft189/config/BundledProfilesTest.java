package perihelion.integration.minecraft189.config;

import static org.junit.Assert.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import perihelion.core.diagnostics.ClientLog;
import perihelion.core.input.Keybind;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleEntry;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.gui.ClickGuiModule;
import perihelion.integration.minecraft189.module.BuiltinModules;

public class BundledProfilesTest {
  @Rule public TemporaryFolder folder = new TemporaryFolder();
  private final List<String> warnings = new ArrayList<>();
  private final ClientLog log =
      new ClientLog() {
        public void debug(String message) {}

        public void info(String message) {}

        public void warn(String message) {
          warnings.add(message);
        }

        public void error(String message, Throwable failure) {
          warnings.add(message + ": " + failure);
        }
      };
  private CompletableFuture<Void> installed;
  private JsonObject fresh;

  private ClientRuntime runtime(Path path) throws Exception {
    ClientRuntime runtime =
        new ClientRuntime(
            path,
            log,
            r -> {
              BuiltinModules.register(r);
              r.modules().register(new ClickGuiModule(r.tasks()::checkThread, () -> {}));
              fresh = r.config().snapshot();
              installed = BundledProfiles.install(r, log);
            });
    runtime.start();
    assertTrue(runtime.isRunning());
    installed.get(3, TimeUnit.SECONDS);
    return runtime;
  }

  private void load(ClientRuntime runtime, String name) throws Exception {
    CompletableFuture<Void> operation = runtime.config().loadProfile(name);
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
    while (!operation.isDone() && System.nanoTime() < deadline) {
      runtime.tasks().drain();
      Thread.sleep(1);
    }
    operation.get(1, TimeUnit.SECONDS);
  }

  @Test
  public void freshStartupIsUnchangedAndPresetsRoundTripWithoutFallbacks() throws Exception {
    ClientRuntime runtime = runtime(folder.getRoot().toPath().resolve("client.json"));
    try {
      assertEquals(fresh, runtime.config().snapshot());
      assertEquals(fresh, runtime.config().store().readProfile("default").get().json());
      assertEquals(95, fresh.getAsJsonObject("modules").entrySet().size());
      for (ModuleEntry entry : runtime.modules().entries()) assertFalse(entry.requestedEnabled());
      JsonObject hypxile = runtime.config().store().readProfile("hypxile").get().json();
      load(runtime, "hypxile");
      assertEquals(hypxile, runtime.config().snapshot());
      Set<String> enabled = new TreeSet<>();
      for (ModuleEntry entry : runtime.modules().entries())
        if (entry.requestedEnabled()) enabled.add(entry.info().id());
      assertEquals(
          new TreeSet<>(
              Arrays.asList(
                  "antibot",
                  "antifireball",
                  "autoswap",
                  "backtrack",
                  "bowaimbot",
                  "fastbreak",
                  "hitselect",
                  "inputfixes",
                  "fastplace",
                  "sprint",
                  "velocity",
                  "wtap")),
          enabled);
      assertEquals(
          new Keybind(Keybind.Device.KEYBOARD, 19), runtime.modules().require("aura").binding());
      assertEquals(
          new Keybind(Keybind.Device.KEYBOARD, 48),
          runtime.modules().require("scaffold").binding());
      assertEquals(
          new Keybind(Keybind.Device.KEYBOARD, 54),
          runtime.modules().require("clickgui").binding());
      assertEquals(18.8, (Double) setting(runtime, "aura", "min-cps").get(), 0);
      assertEquals(5.5, (Double) setting(runtime, "aura", "min-rotation-speed").get(), 0);
      assertEquals(80.0, (Double) setting(runtime, "backtrack", "max-delay").get(), 0);
      assertEquals(true, setting(runtime, "scaffold", "hypixel-telly").get());
      assertEquals(true, setting(runtime, "scaffold", "block-counter").get());
      // Old Yuri's hidden Attack Times=2 must not reach this legacy, non-GUI setting.
      assertEquals(1, setting(runtime, "velocity", "attack-times").get());
      assertEquals(2, setting(runtime, "velocity", "reduce-ticks").get());
      assertEquals(false, setting(runtime, "fastplace", "allow-projectiles").get());
      assertEquals(true, setting(runtime, "manager", "drop-shears").get());
      runtime.modules().setSetting("aura", typed(setting(runtime, "aura", "min-cps")), 10.0);
      load(runtime, "default");
      assertEquals(fresh, runtime.config().snapshot());
      assertTrue(warnings.toString(), warnings.isEmpty());
    } finally {
      runtime.stop();
    }
  }

  @Test
  public void restartKeepsWorkingEditsAndDeletedBundledProfilesStayDeleted() throws Exception {
    Path path = folder.getRoot().toPath().resolve("client.json");
    ClientRuntime runtime = runtime(path);
    try {
      load(runtime, "hypxile");
      runtime.modules().setSetting("aura", typed(setting(runtime, "aura", "min-cps")), 11.0);
      runtime.config().saveProfile("hypxile", true).get(3, TimeUnit.SECONDS);
      // Updating one preset and deleting the other remain ordinary profile operations.
      runtime.config().store().deleteProfile("default").get(3, TimeUnit.SECONDS);
    } finally {
      runtime.stop();
    }
    runtime = runtime(path);
    try {
      assertEquals(
          Collections.singletonList("hypxile"), runtime.config().store().listProfiles().get());
      assertEquals(11.0, (Double) setting(runtime, "aura", "min-cps").get(), 0);
      load(runtime, "hypxile");
      assertEquals(11.0, (Double) setting(runtime, "aura", "min-cps").get(), 0);
      runtime.config().store().deleteProfile("hypxile").get(3, TimeUnit.SECONDS);
    } finally {
      runtime.stop();
    }
    runtime = runtime(path);
    try {
      assertTrue(runtime.config().store().listProfiles().get().isEmpty());
      assertEquals(11.0, (Double) setting(runtime, "aura", "min-cps").get(), 0);
      assertTrue(warnings.toString(), warnings.isEmpty());
    } finally {
      runtime.stop();
    }
  }

  @Test
  public void everyImportedSettingHasAReachableGuiControl() throws Exception {
    ClientRuntime runtime = runtime(folder.getRoot().toPath().resolve("client.json"));
    try (InputStreamReader reader =
        new InputStreamReader(
            BundledProfiles.class.getResourceAsStream("/assets/perihelion/configs/hypxile.json"),
            StandardCharsets.UTF_8)) {
      JsonObject overrides =
          new JsonParser().parse(reader).getAsJsonObject().getAsJsonObject("modules");
      int count = 0;
      assertEquals(50, overrides.entrySet().size());
      for (Map.Entry<String, JsonElement> item : overrides.entrySet()) {
        ModuleEntry module = runtime.modules().require(item.getKey());
        Set<String> visible = new HashSet<>();
        observe(module, visible);
        for (Setting<?> knob : module.module().settings().values()) {
          Object original = knob.get();
          List<String> choices = knob.metadata().options();
          if (knob.metadata().kind() == perihelion.core.setting.SettingMetadata.Kind.BOOLEAN)
            choices = Arrays.asList("true", "false");
          else if (knob.metadata().kind() != perihelion.core.setting.SettingMetadata.Kind.ENUM)
            continue;
          for (String choice : choices) {
            runtime.modules().setSetting(item.getKey(), typed(knob), knob.parse(choice));
            observe(module, visible);
          }
          runtime.modules().setSetting(item.getKey(), typed(knob), original);
        }
        for (Map.Entry<String, JsonElement> value :
            item.getValue().getAsJsonObject().getAsJsonObject("settings").entrySet()) {
          assertTrue(
              "No reachable GUI control: " + item.getKey() + "." + value.getKey(),
              visible.contains(value.getKey()));
          count++;
        }
      }
      assertEquals(225, count);
    } finally {
      runtime.stop();
    }
  }

  private static void observe(ModuleEntry module, Set<String> visible) {
    for (Setting<?> setting : module.module().settings().values())
      if (setting.isVisible()) visible.add(setting.id());
  }

  private static Setting<?> setting(ClientRuntime runtime, String module, String id) {
    return runtime.modules().require(module).module().settings().get(id);
  }

  @SuppressWarnings("unchecked")
  private static Setting<Object> typed(Setting<?> setting) {
    return (Setting<Object>) setting;
  }
}
