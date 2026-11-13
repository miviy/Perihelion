package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import perihelion.core.control.ControlRequest;
import perihelion.core.control.HotbarSelection;
import perihelion.core.control.InputKey;
import perihelion.core.control.Rotation;
import perihelion.core.event.EventOwner;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleState;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.*;

/** Source-derived B4 behavior checks against the packaged, transformed client. */
public final class B4MovementBedWarsProbe {
  private static int checks;

  public static void run(ClientRuntime r) throws Exception {
    checks = 0;
    Object game = method(Class.forName("net.minecraft.client.Minecraft"), "getMinecraft", "func_71410_x").invoke(null);
    Object player = field(game.getClass(), "thePlayer", "field_71439_g").get(game);
    Object world = field(game.getClass(), "theWorld", "field_71441_e").get(game);
    Object inventory = field(player.getClass(), "inventory", "field_71071_by").get(player);
    Object[] items = (Object[]) field(inventory.getClass(), "mainInventory", "field_70462_a").get(inventory);
    Object[] savedItems = items.clone();
    Field selected = field(inventory.getClass(), "currentItem", "field_70461_c");
    int savedSlot = selected.getInt(inventory);
    Field screen = field(game.getClass(), "currentScreen", "field_71462_r");
    Object savedScreen = screen.get(game);
    Object input = field(player.getClass(), "movementInput", "field_71158_b").get(player);
    Map<Field, Object> saved = new LinkedHashMap<>();
    for (String[] name : new String[][] {
        {"onGround", "field_70122_E"}, {"motionX", "field_70159_w"}, {"motionY", "field_70181_x"},
        {"motionZ", "field_70179_y"}, {"rotationYaw", "field_70177_z"}, {"rotationPitch", "field_70125_A"},
        {"lastReportedYaw", "field_175164_bL"}, {"lastReportedPitch", "field_175165_bM"},
        {"ticksExisted", "field_70173_aa"}}) {
      Field value = field(player.getClass(), name); saved.put(value, value.get(player));
    }
    Field forward = field(input.getClass(), "moveForward", "field_78900_b"), side = field(input.getClass(), "moveStrafe", "field_78902_a");
    float savedForward = forward.getFloat(input), savedSide = side.getFloat(input);
    Map<String, Boolean> enabled = new LinkedHashMap<>();
    Map<String, Map<Setting<?>, Object>> settings = new LinkedHashMap<>();
    for (String id : new String[] {"noslow", "longjump", "bedwarsutility"}) {
      enabled.put(id, r.modules().require(id).requestedEnabled());
      r.modules().setEnabled(id, false);
      Map<Setting<?>, Object> values = new LinkedHashMap<>();
      for (Setting<?> s : r.modules().require(id).module().settings().values()) values.put(s, s.get());
      settings.put(id, values);
    }
    int subscriptions = r.clientEvents().subscriptionCount(), requests = r.controls().requestCount();
    EventOwner observer = new EventOwner("b4-movement-fixture", failure -> { throw new AssertionError(failure); });
    observer.activate();
    AtomicInteger clicks = new AtomicInteger();
    boolean[] cancelClicks = {true};
    r.clientEvents().subscribe(observer, RightClickEvent.class, -10000, true, e -> {
      clicks.incrementAndGet();
      if (cancelClicks[0]) e.cancel();
    });
    try {
      screen.set(game, null);
      selected.setInt(inventory, 0);
      field(player.getClass(), "onGround", "field_70122_E").setBoolean(player, true);
      field(player.getClass(), "rotationYaw", "field_70177_z").setFloat(player, 0);
      forward.setFloat(input, 1); side.setFloat(input, 0);
      items[0] = stack("apple");
      use(player, items[0]);
      set(r, "noslow", "mode", "VANILLA");
      r.modules().setEnabled("noslow", true);
      check(r.clientEvents().post(new ItemSlowdownEvent()).isCancelled(), "legacy Vanilla still removes selected slowdown");
      set(r, "noslow", "mode", "BLINK");
      for (int tick = 1; tick <= 34; tick++) {
        r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
        ItemSlowdownEvent e = r.clientEvents().post(new ItemSlowdownEvent());
        check(!e.isCancelled() && e.factor() == (tick < 14 ? .2f : 1f), "Blink item factor tick " + tick);
      }
      set(r, "noslow", "mode", "GROUND");
      motion(player);
      field(player.getClass(), "onGround", "field_70122_E").setBoolean(player, false);
      Object handler = method(game.getClass(), "getNetHandler", "func_147114_u").invoke(game);
      Object manager = method(handler.getClass(), "getNetworkManager", "func_147298_b").invoke(handler);
      check((Boolean) method(manager.getClass(), "isLocalChannel", "func_150731_c").invoke(manager),
          "Ground use fixture exercises the real singleplayer PLAY connection");
      sendUse(game, player, world, items[0]);
      field(player.getClass(), "onGround", "field_70122_E").setBoolean(player, true);
      r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      check(r.clientEvents().post(new ItemSlowdownEvent()).factor() == .3f, "airborne-started Ground use slows to 30 percent after landing");
      check(r.controls().key(InputKey.JUMP).equals(Optional.of(false)), "Ground blocks automatic repeated use jumps");
      clearUse(player);
      r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      check(!r.controls().key(InputKey.JUMP).isPresent(), "ending Ground use releases the jump claim");
      use(player, items[0]);
      noSlowFacts(r, game, player, world, inventory, items);
      items[0] = stack("milk_bucket");
      use(player, items[0]);
      set(r, "noslow", "mode", "INVALID");
      field(player.getClass(), "ticksExisted", "field_70173_aa").setInt(player, 1);
      r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      check(r.clientEvents().post(new ItemSlowdownEvent()).factor() == 1, "B4 potion category includes milk");
      r.modules().setEnabled("noslow", false);
      check(!r.clientEvents().post(new ItemSlowdownEvent()).isCancelled(), "disable removes slowdown interception");
      clearUse(player);

      Arrays.fill(items, null);
      items[0] = stack("fire_charge");
      set(r, "longjump", "fireball", "false");
      set(r, "longjump", "fireball-mode", "B4_AUTO");
      r.modules().setEnabled("longjump", true);
      check(r.modules().require("longjump").state() == ModuleState.ENABLED,
          "non-fireball activation can retain the hidden B4 selection");
      set(r, "longjump", "fireball", "true");
      check(r.modules().require("longjump").state() == ModuleState.DISABLED,
          "entering B4 through Fireball retires legacy listeners before they can throw");
      check(!r.controls().hotbar().isPresent() && !r.controls().rotation().isPresent(),
          "Fireball branch change releases legacy controls");
      set(r, "longjump", "b4-horizontal-boost", "0.8");
      set(r, "longjump", "b4-vertical-motion", "0.4");
      longJump(r, game, player, world, input, forward, clicks, cancelClicks);
      motionGuard(r, game, player);

      bedwars(r, game, player, world);
    } finally {
      for (String id : enabled.keySet()) r.modules().setEnabled(id, false);
      observer.close();
      clearUse(player);
      HookBridge.gameControls().apply();
      System.arraycopy(savedItems, 0, items, 0, items.length);
      selected.setInt(inventory, savedSlot);
      screen.set(game, savedScreen);
      for (Map.Entry<Field, Object> value : saved.entrySet()) value.getKey().set(player, value.getValue());
      forward.setFloat(input, savedForward); side.setFloat(input, savedSide);
      for (String id : settings.keySet()) for (Map.Entry<Setting<?>, Object> value : settings.get(id).entrySet())
        restore(r, id, value.getKey(), value.getValue());
      check(r.clientEvents().subscriptionCount() == subscriptions, "all B4 client listeners retire on disable");
      check(r.controls().requestCount() == requests, "all B4 shared controls retire on disable");
      for (String id : enabled.keySet()) r.modules().setEnabled(id, enabled.get(id));
    }
    System.out.println("PERIHELION_SMOKE B4_MOVEMENT_BEDWARS PASS checks=" + checks);
  }

