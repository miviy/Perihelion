package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;
import static perihelion.smoke.RotationPipelineProbe.*;

import java.lang.reflect.*;
import java.util.*;
import perihelion.core.event.ClientTickEvent;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleEntry;
import perihelion.core.setting.Setting;

/** Packaged Yuri October 25 update: real container clicks and world ray intersections. */
public final class YuriUpdateProbe {
  private static int checks;

  public static void run(ClientRuntime r) throws Exception {
    checks = 0;
    Object game =
        method(Class.forName("net.minecraft.client.Minecraft"), "getMinecraft", "func_71410_x")
            .invoke(null);
    Object player = field(game.getClass(), "thePlayer", "field_71439_g").get(game);
    Object world = field(game.getClass(), "theWorld", "field_71441_e").get(game);
    Object inventory = field(player.getClass(), "inventory", "field_71071_by").get(player);
    Object[] items =
        (Object[]) field(inventory.getClass(), "mainInventory", "field_70462_a").get(inventory);
    Object[] oldItems = items.clone();
    Field screen = field(game.getClass(), "currentScreen", "field_71462_r");
    Object oldScreen = screen.get(game);
    Map<String, Boolean> enabled = new LinkedHashMap<>();
    Map<Setting<?>, Object> settings = new LinkedHashMap<>();
    for (ModuleEntry entry : r.modules().entries()) {
      if (!entry.info().requiresWorld()) continue;
      enabled.put(entry.info().id(), entry.requestedEnabled());
      r.modules().setEnabled(entry.info().id(), false);
    }
    for (String id : new String[] {"manager", "aimassist"})
      for (Setting<?> s : r.modules().require(id).module().settings().values())
        settings.put(s, s.get());
    double x = number(player, "posX", "field_70165_t"),
        y = number(player, "posY", "field_70163_u"),
        z = number(player, "posZ", "field_70161_v");
    float yaw = field(player.getClass(), "rotationYaw", "field_70177_z").getFloat(player);
    float pitch = field(player.getClass(), "rotationPitch", "field_70125_A").getFloat(player);
    try (GameplayLoopback peer = new GameplayLoopback(game)) {
      manager(r, game, player, items, screen);
      aim(r, game, player, world, screen, x, z);
      peer.client.eventLoop().submit(() -> {}).syncUninterruptibly();
      check(peer.failure.get() == null, "actual inventory packets serialize through PLAY");
      System.out.println("PERIHELION_SMOKE YURI_UPDATE checks=" + checks + " PASS");
    } finally {
      for (String id : new String[] {"manager", "aimassist"}) r.modules().setEnabled(id, false);
      System.arraycopy(oldItems, 0, items, 0, items.length);
      for (String id : new String[] {"manager", "aimassist"})
        for (Setting<?> s : r.modules().require(id).module().settings().values())
          restore(r, id, s, settings.get(s));
      position(player, x, y, z);
      camera(player, yaw, pitch);
      screen.set(game, oldScreen);
      for (Map.Entry<String, Boolean> e : enabled.entrySet())
        if (e.getValue()) r.modules().setEnabled(e.getKey(), true);
    }
  }

