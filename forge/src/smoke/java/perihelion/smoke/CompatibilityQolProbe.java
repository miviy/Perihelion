package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;
import static perihelion.smoke.RotationPipelineProbe.*;

import io.netty.channel.Channel;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import perihelion.core.context.ConnectionContext;
import perihelion.core.control.*;
import perihelion.core.event.EventOwner;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.*;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.*;
import perihelion.integration.minecraft189.gui.ClickGuiModule;
import perihelion.integration.minecraft189.gui.font.GuiFonts;
import perihelion.integration.minecraft189.render.RenderState;

/** Tests actual writes after buffering, plus native camera and font hooks. */
public final class CompatibilityQolProbe {
  private static int checks;
  private static final AtomicInteger networkEvents = new AtomicInteger();
  private static final AtomicInteger deliveredEvents = new AtomicInteger();

  public static int networkEvents() {
    return deliveredEvents.get();
  }

  private static GameplayLoopback loopback;
  private static final String[] IDS = {
    "fakelag",
    "blink",
    "backtrack",
    "autof5",
    "nameprotect",
    "freelook",
    "freecam",
    "scaffold",
    "aura"
  };

  static void run(
      ClientRuntime runtime,
      Object game,
      Object player,
      Object world,
      Object[] items,
      Method getState,
      Method putState,
      Channel channel,
      List<Object> packets,
      int x,
      int y,
      int z)
      throws Exception {
    checks = 0;
    networkEvents.set(0);
    deliveredEvents.set(0);
    Object settings = field(game.getClass(), "gameSettings", "field_71474_y").get(game);
    Field view = field(settings.getClass(), "thirdPersonView", "field_74320_O");
    int originalView = view.getInt(settings);
    try {
      for (String id : IDS) runtime.modules().setEnabled(id, false);
      HookBridge.gameControls().apply();
      EventOwner observer =
          new EventOwner(
              "compatibility-network-observer",
              failure -> {
                throw new AssertionError(failure);
              });
      observer.activate();
      AutoCloseable sent =
          runtime
              .networkEvents()
              .subscribe(
                  observer,
                  PacketSendEvent.class,
                  10000,
                  true,
                  event -> networkEvents.incrementAndGet());
      AutoCloseable received =
          runtime
              .networkEvents()
              .subscribe(
                  observer,
                  PacketReceiveEvent.class,
                  10000,
                  true,
                  event -> networkEvents.incrementAndGet());
      AutoCloseable deliveredSend =
          runtime
              .networkEvents()
              .subscribe(
                  observer, PacketSendEvent.class, event -> deliveredEvents.incrementAndGet());
      AutoCloseable deliveredReceive =
          runtime
              .networkEvents()
              .subscribe(
                  observer, PacketReceiveEvent.class, event -> deliveredEvents.incrementAndGet());
      try (GameplayLoopback peer = new GameplayLoopback(game)) {
        loopback = peer;
        check(
            peer.verifyOriginalKeepAlive(),
            "integrated-server keepalive replies stay on their original connection");
        packetSequences(runtime, game, player, peer.client, peer.received);
        canceledWalking(runtime, game, player, peer.client, peer.received);
        preWalking(runtime, game, player, peer.client, peer.received);
        backtrack(runtime, game, player, world, peer);
        runtime.modules().setEnabled("fakelag", true);
        peer.received.clear();
        send(game, look(43, 22));
        send(game, use());
        flush(peer.client);
        runtime.updateWorld(false, ConnectionContext.DISCONNECTED, true);
        flush(peer.client);
        check(peer.received.isEmpty(), "world retirement drops the buffered outbound sequence");
        check(
            HookBridge.gameControls().outboundOrder().owner() == null,
            "world retirement clears ordering ownership");
        runtime.modules().setEnabled("fakelag", false);
        runtime.updateWorld(
            true, new ConnectionContext(ConnectionContext.Kind.SINGLEPLAYER, ""), true);
      } finally {
        sent.close();
        received.close();
        deliveredSend.close();
        deliveredReceive.close();
        observer.close();
        loopback = null;
      }
      check(networkEvents.get() > 0, "TCP fixtures exercise the native raw multiplayer hooks");
      perspectives(runtime, settings, player, items);
      names(runtime, game, player);
      ScaffoldTerrainProbe.run(
          runtime, game, player, world, items, getState, putState, channel, packets, x, y, z);
      HookBridge.gameControls().apply();
    } finally {
      for (String id : IDS) {
        runtime.modules().setEnabled(id, false);
        for (perihelion.core.setting.Setting<?> s :
            runtime.modules().require(id).module().settings().values())
          resetSetting(runtime, id, s);
      }
      HookBridge.gameControls().apply();
      view.setInt(settings, originalView);
    }
    System.out.println(
        "PERIHELION_SMOKE COMPATIBILITY_QOL checks="
            + checks
            + " rawEvents="
            + networkEvents.get()
            + " unbufferedEvents="
            + deliveredEvents.get()
            + " PASS");
  }

