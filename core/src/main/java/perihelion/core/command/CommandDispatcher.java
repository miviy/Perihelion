package perihelion.core.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import perihelion.core.diagnostics.ClientLog;
import perihelion.core.diagnostics.Failures;
import perihelion.core.input.Keybind;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleEntry;
import perihelion.core.setting.Setting;

/** Loader-independent command registry. The adapter always consumes its root command. */
public final class CommandDispatcher {
  private final Map<String, ClientCommand> commands = new LinkedHashMap<>();
  private final ClientRuntime runtime;
  private final ClientLog log;

  public CommandDispatcher(ClientRuntime runtime, ClientLog log) {
    this.runtime = runtime;
    this.log = log;
    builtin(
        "help",
        "help",
        (args, out) -> {
          exact(args, 0);
          for (ClientCommand c : commands.values()) out.accept("." + c.usage());
        },
        args -> Collections.emptyList());
    builtin(
        "status",
        "status [timing on|off]",
        (args, out) -> {
          if (!args.isEmpty()) {
            exact(args, 2);
            if (!args.get(0).equals("timing")
                || !(args.get(1).equals("on") || args.get(1).equals("off")))
              throw new IllegalArgumentException("Use status timing on|off");
            boolean enabled = args.get(1).equals("on");
            runtime.clientEvents().setTimingEnabled(enabled);
            runtime.networkEvents().setTimingEnabled(enabled);
          }
          out.accept(
              "Perihelion "
                  + runtime.state()
                  + "; connection="
                  + runtime.connection().kind()
                  + "; modules="
                  + runtime.modules().entries().size()
                  + "; listeners="
                  + (runtime.clientEvents().subscriptionCount()
                      + runtime.networkEvents().subscriptionCount())
                  + "; queued="
                  + runtime.tasks().pendingCount()
                  + "; config="
                  + (runtime.config().store().isReadOnly()
                      ? "read-only"
                      : runtime.config().isDirty() ? "unsaved" : "saved"));
          if (runtime.clientEvents().isTimingEnabled())
            out.accept(
                "Measured dispatches="
                    + (runtime.clientEvents().measuredDispatches()
                        + runtime.networkEvents().measuredDispatches())
                    + "; total nanoseconds="
                    + (runtime.clientEvents().measuredNanos()
                        + runtime.networkEvents().measuredNanos()));
          if (runtime.config().store().lastFailure() != null)
            out.accept("Configuration error; see client log.");
        },
        args -> args.size() <= 1 ? Arrays.asList("timing") : Arrays.asList("on", "off"));
    builtin(
        "modules",
        "modules",
        (args, out) -> {
          exact(args, 0);
          if (runtime.modules().entries().isEmpty())
            out.accept("No modules are installed in this foundation.");
          for (ModuleEntry entry : runtime.modules().entries())
            out.accept(entry.info().id() + " " + entry.state() + " bind=" + entry.binding());
        },
        args -> Collections.emptyList());
    builtin(
        "toggle",
        "toggle <module>",
        (args, out) -> {
          exact(args, 1);
          requireRunning();
          runtime.modules().toggle(args.get(0));
          out.accept(args.get(0) + " " + runtime.modules().require(args.get(0)).state());
        },
        args -> moduleIds());
    builtin(
        "set",
        "set <module> <setting> <value>",
        (args, out) -> {
          exact(args, 3);
          requireRunning();
          Setting<?> setting = setting(args.get(0), args.get(1));
          setText(args.get(0), setting, args.get(2));
          out.accept(args.get(0) + "." + setting.id() + " = " + setting.get());
        },
        args -> {
          if (args.size() <= 1) return moduleIds();
          if (args.size() == 2)
            return new ArrayList<>(
                runtime.modules().require(args.get(0)).module().settings().keySet());
          return Collections.emptyList();
        });
    builtin(
        "bind",
        "bind <module> <keyboard:code|mouse:code|none>",
        (args, out) -> {
          exact(args, 2);
          requireRunning();
          Keybind binding = Keybind.parse(args.get(1));
          runtime.modules().setBinding(args.get(0), binding);
          out.accept(args.get(0) + " bound to " + binding);
        },
        args -> args.size() <= 1 ? moduleIds() : Arrays.asList("none", "keyboard:", "mouse:"));
    builtin(
        "config",
        "config list|save <name>|load <name>|update <name>|delete <name>|reload",
        (args, out) -> {
          requireRunning();
          if (args.isEmpty())
            throw new IllegalArgumentException(
                "Use .config list|save <name>|load <name>|update <name>|delete <name>|reload");
          String action = args.get(0).toLowerCase(Locale.ROOT);
          if (action.equals("list")) {
            exact(args, 1);
            runtime
                .config()
                .store()
                .listProfiles()
                .whenComplete(
                    (names, failure) ->
                        reply(
                            () -> {
                              if (failure != null)
                                out.accept("Config list failed: " + reason(failure));
                              else
                                out.accept(
                                    names.isEmpty()
                                        ? "No configs. Use .config save <name>."
                                        : "Configs: " + String.join(", ", names));
                            }));
          } else if (action.equals("reload")) {
            exact(args, 1);
            report(runtime.config().reload(), "Working configuration reloaded.", out);
          } else if (action.equals("save") && args.size() == 1) {
            report(runtime.config().saveNow(), "Working configuration saved.", out);
          } else {
            exact(args, 2);
            String name = args.get(1);
            if (action.equals("save") || action.equals("create"))
              report(runtime.config().saveProfile(name, false), "Config created: " + name, out);
            else if (action.equals("update"))
              report(runtime.config().saveProfile(name, true), "Config updated: " + name, out);
            else if (action.equals("load"))
              report(runtime.config().loadProfile(name), "Config loaded: " + name, out);
            else if (action.equals("delete"))
              report(
                  runtime.config().store().deleteProfile(name),
                  "Config deleted (recovery copy retained): " + name,
                  out);
            else
              throw new IllegalArgumentException("Use .config list|save|load|update|delete|reload");
          }
        },
        args ->
            args.size() <= 1
                ? Arrays.asList("list", "save", "load", "update", "delete", "reload")
                : Collections.emptyList());
  }

