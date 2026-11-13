package perihelion.integration.minecraft189.gui;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import perihelion.core.command.ClientCommand;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.integration.minecraft189.gui.font.GuiFonts;
import perihelion.integration.minecraft189.gui.modern.ModernScreen;
import perihelion.integration.minecraft189.gui.panel.ModulePanelScreen;
import perihelion.integration.minecraft189.gui.panel.PanelScreen;
import perihelion.integration.minecraft189.gui.raven.RavenScreen;

/**
 * Small composition seam: another frontend can supply a different GuiScreen without changing
 * modules.
 */
public final class ClickGuiService implements AutoCloseable {
  private final ClientRuntime runtime;
  private final GuiFonts fonts = new GuiFonts();
  private final ClickGuiModule preferences;
  private Supplier<? extends GuiScreen> factory;
  private GuiScreen current;

  public ClickGuiService(ClientRuntime runtime) {
    this.runtime = runtime;
    perihelion.integration.minecraft189.gui.theme.ThemeModule themes =
        new perihelion.integration.minecraft189.gui.theme.ThemeModule(runtime.tasks()::checkThread);
    runtime.modules().register(themes);
    preferences = new ClickGuiModule(runtime.tasks()::checkThread, this::open, themes);
    runtime.modules().register(preferences);
    factory =
        () ->
            preferences.modern()
                ? new ModernScreen(runtime, preferences, fonts)
                : preferences.clientStyle.get() == ClickGuiModule.ClientStyle.RAVEN
                    ? new RavenScreen(runtime, preferences, fonts)
                    : new PanelScreen(runtime, preferences, fonts);
    runtime
        .commands()
        .register(
            new ClientCommand() {
              public String name() {
                return "gui";
              }

              public String usage() {
                return "gui [reset]";
              }

              public void execute(List<String> arguments, Consumer<String> output) {
                if (arguments.size() == 1 && arguments.get(0).equalsIgnoreCase("reset")) {
                  runtime.modules().setSetting("clickgui", preferences.panelLayout(), "[]");
                  if (Minecraft.getMinecraft().currentScreen instanceof ModulePanelScreen)
                    ((ModulePanelScreen) Minecraft.getMinecraft().currentScreen).resetLayout();
                } else if (!arguments.isEmpty())
                  throw new IllegalArgumentException("Use .gui [reset]");
                open();
              }
            });
    runtime.own(this);
  }

  public ClickGuiModule preferences() {
    return preferences;
  }

  public void frontend(Supplier<? extends GuiScreen> factory) {
    runtime.tasks().checkThread();
    this.factory = java.util.Objects.requireNonNull(factory);
  }

  public void open() {
    open(null);
  }

  public void open(GuiScreen parent) {
    runtime.tasks().checkThread();
    if (!runtime.isRunning()) return;
    Minecraft game = Minecraft.getMinecraft();
    if (game.currentScreen instanceof ModulePanelScreen) {
      boolean modern = game.currentScreen instanceof ModernScreen;
      if (modern == preferences.modern()
          && (modern
              || ((ModulePanelScreen) game.currentScreen).style() == preferences.clientStyle.get()))
        return;
    }
    if (game.currentScreen == current && current != null && !(current instanceof ModulePanelScreen))
      return;
    KeyBinding.unPressAllKeys();
    runtime.input().setAllowed(false, java.util.Collections.emptyList());
    current = factory.get();
    if (current instanceof ModulePanelScreen) ((ModulePanelScreen) current).parent(parent);
    game.displayGuiScreen(current);
  }

  @Override
  public void close() {
    Minecraft game = Minecraft.getMinecraft();
    try {
      if (current != null
          && (game.currentScreen == current
              || game.currentScreen instanceof ModulePanelScreen
              || game.currentScreen instanceof ModernScreen)) game.displayGuiScreen(null);
    } finally {
      current = null;
      fonts.close();
    }
  }
}
