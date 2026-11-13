package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;
import static perihelion.smoke.RotationPipelineProbe.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import perihelion.core.event.EventOwner;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.*;
import perihelion.integration.minecraft189.network.*;

/** Source scan ordering and action windows on actual local/remote packet dispatch. */
public final class FireballParityProbe {
  private static int checks;
  private static final AtomicInteger network = new AtomicInteger();
  private static final AtomicInteger cancelledEvents = new AtomicInteger();

  public static int networkEvents() {
    // SmokeProbe's default observer does not receive already-cancelled events.
    return network.get() - cancelledEvents.get();
  }

  static void run(ClientRuntime r) throws Exception {
    checks = 0;
    network.set(0);
    cancelledEvents.set(0);
    Object game =
        method(Class.forName("net.minecraft.client.Minecraft"), "getMinecraft", "func_71410_x")
            .invoke(null);
    Object p = field(game.getClass(), "thePlayer", "field_71439_g").get(game),
        world = field(game.getClass(), "theWorld", "field_71441_e").get(game),
        inv = field(p.getClass(), "inventory", "field_71071_by").get(p);
    Object[] items = (Object[]) field(inv.getClass(), "mainInventory", "field_70462_a").get(inv),
        savedItems = items.clone();
    Field selected = field(inv.getClass(), "currentItem", "field_70461_c"),
        screen = field(game.getClass(), "currentScreen", "field_71462_r"),
        focus = field(game.getClass(), "inGameHasFocus", "field_71415_G");
    int oldSlot = selected.getInt(inv);
    Object oldScreen = screen.get(game);
    boolean oldFocus = focus.getBoolean(game);
    Map<Field, Object> saved = new LinkedHashMap<>();
    for (String[] names :
        new String[][] {
          {"rotationYaw", "field_70177_z"},
          {"rotationPitch", "field_70125_A"},
          {"lastReportedYaw", "field_175164_bL"},
          {"lastReportedPitch", "field_175165_bM"},
          {"onGround", "field_70122_E"}
        }) {
      Field f = field(p.getClass(), names);
      saved.put(f, f.get(p));
    }
    double x = number(p, "posX", "field_70165_t"),
        y = number(p, "posY", "field_70163_u"),
        z = number(p, "posZ", "field_70161_v");
    Object module = r.modules().require("antifireball").module();
    boolean enabled = r.modules().require("antifireball").requestedEnabled();
    r.modules().setEnabled("antifireball", false);
    Object state = field(module.getClass(), "state").get(module);
    Set seen = (Set) field(state.getClass(), "attacked").get(state);
    Set oldSeen = new LinkedHashSet(seen);
    Field generation = field(state.getClass(), "generation");
    long oldGeneration = generation.getLong(state);
    Field nearby = field(state.getClass(), "nearby");
    boolean oldNearby = nearby.getBoolean(state);
    nearby.setBoolean(state, false);
    Object gui = field(game.getClass(), "ingameGUI", "field_71456_v").get(game);
    Object chat = method(gui.getClass(), "getChatGUI", "func_146158_b").invoke(gui);
    List lines = (List) field(chat.getClass(), "chatLines", "field_146252_h").get(chat);
    List drawn = (List) field(chat.getClass(), "drawnChatLines", "field_146253_i").get(chat);
    List oldLines = new ArrayList(lines), oldDrawn = new ArrayList(drawn);
    int oldNotices = notices(lines);
    EventOwner owner =
        new EventOwner(
            "fireball-parity",
            t -> {
              throw new AssertionError(t);
            });
    owner.activate();
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
    Class<?> packet = Class.forName("net.minecraft.network.Packet");
    Method packetGetter = PacketSendEvent.class.getMethod("packet");
    AtomicReference<Object> cancelled = new AtomicReference<>();
    r.networkEvents()
        .subscribe(
            owner,
            PacketSendEvent.class,
            100000,
            true,
            e -> {
              try {
                if (packetGetter.invoke(e) == cancelled.get()) {
                  e.cancel();
                  cancelledEvents.incrementAndGet();
                }
              } catch (Exception problem) {
                throw new AssertionError(problem);
              }
            });
    Method
        add =
            method(
                world.getClass(),
                new Class<?>[] {int.class, Class.forName("net.minecraft.entity.Entity")},
                "addEntityToWorld",
                "func_73027_a"),
        remove =
            method(
                world.getClass(),
                new Class<?>[] {int.class},
                "removeEntityFromWorld",
                "func_73028_b");
    Object far = ball(world), near = ball(world), other = ball(world);
    position(far, x + 6, y, z);
    position(near, x, y, z + 2);
    position(other, x + 1, y, z + 2);
    add.invoke(world, -976640, far);
    add.invoke(world, -976641, near);
    add.invoke(world, -976642, other);
    OutgoingPacketFacts facts = HookBridge.gameControls().outgoingFacts();
    try {
      screen.set(game, null);
      focus.setBoolean(game, true);
      Arrays.fill(items, null);
      items[0] = stack("diamond_sword");
      selected.setInt(inv, 0);
      field(p.getClass(), "rotationYaw", "field_70177_z").setFloat(p, 0);
      field(p.getClass(), "rotationPitch", "field_70125_A").setFloat(p, 0);
      try (GameplayLoopback peer = new GameplayLoopback(game)) {
        Object handler = method(game.getClass(), "getNetHandler", "func_147114_u").invoke(game);
        Method send =
            method(handler.getClass(), new Class<?>[] {packet}, "addToSendQueue", "func_147297_a");
        walk(p, peer);
        AtomicBoolean offThreadControlsHidden = new AtomicBoolean();
        Object backgroundAction =
            Class.forName("net.minecraft.network.play.client.C0APacketAnimation")
                .getConstructor()
                .newInstance();
        peer.client
            .eventLoop()
            .submit(
                () -> {
                  offThreadControlsHidden.set(HookBridge.gameControls() == null);
                  send.invoke(handler, backgroundAction);
                  return null;
                })
            .sync();
        drain(peer);
        check(offThreadControlsHidden.get(), "background packet facts do not expose game controls");
        check(facts.bad(), "actual Netty-thread action dispatch updates the scalar facts");
        Object backgroundWalk =
            Class.forName("net.minecraft.network.play.client.C03PacketPlayer")
                .getConstructor(boolean.class)
                .newInstance(true);
        peer.client
            .eventLoop()
            .submit(
                () -> {
                  send.invoke(handler, backgroundWalk);
                  return null;
                })
            .sync();
        drain(peer);
        check(!facts.bad(), "actual Netty-thread walking dispatch clears the scalar facts");
        // The native facts collector must work even while the consuming module is disabled.
        send.invoke(
            handler,
            Class.forName("net.minecraft.network.play.client.C09PacketHeldItemChange")
                .getConstructor(int.class)
                .newInstance(0));
        drain(peer);
        r.modules().setEnabled("antifireball", true);
        input(peer);
        check(
            facts.bad() && !r.controls().rotation().isPresent(),
            "slot traffic before enable blocks the source action window");
        check(notices(lines) == oldNotices, "bad action window also postpones the nearby notice");
        walk(p, peer);
        input(peer);
        check(
            !r.controls().rotation().isPresent(),
            "first fireball within twelve blocks masks a later close fireball");
        check(notices(lines) == oldNotices + 1, "nearby fireball produces one native notice");
        position(far, x + 11.999, y, z);
        walk(p, peer);
        input(peer);
        check(
            !r.controls().rotation().isPresent(),
            "scan includes a first fireball strictly below twelve");
        check(notices(lines) == oldNotices + 1, "continued nearby scans do not repeat the notice");
        position(far, x + 12, y, z);
        position(near, x, y, z + 3.001);
        walk(p, peer);
        input(peer);
        check(
            !r.controls().rotation().isPresent(),
            "attack range excludes a first candidate beyond three blocks");
        position(near, x, y, z + 3);
        walk(p, peer);
        input(peer);
        check(
            r.controls().rotation().isPresent(),
            "scan excludes exactly twelve and attack includes exactly three");
        position(near, x, y, z + 2);
        items[0] = stack("fire_charge");
        walk(p, peer);
        input(peer);
        check(!r.controls().rotation().isPresent(), "held fire charge prevents automated response");
        items[0] = stack("diamond_sword");
        walk(p, peer);
        input(peer);
        check(
            r.controls().rotation().get().pitch() > 18
                && r.controls().rotation().get().pitch() < 22,
            "aim uses the source clamped eye-height point rather than the fireball's feet");
        List<Object> foreign = foreignPackets(far);
        for (Object q : foreign) {
          walk(p, peer);
          send.invoke(handler, q);
          drain(peer);
          long before = attacks(peer, -976641);
          input(peer);
          check(
              facts.bad()
                  && attacks(peer, -976641) == before
                  && !r.controls().rotation().isPresent(),
              q.getClass().getSimpleName() + " blocks the action until another ordinary walk");
          walk(p, peer);
          input(peer);
          check(
              !facts.bad() && r.controls().rotation().isPresent(),
              "walking clears the window after " + q.getClass().getSimpleName());
        }
        walk(p, peer);
        Object q =
            Class.forName("net.minecraft.network.play.client.C08PacketPlayerBlockPlacement")
                .getConstructor(Class.forName("net.minecraft.item.ItemStack"))
                .newInstance(items[0]);
        cancelled.set(q);
        long uses = count(peer, "C08PacketPlayerBlockPlacement");
        send.invoke(handler, q);
        drain(peer);
        input(peer);
        check(
            facts.bad()
                && count(peer, "C08PacketPlayerBlockPlacement") == uses
                && !r.controls().rotation().isPresent(),
            "cancelled outgoing action still supplies facts without reaching the peer");
        cancelled.set(null);
        Object silent = look(14, 15);
        RawHookBridge.replay(
            () -> {
              try {
                send.invoke(handler, silent);
              } catch (Exception problem) {
                throw new AssertionError(problem);
              }
            });
        drain(peer);
        check(facts.bad(), "silent replay does not reset or count the packet window again");
        walk(p, peer);
        Class<?> status =
            Class.forName("net.minecraft.network.play.client.C16PacketClientStatus$EnumState");
        send.invoke(
            handler,
            Class.forName("net.minecraft.network.play.client.C16PacketClientStatus")
                .getConstructor(status)
                .newInstance(status.getField("PERFORM_RESPAWN").get(null)));
        drain(peer);
        check(
            !facts.bad(),
            "non-inventory client status and keepalive traffic do not dirty the window");
        peer.received.clear();
        input(peer);
        walk(p, peer);
        input(peer);
        walk(p, peer);
        check(
            attacks(peer, -976641) == 1,
            "the first eligible fireball receives exactly one response");
        int attack = index(peer, "C02PacketUseEntity");
        Class<?> walking = Class.forName("net.minecraft.network.play.client.C03PacketPlayer");
        check(
            peer.received.subList(0, attack).stream().anyMatch(walking::isInstance),
            "response has a preceding serialized walking aim");
        // The list contains the preparation tick too. Scope the PRE assertion to
        // the action tick, rather than mistaking its preceding walk for its next walk.
        orderedBeforeWalking(
            new ArrayList<>(peer.received.subList(attack - 1, peer.received.size())),
            "C02PacketUseEntity",
            "Anti Fireball source packet window");
        check(
            attack > 0 && simple(peer.received.get(attack - 1)).equals("C0APacketAnimation"),
            "serialized animation immediately precedes the fireball attack");
        input(peer);
        check(
            !r.controls().rotation().isPresent() && attacks(peer, -976642) == 0,
            "an already-hit first candidate continues to mask a later fireball");
        r.modules().setEnabled("antifireball", false);
        r.modules().setEnabled("antifireball", true);
        walk(p, peer);
        input(peer);
        check(
            !r.controls().rotation().isPresent() && attacks(peer, -976641) == 1,
            "ordinary toggles retain hit UUIDs");
        position(near, x, y, z + 13);
        walk(p, peer);
        input(peer);
        walk(p, peer);
        input(peer);
        walk(p, peer);
        check(
            attacks(peer, -976642) == 1,
            "a later candidate becomes eligible when the earlier one leaves scan range");
        check(notices(lines) == oldNotices + 1, "ordinary toggles do not repeat the nearby notice");
        position(far, x + 20, y, z);
        position(near, x + 20, y, z + 1);
        position(other, x + 20, y, z + 2);
        walk(p, peer);
        input(peer);
        check(!nearby.getBoolean(state), "an eligible empty scan rearms the notice");
        position(far, x + 6, y, z);
        walk(p, peer);
        input(peer);
        check(notices(lines) == oldNotices + 2, "a new nearby period emits a fresh notice");
        r.modules().setEnabled("antifireball", false);
      }
      // Local PLAY facts remain visible while the raw multiplayer bus stays silent.
      Object handler = method(game.getClass(), "getNetHandler", "func_147114_u").invoke(game);
      Method send =
          method(handler.getClass(), new Class<?>[] {packet}, "addToSendQueue", "func_147297_a");
      facts.reset();
      int before = network.get();
      send.invoke(
          handler,
          Class.forName("net.minecraft.network.play.client.C0APacketAnimation")
              .getConstructor()
              .newInstance());
      check(
          facts.bad() && network.get() == before,
          "local action classification does not publish multiplayer raw events");
      forceLook(p);
      motion(p);
      check(
          !facts.bad() && network.get() == before,
          "local walking clears facts without publishing raw events");
      check(
          r.modules().require("antifireball").state() != perihelion.core.module.ModuleState.FAULTED,
          "Anti Fireball remains unfaulted");
      System.out.println(
          "PERIHELION_SMOKE FIREBALL_PARITY checks="
              + checks
              + " networkEvents="
              + networkEvents()
              + " cancelledEvents="
              + cancelledEvents.get()
              + " rawEvents="
              + network.get()
              + " PASS");
    } finally {
      r.modules().setEnabled("antifireball", false);
      owner.close();
      for (int id : new int[] {-976640, -976641, -976642}) remove.invoke(world, id);
      seen.clear();
      seen.addAll(oldSeen);
      generation.setLong(state, oldGeneration);
      nearby.setBoolean(state, oldNearby);
      lines.clear();
      lines.addAll(oldLines);
      drawn.clear();
      drawn.addAll(oldDrawn);
      System.arraycopy(savedItems, 0, items, 0, items.length);
      selected.setInt(inv, oldSlot);
      for (Map.Entry<Field, Object> e : saved.entrySet()) e.getKey().set(p, e.getValue());
      position(p, x, y, z);
      screen.set(game, oldScreen);
      focus.setBoolean(game, oldFocus);
      facts.reset();
      HookBridge.gameControls().apply();
      if (enabled) r.modules().setEnabled("antifireball", true);
    }
  }