  private static void sendUse(Object game, Object player, Object world, Object stack) throws Exception {
    Object controller = field(game.getClass(), "playerController", "field_71442_b").get(game);
    method(controller.getClass(), new Class<?>[] {Class.forName("net.minecraft.entity.player.EntityPlayer"),
        Class.forName("net.minecraft.world.World"), Class.forName("net.minecraft.item.ItemStack")},
        "sendUseItem", "func_78769_a").invoke(controller, player, world, stack);
  }

  private static void noSlowFacts(ClientRuntime r, Object game, Object player, Object world,
      Object inventory, Object[] items) throws Exception {
    EventOwner owner = new EventOwner("b4-noslow-facts", failure -> { throw new AssertionError(failure); });
    owner.activate();
    List<AutoCloseable> claims = new ArrayList<>();
    ControlRequest<HotbarSelection> desired = r.controls().scope(owner, claims::add).hotbar(10000);
    Field ground = field(player.getClass(), "onGround", "field_70122_E");
    Class<?> held = Class.forName("net.minecraft.network.play.client.C09PacketHeldItemChange");
    Object slotZero = held.getConstructor(int.class).newInstance(0), slotOne = held.getConstructor(int.class).newInstance(1);
    AtomicInteger uses = new AtomicInteger();
    r.clientEvents().subscribe(owner, ClientSendFactEvent.class, e -> {
      if (e.action() == ClientSendFactEvent.Action.USE_ITEM) uses.incrementAndGet();
    });
    try {
      set(r, "noslow", "mode", "BLINK");
      outbound(r, slotZero);
      desired.set(HotbarSelection.spoof(1));
      r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      check(r.clientEvents().post(new ItemSlowdownEvent()).factor() == .2f,
          "merely desiring a silent slot cannot replace the observed C09 slot");
      outbound(r, slotOne);
      r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      check(r.clientEvents().post(new ItemSlowdownEvent()).factor() == 1,
          "an actual mismatched C09 attempt suspends the slowed-item sequence");
      desired.clear();
      outbound(r, slotZero);
      r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      check(r.clientEvents().post(new ItemSlowdownEvent()).factor() == .2f,
          "observing the held slot again restarts Blink's first slow tick");
      Object handler = method(game.getClass(), "getNetHandler", "func_147114_u").invoke(game);
      Class<?> correction = Class.forName("net.minecraft.network.play.server.S09PacketHeldItemChange");
      method(handler.getClass(), new Class<?>[] {correction}, "handleHeldItemChange", "func_147257_a")
          .invoke(handler, correction.getConstructor(int.class).newInstance(1));
      check(HookBridge.gameControls().attemptedServerSlot(0) == 1,
          "transformed S09 handoff updates the same observed slot fact");
      field(inventory.getClass(), "currentItem", "field_70461_c").setInt(inventory, 0);
      outbound(r, slotZero);
      set(r, "noslow", "mode", "GROUND");
      ground.setBoolean(player, true);
      motion(player);
      ground.setBoolean(player, false);
      for (int i = 0; i < 11; i++) motion(player);
      check(HookBridge.gameControls().walkingAirTicks() == 11, "air history counts the post-physics walking passes");
      sendUse(game, player, world, items[0]);
      check(uses.get() == 1, "one real local item use emits one observational fact");
      ground.setBoolean(player, true);
      r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      check(r.clientEvents().post(new ItemSlowdownEvent()).factor() == 1,
          "eleventh walking-air use near ground preserves the donor Ground exception");
      set(r, "noslow", "mode", "BLINK");
      set(r, "noslow", "mode", "GROUND");
      motion(player);
      ground.setBoolean(player, false);
      for (int i = 0; i < 10; i++) motion(player);
      sendUse(game, player, world, items[0]);
      ground.setBoolean(player, true);
      r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      check(r.clientEvents().post(new ItemSlowdownEvent()).factor() == .3f,
          "tenth walking-air use retains Ground's landing slowdown");
      Object placement = Class.forName("net.minecraft.network.play.client.C08PacketPlayerBlockPlacement")
          .getConstructor(Class.forName("net.minecraft.item.ItemStack")).newInstance(items[0]);
      int count = uses.get();
      perihelion.integration.minecraft189.network.RawHookBridge.replay(() -> {
        try { outbound(r, placement); }
        catch (Exception failure) { throw new AssertionError(failure); }
      });
      check(uses.get() == count, "silent queued replay is never a fresh item-use observation");
      Object network = method(handler.getClass(), "getNetworkManager", "func_147298_b").invoke(handler);
      Method observe = method(HookBridge.gameControls().getClass(), new Class<?>[] {
          Class.forName("net.minecraft.network.NetworkManager"), Class.forName("net.minecraft.network.Packet")}, "observeClientSend");
      java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
      // Resolve the controls object on the client: its provider intentionally returns null on Netty.
      Object controls = HookBridge.gameControls();
      Thread netty = new Thread(() -> {
        try { observe.invoke(controls, network, placement); }
        catch (Throwable problem) { failure.set(problem); }
      }, "B4-fact-thread-guard");
      netty.start(); netty.join(5000);
      check(!netty.isAlive() && failure.get() == null && uses.get() == count,
          "off-thread send facts return before client-world inspection or event dispatch");
    } finally {
      desired.clear();
      owner.close();
      for (int i = claims.size() - 1; i >= 0; i--) claims.get(i).close();
      HookBridge.gameControls().apply();
    }
  }

