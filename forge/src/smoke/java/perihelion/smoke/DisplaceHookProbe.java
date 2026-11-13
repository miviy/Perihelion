package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;
import static perihelion.smoke.RotationPipelineProbe.*;

import io.netty.channel.Channel;
import java.lang.reflect.*;
import java.util.*;
import perihelion.core.control.*;
import perihelion.core.event.EventOwner;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.HookBridge;

/**
 * Actual Aura/Displace ownership handoff, PRE packets, finishing release and foreign-aim rejection.
 */
public final class DisplaceHookProbe {
  private static final java.util.concurrent.atomic.AtomicInteger tcpEvents =
      new java.util.concurrent.atomic.AtomicInteger();

  public static int networkEvents() {
    return tcpEvents.get();
  }

  static void run(
      ClientRuntime runtime,
      Object game,
      Object player,
      Object world,
      Object[] items,
      Channel channel,
      List<Object> packets,
      int x,
      int y,
      int z)
      throws Exception {
    tcpEvents.set(0);
    int checks = 0;
    String[] ids = {"aura", "displace"};
    Map<String, Boolean> enabled = new LinkedHashMap<>();
    Map<Setting<?>, Object> settings = new LinkedHashMap<>();
    for (String id : ids) {
      enabled.put(id, runtime.modules().require(id).requestedEnabled());
      runtime.modules().setEnabled(id, false);
      for (Setting<?> setting : runtime.modules().require(id).module().settings().values())
        settings.put(setting, setting.get());
    }
    Object aura = runtime.modules().require("aura").module(),
        displace = runtime.modules().require("displace").module();
    Object profile =
        Class.forName("com.mojang.authlib.GameProfile")
            .getConstructor(UUID.class, String.class)
            .newInstance(UUID.randomUUID(), "DisplaceFixture");
    Object target =
        Class.forName("net.minecraft.client.entity.EntityOtherPlayerMP")
            .getConstructor(Class.forName("net.minecraft.world.World"), profile.getClass())
            .newInstance(world, profile);
    position(player, x + .5, y + 1, z + .5);
    position(target, x + .5, y + 1, z + 2.5);
    method(
            world.getClass(),
            new Class<?>[] {int.class, Class.forName("net.minecraft.entity.Entity")},
            "addEntityToWorld",
            "func_73027_a")
        .invoke(world, -887719, target);
    EventOwner owner =
        new EventOwner(
            "displace-hook-probe",
            failure -> {
              throw new AssertionError(failure);
            });
    owner.activate();
    List<AutoCloseable> claims = new ArrayList<>();
    ControlRequest<Rotation> blocker = runtime.controls().scope(owner, claims::add).rotation(1000);
    Field next = field(aura.getClass(), "nextAttack");
    Method sprint =
        method(player.getClass(), new Class<?>[] {boolean.class}, "setSprinting", "func_70031_b");
    try {
      Arrays.fill(items, null);
      items[0] = stack("diamond_sword");
      Object inventory = field(player.getClass(), "inventory", "field_71071_by").get(player);
      field(inventory.getClass(), "currentItem", "field_70461_c").setInt(inventory, 0);
      camera(player, 0, 0);
      field(game.getClass(), "inGameHasFocus", "field_71415_G").setBoolean(game, true);
      field(game.getClass(), "currentScreen", "field_71462_r").set(game, null);
      method(player.getClass(), "clearItemInUse", "func_71041_bz").invoke(player);
      set(runtime, "aura", "rotations", "NORMAL");
      set(runtime, "aura", "targets", "PLAYERS,TEAMMATES");
      set(runtime, "aura", "through-walls", "true");
      set(runtime, "aura", "auto-block", "NONE");
      set(runtime, "aura", "fov", "360");
      set(runtime, "aura", "simulate-mouse-clicks", "false");
      set(runtime, "aura", "ray-cast", "false");
      set(runtime, "aura", "auto-disable", "false");
      set(runtime, "displace", "mode", "LEFT");
      set(runtime, "displace", "cooldown", "5");
      runtime.modules().setEnabled("aura", true);
      runtime.modules().setEnabled("displace", true);
      field(displace.getClass(), "lastAt").setLong(displace, -1);
      sprint.invoke(player, true);
      flush(channel);
      packets.clear();
      next.setDouble(aura, 0);
      HookBridge.gameControls().beforeInput();
      require(
          runtime.controls().rotationRequest().isPresent(),
          "handoff fixture prepares a rotation: aura="
              + runtime.modules().require("aura").state()
              + " target="
              + method(aura.getClass(), "target").invoke(aura)
              + " displace="
              + runtime.modules().require("displace").state());
      ControlRequest<Rotation> request = runtime.controls().rotationRequest().get();
      require(request.ownerId().equals("displace"), "Displace owns the handoff request");
      checks++;
      Rotation submitted = request.value().get();
      require(
          packets.stream()
              .noneMatch(p -> p.getClass().getSimpleName().equals("C02PacketUseEntity")),
          "unsent Displace aim cannot attack");
      checks++;
      forceLook(player);
      motion(player);
      flush(channel);
      next.setDouble(aura, 0);
      HookBridge.gameControls().beforeInput();
      flush(channel);
      require(
          packets.stream()
                  .filter(p -> p.getClass().getSimpleName().equals("C02PacketUseEntity"))
                  .count()
              == 1,
          "Aura attacks once under submitted Displace aim");
      checks++;
      ordered(packets, "C02PacketUseEntity", submitted, "Displace handoff");
      checks += 2;
      require(
          runtime.controls().rotationRequest().get() == request,
          "Displace retains ownership through the finishing interval");
      checks++;
      forceLook(player);
      motion(player);
      flush(channel);
      require(request.value().isPresent() == false, "finishing walking pass releases Displace");
      checks++;
      next.setDouble(aura, Double.MAX_VALUE);
      HookBridge.gameControls().beforeInput();
      require(
          runtime.controls().rotationRequest().get().ownerId().equals("aura"),
          "Aura resumes its native rotation request");
      checks++;
      runtime.modules().setEnabled("displace", false);
      runtime.modules().setEnabled("aura", false);
      runtime.modules().setEnabled("aura", true);
      runtime.modules().setEnabled("displace", true);
      field(displace.getClass(), "lastAt").setLong(displace, -1);
      sprint.invoke(player, true);
      blocker.set(Rotation.server(130, 10));
      next.setDouble(aura, 0);
      HookBridge.gameControls().beforeInput();
      forceLook(player);
      motion(player);
      flush(channel);
      packets.clear();
      blocker.clear();
      next.setDouble(aura, 0);
      HookBridge.gameControls().beforeInput();
      flush(channel);
      require(
          packets.stream()
              .noneMatch(p -> p.getClass().getSimpleName().equals("C02PacketUseEntity")),
          "Displace cannot borrow a stronger owner's preceding walking aim");
      checks++;
      runtime.modules().setEnabled("displace", false);
      runtime.modules().setEnabled("aura", false);
      require(!runtime.controls().rotation().isPresent(), "disable retires all handoff claims");
      checks++;
      // Integrated-server channels skip raw multiplayer hooks. Use real serialized TCP
      // here so foreign block/dig windows and own-release listeners actually run.
      try (GameplayLoopback peer = new GameplayLoopback(game)) {
        List<Object> tcpPackets = new java.util.concurrent.CopyOnWriteArrayList<>();
        peer.client
            .pipeline()
            .addLast(
                "hypixel-timing-recorder",
                new io.netty.channel.ChannelOutboundHandlerAdapter() {
                  @Override
                  public void write(
                      io.netty.channel.ChannelHandlerContext ctx,
                      Object msg,
                      io.netty.channel.ChannelPromise promise)
                      throws Exception {
                    tcpPackets.add(msg);
                    ctx.write(msg, promise);
                  }
                });
        AutoCloseable ledger =
            runtime
                .networkEvents()
                .subscribe(
                    owner,
                    perihelion.integration.minecraft189.event.PacketSendEvent.class,
                    event -> tcpEvents.incrementAndGet());
        try {
          hypixelTiming(runtime, game, player, items, peer.client, tcpPackets, aura, next);
        } finally {
          ledger.close();
        }
      }
      System.out.println("PERIHELION_SMOKE DISPLACE_HANDOFF checks=" + checks + " PASS");
    } finally {
      for (String id : ids) runtime.modules().setEnabled(id, false);
      for (AutoCloseable claim : claims) claim.close();
      owner.close();
      for (String id : ids)
        for (Setting<?> setting : runtime.modules().require(id).module().settings().values())
          restore(runtime, id, setting, settings.get(setting));
      method(world.getClass(), new Class<?>[] {int.class}, "removeEntityFromWorld", "func_73028_b")
          .invoke(world, -887719);
      HookBridge.gameControls().apply();
      for (String id : ids) if (enabled.get(id)) runtime.modules().setEnabled(id, true);
    }
  }