  private static void packetSequences(
      ClientRuntime r, Object game, Object player, Channel channel, List<Object> packets)
      throws Exception {
    for (String first : new String[] {"fakelag", "blink"}) {
      String second = first.equals("fakelag") ? "blink" : "fakelag";
      set(r, "fakelag", "delay", "2000");
      set(r, "blink", "mode", "CONSTANT");
      r.modules().setEnabled(first, true);
      r.clientEvents().post(PlayerControlEvent.AFTER_MOTION);
      flush(channel);
      packets.clear();
      send(game, look(71, 32));
      r.modules().setEnabled(second, true);
      r.clientEvents().post(PlayerControlEvent.AFTER_MOTION);
      for (Object packet :
          new Object[] {slot(2), attack(player), use(), release(), slot(4), animation()})
        send(game, packet);
      flush(channel);
      check(packets.isEmpty(), first + " keeps interactions behind its walking packet");
      r.modules().setEnabled(second, false);
      flush(channel);
      check(packets.isEmpty(), "retiring " + second + " cannot release " + first + "'s sequence");
      r.modules().setEnabled(first, false);
      flush(channel);
      loopback.await(7);
      String[] expected = {
        "C05PacketPlayerLook",
        "C09PacketHeldItemChange",
        "C02PacketUseEntity",
        "C08PacketPlayerBlockPlacement",
        "C07PacketPlayerDigging",
        "C09PacketHeldItemChange",
        "C0APacketAnimation"
      };
      check(packets.size() == expected.length, first + " sends every sequence packet once");
      for (int i = 0; i < expected.length; i++)
        check(
            packets.get(i).getClass().getSimpleName().equals(expected[i]),
            first + " preserves actual write " + i);
      check(
          HookBridge.gameControls().outboundOrder().owner() == null,
          "completed sequence releases its owner");
    }
    // A shorter newly configured delay cannot let a later action jump the old look.
    set(r, "fakelag", "delay", "2000");
    r.modules().setEnabled("fakelag", true);
    flush(channel);
    packets.clear();
    send(game, look(51, 11));
    set(r, "fakelag", "delay", "50");
    send(game, use());
    Thread.sleep(65);
    r.clientEvents().post(PlayerControlEvent.AFTER_MOTION);
    flush(channel);
    check(packets.isEmpty(), "shorter later delay cannot overtake an earlier look");
    r.modules().setEnabled("fakelag", false);
    flush(channel);
    loopback.await(2);
    check(
        packets.size() == 2
            && packets.get(0).getClass().getSimpleName().equals("C05PacketPlayerLook"),
        "delay changes preserve transmitted FIFO: actual="
            + packets.stream()
                .map(p -> p.getClass().getSimpleName())
                .collect(java.util.stream.Collectors.joining(",")));
  }