  private static void manager(
      ClientRuntime r, Object game, Object player, Object[] items, Field screen) throws Exception {
    Object manager = r.modules().require("manager").module();
    check(
        r.modules().require("manager").module().settings().get("start-delay").get().equals(120),
        "Manager start delay defaults to 120ms");
    set(r, "manager", "delay", "300");
    set(r, "manager", "start-delay", "500");
    set(r, "manager", "instant", "false");
    set(r, "manager", "inv-only", "true");
    set(r, "manager", "not-moving", "false");
    set(r, "manager", "drop-items", "false");
    set(r, "manager", "manage-weapon", "true");
    set(r, "manager", "weapon-slot", "1");
    set(r, "manager", "protected-slots", "");
    set(r, "manager", "protected-items", "");
    Field action = field(manager.getClass(), "lastAction");
    Field opened = field(manager.getClass(), "inventoryOpenedAt");
    Arrays.fill(items, null);
    Object sword = stack("diamond_sword");
    items[9] = sword;
    screen.set(game, null);
    r.modules().setEnabled("manager", true);
    action.setLong(manager, now() - 1000);
    motion(player);
    check(items[9] == sword, "Inv Only still blocks closed-inventory sorting");
    screen.set(game, inventoryScreen(player));
    motion(player);
    check(items[9] == sword, "opening inventory waits before the first actual click");
    opened.setLong(manager, now() - 1000);
    action.setLong(manager, now());
    motion(player);
    check(items[9] == sword, "start delay does not bypass action delay");
    action.setLong(manager, now() - 1000);
    motion(player);
    check(items[9] == null && items[0] != null, "expired timers allow a real container swap");
    Arrays.fill(items, null);
    items[9] = sword;
    screen.set(game, null);
    motion(player);
    screen.set(game, inventoryScreen(player));
    action.setLong(manager, now() - 1000);
    motion(player);
    check(items[9] == sword, "reopening starts a fresh inventory delay");
    set(r, "manager", "start-delay", "0");
    motion(player);
    check(items[9] == null, "zero start delay permits immediate sorting");
    Arrays.fill(items, null);
    items[9] = sword;
    set(r, "manager", "start-delay", "500");
    set(r, "manager", "instant", "true");
    screen.set(game, inventoryScreen(player));
    action.setLong(manager, now());
    motion(player);
    check(items[9] == null, "Instant bypasses both timers");
    Arrays.fill(items, null);
    items[9] = sword;
    set(r, "manager", "instant", "false");
    set(r, "manager", "inv-only", "false");
    screen.set(game, null);
    action.setLong(manager, now() - 1000);
    motion(player);
    check(items[9] == null, "non-inventory sorting remains usable with nonzero start delay");
    r.modules().setEnabled("manager", false);
  }

