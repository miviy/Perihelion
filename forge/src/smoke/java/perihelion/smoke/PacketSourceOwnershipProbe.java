package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;
import static perihelion.smoke.RotationPipelineProbe.*;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import perihelion.core.event.EventOwner;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.event.ClientSendFactEvent;
import perihelion.integration.minecraft189.event.PacketReceiveEvent;
import perihelion.integration.minecraft189.event.PacketSendEvent;
import perihelion.integration.minecraft189.event.PlayerControlEvent;

/** Real two-manager PLAY traffic verifies capture ownership and frozen replay destination. */
public final class PacketSourceOwnershipProbe {
  private static int checks;

  public static void run(ClientRuntime runtime) throws Exception {
    checks = 0;
    Object game =
        method(Class.forName("net.minecraft.client.Minecraft"), "getMinecraft", "func_71410_x")
            .invoke(null);
    Field local = field(game.getClass(), "integratedServerIsRunning", "field_71455_al");
    boolean savedLocal = local.getBoolean(game);
    String[] ids = {"blink", "fakelag", "lagrange", "backtrack", "longjump", "noslow", "stasis"};
    Map<String, Boolean> enabled = new LinkedHashMap<>();
    for (String id : ids) {
      enabled.put(id, runtime.modules().require(id).requestedEnabled());
      runtime.modules().setEnabled(id, false);
    }
    Map<Setting<?>, Object> config = new LinkedHashMap<>();
    for (Setting<?> setting : runtime.modules().require("blink").module().settings().values())
      config.put(setting, setting.get());
    try {
      local.setBoolean(game, false);
      set(runtime, "blink", "mode", "CONSTANT");
      set(runtime, "blink", "cancel-received-packets", "true");
      restore(
          runtime,
          runtime.modules().require("blink").module().settings().get("release-on"),
          Collections.emptySet());
      try (GameplayLoopback foreign = new GameplayLoopback(game);
          GameplayLoopback own = new GameplayLoopback(game)) {
        EventOwner observer =
            new EventOwner(
                "packet-source-ownership-probe",
                failure -> {
                  throw new AssertionError(failure);
                });
        observer.activate();
        AtomicInteger ownSends = new AtomicInteger(),
            foreignSends = new AtomicInteger(),
            ownReceives = new AtomicInteger(),
            foreignReceives = new AtomicInteger(),
            facts = new AtomicInteger();
        runtime
            .networkEvents()
            .subscribe(
                observer,
                PacketSendEvent.class,
                -2000,
                true,
                event -> {
                  Object source = source(event);
                  if (source == own.manager.get()) {
                    check(
                        event.isDeferred(),
                        "own-manager outgoing raw packet is deferred by its buffer");
                    ownSends.incrementAndGet();
                  } else if (source == foreign.manager.get()) {
                    check(
                        !event.isCancelled(),
                        "foreign-manager outgoing raw packet is not cancelled by current buffer");
                    foreignSends.incrementAndGet();
                  }
                });
        runtime
            .networkEvents()
            .subscribe(
                observer,
                PacketReceiveEvent.class,
                -2000,
                true,
                event -> {
                  Object source = source(event);
                  if (source == own.manager.get()) {
                    if (!event.isCancelled())
                      throw new AssertionError("own-manager inbound packet escaped buffer");
                    ownReceives.incrementAndGet();
                  } else if (source == foreign.manager.get()) {
                    if (event.isCancelled())
                      throw new AssertionError(
                          "foreign-manager inbound packet captured by current buffer");
                    foreignReceives.incrementAndGet();
                  }
                });
        runtime
            .clientEvents()
            .subscribe(
                observer,
                ClientSendFactEvent.class,
                event -> {
                  if (event.action() == ClientSendFactEvent.Action.WALK) facts.incrementAndGet();
                });
        try {
          enable(runtime);
          int foreignBase = foreign.received.size(), ownBase = own.received.size();
          send(foreign.manager.get(), walk(11));
          foreign.await(foreignBase + 1);
          check(
              foreignSends.get() == 1 && own.received.size() == ownBase,
              "actual foreign PLAY send reaches only its original peer");
          foreign.sendServer(destroy(-989123));
          delivered(foreign, 1);
          check(
              foreignReceives.get() == 1 && own.delivered.isEmpty(),
              "actual foreign inbound PLAY packet reaches its own handler without capture");
          send(own.manager.get(), walk(22));
          send(
              own.manager.get(),
              Class.forName("net.minecraft.network.play.client.C0APacketAnimation").newInstance());
          own.sendServer(destroy(-989124));
          await(ownReceives, 1);
          flush(own.client);
          check(
              ownSends.get() == 2 && own.received.size() == ownBase && own.delivered.isEmpty(),
              "own-manager movement, action and inbound packet are genuinely held");
          check(
              facts.get() == 1, "only the current manager contributes a fresh client walking fact");
          runtime.modules().setEnabled("blink", false);
          own.await(ownBase + 2);
          check(
              own.received.get(ownBase).getClass().getSimpleName().equals("C04PacketPlayerPosition")
                  && own.received
                      .get(ownBase + 1)
                      .getClass()
                      .getSimpleName()
                      .equals("C0APacketAnimation"),
              "owned replay retains movement-before-action order on its original manager");
          check(
              ownSends.get() == 2 && facts.get() == 1,
              "snapshot replay is not dispatched again as raw events or fresh client facts");
          check(
              method(
                          Class.forName(
                              "perihelion.integration.minecraft189.network.RawHookBridge"),
                          "source")
                      .invoke(null)
                  == null,
              "dispatch source identity is cleared outside the synchronous callback");

          enable(runtime);
          int beforeOwn = own.received.size(), beforeForeign = foreign.received.size();
          send(own.manager.get(), walk(33));
          own.managerField.set(own.gameHandler, foreign.manager.get());
          runtime.modules().setEnabled("blink", false);
          // A marker settles the destination that a broken handler-based replay would redirect to.
          send(
              foreign.manager.get(),
              Class.forName("net.minecraft.network.play.client.C00PacketKeepAlive")
                  .getConstructor(int.class)
                  .newInstance(0x7a1b));
          foreign.await(beforeForeign + 1);
          flush(own.client);
          check(
              foreign.received.size() == beforeForeign + 1 && own.received.size() == beforeOwn,
              "same-handler manager handoff discards queued snapshots instead of redirecting them");
          own.managerField.set(own.gameHandler, own.manager.get());
        } finally {
          runtime.modules().setEnabled("blink", false);
          observer.close();
          own.managerField.set(own.gameHandler, own.manager.get());
        }
        backtrack(runtime, game, foreign, own);
      }
    } finally {
      runtime.modules().setEnabled("blink", false);
      for (Map.Entry<Setting<?>, Object> value : config.entrySet())
        restore(runtime, value.getKey(), value.getValue());
      local.setBoolean(game, savedLocal);
      for (String id : ids) runtime.modules().setEnabled(id, enabled.get(id));
    }
    System.out.println("PERIHELION_SMOKE PACKET_SOURCE_OWNERSHIP checks=" + checks + " PASS");
  }