  private static void preWalking(
      ClientRuntime r, Object game, Object player, Channel channel, List<Object> packets)
      throws Exception {
    EventOwner owner =
        new EventOwner(
            "pre-walking-fixture",
            failure -> {
              throw new AssertionError(failure);
            });
    owner.activate();
    List<AutoCloseable> resources = new ArrayList<>();
    ControlRequest<Rotation> aim = r.controls().scope(owner, resources::add).rotation(1500);
    ControlRequest<Rotation> other = r.controls().scope(owner, resources::add).rotation(2500);
    perihelion.integration.minecraft189.control.MinecraftControls controls =
        HookBridge.gameControls();
    boolean[] canceled = {false};
    Class<?> c03 = Class.forName("net.minecraft.network.play.client.C03PacketPlayer");
    resources.add(
        r.networkEvents()
            .subscribe(
                owner,
                PacketSendEvent.class,
                5000,
                false,
                event -> {
                  try {
                    if (canceled[0]
                        && c03.isInstance(PacketSendEvent.class.getMethod("packet").invoke(event)))
                      event.cancel();
                  } catch (ReflectiveOperationException failure) {
                    throw new RuntimeException(failure);
                  }
                }));
    double x = number(player, "posX", "field_70165_t"),
        y = number(player, "posY", "field_70163_u"),
        z = number(player, "posZ", "field_70161_v");
    float yaw = (float) number(player, "rotationYaw", "field_70177_z"),
        pitch = (float) number(player, "rotationPitch", "field_70125_A");
    try {
      aim.set(Rotation.server(63, 21));
      flush(channel);
      packets.clear();
      forceLook(player);
      motion(player);
      flush(channel);
      loopback.await(1);
      check(
          !controls.previousRotation(aim).isPresent(),
          "prior aim is unavailable outside PRE input");
      controls.beforeInput();
      Rotation previous = controls.previousRotation(aim).get();
      aim.set(previous);
      check(
          controls.isPreviousRotationApplied(aim),
          "PRE accepts the identical previous owner/value");
      aim.set(Rotation.server(previous.yaw(), previous.pitch() + 1));
      check(!controls.isPreviousRotationApplied(aim), "PRE rejects an unsent new angle");
      aim.set(previous);
      other.set(Rotation.server(0, 0));
      check(!controls.isPreviousRotationApplied(aim), "PRE yields to competing rotation ownership");
      other.clear();
      position(player, x + .25, y, z);
      check(!controls.previousRotation(aim).isPresent(), "PRE rejects changed player position");
      position(player, x, y, z);
      check(
          controls.withPreviousRotation(
              aim,
              () -> {
                try {
                  send(game, use());
                } catch (Exception failure) {
                  throw new RuntimeException(failure);
                }
              }),
          "PRE runs authorized action");
      check(
          number(player, "rotationYaw", "field_70177_z") == yaw
              && number(player, "rotationPitch", "field_70125_A") == pitch,
          "PRE restores camera angles");
      forceLook(player);
      motion(player);
      flush(channel);
      loopback.await(3);
      orderedPrePlacement(packets, "PRE actual TCP order");
      controls.beforeInput();
      controls.beforeInput();
      check(
          !controls.previousRotation(aim).isPresent(),
          "repeated input cannot reuse an older walking pass");
      canceled[0] = true;
      forceLook(player);
      motion(player);
      controls.beforeInput();
      check(
          !controls.previousRotation(aim).isPresent(),
          "hard-canceled walking never grants PRE permission");
      canceled[0] = false;
      forceLook(player);
      motion(player);
      controls.beforeInput();
      check(
          controls.previousRotation(aim).isPresent(),
          "fresh accepted walking restores PRE permission");
      Class<?> correction =
          Class.forName("net.minecraft.network.play.server.S08PacketPlayerPosLook");
      Object packet =
          correction
              .getConstructor(
                  double.class, double.class, double.class, float.class, float.class, Set.class)
              .newInstance(x, y, z, yaw + 37, pitch, Collections.emptySet());
      Object handler = method(game.getClass(), "getNetHandler", "func_147114_u").invoke(game);
      method(
              handler.getClass(),
              new Class<?>[] {correction},
              "handlePlayerPosLook",
              "func_147258_a")
          .invoke(handler, packet);
      check(
          !controls.previousRotation(aim).isPresent(),
          "real rotation-only correction invalidates PRE aim");
      // Drain the correction ACK at the actual TCP peer before starting the buffer case.
      long deadline = System.nanoTime() + 2_000_000_000L;
      boolean acknowledged = false;
      while (!acknowledged && System.nanoTime() < deadline) {
        for (Object received : packets)
          if (received.getClass().getSimpleName().equals("C06PacketPlayerPosLook")
              && ((Number) method(received.getClass(), "getYaw", "func_149462_g").invoke(received))
                      .floatValue()
                  == yaw + 37) acknowledged = true;
        if (!acknowledged) Thread.sleep(2);
      }
      check(acknowledged, "rotation-only correction ACK reaches the real TCP peer");
      camera(player, yaw, pitch);
      flush(channel);
      packets.clear();
      set(r, "fakelag", "delay", "2000");
      r.modules().setEnabled("fakelag", true);
      forceLook(player);
      motion(player);
      controls.beforeInput();
      previous = controls.previousRotation(aim).get();
      aim.set(previous);
      check(
          controls.withPreviousRotation(
              aim,
              () -> {
                try {
                  send(game, use());
                } catch (Exception failure) {
                  throw new RuntimeException(failure);
                }
              }),
          "buffered previous walking permits PRE action in the same sequence");
      forceLook(player);
      motion(player);
      flush(channel);
      check(packets.isEmpty(), "buffer keeps the PRE sequence private until flush");
      r.modules().setEnabled("fakelag", false);
      flush(channel);
      loopback.await(3);
      orderedPrePlacement(packets, "buffered PRE actual TCP order");
    } finally {
      r.modules().setEnabled("fakelag", false);
      for (AutoCloseable resource : resources) resource.close();
      owner.close();
      position(player, x, y, z);
      camera(player, yaw, pitch);
      controls.apply();
    }
  }