  private static void hypixelTiming(
      ClientRuntime runtime,
      Object game,
      Object player,
      Object[] items,
      Channel channel,
      List<Object> packets,
      Object aura,
      Field next)
      throws Exception {
    set(runtime, "aura", "auto-block", "HYPIXEL");
    set(runtime, "aura", "min-cps", "10");
    set(runtime, "aura", "max-cps", "10");
    long enabledAt = System.nanoTime() / 1_000_000L;
    runtime.modules().setEnabled("aura", true);
    require(
        next.getDouble(aura) >= enabledAt + 100
            && next.getDouble(aura) <= System.nanoTime() / 1_000_000L + 100,
        "Aura's first delay uses configured CPS before the mode-specific cadence");
    next.setDouble(aura, System.nanoTime() / 1_000_000L + 20);
    HookBridge.gameControls().beforeInput();
    flush(channel);
    packets.clear();
    forceLook(player);
    motion(player);
    flush(channel);
    Rotation previous = runtime.controls().rotation().get();
    next.setDouble(aura, System.nanoTime() / 1_000_000L - 40);
    long start = System.nanoTime() / 1_000_000L;
    HookBridge.gameControls().beforeInput();
    flush(channel);
    long end = System.nanoTime() / 1_000_000L;
    double deadline = next.getDouble(aura);
    require(
        deadline >= start + 125 && deadline <= end + 125,
        "late Aura ticks restart the donor's 125ms cooldown");
    require(
        count(packets, "C02PacketUseEntity") == 1,
        "a due Hypixel interval sends exactly one attack");
    ordered(packets, "C02PacketUseEntity", previous, "Hypixel due attack");
    forceLook(player);
    motion(player);
    flush(channel);
    packets.clear();
    next.setDouble(aura, Double.MAX_VALUE);
    HookBridge.gameControls().beforeInput();
    flush(channel);
    require(
        count(packets, "C08PacketPlayerBlockPlacement") == 1
            && count(packets, "C02PacketUseEntity") == 0,
        "the following cooldown interval blocks once without attacking");
    packets.clear();
    forceLook(player);
    motion(player);
    flush(channel);
    next.setDouble(aura, System.nanoTime() / 1_000_000L - 40);
    HookBridge.gameControls().beforeInput();
    flush(channel);
    require(
        count(packets, "C07PacketPlayerDigging") == 1 && count(packets, "C02PacketUseEntity") == 1,
        "own Hypixel unblock does not add a second attack wait");
    require(
        index(packets, "C07PacketPlayerDigging") < index(packets, "C02PacketUseEntity"),
        "Hypixel release precedes its due PRE attack");
    forceLook(player);
    motion(player);
    flush(channel);
    packets.clear();
    next.setDouble(aura, Double.MAX_VALUE);
    HookBridge.gameControls().beforeInput();
    flush(channel);
    require(
        count(packets, "C08PacketPlayerBlockPlacement") == 1,
        "second cooldown interval establishes a fresh owned block");
    forceLook(player);
    motion(player);
    flush(channel);
    packets.clear();
    Object foreign =
        Class.forName("net.minecraft.network.play.client.C08PacketPlayerBlockPlacement")
            .getConstructor(Class.forName("net.minecraft.item.ItemStack"))
            .newInstance(items[0]);
    method(
            Class.forName("perihelion.integration.minecraft189.module.gameplay.GameActions"),
            new Class<?>[] {Class.forName("net.minecraft.network.Packet")},
            "send")
        .invoke(null, foreign);
    next.setDouble(aura, System.nanoTime() / 1_000_000L - 40);
    HookBridge.gameControls().beforeInput();
    flush(channel);
    require(
        count(packets, "C07PacketPlayerDigging") == 1 && count(packets, "C02PacketUseEntity") == 0,
        "own release cannot clear a foreign packet's block window");
    forceLook(player);
    motion(player);
    flush(channel);
    next.setDouble(aura, Double.MAX_VALUE);
    HookBridge.gameControls().beforeInput();
    forceLook(player);
    motion(player);
    flush(channel);
    packets.clear();
    Class<?> dig = Class.forName("net.minecraft.network.play.client.C07PacketPlayerDigging");
    Class<?> action =
        Class.forName("net.minecraft.network.play.client.C07PacketPlayerDigging$Action");
    Class<?> pos = Class.forName("net.minecraft.util.BlockPos"),
        facing = Class.forName("net.minecraft.util.EnumFacing");
    Object stop =
        dig.getConstructor(action, pos, facing)
            .newInstance(
                action.getField("STOP_DESTROY_BLOCK").get(null),
                pos.getConstructor(int.class, int.class, int.class).newInstance(0, 0, 0),
                facing.getField("DOWN").get(null));
    // RawHookBridge deliberately rejects recursive sends. Send a separate foreign
    // dig between walking and PRE rather than violating that callback contract.
    method(
            Class.forName("perihelion.integration.minecraft189.module.gameplay.GameActions"),
            new Class<?>[] {Class.forName("net.minecraft.network.Packet")},
            "send")
        .invoke(null, stop);
    next.setDouble(aura, System.nanoTime() / 1_000_000L - 40);
    HookBridge.gameControls().beforeInput();
    flush(channel);
    require(
        count(packets, "C07PacketPlayerDigging") == 2 && count(packets, "C02PacketUseEntity") == 0,
        "a foreign dig keeps the attack window blocked during Aura's own release");
    runtime.modules().setEnabled("aura", false);
    System.out.println("PERIHELION_SMOKE AURA_HYPIXEL_TIMING checks=11 PASS");
  }

  private static long count(List<Object> packets, String name) {
    return packets.stream().filter(p -> p.getClass().getSimpleName().equals(name)).count();
  }

  private static int index(List<Object> packets, String name) {
    for (int i = 0; i < packets.size(); i++)
      if (packets.get(i).getClass().getSimpleName().equals(name)) return i;
    return -1;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void restore(ClientRuntime r, String id, Setting setting, Object value) {
    r.modules().setSetting(id, setting, value);
  }
}
