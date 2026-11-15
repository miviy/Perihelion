package perihelion.smoke;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import perihelion.core.context.ConnectionContext;
import perihelion.core.event.ClientTickEvent;
import perihelion.core.event.EventOwner;
import perihelion.core.event.RenderTickEvent;
import perihelion.core.event.WorldContextEvent;
import perihelion.core.event.WorldRenderEvent;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.lifecycle.LifecycleState;
import perihelion.integration.minecraft189.event.PacketReceiveEvent;
import perihelion.integration.minecraft189.event.PacketSendEvent;

/** Separate test-only mod. No fixture classes belong in the distributable. */
@Mod(
    modid = "perihelionsmoke",
    name = "Perihelion smoke probe",
    version = "1",
    clientSideOnly = true,
    dependencies = "required-after:perihelion")
public final class SmokeProbe {
  // Forge redirects System.out through Log4j, whose shutdown hook races ours.
  // Keep fixture reports independent so teardown cannot hide success or failure.
  private static final PrintStream REPORT =
      new PrintStream(new FileOutputStream(FileDescriptor.out), true);

  private enum Stage {
    TITLE,
    CREATE,
    FIRST_WORLD,
    LIVE_PLAY,
    UNLOAD,
    BETWEEN_WORLDS,
    REJOIN,
    SECOND_WORLD,
    FINISHED
  }

  private enum ConfigStage {
    NEW,
    SAVING,
    RELOADING,
    COMPLETE
  }

  private Object minecraft;
  private Object adapter;
  private Field screen, world, player;
  private Method shutdown;
  private ClientRuntime runtime;
  private EventOwner observer;
  private HudHookProbe hudProbe;
  private perihelion.smoke.LiveGameplayProfile liveProfile;
  private String scenario;
  private Stage stage = Stage.TITLE;
  private ConfigStage configStage = ConfigStage.NEW;
  private CompletableFuture<Void> configOperation;
  private long configDeadline;
  private int stableTicks, coreStarts, coreEnds, forgeEnds, worldEvents, renderEvents, worldRenders;
  private final AtomicInteger unexpectedPackets = new AtomicInteger();
  private final Intervals ticks = new Intervals(), frames = new Intervals();
  private long initialAllocated = -1;
  private long firstGeneration;
  private volatile boolean finished;
  private Throwable observerFailure;

