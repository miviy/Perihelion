package perihelion.core.command;

import static org.junit.Assert.*;

import java.util.*;
import java.util.function.Consumer;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import perihelion.core.diagnostics.ClientLog;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.*;
import perihelion.core.setting.Setting;

public class CommandDispatcherTest {
  @Rule public TemporaryFolder temporary = new TemporaryFolder();
  private ClientRuntime runtime;
  private Setting<String> text;
  private Setting<Integer> count;
  private ModuleContext watcher;

  @Before
  public void start() {
    runtime =
        new ClientRuntime(
            temporary.getRoot().toPath().resolve("client.json"),
            ClientLog.NONE,
            r -> {
              r.modules()
                  .register(
                      new ClientModule(
                          new ModuleInfo(
                              "probe", "Probe", "test-only", ModuleCategory.MISC, false)) {
                        {
                          text =
                              setting(
                                  Setting.string(
                                      "text",
                                      "Text",
                                      "Text",
                                      "default",
                                      64,
                                      r.tasks()::checkThread));
                          count =
                              setting(
                                  Setting.integer(
                                      "count", "Count", "Count", 2, 0, 10, r.tasks()::checkThread));
                        }
                      });
              r.modules()
                  .register(
                      new ClientModule(
                          new ModuleInfo(
                              "watcher", "Watcher", "test-only", ModuleCategory.MISC, false)) {
                        @Override
                        protected void onEnable(ModuleContext context) {
                          watcher = context;
                        }
                      });
            });
    runtime.start();
  }

  @After
  public void stop() {
    runtime.stop();
  }

  private List<String> execute(String command) {
    List<String> lines = new ArrayList<>();
    runtime.commands().execute(command, lines::add);
    return lines;
  }

  @Test
  public void dotCommandsAreLocalIncludingTyposAndOrdinaryChatIsUntouched() {
    List<String> lines = new ArrayList<>();
    assertFalse(runtime.commands().executeChat("hello everyone", lines::add));
    assertTrue(lines.isEmpty());
    assertTrue(runtime.commands().executeChat(".set probe count 7", lines::add));
    assertEquals(Integer.valueOf(7), count.get());
    assertTrue(runtime.commands().executeChat(".config nope", lines::add));
    assertTrue(runtime.commands().executeChat(".unknown", lines::add));
    assertTrue(lines.get(lines.size() - 1).contains(".help"));
    assertTrue(execute("help").stream().allMatch(line -> line.startsWith(".")));
  }

  @Test
  public void configCommandsCreateListUpdateAndLoadNamedPresets() throws Exception {
    List<String> lines = new ArrayList<>();
    runtime.commands().executeChat(".config save first", lines::add);
    pumpReply(lines);
    assertTrue(lines.get(0).contains("created"));
    lines.clear();
    execute("set probe count 8");
    runtime.commands().executeChat(".config update first", lines::add);
    pumpReply(lines);
    lines.clear();
    execute("set probe count 1");
    runtime.commands().executeChat(".config load first", lines::add);
    pumpReply(lines);
    assertEquals(Integer.valueOf(8), count.get());
    lines.clear();
    runtime.commands().executeChat(".config list", lines::add);
    pumpReply(lines);
    assertTrue(lines.get(0).contains("first"));
  }

  private void pumpReply(List<String> lines) throws Exception {
    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
    while (lines.isEmpty() && System.nanoTime() < deadline) {
      runtime.tasks().drain();
      Thread.sleep(1);
    }
    assertFalse("Asynchronous command replied", lines.isEmpty());
  }

  @Test
  public void quotedAndEscapedValuesUseCentralValidation() {
    execute("set probe text \"two words\"");
    assertEquals("two words", text.get());
    execute("set probe text \"a\\\"b\"");
    assertEquals("a\"b", text.get());
    assertTrue(execute("set probe count 99").get(0).startsWith("Error:"));
    assertEquals(Integer.valueOf(2), count.get());
    assertTrue(execute("set probe text \"unfinished").get(0).startsWith("Error:"));
    execute("set probe text \"\"");
    assertEquals("", text.get());
  }