  private static void backtrack(
      ClientRuntime runtime, Object game, GameplayLoopback foreign, GameplayLoopback own)
      throws Exception {
    Map<Setting<?>, Object> config = new LinkedHashMap<>();
    for (Setting<?> setting : runtime.modules().require("backtrack").module().settings().values())
      config.put(setting, setting.get());
    Object world = field(game.getClass(), "theWorld", "field_71441_e").get(game);
    Object player = field(game.getClass(), "thePlayer", "field_71439_g").get(game);
    Object pig =
        Class.forName("net.minecraft.entity.passive.EntityPig")
            .getConstructor(Class.forName("net.minecraft.world.World"))
            .newInstance(world);
    final int id = -989125;
    double x = field(player.getClass(), "posX", "field_70165_t").getDouble(player),
        y = field(player.getClass(), "posY", "field_70163_u").getDouble(player),
        z = field(player.getClass(), "posZ", "field_70161_v").getDouble(player);
    position(pig, x + 2, y, z);
    method(
            world.getClass(),
            new Class<?>[] {int.class, Class.forName("net.minecraft.entity.Entity")},
            "addEntityToWorld",
            "func_73027_a")
        .invoke(world, id, pig);
    try {
      set(runtime, "backtrack", "mode", "BASIC");
      set(runtime, "backtrack", "style", "OLD");
      runtime.modules().setEnabled("backtrack", true);
      Object session = DeepGameplayProbe.backtrackSession(runtime);
      Object state = field(session.getClass(), "state").get(session);
      int sx = (int) ((x + 10) * 32), sy = (int) (y * 32), sz = (int) (z * 32);
      method(
              state.getClass(),
              new Class<?>[] {int.class, int.class, int.class, int.class, int.class, long.class},
              "begin")
          .invoke(state, id, -1, sx, sy, sz, System.nanoTime() / 1000000L);
      field(session.getClass(), "trackedTarget").set(session, pig);
      Class<?> action =
          Class.forName("net.minecraft.network.play.client.C02PacketUseEntity$Action");
      Object attack =
          Class.forName("net.minecraft.network.play.client.C02PacketUseEntity")
              .getConstructor(Class.forName("net.minecraft.entity.Entity"), action)
              .newInstance(pig, action.getField("ATTACK").get(null));
      send(foreign.manager.get(), attack);
      check(
          field(pig.getClass(), "posX", "field_70165_t").getDouble(pig) == x + 2,
          "foreign source attack cannot apply BackTrack's pending server position");
      send(own.manager.get(), attack);
      check(
          field(pig.getClass(), "posX", "field_70165_t").getDouble(pig) == sx / 32.0,
          "own source attack retains BackTrack's normal legacy position update");
      Object queue = field(session.getClass(), "held").get(session);
      Object teleport =
          Class.forName("net.minecraft.network.play.server.S18PacketEntityTeleport")
              .getConstructor(
                  int.class, int.class, int.class, int.class, byte.class, byte.class, boolean.class)
              .newInstance(id, sx + 64, sy, sz, (byte) 0, (byte) 0, true);
      int deliveredBefore = foreign.delivered.size();
      foreign.sendServer(teleport);
      delivered(foreign, deliveredBefore + 1);
      check(
          ((Number) method(queue.getClass(), "size").invoke(queue)).intValue() == 0,
          "foreign inbound source cannot enter BackTrack's custom queue");
      own.sendServer(teleport);
      long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
      while (((Number) method(queue.getClass(), "size").invoke(queue)).intValue() == 0
          && System.nanoTime() < until) Thread.sleep(2);
      check(
          ((Number) method(queue.getClass(), "size").invoke(queue)).intValue() == 1,
          "own inbound source still enters BackTrack's custom queue");
      Field serverX = field(pig.getClass(), "serverPosX", "field_70118_ct");
      int beforeServerX = serverX.getInt(pig);
      own.managerField.set(own.gameHandler, foreign.manager.get());
      check(
          !(Boolean) method(session.getClass(), "current").invoke(session),
          "BackTrack rejects a different manager behind the same handler");
      runtime.modules().setEnabled("backtrack", false);
      check(
          serverX.getInt(pig) == beforeServerX
              && field(session.getClass(), "closed").getBoolean(session),
          "manager handoff discards BackTrack snapshots without applying them through the"
              + " replacement");
    } finally {
      runtime.modules().setEnabled("backtrack", false);
      own.managerField.set(own.gameHandler, own.manager.get());
      method(world.getClass(), new Class<?>[] {int.class}, "removeEntityFromWorld", "func_73028_b")
          .invoke(world, id);
      for (Map.Entry<Setting<?>, Object> value : config.entrySet())
        restoreFor(runtime, "backtrack", value.getKey(), value.getValue());
    }
  }