  private static void canceledWalking(
      ClientRuntime r, Object game, Object player, Channel channel, List<Object> packets)
      throws Exception {
    EventOwner owner =
        new EventOwner(
            "canceled-walking-fixture",
            failure -> {
              throw new AssertionError(failure);
            });
    owner.activate();
    List<AutoCloseable> resources = new ArrayList<>();
    ControlRequest<Rotation> aim = r.controls().scope(owner, resources::add).rotation(1500);
    aim.set(Rotation.server(63, 21));
    boolean[] cancel = {true};
    Class<?> walking = Class.forName("net.minecraft.network.play.client.C03PacketPlayer");
    Method packetValue = PacketSendEvent.class.getMethod("packet");
    resources.add(
        r.networkEvents()
            .subscribe(
                owner,
                PacketSendEvent.class,
                5000,
                false,
                event -> {
                  if (cancel[0] && walking.isInstance(packetValue.invoke(event))) event.cancel();
                }));
    resources.add(
        r.clientEvents()
            .subscribe(
                owner,
                PlayerControlEvent.class,
                event -> {
                  if (event == PlayerControlEvent.AFTER_MOTION
                      && HookBridge.gameControls().isRotationApplied(aim)) {
                    try {
                      send(game, use());
                    } catch (Exception failure) {
                      throw new RuntimeException(failure);
                    }
                  }
                }));
    try {
      flush(channel);
      packets.clear();
      forceLook(player);
      motion(player);
      flush(channel);
      check(packets.isEmpty(), "canceled walking does not authorize an automatic interaction");
      check(
          !HookBridge.gameControls().isRotationApplied(aim),
          "canceled rotation has no applied authorization");
      cancel[0] = false;
      motion(player);
      flush(channel);
      loopback.await(2);
      check(
          packets.size() >= 2 && walking.isInstance(packets.get(0)),
          "same aim retries its walking look after cancellation");
      ordered(
          packets, "C08PacketPlayerBlockPlacement", aim.value().get(), "retry after cancellation");
      aim.clear();
      cancel[0] = true;
      packets.clear();
      camera(player, 63, 21);
      forceLook(player);
      motion(player);
      flush(channel);
      check(packets.isEmpty(), "hard cancellation also withholds an unowned camera look");
      aim.set(Rotation.server(63, 21));
      cancel[0] = false;
      motion(player);
      flush(channel);
      loopback.await(2);
      ordered(
          packets,
          "C08PacketPlayerBlockPlacement",
          aim.value().get(),
          "ownership after an unowned canceled look");
      r.modules().setEnabled("fakelag", true);
      flush(channel);
      packets.clear();
      forceLook(player);
      motion(player);
      flush(channel);
      check(
          packets.isEmpty() && HookBridge.gameControls().isRotationApplied(aim),
          "deferred walking authorizes an interaction in the same ordered buffer");
      r.modules().setEnabled("fakelag", false);
      flush(channel);
      loopback.await(2);
      ordered(
          packets, "C08PacketPlayerBlockPlacement", aim.value().get(), "deferred walking replay");
    } finally {
      r.modules().setEnabled("fakelag", false);
      for (AutoCloseable resource : resources) resource.close();
      owner.close();
    }
  }

