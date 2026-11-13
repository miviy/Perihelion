package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;
import static perihelion.smoke.RotationPipelineProbe.position;

import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.lwjgl.input.Keyboard;
import perihelion.core.control.*;
import perihelion.core.event.EventOwner;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.*;

/** Final cross-check of stateful helpers not covered by the earlier per-module probes. */
public final class FinalParityProbe {
  private static int checks;
  private static final AtomicInteger network = new AtomicInteger();

  public static int networkEvents() {
    return network.get();
  }

  static void run(ClientRuntime r) throws Exception {
    checks = 0;
    network.set(0);
    Object game =
        method(Class.forName("net.minecraft.client.Minecraft"), "getMinecraft", "func_71410_x")
            .invoke(null);
    Object p = field(game.getClass(), "thePlayer", "field_71439_g").get(game),
        world = field(game.getClass(), "theWorld", "field_71441_e").get(game);
    Object settings = field(game.getClass(), "gameSettings", "field_71474_y").get(game),
        input = field(p.getClass(), "movementInput", "field_71158_b").get(p);
    String[] ids = {
      "aura",
      "flight",
      "targetstrafe",
      "derp",
      "inputfixes",
      "criticals",
      "lagrange",
      "tickbase",
      "phase",
      "waterwalk",
      "timer"
    };
    Map<String, Boolean> enabled = new LinkedHashMap<>();
    Map<Setting<?>, Object> values = new LinkedHashMap<>();
    for (String id : ids) {
      enabled.put(id, r.modules().require(id).requestedEnabled());
      r.modules().setEnabled(id, false);
      for (Setting<?> s : r.modules().require(id).module().settings().values())
        values.put(s, s.get());
    }
    Field screen = field(game.getClass(), "currentScreen", "field_71462_r"),
        focus = field(game.getClass(), "inGameHasFocus", "field_71415_G");
    Object oldScreen = screen.get(game);
    boolean oldFocus = focus.getBoolean(game);
    Field click = field(game.getClass(), "leftClickCounter", "field_71429_W");
    int oldClick = click.getInt(game);
    Field perspective = field(settings.getClass(), "thirdPersonView", "field_74320_O");
    int oldView = perspective.getInt(settings);
    Map<Field, Object> saved = new LinkedHashMap<>();
    for (String[] names :
        new String[][] {
          {"motionX", "field_70159_w"},
          {"motionY", "field_70181_x"},
          {"motionZ", "field_70179_y"},
          {"rotationYaw", "field_70177_z"},
          {"rotationPitch", "field_70125_A"},
          {"onGround", "field_70122_E"},
          {"prevPosX", "field_70169_q"},
          {"prevPosZ", "field_70166_s"},
          {"ticksExisted", "field_70173_aa"},
          {"isCollidedHorizontally", "field_70123_F"}
        }) {
      Field f = field(p.getClass(), names);
      saved.put(f, f.get(p));
    }
    Field inputForward = field(input.getClass(), "moveForward", "field_78900_b");
    float oldForward = inputForward.getFloat(input);
    Object forward = field(settings.getClass(), "keyBindForward", "field_74351_w").get(settings),
        sneak = field(settings.getClass(), "keyBindSneak", "field_74311_E").get(settings);
    Field pressed = field(forward.getClass(), "pressed", "field_74513_e"),
        code = field(sneak.getClass(), "keyCode", "field_74512_d");
    boolean oldPressed = pressed.getBoolean(forward);
    int oldCode = code.getInt(sneak);
    ByteBuffer keyboard = (ByteBuffer) field(Keyboard.class, "keyDownBuffer").get(null);
    byte oldShift = keyboard.get(Keyboard.KEY_LSHIFT);
    Object derp = r.modules().require("derp").module();
    Field spin = field(derp.getClass(), "spinningYaw");
    int oldSpin = spin.getInt(derp);
    Object pulse =
        field(r.modules().require("timer").module().getClass(), "pulse")
            .get(r.modules().require("timer").module());
    Map<Field, Object> savedPulse = new LinkedHashMap<>();
    for (Field f : pulse.getClass().getDeclaredFields()) {
      f.setAccessible(true);
      savedPulse.put(f, f.get(pulse));
    }
    double x = number(p, "posX", "field_70165_t"),
        y = number(p, "posY", "field_70163_u"),
        z = number(p, "posZ", "field_70161_v");
    Object pig =
        Class.forName("net.minecraft.entity.passive.EntityPig")
            .getConstructor(Class.forName("net.minecraft.world.World"))
            .newInstance(world);
    position(pig, x + 4, y, z);
    method(
            world.getClass(),
            new Class<?>[] {int.class, Class.forName("net.minecraft.entity.Entity")},
            "addEntityToWorld",
            "func_73027_a")
        .invoke(world, -976631, pig);
    EventOwner owner =
        new EventOwner(
            "final-parity",
            t -> {
              throw new AssertionError(t);
            });
    owner.activate();
    List<AutoCloseable> resources = new ArrayList<>();
    r.networkEvents()
        .subscribe(
            owner, PacketSendEvent.class, Integer.MAX_VALUE, true, e -> network.incrementAndGet());
    ControlRequest<Rotation> blocker = r.controls().scope(owner, resources::add).rotation(1000);
    boolean sprinting = (Boolean) method(p.getClass(), "isSprinting", "func_70051_ag").invoke(p);
    Method sprint =
        method(p.getClass(), new Class<?>[] {boolean.class}, "setSprinting", "func_70031_b");
    try {
      screen.set(game, null);
      focus.setBoolean(game, true);
      perspective.setInt(settings, 1);
      set(r, "derp", "mode", "CLIENT");
      set(r, "derp", "rotation-speed", "2.5");
      r.modules().setEnabled("derp", true);
      pre(r);
      check(
          spin.getInt(derp) == oldSpin + 20,
          "Derp uses the source integer increment for off-step saved speed");
      r.modules().setEnabled("derp", false);
      r.modules().setEnabled("derp", true);
      pre(r);
      check(spin.getInt(derp) == oldSpin + 40, "Derp yaw survives ordinary toggles");
      field(p.getClass(), "rotationYaw", "field_70177_z").setFloat(p, 170);
      field(p.getClass(), "rotationPitch", "field_70125_A").setFloat(p, 45);
      set(r, "derp", "mode", "SERVER");
      set(r, "derp", "rotation-speed", "1");
      set(r, "derp", "pitch", "90");
      pre(r);
      Rotation switched = r.controls().rotation().get();
      double switchedYaw = (switched.yaw() - 170 + 540) % 360 - 180;
      check(
          Math.abs(switchedYaw) < 30 && switched.pitch() >= 45 && switched.pitch() < 75,
          "live Derp mode change smooths from the current camera, not its enable-time view");
      r.modules().setEnabled("derp", false);
      spin.setInt(derp, 0);
      field(p.getClass(), "rotationYaw", "field_70177_z").setFloat(p, 0);
      field(p.getClass(), "rotationPitch", "field_70125_A").setFloat(p, 0);
      set(r, "derp", "mode", "SERVER");
      set(r, "derp", "pitch", "90");
      set(r, "derp", "rotation-speed", "1");
      r.modules().setEnabled("derp", true);
      pre(r);
      Rotation aim = r.controls().rotation().get();
      check(
          aim.pitch() > 0 && aim.pitch() < 30,
          "server Derp smooths its first pitch instead of snapping to 90");
      MovementYawEvent jump = new MovementYawEvent(123, true);
      r.clientEvents().post(jump);
      check(jump.yaw() == aim.yaw(), "Derp jump correction follows its submitted smooth yaw");
      r.modules().setEnabled("derp", false);
      code.setInt(sneak, Keyboard.KEY_LSHIFT);
      keyboard.put(Keyboard.KEY_LSHIFT, (byte) 1);
      screen.set(
          game, Class.forName("net.minecraft.client.gui.GuiChat").getConstructor().newInstance());
      r.modules().setEnabled("inputfixes", true);
      pre(r);
      screen.set(game, null);
      pre(r);
      check(
          Boolean.TRUE.equals(r.controls().key(InputKey.SNEAK).orElse(null)),
          "Input Fixes restores held Sneak after a screen closes");
      for (int i = 0; i < 3; i++) {
        r.controls().beginTick();
        pre(r);
        check(
            Boolean.TRUE.equals(r.controls().key(InputKey.SNEAK).orElse(null)),
            "screen-close Sneak restoration survives subsequent held ticks");
      }
      keyboard.put(Keyboard.KEY_LSHIFT, (byte) 0);
      pre(r);
      pre(r);
      check(
          !r.controls().key(InputKey.SNEAK).isPresent(),
          "physical release retires the restored Sneak claim");
      click.setInt(game, 4);
      r.clientEvents().post(PlayerControlEvent.BEFORE_MOTION);
      check(click.getInt(game) == 0, "Input Fixes clears miss delay in PRE motion");
      click.setInt(game, 4);
      r.clientEvents().post(PlayerControlEvent.AFTER_MOTION);
      check(click.getInt(game) == 0, "Input Fixes clears miss delay in POST motion");
      r.modules().setEnabled("inputfixes", false);
      set(r, "aura", "targets", "ANIMALS");
      set(r, "aura", "through-walls", "true");
      set(r, "aura", "rotations", "NONE");
      set(r, "aura", "auto-disable", "false");
      set(r, "aura", "fov", "360");
      r.modules().setEnabled("aura", true);
      r.modules().setEnabled("flight", true);
      r.modules().setEnabled("targetstrafe", true);
      pressed.setBoolean(forward, true);
      inputForward.setFloat(input, 1);
      blocker.set(Rotation.server(130, 0));
      pre(r);
      MovementYawEvent orbit = new MovementYawEvent(0), orbitJump = new MovementYawEvent(0, true);
      r.clientEvents().post(orbit);
      r.clientEvents().post(orbitJump);
      check(
          orbit.yaw() == orbitJump.yaw() && Math.abs(orbit.yaw() - 130) > .1,
          "Target Strafe jump and walking share orbit yaw despite another packet rotation");
      for (String id : new String[] {"targetstrafe", "flight", "aura"})
        r.modules().setEnabled(id, false);
      blocker.clear();
      packets(r, game, p, pig, inputForward, input);
      geometry(r, game, p, world, settings);
      set(r, "timer", "mode", "CONSTANT");
      set(r, "timer", "time-amount", "3");
      set(r, "timer", "time-amount-2", "2");
      field(pulse.getClass(), "start").setLong(pulse, 0);
      r.modules().setEnabled("timer", true);
      pre(r);
      check(r.controls().timerSpeed().get() == 3, "Timer constant mode sets its owned speed");
      set(r, "timer", "mode", "PULSE");
      pre(r);
      check(
          r.controls().timerSpeed().get() == 3,
          "constant-to-pulse reset update retains the preceding speed");
      r.modules().setEnabled("timer", false);
      long priorStart = field(pulse.getClass(), "start").getLong(pulse);
      r.modules().setEnabled("timer", true);
      check(
          field(pulse.getClass(), "start").getLong(pulse) == priorStart,
          "Timer toggle preserves the source pulse clock");
      r.modules().setEnabled("timer", false);
      for (String id : ids)
        check(
            r.modules().require(id).state() != perihelion.core.module.ModuleState.FAULTED,
            id + " remains unfaulted");
      System.out.println(
          "PERIHELION_SMOKE FINAL_PARITY checks="
              + checks
              + " networkEvents="
              + network.get()
              + " PASS");
    } finally {
      for (String id : ids) r.modules().setEnabled(id, false);
      for (String id : ids)
        for (Setting<?> s : r.modules().require(id).module().settings().values())
          restore(r, id, s, values.get(s));
      for (AutoCloseable c : resources) c.close();
      owner.close();
      for (Map.Entry<Field, Object> e : saved.entrySet()) e.getKey().set(p, e.getValue());
      sprint.invoke(p, sprinting);
      inputForward.setFloat(input, oldForward);
      pressed.setBoolean(forward, oldPressed);
      code.setInt(sneak, oldCode);
      keyboard.put(Keyboard.KEY_LSHIFT, oldShift);
      spin.setInt(derp, oldSpin);
      for (Map.Entry<Field, Object> e : savedPulse.entrySet()) e.getKey().set(pulse, e.getValue());
      screen.set(game, oldScreen);
      focus.setBoolean(game, oldFocus);
      click.setInt(game, oldClick);
      perspective.setInt(settings, oldView);
      method(world.getClass(), new Class<?>[] {int.class}, "removeEntityFromWorld", "func_73028_b")
          .invoke(world, -976631);
      HookBridge.gameControls().apply();
      for (String id : ids) if (enabled.get(id)) r.modules().setEnabled(id, true);
    }
  }