  private static void enable(ClientRuntime runtime) {
    runtime.modules().setEnabled("blink", true);
    runtime.clientEvents().post(PlayerControlEvent.AFTER_MOTION);
  }

  private static Object source(Object event) {
    try {
      return method(event.getClass(), "source").invoke(event);
    } catch (Exception failure) {
      throw new AssertionError(failure);
    }
  }

  private static Object walk(double x) throws Exception {
    return Class.forName(
            "net.minecraft.network.play.client.C03PacketPlayer$C04PacketPlayerPosition")
        .getConstructor(double.class, double.class, double.class, boolean.class)
        .newInstance(x, 240d, 0d, true);
  }

  private static Object destroy(int id) throws Exception {
    return Class.forName("net.minecraft.network.play.server.S13PacketDestroyEntities")
        .getConstructor(int[].class)
        .newInstance((Object) new int[] {id});
  }

  private static void send(Object manager, Object packet) throws Exception {
    method(
            manager.getClass(),
            new Class<?>[] {Class.forName("net.minecraft.network.Packet")},
            "sendPacket",
            "func_179290_a")
        .invoke(manager, packet);
  }

  private static void delivered(GameplayLoopback peer, int count) throws Exception {
    long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
    while (peer.delivered.size() < count && System.nanoTime() < until) Thread.sleep(2);
    check(peer.delivered.size() >= count, "foreign inbound packet completes actual TCP decoding");
  }

  private static void await(AtomicInteger counter, int count) throws Exception {
    long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
    while (counter.get() < count && System.nanoTime() < until) Thread.sleep(2);
    check(counter.get() >= count, "own inbound packet reaches the source-aware raw callback");
  }

  private static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
    checks++;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void restore(ClientRuntime runtime, Setting setting, Object value) {
    restoreFor(runtime, "blink", setting, value);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void restoreFor(ClientRuntime runtime, String id, Setting setting, Object value) {
    runtime.modules().setSetting(id, setting, value);
  }
}