  /** Consumes all dot-prefixed input, including typos, before it can reach a server. */
  public boolean executeChat(String text, Consumer<String> output) {
    runtime.tasks().checkThread();
    if (text == null || !text.startsWith(".")) return false;
    execute(text.substring(1), output);
    return true;
  }

  private void reply(Runnable reply) {
    if (runtime.tasks().isClientThread()) reply.run();
    else if (!runtime.tasks().execute(reply))
      log.warn("Command completed after client task queue became unavailable");
  }

  private static String reason(Throwable failure) {
    while (failure.getCause() != null) failure = failure.getCause();
    return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
  }

  public void register(ClientCommand command) {
    runtime.tasks().checkThread();
    if (command.name() == null || !command.name().matches("[a-z][a-z0-9-]*"))
      throw new IllegalArgumentException("Invalid command name");
    if (commands.putIfAbsent(command.name(), command) != null)
      throw new IllegalArgumentException("Duplicate command: " + command.name());
  }

  public void execute(String input, Consumer<String> output) {
    runtime.tasks().checkThread();
    try {
      List<String> words = tokenize(input, false);
      if (words.isEmpty()) words.add("help");
      ClientCommand command = commands.get(words.get(0).toLowerCase(Locale.ROOT));
      if (command == null) {
        output.accept("Unknown Perihelion command. Use .help.");
        return;
      }
      command.execute(words.subList(1, words.size()), output);
    } catch (IllegalArgumentException invalid) {
      output.accept("Error: " + invalid.getMessage());
    } catch (Throwable failure) {
      Failures.rethrowFatal(failure);
      log.error("Client command failed", failure);
      output.accept("Command failed: " + failure.getMessage());
    }
  }

