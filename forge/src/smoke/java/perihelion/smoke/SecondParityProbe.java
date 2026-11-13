package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;
import static perihelion.smoke.RotationPipelineProbe.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import perihelion.core.event.EventOwner;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.*;

/** Second source comparison: Intave environmental gates and actual chest Blink capture. */
public final class SecondParityProbe {
  private static int checks;
  private static final AtomicInteger tcpEvents = new AtomicInteger();

  public static int networkEvents() {
    return tcpEvents.get();
  }

  static void run(ClientRuntime runtime) throws Exception {
    checks = 0;
    tcpEvents.set(0);
    Object game =
        method(Class.forName("net.minecraft.client.Minecraft"), "getMinecraft", "func_71410_x")
            .invoke(null);
    Object player = field(game.getClass(), "thePlayer", "field_71439_g").get(game);
    Object world = field(game.getClass(), "theWorld", "field_71441_e").get(game);
    Object history =
        ((perihelion.integration.minecraft189.control.PlayerGroundAccess) player)
            .perihelion$groundTicks();
    Map<Field, Object> savedHistory = new LinkedHashMap<>();
    for (Field f : history.getClass().getDeclaredFields()) {
      f.setAccessible(true);
      savedHistory.put(f, f.get(history));
    }

    Field screen = field(game.getClass(), "currentScreen", "field_71462_r");
    Object savedScreen = screen.get(game);
    Map<Field, Object> saved = new LinkedHashMap<>();
    for (String[] names :
        new String[][] {
          {"motionX", "field_70159_w"}, {"motionY", "field_70181_x"},
          {"motionZ", "field_70179_y"}, {"hurtTime", "field_70737_aN"},
          {"onGround", "field_70122_E"}, {"isInWeb", "field_70134_J"},
          {"inWater", "field_70171_ac"}, {"fire", "field_70151_c"}
        }) {
      Field f = field(player.getClass(), names);
      saved.put(f, f.get(player));
    }
    Method sprint =
        method(player.getClass(), new Class<?>[] {boolean.class}, "setSprinting", "func_70031_b");
    boolean wasSprinting =
        (Boolean) method(player.getClass(), "isSprinting", "func_70051_ag").invoke(player);
    Method flag =
        method(
            player.getClass(),
            new Class<?>[] {int.class, boolean.class},
            "setFlag",
            "func_70052_a");
    boolean wasBurningFlag =
        (Boolean)
            method(player.getClass(), new Class<?>[] {int.class}, "getFlag", "func_70083_f")
                .invoke(player, 0);
    Object pig =
        Class.forName("net.minecraft.entity.passive.EntityPig")
            .getConstructor(Class.forName("net.minecraft.world.World"))
            .newInstance(world);
    Map<String, Boolean> enabled = new LinkedHashMap<>();
    Map<Setting<?>, Object> settings = new LinkedHashMap<>();
    for (String id : new String[] {"velocity", "invmove"}) {
      enabled.put(id, runtime.modules().require(id).requestedEnabled());
      runtime.modules().setEnabled(id, false);
      for (Setting<?> setting : runtime.modules().require(id).module().settings().values())
        settings.put(setting, setting.get());
    }
    try {
      flag.invoke(player, 0, false);
      screen.set(game, null);
      intave(runtime, player, pig, sprint);
      runtime.modules().setEnabled("velocity", false);
      chest(runtime, game, player, screen);
      check(
          runtime.modules().require("velocity").state()
              != perihelion.core.module.ModuleState.FAULTED,
          "Intave cases do not fault");
      check(
          runtime.modules().require("invmove").state()
              != perihelion.core.module.ModuleState.FAULTED,
          "Inv Move cases do not fault");
      System.out.println(
          "PERIHELION_SMOKE SECOND_PARITY checks="
              + checks
              + " tcpEvents="
              + tcpEvents.get()
              + " PASS");
    } finally {
      for (String id : enabled.keySet()) runtime.modules().setEnabled(id, false);
      for (String id : enabled.keySet())
        for (Setting<?> setting : runtime.modules().require(id).module().settings().values())
          restore(runtime, id, setting, settings.get(setting));
      for (Map.Entry<Field, Object> entry : saved.entrySet())
        entry.getKey().set(player, entry.getValue());
      flag.invoke(player, 0, wasBurningFlag);
      sprint.invoke(player, wasSprinting);
      for (Map.Entry<Field, Object> entry : savedHistory.entrySet())
        entry.getKey().set(history, entry.getValue());
      screen.set(game, savedScreen);
      HookBridge.gameControls().apply();
      for (String id : enabled.keySet())
        if (enabled.get(id)) runtime.modules().setEnabled(id, true);
    }
  }

