package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;
import static perihelion.smoke.RotationPipelineProbe.number;

import java.lang.reflect.*;
import java.util.*;
import perihelion.core.control.*;
import perihelion.core.event.EventOwner;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleEntry;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.HookBridge;

/** Actual walking serialization and vanilla position-cache behavior for Librecraft landing. */
public final class NoFallPacketParityProbe {
  private static int checks;

  public static void run(ClientRuntime runtime) throws Exception {
    checks = 0;
    Object game =
        method(Class.forName("net.minecraft.client.Minecraft"), "getMinecraft", "func_71410_x")
            .invoke(null);
    Object player = field(game.getClass(), "thePlayer", "field_71439_g").get(game);
    Field screen = field(game.getClass(), "currentScreen", "field_71462_r");
    Object oldScreen = screen.get(game);
    Map<String, Boolean> enabled = new LinkedHashMap<>();
    for (ModuleEntry entry : runtime.modules().entries()) {
      if (!entry.info().requiresWorld()) continue;
      enabled.put(entry.info().id(), entry.requestedEnabled());
      runtime.modules().setEnabled(entry.info().id(), false);
    }
    Map<Setting<?>, Object> settings = new LinkedHashMap<>();
    for (Setting<?> setting : runtime.modules().require("nofall").module().settings().values())
      settings.put(setting, setting.get());
    Map<Field, Object> saved = new LinkedHashMap<>();
    for (String[] names :
        new String[][] {
          {"lastReportedPosX", "field_175172_bI"},
          {"lastReportedPosY", "field_175166_bJ"},
          {"lastReportedPosZ", "field_175167_bK"},
          {"lastReportedYaw", "field_175164_bL"},
          {"lastReportedPitch", "field_175165_bM"},
          {"positionUpdateTicks", "field_175168_bP"},
          {"onGround", "field_70122_E"},
          {"fallDistance", "field_70143_R"},
          {"rotationYaw", "field_70177_z"},
          {"rotationPitch", "field_70125_A"}
        }) {
      Field f = field(player.getClass(), names);
      saved.put(f, f.get(player));
    }
    double oldX = number(player, "posX", "field_70165_t"),
        oldY = number(player, "posY", "field_70163_u"),
        oldZ = number(player, "posZ", "field_70161_v");
    Method position =
        method(
            player.getClass(),
            new Class<?>[] {double.class, double.class, double.class},
            "setPosition",
            "func_70107_b");
    Method walking = method(player.getClass(), "onUpdateWalkingPlayer", "func_175161_p");
    Class<?> c03 = Class.forName("net.minecraft.network.play.client.C03PacketPlayer");
    Field ground = field(player.getClass(), "onGround", "field_70122_E"),
        fall = field(player.getClass(), "fallDistance", "field_70143_R"),
        cacheY = field(player.getClass(), "lastReportedPosY", "field_175166_bJ");
    EventOwner owner =
        new EventOwner(
            "walking-session-parity",
            t -> {
              throw new AssertionError(t);
            });
    owner.activate();
    List<AutoCloseable> owned = new ArrayList<>();
    ControlRequest<Rotation> rotation = runtime.controls().scope(owner, owned::add).rotation(2000);
    try (GameplayLoopback peer = new GameplayLoopback(game)) {
      screen.set(game, null);
      position.invoke(player, oldX, 200.0, oldZ);
      ground.setBoolean(player, false);
      HookBridge.gameControls().beforeInput();
      rotation.set(Rotation.serverUnwrapped(37, 16));
      walking.invoke(player);
      boundary(peer, 0x4e460001);
      check(
          HookBridge.gameControls().isRotationApplied(rotation),
          "walking receipt belongs to its live connection");
      peer.managerField.set(peer.gameHandler, peer.originalManager);
      check(
          !HookBridge.gameControls().isRotationApplied(rotation),
          "an applied walking aim cannot cross a changed live connection");
      java.util.concurrent.atomic.AtomicInteger actions =
          new java.util.concurrent.atomic.AtomicInteger();
      check(
          !HookBridge.gameControls().withRotation(rotation, actions::incrementAndGet)
              && actions.get() == 0,
          "connection handoff prevents actions using the old walking receipt");
      peer.managerField.set(peer.gameHandler, peer.manager.get());
      HookBridge.gameControls().beforeInput();
      check(
          HookBridge.gameControls().previousRotation(rotation).isPresent(),
          "previous walking aim is available on its original connection");
      peer.managerField.set(peer.gameHandler, peer.originalManager);
      check(
          !HookBridge.gameControls().previousRotation(rotation).isPresent(),
          "previous walking aim cannot cross a changed live connection");
      check(
          !HookBridge.gameControls().withPreviousAim(rotation, aim -> actions.incrementAndGet())
              && actions.get() == 0,
          "PRE action callback is suppressed after connection handoff");
      peer.managerField.set(peer.gameHandler, peer.manager.get());
      // Hard cancellation is different from an intentionally deferred ordered walk.
      boolean[] reject = {true};
      owned.add(
          runtime
              .networkEvents()
              .subscribe(
                  owner,
                  perihelion.integration.minecraft189.event.PacketSendEvent.class,
                  10000,
                  false,
                  event -> {
                    if (reject[0]
                        && c03.isInstance(event.getClass().getMethod("packet").invoke(event))
                        && (Boolean)
                            method(c03, "isMoving", "func_149466_j")
                                .invoke(event.getClass().getMethod("packet").invoke(event))) {
                      reject[0] = false;
                      event.cancel();
                    }
                  }));
      position.invoke(player, oldX, 199.75, oldZ);
      walking.invoke(player);
      check(
          !HookBridge.gameControls().isRotationApplied(rotation),
          "hard-cancelled walking does not authorize its applied angle");
      HookBridge.gameControls().beforeInput();
      walking.invoke(player); // Vanilla chose ground-only after caching the cancelled position.
      HookBridge.gameControls().beforeInput();
      check(
          !HookBridge.gameControls().previousRotation(rotation).isPresent(),
          "a hard-cancelled position cannot be repaired by a later ground-only walk");
      cacheY.setDouble(player, 201.0);
      walking.invoke(player);
      HookBridge.gameControls().beforeInput();
      check(
          HookBridge.gameControls().previousRotation(rotation).isPresent(),
          "a fresh positional walk repairs the hard-cancelled position receipt");
      position.invoke(player, oldX, 200.0, oldZ);
      walking.invoke(player);
      set(runtime, "nofall", "mode", "LIBRECRAFT");
      runtime.modules().setEnabled("nofall", true);
      fall.setFloat(player, 4);
      HookBridge.gameControls().beforeInput();
      fall.setFloat(player, 0);
      ground.setBoolean(player, true);
      // Foreign-thread, stale-manager and replay preflight calls cannot mutate or consume
      // this activation's pending landing. These exercise the guarded native boundary directly.
      Constructor<?> c04 =
          Class.forName("net.minecraft.network.play.client.C03PacketPlayer$C04PacketPlayerPosition")
              .getConstructor(double.class, double.class, double.class, boolean.class);
      Method observe =
          HookBridge.class.getMethod(
              "observeClientSend",
              Class.forName("net.minecraft.network.NetworkManager"),
              Class.forName("net.minecraft.network.Packet"));
      Object guarded = c04.newInstance(oldX, 195.0, oldZ, true);
      java.util.concurrent.atomic.AtomicReference<Throwable> foreignFailure =
          new java.util.concurrent.atomic.AtomicReference<>();
      Thread foreign =
          new Thread(
              () -> {
                try {
                  observe.invoke(null, peer.manager.get(), guarded);
                } catch (Throwable t) {
                  foreignFailure.set(t);
                }
              },
              "nofall-foreign-preflight");
      foreign.start();
      foreign.join(3000);
      check(
          !foreign.isAlive()
              && foreignFailure.get() == null
              && value(c03, guarded, "getPositionY", "func_149467_d") == 195.0,
          "foreign-thread preflight does not mutate landing packet");
      observe.invoke(null, peer.originalManager, guarded);
      check(
          value(c03, guarded, "getPositionY", "func_149467_d") == 195.0,
          "stale connection preflight does not mutate landing packet");
      perihelion.integration.minecraft189.network.RawHookBridge.replay(
          () -> {
            try {
              observe.invoke(null, peer.manager.get(), guarded);
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          });
      check(
          value(c03, guarded, "getPositionY", "func_149467_d") == 195.0,
          "replay preflight does not apply landing sequence twice");
      int negativesStart = peer.received.size();
      ground.setBoolean(player, false);
      send(peer, c04.newInstance(oldX, 195.0, oldZ, true));
      ground.setBoolean(player, true);
      send(peer, c04.newInstance(oldX, 195.0, oldZ, false));
      boundary(peer, 0x4e460006);
      List<Object> negatives =
          walks(peer.received.subList(negativesStart, peer.received.size()), c03);
      check(
          negatives.size() == 2
              && value(c03, negatives.get(0), "getPositionY", "func_149467_d") == 195.0
              && value(c03, negatives.get(1), "getPositionY", "func_149467_d") == 195.0,
          "airborne player and ungrounded packet neither prepend nor consume the pending landing");
      check(ground.getBoolean(player), "negative sends preserve local onGround");
      position.invoke(player, oldX, 199.0, oldZ);
      int start = peer.received.size();
      walking.invoke(player);
      check(
          cacheY.getDouble(player) == 199.0,
          "Librecraft offsets only the packet, leaving vanilla's cached walking Y at the original"
              + " landing Y");
      check(!ground.getBoolean(player), "landing sequence clears local onGround");
      boundary(peer, 0x4e460002);
      List<Object> landing = walks(peer.received.subList(start, peer.received.size()), c03);
      check(
          landing.size() == 3,
          "landing emits exactly two prepended positions and the original walking packet");
      double[] ys = {200.0 - 1.0784, 199.5, 200.0};
      for (int i = 0; i < landing.size(); i++) {
        Object packet = landing.get(i);
        check(
            (Boolean) method(c03, "isMoving", "func_149466_j").invoke(packet),
            "landing packet " + i + " carries coordinates");
        check(
            value(c03, packet, "getPositionX", "func_149464_c") == oldX
                && value(c03, packet, "getPositionZ", "func_149472_e") == oldZ,
            "landing packet " + i + " uses original packet X/Z");
        check(
            value(c03, packet, "getPositionY", "func_149467_d") == ys[i],
            "landing packet " + i + " serializes exact source Y");
        check(
            (Boolean) method(c03, "isOnGround", "func_149465_i").invoke(packet) == (i != 0),
            "landing packet " + i + " preserves source ground sequence");
      }
      HookBridge.gameControls().beforeInput();
      check(
          !HookBridge.gameControls().previousRotation(rotation).isPresent(),
          "packet-only landing offset cannot authorize an action from the unreported local"
              + " position");
      check(
          !HookBridge.gameControls().withPreviousAim(rotation, aim -> actions.incrementAndGet())
              && actions.get() == 0,
          "position-spoofed walking receipt cannot execute a PRE action callback");
      start = peer.received.size();
      ground.setBoolean(player, true);
      walking.invoke(player);
      boundary(peer, 0x4e460003);
      List<Object> next = walks(peer.received.subList(start, peer.received.size()), c03);
      check(
          next.size() == 1
              && !(Boolean) method(c03, "isMoving", "func_149466_j").invoke(next.get(0)),
          "unchanged next walk does not manufacture a return-position packet");
      HookBridge.gameControls().beforeInput();
      check(
          !HookBridge.gameControls().previousRotation(rotation).isPresent(),
          "ground-only walking does not repair the mismatched planned server position");
      cacheY.setDouble(
          player, 201.0); // Force an ordinary vanilla positional update in the fixture.
      walking.invoke(player);
      boundary(peer, 0x4e460007);
      HookBridge.gameControls().beforeInput();
      check(
          HookBridge.gameControls().previousRotation(rotation).isPresent(),
          "an ordinary correctly positioned walking packet restores previous-aim eligibility");
      // When vanilla selected a ground-only C03, changing its dormant Y must not upgrade its type.
      fall.setFloat(player, 4);
      HookBridge.gameControls().beforeInput();
      fall.setFloat(player, 0);
      ground.setBoolean(player, true);
      start = peer.received.size();
      walking.invoke(player);
      boundary(peer, 0x4e460004);
      List<Object> stationary = walks(peer.received.subList(start, peer.received.size()), c03);
      check(stationary.size() == 3, "stationary armed landing preserves the source triplet");
      check(
          !(Boolean) method(c03, "isMoving", "func_149466_j").invoke(stationary.get(2)),
          "ground-only original packet keeps its wire type");
      check(
          value(c03, stationary.get(0), "getPositionX", "func_149464_c") == 0
              && value(c03, stationary.get(0), "getPositionY", "func_149467_d") == 1.0 - 1.0784,
          "ground-only source uses dormant packet coordinates, not live player coordinates");
      fall.setFloat(player, 4);
      HookBridge.gameControls().beforeInput();
      fall.setFloat(player, 0);
      runtime.modules().setEnabled("nofall", false);
      ground.setBoolean(player, true);
      start = peer.received.size();
      walking.invoke(player);
      boundary(peer, 0x4e460005);
      check(
          walks(peer.received.subList(start, peer.received.size()), c03).size() == 1,
          "disabling an armed activation removes its landing hook");
      check(ground.getBoolean(player), "disabled pending landing leaves local ground unchanged");
      check(peer.failure.get() == null, "all NoFall packets decode through a real PLAY peer");
      System.out.println("PERIHELION_SMOKE NOFALL_PACKET_PARITY checks=" + checks + " PASS");
    } finally {
      runtime.modules().setEnabled("nofall", false);
      for (AutoCloseable resource : owned) resource.close();
      owner.close();
      position.invoke(player, oldX, oldY, oldZ);
      for (Map.Entry<Field, Object> value : saved.entrySet())
        value.getKey().set(player, value.getValue());
      screen.set(game, oldScreen);
      for (Map.Entry<Setting<?>, Object> value : settings.entrySet())
        restore(runtime, value.getKey(), value.getValue());
      for (Map.Entry<String, Boolean> value : enabled.entrySet())
        runtime.modules().setEnabled(value.getKey(), value.getValue());
      HookBridge.gameControls().apply();
    }
  }

  private static List<Object> walks(List<Object> packets, Class<?> c03) {
    List<Object> result = new ArrayList<>();
    for (Object p : packets) if (c03.isInstance(p)) result.add(p);
    return result;
  }

  private static double value(Class<?> type, Object packet, String mcp, String srg)
      throws Exception {
    return ((Number) method(type, mcp, srg).invoke(packet)).doubleValue();
  }

  private static void send(GameplayLoopback peer, Object packet) throws Exception {
    method(
            peer.manager.get().getClass(),
            new Class<?>[] {Class.forName("net.minecraft.network.Packet")},
            "sendPacket",
            "func_179290_a")
        .invoke(peer.manager.get(), packet);
  }

  private static void boundary(GameplayLoopback peer, int marker) throws Exception {
    Object fence =
        Class.forName("net.minecraft.network.play.client.C00PacketKeepAlive")
            .getConstructor(int.class)
            .newInstance(marker);
    method(
            peer.manager.get().getClass(),
            new Class<?>[] {Class.forName("net.minecraft.network.Packet")},
            "sendPacket",
            "func_179290_a")
        .invoke(peer.manager.get(), fence);
    long deadline = System.nanoTime() + 3_000_000_000L;
    while (System.nanoTime() < deadline) {
      if (peer.failure.get() != null) throw new AssertionError(peer.failure.get());
      for (Object p : peer.received)
        if (p.getClass().getSimpleName().equals("C00PacketKeepAlive")
            && ((Number) method(p.getClass(), "getKey", "func_149460_c").invoke(p)).intValue()
                == marker) return;
      Thread.sleep(2);
    }
    throw new AssertionError("NoFall PLAY packet boundary timed out");
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void restore(ClientRuntime runtime, Setting setting, Object value) {
    runtime.modules().setSetting("nofall", setting, value);
  }

  private static void check(boolean condition, String label) {
    checks++;
    if (!condition) throw new AssertionError(label);
  }
}
