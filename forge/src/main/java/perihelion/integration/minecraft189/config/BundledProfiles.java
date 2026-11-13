package perihelion.integration.minecraft189.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import perihelion.core.diagnostics.ClientLog;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleEntry;
import perihelion.core.setting.Setting;

/** Seeds normal profile files after definitions are registered, before the working config loads. */
public final class BundledProfiles {
  private BundledProfiles() {}

  public static CompletableFuture<Void> install(ClientRuntime runtime, ClientLog log) {
    runtime.tasks().checkThread();
    CompletableFuture<Void> operation;
    try {
      operation = runtime.config().store().installProfiles(documents(runtime));
    } catch (IOException | RuntimeException failure) {
      operation = new CompletableFuture<>();
      operation.completeExceptionally(failure);
    }
    operation.whenComplete(
        (ignored, failure) -> {
          if (failure != null) log.error("Could not install bundled config profiles", failure);
        });
    return operation;
  }

  static Map<String, JsonObject> documents(ClientRuntime runtime) throws IOException {
    // Capture untouched definitions, including the normal ClickGUI binding and HUD defaults.
    JsonObject defaults = runtime.config().snapshot();
    JsonObject hypxile = new JsonParser().parse(defaults.toString()).getAsJsonObject();
    JsonObject overrides;
    try (InputStream input =
        BundledProfiles.class.getResourceAsStream("/assets/perihelion/configs/hypxile.json")) {
      if (input == null) throw new IOException("Bundled hypxile config is missing");
      try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
        overrides = new JsonParser().parse(reader).getAsJsonObject().getAsJsonObject("modules");
      }
    }
    JsonObject modules = hypxile.getAsJsonObject("modules");
    for (Map.Entry<String, JsonElement> item : overrides.entrySet()) {
      ModuleEntry entry = runtime.modules().require(item.getKey());
      JsonObject source = item.getValue().getAsJsonObject();
      JsonObject target = modules.getAsJsonObject(item.getKey());
      target.add("enabled", source.get("enabled"));
      target.add("binding", source.get("binding"));
      JsonObject settings = target.getAsJsonObject("settings");
      for (Map.Entry<String, JsonElement> value : source.getAsJsonObject("settings").entrySet()) {
        Setting<?> setting = entry.module().settings().get(value.getKey());
        if (setting == null)
          throw new IOException("Unknown bundled setting: " + item.getKey() + "." + value.getKey());
        setting.fromJson(
            value.getValue()); // Fail visibly if a future definition invalidates the preset.
        settings.add(value.getKey(), value.getValue());
      }
    }
    Map<String, JsonObject> profiles = new LinkedHashMap<>();
    profiles.put("default", defaults);
    profiles.put("hypxile", hypxile);
    return profiles;
  }
}
