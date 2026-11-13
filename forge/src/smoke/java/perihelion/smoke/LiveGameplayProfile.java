package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;
import static perihelion.smoke.RotationPipelineProbe.*;

import io.netty.channel.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import perihelion.core.control.*;
import perihelion.core.event.EventOwner;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.integration.minecraft189.HookBridge;

/** Actual game ticks and rendered frames while walking, then bridging with populated visuals. */
public final class LiveGameplayProfile implements AutoCloseable {
  private final ClientRuntime runtime;
  private final boolean tellyOnly;
  private final Object game, player, world, serverWorld, settings, inventory;
  private final Method getState, putState;
  private final Map<Object, Object> saved = new LinkedHashMap<>(),
      serverSaved = new LinkedHashMap<>();
  private final Map<Field, Object> playerSaved = new LinkedHashMap<>();
  private final Map<Field, Object> serverPlayerSaved = new LinkedHashMap<>();
  private Object serverPlayer, serverInventory;
  private Object[] serverItems, savedServerItems;
  private int serverSelected;
  private double serverX, serverY, serverZ;
  private final Object[] items, savedItems;
  private final Object forwardKey;
  private final boolean oldPressed, oldTiming;
  private final int selected;
  private final int originalFrameLimit;
  private final double px, py, pz;
  private final int x, y, z;
  private final EventOwner owner =
      new EventOwner(
          "live-gameplay-profile",
          failure -> {
            throw new AssertionError(failure);
          });
  private final List<AutoCloseable> resources = new ArrayList<>();
  private final List<Long> frames = new ArrayList<>();
  private ControlRequest<Boolean> forward;
  private long lastFrame, started, dispatchNanos, dispatches, allocated;
  private int ticks, phase, frameCount;
  private boolean closed;
  private Channel diagnosticChannel;
  private String diagnosticHandler;
  private final AtomicInteger placementPackets = new AtomicInteger(),
      corrections = new AtomicInteger();
  private static final String[] MODULES = {"hud", "scaffold", "blockesp", "esp", "nameprotect"};

  public LiveGameplayProfile(ClientRuntime runtime) throws Exception {
    this(runtime, false);
  }

