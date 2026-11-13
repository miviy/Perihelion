package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import perihelion.core.event.EventOwner;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleEntry;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.ClientSendFactEvent;

/** Real inventory transactions and global NoSlow packet windows across activation changes. */
public final class InventoryTimingParityProbe {
  private static int checks;

  public static void run(ClientRuntime runtime) throws Exception {
    checks = 0;
    Object game =
        method(Class.forName("net.minecraft.client.Minecraft"), "getMinecraft", "func_71410_x")
            .invoke(null);
    Object player = field(game.getClass(), "thePlayer", "field_71439_g").get(game);
    Object inventory = field(player.getClass(), "inventory", "field_71071_by").get(player);
    Object[] items =
        (Object[]) field(inventory.getClass(), "mainInventory", "field_70462_a").get(inventory);
    Object[] armor =
        (Object[]) field(inventory.getClass(), "armorInventory", "field_70460_b").get(inventory);
    Object[] oldItems = items.clone(), oldArmor = armor.clone();
    Field selected = field(inventory.getClass(), "currentItem", "field_70461_c"),
        screen = field(game.getClass(), "currentScreen", "field_71462_r"),
        focus = field(game.getClass(), "inGameHasFocus", "field_71415_G"),
        container = field(player.getClass(), "openContainer", "field_71070_bA"),
        ticks = field(player.getClass(), "ticksExisted", "field_70173_aa");
    Object oldScreen = screen.get(game), oldContainer = container.get(player);
    int oldSlot = selected.getInt(inventory), oldTicks = ticks.getInt(player);
    boolean oldFocus = focus.getBoolean(game);
    Object oldCursor =
        method(inventory.getClass(), "getItemStack", "func_70445_o").invoke(inventory);
    Method cursor =
        method(
            inventory.getClass(),
            new Class<?>[] {Class.forName("net.minecraft.item.ItemStack")},
            "setItemStack",
            "func_70437_b");
    Object oldUse = method(player.getClass(), "getItemInUse", "func_71011_bu").invoke(player);
    int oldUseCount =
        ((Number) method(player.getClass(), "getItemInUseCount", "func_71052_bv").invoke(player))
            .intValue();
    Method clearUse = method(player.getClass(), "clearItemInUse", "func_71041_bz"),
        setUse =
            method(
                player.getClass(),
                new Class<?>[] {Class.forName("net.minecraft.item.ItemStack"), int.class},
                "setItemInUse",
                "func_71008_a");
    Map<String, Boolean> enabled = new LinkedHashMap<>();
    for (ModuleEntry entry : runtime.modules().entries()) {
      if (!entry.info().requiresWorld()) continue;
      enabled.put(entry.info().id(), entry.requestedEnabled());
      runtime.modules().setEnabled(entry.info().id(), false);
    }
    Map<String, Map<Setting<?>, Object>> savedSettings = new LinkedHashMap<>();
    Map<Object, Long> clocks = new LinkedHashMap<>();
    for (String id : new String[] {"manager", "autoarmor", "noslow"}) {
      Object module = runtime.modules().require(id).module();
      Map<Setting<?>, Object> values = new LinkedHashMap<>();
      for (Setting<?> setting : runtime.modules().require(id).module().settings().values())
        values.put(setting, setting.get());
      savedSettings.put(id, values);
      if (!id.equals("noslow"))
        clocks.put(module, field(module.getClass(), "lastAction").getLong(module));
    }
    EventOwner owner =
        new EventOwner(
            "inventory-timing-parity",
            failure -> {
              throw new AssertionError(failure);
            });
    owner.activate();
    AtomicInteger clicks = new AtomicInteger(), uses = new AtomicInteger();
    AutoCloseable subscription =
        runtime
            .clientEvents()
            .subscribe(
                owner,
                ClientSendFactEvent.class,
                event -> {
                  if (event.action() == ClientSendFactEvent.Action.USE_ITEM
                      || event.action() == ClientSendFactEvent.Action.PLACE) uses.incrementAndGet();
                });
    AutoCloseable wireSubscription =
        runtime
            .networkEvents()
            .subscribe(
                owner,
                perihelion.integration.minecraft189.event.PacketSendEvent.class,
                Integer.MAX_VALUE,
                true,
                event -> {
                  Object packet = event.getClass().getMethod("packet").invoke(event);
                  if (packet.getClass().getSimpleName().equals("C0EPacketClickWindow"))
                    clicks.incrementAndGet();
                });
    try (GameplayLoopback peer = new GameplayLoopback(game)) {
      screen.set(game, null);
      focus.setBoolean(game, true);
      container.set(
          player, field(player.getClass(), "inventoryContainer", "field_71069_bz").get(player));
      cursor.invoke(inventory, new Object[] {null});
      clearUse.invoke(player);
      selected.setInt(inventory, 0);
      set(runtime, "manager", "instant", "false");
      set(runtime, "manager", "inv-only", "false");
      set(runtime, "manager", "send-inv-packets", "false");
      set(runtime, "manager", "not-moving", "false");
      set(runtime, "manager", "drop-items", "false");
      restore(
          runtime,
          "manager",
          runtime.modules().require("manager").module().settings().get("protected-slots"),
          "");
      restore(
          runtime,
          "manager",
          runtime.modules().require("manager").module().settings().get("protected-items"),
          "");
      set(runtime, "manager", "protect-named", "false");
      set(runtime, "manager", "manage-weapon", "true");
      set(runtime, "manager", "manage-pickaxe", "true");
      set(runtime, "manager", "weapon-slot", "1");
      set(runtime, "manager", "pickaxe-slot", "2");
      set(runtime, "autoarmor", "inventory-only", "false");
      set(runtime, "autoarmor", "stop-when-moving", "false");
      for (String id : new String[] {"manager", "autoarmor"}) {
        set(runtime, id, "delay", "300");
        Object module = runtime.modules().require(id).module();
        Field clock = field(module.getClass(), "lastAction");
        constructorClock(runtime, module.getClass());
        Arrays.fill(items, null);
        Arrays.fill(armor, null);
        Object first = stack(id.equals("manager") ? "diamond_sword" : "diamond_helmet");
        Object second = stack(id.equals("manager") ? "diamond_pickaxe" : "diamond_chestplate");
        items[9] = first;
        items[10] = second;
        int before = clicks.get();
        clock.setLong(module, now() - 301);
        runtime.modules().setEnabled(id, true);
        motion(player);
        check(clicks.get() == before + 1, id + " performs one actual eligible transaction");
        check(
            sameItem(id.equals("manager") ? items[0] : armor[3], first) && items[9] == null,
            id + " first transaction changed the actual container inventory");
        long firstAction = clock.getLong(module);
        runtime.modules().setEnabled(id, false);
        runtime.modules().setEnabled(id, true);
        check(clock.getLong(module) == firstAction, id + " immediate toggle preserves action time");
        motion(player);
        check(
            clicks.get() == before + 1 && items[10] == second,
            id + " immediate toggle cannot cause a second pending transaction");

        runtime.modules().setEnabled(id, false);
        long partial = now() - 100;
        clock.setLong(module, partial);
        runtime.modules().setEnabled(id, true);
        check(clock.getLong(module) == partial, id + " retains partially elapsed delay on enable");
        motion(player);
        check(
            clicks.get() == before + 1 && items[10] == second,
            id + " waits for the remaining interval, rather than bypassing it");

        runtime.modules().setEnabled(id, false);
        long expired = now() - 301;
        clock.setLong(module, expired);
        runtime.modules().setEnabled(id, true);
        check(clock.getLong(module) == expired, id + " does not restart an expired delay");
        motion(player);
        check(clicks.get() == before + 2, id + " resumes immediately after the delay has elapsed");
        check(
            sameItem(id.equals("manager") ? items[1] : armor[2], second) && items[10] == null,
            id + " expired-delay transaction moved the second actual item");
        runtime.modules().setEnabled(id, false);
      }

      noSlow(runtime, player, inventory, items, selected, ticks, setUse, clearUse, uses, peer);
      boundary(peer, 0x49540001);
      int decodedClicks = 0;
      for (Object packet : peer.received)
        if (packet.getClass().getSimpleName().equals("C0EPacketClickWindow")) decodedClicks++;
      check(
          decodedClicks == 4, "PLAY peer decoded exactly the four intended inventory transactions");
      check(peer.failure.get() == null, "inventory and NoSlow packets serialize through PLAY");
      System.out.println("PERIHELION_SMOKE INVENTORY_TIMING_PARITY checks=" + checks + " PASS");
    } finally {
      for (String id : savedSettings.keySet()) runtime.modules().setEnabled(id, false);
      subscription.close();
      wireSubscription.close();
      owner.close();
      clearUse.invoke(player);
      System.arraycopy(oldItems, 0, items, 0, items.length);
      System.arraycopy(oldArmor, 0, armor, 0, armor.length);
      selected.setInt(inventory, oldSlot);
      ticks.setInt(player, oldTicks);
      container.set(player, oldContainer);
      cursor.invoke(inventory, oldCursor);
      if (oldUse != null) setUse.invoke(player, oldUse, oldUseCount);
      for (Map.Entry<Object, Long> clock : clocks.entrySet())
        field(clock.getKey().getClass(), "lastAction").setLong(clock.getKey(), clock.getValue());
      for (Map.Entry<String, Map<Setting<?>, Object>> module : savedSettings.entrySet())
        for (Map.Entry<Setting<?>, Object> value : module.getValue().entrySet())
          restore(runtime, module.getKey(), value.getKey(), value.getValue());
      screen.set(game, oldScreen);
      focus.setBoolean(game, oldFocus);
      for (Map.Entry<String, Boolean> entry : enabled.entrySet())
        if (entry.getValue()) runtime.modules().setEnabled(entry.getKey(), true);
      HookBridge.gameControls().apply();
    }
  }