  private static void intave(ClientRuntime runtime, Object player, Object pig, Method sprint)
      throws Exception {
    set(runtime, "velocity", "mode", "INTAVE");
    set(runtime, "velocity", "ignore-on-fire", "true");
    runtime.modules().setEnabled("velocity", true);
    Constructor<?> attack =
        PlayerAttackEvent.class.getConstructor(
            Class.forName("net.minecraft.entity.EntityLivingBase"));
    Field mx = field(player.getClass(), "motionX", "field_70159_w"),
        mz = field(player.getClass(), "motionZ", "field_70179_y"),
        hurt = field(player.getClass(), "hurtTime", "field_70737_aN"),
        web = field(player.getClass(), "isInWeb", "field_70134_J"),
        water = field(player.getClass(), "inWater", "field_70171_ac"),
        fire = field(player.getClass(), "fire", "field_70151_c"),
        ground = field(player.getClass(), "onGround", "field_70122_E");
    for (String condition : new String[] {"normal", "web", "water", "burning"}) {
      set(runtime, "velocity", "intave-mode", "INTAVE_LATEST");
      hurt.setInt(player, 5);
      web.setBoolean(player, condition.equals("web"));
      water.setBoolean(player, condition.equals("water"));
      fire.setInt(player, condition.equals("burning") ? 100 : 0);
      mx.setDouble(player, 1);
      mz.setDouble(player, -1);
      sprint.invoke(player, true);
      runtime.clientEvents().post(attack.newInstance(pig));
      check(
          Math.abs(mx.getDouble(player) - .6) < 1e-9
              && Math.abs(mz.getDouble(player) + .6) < 1e-9
              && !(Boolean)
                  method(player.getClass(), "isSprinting", "func_70051_ag").invoke(player),
          "Latest retains its slowdown and sprint stop while " + condition);
    }
    // A successful vanilla hit already applied its slowdown; the next attack must not double it.
    mx.setDouble(player, 1);
    mz.setDouble(player, -1);
    sprint.invoke(player, true);
    runtime.clientEvents().post(new HitSlowdownEvent());
    runtime.clientEvents().post(attack.newInstance(pig));
    check(
        mx.getDouble(player) == 1
            && mz.getDouble(player) == -1
            && (Boolean) method(player.getClass(), "isSprinting", "func_70051_ag").invoke(player),
        "Latest does not apply a second vanilla hit slowdown");
    runtime.clientEvents().post(attack.newInstance(pig));
    check(Math.abs(mx.getDouble(player) - .6) < 1e-9, "Latest consumes its hit-slowdown flag once");
    for (String condition : new String[] {"ground", "air", "web", "water", "burning", "unhurt"}) {
      set(runtime, "velocity", "intave-mode", "INTAVE_13");
      ground.setBoolean(player, !condition.equals("air"));
      hurt.setInt(player, condition.equals("unhurt") ? 0 : 5);
      web.setBoolean(player, condition.equals("web"));
      water.setBoolean(player, condition.equals("water"));
      fire.setInt(player, condition.equals("burning") ? 100 : 0);
      mx.setDouble(player, 1);
      mz.setDouble(player, -1);
      runtime.clientEvents().post(attack.newInstance(pig));
      double expected =
          condition.equals("web") || condition.equals("water") || condition.equals("unhurt")
              ? 1
              : condition.equals("air") ? .8 : .52;
      check(
          Math.abs(mx.getDouble(player) - expected) < 1e-9
              && Math.abs(mz.getDouble(player) + expected) < 1e-9,
          "Intave 13 matches its environmental gate: " + condition);
    }
    web.setBoolean(player, false);
    water.setBoolean(player, false);
    fire.setInt(player, 0);
    ground.setBoolean(player, true);
  }