  private static int notices(List lines) throws Exception {
    int count = 0;
    for (Object line : lines) {
      Object component = method(line.getClass(), "getChatComponent", "func_151461_a").invoke(line);
      String text =
          (String)
              method(component.getClass(), "getUnformattedText", "func_150260_c").invoke(component);
      if (text.equals("[Perihelion] Fireball detected!")) count++;
    }
    return count;
  }

  private static List<Object> foreignPackets(Object target) throws Exception {
    String prefix = "net.minecraft.network.play.client.";
    Class<?> item = Class.forName("net.minecraft.item.ItemStack"),
        pos = Class.forName("net.minecraft.util.BlockPos"),
        face = Class.forName("net.minecraft.util.EnumFacing"),
        dig = Class.forName(prefix + "C07PacketPlayerDigging$Action"),
        attack = Class.forName(prefix + "C02PacketUseEntity$Action"),
        status = Class.forName(prefix + "C16PacketClientStatus$EnumState");
    return Arrays.asList(
        Class.forName(prefix + "C09PacketHeldItemChange").getConstructor(int.class).newInstance(0),
        Class.forName(prefix + "C0APacketAnimation").getConstructor().newInstance(),
        Class.forName(prefix + "C02PacketUseEntity")
            .getConstructor(Class.forName("net.minecraft.entity.Entity"), attack)
            .newInstance(target, attack.getField("ATTACK").get(null)),
        Class.forName(prefix + "C07PacketPlayerDigging")
            .getConstructor(dig, pos, face)
            .newInstance(
                dig.getField("RELEASE_USE_ITEM").get(null),
                pos.getConstructor(int.class, int.class, int.class).newInstance(0, 0, 0),
                face.getField("DOWN").get(null)),
        Class.forName(prefix + "C08PacketPlayerBlockPlacement")
            .getConstructor(item)
            .newInstance(new Object[] {null}),
        Class.forName(prefix + "C0EPacketClickWindow")
            .getConstructor(int.class, int.class, int.class, int.class, item, short.class)
            .newInstance(0, 0, 0, 0, null, (short) 1),
        Class.forName(prefix + "C0DPacketCloseWindow").getConstructor(int.class).newInstance(0),
        Class.forName(prefix + "C16PacketClientStatus")
            .getConstructor(status)
            .newInstance(status.getField("OPEN_INVENTORY_ACHIEVEMENT").get(null)));
  }

