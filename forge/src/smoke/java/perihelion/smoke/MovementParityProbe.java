package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;

import io.netty.channel.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import perihelion.core.control.*;
import perihelion.core.event.EventOwner;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.control.*;
import perihelion.integration.minecraft189.event.*;

/** Player-lifetime movement counters, Librecraft decisions and PRE update packet counts. */
public final class MovementParityProbe {
  private static int checks;
  private static final AtomicInteger networkEvents = new AtomicInteger();

  public static int networkEvents() {
    return networkEvents.get();
  }

  static void run(ClientRuntime r) throws Exception {
    checks = 0;
    networkEvents.set(0);
    Object game =
        method(Class.forName("net.minecraft.client.Minecraft"), "getMinecraft", "func_71410_x")
            .invoke(null);
    Object p = field(game.getClass(), "thePlayer", "field_71439_g").get(game);
    Object world = field(game.getClass(), "theWorld", "field_71441_e").get(game);
    Object input = field(p.getClass(), "movementInput", "field_71158_b").get(p);
    Object inventory = field(p.getClass(), "inventory", "field_71071_by").get(p);
    Object[] items =
        (Object[]) field(inventory.getClass(), "mainInventory", "field_70462_a").get(inventory);
    Object[] savedItems = items.clone();
    GroundTickHistory history = ((PlayerGroundAccess) p).perihelion$groundTicks();
    Map<Field, Object> savedHistory = saveAll(history);
    Map<Field, Object> saved = new LinkedHashMap<>();
    for (String[] names :
        new String[][] {
          {"motionX", "field_70159_w"},
          {"motionY", "field_70181_x"},
          {"motionZ", "field_70179_y"},
          {"onGround", "field_70122_E"},
          {"rotationYaw", "field_70177_z"},
          {"moveForward", "field_70701_bs"},
          {"moveStrafing", "field_70702_br"},
          {"hurtTime", "field_70737_aN"},
          {"isAirBorne", "field_70160_al"},
          {"jumpMovementFactor", "field_70747_aH"},
          {"isCollidedHorizontally", "field_70123_F"},
          {"inWater", "field_70171_ac"},
          {"isInWeb", "field_70134_J"}
        }) {
      Field f = field(p.getClass(), names);
      saved.put(f, f.get(p));
    }
    Map<Field, Object> savedInput = saveAll(input);
    Field screen = field(game.getClass(), "currentScreen", "field_71462_r");
    Object oldScreen = screen.get(game);
    Field focus = field(game.getClass(), "inGameHasFocus", "field_71415_G");
    boolean oldFocus = focus.getBoolean(game);
    Object autoPot = r.modules().require("autopot").module();
    Map<Field, Object> savedPotState = new LinkedHashMap<>();
    for (String name : new String[] {"attackTicks", "nextThrowAt"}) {
      Field v = field(autoPot.getClass(), name);
      savedPotState.put(v, v.get(autoPot));
    }
    Field selected = field(inventory.getClass(), "currentItem", "field_70461_c");
    int oldSlot = selected.getInt(inventory);
    Object oldAttacker = method(p.getClass(), "getLastAttacker", "func_94060_bK").invoke(p);
    Method attacker =
        method(
            p.getClass(),
            new Class<?>[] {Class.forName("net.minecraft.entity.Entity")},
            "setLastAttacker",
            "func_130011_c");
    Method health = method(p.getClass(), "getHealth", "func_110143_aJ"),
        setHealth = method(p.getClass(), new Class<?>[] {float.class}, "setHealth", "func_70606_j");
    float oldHealth = ((Number) health.invoke(p)).floatValue();
    Field effects = field(p.getClass(), "activePotionsMap", "field_70713_bf");
    Map potionMap = (Map) effects.get(p);
    Map oldEffects = new HashMap(potionMap);
    Map<String, Boolean> enabled = new LinkedHashMap<>();
    Map<Setting<?>, Object> values = new LinkedHashMap<>();
    for (String id :
        new String[] {
          "flight", "speed", "longjump", "step", "stasis", "aura", "regen", "fastuse", "autopot"
        }) {
      enabled.put(id, r.modules().require(id).requestedEnabled());
      r.modules().setEnabled(id, false);
      for (Setting<?> s : r.modules().require(id).module().settings().values())
        values.put(s, s.get());
    }
    EventOwner owner =
        new EventOwner(
            "movement-parity",
            failure -> {
              throw new AssertionError(failure);
            });
    owner.activate();
    r.networkEvents()
        .subscribe(
            owner,
            PacketReceiveEvent.class,
            Integer.MAX_VALUE,
            true,
            e -> networkEvents.incrementAndGet());
    r.networkEvents()
        .subscribe(
            owner,
            PacketSendEvent.class,
            Integer.MAX_VALUE,
            true,
            e -> networkEvents.incrementAndGet());
    List<AutoCloseable> resources = new ArrayList<>();
    ControlRequest<Rotation> rotation = r.controls().scope(owner, resources::add).rotation(500);
    Object pig =
        Class.forName("net.minecraft.entity.passive.EntityPig")
            .getConstructor(Class.forName("net.minecraft.world.World"))
            .newInstance(world);
    try {
      check(
          history.ground() + history.air() > 0,
          "the transformed player updates ground history while movement modules are disabled");
      screen.set(game, null);
      focus.setBoolean(game, true);
      potionMap.clear();
      b(p, "onGround", "field_70122_E", false);
      b(p, "inWater", "field_70171_ac", false);
      b(p, "isInWeb", "field_70134_J", false);
      f(p, "rotationYaw", "field_70177_z", 0);
      direction(p, input, 1, 0);
      potionTiming(r, p, items, history);
      b(p, "onGround", "field_70122_E", false);
      acceleration(r, p, input);
      clear(history);
      history.advance(false);
      history.advance(false);
      history.advance(false);
      set(r, "speed", "mode", "NCP");
      r.modules().setEnabled("speed", true);
      motion(p, 0, 0, .2);
      pre(r);
      check(
          close(speed(p), .3222259f),
          "enabling NCP midair uses the player's completed second air tick");
      set(r, "speed", "mode", "VANILLA");
      set(r, "speed", "mode", "NCP");
      motion(p, 0, 0, .2);
      pre(r);
      check(close(speed(p), .3222259f), "mode changes do not restart player air history");
      Object speedModule = r.modules().require("speed").module();
      correction(r);
      pre(r);
      check(
          field(speedModule.getClass(), "setback").getInt(speedModule) == 59,
          "first correction update consumes one of 60 paused updates");
      for (int i = 0; i < 29; i++) pre(r);
      correction(r);
      check(
          field(speedModule.getClass(), "setback").getInt(speedModule) == 30,
          "another correction retains the donor's existing pause progress");
      motion(p, 0, 0, .2);
      for (int i = 0; i < 29; i++) pre(r);
      check(close(speed(p), .2), "NCP remains paused through update 59");
      pre(r);
      check(close(speed(p), .3222259f), "NCP resumes on correction update 60");
      // A cached norm produces a different vector at non-axis-aligned yaw.
      history.advance(false);
      f(p, "rotationYaw", "field_70177_z", 45);
      direction(p, input, 1, 0);
      motion(p, .1, 0, .2);
      pre(r);
      Class<?> math = Class.forName("net.minecraft.util.MathHelper");
      float radians = (float) Math.toRadians(45);
      double expectedX =
          -((Number)
                      method(math, new Class<?>[] {float.class}, "sin", "func_76126_a")
                          .invoke(null, radians))
                  .floatValue()
              * Math.hypot(.1, .2);
      double expectedZ =
          ((Number)
                      method(math, new Class<?>[] {float.class}, "cos", "func_76134_b")
                          .invoke(null, radians))
                  .floatValue()
              * Math.hypot(expectedX, .2);
      check(
          number(p, "motionX", "field_70159_w") == expectedX
              && number(p, "motionZ", "field_70179_y") == expectedZ,
          "NCP no-argument strafe preserves source lookup trig and sequential speed sampling");
      f(p, "rotationYaw", "field_70177_z", 0);
      set(r, "speed", "mode", "LIBRECRAFT");
      float[][] cases = {
        {0, 1, 0, 0},
        {0, 1, 0, 45},
        {0, 1, 0, 44.99f},
        {179, 1, 0, -179},
        {0, 1, 1, -45},
        {0, 0, 1, 0}
      };
      for (float[] a : cases) {
        f(p, "rotationYaw", "field_70177_z", a[0]);
        direction(p, input, a[1], a[2]);
        rotation.set(Rotation.server(a[3], 0));
        motion(p, 0, 0, .3);
        pre(r);
        float moving = a[0] - (a[2] > 0 ? (a[1] > 0 ? 45 : 90) : 0);
        float delta = (moving - a[3]) % 360;
        if (delta >= 180) delta -= 360;
        if (delta < -180) delta += 360;
        check(
            close(
                number(p, "jumpMovementFactor", "field_70747_aH"),
                Math.abs(delta) < 45 ? .026499f : .0244f),
            "Librecraft rotation-aware factor at yaw " + Arrays.toString(a));
      }
      rotation.clear();
      f(p, "rotationYaw", "field_70177_z", 0);
      direction(p, input, 1, 0);
      motion(p, 0, 0, .3);
      pre(r);
      check(
          close(number(p, "jumpMovementFactor", "field_70747_aH"), .026499f),
          "no rotation retains straight-input factor");
      direction(p, input, 1, 1);
      motion(p, 0, 0, .3);
      pre(r);
      check(
          close(number(p, "jumpMovementFactor", "field_70747_aH"), .0244f),
          "no rotation retains side-input factor");
      direction(p, input, 1, 0);
      field(p.getClass(), "hurtTime", "field_70737_aN").setInt(p, 10);
      attacker.invoke(p, pig);
      motion(p, 0, 0, .3);
      pre(r);
      check(close(speed(p), .3), "a last attacker alone does not trigger Librecraft combat speed");
      attacker.invoke(p, new Object[] {null});
      r.modules().setEnabled("aura", true);
      field(r.modules().require("aura").module().getClass(), "target")
          .set(r.modules().require("aura").module(), pig);
      motion(p, 0, 0, .3);
      pre(r);
      check(
          close(speed(p), .455),
          "active Aura target triggers combat speed without a last attacker");
      r.modules().setEnabled("aura", false);
      field(p.getClass(), "hurtTime", "field_70737_aN").setInt(p, 0);
      b(p, "onGround", "field_70122_E", true);
      motion(p, 0, 0, .3);
      pre(r);
      check(
          Boolean.FALSE.equals(r.controls().key(InputKey.JUMP).orElse(null)),
          "Librecraft ground jump owns a one-tick false jump key");
      check(close(r.controls().timerSpeed().orElse(1f), 1.25f), "Librecraft jump timer is 1.25");
      set(r, "speed", "mode", "VANILLA");
      check(
          !r.controls().key(InputKey.JUMP).isPresent() && !r.controls().timerSpeed().isPresent(),
          "mode switch immediately releases jump and timer");
      r.modules().setEnabled("speed", false);
      items[3] = stack("fire_charge");
      set(r, "longjump", "fireball", "true");
      r.modules().setEnabled("longjump", true);
      clear(history);
      history.advance(true);
      history.advance(true);
      motion(p, .3, 0, .4);
      pre(r);
      check(speed(p) == 0, "fireball Long Jump stops on the first completed grounded tick");
      history.advance(true);
      motion(p, .3, 0, .4);
      pre(r);
      check(close(speed(p), .5), "fireball Long Jump does not keep stopping a grounded player");
      r.modules().setEnabled("longjump", false);
      set(r, "longjump", "fireball", "false");
      clear(history);
      for (int i = 0; i < 22; i++) history.advance(false);
      b(p, "onGround", "field_70122_E", false);
      r.modules().setEnabled("longjump", true);
      motion(p, 0, .3, .2);
      r.clientEvents().post(new StrafeEvent(0, 1, .1f, 0));
      check(
          close(number(p, "motionY", "field_70181_x"), -.2f),
          "Mospixel midair enable uses current player tick 22 immediately");
      r.modules().setEnabled("longjump", false);
      set(r, "step", "mode", "NCP");
      clear(history);
      history.advance(true);
      b(p, "onGround", "field_70122_E", true);
      b(p, "isCollidedHorizontally", "field_70123_F", true);
      r.modules().setEnabled("step", true);
      r.clientEvents().post(PlayerControlEvent.BEFORE_MOTION);
      b(p, "onGround", "field_70122_E", false);
      b(p, "isCollidedHorizontally", "field_70123_F", false);
      history.advance(false);
      history.advance(false);
      motion(p, 0, .3, .2);
      r.clientEvents().post(PlayerControlEvent.BEFORE_MOTION);
      check(
          close(number(p, "motionY", "field_70181_x"), .3), "NCP Step leaves air tick 2 unchanged");
      history.advance(false);
      r.clientEvents().post(PlayerControlEvent.BEFORE_MOTION);
      check(
          number(p, "motionY", "field_70181_x") == ((.3 - .08) * .98F - .08) * .98F,
          "NCP Step predicts two gravity steps exactly on current air tick 3");
      r.modules().setEnabled("step", false);
      packetCounts(r, game, p, items, selected, setHealth);
      stasis(r, game, p);
      for (String id : enabled.keySet())
        check(
            r.modules().require(id).state() != perihelion.core.module.ModuleState.FAULTED,
            id + " remains unfaulted");
      System.out.println(
          "PERIHELION_SMOKE MOVEMENT_PARITY checks="
              + checks
              + " networkEvents="
              + networkEvents.get()
              + " PASS");
    } finally {
      for (String id : enabled.keySet()) r.modules().setEnabled(id, false);
      for (String id : enabled.keySet())
        for (Setting<?> s : r.modules().require(id).module().settings().values())
          restore(r, id, s, values.get(s));
      for (AutoCloseable resource : resources) resource.close();
      owner.close();
      restoreFields(history, savedHistory);
      restoreFields(p, saved);
      restoreFields(input, savedInput);
      potionMap.clear();
      potionMap.putAll(oldEffects);
      attacker.invoke(p, oldAttacker);
      setHealth.invoke(p, oldHealth);
      System.arraycopy(savedItems, 0, items, 0, items.length);
      selected.setInt(inventory, oldSlot);
      screen.set(game, oldScreen);
      focus.setBoolean(game, oldFocus);
      restoreFields(autoPot, savedPotState);
      HookBridge.gameControls().apply();
      for (String id : enabled.keySet()) if (enabled.get(id)) r.modules().setEnabled(id, true);
    }
  }

