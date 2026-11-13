package perihelion.bootstrap.forge;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import perihelion.core.diagnostics.ClientLog;
import perihelion.core.diagnostics.Failures;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.integration.minecraft189.control.MinecraftControls;

@Mod(
    modid = "perihelion",
    name = "Perihelion",
    version = PerihelionVersion.VALUE,
    useMetadata = true,
    acceptedMinecraftVersions = "[1.8.9]",
    clientSideOnly = true)
public final class PerihelionMod {
  private ClientRuntime runtime;
  private ForgeClientAdapter adapter;
  private MinecraftControls controls;
  private perihelion.integration.minecraft189.gui.ClickGuiService gui;
  private perihelion.integration.minecraft189.account.AccountService accounts;

  @Mod.EventHandler
  public void initialize(FMLInitializationEvent event) {
    if (runtime != null) return;
    ClientLog log = new Log4jClientLog();
    try {
      runtime =
          new ClientRuntime(
              Minecraft.getMinecraft().mcDataDir.toPath().resolve("config/perihelion/client.json"),
              log,
              client -> {
                controls = new MinecraftControls(client);
                perihelion.integration.minecraft189.module.BuiltinModules.register(client);
                gui = new perihelion.integration.minecraft189.gui.ClickGuiService(client);
                accounts =
                    new perihelion.integration.minecraft189.account.AccountService(
                        client,
                        Minecraft.getMinecraft()
                            .mcDataDir
                            .toPath()
                            .resolve("config/perihelion/accounts"));
                new ForgeAccountAdapter(accounts);
                new ForgeMainMenuAdapter(accounts, gui);
                perihelion.integration.minecraft189.config.BundledProfiles.install(client, log);
              });
      runtime.start();
      if (runtime.isRunning()) {
        adapter = new ForgeClientAdapter(runtime, log, controls);
        adapter.attach();
      }
    } catch (Throwable failure) {
      Failures.rethrowFatal(failure);
      // FML initialization must not fail because Perihelion's adapter could not attach.
      if (adapter != null) {
        try {
          adapter.stop();
        } catch (Throwable cleanup) {
          suppress(failure, cleanup);
        }
        adapter = null;
      }
      if (runtime != null) {
        try {
          runtime.stop();
        } catch (Throwable cleanup) {
          suppress(failure, cleanup);
        }
      }
      if (controls != null) {
        try {
          controls.close();
        } catch (Throwable cleanup) {
          suppress(failure, cleanup);
        }
      }
      log.error("Perihelion bootstrap failed; client integration disabled", failure);
    }
  }

  private static void suppress(Throwable failure, Throwable cleanup) {
    Failures.rethrowFatal(cleanup);
    if (failure != cleanup) failure.addSuppressed(cleanup);
  }

  public ClientRuntime runtime() {
    return runtime;
  }
}