  public LiveGameplayProfile(ClientRuntime runtime, boolean tellyOnly) throws Exception {
    this.runtime = runtime;
    this.tellyOnly = tellyOnly;
    game =
        method(Class.forName("net.minecraft.client.Minecraft"), "getMinecraft", "func_71410_x")
            .invoke(null);
    player = field(game.getClass(), "thePlayer", "field_71439_g").get(game);
    world = field(game.getClass(), "theWorld", "field_71441_e").get(game);
    settings = field(game.getClass(), "gameSettings", "field_71474_y").get(game);
    originalFrameLimit =
        field(settings.getClass(), "limitFramerate", "field_74350_i").getInt(settings);
    field(settings.getClass(), "limitFramerate", "field_74350_i").setInt(settings, 60);
    inventory = field(player.getClass(), "inventory", "field_71071_by").get(player);
    items = (Object[]) field(inventory.getClass(), "mainInventory", "field_70462_a").get(inventory);
    savedItems = items.clone();
    selected = field(inventory.getClass(), "currentItem", "field_70461_c").getInt(inventory);
    forwardKey = field(settings.getClass(), "keyBindForward", "field_74351_w").get(settings);
    oldPressed = field(forwardKey.getClass(), "pressed", "field_74513_e").getBoolean(forwardKey);
    px = number(player, "posX", "field_70165_t");
    py = number(player, "posY", "field_70163_u");
    pz = number(player, "posZ", "field_70161_v");
    x = (int) Math.floor(px);
    y = Math.min(200, (int) Math.floor(py) + 20);
    z = (int) Math.floor(pz);
    for (String[] names :
        new String[][] {
          {"rotationYaw", "field_70177_z"},
          {"rotationPitch", "field_70125_A"},
          {"motionX", "field_70159_w"},
          {"motionY", "field_70181_x"},
          {"motionZ", "field_70179_y"},
          {"onGround", "field_70122_E"},
          {"fallDistance", "field_70143_R"}
        }) {
      Field f = field(player.getClass(), names);
      playerSaved.put(f, f.get(player));
    }
    Class<?> pos = Class.forName("net.minecraft.util.BlockPos"),
        state = Class.forName("net.minecraft.block.state.IBlockState");
    getState = method(world.getClass(), new Class<?>[] {pos}, "getBlockState", "func_180495_p");
    putState =
        method(
            world.getClass(),
            new Class<?>[] {pos, state, int.class},
            "setBlockState",
            "func_180501_a");
    Object server = method(game.getClass(), "getIntegratedServer", "func_71401_C").invoke(game);
    serverWorld =
        method(
                server.getClass(),
                new Class<?>[] {int.class},
                "worldServerForDimension",
                "func_71218_a")
            .invoke(server, 0);
    oldTiming = runtime.clientEvents().isTimingEnabled();
    runtime.clientEvents().setTimingEnabled(true);
    try {
      Object handler = method(game.getClass(), "getNetHandler", "func_147114_u").invoke(game);
      Object network =
          method(handler.getClass(), "getNetworkManager", "func_147298_b").invoke(handler);
      diagnosticChannel =
          (Channel) field(network.getClass(), "channel", "field_150746_k").get(network);
      diagnosticHandler = "perihelion-live-profile-" + System.identityHashCode(this);
      diagnosticChannel
          .pipeline()
          .addLast(
              diagnosticHandler,
              new ChannelDuplexHandler() {
                @Override
                public void write(
                    ChannelHandlerContext context, Object message, ChannelPromise promise)
                    throws Exception {
                  if (message.getClass().getSimpleName().equals("C08PacketPlayerBlockPlacement"))
                    placementPackets.incrementAndGet();
                  context.write(message, promise);
                }

                @Override
                public void channelRead(ChannelHandlerContext context, Object message)
                    throws Exception {
                  if (message.getClass().getSimpleName().equals("S08PacketPlayerPosLook"))
                    corrections.incrementAndGet();
                  context.fireChannelRead(message);
                }
              });
      for (String id : MODULES) runtime.modules().setEnabled(id, false);
      for (int dx = -3; dx <= 3; dx++)
        for (int dz = -2; dz <= (tellyOnly ? 40 : 16); dz++)
          for (int dy = 0; dy <= 3; dy++) {
            Object point = pos(x + dx, y + dy, z + dz);
            saved.put(point, getState.invoke(world, point));
            putState.invoke(world, point, dy == 0 ? state("stone") : state("air"), 2);
          }
      serverTask(
          () -> {
            Method read =
                method(
                    serverWorld.getClass(), new Class<?>[] {pos}, "getBlockState", "func_180495_p");
            for (Object point : saved.keySet())
              serverSaved.put(point, read.invoke(serverWorld, point));
            Object manager =
                method(server.getClass(), "getConfigurationManager", "func_71203_ab")
                    .invoke(server);
            String name =
                (String) method(player.getClass(), "getName", "func_70005_c_").invoke(player);
            serverPlayer =
                method(
                        manager.getClass(),
                        new Class<?>[] {String.class},
                        "getPlayerByUsername",
                        "func_152612_a")
                    .invoke(manager, name);
            serverInventory =
                field(serverPlayer.getClass(), "inventory", "field_71071_by").get(serverPlayer);
            serverItems =
                (Object[])
                    field(serverInventory.getClass(), "mainInventory", "field_70462_a")
                        .get(serverInventory);
            savedServerItems = serverItems.clone();
            serverSelected =
                field(serverInventory.getClass(), "currentItem", "field_70461_c")
                    .getInt(serverInventory);
            serverX = number(serverPlayer, "posX", "field_70165_t");
            serverY = number(serverPlayer, "posY", "field_70163_u");
            serverZ = number(serverPlayer, "posZ", "field_70161_v");
            for (Field f : playerSaved.keySet()) {
              Field other = field(serverPlayer.getClass(), f.getName());
              serverPlayerSaved.put(other, other.get(serverPlayer));
            }
          });
      owner.activate();
      forward = runtime.controls().scope(owner, resources::add).key(InputKey.FORWARD, 2000);
      forward.set(true);
      field(forwardKey.getClass(), "pressed", "field_74513_e").setBoolean(forwardKey, true);
      for (int i = 0; i < (tellyOnly ? 0 : 4); i++) {
        Class<?> profileType = Class.forName("com.mojang.authlib.GameProfile");
        Object profile =
            profileType
                .getConstructor(UUID.class, String.class)
                .newInstance(new UUID(885500, i), "Profile" + i);
        Object peer =
            Class.forName("net.minecraft.client.entity.EntityOtherPlayerMP")
                .getConstructor(Class.forName("net.minecraft.world.World"), profileType)
                .newInstance(world, profile);
        position(peer, x + (i % 2 == 0 ? -1.5 : 2.5), y + 1, z + 2 + i * 2);
        method(
                world.getClass(),
                new Class<?>[] {int.class, Class.forName("net.minecraft.entity.Entity")},
                "addEntityToWorld",
                "func_73027_a")
            .invoke(world, -885500 - i, peer);
      }
      if (!tellyOnly)
        for (int dz : new int[] {3, 8, 13})
          putState.invoke(world, pos(x + 2, y + 1, z + dz), state("chest"), 2);
      resetPlayer();
      startPhase();
    } catch (Throwable failure) {
      close();
      throw failure;
    }
  }