  private static void perspectives(ClientRuntime r, Object settings, Object player, Object[] items)
      throws Exception {
    Arrays.fill(items, null);
    items[0] = stack("stone");
    set(r, "scaffold", "auto-disable", "false");
    Field view = field(settings.getClass(), "thirdPersonView", "field_74320_O");
    view.setInt(settings, 0);
    set(r, "autof5", "on-aura", "false");
    r.modules().setEnabled("autof5", true);
    HookBridge.gameControls().beforeInput();
    check(view.getInt(settings) == 0, "Auto F5 is idle without an active trigger");
    r.modules().setEnabled("scaffold", true);
    HookBridge.gameControls().beforeInput();
    check(view.getInt(settings) == 1, "Scaffold triggers Auto F5");
    r.modules().setEnabled("scaffold", false);
    HookBridge.gameControls().beforeInput();
    check(view.getInt(settings) == 0, "Auto F5 restores first person when the trigger ends");
    view.setInt(settings, 2);
    r.modules().setEnabled("scaffold", true);
    HookBridge.gameControls().beforeInput();
    check(view.getInt(settings) == 2, "Auto F5 leaves an existing front view alone");
    view.setInt(settings, 0);
    HookBridge.gameControls().beforeInput();
    check(view.getInt(settings) == 1, "Auto F5 acquires only a first-person view");
    r.controls().beginTick();
    view.setInt(settings, 2);
    HookBridge.gameControls().beforeInput();
    check(
        view.getInt(settings) == 2 && !r.controls().perspective().isPresent(),
        "manual F5 overrides Auto F5");
    view.setInt(settings, 0);
    HookBridge.gameControls().beforeInput();
    check(
        view.getInt(settings) == 0, "manual first person remains selected until the trigger ends");
    r.modules().setEnabled("scaffold", false);
    HookBridge.gameControls().beforeInput();
    view.setInt(settings, 0);
    r.modules().setEnabled("scaffold", true);
    HookBridge.gameControls().beforeInput();
    set(r, "freelook", "hold", "false");
    r.modules().setEnabled("freelook", true);
    HookBridge.gameControls().beforeInput();
    r.modules().setEnabled("autof5", false);
    HookBridge.gameControls().apply();
    check(view.getInt(settings) == 1, "disabling Auto F5 does not displace FreeLook");
    r.modules().setEnabled("freelook", false);
    HookBridge.gameControls().apply();
    check(view.getInt(settings) == 0, "last camera owner restores the original perspective");
    r.modules().setEnabled("autof5", true);
    HookBridge.gameControls().beforeInput();
    r.modules().setEnabled("freecam", true);
    HookBridge.gameControls().beforeInput();
    check(
        !r.controls().perspective().isPresent() && view.getInt(settings) == 0,
        "Auto F5 yields to detached Freecam");
    r.modules().setEnabled("freecam", false);
    HookBridge.gameControls().beforeInput();
    check(view.getInt(settings) == 1, "Auto F5 resumes after Freecam");
    r.modules().setEnabled("scaffold", false);
    HookBridge.gameControls().beforeInput();
    r.modules().setEnabled("autof5", false);
    HookBridge.gameControls().apply();
  }