  private static void longJump(ClientRuntime r, Object game, Object player, Object world,
      Object input, Field forward, AtomicInteger clicks, boolean[] cancelClicks) throws Exception {
    Field px = field(player.getClass(), "posX", "field_70165_t"),
        py = field(player.getClass(), "posY", "field_70163_u"),
        pz = field(player.getClass(), "posZ", "field_70161_v");
    double x = px.getDouble(player), y = py.getDouble(player), z = pz.getDouble(player);
    Method position = method(player.getClass(), new Class<?>[] {double.class, double.class, double.class},
        "setPosition", "func_70107_b");
    Class<?> pos = Class.forName("net.minecraft.util.BlockPos"), blockState = Class.forName("net.minecraft.block.state.IBlockState");
    Object feet = pos.getConstructor(double.class, double.class, double.class).newInstance(x + .125, y, z);
    Method getBlock = method(world.getClass(), new Class<?>[] {pos}, "getBlockState", "func_180495_p"),
        setBlock = method(world.getClass(), new Class<?>[] {pos, blockState, int.class}, "setBlockState", "func_180501_a");
    Object savedBlock = getBlock.invoke(world, feet);
    EventOwner packets = new EventOwner("b4-fireball-order", failure -> { throw new AssertionError(failure); });
    packets.activate();
    List<Object> sent = new java.util.concurrent.CopyOnWriteArrayList<>();
    r.networkEvents().subscribe(packets, PacketSendEvent.class, -10000, true, e -> {
      try { sent.add(e.getClass().getMethod("packet").invoke(e)); }
      catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    });
    try (GameplayLoopback peer = new GameplayLoopback(game)) {
      r.modules().setEnabled("longjump", true);
      check(r.modules().require("longjump").state() == ModuleState.ENABLED,
          "B4 Fireball Auto accepts a held fireball near ground");
      HookBridge.gameControls().beforeInput();
      motion(player);
      check(clicks.get() == 0, "first walking pass only establishes throw aim");
      HookBridge.gameControls().beforeInput();
      check(clicks.get() == 0, "second input pass cannot throw before physics");
      position.invoke(player, x + .125, y, z);
      double[] clickX = {Double.NaN};
      r.clientEvents().subscribe(packets, RightClickEvent.class, -9999, true, e -> {
        try { clickX[0] = px.getDouble(player); }
        catch (IllegalAccessException failure) { throw new AssertionError(failure); }
      });
      cancelClicks[0] = false;
      motion(player);
      cancelClicks[0] = true;
      check(clicks.get() == 1 && clickX[0] == x + .125,
          "second walking pass throws once from the post-physics player position");
      RotationPipelineProbe.flush(peer.client);
      peer.await(sent.size());
      Class<?> walk = Class.forName("net.minecraft.network.play.client.C03PacketPlayer"),
          place = Class.forName("net.minecraft.network.play.client.C08PacketPlayerBlockPlacement");
      int firstWalk = -1, secondWalk = -1, firstPlace = -1, lastPlace = -1;
      for (int i = 0; i < peer.received.size(); i++) {
        Object packet = peer.received.get(i);
        if (walk.isInstance(packet)) {
          if (firstWalk < 0) firstWalk = i;
          else if (secondWalk < 0) secondWalk = i;
        }
        if (place.isInstance(packet)) {
          if (firstPlace < 0) firstPlace = i;
          lastPlace = i;
        }
      }
      check(firstWalk >= 0 && firstWalk < firstPlace && lastPlace < secondWalk,
          "real TCP fireball use occurs after the first aim and before the second C03");
      Object handler = method(game.getClass(), "getNetHandler", "func_147114_u").invoke(game);
      Class<?> vec = Class.forName("net.minecraft.util.Vec3"),
          explosion = Class.forName("net.minecraft.network.play.server.S27PacketExplosion");
      Object packet = explosion.getConstructor(double.class, double.class, double.class, float.class, List.class, vec)
          .newInstance(px.getDouble(player), y, z, 0f, Collections.emptyList(),
              vec.getConstructor(double.class, double.class, double.class).newInstance(4d, 4d, 4d));
      Field mx = field(player.getClass(), "motionX", "field_70159_w"),
          my = field(player.getClass(), "motionY", "field_70181_x"),
          mz = field(player.getClass(), "motionZ", "field_70179_y");
      mx.setDouble(player, 0); my.setDouble(player, 0); mz.setDouble(player, 0);
      method(handler.getClass(), new Class<?>[] {explosion}, "handleExplosion", "func_147283_a").invoke(handler, packet);
      check(mx.getDouble(player) == 0 && my.getDouble(player) == 0 && mz.getDouble(player) == 0,
          "transformed explosion hook cancels the matching vanilla impulse");
      forward.setFloat(input, 0);
      HookBridge.gameControls().beforeInput();
      motion(player);
      double speed = Math.hypot(mx.getDouble(player), mz.getDouble(player));
      check(speed > .7998 && speed <= .8 && Math.abs(my.getDouble(player) - .4) < 1e-9,
          "next motion applies donor boost even without movement keys, plus vertical override");
      HookBridge.gameControls().beforeInput();
      check(r.modules().require("longjump").state() == ModuleState.DISABLED,
          "fixed donor-default one boost tick disables and releases controls");
      check(!r.controls().hotbar().isPresent() && !r.controls().rotation().isPresent(),
          "Fireball Auto has no residual slot or rotation ownership");
    } finally {
      cancelClicks[0] = true;
      r.modules().setEnabled("longjump", false);
      packets.close();
      position.invoke(player, x, y, z);
      setBlock.invoke(world, feet, savedBlock, 3);
    }
  }