  public boolean tick() throws Exception {
    if (closed) return true;
    if (tellyOnly) return tellyTick();
    ticks++;
    if (phase > 0 && ticks % 5 == 0) trace();
    if (ticks < (phase == 0 ? 35 : 45)) return false;
    report();
    if (phase > 0) {
      int placed = 0;
      for (int dz = 2; dz <= 16; dz++)
        if (!state("air").equals(getState.invoke(world, pos(x, y, z + dz)))) placed++;
      double distance = number(player, "posZ", "field_70161_v") - z - .5;
      if (placed < 2 || distance < 2) {
        serverTask(
            () ->
                System.out.println(
                    "PERIHELION_SMOKE LIVE_SERVER phase="
                        + phase
                        + " position="
                        + number(serverPlayer, "posX", "field_70165_t")
                        + ","
                        + number(serverPlayer, "posY", "field_70163_u")
                        + ","
                        + number(serverPlayer, "posZ", "field_70161_v")
                        + " selected="
                        + field(serverInventory.getClass(), "currentItem", "field_70461_c")
                            .getInt(serverInventory)));
        throw new AssertionError(
            "Live profile must actually move and bridge: distance="
                + distance
                + " blocks="
                + placed);
      }
      System.out.println(
          "PERIHELION_SMOKE LIVE_BRIDGE phase="
              + phase
              + " distance="
              + distance
              + " bridgeBlocks="
              + placed
              + " PASS");
      if (phase == 2) {
        System.out.println("PERIHELION_SMOKE LIVE_PLAY ticks=125 frames=" + frameCount + " PASS");
        close();
        return true;
      }
    }
    phase++;
    runtime.modules().setEnabled("scaffold", false);
    HookBridge.gameControls().apply();
    field(settings.getClass(), "limitFramerate", "field_74350_i")
        .setInt(settings, phase == 2 ? 10 : 60);
    for (int dx = -1; dx <= 1; dx++)
      for (int dz = 2; dz <= 16; dz++)
        putState.invoke(world, pos(x + dx, y, z + dz), state("air"), 2);
    resetPlayer();
    set(runtime, "scaffold", "auto-disable", "false");
    set(runtime, "scaffold", "mode", "HYPIXEL");
    set(runtime, "scaffold", "ray-cast", "STRICT");
    set(runtime, "scaffold", "keep-y", "false");
    set(runtime, "scaffold", "auto-jump", "false");
    for (String id : MODULES) runtime.modules().setEnabled(id, true);
    startPhase();
    return false;
  }