  private static void backtrack(
      ClientRuntime r, Object game, Object player, Object world, GameplayLoopback peer)
      throws Exception {
    Class<?> entityType = Class.forName("net.minecraft.entity.Entity");
    Method add =
        method(
            world.getClass(),
            new Class<?>[] {int.class, entityType},
            "addEntityToWorld",
            "func_73027_a");
    Method remove =
        method(
            world.getClass(), new Class<?>[] {int.class}, "removeEntityFromWorld", "func_73028_b");
    Constructor<?> pigType =
        Class.forName("net.minecraft.entity.passive.EntityPig")
            .getConstructor(Class.forName("net.minecraft.world.World"));
    Object first = pigType.newInstance(world), second = pigType.newInstance(world);
    double x = number(player, "posX", "field_70165_t"),
        y = number(player, "posY", "field_70163_u"),
        z = number(player, "posZ", "field_70161_v");
    position(first, x + 2, y, z);
    position(second, x + 4, y, z);
    add.invoke(world, -884411, first);
    add.invoke(world, -884412, second);
    Field serverX = field(first.getClass(), "serverPosX", "field_70118_ct");
    serverX.setInt(first, (int) Math.floor((x + 2) * 32));
    field(first.getClass(), "serverPosY", "field_70117_cu").setInt(first, (int) Math.floor(y * 32));
    field(first.getClass(), "serverPosZ", "field_70116_cv").setInt(first, (int) Math.floor(z * 32));
    field(second.getClass(), "serverPosX", "field_70118_ct")
        .setInt(second, (int) Math.floor((x + 4) * 32));
    field(second.getClass(), "serverPosY", "field_70117_cu")
        .setInt(second, (int) Math.floor(y * 32));
    field(second.getClass(), "serverPosZ", "field_70116_cv")
        .setInt(second, (int) Math.floor(z * 32));
    Field[] previous = {
      field(player.getClass(), "prevPosX", "field_70169_q"),
      field(player.getClass(), "prevPosY", "field_70167_r"),
      field(player.getClass(), "prevPosZ", "field_70166_s")
    };
    double[] savedPrevious = new double[3], current = {x, y, z};
    for (int i = 0; i < 3; i++) {
      savedPrevious[i] = previous[i].getDouble(player);
      previous[i].setDouble(player, current[i]);
    }
    Constructor<?> relative =
        Class.forName("net.minecraft.network.play.server.S14PacketEntity$S15PacketEntityRelMove")
            .getConstructor(int.class, byte.class, byte.class, byte.class, boolean.class);
    try {
      set(r, "aura", "targets", "ANIMALS");
      set(r, "aura", "through-walls", "true");
      set(r, "aura", "auto-disable", "false");
      set(r, "aura", "sort", "DISTANCE");
      set(r, "backtrack", "only-kill-aura", "false");
      set(r, "backtrack", "style", "OLD");
      set(r, "backtrack", "min-range", "0");
      set(r, "backtrack", "max-range", "10");
      set(r, "backtrack", "max-delay", "2000");
      set(r, "fakelag", "delay", "2000");
      set(r, "fakelag", "delay-entity-movements", "false");
      set(r, "fakelag", "delay-velocity", "false");
      set(r, "blink", "cancel-received-packets", "false");
      r.modules().setEnabled("backtrack", true);
      HookBridge.gameControls().beforeInput();
      Object session = DeepGameplayProbe.backtrackSession(r);
      Object snapshot = field(session.getClass(), "state").get(session);
      check(
          field(snapshot.getClass(), "holding").getBoolean(snapshot)
              && field(snapshot.getClass(), "targetId").getInt(snapshot) == -884411,
          "Back Track selects the closest eligible target");
      // Exercise live style transitions on real captured TCP packets, before enabling outbound
      // buffers.
      int transitionStart = serverX.getInt(first);
      int receivedBefore = networkEvents.get();
      peer.sendServer(relative.newInstance(-884411, (byte) 16, (byte) 0, (byte) 0, true));
      awaitInbound(peer, receivedBefore + 1);
      check(serverX.getInt(first) == transitionStart, "Old style holds the transition movement");
      set(r, "backtrack", "style", "NEW");
      check(
          serverX.getInt(first) == transitionStart + 16
              && !field(snapshot.getClass(), "holding").getBoolean(snapshot),
          "Old to New flushes the infinite-deadline head before timed capture");
      HookBridge.gameControls().beforeInput();
      receivedBefore = networkEvents.get();
      peer.sendServer(relative.newInstance(-884411, (byte) 16, (byte) 0, (byte) 0, true));
      awaitInbound(peer, receivedBefore + 1);
      check(
          serverX.getInt(first) == transitionStart + 16, "New style retains its configured delay");
      set(r, "backtrack", "style", "OLD");
      check(
          serverX.getInt(first) == transitionStart + 32
              && !field(snapshot.getClass(), "holding").getBoolean(snapshot),
          "New to Old flushes timed traffic before beginning a legacy batch");
      HookBridge.gameControls().beforeInput();
      r.modules().setEnabled("fakelag", true);
      r.modules().setEnabled("blink", true);
      r.clientEvents().post(PlayerControlEvent.AFTER_MOTION);
      peer.received.clear();
      peer.delivered.clear();
      send(game, look(39, 27));
      send(game, use());
      flush(peer.client);
      int initialServerX = serverX.getInt(first), before = networkEvents.get();
      peer.sendServer(relative.newInstance(-884411, (byte) 16, (byte) 0, (byte) 0, true));
      peer.sendServer(relative.newInstance(-884411, (byte) 16, (byte) 0, (byte) 0, true));
      awaitInbound(peer, before + 2);
      Object pending = method(snapshot.getClass(), "position").invoke(snapshot);
      check(
          pending != null && Math.abs(number(pending, "x") - (initialServerX / 32.0 + 1)) < 1e-6,
          "real inbound deltas accumulate while outbound buffers are active");
      check(
          serverX.getInt(first) == initialServerX && peer.delivered.isEmpty(),
          "Back Track withholds both moves from the game handler");
      position(first, x + 8, y, z);
      HookBridge.gameControls().beforeInput();
      Object next = method(snapshot.getClass(), "position").invoke(snapshot);
      check(
          serverX.getInt(first) == initialServerX + 32,
          "retargeting replays both moves in order into the native handler");
      check(
          next != null
              && field(snapshot.getClass(), "targetId").getInt(snapshot) == -884412
              && Math.abs(number(next, "x") - (x + 4)) < 1e-6,
          "retargeting starts a fresh target position");
      check(peer.received.isEmpty(), "inbound retarget flush cannot release the outbound sequence");
      before = networkEvents.get();
      peer.sendServer(relative.newInstance(-884412, (byte) 16, (byte) 0, (byte) 0, true));
      peer.sendServer(
          Class.forName("net.minecraft.network.play.server.S12PacketEntityVelocity")
              .getConstructor(int.class, double.class, double.class, double.class)
              .newInstance(
                  (Integer)
                      method(player.getClass(), "getEntityId", "func_145782_y").invoke(player),
                  0.1,
                  0.2,
                  0.1));
      awaitInbound(peer, before + 2);
      method(session.getClass(), "renderRelease").invoke(session);
      check(
          method(snapshot.getClass(), "position").invoke(snapshot) == null,
          "local velocity retires the inbound position at the client render release");
      r.modules().setEnabled("blink", false);
      flush(peer.client);
      check(
          peer.received.isEmpty(),
          "non-owning Blink retirement leaves Fake Lag's pending aim intact");
      r.modules().setEnabled("fakelag", false);
      flush(peer.client);
      peer.await(2);
      ordered(
          peer.received,
          "C08PacketPlayerBlockPlacement",
          Rotation.server(39, 27),
          "Back Track and outbound buffers");
      Object settings = field(game.getClass(), "gameSettings", "field_71474_y").get(game);
      Field view = field(settings.getClass(), "thirdPersonView", "field_74320_O");
      view.setInt(settings, 0);
      r.modules().setEnabled("aura", true);
      r.modules().setEnabled("autof5", true);
      HookBridge.gameControls().beforeInput();
      check(view.getInt(settings) == 1, "an actual Aura target triggers Auto F5");
      r.modules().setEnabled("aura", false);
      HookBridge.gameControls().beforeInput();
      check(view.getInt(settings) == 0, "losing Aura's target restores first person");
    } finally {
      for (String id : new String[] {"autof5", "aura", "backtrack", "blink", "fakelag"})
        r.modules().setEnabled(id, false);
      HookBridge.gameControls().apply();
      remove.invoke(world, -884411);
      remove.invoke(world, -884412);
      for (int i = 0; i < 3; i++) previous[i].setDouble(player, savedPrevious[i]);
    }
  }

