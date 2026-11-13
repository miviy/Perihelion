package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;
import static perihelion.smoke.RotationPipelineProbe.number;
import static perihelion.smoke.RotationPipelineProbe.position;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import perihelion.core.event.*;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.*;
import perihelion.integration.minecraft189.network.*;
import perihelion.integration.minecraft189.render.RenderState;

/** Packet acceptance, buffered-position markers and GL ownership through transformed PLAY hooks. */
public final class PacketPositionProbe {
  private static int checks;
  private static final AtomicInteger network = new AtomicInteger(), cancelled = new AtomicInteger();

  public static int networkEvents() {
    return network.get() - cancelled.get();
  }

  static void run(ClientRuntime r) throws Exception {
    checks = 0;
    network.set(0);
    cancelled.set(0);
    Object game =
        method(Class.forName("net.minecraft.client.Minecraft"), "getMinecraft", "func_71410_x")
            .invoke(null);
    Object player = field(game.getClass(), "thePlayer", "field_71439_g").get(game);
    Object world = field(game.getClass(), "theWorld", "field_71441_e").get(game);
    Object settings = field(game.getClass(), "gameSettings", "field_71474_y").get(game);
    Object input = field(player.getClass(), "movementInput", "field_71158_b").get(player);
    Field view = field(settings.getClass(), "thirdPersonView", "field_74320_O");
    Field forward = field(input.getClass(), "moveForward", "field_78900_b");
    int oldView = view.getInt(settings);
    float oldForward = forward.getFloat(input);
    double x = number(player, "posX", "field_70165_t"),
        y = number(player, "posY", "field_70163_u"),
        z = number(player, "posZ", "field_70161_v");
    Map<String, Boolean> enabled = new LinkedHashMap<>();
    Map<Setting<?>, Object> values = new LinkedHashMap<>();
    for (String id : new String[] {"blink", "lagrange", "aura"}) {
      enabled.put(id, r.modules().require(id).requestedEnabled());
      r.modules().setEnabled(id, false);
      for (Setting<?> s : r.modules().require(id).module().settings().values())
        values.put(s, s.get());
    }
    EventOwner owner =
        new EventOwner(
            "packet-position-probe",
            t -> {
              throw new AssertionError(t);
            });
    owner.activate();
    AtomicReference<Object> cancellation = new AtomicReference<>();
    r.networkEvents()
        .subscribe(
            owner, PacketSendEvent.class, Integer.MAX_VALUE, true, e -> network.incrementAndGet());
    r.networkEvents()
        .subscribe(
            owner,
            PacketReceiveEvent.class,
            Integer.MAX_VALUE,
            true,
            e -> network.incrementAndGet());
    Method packetGetter = PacketSendEvent.class.getMethod("packet");
    r.networkEvents()
        .subscribe(
            owner,
            PacketSendEvent.class,
            100000,
            true,
            e -> {
              try {
                if (packetGetter.invoke(e) == cancellation.get()) {
                  e.cancel();
                  cancelled.incrementAndGet();
                }
              } catch (Exception problem) {
                throw new AssertionError(problem);
              }
            });
    Object pig =
        Class.forName("net.minecraft.entity.passive.EntityPig")
            .getConstructor(Class.forName("net.minecraft.world.World"))
            .newInstance(world);
    Method add =
        method(
            world.getClass(),
            new Class<?>[] {int.class, Class.forName("net.minecraft.entity.Entity")},
            "addEntityToWorld",
            "func_73027_a");
    add.invoke(world, -976650, pig);
    OutgoingPacketFacts facts = HookBridge.gameControls().outgoingFacts();
    Object blink = r.modules().require("blink").module();
    Object lag = r.modules().require("lagrange").module();
    try (GameplayLoopback peer = new GameplayLoopback(game)) {
      Object handler = method(game.getClass(), "getNetHandler", "func_147114_u").invoke(game);
      Method send =
          method(
              handler.getClass(),
              new Class<?>[] {Class.forName("net.minecraft.network.Packet")},
              "addToSendQueue",
              "func_147297_a");
      facts.reset();
      check(facts.position() == null, "world position begins unknown");
      send.invoke(handler, movement(1.25, 75.5, -2.75));
      drain(peer);
      check(
          point(facts.position(), 1.25, 75.5, -2.75),
          "accepted positional movement records coordinates before modules enable");
      Object first = facts.position();
      send.invoke(
          handler,
          Class.forName("net.minecraft.network.play.client.C03PacketPlayer")
              .getConstructor(boolean.class)
              .newInstance(true));
      send.invoke(
          handler,
          Class.forName("net.minecraft.network.play.client.C03PacketPlayer$C05PacketPlayerLook")
              .getConstructor(float.class, float.class, boolean.class)
              .newInstance(12f, 34f, true));
      drain(peer);
      check(
          facts.position() == first,
          "ground and look-only packets do not invent positional coordinates");
      Object rejected = movement(4, 5, 6);
      cancellation.set(rejected);
      send.invoke(handler, rejected);
      drain(peer);
      cancellation.set(null);
      check(facts.position() == first, "cancelled movement does not advance the accepted position");
      int raw = network.get();
      Object replay = movement(8, 9, 10);
      RawHookBridge.replay(
          () -> {
            try {
              send.invoke(handler, replay);
            } catch (Exception e) {
              throw new AssertionError(e);
            }
          });
      check(network.get() == raw, "accepted silent replay remains absent from the raw bus");
      drain(peer);
      check(
          point(facts.position(), 8, 9, 10),
          "accepted replay advances the position as the source queue dispatch does");
      Object background = movement(11, 12, 13);
      peer.client
          .eventLoop()
          .submit(
              () -> {
                send.invoke(handler, background);
                return null;
              })
          .sync();
      drain(peer);
      check(
          point(facts.position(), 11, 12, 13),
          "actual Netty dispatch publishes one accepted coordinate tuple");

      view.setInt(settings, 1);
      set(r, "blink", "mode", "CONSTANT");
      set(r, "blink", "cancel-received-packets", "false");
      set(r, "blink", "render-blink-pos", "true");
      r.modules().setEnabled("blink", true);
      check(
          field(blink.getClass(), "position").get(blink) == null
              && samples(r, new SentPosition.Point(x, y, z)) == 0,
          "Blink has no marker before its first Motion PRE");
      r.clientEvents().post(PlayerControlEvent.BEFORE_MOTION);
      SentPosition.Point anchor =
          (SentPosition.Point) field(blink.getClass(), "position").get(blink);
      check(point(anchor, x, y, z), "Blink captures the exact first Motion PRE position");
      check(samples(r, anchor) > 0, "Blink draws the configured third-person marker");
      r.clientEvents().post(PlayerControlEvent.AFTER_MOTION);
      position(player, x + 5, y, z);
      r.clientEvents().post(PlayerControlEvent.BEFORE_MOTION);
      check(
          field(blink.getClass(), "position").get(blink) == anchor,
          "Blink movement retains the original anchor");
      view.setInt(settings, 0);
      check(samples(r, anchor) == 0, "Blink hides the marker in first person");
      view.setInt(settings, 2);
      check(samples(r, anchor) > 0, "Blink also draws in front-facing third person");
      set(r, "blink", "render-blink-pos", "false");
      check(samples(r, anchor) == 0, "Blink render setting hides the marker");
      set(r, "blink", "render-blink-pos", "true");
      set(r, "blink", "mode", "PULSE");
      set(r, "blink", "delay", "0");
      send.invoke(handler, movement(x + 5, y, z));
      r.clientEvents().post(PlayerControlEvent.AFTER_MOTION);
      drain(peer);
      check(
          field(blink.getClass(), "position").get(blink) == anchor && samples(r, anchor) > 0,
          "pulse flush retains the source activation anchor");
      r.modules().setEnabled("blink", false);
      check(
          field(blink.getClass(), "position").get(blink) == null && samples(r, anchor) == 0,
          "Blink disable retires marker state and rendering");
      r.modules().setEnabled("blink", true);
      r.clientEvents().post(PlayerControlEvent.BEFORE_MOTION);
      check(
          point((SentPosition.Point) field(blink.getClass(), "position").get(blink), x + 5, y, z),
          "Blink reenable takes a fresh first-motion anchor");
      r.modules().setEnabled("blink", false);

      set(r, "lagrange", "mode", "BLINK");
      set(r, "lagrange", "only-with-kill-aura", "true");
      set(r, "lagrange", "render-lag-pos", "true");
      set(r, "lagrange", "min-delay", "500");
      set(r, "lagrange", "max-delay", "500");
      r.modules().setEnabled("aura", true);
      field(r.modules().require("aura").module().getClass(), "target")
          .set(r.modules().require("aura").module(), pig);
      position(pig, x + 9, y, z);
      forward.setFloat(input, 1);
      r.modules().setEnabled("lagrange", true);
      check(samples(r, anchor) == 0, "Lag Range has no marker outside an active lag cycle");
      r.clientEvents().post(PlayerControlEvent.BEFORE_MOTION);
      SentPosition.Point lagAnchor =
          (SentPosition.Point) field(lag.getClass(), "position").get(lag);
      check(
          point(lagAnchor, x + 5, y, z) && samples(r, lagAnchor) > 0,
          "Blink Range captures and renders its range-entry anchor");
      SentPosition.Point accepted = facts.position();
      send.invoke(handler, movement(x + 5.5, y, z));
      check(
          facts.position() == accepted && HookBridge.gameControls().outboundOrder().owner() != null,
          "held Blink movement cannot advance accepted position");
      set(r, "lagrange", "mode", "LAG");
      drain(peer);
      check(
          point(facts.position(), x + 5.5, y, z),
          "live mode flush advances accepted position without raw redispatch");
      check(
          field(lag.getClass(), "position").get(lag) == null && samples(r, lagAnchor) == 0,
          "live mode change retires Blink marker and lag cycle");

      r.clientEvents().post(PlayerControlEvent.BEFORE_MOTION);
      send.invoke(handler, movement(x + 6, y, z));
      drain(peer);
      check(
          point(facts.position(), x + 6, y, z) && samples(r, facts.position()) > 0,
          "Lag mode uses accepted walking position in its initial window");
      r.clientEvents().post(PlayerControlEvent.AFTER_MOTION);
      accepted = facts.position();
      send.invoke(handler, movement(x + 7, y, z));
      check(facts.position() == accepted, "held Lag movement keeps the last accepted marker");
      Thread.sleep(120);
      r.clientEvents().post(PlayerControlEvent.AFTER_MOTION);
      drain(peer);
      check(
          point(facts.position(), x + 7, y, z) && samples(r, facts.position()) > 0,
          "lease replay advances marker while the source duration remains active");
      view.setInt(settings, 0);
      check(samples(r, facts.position()) == 0, "Lag marker hides in first person");
      view.setInt(settings, 1);
      set(r, "lagrange", "render-lag-pos", "false");
      check(samples(r, facts.position()) == 0, "Lag render setting hides an active marker");
      set(r, "lagrange", "render-lag-pos", "true");
      forward.setFloat(input, 0);
      r.clientEvents().post(PlayerControlEvent.BEFORE_MOTION);
      check(samples(r, facts.position()) == 0, "stopping movement ends visible lag state at PRE");
      r.clientEvents().post(PlayerControlEvent.AFTER_MOTION);
      Object aura = r.modules().require("aura").module();
      for (int reason = 0; reason < 4; reason++) {
        String label =
            new String[] {"movement stop", "target loss", "range exit", "duration end"}[reason];
        r.modules().setEnabled("lagrange", false);
        set(r, "lagrange", "mode", reason == 3 ? "LAG" : "BLINK");
        set(r, "lagrange", "min-delay", "0");
        set(r, "lagrange", "max-delay", "0");
        position(player, x + 5, y, z);
        position(pig, x + 9, y, z);
        field(aura.getClass(), "target").set(aura, pig);
        forward.setFloat(input, 1);
        r.modules().setEnabled("lagrange", true);
        r.clientEvents().post(PlayerControlEvent.BEFORE_MOTION);
        if (reason == 3) r.clientEvents().post(PlayerControlEvent.AFTER_MOTION);
        send.invoke(handler, movement(x + 20 + reason, y, z));
        check(
            HookBridge.gameControls().outboundOrder().owner() != null,
            label + " fixture begins with an actually held walking packet");
        if (reason == 0) forward.setFloat(input, 0);
        else if (reason == 1) field(aura.getClass(), "target").set(aura, null);
        else if (reason == 2) position(pig, x + 10, y, z);
        else Thread.sleep(5);
        r.clientEvents().post(PlayerControlEvent.BEFORE_MOTION);
        drain(peer);
        check(
            HookBridge.gameControls().outboundOrder().owner() == null
                && point(facts.position(), x + 20 + reason, y, z),
            label + " releases the previous packet during PRE before another walking pass");
        send.invoke(handler, movement(x + 30 + reason, y, z));
        drain(peer);
        check(
            point(facts.position(), x + 30 + reason, y, z),
            label + " permits the next walking packet before POST");
        check(samples(r, facts.position()) == 0, label + " retires its visible marker at PRE");
        r.clientEvents().post(PlayerControlEvent.AFTER_MOTION);
      }
      r.modules().setEnabled("lagrange", false);
      r.modules().setEnabled("aura", false);
      for (String id : enabled.keySet())
        check(
            r.modules().require(id).state() != perihelion.core.module.ModuleState.FAULTED,
            id + " remains unfaulted");
    } finally {
      for (String id : enabled.keySet()) r.modules().setEnabled(id, false);
      owner.close();
      for (String id : enabled.keySet())
        for (Setting<?> s : r.modules().require(id).module().settings().values())
          restore(r, id, s, values.get(s));
      method(world.getClass(), new Class<?>[] {int.class}, "removeEntityFromWorld", "func_73028_b")
          .invoke(world, -976650);
      position(player, x, y, z);
      view.setInt(settings, oldView);
      forward.setFloat(input, oldForward);
      facts.reset();
      for (String id : enabled.keySet()) if (enabled.get(id)) r.modules().setEnabled(id, true);
    }
    System.out.println(
        "PERIHELION_SMOKE PACKET_POSITION checks="
            + checks
            + " networkEvents="
            + networkEvents()
            + " cancelledEvents="
            + cancelled.get()
            + " rawEvents="
            + network.get()
            + " PASS");
  }