  private static void packets(
      ClientRuntime r, Object game, Object p, Object target, Field inputForward, Object input)
      throws Exception {
    try (GameplayLoopback peer = new GameplayLoopback(game)) {
      Object handler = method(game.getClass(), "getNetHandler", "func_147114_u").invoke(game);
      Method send =
          method(
              handler.getClass(),
              new Class<?>[] {Class.forName("net.minecraft.network.Packet")},
              "addToSendQueue",
              "func_147297_a");
      Constructor<?> look =
          Class.forName("net.minecraft.network.play.client.C03PacketPlayer$C05PacketPlayerLook")
              .getConstructor(float.class, float.class, boolean.class);
      Constructor<?> attack =
          PlayerAttackEvent.class.getConstructor(
              Class.forName("net.minecraft.entity.EntityLivingBase"));
      field(p.getClass(), "onGround", "field_70122_E").setBoolean(p, true);
      set(r, "criticals", "mode", "PACKET");
      r.modules().setEnabled("criticals", true);
      for (int i = 0; i < 7; i++) r.clientEvents().post(PlayerControlEvent.BEFORE_MOTION);
      set(r, "criticals", "mode", "NCP");
      r.clientEvents().post(attack.newInstance(target));
      send.invoke(handler, look.newInstance(1f, 0f, false));
      peer.await(1);
      check(peer.received.size() == 1, "Packet-mode ticks cannot prime NCP Criticals");
      for (int i = 0; i < 7; i++) r.clientEvents().post(PlayerControlEvent.BEFORE_MOTION);
      r.clientEvents().post(attack.newInstance(target));
      send.invoke(handler, look.newInstance(2f, 0f, false));
      peer.await(5);
      double y = number(p, "posY", "field_70163_u");
      double[] offsets = {.00001, .000008, .0000002};
      for (int i = 0; i < 3; i++) {
        Object packet = peer.received.get(i + 1);
        double py =
            ((Number) method(packet.getClass(), "getPositionY", "func_149467_d").invoke(packet))
                .doubleValue();
        check(
            packet.getClass().getSimpleName().equals("C04PacketPlayerPosition")
                && Math.abs(py - y - offsets[i]) < 1e-10,
            "NCP Criticals serialized position offset " + i);
      }
      r.clientEvents().post(attack.newInstance(target));
      send.invoke(handler, look.newInstance(3f, 0f, false));
      peer.await(6);
      check(peer.received.size() == 6, "NCP critical sequence resets its seven-motion gate");
      r.modules().setEnabled("criticals", false);
      r.modules().setEnabled("aura", true);
      field(r.modules().require("aura").module().getClass(), "target")
          .set(r.modules().require("aura").module(), target);
      inputForward.setFloat(input, 1);
      set(r, "lagrange", "mode", "LAG");
      set(r, "lagrange", "min-delay", "500");
      set(r, "lagrange", "max-delay", "500");
      r.modules().setEnabled("lagrange", true);
      r.clientEvents().post(PlayerControlEvent.BEFORE_MOTION);
      send.invoke(handler, look.newInstance(4f, 0f, false));
      peer.await(7);
      check(
          peer.received.size() == 7,
          "Lag Range's initial walking-window packet passes before POST arming");
      r.clientEvents().post(PlayerControlEvent.AFTER_MOTION);
      send.invoke(handler, look.newInstance(5f, 0f, false));
      peer.client.eventLoop().submit(() -> {}).sync();
      check(
          HookBridge.gameControls().outboundOrder().owner() != null,
          "Lag Range captures after POST");
      Thread.sleep(120);
      r.clientEvents().post(PlayerControlEvent.AFTER_MOTION);
      peer.await(8);
      check(
          HookBridge.gameControls().outboundOrder().owner() == null,
          "single spoof lease flushes before a 500ms target duration completes");
      send.invoke(handler, look.newInstance(6f, 0f, false));
      peer.await(9);
      check(
          peer.received.size() == 9,
          "expired Lag lease does not silently rearm during the same range cycle");
      set(r, "lagrange", "mode", "BLINK");
      r.clientEvents().post(PlayerControlEvent.BEFORE_MOTION);
      send.invoke(handler, look.newInstance(7f, 0f, false));
      peer.client.eventLoop().submit(() -> {}).sync();
      check(
          HookBridge.gameControls().outboundOrder().owner() != null,
          "Blink Range captures in its starting PRE window");
      set(r, "lagrange", "mode", "LAG");
      peer.await(10);
      check(
          HookBridge.gameControls().outboundOrder().owner() == null,
          "mode change flushes the old ordered sequence");
      r.modules().setEnabled("lagrange", false);
      r.modules().setEnabled("aura", false);
    } finally {
      r.modules().setEnabled("criticals", false);
      r.modules().setEnabled("lagrange", false);
      r.modules().setEnabled("aura", false);
    }
  }