  private static void awaitInbound(GameplayLoopback peer, int count) throws Exception {
    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
    while (networkEvents.get() < count && System.nanoTime() < deadline) Thread.sleep(2);
    peer.client.eventLoop().submit(() -> {}).sync();
    check(networkEvents.get() >= count, "the TCP client receives the scripted inbound packets");
  }

  private static void names(ClientRuntime r, Object game, Object player) throws Exception {
    String original = (String) method(player.getClass(), "getName", "func_70005_c_").invoke(player);
    Object font = field(game.getClass(), "fontRendererObj", "field_71466_p").get(game);
    Method width =
        method(font.getClass(), new Class<?>[] {String.class}, "getStringWidth", "func_78256_a");
    int maskedWidth = (Integer) width.invoke(font, "You"),
        originalWidth = (Integer) width.invoke(font, original);
    r.modules().setEnabled("nameprotect", true);
    check(
        HookBridge.displayText("<" + original + "> " + original).equals("<You> You"),
        "Name Protect replaces repeated local names");
    check(
        (Integer) width.invoke(font, original) == maskedWidth,
        "vanilla font measurement uses the protected name");
    Method trim =
        method(
            font.getClass(),
            new Class<?>[] {String.class, int.class, boolean.class},
            "trimStringToWidth",
            "func_78262_a");
    check(
        trim.invoke(font, original, maskedWidth, false).equals(original),
        "vanilla trimming returns original text sized by its protected width");
    String line = original + " " + original + " end";
    String kept = (String) trim.invoke(font, line, maskedWidth * 2 + 5, false);
    check(
        java.util.Arrays.asList(
                original,
                original + " ",
                original + " " + original,
                original + " " + original + " ",
                line)
            .contains(kept),
        "trimmed chat text stays indexable against the original without a partial name");
    Method wrap =
        method(
            font.getClass(),
            new Class<?>[] {String.class, int.class},
            "listFormattedStringToWidth",
            "func_78271_c");
    check(
        !wrap.invoke(font, original + " " + original, maskedWidth + 2)
            .toString()
            .contains(original),
        "wrapped lines cannot reintroduce the local name");
    try (RenderState ignored = new RenderState().open();
        GuiFonts fonts = new GuiFonts()) {
      GuiFonts.Face smooth = fonts.get(ClickGuiModule.Font.SANS);
      check(
          smooth.width(original) == smooth.width("You"),
          "smooth font measurement matches protected drawing");
      smooth.draw(original, 0, 0, 0xffffffff);
      Method draw =
          method(
              font.getClass(),
              new Class<?>[] {String.class, float.class, float.class, int.class, boolean.class},
              "drawString",
              "func_175065_a");
      check(
          (Integer) draw.invoke(font, original, 0f, 0f, -1, false) == maskedWidth,
          "transformed vanilla draw returns the protected advance");
      Class<?> hudType = Class.forName("perihelion.integration.minecraft189.hud.HudFont");
      Constructor<?> constructor = hudType.getDeclaredConstructor();
      constructor.setAccessible(true);
      AutoCloseable hud = (AutoCloseable) constructor.newInstance();
      try {
        Method hudWidth = method(hudType, new Class<?>[] {String.class}, "width");
        check(
            hudWidth.invoke(hud, "\u00a7a" + original).equals(hudWidth.invoke(hud, "\u00a7aYou")),
            "formatted HUD runs retain protected name metrics");
      } finally {
        hud.close();
      }
    }
    check(
        method(player.getClass(), "getName", "func_70005_c_").invoke(player).equals(original),
        "Name Protect leaves the real player identity unchanged");
    r.modules().setEnabled("nameprotect", false);
    check(
        HookBridge.displayText(original).equals(original)
            && (Integer) width.invoke(font, original) == originalWidth,
        "disabling Name Protect restores original drawing and measurement");
  }