  /** Real ticks and accepted server blocks, separate from the synthetic PLAY-peer matrix. */
  private boolean tellyTick() throws Exception {
    ticks++;
    if (phase > 0 && ticks % 40 == 0) trace();
    if (ticks < (phase == 0 ? 35 : 160)) return false;
    if (phase > 0) {
      int[] counts = {0, 0};
      for (int dx = -3; dx <= 3; dx++)
        for (int dz = 2; dz <= 40; dz++)
          if (!state("air").equals(getState.invoke(world, pos(x + dx, y, z + dz)))) counts[0]++;
      serverTask(
          () -> {
            Method read =
                method(
                    serverWorld.getClass(),
                    new Class<?>[] {Class.forName("net.minecraft.util.BlockPos")},
                    "getBlockState",
                    "func_180495_p");
            for (int dx = -3; dx <= 3; dx++)
              for (int dz = 2; dz <= 40; dz++)
                if (!state("air").equals(read.invoke(serverWorld, pos(x + dx, y, z + dz))))
                  counts[1]++;
          });
      double travel = number(player, "posZ", "field_70161_v") - z - .5;
      double height = number(player, "posY", "field_70163_u");
      System.out.println(
          "[PERIHELION_SMOKE] TELLY_SERVER phase="
              + phase
              + " ticks="
              + ticks
              + " distance="
              + travel
              + " clientBlocks="
              + counts[0]
              + " acceptedBlocks="
              + counts[1]
              + " height="
              + height);
      if (travel < 12 || counts[1] < 10 || height < y + .9)
        throw new AssertionError("Telly must sustain a server-confirmed bridge");
      if (phase == 4) {
        System.out.println("[PERIHELION_SMOKE] TELLY_SERVER cases=4 ticksPerCase=160 PASS");
        close();
        return true;
      }
    }
    phase++;
    runtime.modules().setEnabled("scaffold", false);
    HookBridge.gameControls().apply();
    for (int dx = -3; dx <= 3; dx++)
      for (int dz = 2; dz <= 40; dz++)
        putState.invoke(world, pos(x + dx, y, z + dz), state("air"), 2);
    resetPlayer();
    set(runtime, "scaffold", "mode", "TELLY");
    set(runtime, "scaffold", "hypixel-telly", String.valueOf(phase <= 2));
    set(runtime, "scaffold", "ray-cast", phase % 2 == 1 ? "NORMAL" : "STRICT");
    set(runtime, "scaffold", "keep-y", "true");
    set(runtime, "scaffold", "sprint", "NONE");
    set(runtime, "scaffold", "tower", "NONE");
    set(runtime, "scaffold", "expand", "0");
    set(runtime, "scaffold", "auto-jump", "true");
    set(runtime, "scaffold", "move-fix", "true");
    set(runtime, "scaffold", "auto-disable", "false");
    set(runtime, "scaffold", "rotations", "RANDOMIZED");
    set(runtime, "scaffold", "randomized-speed-min", "7");
    set(runtime, "scaffold", "randomized-speed-max", "10");
    set(runtime, "scaffold", "min-rotation", "10");
    set(runtime, "scaffold", "max-rotation", "10");
    set(runtime, "scaffold", "search-algorithm", "NORMAL");
    set(runtime, "scaffold", "place-delay", "0");
    runtime.modules().setEnabled("scaffold", true);
    startPhase();
    return false;
  }

  public void frame() {
    if (closed) return;
    frameCount++;
    long now = System.nanoTime();
    if (ticks >= 5 && lastFrame != 0) frames.add(now - lastFrame);
    lastFrame = now;
  }

  private void resetPlayer() throws Exception {
    position(player, x + .5, y + 1, z + .5);
    camera(player, 0, 80);
    for (String[] names :
        new String[][] {
          {"motionX", "field_70159_w"}, {"motionY", "field_70181_x"}, {"motionZ", "field_70179_y"}
        }) field(player.getClass(), names).setDouble(player, 0);
    field(player.getClass(), "onGround", "field_70122_E").setBoolean(player, true);
    Arrays.fill(items, null);
    items[0] = stack("stone");
    field(items[0].getClass(), "stackSize", "field_77994_a").setInt(items[0], 64);
    field(inventory.getClass(), "currentItem", "field_70461_c").setInt(inventory, 0);
    Map<Object, Object> scene = new LinkedHashMap<>();
    for (Object point : saved.keySet()) scene.put(point, getState.invoke(world, point));
    serverTask(
        () -> {
          Method write =
              method(
                  serverWorld.getClass(),
                  new Class<?>[] {
                    Class.forName("net.minecraft.util.BlockPos"),
                    Class.forName("net.minecraft.block.state.IBlockState"),
                    int.class
                  },
                  "setBlockState",
                  "func_180501_a");
          for (Map.Entry<Object, Object> entry : scene.entrySet())
            write.invoke(serverWorld, entry.getKey(), entry.getValue(), 2);
          Arrays.fill(serverItems, null);
          serverItems[0] = stack("stone");
          field(serverItems[0].getClass(), "stackSize", "field_77994_a").setInt(serverItems[0], 64);
          field(serverInventory.getClass(), "currentItem", "field_70461_c")
              .setInt(serverInventory, 0);
          teleportServer(x + .5, y + 1, z + .5, 0, 80);
        });
  }