  private static void constructorClock(ClientRuntime runtime, Class<?> type) throws Exception {
    long before = now();
    Constructor<?> constructor = type.getDeclaredConstructor(ClientRuntime.class);
    constructor.setAccessible(true);
    Object fresh = constructor.newInstance(runtime);
    long initialized = field(type, "lastAction").getLong(fresh), after = now();
    check(
        initialized >= before && initialized <= after,
        type.getSimpleName() + " initializes its timer at construction like TimerUtils");
  }

  private static void noSlow(
      ClientRuntime runtime,
      Object player,
      Object inventory,
      Object[] items,
      Field selected,
      Field ticks,
      Method setUse,
      Method clearUse,
      AtomicInteger uses,
      GameplayLoopback peer)
      throws Exception {
    Arrays.fill(items, null);
    Object bread = stack("bread");
    items[0] = bread;
    selected.setInt(inventory, 0);
    clearUse.invoke(player);
    setUse.invoke(player, bread, 32);
    int tick = ticks.getInt(player);
    ticks.setInt(player, tick + Math.floorMod(-tick, 3));
    Class<?> item = Class.forName("net.minecraft.item.ItemStack");
    Object use =
        Class.forName("net.minecraft.network.play.client.C08PacketPlayerBlockPlacement")
            .getConstructor(item)
            .newInstance(bread);
    Class<?> walk = Class.forName("net.minecraft.network.play.client.C03PacketPlayer");
    Object slot =
        Class.forName("net.minecraft.network.play.client.C09PacketHeldItemChange")
            .getConstructor(int.class)
            .newInstance(0);
    send(peer, slot);
    set(runtime, "noslow", "mode", "INVALID");
    set(runtime, "noslow", "food-items", "true");
    int beforeUses = uses.get();
    for (String transition : new String[] {"enable", "mode", "category"}) {
      if (transition.equals("enable")) runtime.modules().setEnabled("noslow", false);
      else if (transition.equals("mode")) set(runtime, "noslow", "mode", "VANILLA");
      send(peer, walk.getConstructor(boolean.class).newInstance(false));
      send(peer, use);
      int userUses = uses.get();
      check(
          HookBridge.gameControls().clientActionMask() == 8,
          "fresh user use is globally known before NoSlow " + transition);
      if (transition.equals("enable")) runtime.modules().setEnabled("noslow", true);
      else if (transition.equals("mode")) set(runtime, "noslow", "mode", "INVALID");
      else {
        set(runtime, "noslow", "food-items", "false");
        set(runtime, "noslow", "food-items", "true");
      }
      HookBridge.gameControls().beforeInput();
      check(uses.get() == userUses, "NoSlow " + transition + " cannot erase the fresh C08 window");
    }
    send(peer, walk.getConstructor(boolean.class).newInstance(false));
    int clean = uses.get();
    HookBridge.gameControls().beforeInput();
    check(uses.get() == clean + 1, "an ordinary C03 reopens exactly one INVALID use opportunity");
    HookBridge.gameControls().beforeInput();
    check(uses.get() == clean + 1, "INVALID's own C08 prevents another use in the same window");
    runtime.modules().setEnabled("noslow", false);
    clearUse.invoke(player);
    boundary(peer, 0x49540002);
    int decodedUses = 0;
    for (Object packet : peer.received)
      if (packet.getClass().getSimpleName().equals("C08PacketPlayerBlockPlacement")) decodedUses++;
    check(
        uses.get() == beforeUses + 4 && decodedUses == 4,
        "PLAY peer decoded three user C08s and only the one permitted INVALID C08");
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
    send(
        peer,
        Class.forName("net.minecraft.network.play.client.C00PacketKeepAlive")
            .getConstructor(int.class)
            .newInstance(marker));
    long deadline = System.nanoTime() + 3_000_000_000L;
    while (System.nanoTime() < deadline) {
      if (peer.failure.get() != null) throw new AssertionError(peer.failure.get());
      for (Object packet : peer.received)
        if (packet.getClass().getSimpleName().equals("C00PacketKeepAlive")
            && ((Number) method(packet.getClass(), "getKey", "func_149460_c").invoke(packet))
                    .intValue()
                == marker) return;
      Thread.sleep(2);
    }
    throw new AssertionError("inventory timing PLAY boundary timed out");
  }

  private static long now() {
    return System.nanoTime() / 1_000_000L;
  }

  private static boolean sameItem(Object actual, Object expected) throws Exception {
    return actual != null
        && expected != null
        && method(actual.getClass(), "getItem", "func_77973_b").invoke(actual)
            == method(expected.getClass(), "getItem", "func_77973_b").invoke(expected);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void restore(ClientRuntime runtime, String id, Setting setting, Object value) {
    runtime.modules().setSetting(id, setting, value);
  }

  private static void check(boolean condition, String label) {
    checks++;
    if (!condition) throw new AssertionError(label);
  }
}