  private static void motionGuard(ClientRuntime r, Object game, Object player) throws Exception {
    EventOwner owner = new EventOwner("submitted-motion-guard", failure -> { throw new AssertionError(failure); });
    owner.activate();
    List<AutoCloseable> claims = new ArrayList<>();
    ControlRequest<Rotation> request = r.controls().scope(owner, claims::add).rotation(10000),
        blocker = r.controls().scope(owner, claims::add).rotation(20000);
    perihelion.integration.minecraft189.control.MinecraftControls controls = HookBridge.gameControls();
    boolean[] result = {false};
    r.clientEvents().subscribe(owner, PlayerControlEvent.class, -10000, false, e -> {
      if (e == PlayerControlEvent.BEFORE_MOTION)
        result[0] = controls.withSubmittedMotionAim(request, aim -> {});
    });
    Field px = field(player.getClass(), "posX", "field_70165_t"),
        py = field(player.getClass(), "posY", "field_70163_u"),
        pz = field(player.getClass(), "posZ", "field_70161_v");
    double x = px.getDouble(player), y = py.getDouble(player), z = pz.getDouble(player);
    Method position = method(player.getClass(), new Class<?>[] {double.class, double.class, double.class}, "setPosition", "func_70107_b");
    try {
      request.set(Rotation.server(31, 77));
      check(!controls.withSubmittedMotionAim(request, aim -> {}), "motion action rejects calls outside its bounded phase");
      controls.beforeInput(); motion(player);
      check(!result[0], "motion action rejects a new unsent owner");
      controls.beforeInput();
      position.invoke(player, x + .25, y, z);
      check(!controls.withPreviousAim(request, aim -> {}), "existing PRE input guard still rejects moved geometry");
      motion(player);
      check(result[0], "motion action accepts the same submitted aim after physics movement");
      check(!controls.withSubmittedMotionAim(request, aim -> {}), "motion action phase closes after dispatch");
      request.set(Rotation.server(31, 77));
      controls.beforeInput(); motion(player);
      check(!result[0], "motion action rejects an equal-angle replacement that has not been submitted");
      blocker.set(Rotation.server(-60, 22));
      controls.beforeInput(); motion(player);
      check(!result[0], "motion action rejects a losing owner");
      blocker.clear();
      controls.beforeInput(); motion(player);
      check(!result[0], "previous higher-priority submission cannot authorize a lower owner");
      controls.requestRotationResend();
      controls.beforeInput(); motion(player);
      check(!result[0], "motion action rejects an outstanding server rotation resend");
      Field reported = field(player.getClass(), "lastReportedYaw", "field_175164_bL");
      reported.setFloat(player, reported.getFloat(player) + 1);
      controls.beforeInput(); motion(player);
      check(!result[0], "motion action rejects a mismatched actual reported angle");
      try (GameplayLoopback changedConnection = new GameplayLoopback(game)) {
        controls.beforeInput(); motion(player);
        check(!result[0], "motion action rejects a replaced network connection");
      }
      controls.beforeInput(); motion(player);
      check(!result[0], "motion action rejects a retired connection's submission");
      method(controls.getClass(), "releasePlayer").invoke(controls);
      controls.beforeInput(); motion(player);
      check(!result[0], "player reset retires the old submitted motion authority");
    } finally {
      owner.close();
      for (int i = claims.size() - 1; i >= 0; i--) claims.get(i).close();
      position.invoke(player, x, y, z);
      controls.apply();
    }
  }