  private static void potionTiming(ClientRuntime r, Object p, Object[] items, GroundTickHistory h)
      throws Exception {
    Object pot = stack("potion");
    method(pot.getClass(), new Class<?>[] {int.class}, "setItemDamage", "func_77964_b")
        .invoke(pot, 16421);
    items[3] = pot;
    set(r, "autopot", "health", "20");
    Object module = r.modules().require("autopot").module();
    Field attacks = field(module.getClass(), "attackTicks"),
        next = field(module.getClass(), "nextThrowAt");
    attacks.setInt(module, 9);
    next.setLong(module, 0);
    clear(h);
    h.advance(true);
    h.advance(true);
    b(p, "onGround", "field_70122_E", true);
    r.modules().setEnabled("autopot", true);
    pre(r);
    check(
        !r.controls().rotation().isPresent(),
        "Auto Pot excludes the first completed grounded tick");
    h.advance(true);
    pre(r);
    check(
        (Boolean) method(module.getClass(), "busy").invoke(module)
            && !r.controls().rotation().isPresent()
            && !r.controls().hotbar().isPresent(),
        "Auto Pot starts after two completed grounded ticks without an early slot change");
    pre(r);
    check(
        r.controls().rotation().isPresent() && !r.controls().hotbar().isPresent(),
        "Auto Pot begins smooth aiming on the following update");
    r.modules().setEnabled("autopot", false);
    int count = attacks.getInt(module);
    long pending = System.nanoTime() / 1_000_000L + 60000;
    next.setLong(module, pending);
    r.modules().setEnabled("autopot", true);
    check(
        attacks.getInt(module) == count && next.getLong(module) == pending,
        "Auto Pot attack interval and throw cooldown survive ordinary toggles");
    pre(r);
    check(
        !r.controls().rotation().isPresent(),
        "Auto Pot toggle cannot bypass a pending throw deadline");
    r.modules().setEnabled("autopot", false);
  }