  @Test
  public void builtinsUsageUnknownCommandsAndCompletion() {
    assertEquals(7, execute("").size());
    assertTrue(execute("invalid command").get(0).contains("Unknown"));
    assertTrue(execute("toggle").get(0).startsWith("Error:"));
    execute("toggle probe");
    assertTrue(runtime.modules().require("probe").requestedEnabled());
    execute("bind probe mouse:0");
    assertEquals("mouse:0", runtime.modules().require("probe").binding().toString());
    assertEquals(Arrays.asList("config"), runtime.commands().complete("con"));
    assertEquals(Arrays.asList("probe"), runtime.commands().complete("set pr"));
    assertEquals(Arrays.asList("text"), runtime.commands().complete("set probe te"));
    assertTrue(runtime.commands().complete("set nonexistent x").isEmpty());
  }

  @Test
  public void statusIncludesUncapturedConfigChanges() throws Exception {
    runtime.config().saveNow().get(3, java.util.concurrent.TimeUnit.SECONDS);
    assertTrue(execute("status").get(0).contains("config=saved"));
    execute("set probe count 3");
    assertFalse(runtime.config().store().isDirty());
    assertTrue(execute("status").get(0).contains("config=unsaved"));
  }

  @Test
  public void invalidValueKeepsModuleActiveButBrokenObserverQuarantinesIt() {
    execute("toggle probe");
    execute("set probe count 999");
    assertEquals(ModuleState.ENABLED, runtime.modules().require("probe").state());
    count.onChange(
        (setting, before, after) -> {
          throw new IllegalArgumentException("test observer");
        });
    assertTrue(execute("set probe count 3").get(0).contains("module quarantined"));
    assertEquals(Integer.valueOf(3), count.get());
    assertEquals(ModuleState.FAULTED, runtime.modules().require("probe").state());
    assertFalse(runtime.modules().require("probe").requestedEnabled());
  }

  @Test
  public void commandMutationFaultsTheObservingModuleAndStillPersistsTheValue() throws Exception {
    execute("toggle probe");
    execute("toggle watcher");
    runtime.config().saveNow().get(3, java.util.concurrent.TimeUnit.SECONDS);
    RuntimeException failure = new IllegalStateException("watcher");
    watcher.observe(
        count,
        (setting, before, after) -> {
          throw failure;
        });
    assertEquals("probe.count = 3", execute("set probe count 3").get(0));
    assertEquals(Integer.valueOf(3), count.get());
    assertEquals(ModuleState.ENABLED, runtime.modules().require("probe").state());
    assertEquals(ModuleState.FAULTED, runtime.modules().require("watcher").state());
    assertSame(failure, runtime.modules().require("watcher").lastFailure());
    assertTrue(runtime.config().isDirty());
    assertEquals(
        3,
        runtime
            .config()
            .snapshot()
            .getAsJsonObject("modules")
            .getAsJsonObject("probe")
            .getAsJsonObject("settings")
            .get("count")
            .getAsInt());
  }

  @Test
  public void commandAndCompletionFailuresAreContainedButFatalErrorsPropagate() {
    runtime
        .commands()
        .register(
            new ClientCommand() {
              public String name() {
                return "broken";
              }

              public String usage() {
                return "broken";
              }

              public void execute(List<String> args, Consumer<String> output) {
                throw new AssertionError("test");
              }

              public List<String> complete(List<String> args) {
                throw new AssertionError("test");
              }
            });
    assertTrue(execute("broken").get(0).contains("Command failed"));
    assertTrue(runtime.commands().complete("broken ").isEmpty());
    assertFalse(execute("status").isEmpty());
    runtime
        .commands()
        .register(
            new ClientCommand() {
              public String name() {
                return "fatal";
              }

              public String usage() {
                return "fatal";
              }

              public void execute(List<String> args, Consumer<String> output) {
                throw new ThreadDeath();
              }
            });
    assertThrows(ThreadDeath.class, () -> execute("fatal"));
  }
}