  private static void bedwars(ClientRuntime r, Object game, Object player, Object world) throws Exception {
    Object module = r.modules().require("bedwarsutility").module();
    Object other = Class.forName("net.minecraft.client.entity.EntityOtherPlayerMP")
        .getConstructor(Class.forName("net.minecraft.world.World"), Class.forName("com.mojang.authlib.GameProfile"))
        .newInstance(world, Class.forName("com.mojang.authlib.GameProfile").getConstructor(UUID.class, String.class)
            .newInstance(UUID.fromString("00000000-0000-0000-0000-00000000b411"), "B4Fixture"));
    Object otherInventory = field(other.getClass(), "inventory", "field_71071_by").get(other);
    Object[] otherItems = (Object[]) field(otherInventory.getClass(), "mainInventory", "field_70462_a").get(otherInventory);
    Class<?> alert = Class.forName("perihelion.integration.minecraft189.module.gameplay.B4BedWarsState$Alert");
    Method flag = method(module.getClass(), new Class<?>[] {Class.forName("net.minecraft.entity.player.EntityPlayer"), alert}, "flag");
    otherItems[0] = stack("fire_charge");
    check((Boolean) flag.invoke(module, other, enumValue(alert, "FIREBALL")), "BedWars classifier detects held fireballs");
    check(!(Boolean) flag.invoke(module, other, enumValue(alert, "ENDERPEARL")), "fireball does not also become a pearl alert");
    otherItems[0] = stack("ender_pearl");
    check((Boolean) flag.invoke(module, other, enumValue(alert, "ENDERPEARL")), "BedWars classifier detects held pearls");
    otherItems[0] = stack("potion");
    method(otherItems[0].getClass(), new Class<?>[] {int.class}, "setItemDamage", "func_77964_b").invoke(otherItems[0], 8238);
    check((Boolean) flag.invoke(module, other, enumValue(alert, "POTION_INVIS")), "BedWars reads invis potion effects");
    check(!(Boolean) flag.invoke(module, other, enumValue(alert, "POTION_SPEED")), "invis potion is not classified as speed");
    Object[] armor = (Object[]) field(otherInventory.getClass(), "armorInventory", "field_70460_b").get(otherInventory);
    armor[1] = stack("diamond_leggings");
    check((Boolean) flag.invoke(module, other, enumValue(alert, "DIA_ARMOR")), "BedWars inspects leggings for diamond purchase");
    finishBridge(r, player, other);
    r.modules().setEnabled("bedwarsutility", true);
    set(r, "bedwarsutility", "b4-alerts", "true");
    Object milk = stack("milk_bucket");
    Object consumed = ItemUseFinishedEvent.class.getConstructor(Class.forName("net.minecraft.item.ItemStack")).newInstance(milk);
    r.clientEvents().post(consumed);
    Object state = field(module.getClass(), "b4").get(module);
    check((Integer) method(state.getClass(), "milkTicks").invoke(state) == 0,
        "milk timer ignores consumption outside a real Hypixel BedWars match");
    method(state.getClass(), "milkFinished").invoke(state);
    check((Integer) method(state.getClass(), "milkTicks").invoke(state) == 600, "milk event state starts at exactly thirty seconds");
    r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
    check((Integer) method(state.getClass(), "milkTicks").invoke(state) == 600,
        "temporary missing BedWars scoreboard preserves rather than consumes or resets milk state");
    chat(r, "The game has started!", 1);
    check((Integer) method(state.getClass(), "milkTicks").invoke(state) == 600,
        "generic legacy game-start messages cannot reset B4 alert or milk state");
    chat(r, "  Select an ultimate to use this game", 2);
    check((Integer) method(state.getClass(), "milkTicks").invoke(state) == 600,
        "actionbar text cannot reset B4 match state");
    chat(r, "  Select an ultimate to use this game", 1);
    check((Integer) method(state.getClass(), "milkTicks").invoke(state) == 0,
        "exact leading-space B4 game-start text resets B4 match state");
    method(state.getClass(), "milkFinished").invoke(state);
    r.modules().setEnabled("bedwarsutility", false);
    check((Integer) method(state.getClass(), "milkTicks").invoke(state) == 0, "disable clears countdown and alert state");
  }