  private static Object movement(double x, double y, double z) throws Exception {
    return Class.forName(
            "net.minecraft.network.play.client.C03PacketPlayer$C04PacketPlayerPosition")
        .getConstructor(double.class, double.class, double.class, boolean.class)
        .newInstance(x, y, z, true);
  }

  private static boolean point(SentPosition.Point p, double x, double y, double z) {
    return p != null && p.x == x && p.y == y && p.z == z;
  }

  private static int samples(ClientRuntime r, SentPosition.Point point) throws Exception {
    Method read = method(RenderHookProbe.class, "readState");
    Object before = read.invoke(null);
    int query = GL15.glGenQueries();
    Object game =
        method(Class.forName("net.minecraft.client.Minecraft"), "getMinecraft", "func_71410_x")
            .invoke(null);
    Object manager = method(game.getClass(), "getRenderManager", "func_175598_ae").invoke(game);
    double cx = point.x - number(manager, "viewerPosX", "field_78730_l"),
        cy = point.y - number(manager, "viewerPosY", "field_78731_m");
    int result;
    try (RenderState ignored = new RenderState().open()) {
      GL11.glDisable(GL11.GL_SCISSOR_TEST);
      GL11.glMatrixMode(GL11.GL_PROJECTION);
      GL11.glLoadIdentity();
      GL11.glOrtho(cx - 2, cx + 2, cy - 1, cy + 3, -10000, 10000);
      GL11.glMatrixMode(GL11.GL_MODELVIEW);
      GL11.glLoadIdentity();
      GL15.glBeginQuery(GL15.GL_SAMPLES_PASSED, query);
      try {
        r.clientEvents().post(new WorldRenderEvent(1));
      } finally {
        GL15.glEndQuery(GL15.GL_SAMPLES_PASSED);
      }
      result = GL15.glGetQueryObjecti(query, GL15.GL_QUERY_RESULT);
    } finally {
      GL15.glDeleteQueries(query);
    }
    check(before.equals(read.invoke(null)), "marker and query projection restore native GL state");
    return result;
  }

  private static void drain(GameplayLoopback peer) throws Exception {
    peer.client.eventLoop().submit(() -> {}).sync();
    Object sentinel =
        Class.forName("net.minecraft.network.play.client.C00PacketKeepAlive")
            .getConstructor(int.class)
            .newInstance(0x5a140);
    method(
            peer.manager.get().getClass(),
            new Class<?>[] {Class.forName("net.minecraft.network.Packet")},
            "sendPacket",
            "func_179290_a")
        .invoke(peer.manager.get(), sentinel);
    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
    while (System.nanoTime() < deadline) {
      if (!peer.received.isEmpty()) {
        Object last = peer.received.get(peer.received.size() - 1);
        if (last.getClass().getSimpleName().equals("C00PacketKeepAlive")
            && ((Number) method(last.getClass(), "getKey", "func_149460_c").invoke(last)).intValue()
                == 0x5a140) {
          peer.received.remove(peer.received.size() - 1);
          return;
        }
      }
      Thread.sleep(2);
    }
    throw new AssertionError("position TCP drain timeout");
  }

  private static void restore(ClientRuntime r, String id, Setting s, Object value) {
    r.modules().setSetting(id, s, value);
  }

  private static void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }
}