  private static void acceleration(ClientRuntime r, Object p, Object input) throws Exception {
    Method move =
        method(
            p.getClass(),
            new Class<?>[] {float.class, float.class, float.class},
            "moveFlying",
            "func_70060_a");
    for (String id : new String[] {"flight", "speed"}) {
      set(r, id, "mode", id.equals("flight") ? "MOTION" : "VANILLA");
      set(r, id, id.equals("flight") ? "motion-speed" : "speed", "0.9");
      r.modules().setEnabled(id, true);
      for (float[] a : new float[][] {{1, 0}, {1, 1}, {.3f, 0}, {0, 0}}) {
        direction(p, input, a[0], a[1]);
        motion(p, .4, 0, .3);
        move.invoke(p, a[1], a[0], .1f);
        double expected =
            .9 * (a[0] != 0 && a[1] != 0 ? .98f : 1) * Math.min(1, Math.hypot(a[0], a[1]));
        check(
            close(speed(p), expected),
            id + " transformed acceleration retains analog/diagonal input " + Arrays.toString(a));
      }
      r.modules().setEnabled(id, false);
    }
    set(r, "flight", "mode", "NCP");
    r.modules().setEnabled("flight", true);
    direction(p, input, 0, 0);
    for (int i = 0; i < 16; i++) {
      motion(p, .4, 0, -.3);
      move.invoke(p, 0f, 0f, .1f);
      double x = number(p, "motionX", "field_70159_w"), z = number(p, "motionZ", "field_70179_y");
      check(
          x >= 0 && x < .4 / 2000 && close(x / .4, z / -.3),
          "NCP Flight retains one shared random motion multiplier in [0,1/2000)");
    }
    direction(p, input, 1, 1);
    motion(p, 0, 0, 0);
    move.invoke(p, 1f, 1f, .1f);
    check(close(speed(p), .2873 * .98f), "NCP Flight applies the diagonal base-speed factor");
    r.modules().setEnabled("flight", false);
    for (String mode : new String[] {"INTAVE", "POLAR"}) {
      set(r, "speed", "mode", mode);
      r.modules().setEnabled("speed", true);
      direction(p, input, 1, 0);
      b(p, "onGround", "field_70122_E", true);
      b(p, "isInWeb", "field_70134_J", true);
      motion(p, 0, .123, 0);
      move.invoke(p, 0f, 1f, .1f);
      check(
          close(number(p, "motionY", "field_70181_x"), .123),
          mode + " does not auto-jump in a web");
      b(p, "isInWeb", "field_70134_J", false);
      motion(p, 0, .123, 0);
      move.invoke(p, 0f, 1f, .1f);
      check(number(p, "motionY", "field_70181_x") > .4, mode + " auto-jumps on eligible ground");
      r.modules().setEnabled("speed", false);
    }
    b(p, "onGround", "field_70122_E", false);
    direction(p, input, 1, 0);
  }

