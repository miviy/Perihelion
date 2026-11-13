package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;
import static perihelion.smoke.RotationPipelineProbe.*;

import java.lang.reflect.*;
import java.util.*;
import perihelion.core.control.*;
import perihelion.core.lifecycle.ClientRuntime;

/** Sustained native walking over a finite platform; the test never moves the player per tick. */
final class ScaffoldBridgeProbe {
  static void run(
      ClientRuntime r,
      Object game,
      Object player,
      Object[] items,
      Map<InputKey, ControlRequest<Boolean>> keys,
      B4ManualCombatProbe.NativeKeys nativeKeys,
      GameplayLoopback peer,
      double px,
      double pz)
      throws Exception {
    Object world = field(game.getClass(), "theWorld", "field_71441_e").get(game);
    Class<?> bp = Class.forName("net.minecraft.util.BlockPos");
    Method get = method(world.getClass(), new Class<?>[] {bp}, "getBlockState", "func_180495_p"),
        put =
            method(
                world.getClass(),
                new Class<?>[] {
                  bp, Class.forName("net.minecraft.block.state.IBlockState"), int.class
                },
                "setBlockState",
                "func_180501_a");
    int x = (int) Math.floor(px), y = 170, z = (int) Math.floor(pz), cases = 0;
    Map<Object, Object> saved = new LinkedHashMap<>();
    for (int dx = -10; dx <= 10; dx++)
      for (int dy = -3; dy <= 5; dy++)
        for (int dz = -5; dz <= 40; dz++) {
          Object p = pos(x + dx, y + dy, z + dz);
          saved.put(p, get.invoke(world, p));
        }
    try {
      for (String mode : new String[] {"HYPIXEL_TELLY", "NORMAL", "TELLY"})
        for (String ray : new String[] {"NORMAL", "STRICT"}) {
          r.modules().setEnabled("scaffold", false);
          for (Object p : saved.keySet()) put.invoke(world, p, state("air"), 2);
          for (int dx = -2; dx <= 2; dx++)
            for (int dz = -3; dz <= 0; dz++)
              put.invoke(world, pos(x + dx, y, z + dz), state("stone"), 2);
          position(player, x + .5, y + 1, z - .5);
          camera(player, 0, 0);
          for (String[] name :
              new String[][] {
                {"motionX", "field_70159_w"},
                {"motionY", "field_70181_x"},
                {"motionZ", "field_70179_y"}
              }) field(player.getClass(), name).setDouble(player, 0);
          field(player.getClass(), "onGround", "field_70122_E").setBoolean(player, true);
          field(player.getClass(), "fallDistance", "field_70143_R").setFloat(player, 0);
          field(player.getClass(), "jumpTicks", "field_70773_bE").setInt(player, 0);
          field(player.getClass(), "sprintToggleTimer", "field_71156_d").setInt(player, 0);
          method(player.getClass(), new Class<?>[] {boolean.class}, "setSprinting", "func_70031_b")
              .invoke(player, false);
          Arrays.fill(items, null);
          items[0] = stack("stone");
          field(items[0].getClass(), "stackSize", "field_77994_a").setInt(items[0], 64);
          for (InputKey key : keys.keySet()) {
            keys.get(key).set(key == InputKey.FORWARD);
            nativeKeys.set(key, key == InputKey.FORWARD);
          }
          set(r, "scaffold", "mode", mode.equals("HYPIXEL_TELLY") ? "TELLY" : mode);
          set(r, "scaffold", "hypixel-telly", String.valueOf(mode.equals("HYPIXEL_TELLY")));
          set(r, "scaffold", "keep-y", "true");
          set(r, "scaffold", "sprint", "NONE");
          set(r, "scaffold", "tower", "NONE");
          set(r, "scaffold", "expand", "0");
          set(r, "scaffold", "move-fix", "true");
          set(r, "scaffold", "auto-jump", String.valueOf(!mode.equals("NORMAL")));
          set(r, "scaffold", "rotations", "RANDOMIZED");
          set(r, "scaffold", "randomized-speed-min", "7");
          set(r, "scaffold", "randomized-speed-max", "10");
          set(r, "scaffold", "min-rotation", "10");
          set(r, "scaffold", "max-rotation", "10");
          set(r, "scaffold", "search-algorithm", "NORMAL");
          set(r, "scaffold", "place-delay", "0");
          set(r, "scaffold", "ray-cast", ray);
          r.modules().setEnabled("scaffold", true);
          Thread.sleep(450); // Exercise the real post-startup Telly gate, not its 400ms exemption.
          int ticks = 0;
          for (; ticks < 160; ticks++) {
            r.controls().beginTick();
            method(player.getClass(), "onUpdate", "func_70071_h_").invoke(player);
            if (mode.equals("HYPIXEL_TELLY") && ray.equals("NORMAL") && ticks < 45) {
              Rotation a = r.controls().rotation().orElse(Rotation.server(0, 0));
              Object hit =
                  method(
                          Class.forName(
                              "perihelion.integration.minecraft189.module.gameplay.BreakerPlan"),
                          new Class<?>[] {game.getClass(), Rotation.class, double.class},
                          "trace")
                      .invoke(null, game, a, 4.5);
              Object h =
                  hit == null
                      ? "miss"
                      : method(hit.getClass(), "getBlockPos", "func_178782_a").invoke(hit);
              System.out.println(
                  "[PERIHELION_SMOKE] BRIDGE_TRACE tick="
                      + ticks
                      + " z="
                      + number(player, "posZ", "field_70161_v")
                      + " y="
                      + number(player, "posY", "field_70163_u")
                      + " ground="
                      + field(player.getClass(), "onGround", "field_70122_E").getBoolean(player)
                      + " aim="
                      + a.yaw()
                      + ","
                      + a.pitch()
                      + " hit="
                      + h);
            }
            if (number(player, "posY", "field_70163_u") < y - .5) break;
          }
          double travel = number(player, "posZ", "field_70161_v") - (z - .5);
          int blocks = 0;
          for (int dx = -10; dx <= 10; dx++)
            for (int dz = 1; dz <= 40; dz++)
              if (!get.invoke(world, pos(x + dx, y, z + dz)).equals(state("air"))) blocks++;
          String label =
              mode
                  + "/"
                  + ray
                  + " ticks="
                  + ticks
                  + " travel="
                  + travel
                  + " blocks="
                  + blocks
                  + " y="
                  + number(player, "posY", "field_70163_u");
          System.out.println("[PERIHELION_SMOKE] SCAFFOLD_BRIDGE_CASE " + label);
          if (ticks != 160 || travel < 12 || blocks < 10)
            throw new AssertionError("Continuous Scaffold bridge failed: " + label);
          cases++;
        }
      System.out.println(
          "[PERIHELION_SMOKE] SCAFFOLD_BRIDGE cases=" + cases + " ticksPerCase=160 PASS");
    } finally {
      r.modules().setEnabled("scaffold", false);
      for (Map.Entry<Object, Object> entry : saved.entrySet())
        put.invoke(world, entry.getKey(), entry.getValue(), 2);
    }
  }
}