  private static void aim(
      ClientRuntime r, Object game, Object player, Object world, Field screen, double x, double z)
      throws Exception {
    Object assist = r.modules().require("aimassist").module();
    check(
        r.modules()
            .require("aimassist")
            .module()
            .settings()
            .get("reduce-on-target")
            .get()
            .equals(false),
        "reduction defaults off");
    check(
        r.modules().require("aimassist").module().settings().get("reduce-factor").get().equals(.5),
        "reduction factor defaults to 50 percent");
    screen.set(game, null);
    set(r, "aimassist", "mode", "SIMPLE");
    set(r, "aimassist", "horizontal-speed", "8");
    set(r, "aimassist", "vertical-speed", "6");
    set(r, "aimassist", "distance", "5");
    r.modules().setEnabled("aimassist", true);
    // A level ray crosses a pig's body above the player's feet in empty sky.
    position(player, Math.floor(x) + .5, 200, Math.floor(z) + .5);
    field(player.getClass(), "prevPosX", "field_70169_q").setDouble(player, Math.floor(x) + .5);
    field(player.getClass(), "prevPosY", "field_70167_r").setDouble(player, 200);
    field(player.getClass(), "prevPosZ", "field_70166_s").setDouble(player, Math.floor(z) + .5);
    camera(player, 0, 0);
    Object target = pig(world, -991812, Math.floor(x) + .5, Math.floor(z) + 4.5);
    Object nearer = pig(world, -991813, Math.floor(x) + 3.5, Math.floor(z) + 2.5);
    Field selected = field(assist.getClass(), "target");
    Method put =
        method(
            world.getClass(),
            new Class<?>[] {
              Class.forName("net.minecraft.util.BlockPos"),
              Class.forName("net.minecraft.block.state.IBlockState"),
              int.class
            },
            "setBlockState",
            "func_180501_a");
    Object obstruction = pos((int) Math.floor(x), 201, (int) Math.floor(z) + 2);
    Method read =
        method(
            world.getClass(),
            new Class<?>[] {Class.forName("net.minecraft.util.BlockPos")},
            "getBlockState",
            "func_180495_p");
    Object oldBlock = read.invoke(world, obstruction);
    try {
      selected.set(assist, target);
      sample(r);
      speeds(assist, 8, 6, "disabled option preserves existing speed");
      set(r, "aimassist", "reduce-on-target", "true");
      sample(r);
      speeds(assist, 4, 3, "visible target reduces both speeds by 50 percent");
      set(r, "aimassist", "reduce-factor", "1");
      speeds(assist, 0, 0, "factor one removes both speed inputs");
      set(r, "aimassist", "reduce-factor", "0");
      speeds(assist, 8, 6, "factor zero leaves both speeds unchanged");
      set(r, "aimassist", "reduce-factor", ".5");
      camera(player, 90, 0);
      sample(r);
      speeds(assist, 8, 6, "off-target ray restores full speed");
      camera(player, 0, 0);
      put.invoke(world, obstruction, state("stone"), 2);
      sample(r);
      speeds(assist, 8, 6, "blocking world geometry prevents slowdown");
      put.invoke(world, obstruction, state("air"), 2);
      position(nearer, Math.floor(x) + .5, 201, Math.floor(z) + 2.5);
      sample(r);
      speeds(assist, 8, 6, "nearer collidable entity prevents slowdown on the selected target");
      position(nearer, Math.floor(x) + 3.5, 201, Math.floor(z) + 2.5);
      position(target, Math.floor(x) + .5, 201, Math.floor(z) + 7.5);
      sample(r);
      speeds(assist, 8, 6, "configured distance bounds the ray");
      position(target, Math.floor(x) + .5, 201, Math.floor(z) + 4.5);
      set(r, "aimassist", "mode", "ADAPTIVE");
      selected.set(assist, target);
      sample(r);
      speeds(assist, 4, 3, "Adaptive uses the same reduction");
      selected.set(assist, nearer);
      speeds(assist, 8, 6, "retargeting cannot inherit another target's slowdown");
      set(r, "aimassist", "mode", "B4_SILENT");
      selected.set(assist, target);
      sample(r);
      speeds(assist, 8, 6, "Silent mode retains its existing speed behavior");
      r.modules().setEnabled("aimassist", false);
      check(
          field(assist.getClass(), "reducedTarget").get(assist) == null,
          "disable clears sampled target state");
    } finally {
      put.invoke(world, obstruction, oldBlock, 2);
      Method remove =
          method(
              world.getClass(),
              new Class<?>[] {int.class},
              "removeEntityFromWorld",
              "func_73028_b");
      remove.invoke(world, -991812);
      remove.invoke(world, -991813);
    }
  }

  private static Object pig(Object world, int id, double x, double z) throws Exception {
    Object pig =
        Class.forName("net.minecraft.entity.passive.EntityPig")
            .getConstructor(Class.forName("net.minecraft.world.World"))
            .newInstance(world);
    position(pig, x, 201, z);
    method(
            world.getClass(),
            new Class<?>[] {int.class, Class.forName("net.minecraft.entity.Entity")},
            "addEntityToWorld",
            "func_73027_a")
        .invoke(world, id, pig);
    return pig;
  }

  private static Object inventoryScreen(Object player) throws Exception {
    return Class.forName("net.minecraft.client.gui.inventory.GuiInventory")
        .getConstructor(Class.forName("net.minecraft.entity.player.EntityPlayer"))
        .newInstance(player);
  }

  private static void sample(ClientRuntime r) {
    r.clientEvents().post(ClientTickEvent.START);
  }

  private static long now() {
    return System.nanoTime() / 1_000_000L;
  }

  private static void speeds(Object assist, float h, float v, String label) throws Exception {
    check(
        ((Number) method(assist.getClass(), "getHSpeed").invoke(assist)).floatValue() == h
            && ((Number) method(assist.getClass(), "getVSpeed").invoke(assist)).floatValue() == v,
        label);
  }

  @SuppressWarnings("unchecked")
  private static <T> void restore(ClientRuntime r, String id, Setting<?> setting, Object value) {
    r.modules().setSetting(id, (Setting<T>) setting, (T) value);
  }

  private static void check(boolean value, String label) {
    if (!value) throw new AssertionError("Yuri update: " + label);
    checks++;
  }
}