  private static void correction(ClientRuntime r) throws Exception {
    Class<?> packet = Class.forName("net.minecraft.network.Packet");
    Object s08 =
        Class.forName("net.minecraft.network.play.server.S08PacketPlayerPosLook")
            .getConstructor()
            .newInstance();
    r.networkEvents().post(PacketReceiveEvent.class.getConstructor(packet).newInstance(s08));
    r.tasks().drain();
  }

  private static void packetCounts(
      ClientRuntime r, Object game, Object p, Object[] items, Field slot, Method health)
      throws Exception {
    Object handler = method(game.getClass(), "getNetHandler", "func_147114_u").invoke(game);
    Object manager = field(handler.getClass(), "netManager", "field_147302_e").get(handler);
    Channel channel = (Channel) field(manager.getClass(), "channel", "field_150746_k").get(manager);
    List<Object> packets = new CopyOnWriteArrayList<>();
    String name = "movement-parity-packets";
    channel
        .pipeline()
        .addLast(
            name,
            new ChannelOutboundHandlerAdapter() {
              @Override
              public void write(ChannelHandlerContext c, Object m, ChannelPromise promise) {
                packets.add(m);
                c.write(m, promise);
              }
            });
    Object inv = field(p.getClass(), "inventory", "field_71071_by").get(p);
    Method use =
        method(
            p.getClass(),
            new Class<?>[] {Class.forName("net.minecraft.item.ItemStack"), int.class},
            "setItemInUse",
            "func_71008_a");
    Method clear = method(p.getClass(), "clearItemInUse", "func_71034_by");
    try {
      set(r, "regen", "speed", "3");
      set(r, "regen", "minimum-health", "15");
      health.invoke(p, 14f);
      r.modules().setEnabled("regen", true);
      packets.clear();
      r.clientEvents().post(PlayerControlEvent.BEFORE_MOTION);
      flush(channel);
      check(
          movements(packets) == 3,
          "Regen sends exactly configured C06 updates below the threshold");
      health.invoke(p, 15f);
      packets.clear();
      r.clientEvents().post(PlayerControlEvent.BEFORE_MOTION);
      flush(channel);
      check(movements(packets) == 0, "Regen threshold is strict less-than");
      r.modules().setEnabled("regen", false);
      items[0] = stack("apple");
      slot.setInt(inv, 0);
      set(r, "fastuse", "speed", "3");
      set(r, "fastuse", "food-items", "true");
      r.modules().setEnabled("fastuse", true);
      use.invoke(p, items[0], 31);
      packets.clear();
      r.clientEvents().post(PlayerControlEvent.BEFORE_MOTION);
      flush(channel);
      check(movements(packets) == 4, "Fast Use sends speed plus one C06 updates at use count 31");
      clear.invoke(p);
      use.invoke(p, items[0], 30);
      packets.clear();
      r.clientEvents().post(PlayerControlEvent.BEFORE_MOTION);
      flush(channel);
      check(movements(packets) == 0, "Fast Use excludes use count 30");
      clear.invoke(p);
      use.invoke(p, items[0], 31);
      set(r, "fastuse", "food-items", "false");
      packets.clear();
      r.clientEvents().post(PlayerControlEvent.BEFORE_MOTION);
      flush(channel);
      check(movements(packets) == 0, "Fast Use respects disabled food category");
    } finally {
      r.modules().setEnabled("regen", false);
      r.modules().setEnabled("fastuse", false);
      clear.invoke(p);
      channel.pipeline().remove(name);
    }
  }