  private void startPhase() {
    ticks = 0;
    frames.clear();
    lastFrame = 0;
    started = System.nanoTime();
    placementPackets.set(0);
    corrections.set(0);
    dispatchNanos = runtime.clientEvents().measuredNanos();
    dispatches = runtime.clientEvents().measuredDispatches();
    allocated = allocated();
  }

  private void trace() throws Exception {
    ControlRequest<Rotation> request = runtime.controls().rotationRequest().orElse(null);
    Rotation rotation = request == null ? null : request.value().orElse(null);
    Object stack =
        items[field(inventory.getClass(), "currentItem", "field_70461_c").getInt(inventory)];
    String plan = "none";
    Map listeners =
        (Map) field(runtime.clientEvents().getClass(), "listeners").get(runtime.clientEvents());
    Object[] entries =
        (Object[])
            listeners.get(perihelion.integration.minecraft189.event.PlayerControlEvent.class);
    if (entries != null)
      for (Object entry : entries) {
        EventOwner entryOwner = (EventOwner) field(entry.getClass(), "owner").get(entry);
        if (!entryOwner.id().contains("scaffold")) continue;
        Object listener = field(entry.getClass(), "listener").get(entry);
        for (Field capture : listener.getClass().getDeclaredFields()) {
          if (!capture.getType().isArray()
              || !capture.getType().getComponentType().getSimpleName().equals("Placement"))
            continue;
          capture.setAccessible(true);
          Object array = capture.get(listener);
          Object value = Array.getLength(array) == 0 ? null : Array.get(array, 0);
          if (value != null) {
            boolean ray =
                rotation != null
                    && (Boolean)
                        method(
                                value.getClass(),
                                new Class<?>[] {
                                  game.getClass(), Rotation.class, double.class, boolean.class
                                },
                                "ray")
                            .invoke(value, game, rotation, 4.5, true);
            plan =
                field(value.getClass(), "target").get(value)
                    + " support="
                    + field(value.getClass(), "support").get(value)
                    + " face="
                    + field(value.getClass(), "face").get(value)
                    + " ray="
                    + ray;
          }
        }
      }
    System.out.println(
        "PERIHELION_SMOKE LIVE_TRACE phase="
            + phase
            + " tick="
            + ticks
            + " position="
            + number(player, "posX", "field_70165_t")
            + ","
            + number(player, "posY", "field_70163_u")
            + ","
            + number(player, "posZ", "field_70161_v")
            + " ground="
            + field(player.getClass(), "onGround", "field_70122_E").getBoolean(player)
            + " focus="
            + field(game.getClass(), "inGameHasFocus", "field_71415_G").getBoolean(game)
            + " selected="
            + field(inventory.getClass(), "currentItem", "field_70461_c").getInt(inventory)
            + " stack="
            + (stack == null
                ? "none"
                : field(stack.getClass(), "stackSize", "field_77994_a").getInt(stack))
            + " aim="
            + (rotation == null ? "none" : rotation.yaw() + "," + rotation.pitch())
            + " applied="
            + (request != null && HookBridge.gameControls().isRotationApplied(request))
            + " placements="
            + placementPackets.get()
            + " corrections="
            + corrections.get()
            + " plan="
            + plan);
  }

  private void report() {
    Collections.sort(frames);
    long events = runtime.clientEvents().measuredDispatches() - dispatches,
        nanos = runtime.clientEvents().measuredNanos() - dispatchNanos;
    double p95 =
        frames.isEmpty()
            ? 0
            : frames.get(Math.min(frames.size() - 1, (int) (frames.size() * .95))) / 1e6;
    System.out.println(
        "PERIHELION_SMOKE LIVE_PROFILE phase="
            + (phase == 0
                ? "walking-baseline"
                : phase == 1 ? "bridging-hud-esp-privacy-60cap" : "bridging-hud-esp-privacy-10cap")
            + " ticks="
            + ticks
            + " frames="
            + frames.size()
            + " elapsedMs="
            + (System.nanoTime() - started) / 1e6
            + " frameP95Ms="
            + p95
            + " frameMaxMs="
            + (frames.isEmpty() ? 0 : frames.get(frames.size() - 1) / 1e6)
            + " dispatches="
            + events
            + " totalDispatchMs="
            + nanos / 1e6
            + " meanDispatchUs="
            + (events == 0 ? 0 : nanos / 1000.0 / events)
            + " allocatedBytes="
            + (allocated < 0 ? -1 : allocated() - allocated));
  }