  private static void finishBridge(ClientRuntime r, Object local, Object remote) throws Exception {
    EventOwner owner = new EventOwner("b4-finish-bridge-fixture", failure -> { throw new AssertionError(failure); });
    owner.activate();
    AtomicInteger delivered = new AtomicInteger();
    Object[] observed = {null};
    r.clientEvents().subscribe(owner, ItemUseFinishedEvent.class, e -> {
      delivered.incrementAndGet(); observed[0] = e;
    });
    try {
      Class<?> player = Class.forName("net.minecraft.entity.player.EntityPlayer"), stack = Class.forName("net.minecraft.item.ItemStack");
      Constructor<?> finish = Class.forName("net.minecraftforge.event.entity.player.PlayerUseItemEvent$Finish")
          .getConstructor(player, stack, int.class, stack);
      Object bus = Class.forName("net.minecraftforge.common.MinecraftForge").getField("EVENT_BUS").get(null);
      Method post = method(bus.getClass(), new Class<?>[] {Class.forName("net.minecraftforge.fml.common.eventhandler.Event")}, "post");
      Object milk = stack("milk_bucket"), bucket = stack("bucket");
      post.invoke(bus, finish.newInstance(local, milk, 0, bucket));
      check(delivered.get() == 1, "Forge Finish for local consumption reaches the native event bridge exactly once");
      Method payload = method(ItemUseFinishedEvent.class, "item");
      Object copied = payload.invoke(observed[0]);
      Method item = method(stack, "getItem", "func_77973_b");
      check(copied != milk && item.invoke(copied) == item.invoke(milk) && item.invoke(copied) != item.invoke(bucket),
          "Finish bridge copies consumed milk rather than the replacement bucket");
      Field count = field(stack, "stackSize", "field_77994_a");
      count.setInt(milk, 0);
      check(count.getInt(payload.invoke(observed[0])) == 1, "source stack mutation cannot alter the consumed-item snapshot");
      count.setInt(copied, 77);
      check(count.getInt(payload.invoke(observed[0])) == 1, "payload access returns a defensive consumed-stack copy");
      post.invoke(bus, finish.newInstance(remote, milk, 0, bucket));
      check(delivered.get() == 1, "Forge Finish for a remote player cannot start local consumption effects");
    } finally {
      owner.close();
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"}) private static Object enumValue(Class<?> type, String name) {
    return Enum.valueOf((Class) type, name);
  }
  private static void outbound(ClientRuntime r, Object packet) throws Exception {
    Object game = method(Class.forName("net.minecraft.client.Minecraft"), "getMinecraft", "func_71410_x").invoke(null);
    Object handler = method(game.getClass(), "getNetHandler", "func_147114_u").invoke(game);
    method(handler.getClass(), new Class<?>[] {Class.forName("net.minecraft.network.Packet")},
        "addToSendQueue", "func_147297_a").invoke(handler, packet);
  }
  private static void chat(ClientRuntime r, String text, int type) throws Exception {
    Class<?> component = Class.forName("net.minecraft.util.IChatComponent");
    Object message = Class.forName("net.minecraft.util.ChatComponentText").getConstructor(String.class).newInstance(text);
    Object packet = Class.forName("net.minecraft.network.play.server.S02PacketChat")
        .getConstructor(component, byte.class).newInstance(message, (byte) type);
    r.clientEvents().post(ClientPacketEvent.class.getConstructor(Class.forName("net.minecraft.network.Packet")).newInstance(packet));
  }
  private static void use(Object player, Object stack) throws Exception {
    method(player.getClass(), new Class<?>[] {Class.forName("net.minecraft.item.ItemStack"), int.class}, "setItemInUse", "func_71008_a")
        .invoke(player, stack, 72000);
  }
  private static void clearUse(Object player) throws Exception { method(player.getClass(), "clearItemInUse", "func_71041_bz").invoke(player); }
  @SuppressWarnings({"unchecked", "rawtypes"}) private static void restore(ClientRuntime r, String id, Setting setting, Object value) {
    r.modules().setSetting(id, setting, value);
  }
  private static void check(boolean value, String message) {
    checks++; if (!value) throw new AssertionError("B4 movement/BedWars: " + message);
  }
}