  private static long movements(List<Object> packets) {
    return packets.stream()
        .filter(p -> p.getClass().getSimpleName().equals("C06PacketPlayerPosLook"))
        .count();
  }

  private static void flush(Channel c) throws Exception {
    c.eventLoop().submit(() -> {}).sync();
  }

  private static void stasis(ClientRuntime r, Object game, Object p) throws Exception {
    b(p, "onGround", "field_70122_E", false);
    field(p.getClass(), "hurtTime", "field_70737_aN").setInt(p, 0);
    motion(p, .3, .2, .4);
    try (GameplayLoopback peer = new GameplayLoopback(game)) {
      Object handler = method(game.getClass(), "getNetHandler", "func_147114_u").invoke(game);
      Class<?> packet = Class.forName("net.minecraft.network.Packet");
      Method send =
          method(handler.getClass(), new Class<?>[] {packet}, "addToSendQueue", "func_147297_a");
      Constructor<?> position =
          Class.forName("net.minecraft.network.play.client.C03PacketPlayer$C04PacketPlayerPosition")
              .getConstructor(double.class, double.class, double.class, boolean.class);
      Constructor<?> look =
          Class.forName("net.minecraft.network.play.client.C03PacketPlayer$C05PacketPlayerLook")
              .getConstructor(float.class, float.class, boolean.class);
      r.modules().setEnabled("stasis", true);
      send.invoke(handler, position.newInstance(1d, 2d, 3d, false));
      send.invoke(handler, look.newInstance(0f, 0f, false));
      peer.await(1);
      check(
          peer.received.size() == 1
              && peer.received.get(0).getClass().getSimpleName().equals("C05PacketPlayerLook"),
          "Stasis immediately suppresses position but permits look before its first input"
              + " callback");
      for (int tick = 1; tick <= 44; tick++) {
        pre(r);
        check(
            speed(p) == 0 && number(p, "motionY", "field_70181_x") == 0,
            "Stasis freeze tick " + tick);
      }
      pre(r);
      check(
          close(speed(p), .5) && close(number(p, "motionY", "field_70181_x"), .2),
          "Stasis releases saved motion on tick 45");
      send.invoke(handler, position.newInstance(4d, 5d, 6d, false));
      send.invoke(handler, look.newInstance(1f, 0f, false));
      peer.await(3);
      check(
          peer.received.size() == 3
              && peer.received.get(1).getClass().getSimpleName().equals("C04PacketPlayerPosition"),
          "release tick permits real TCP position before look");
      motion(p, .6, .1, .8);
      pre(r);
      check(speed(p) == 0, "Stasis freezes again on tick 46");
      send.invoke(handler, position.newInstance(7d, 8d, 9d, false));
      send.invoke(handler, look.newInstance(2f, 0f, false));
      peer.await(4);
      check(
          peer.received.size() == 4
              && peer.received.get(3).getClass().getSimpleName().equals("C05PacketPlayerLook"),
          "refrozen tick suppresses TCP position and preserves look");
      field(p.getClass(), "hurtTime", "field_70737_aN").setInt(p, 10);
      pre(r);
      send.invoke(handler, position.newInstance(10d, 11d, 12d, false));
      send.invoke(handler, look.newInstance(3f, 0f, false));
      peer.await(6);
      check(
          peer.received.size() == 6
              && peer.received.get(4).getClass().getSimpleName().equals("C04PacketPlayerPosition"),
          "hurt-state exception permits real TCP movement while frozen");
      r.modules().setEnabled("stasis", false);
      check(
          close(speed(p), 1) && close(number(p, "motionY", "field_70181_x"), .1),
          "Stasis restores the newly saved release motion on disable");
    } finally {
      r.modules().setEnabled("stasis", false);
    }
  }