  public List<String> complete(String input) {
    runtime.tasks().checkThread();
    try {
      List<String> words = tokenize(input, true);
      if (words.isEmpty()) words.add("");
      List<String> choices;
      if (words.size() == 1) choices = new ArrayList<>(commands.keySet());
      else {
        ClientCommand command = commands.get(words.get(0).toLowerCase(Locale.ROOT));
        if (command == null) return Collections.emptyList();
        choices = command.complete(words.subList(1, words.size()));
      }
      String prefix = words.get(words.size() - 1).toLowerCase(Locale.ROOT);
      List<String> result = new ArrayList<>();
      for (String choice : choices)
        if (choice.toLowerCase(Locale.ROOT).startsWith(prefix)) result.add(choice);
      return result;
    } catch (Throwable failure) {
      Failures.rethrowFatal(failure);
      if (!(failure instanceof IllegalArgumentException))
        log.error("Client command completion failed", failure);
      return Collections.emptyList();
    }
  }

  private void requireRunning() {
    if (!runtime.isRunning()) throw new IllegalStateException("Client is not running");
  }

  private Setting<?> setting(String module, String id) {
    Setting<?> setting = runtime.modules().require(module).module().settings().get(id);
    if (setting == null) throw new IllegalArgumentException("Unknown setting: " + id);
    return setting;
  }

  private <T> void setText(String module, Setting<T> setting, String text) {
    if (!runtime.modules().setSetting(module, setting, setting.parse(text))) {
      throw new IllegalStateException(
          "Setting observer failed; module quarantined: " + module,
          runtime.modules().require(module).lastFailure());
    }
  }

  private List<String> moduleIds() {
    List<String> ids = new ArrayList<>();
    for (ModuleEntry entry : runtime.modules().entries()) ids.add(entry.info().id());
    return ids;
  }

  private static void exact(List<String> args, int count) {
    if (args.size() != count)
      throw new IllegalArgumentException(
          "Expected " + count + " arguments; quote values containing spaces. Use .help.");
  }

  private void report(CompletableFuture<Void> operation, String success, Consumer<String> out) {
    operation.whenComplete(
        (ignored, failure) -> {
          Runnable reply =
              () -> {
                if (failure == null) out.accept(success);
                else {
                  log.error("Configuration command failed", failure);
                  out.accept("Configuration operation failed: " + reason(failure));
                }
              };
          if (runtime.tasks().isClientThread()) reply.run();
          else if (!runtime.tasks().execute(reply))
            log.warn("Command completed after client task queue became unavailable");
        });
  }

  private void builtin(
      String name,
      String usage,
      BiConsumer<List<String>, Consumer<String>> run,
      Function<List<String>, List<String>> complete) {
    register(
        new ClientCommand() {
          public String name() {
            return name;
          }

          public String usage() {
            return usage;
          }

          public void execute(List<String> args, Consumer<String> out) {
            run.accept(args, out);
          }

          public List<String> complete(List<String> args) {
            return complete.apply(args);
          }
        });
  }

  static List<String> tokenize(String input, boolean completion) {
    List<String> words = new ArrayList<>();
    StringBuilder word = new StringBuilder();
    boolean quoted = false, escaped = false, started = false;
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      if (escaped) {
        word.append(c);
        escaped = false;
        started = true;
      } else if (c == '\\') {
        escaped = true;
        started = true;
      } else if (c == '"') {
        quoted = !quoted;
        started = true;
      } else if (Character.isWhitespace(c) && !quoted) {
        if (started) {
          words.add(word.toString());
          word.setLength(0);
          started = false;
        }
      } else {
        word.append(c);
        started = true;
      }
    }
    if ((quoted || escaped) && !completion)
      throw new IllegalArgumentException("Unclosed quote or escape");
    if (started) words.add(word.toString());
    else if (completion) words.add("");
    return words;
  }
}