  private static Object ball(Object world) throws Exception {
    return Class.forName("net.minecraft.entity.projectile.EntityLargeFireball")
        .getConstructor(Class.forName("net.minecraft.world.World"))
        .newInstance(world);
  }

  private static Object look(float yaw, float pitch) throws Exception {
    return Class.forName("net.minecraft.network.play.client.C03PacketPlayer$C05PacketPlayerLook")
        .getConstructor(float.class, float.class, boolean.class)
        .newInstance(yaw, pitch, false);
  }

  private static void input(GameplayLoopback peer) throws Exception {
    HookBridge.gameControls().beforeInput();
    drain(peer);
  }

  private static void walk(Object p, GameplayLoopback peer) throws Exception {
    forceLook(p);
    motion(p);
    drain(peer);
  }

  private static void drain(GameplayLoopback peer) throws Exception {
    Object marker =
        Class.forName("net.minecraft.network.play.client.C00PacketKeepAlive")
            .getConstructor(int.class)
            .newInstance(0x5a139);
    method(
            peer.manager.get().getClass(),
            new Class<?>[] {Class.forName("net.minecraft.network.Packet")},
            "sendPacket",
            "func_179290_a")
        .invoke(peer.manager.get(), marker);
    long end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
    while (System.nanoTime() < end) {
      if (!peer.received.isEmpty()) {
        Object last = peer.received.get(peer.received.size() - 1);
        if (simple(last).equals("C00PacketKeepAlive")
            && ((Number) method(last.getClass(), "getKey", "func_149460_c").invoke(last)).intValue()
                == 0x5a139) {
          peer.received.remove(peer.received.size() - 1);
          return;
        }
      }
      Thread.sleep(2);
    }
    throw new AssertionError("fireball TCP drain timeout");
  }

  private static String simple(Object p) {
    return p.getClass().getSimpleName();
  }

  private static long count(GameplayLoopback peer, String name) {
    return peer.received.stream().filter(p -> simple(p).equals(name)).count();
  }

  private static long attacks(GameplayLoopback peer, int id) throws Exception {
    long count = 0;
    for (Object p : peer.received)
      if (simple(p).equals("C02PacketUseEntity")
          && ((Number) field(p.getClass(), "entityId", "field_149567_a").get(p)).intValue() == id)
        count++;
    return count;
  }

  private static int index(GameplayLoopback peer, String name) {
    for (int i = 0; i < peer.received.size(); i++)
      if (simple(peer.received.get(i)).equals(name)) return i;
    throw new AssertionError("missing " + name);
  }

  private static void check(boolean b, String message) {
    checks++;
    if (!b) throw new AssertionError(message);
  }
}
