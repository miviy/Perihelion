package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;
import static perihelion.smoke.RotationPipelineProbe.camera;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import perihelion.core.control.*;
import perihelion.core.event.EventOwner;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleEntry;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.control.PlayerRotationAccess;
import perihelion.integration.minecraft189.event.PacketSendEvent;

/** Yuri fireball turning, real walking-before-use order and competing-owner exclusion. */
public final class LongJumpParityProbe {
  private static int checks;
  private static final AtomicInteger network = new AtomicInteger();

  public static int networkEvents() {
    return network.get();
  }

  public static void run(ClientRuntime runtime) throws Exception {
    checks = 0;
    network.set(0);
    Object game =
        method(Class.forName("net.minecraft.client.Minecraft"), "getMinecraft", "func_71410_x")
            .invoke(null);
    Object player = field(game.getClass(), "thePlayer", "field_71439_g").get(game);
    Object inventory = field(player.getClass(), "inventory", "field_71071_by").get(player);
    Object[] items =
        (Object[]) field(inventory.getClass(), "mainInventory", "field_70462_a").get(inventory);
    Object[] oldItems = items.clone();
    Field selected = field(inventory.getClass(), "currentItem", "field_70461_c"),
        screen = field(game.getClass(), "currentScreen", "field_71462_r"),
        focus = field(game.getClass(), "inGameHasFocus", "field_71415_G");
    int oldSlot = selected.getInt(inventory);
    Object oldScreen = screen.get(game);
    boolean oldFocus = focus.getBoolean(game);
    Map<Field, Object> savedPlayer = new LinkedHashMap<>();
    for (String[] names :
        new String[][] {
          {"rotationYaw", "field_70177_z"},
          {"rotationPitch", "field_70125_A"},
          {"lastReportedYaw", "field_175164_bL"},
          {"lastReportedPitch", "field_175165_bM"},
          {"onGround", "field_70122_E"},
          {"motionX", "field_70159_w"},
          {"motionY", "field_70181_x"},
          {"motionZ", "field_70179_y"}
        }) {
      Field field = field(player.getClass(), names);
      savedPlayer.put(field, field.get(player));
    }
    Map<String, Boolean> enabled = new LinkedHashMap<>();
    // Isolate this action from other world modules, then restore their requested states.
    for (ModuleEntry entry : runtime.modules().entries()) {
      if (!entry.info().requiresWorld()) continue;
      enabled.put(entry.info().id(), entry.requestedEnabled());
      runtime.modules().setEnabled(entry.info().id(), false);
    }
    Map<Setting<?>, Object> settings = new LinkedHashMap<>();
    for (Setting<?> setting : runtime.modules().require("longjump").module().settings().values())
      settings.put(setting, setting.get());
    EventOwner owner =
        new EventOwner(
            "longjump-parity",
            failure -> {
              throw new AssertionError(failure);
            });
    owner.activate();
    List<AutoCloseable> resources = new ArrayList<>();
    ControlRequest<Rotation> blocker =
        runtime.controls().scope(owner, resources::add).rotation(1000);
    AtomicInteger uses = new AtomicInteger();
    List<Rotation> useAngles = new ArrayList<>();
    resources.add(
        runtime
            .networkEvents()
            .subscribe(
                owner,
                PacketSendEvent.class,
                Integer.MAX_VALUE,
                true,
                event -> {
                  network.incrementAndGet();
                  Object packet = event.getClass().getMethod("packet").invoke(event);
                  if (packet.getClass().getSimpleName().equals("C08PacketPlayerBlockPlacement")) {
                    uses.incrementAndGet();
                    useAngles.add(reported(player));
                  }
                }));
    try (GameplayLoopback peer = new GameplayLoopback(game)) {
      screen.set(game, null);
      focus.setBoolean(game, true);
      field(player.getClass(), "onGround", "field_70122_E").setBoolean(player, false);
      Arrays.fill(items, null);
      items[0] = stack("stick");
      items[3] = stack("fire_charge");
      set(runtime, "longjump", "fireball", "true");
      set(runtime, "longjump", "delay", "400");
      int marker = 0x4c4a0000;
      for (String mode : new String[] {"LEGIT", "LAG"}) {
        selected.setInt(inventory, 0);
        set(runtime, "longjump", "fireball-mode", mode);
        blocker.set(Rotation.serverUnwrapped(150, -90));
        motion(player);
        Rotation incoming = reported(player);
        check(same(incoming, Rotation.serverUnwrapped(150, -90)), mode + " submits incoming aim");
        camera(player, 0, 0);
        blocker.clear();
        int beforeUses = uses.get(), wireStart = peer.received.size();
        runtime.modules().setEnabled("longjump", true);
        HookBridge.gameControls().beforeInput();
        Rotation first = runtime.controls().rotation().get();
        boundedTurn(incoming, first, mode + " first handoff");
        check(selected.getInt(inventory) == 3, mode + " selects fireball on tick one");
        check(uses.get() == beforeUses, mode + " tick one does not use before walking");
        motion(player);
        check(same(reported(player), first), mode + " walking submits the first smooth angle");
        HookBridge.gameControls().beforeInput();
        check(uses.get() == beforeUses + 1, mode + " uses exactly once on tick two");
        check(
            same(useAngles.get(useAngles.size() - 1), first), mode + " use follows submitted aim");
        check(
            !same(runtime.controls().rotation().get(), first),
            mode + " does not substitute the newly computed tick-two aim into the earlier use");
        motion(player);
        if (mode.equals("LEGIT")) {
          for (int tick = 3; tick <= 5; tick++) {
            HookBridge.gameControls().beforeInput();
            motion(player);
          }
          check(
              !runtime.modules().require("longjump").requestedEnabled(),
              "Legit stops at tick five");
        } else runtime.modules().setEnabled("longjump", false);
        HookBridge.gameControls().apply();
        check(uses.get() == beforeUses + 1, mode + " cleanup emits no repeated fireball use");
        check(
            !runtime.controls().rotation().isPresent()
                && !runtime.controls().hotbar().isPresent()
                && selected.getInt(inventory) == 0,
            mode + " cleanup releases rotation and restores its owned item slot");
        boundary(peer, ++marker);
        verifyWire(peer.received.subList(wireStart, peer.received.size()), first, mode);
      }

      set(runtime, "longjump", "fireball-mode", "LEGIT");
      blocker.set(Rotation.serverUnwrapped(150, -90));
      motion(player);
      camera(player, 0, 0);
      int beforeUses = uses.get();
      runtime.modules().setEnabled("longjump", true);
      for (int tick = 1; tick <= 2; tick++) {
        HookBridge.gameControls().beforeInput();
        motion(player);
      }
      check(uses.get() == beforeUses, "a higher-priority rotation owner prevents tick-two use");
      blocker.clear();
      HookBridge.gameControls().beforeInput();
      boundedTurn(
          Rotation.serverUnwrapped(150, -90),
          runtime.controls().rotation().get(),
          "handoff after two losing ticks");
      check(uses.get() == beforeUses, "a skipped tick-two action is not retried on tick three");
      runtime.modules().setEnabled("longjump", false);
      boundary(peer, ++marker);
      check(peer.failure.get() == null, "LongJump packets decode through a real PLAY peer");
      System.out.println("PERIHELION_SMOKE LONGJUMP_PARITY checks=" + checks + " PASS");
    } finally {
      runtime.modules().setEnabled("longjump", false);
      blocker.clear();
      for (AutoCloseable resource : resources) resource.close();
      owner.close();
      for (Map.Entry<Setting<?>, Object> value : settings.entrySet())
        restore(runtime, value.getKey(), value.getValue());
      System.arraycopy(oldItems, 0, items, 0, items.length);
      selected.setInt(inventory, oldSlot);
      for (Map.Entry<Field, Object> value : savedPlayer.entrySet())
        value.getKey().set(player, value.getValue());
      screen.set(game, oldScreen);
      focus.setBoolean(game, oldFocus);
      HookBridge.gameControls().apply();
      for (Map.Entry<String, Boolean> entry : enabled.entrySet())
        if (entry.getValue()) runtime.modules().setEnabled(entry.getKey(), true);
    }
  }