  private static void chest(ClientRuntime runtime, Object game, Object player, Field screen)
      throws Exception {
    Class<?> inventoryType = Class.forName("net.minecraft.inventory.IInventory"),
        potionType = Class.forName("net.minecraft.potion.Potion"),
        effectType = Class.forName("net.minecraft.potion.PotionEffect");
    Object inventory = field(player.getClass(), "inventory", "field_71071_by").get(player);
    Object chest =
        Class.forName("net.minecraft.inventory.InventoryBasic")
            .getConstructor(String.class, boolean.class, int.class)
            .newInstance("Parity chest", true, 27);
    Object gui =
        Class.forName("net.minecraft.client.gui.inventory.GuiChest")
            .getConstructor(inventoryType, inventoryType)
            .newInstance(inventory, chest);
    Object speed = field(potionType, "moveSpeed", "field_76424_c").get(null);
    Object savedEffect =
        method(
                player.getClass(),
                new Class<?>[] {potionType},
                "getActivePotionEffect",
                "func_70660_b")
            .invoke(player, speed);
    int speedId = ((Number) method(potionType, "getId", "func_76396_c").invoke(speed)).intValue();
    Method remove =
        method(player.getClass(), new Class<?>[] {int.class}, "removePotionEffect", "func_82170_o");
    Method add =
        method(player.getClass(), new Class<?>[] {effectType}, "addPotionEffect", "func_70690_d");
    EventOwner observer =
        new EventOwner(
            "second-parity-tcp",
            failure -> {
              throw new AssertionError(failure);
            });
    observer.activate();
    AutoCloseable ledger =
        runtime
            .networkEvents()
            .subscribe(observer, PacketSendEvent.class, event -> tcpEvents.incrementAndGet());
    try (GameplayLoopback peer = new GameplayLoopback(game)) {
      set(runtime, "invmove", "chest", "BLINK");
      screen.set(game, gui);
      for (String condition :
          new String[] {"midair-disabled", "landing-denied", "speed-disabled", "allowed"}) {
        runtime.modules().setEnabled("invmove", false);
        remove.invoke(player, speedId);
        set(
            runtime,
            "invmove",
            "allow-midair",
            (condition.equals("midair-disabled") || condition.equals("landing-denied"))
                ? "false"
                : "true");
        set(runtime, "invmove", "allow-speed-potion", "false");
        field(player.getClass(), "onGround", "field_70122_E")
            .setBoolean(player, !condition.equals("midair-disabled"));
        if (condition.equals("speed-disabled"))
          add.invoke(
              player,
              effectType
                  .getConstructor(int.class, int.class, int.class)
                  .newInstance(speedId, 200, 0));
        runtime.modules().setEnabled("invmove", true);
        Object history =
            ((perihelion.integration.minecraft189.control.PlayerGroundAccess) player)
                .perihelion$groundTicks();
        field(history.getClass(), "beforeAir")
            .setInt(
                history,
                (condition.equals("midair-disabled") || condition.equals("landing-denied"))
                    ? 3
                    : 0);
        HookBridge.gameControls().beforeInput();
        flush(peer.client);
        peer.received.clear();
        Object look =
            Class.forName("net.minecraft.network.play.client.C03PacketPlayer$C05PacketPlayerLook")
                .getConstructor(float.class, float.class, boolean.class)
                .newInstance(43f, 22f, false);
        Object handler = method(game.getClass(), "getNetHandler", "func_147114_u").invoke(game);
        method(
                handler.getClass(),
                new Class<?>[] {Class.forName("net.minecraft.network.Packet")},
                "addToSendQueue",
                "func_147297_a")
            .invoke(handler, look);
        flush(peer.client);
        if (condition.equals("allowed")) {
          check(
              HookBridge.gameControls().outboundOrder().owner() != null && peer.received.isEmpty(),
              "eligible chest Blink holds the actual TCP look packet");
          runtime.modules().setEnabled("invmove", false);
          peer.await(1);
          check(
              HookBridge.gameControls().outboundOrder().owner() == null,
              "disable flushes the original TCP packet and releases sequence ownership");
        } else {
          peer.await(1);
          check(
              HookBridge.gameControls().outboundOrder().owner() == null,
              condition + " does not start chest Blink or capture traffic");
        }
        runtime.modules().setEnabled("invmove", false);
        flush(peer.client);
      }
    } finally {
      runtime.modules().setEnabled("invmove", false);
      ledger.close();
      observer.close();
      remove.invoke(player, speedId);
      if (savedEffect != null) add.invoke(player, savedEffect);
      screen.set(game, null);
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void restore(ClientRuntime runtime, String id, Setting setting, Object value) {
    runtime.modules().setSetting(id, setting, value);
  }

  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError("Second parity: " + message);
    checks++;
  }
}