  @Mod.EventHandler
  public void initialize(FMLInitializationEvent event) throws Exception {
    check(Loader.isModLoaded("perihelion"), "Perihelion was not loaded");
    String expectedVersion =
        (String)
            field(Class.forName("perihelion.bootstrap.forge.PerihelionVersion"), "VALUE").get(null);
    check(
        expectedVersion.equals(
            Loader.instance().getIndexedModList().get("perihelion").getVersion()),
        "Forge loaded an incorrect Perihelion version");
    marker("VERSION value=" + expectedVersion);
    scenario = System.getProperty("perihelion.smoke.scenario", "full");
    Class<?> type = Class.forName("net.minecraft.client.Minecraft");
    minecraft = method(type, new Class<?>[0], "getMinecraft", "func_71410_x").invoke(null);
    // The disposable client can lose desktop focus while its real-tick profile is running.
    Object settings = field(type, "gameSettings", "field_71474_y").get(minecraft);
    field(settings.getClass(), "pauseOnLostFocus", "field_82881_y").setBoolean(settings, false);
    screen = field(type, "currentScreen", "field_71462_r");
    world = field(type, "theWorld", "field_71441_e");
    player = field(type, "thePlayer", "field_71439_g");
    shutdown = method(type, new Class<?>[0], "shutdown", "func_71400_g");
    Object mod = Loader.instance().getIndexedModList().get("perihelion").getMod();
    runtime = (ClientRuntime) mod.getClass().getMethod("runtime").invoke(mod);
    adapter = field(mod.getClass(), "adapter").get(mod);
    check(runtime != null && runtime.isRunning(), "Core runtime is not RUNNING");
    check(adapter != null, "Forge adapter is missing");
    observer = new EventOwner("smoke-observer", failure -> observerFailure = failure);
    runtime
        .clientEvents()
        .subscribe(
            observer,
            ClientTickEvent.class,
            tick -> {
              check(runtime.tasks().isClientThread(), "Tick callback left client thread");
              if (tick == ClientTickEvent.START) coreStarts++;
              else coreEnds++;
            });
    runtime
        .clientEvents()
        .subscribe(
            observer,
            WorldContextEvent.class,
            change -> {
              check(runtime.tasks().isClientThread(), "World callback left client thread");
              worldEvents++;
              marker(
                  "WORLD generation="
                      + change.generation()
                      + " ready="
                      + change.worldReady()
                      + " connection="
                      + change.connection().kind());
            });
    runtime
        .clientEvents()
        .subscribe(
            observer,
            RenderTickEvent.class,
            render -> {
              renderEvents++;
              if (render.phase() == RenderTickEvent.Phase.END
                  && stableTicks >= 2
                  && (stage == Stage.TITLE
                      || stage == Stage.FIRST_WORLD
                      || stage == Stage.SECOND_WORLD)) frames.record();
            });
    runtime.clientEvents().subscribe(observer, WorldRenderEvent.class, render -> worldRenders++);
    runtime
        .networkEvents()
        .subscribe(
            observer, PacketReceiveEvent.class, packet -> unexpectedPackets.incrementAndGet());
    runtime
        .networkEvents()
        .subscribe(observer, PacketSendEvent.class, packet -> unexpectedPackets.incrementAndGet());
    observer.activate();
    hudProbe = new HudHookProbe(runtime, minecraft);
    runtime.clientEvents().setTimingEnabled(true);
    runtime.networkEvents().setTimingEnabled(true);
    initialAllocated = allocatedBytes();
    FMLCommonHandler.instance().bus().register(this);
    // A different JVM shutdown hook can close stdout, including its file descriptor.
    // Keep an independent report so assertion results survive that teardown race.
    final PrintStream shutdownReport =
        new PrintStream(
            new File(System.getProperty("perihelion.smoke.output"), "shutdown-report.txt"),
            "UTF-8");
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    check(
                        runtime.state() == LifecycleState.STOPPED,
                        "Shutdown hook did not stop core: " + runtime.state());
                    check(
                        runtime.tasks().isClosed() && runtime.tasks().pendingCount() == 0,
                        "Shutdown leaked queued tasks");
                    check(
                        runtime.clientEvents().subscriptionCount() == 0
                            && runtime.networkEvents().subscriptionCount() == 0,
                        "Shutdown leaked event subscriptions");
                    check(
                        new File(
                                System.getProperty("perihelion.smoke.output"),
                                "config/perihelion/client.json")
                            .isFile(),
                        "Shutdown did not produce configuration");
                    check(
                        runtime.config().store().lastFailure() == null
                            && !runtime.config().store().isDirty(),
                        "Configuration remained unsaved or failed on" + " shutdown");
                    assertReferencesCleared("shutdown");
                    check(
                        field(
                                    Class.forName("perihelion.integration.minecraft189.HookBridge"),
                                    "target")
                                .get(null)
                            == null,
                        "Shutdown retained the installed hook target");
                    marker("HOOK target=cleared");
                    marker("STOPPED subscriptions=0 queued=0 config=present");
                    shutdownReport.println(
                        "[PERIHELION_SMOKE] REFERENCES phase=shutdown world=cleared player=cleared"
                            + " manager=cleared");
                    shutdownReport.println("[PERIHELION_SMOKE] HOOK target=cleared");
                    shutdownReport.println(
                        "[PERIHELION_SMOKE] STOPPED subscriptions=0 queued=0 config=present");
                  } catch (Throwable failure) {
                    REPORT.println("[PERIHELION_SMOKE] FAIL shutdown " + failure);
                    failure.printStackTrace(REPORT);
                    shutdownReport.println("[PERIHELION_SMOKE] FAIL shutdown " + failure);
                    failure.printStackTrace(shutdownReport);
                  } finally {
                    shutdownReport.println(
                        "[PERIHELION_SMOKE] SHUTDOWN stdoutError=" + REPORT.checkError());
                    shutdownReport.close();
                  }
                },
                "Perihelion-smoke-shutdown-check"));
    marker("INITIALIZED scenario=" + scenario);
  }

  @SubscribeEvent
  public void tick(TickEvent.ClientTickEvent event) {
    if (finished || event.phase != TickEvent.Phase.END) return;
    forgeEnds++;
    try {
      check(observerFailure == null, "Core observer was quarantined: " + observerFailure);
      if (stage == Stage.LIVE_PLAY) {
        if (liveProfile.tick()) {
          liveProfile = null;
          if (scenario.equals("scaffold")) { pass(); return; }
          assertRuntime(true);
          stage = Stage.UNLOAD;
        }
        return;
      }
      if (stage == Stage.CREATE || stage == Stage.REJOIN) {
        boolean create = stage == Stage.CREATE;
        stage = create ? Stage.FIRST_WORLD : Stage.SECOND_WORLD;
        stableTicks = 0;
        launchWorld(create);
        return;
      }
      if (stage == Stage.UNLOAD) {
        Object oldHandler =
            method(minecraft.getClass(), new Class<?>[0], "getNetHandler", "func_147114_u")
                .invoke(minecraft);
        Object oldWorld = world.get(minecraft);
        Object chunk =
            method(
                    oldWorld.getClass(),
                    new Class<?>[] {int.class, int.class},
                    "getChunkFromChunkCoords",
                    "func_72964_e")
                .invoke(oldWorld, 0, 0);
        Class<?> blocksType =
            Class.forName("net.minecraft.network.play.server.S22PacketMultiBlockChange");
        Object blocks =
            blocksType
                .getConstructor(
                    int.class, short[].class, Class.forName("net.minecraft.world.chunk.Chunk"))
                .newInstance(1, new short[] {0}, chunk);
        // Let loadWorld initiate integrated-server shutdown. Sending a disconnect
        // first can make Forge stop the server before initiateShutdown queues its
        // synchronous task, leaving this test waiting on an already-stopped server.
        method(
                minecraft.getClass(),
                new Class<?>[] {Class.forName("net.minecraft.client.multiplayer.WorldClient")},
                "loadWorld",
                "func_71403_a")
            .invoke(minecraft, new Object[] {null});
        Class<?> moveType = Class.forName("net.minecraft.network.play.server.S14PacketEntity");
        Object move =
            Class.forName(
                    "net.minecraft.network.play.server.S14PacketEntity$S15PacketEntityRelMove")
                .getConstructor(int.class, byte.class, byte.class, byte.class, boolean.class)
                .newInstance(-887799, (byte) 1, (byte) 0, (byte) 0, false);
        Class<?> teleportType =
            Class.forName("net.minecraft.network.play.server.S18PacketEntityTeleport");
        Object teleport =
            teleportType
                .getConstructor(
                    int.class,
                    int.class,
                    int.class,
                    int.class,
                    byte.class,
                    byte.class,
                    boolean.class)
                .newInstance(-887799, 0, 0, 0, (byte) 0, (byte) 0, false);
        method(
                oldHandler.getClass(),
                new Class<?>[] {moveType},
                "handleEntityMovement",
                "func_147259_a")
            .invoke(oldHandler, move);
        method(
                oldHandler.getClass(),
                new Class<?>[] {teleportType},
                "handleEntityTeleport",
                "func_147275_a")
            .invoke(oldHandler, teleport);
        Class<?> headType =
            Class.forName("net.minecraft.network.play.server.S19PacketEntityHeadLook");
        method(
                oldHandler.getClass(),
                new Class<?>[] {headType},
                "handleEntityHeadLook",
                "func_147267_a")
            .invoke(oldHandler, headType.newInstance());
        marker("STALE_ENTITY_PACKETS checks=3 PASS");
        Class<?> slotType = Class.forName("net.minecraft.network.play.server.S2FPacketSetSlot");
        Object slot =
            slotType
                .getConstructor(int.class, int.class, Class.forName("net.minecraft.item.ItemStack"))
                .newInstance(0, 36, null);
        Class<?> correctionType =
            Class.forName("net.minecraft.network.play.server.S08PacketPlayerPosLook");
        Object correction =
            correctionType
                .getConstructor(
                    double.class,
                    double.class,
                    double.class,
                    float.class,
                    float.class,
                    java.util.Set.class)
                .newInstance(0d, 0d, 0d, 0f, 0f, java.util.Collections.emptySet());
        method(
                oldHandler.getClass(),
                new Class<?>[] {blocksType},
                "handleMultiBlockChange",
                "func_147287_a")
            .invoke(oldHandler, blocks);
        method(oldHandler.getClass(), new Class<?>[] {slotType}, "handleSetSlot", "func_147266_a")
            .invoke(oldHandler, slot);
        method(
                oldHandler.getClass(),
                new Class<?>[] {correctionType},
                "handlePlayerPosLook",
                "func_147258_a")
            .invoke(oldHandler, correction);
        marker("STALE_WORLD_PACKETS checks=3 PASS");
        Object menu = Class.forName("net.minecraft.client.gui.GuiMainMenu").newInstance();
        method(
                minecraft.getClass(),
                new Class<?>[] {Class.forName("net.minecraft.client.gui.GuiScreen")},
                "displayGuiScreen",
                "func_147108_a")
            .invoke(minecraft, menu);
        stage = Stage.BETWEEN_WORLDS;
        stableTicks = 0;
        return;
      }
      boolean expectTitle = stage == Stage.TITLE || stage == Stage.BETWEEN_WORLDS;
      Object current = screen.get(minecraft);
      boolean ready =
          expectTitle
              ? current != null
                  && ("net.minecraft.client.gui.GuiMainMenu".equals(current.getClass().getName())
                      || "perihelion.integration.minecraft189.gui.menu.MainMenuScreen"
                          .equals(current.getClass().getName()))
              : world.get(minecraft) != null
                  && player.get(minecraft) != null
                  && runtime.worldReady();
      stableTicks = ready ? stableTicks + 1 : 0;
      if (ready && stableTicks >= 2) ticks.record();
      if (ready && !expectTitle && stableTicks >= 2) verifyConfigRoundTrip();
      if (stage == Stage.BETWEEN_WORLDS && stableTicks >= 20) {
        assertRuntime(false);
        assertReferencesCleared("unload");
        check(
            runtime.tasks().generation() > firstGeneration,
            "World unload did not advance session generation");
        marker("UNLOADED generation=" + runtime.tasks().generation());
        ticks.reset();
        frames.reset();
        stage = Stage.REJOIN;
      }
    } catch (Throwable failure) {
      fail(failure);
    }
  }

  @SubscribeEvent
  public void render(TickEvent.RenderTickEvent event) {
    if (finished || event.phase != TickEvent.Phase.END) return;
    try {
      hudProbe.frameEnd();
      if (stage == Stage.LIVE_PLAY) {
        liveProfile.frame();
        return;
      }
      if (stage == Stage.TITLE && stableTicks >= 20) {
        assertRuntime(false);
        capture("title-screen.png");
        if (Boolean.getBoolean("perihelion.smoke.inlineEditing")) {
          Class.forName("perihelion.smoke.InlineEditingProbe")
              .getMethod("run", ClientRuntime.class, Object.class).invoke(null, runtime, minecraft);
          pass();
          return;
        }
        if (scenario.equals("targethud") || scenario.equals("invmove") || scenario.equals("b4") || scenario.equals("scaffold") || scenario.equals("beddefender") || scenario.equals("breaker")) {
          // Keep focused gameplay checks independent of unrelated visual fixtures.
          stage = Stage.CREATE;
          return;
        }
        Class.forName("perihelion.smoke.MainMenuHookProbe")
            .getMethod("run", Object.class)
            .invoke(null, minecraft);
        Class.forName("perihelion.smoke.NewVisualsProbe")
            .getMethod("title", ClientRuntime.class, Object.class)
            .invoke(null, runtime, minecraft);
        Class.forName("perihelion.smoke.MenuFamilyHookProbe")
            .getMethod("run", Object.class)
            .invoke(null, minecraft);
        Class.forName("perihelion.smoke.RenderHookProbe").getMethod("run").invoke(null);
        capture("render-helpers.png");
        Class.forName("perihelion.smoke.AccountHookProbe")
            .getMethod("title", ClientRuntime.class, Object.class)
            .invoke(null, runtime, minecraft);
        marker("TITLE stableTicks=" + stableTicks);
        profile("title");
        if (scenario.equals("packets") || scenario.equals("full")) {
          Class.forName("perihelion.smoke.NetworkHookProbe").getMethod("run").invoke(null);
          check(runtime.isRunning(), "Packet fixture did not restore running runtime");
        }
        if (scenario.equals("world")
            || scenario.equals("ui")
            || scenario.equals("yuri-update")
            || scenario.equals("b4")
            || scenario.equals("full")) {
          ticks.reset();
          frames.reset();
          stage = Stage.CREATE;
        } else pass();
      } else if ((stage == Stage.FIRST_WORLD || stage == Stage.SECOND_WORLD)
          && stableTicks >= 40
          && configStage == ConfigStage.COMPLETE
          && hudProbe.complete()) {
        assertRuntime(true);
        localCommand("/perihelion status");
        localCommand("/perihelion invalid-smoke-command");
        capture(stage == Stage.FIRST_WORLD ? "first-world-screen.png" : "second-world-screen.png");
        if (stage == Stage.FIRST_WORLD) {
          hudProbe.close(); // Real overlay assertions finish before synthetic gameplay/GUI draws.
          if (scenario.equals("targethud")) {
            perihelion.smoke.TargetHudDesignProbe.run(runtime, minecraft);
            pass();
            return;
          }
          if (scenario.equals("breaker")) {
            perihelion.smoke.BreakerLayerProbe.run(runtime);
            pass();
            return;
          }
          if (scenario.equals("beddefender")) {
            perihelion.smoke.BuildingAuditProbe.run(runtime);
            perihelion.smoke.BedDefenderRotationProbe.run(runtime);
            pass();
            return;
          }
          if (scenario.equals("scaffold")) {
            perihelion.smoke.ScaffoldMovementProbe.run(runtime);
            liveProfile = new perihelion.smoke.LiveGameplayProfile(runtime,true);
            stage = Stage.LIVE_PLAY;
            return;
          }
          if (scenario.equals("yuri-update")) {
            perihelion.smoke.YuriUpdateProbe.run(runtime);
            pass();
            return;
          }
          if (scenario.equals("invmove")) {
            perihelion.smoke.InvMoveProbe.run(runtime);
            pass();
            return;
          }
          if (scenario.equals("b4") || scenario.equals("full")) {
            // These probes own explicit loopback PLAY sessions. Account their controlled
            // interval independently before the pre-existing gameplay fixture baseline.
            int beforeB4Packets = unexpectedPackets.get();
            perihelion.smoke.NoFallPacketParityProbe.run(runtime);
            perihelion.smoke.AutoAppleOwnershipProbe.run(runtime);
            perihelion.smoke.VisualOwnershipProbe.run(runtime);
            perihelion.smoke.InventoryTimingParityProbe.run(runtime);
            perihelion.smoke.PacketSourceOwnershipProbe.run(runtime);
            perihelion.smoke.B4ManualCombatProbe.run(runtime);
            perihelion.smoke.B4BuildingProbe.run(runtime);
            perihelion.smoke.B4CombatHookProbe.run(runtime);
            perihelion.smoke.B4MovementBedWarsProbe.run(runtime);
            perihelion.smoke.VisualAuditProbe.run(runtime);
            perihelion.smoke.BuildingAuditProbe.run(runtime);
            perihelion.smoke.LongJumpParityProbe.run(runtime);
            perihelion.smoke.PredictionParityProbe.run(runtime);
            // B4's two real runTick calls dispatch both Forge and core events;
            // retain them in the normal bridge counts.
            marker("B4_CONTROLLED_PACKETS events=" + (unexpectedPackets.get() - beforeB4Packets));
            unexpectedPackets.set(beforeB4Packets);
            if (scenario.equals("b4")) {
              pass();
              return;
            }
          }
          Class.forName("perihelion.smoke.MenuFamilyHookProbe")
              .getMethod("world", Object.class)
              .invoke(null, minecraft);
          if (!scenario.equals("ui")) {
            Class.forName("perihelion.smoke.ControlHookProbe").getMethod("run").invoke(null);
            check(runtime.isRunning(), "Control fixture did not restore running runtime");
            Class.forName("perihelion.smoke.ModuleHookProbe")
                .getMethod("run", ClientRuntime.class)
                .invoke(null, runtime);

            int packetEventsBeforeFixture = unexpectedPackets.get();
            Class.forName("perihelion.smoke.GameplayHookProbe")
                .getMethod("run", ClientRuntime.class)
                .invoke(null, runtime);
            // Hit Select's phase fixture explicitly posts core events without a Forge tick.
            // Remove exactly those measured injections before asserting the native bridge.
            coreStarts -= perihelion.smoke.YuriLatestProbe.syntheticStarts();
            coreEnds -= perihelion.smoke.YuriLatestProbe.syntheticEnds();
            check(
                unexpectedPackets.get()
                    == packetEventsBeforeFixture
                        + 4
                        + perihelion.smoke.CompatibilityQolProbe.networkEvents()
                        + perihelion.smoke.DisplaceHookProbe.networkEvents()
                        + perihelion.smoke.SecondParityProbe.networkEvents()
                        + perihelion.smoke.MovementParityProbe.networkEvents()
                        + perihelion.smoke.FinalParityProbe.networkEvents()
                        + perihelion.smoke.RecoveryParityProbe.networkEvents()
                        + perihelion.smoke.FireballParityProbe.networkEvents()
                        + perihelion.smoke.PacketPositionProbe.networkEvents(),
                "Gameplay fixture must account for snapshot, Clutch and controlled TCP events:"
                    + " before="
                    + packetEventsBeforeFixture
                    + " actual="
                    + unexpectedPackets.get()
                    + " TCP="
                    + perihelion.smoke.CompatibilityQolProbe.networkEvents()
                    + " Hypixel="
                    + perihelion.smoke.DisplaceHookProbe.networkEvents()
                    + " Second="
                    + perihelion.smoke.SecondParityProbe.networkEvents()
                    + " Movement="
                    + perihelion.smoke.MovementParityProbe.networkEvents()
                    + " Final="
                    + perihelion.smoke.FinalParityProbe.networkEvents()
                    + " Recovery="
                    + perihelion.smoke.RecoveryParityProbe.networkEvents()
                    + " Fireball="
                    + perihelion.smoke.FireballParityProbe.networkEvents()
                    + " Position="
                    + perihelion.smoke.PacketPositionProbe.networkEvents());
            unexpectedPackets.addAndGet(
                -4
                    - perihelion.smoke.CompatibilityQolProbe.networkEvents()
                    - perihelion.smoke.DisplaceHookProbe.networkEvents()
                    - perihelion.smoke.SecondParityProbe.networkEvents()
                    - perihelion.smoke.MovementParityProbe.networkEvents()
                    - perihelion.smoke.FinalParityProbe.networkEvents()
                    - perihelion.smoke.RecoveryParityProbe.networkEvents()
                    - perihelion.smoke.FireballParityProbe.networkEvents()
                    - perihelion.smoke.PacketPositionProbe.networkEvents());
          }
          perihelion.smoke.HudStyleProbe.checkpoint(runtime, minecraft);
          perihelion.integration.minecraft189.gui.ClickGuiModule guiPreferences =
              (perihelion.integration.minecraft189.gui.ClickGuiModule)
                  runtime.modules().require("clickgui").module();
          perihelion.integration.minecraft189.gui.ClickGuiModule.Visuals savedVisuals =
              guiPreferences.visuals.get();
          runtime
              .modules()
              .setSetting(
                  "clickgui",
                  guiPreferences.visuals,
                  perihelion.integration.minecraft189.gui.ClickGuiModule.Visuals.OLD);
          try {
            Class.forName("perihelion.smoke.GuiHookProbe")
                .getMethod("run", ClientRuntime.class)
                .invoke(null, runtime);
            Class.forName("perihelion.smoke.ProductionHudProbe")
                .getMethod("run", ClientRuntime.class)
                .invoke(null, runtime);
          } finally {
            runtime.modules().setSetting("clickgui", guiPreferences.visuals, savedVisuals);
          }
          Class.forName("perihelion.smoke.NewVisualsProbe")
              .getMethod("world", ClientRuntime.class, Object.class)
              .invoke(null, runtime, minecraft);
          firstGeneration = runtime.tasks().generation();
          marker("JOINED generation=" + firstGeneration + " stableTicks=" + stableTicks);
          profile("first-world");
          ticks.reset();
          frames.reset();
          if (scenario.equals("ui")) stage = Stage.UNLOAD;
          else {
            liveProfile = new perihelion.smoke.LiveGameplayProfile(runtime);
            stage = Stage.LIVE_PLAY;
          }
        } else {
          check(
              runtime.tasks().generation() > firstGeneration,
              "Rejoin retained previous session generation");
          check(worldEvents >= 3, "Missing core world transition callbacks: " + worldEvents);
          marker(
              "REJOINED generation="
                  + runtime.tasks().generation()
                  + " stableTicks="
                  + stableTicks);
          profile("second-world");
          pass();
        }
      }
    } catch (Throwable failure) {
      fail(failure);
    }
  }

  private void assertRuntime(boolean worldExpected) {
    check(runtime.isRunning(), "Runtime stopped unexpectedly: " + runtime.state());
    check(runtime.modules().entries().size() == 97, "Expected 97 production modules");
    check(runtime.worldReady() == worldExpected, "Core world readiness disagrees with Minecraft");
    check(
        runtime.connection().kind()
            == (worldExpected
                ? ConnectionContext.Kind.SINGLEPLAYER
                : ConnectionContext.Kind.DISCONNECTED),
        "Incorrect connection context " + runtime.connection().kind());
    check(!runtime.connection().isHypixel(), "Offline/local test detected as Hypixel");
    check(
        coreStarts > 0 && coreEnds == forgeEnds && coreStarts >= coreEnds,
        "Tick bridge counts differ: start="
            + coreStarts
            + " end="
            + coreEnds
            + " Forge="
            + forgeEnds);
    check(
        renderEvents > 0 && (!worldExpected || worldRenders > 0),
        "Core render hooks were not invoked");
    check(
        unexpectedPackets.get() == 0,
        "Local/integrated-server traffic reached multiplayer packet hooks");
    check(
        runtime.clientEvents().subscriptionCount() == 7 + hudProbe.subscriptions()
            && runtime.networkEvents().subscriptionCount() == 2,
        "Unexpected listeners with default-disabled modules");
    List<String> output = new ArrayList<>();
    runtime.commands().execute("status", output::add);
    check(
        !output.isEmpty() && output.get(0).contains("RUNNING"), "Status command failed: " + output);
    output.clear();
    runtime.commands().execute("modules", output::add);
    check(
        output.size() == runtime.modules().entries().size(),
        "Module listing command failed: " + output);
  }

  private void localCommand(String text) throws Exception {
    Class<?> type = Class.forName("net.minecraftforge.client.ClientCommandHandler");
    Object handler = type.getField("instance").get(null);
    Method execute =
        method(
            type,
            new Class<?>[] {Class.forName("net.minecraft.command.ICommandSender"), String.class},
            "executeCommand",
            "func_71556_a");
    check(
        ((Number) execute.invoke(handler, player.get(minecraft), text)).intValue() == 1,
        "Client command was not consumed: " + text);
    marker("COMMAND consumed=" + text);
  }

  private void assertReferencesCleared(String phase) throws Exception {
    for (String name : new String[] {"lastWorld", "lastPlayer", "lastManager"}) {
      check(
          field(adapter.getClass(), name).get(adapter) == null,
          phase + " retained adapter." + name);
    }
    marker("REFERENCES phase=" + phase + " world=cleared player=cleared manager=cleared");
  }

  /** Poll completed futures from ordinary ticks; never wait for disk I/O on the game thread. */
  private void verifyConfigRoundTrip() {
    if (configStage == ConfigStage.COMPLETE) return;
    if (configStage == ConfigStage.NEW) {
      configDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
      configOperation = runtime.config().saveNow();
      configStage = ConfigStage.SAVING;
      return;
    }
    check(System.nanoTime() < configDeadline, "In-world configuration roundtrip timed out");
    if (!configOperation.isDone()) return;
    configOperation.join(); // Already complete: propagates failures without blocking.
    if (configStage == ConfigStage.SAVING) {
      check(
          !runtime.config().store().isDirty() && runtime.config().store().lastFailure() == null,
          "In-world save completed without a clean durable snapshot");
      configOperation = runtime.config().reload();
      configStage = ConfigStage.RELOADING;
    } else {
      check(!runtime.config().isReloading(), "Completed reload retained its busy state");
      check(runtime.modules().entries().size() == 97, "Reload changed production definitions");
      configStage = ConfigStage.COMPLETE;
      marker("CONFIG saved=true reloaded=true phase=" + stage + " nonblocking=true");
    }
  }

  private void launchWorld(boolean create) throws Exception {
    configStage = ConfigStage.NEW;
    configOperation = null;
    Class<?> settingsType = Class.forName("net.minecraft.world.WorldSettings");
    Object settings = null;
    if (create) {
      Class<?> gameType = Class.forName("net.minecraft.world.WorldSettings$GameType");
      Class<?> worldType = Class.forName("net.minecraft.world.WorldType");
      Object creative =
          method(gameType, new Class<?>[] {int.class}, "getByID", "func_77146_a").invoke(null, 1);
      Object flat = field(worldType, "FLAT", "field_77138_c").get(null);
      settings =
          settingsType
              .getConstructor(long.class, gameType, boolean.class, boolean.class, worldType)
              .newInstance(8675309L, creative, false, false, flat);
    }
    marker(create ? "CREATING isolated flat world" : "REJOINING isolated flat world");
    method(
            minecraft.getClass(),
            new Class<?>[] {String.class, String.class, settingsType},
            "launchIntegratedServer",
            "func_71371_a")
        .invoke(minecraft, "PerihelionSmoke", "Perihelion smoke test", settings);
  }

  private void capture(String name) throws Exception {
    int width = Display.getWidth(), height = Display.getHeight();
    ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
    GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
    GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
    BufferedImage screenshot = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < height; y++)
      for (int x = 0; x < width; x++) {
        int offset = (x + y * width) * 4;
        int rgb =
            (pixels.get(offset) & 255) << 16
                | (pixels.get(offset + 1) & 255) << 8
                | pixels.get(offset + 2) & 255;
        screenshot.setRGB(x, height - y - 1, rgb);
      }
    check(
        ImageIO.write(
            screenshot, "png", new File(System.getProperty("perihelion.smoke.output"), name)),
        "PNG writer unavailable");
  }

  private void pass() throws Exception {
    hudProbe.close();
    observer.close();
    marker(
        "PROFILE cumulativeCoreDispatches="
            + runtime.clientEvents().measuredDispatches()
            + " cumulativeCoreDispatchNanos="
            + runtime.clientEvents().measuredNanos()
            + " clientThreadAllocatedBytes="
            + (initialAllocated < 0 ? "unsupported" : allocatedBytes() - initialAllocated)
            + " subscriptionsAfterProbeClose="
            + (runtime.clientEvents().subscriptionCount()
                + runtime.networkEvents().subscriptionCount())
            + " queued="
            + runtime.tasks().pendingCount()
            + " includesMinecraftAndProbe=true");
    marker("PASS scenario=" + scenario + " ticks=" + forgeEnds + " worldEvents=" + worldEvents);
    finished = true;
    stage = Stage.FINISHED;
    if (!Boolean.getBoolean("perihelion.smoke.stayOpen")) shutdown.invoke(minecraft);
  }

  private void fail(Throwable failure) {
    finished = true;
    REPORT.println("[PERIHELION_SMOKE] FAIL " + failure);
    failure.printStackTrace(REPORT);
    try {
      if (liveProfile != null) liveProfile.close();
      if (hudProbe != null) hudProbe.close();
      if (observer != null) observer.close();
      if (shutdown != null) shutdown.invoke(minecraft);
    } catch (Exception ignored) {
      /* Launcher timeout bounds a failed cleanup. */
    }
  }

  private static void marker(String message) {
    REPORT.println("[PERIHELION_SMOKE] " + message);
  }

  private void profile(String phase) {
    marker(
        "PROFILE phase="
            + phase
            + " tickIntervals="
            + ticks.count
            + " tickMeanMs="
            + ticks.meanMillis()
            + " tickMaxMs="
            + ticks.max / 1000000.0
            + " frameIntervals="
            + frames.count
            + " frameMeanMs="
            + frames.meanMillis()
            + " frameMaxMs="
            + frames.max / 1000000.0
            + " liveHeapBytes="
            + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()));
  }

  private static long allocatedBytes() {
    try {
      Class<?> bean = Class.forName("com.sun.management.ThreadMXBean");
      return ((Number)
              bean.getMethod("getThreadAllocatedBytes", long.class)
                  .invoke(ManagementFactory.getThreadMXBean(), Thread.currentThread().getId()))
          .longValue();
    } catch (ReflectiveOperationException | RuntimeException unsupported) {
      return -1;
    }
  }

  private static final class Intervals {
    long previous, total, count, max;

    void record() {
      long now = System.nanoTime();
      if (previous != 0) {
        long elapsed = now - previous;
        total += elapsed;
        count++;
        max = Math.max(max, elapsed);
      }
      previous = now;
    }

    void reset() {
      previous = total = count = max = 0;
    }

    double meanMillis() {
      return count == 0 ? 0 : total / (double) count / 1000000.0;
    }
  }

  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private static Method method(Class<?> type, Class<?>[] parameters, String... names)
      throws Exception {
    for (String name : names)
      try {
        return type.getMethod(name, parameters);
      } catch (NoSuchMethodException ignored) {
      }
    throw new NoSuchMethodException(type.getName() + " " + java.util.Arrays.toString(names));
  }

  private static Field field(Class<?> type, String... names) throws Exception {
    for (String name : names) {
      try {
        return type.getField(name);
      } catch (NoSuchFieldException ignored) {
      }
      try {
        Field declared = type.getDeclaredField(name);
        declared.setAccessible(true);
        return declared;
      } catch (NoSuchFieldException ignored) {
      }
    }
    throw new NoSuchFieldException(type.getName() + " " + java.util.Arrays.toString(names));
  }
}
