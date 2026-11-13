package perihelion.core.lifecycle;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import perihelion.core.diagnostics.ClientLog;
import perihelion.core.module.ClientModule;
import perihelion.core.module.ModuleCategory;
import perihelion.core.module.ModuleInfo;
import perihelion.core.module.ModuleState;
import perihelion.core.setting.ColorValue;
import perihelion.core.setting.DoubleRange;
import perihelion.core.setting.Setting;
import static org.junit.Assert.*;

public class ExtendedSettingsConfigTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private ClientRuntime runtime;
    private Sample sample;
    private enum Target { FIRST, SECOND, THIRD }
    private Path file() { return folder.getRoot().toPath().resolve("client.json"); }
    @After public void close() { if (runtime != null) runtime.close(); }

    private void start() {
        start("Count", ModuleCategory.MISC);
    }
    private void start(String countDisplayName, ModuleCategory category) {
        runtime = new ClientRuntime(file(), ClientLog.NONE, r -> {
            sample = new Sample(r.tasks()::checkThread, countDisplayName, category);
            r.modules().register(sample);
        });
        runtime.start();
        assertEquals(LifecycleState.RUNNING, runtime.state());
    }
    private void write(String json) throws Exception { Files.write(file(), json.getBytes(StandardCharsets.UTF_8)); }
    private JsonObject read() throws Exception {
        return new JsonParser().parse(new String(Files.readAllBytes(file()), StandardCharsets.UTF_8)).getAsJsonObject();
    }
    private List<String> command(String text) {
        List<String> output = new ArrayList<>();
        runtime.commands().execute(text, output::add);
        return output;
    }
    private void set(String text) {
        List<String> output = command("set probe " + text);
        assertEquals(1, output.size());
        assertTrue(output.toString(), output.get(0).startsWith("probe."));
    }
    private void reload() throws Exception {
        CompletableFuture<Void> result = runtime.config().reload();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!result.isDone() && System.nanoTime() < deadline) {
            runtime.tickStart();
            Thread.sleep(1);
        }
        result.get(1, TimeUnit.SECONDS);
    }

    @Test public void commandsConfigAndReloadRoundTripHiddenCompoundValuesAndPreserveUnknownData() throws Exception {
        write("{\"schemaVersion\":1,\"futureRoot\":true,\"modules\":{\"probe\":{\"futureModule\":42,"
                + "\"settings\":{\"futureSetting\":[1,2,3]}}}}");
        start();
        set("count 4"); set("range 1.5..3.5"); set("color #12345678"); set("targets third,FIRST");
        assertFalse(sample.range.isVisible()); // Presentation does not affect commands or persistence.
        runtime.config().saveNow().get(3, TimeUnit.SECONDS);
        JsonObject document = read();
        assertEquals(1, document.get("schemaVersion").getAsInt());
        assertTrue(document.get("futureRoot").getAsBoolean());
        JsonObject module = document.getAsJsonObject("modules").getAsJsonObject("probe");
        assertEquals(42, module.get("futureModule").getAsInt());
        JsonObject settings = module.getAsJsonObject("settings");
        assertEquals("[1,2,3]", settings.get("futureSetting").toString());
        assertEquals(1.5, settings.getAsJsonObject("range").get("lower").getAsDouble(), 0);
        assertEquals(3.5, settings.getAsJsonObject("range").get("upper").getAsDouble(), 0);
        assertEquals("#12345678", settings.get("color").getAsString());
        assertEquals("[\"first\",\"third\"]", settings.get("targets").toString());
        assertFalse(settings.has("metadata")); assertFalse(settings.has("visibility"));
        set("count 8"); set("range 0..1"); set("color #FFFFFF"); set("targets second"); set("advanced true");
        assertTrue(sample.range.isVisible());
        reload();
        assertEquals(Integer.valueOf(4), sample.count.get());
        assertEquals(new DoubleRange(1.5, 3.5), sample.range.get());
        assertEquals(ColorValue.rgba(18, 52, 86, 120), sample.color.get());
        assertEquals(EnumSet.of(Target.FIRST, Target.THIRD), sample.targets.get());
        assertFalse(sample.range.isVisible());
        assertFalse(runtime.config().isDirty());
        runtime.close();
        assertEquals(document, read());
    }

    @Test public void invalidNewValuesFallBackToDefaultsWithoutFaultingTheModule() throws Exception {
        write("{\"schemaVersion\":1,\"modules\":{\"probe\":{\"enabled\":true,\"settings\":{"
                + "\"count\":3,\"range\":{\"lower\":4,\"upper\":2},\"color\":[255,0,0],"
                + "\"targets\":[\"first\",\"FIRST\"],\"advanced\":\"true\"}}}}");
        start();
        assertEquals(Integer.valueOf(2), sample.count.get());
        assertEquals(new DoubleRange(1, 3), sample.range.get());
        assertEquals(ColorValue.rgb(0, 0, 0), sample.color.get());
        assertEquals(EnumSet.of(Target.FIRST), sample.targets.get());
        assertFalse(sample.advanced.get());
        assertEquals(ModuleState.ENABLED, runtime.modules().require("probe").state());
        assertNull(runtime.modules().require("probe").lastFailure());
    }

    @Test public void renamedLabelsAndRecategorizedModulesKeepConfigAndCommandIds() throws Exception {
        start("Attempt count", ModuleCategory.PLAYER);
        set("count 4");
        runtime.config().saveNow().get(3, TimeUnit.SECONDS);
        JsonObject original = read();
        JsonObject module = original.getAsJsonObject("modules").getAsJsonObject("probe");
        JsonObject settings = module.getAsJsonObject("settings");
        assertEquals(4, settings.get("count").getAsInt());
        assertFalse(settings.has("Attempt count"));
        assertFalse(settings.has("displayName"));
        assertFalse(module.has("category"));
        runtime.close();

        start("Maximum attempts", ModuleCategory.WORLD);
        assertEquals("Maximum attempts", sample.count.displayName());
        assertSame(ModuleCategory.WORLD, sample.info().category());
        assertSame(sample.count, sample.settings().get("count"));
        assertFalse(sample.settings().containsKey("Maximum attempts"));
        assertEquals(Integer.valueOf(4), sample.count.get());
        assertTrue(runtime.commands().complete("set probe co").contains("count"));
        set("count 6");
        reload();
        assertEquals(Integer.valueOf(4), sample.count.get());
        runtime.config().saveNow().get(3, TimeUnit.SECONDS);
        assertEquals(original, read());
    }

    @Test public void invalidCommandsPreserveValuesAndKeepModuleActive() {
        start(); runtime.modules().setEnabled("probe", true);
        for (String input : new String[] { "count 3", "range 2..1", "range 0.25..1", "color #123", "targets first,unknown" }) {
            assertTrue(command("set probe " + input).get(0).startsWith("Error:"));
        }
        assertEquals(Integer.valueOf(2), sample.count.get());
        assertEquals(new DoubleRange(1, 3), sample.range.get());
        assertEquals(ColorValue.rgb(0, 0, 0), sample.color.get());
        assertEquals(EnumSet.of(Target.FIRST), sample.targets.get());
        assertEquals(ModuleState.ENABLED, runtime.modules().require("probe").state());
    }

    private static final class Sample extends ClientModule {
        final Setting<Boolean> advanced;
        final Setting<Integer> count;
        final Setting<DoubleRange> range;
        final Setting<ColorValue> color;
        final Setting<Set<Target>> targets;
        Sample(Runnable guard, String countDisplayName, ModuleCategory category) {
            super(new ModuleInfo("probe", "Probe", "test-only", category, false));
            advanced = setting(Setting.bool("advanced", "Advanced", "Show advanced controls", false, guard));
            count = setting(Setting.integer("count", countDisplayName, "Count", 2, 0, 10, 2, guard));
            range = setting(Setting.range("range", "Range", "Range", new DoubleRange(1, 3), 0, 10, 0.5, guard).visibleWhen(advanced::get));
            color = setting(Setting.color("color", "Color", "Color", ColorValue.rgb(0, 0, 0), guard));
            targets = setting(Setting.multiSelect("targets", "Targets", "Targets", EnumSet.of(Target.FIRST), Target.class, guard));
        }
    }
}