  private static Rotation reported(Object player) {
    PlayerRotationAccess access = (PlayerRotationAccess) player;
    return Rotation.serverUnwrapped(
        access.perihelion$reportedYaw(), access.perihelion$reportedPitch());
  }

  private static void boundedTurn(Rotation incoming, Rotation next, String label) {
    double moved = Math.hypot(wrap(next.yaw() - incoming.yaw()), next.pitch() - incoming.pitch());
    double remaining = Math.hypot(wrap(next.yaw()), 90 - next.pitch());
    // Source speed10 has a 180..181 degree vector budget plus small noise/GCD rounding.
    check(
        moved >= 178 && moved <= 183 && remaining > 40,
        label
            + " limits the >234-degree combined turn instead of snapping down (moved="
            + moved
            + ", remaining="
            + remaining
            + ")");
  }

  private static double wrap(double yaw) {
    yaw %= 360;
    if (yaw >= 180) yaw -= 360;
    if (yaw < -180) yaw += 360;
    return yaw;
  }

  private static boolean same(Rotation left, Rotation right) {
    return Math.abs(left.yaw() - right.yaw()) < 1e-4
        && Math.abs(left.pitch() - right.pitch()) < 1e-4;
  }

  private static void verifyWire(List<Object> packets, Rotation first, String mode)
      throws Exception {
    Class<?> c03 = Class.forName("net.minecraft.network.play.client.C03PacketPlayer");
    Object previousLook = null;
    int uses = 0;
    for (Object packet : packets) {
      if (c03.isInstance(packet)
          && (Boolean) method(c03, "getRotating", "func_149463_k").invoke(packet))
        previousLook = packet;
      if (!packet.getClass().getSimpleName().equals("C08PacketPlayerBlockPlacement")) continue;
      uses++;
      check(previousLook != null, mode + " wire submits a rotating walking packet before C08");
      Rotation angle =
          Rotation.serverUnwrapped(
              ((Number) method(c03, "getYaw", "func_149462_g").invoke(previousLook)).floatValue(),
              ((Number) method(c03, "getPitch", "func_149470_h").invoke(previousLook))
                  .floatValue());
      check(same(angle, first), mode + " serialized use follows the first smooth walking angle");
    }
    check(uses == 1, mode + " wire contains exactly one fireball C08");
  }

  private static void boundary(GameplayLoopback peer, int marker) throws Exception {
    Class<?> packet = Class.forName("net.minecraft.network.Packet");
    Object fence =
        Class.forName("net.minecraft.network.play.client.C00PacketKeepAlive")
            .getConstructor(int.class)
            .newInstance(marker);
    method(peer.manager.get().getClass(), new Class<?>[] {packet}, "sendPacket", "func_179290_a")
        .invoke(peer.manager.get(), fence);
    long deadline = System.nanoTime() + 3_000_000_000L;
    while (System.nanoTime() < deadline) {
      if (peer.failure.get() != null) throw new AssertionError(peer.failure.get());
      for (Object received : peer.received)
        if (received.getClass().getSimpleName().equals("C00PacketKeepAlive")
            && ((Number) method(received.getClass(), "getKey", "func_149460_c").invoke(received))
                    .intValue()
                == marker) return;
      Thread.sleep(2);
    }
    throw new AssertionError("LongJump packet boundary did not reach PLAY peer");
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void restore(ClientRuntime runtime, Setting setting, Object value) {
    runtime.modules().setSetting("longjump", setting, value);
  }

  private static void check(boolean condition, String label) {
    checks++;
    if (!condition) throw new AssertionError(label);
  }
}