  private static void geometry(
      ClientRuntime r, Object game, Object p, Object world, Object settings) throws Exception {
    double ox = number(p, "posX", "field_70165_t"),
        oy = number(p, "posY", "field_70163_u"),
        oz = number(p, "posZ", "field_70161_v");
    int x = (int) Math.floor(ox), z = (int) Math.floor(oz);
    Class<?> pos = Class.forName("net.minecraft.util.BlockPos"),
        state = Class.forName("net.minecraft.block.state.IBlockState");
    Method get = method(world.getClass(), new Class<?>[] {pos}, "getBlockState", "func_180495_p"),
        put =
            method(
                world.getClass(),
                new Class<?>[] {pos, state, int.class},
                "setBlockState",
                "func_180501_a");
    Object a = pos.getConstructor(int.class, int.class, int.class).newInstance(x, 199, z),
        b = pos.getConstructor(int.class, int.class, int.class).newInstance(x + 1, 199, z);
    Object oldA = get.invoke(world, a), oldB = get.invoke(world, b);
    Object jump = field(settings.getClass(), "keyBindJump", "field_74314_A").get(settings);
    Field pressed = field(jump.getClass(), "pressed", "field_74513_e");
    boolean oldJump = pressed.getBoolean(jump);
    try (GameplayLoopback peer = new GameplayLoopback(game)) {
      position(p, x + .95, 200, z + .5);
      field(p.getClass(), "ticksExisted", "field_70173_aa").setInt(p, 20);
      set(r, "waterwalk", "mode", "NCP");
      r.modules().setEnabled("waterwalk", true);
      put.invoke(world, a, blockState("water"), 2);
      put.invoke(world, b, blockState("stone"), 2);
      MotionPacketEvent mixed = new MotionPacketEvent(200, false);
      r.clientEvents().post(mixed);
      check(mixed.y() == 200, "Water Walk rejects a mixed liquid/solid footprint");
      put.invoke(world, b, blockState("air"), 2);
      MotionPacketEvent liquid = new MotionPacketEvent(200, false);
      r.clientEvents().post(liquid);
      check(
          liquid.y() == 200 - .015625, "Water Walk offsets a liquid/air footprint on an even tick");
      put.invoke(world, a, blockState("air"), 2);
      put.invoke(world, b, blockState("water"), 2);
      MotionPacketEvent adjacent = new MotionPacketEvent(200, false);
      r.clientEvents().post(adjacent);
      check(
          adjacent.y() == 200 - .015625,
          "Water Walk sees liquid at the footprint edge while its center is air");
      r.modules().setEnabled("waterwalk", false);
      put.invoke(world, a, blockState("stone"), 2);
      put.invoke(world, b, blockState("air"), 2);
      set(r, "phase", "mode", "POLAR");
      r.modules().setEnabled("phase", true);
      pressed.setBoolean(jump, true);
      position(p, x + .5, 200.2, z + .5);
      field(p.getClass(), "prevPosX", "field_70169_q").setDouble(p, x + .5);
      field(p.getClass(), "prevPosZ", "field_70166_s").setDouble(p, z + .5);
      field(p.getClass(), "motionX", "field_70159_w").setDouble(p, .3);
      field(p.getClass(), "motionY", "field_70181_x").setDouble(p, -.1);
      MotionPacketEvent polar = new MotionPacketEvent(200.2, false);
      r.clientEvents().post(polar);
      check(
          Math.abs(number(p, "motionY", "field_70181_x") - (-.1 - .091f)) < 1e-7
              && !polar.grounded(),
          "Polar uses block-coordinate ground distance and position-based stillness");
      field(p.getClass(), "prevPosX", "field_70169_q").setDouble(p, x + .4);
      field(p.getClass(), "motionY", "field_70181_x").setDouble(p, -.1);
      r.clientEvents().post(new MotionPacketEvent(200.2, false));
      check(
          number(p, "motionY", "field_70181_x") == -.1,
          "Polar ignores a player who actually changed horizontal position");
      field(p.getClass(), "prevPosX", "field_70169_q").setDouble(p, x + .5);
      position(p, x + .5, 200.05, z + .5);
      field(p.getClass(), "motionY", "field_70181_x").setDouble(p, -.1);
      MotionPacketEvent grounded = new MotionPacketEvent(200.05, false);
      r.clientEvents().post(grounded);
      check(
          grounded.grounded(),
          "Polar spoofs ground below the strict 1.1 block-coordinate threshold");
      set(r, "phase", "mode", "NORMAL");
      field(p.getClass(), "isCollidedHorizontally", "field_70123_F").setBoolean(p, false);
      position(p, x + .5, 199.9, z + .5);
      field(p.getClass(), "ticksExisted", "field_70173_aa").setInt(p, 4);
      field(p.getClass(), "motionX", "field_70159_w").setDouble(p, 1);
      r.clientEvents().post(new MotionPacketEvent(199.9, false));
      check(
          number(p, "motionX", "field_70159_w") == 1,
          "Normal Phase excludes players younger than five ticks from inside-block handling");
      field(p.getClass(), "ticksExisted", "field_70173_aa").setInt(p, 5);
      r.clientEvents().post(new MotionPacketEvent(199.9, false));
      check(
          Math.abs(number(p, "motionX", "field_70159_w") - .3) < 1e-9,
          "Normal Phase uses exact bounding-box intersection at five ticks");
      peer.await(1);
      check(
          peer.received.size() == 1
              && peer.received.get(0).getClass().getSimpleName().equals("C04PacketPlayerPosition"),
          "Normal Phase serializes one silent position packet through the controlled TCP peer");
    } finally {
      r.modules().setEnabled("phase", false);
      r.modules().setEnabled("waterwalk", false);
      pressed.setBoolean(jump, oldJump);
      put.invoke(world, a, oldA, 2);
      put.invoke(world, b, oldB, 2);
      position(p, ox, oy, oz);
    }
  }

  private static Object blockState(String name) throws Exception {
    Class<?> block = Class.forName("net.minecraft.block.Block");
    Object b =
        method(block, new Class<?>[] {String.class}, "getBlockFromName", "func_149684_b")
            .invoke(null, name);
    return method(block, "getDefaultState", "func_176223_P").invoke(b);
  }

  private static void pre(ClientRuntime r) {
    r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
  }

  private static double number(Object p, String... names) throws Exception {
    return ((Number) field(p.getClass(), names).get(p)).doubleValue();
  }

  private static void restore(ClientRuntime r, String id, Setting s, Object value) {
    r.modules().setSetting(id, s, value);
  }

  private static void check(boolean b, String message) {
    checks++;
    if (!b) throw new AssertionError(message);
  }
}