  private static long allocated() {
    try {
      return ((Number)
              Class.forName("com.sun.management.ThreadMXBean")
                  .getMethod("getThreadAllocatedBytes", long.class)
                  .invoke(ManagementFactory.getThreadMXBean(), Thread.currentThread().getId()))
          .longValue();
    } catch (Exception unavailable) {
      return -1;
    }
  }

  private interface Work {
    void run() throws Exception;
  }

  private void serverTask(Work work) throws Exception {
    Object server = method(game.getClass(), "getIntegratedServer", "func_71401_C").invoke(game);
    Future<?> task =
        (Future<?>)
            method(
                    server.getClass(),
                    new Class<?>[] {Runnable.class},
                    "addScheduledTask",
                    "func_152344_a")
                .invoke(
                    server,
                    (Runnable)
                        () -> {
                          try {
                            work.run();
                          } catch (Exception failure) {
                            throw new RuntimeException(failure);
                          }
                        });
    task.get(5, TimeUnit.SECONDS);
  }

  @Override
  public void close() throws Exception {
    if (closed) return;
    closed = true;
    if (diagnosticChannel != null
        && diagnosticHandler != null
        && diagnosticChannel.pipeline().context(diagnosticHandler) != null)
      diagnosticChannel.pipeline().remove(diagnosticHandler);
    for (String id : MODULES) {
      runtime.modules().setEnabled(id, false);
      for (perihelion.core.setting.Setting<?> setting :
          runtime.modules().require(id).module().settings().values()) reset(id, setting);
    }
    for (AutoCloseable resource : resources) resource.close();
    owner.close();
    HookBridge.gameControls().apply();
    for (int i = 0; i < 4; i++)
      method(world.getClass(), new Class<?>[] {int.class}, "removeEntityFromWorld", "func_73028_b")
          .invoke(world, -885500 - i);
    for (Map.Entry<Object, Object> entry : saved.entrySet())
      putState.invoke(world, entry.getKey(), entry.getValue(), 2);
    serverTask(
        () -> {
          Method write =
              method(
                  serverWorld.getClass(),
                  new Class<?>[] {
                    Class.forName("net.minecraft.util.BlockPos"),
                    Class.forName("net.minecraft.block.state.IBlockState"),
                    int.class
                  },
                  "setBlockState",
                  "func_180501_a");
          for (Map.Entry<Object, Object> entry : serverSaved.entrySet())
            write.invoke(serverWorld, entry.getKey(), entry.getValue(), 2);
          if (savedServerItems != null) {
            System.arraycopy(savedServerItems, 0, serverItems, 0, serverItems.length);
            field(serverInventory.getClass(), "currentItem", "field_70461_c")
                .setInt(serverInventory, serverSelected);
            teleportServer(serverX, serverY, serverZ, 0, 0);
            for (Map.Entry<Field, Object> entry : serverPlayerSaved.entrySet())
              entry.getKey().set(serverPlayer, entry.getValue());
          }
        });
    System.arraycopy(savedItems, 0, items, 0, items.length);
    field(inventory.getClass(), "currentItem", "field_70461_c").setInt(inventory, selected);
    field(forwardKey.getClass(), "pressed", "field_74513_e").setBoolean(forwardKey, oldPressed);
    field(settings.getClass(), "limitFramerate", "field_74350_i")
        .setInt(settings, originalFrameLimit);
    position(player, px, py, pz);
    for (Map.Entry<Field, Object> entry : playerSaved.entrySet())
      entry.getKey().set(player, entry.getValue());
    runtime.clientEvents().setTimingEnabled(oldTiming);
  }

  private <T> void reset(String id, perihelion.core.setting.Setting<T> setting) {
    runtime.modules().setSetting(id, setting, setting.defaultValue());
  }

  private void teleportServer(double x, double y, double z, float yaw, float pitch)
      throws Exception {
    Object handler =
        field(serverPlayer.getClass(), "playerNetServerHandler", "field_71135_a").get(serverPlayer);
    method(
            handler.getClass(),
            new Class<?>[] {double.class, double.class, double.class, float.class, float.class},
            "setPlayerLocation",
            "func_147364_a")
        .invoke(handler, x, y, z, yaw, pitch);
  }
}