  private static Object look(float yaw, float pitch) throws Exception {
    return Class.forName("net.minecraft.network.play.client.C03PacketPlayer$C05PacketPlayerLook")
        .getConstructor(float.class, float.class, boolean.class)
        .newInstance(yaw, pitch, false);
  }

  private static Object slot(int slot) throws Exception {
    return Class.forName("net.minecraft.network.play.client.C09PacketHeldItemChange")
        .getConstructor(int.class)
        .newInstance(slot);
  }

  private static Object use() throws Exception {
    return Class.forName("net.minecraft.network.play.client.C08PacketPlayerBlockPlacement")
        .getConstructor(Class.forName("net.minecraft.item.ItemStack"))
        .newInstance(stack("stone"));
  }

  private static Object animation() throws Exception {
    return Class.forName("net.minecraft.network.play.client.C0APacketAnimation").newInstance();
  }

  private static Object attack(Object entity) throws Exception {
    Class<?> action = Class.forName("net.minecraft.network.play.client.C02PacketUseEntity$Action");
    return Class.forName("net.minecraft.network.play.client.C02PacketUseEntity")
        .getConstructor(Class.forName("net.minecraft.entity.Entity"), action)
        .newInstance(entity, Enum.valueOf((Class) action, "ATTACK"));
  }

  private static Object release() throws Exception {
    Class<?>
        action = Class.forName("net.minecraft.network.play.client.C07PacketPlayerDigging$Action"),
        face = Class.forName("net.minecraft.util.EnumFacing");
    return Class.forName("net.minecraft.network.play.client.C07PacketPlayerDigging")
        .getConstructor(action, Class.forName("net.minecraft.util.BlockPos"), face)
        .newInstance(
            Enum.valueOf((Class) action, "RELEASE_USE_ITEM"),
            pos(0, 0, 0),
            Enum.valueOf((Class) face, "DOWN"));
  }

  private static void send(Object game, Object packet) throws Exception {
    Object handler = method(game.getClass(), "getNetHandler", "func_147114_u").invoke(game);
    Object network =
        method(handler.getClass(), "getNetworkManager", "func_147298_b").invoke(handler);
    method(
            network.getClass(),
            new Class<?>[] {Class.forName("net.minecraft.network.Packet")},
            "sendPacket",
            "func_179290_a")
        .invoke(network, packet);
  }

  private static <T> void resetSetting(
      ClientRuntime r, String id, perihelion.core.setting.Setting<T> setting) {
    r.modules().setSetting(id, setting, setting.defaultValue());
  }

  private static void check(boolean ok, String message) {
    checks++;
    if (!ok) throw new AssertionError(message);
  }
}