  private static void direction(Object p, Object input, float forward, float side)
      throws Exception {
    f(input, "moveForward", "field_78900_b", forward);
    f(input, "moveStrafe", "field_78902_a", side);
    f(p, "moveForward", "field_70701_bs", forward);
    f(p, "moveStrafing", "field_70702_br", side);
  }

  private static void pre(ClientRuntime r) {
    r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
  }

  private static void motion(Object p, double x, double y, double z) throws Exception {
    field(p.getClass(), "motionX", "field_70159_w").setDouble(p, x);
    field(p.getClass(), "motionY", "field_70181_x").setDouble(p, y);
    field(p.getClass(), "motionZ", "field_70179_y").setDouble(p, z);
  }

  private static double number(Object p, String... names) throws Exception {
    return ((Number) field(p.getClass(), names).get(p)).doubleValue();
  }

  private static double speed(Object p) throws Exception {
    return Math.hypot(number(p, "motionX", "field_70159_w"), number(p, "motionZ", "field_70179_y"));
  }

  private static boolean close(double a, double b) {
    return Math.abs(a - b) < 1e-6;
  }

  private static void b(Object p, String n, String s, boolean v) throws Exception {
    field(p.getClass(), n, s).setBoolean(p, v);
  }

  private static void f(Object p, String n, String s, float v) throws Exception {
    field(p.getClass(), n, s).setFloat(p, v);
  }

  private static Map<Field, Object> saveAll(Object p) throws Exception {
    Map<Field, Object> m = new LinkedHashMap<>();
    for (Field f : p.getClass().getDeclaredFields()) {
      if (Modifier.isStatic(f.getModifiers()) || Modifier.isFinal(f.getModifiers())) continue;
      f.setAccessible(true);
      m.put(f, f.get(p));
    }
    return m;
  }

  private static void restoreFields(Object p, Map<Field, Object> m) throws Exception {
    for (Map.Entry<Field, Object> e : m.entrySet()) e.getKey().set(p, e.getValue());
  }

  private static void clear(GroundTickHistory h) throws Exception {
    for (Field f : GroundTickHistory.class.getDeclaredFields()) {
      f.setAccessible(true);
      f.setInt(h, 0);
    }
  }

  private static void restore(ClientRuntime r, String id, Setting s, Object v) {
    r.modules().setSetting(id, s, v);
  }

  private static void check(boolean b, String message) {
    checks++;
    if (!b) throw new AssertionError(message);
  }
}
