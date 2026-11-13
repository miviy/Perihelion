package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;
import static perihelion.smoke.RotationPipelineProbe.*;

import java.lang.reflect.*;
import java.util.*;
import perihelion.core.control.*;
import perihelion.core.event.EventOwner;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleEntry;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.MovementYawEvent;

/** Actual vanilla living-update physics across diagonal input and silent scaffold headings. */
public final class ScaffoldMovementProbe {
  private static int checks, cases;

  public static void run(ClientRuntime r) throws Exception {
    checks = cases = 0;
    Object game =
        method(Class.forName("net.minecraft.client.Minecraft"), "getMinecraft", "func_71410_x")
            .invoke(null);
    Object player = field(game.getClass(), "thePlayer", "field_71439_g").get(game);
    Object inventory = field(player.getClass(), "inventory", "field_71071_by").get(player);
    Object input = field(player.getClass(), "movementInput", "field_71158_b").get(player);
    Object capabilities = field(player.getClass(), "capabilities", "field_71075_bZ").get(player);
    Field allowFlying = field(capabilities.getClass(), "allowFlying", "field_75101_c"),
        flying = field(capabilities.getClass(), "isFlying", "field_75100_b");
    boolean oldAllowFlying = allowFlying.getBoolean(capabilities),
        oldFlying = flying.getBoolean(capabilities);
    Object[] items =
        (Object[]) field(inventory.getClass(), "mainInventory", "field_70462_a").get(inventory);
    Object[] savedItems = items.clone();
    Field screen = field(game.getClass(), "currentScreen", "field_71462_r");
    Object savedScreen = screen.get(game);
    Map<String, Boolean> enabled = new LinkedHashMap<>();
    Map<Setting<?>, Object> settings = new LinkedHashMap<>();
    for (ModuleEntry entry : r.modules().entries()) {
      if (!entry.info().requiresWorld()) continue;
      enabled.put(entry.info().id(), entry.requestedEnabled());
      r.modules().setEnabled(entry.info().id(), false);
    }
    for (Setting<?> s : r.modules().require("scaffold").module().settings().values())
      settings.put(s, s.get());
    double x = number(player, "posX", "field_70165_t"),
        y = number(player, "posY", "field_70163_u"),
        z = number(player, "posZ", "field_70161_v");
    Map<Field, Object> savedPlayer = new LinkedHashMap<>();
    for (String[] names :
        new String[][] {
          {"rotationYaw", "field_70177_z"},
          {"rotationPitch", "field_70125_A"},
          {"motionX", "field_70159_w"},
          {"motionY", "field_70181_x"},
          {"motionZ", "field_70179_y"},
          {"onGround", "field_70122_E"},
          {"isCollidedHorizontally", "field_70123_F"},
          {"isCollidedVertically", "field_70124_G"},
          {"jumpTicks", "field_70773_bE"},
          {"moveForward", "field_70701_bs"},
          {"moveStrafing", "field_70702_br"},
          {"sprintToggleTimer", "field_71156_d"},
          {"fallDistance", "field_70143_R"},
          {"lastReportedYaw", "field_175164_bL"},
          {"lastReportedPitch", "field_175165_bM"}
        }) {
      Field f = field(player.getClass(), names);
      savedPlayer.put(f, f.get(player));
    }
    boolean sprinting =
        (Boolean) method(player.getClass(), "isSprinting", "func_70051_ag").invoke(player);
    EventOwner owner =
        new EventOwner(
            "scaffold-movement-probe",
            failure -> {
              throw new AssertionError(failure);
            });
    owner.activate();
    List<AutoCloseable> resources = new ArrayList<>();
    Map<InputKey, ControlRequest<Boolean>> keys = new LinkedHashMap<>();
    for (InputKey key :
        new InputKey[] {
          InputKey.FORWARD,
          InputKey.BACK,
          InputKey.LEFT,
          InputKey.RIGHT,
          InputKey.SNEAK,
          InputKey.JUMP,
          InputKey.SPRINT
        }) keys.put(key, r.controls().scope(owner, resources::add).key(key, 1001));
    int[] jumps = {0};
    AutoCloseable observer =
        r.clientEvents()
            .subscribe(
                owner,
                MovementYawEvent.class,
                -10000,
                false,
                event -> {
                  if (event.jump()) jumps[0]++;
                });
    try (GameplayLoopback peer = new GameplayLoopback(game);
        B4ManualCombatProbe.NativeKeys nativeKeys = new B4ManualCombatProbe.NativeKeys()) {
      screen.set(game, null);
      // Repeated manual jump presses must not toggle creative flight in the fixture world.
      allowFlying.setBoolean(capabilities, false);
      flying.setBoolean(capabilities, false);
      Arrays.fill(items, null);
      items[0] = stack("stone");
      set(r, "scaffold", "sprint", "VANILLA");
      set(r, "scaffold", "tower", "NONE");
      set(r, "scaffold", "auto-jump", "true");
      set(r, "scaffold", "move-fix", "true");
      set(r, "scaffold", "require-use", "false");
      set(r, "scaffold", "pitch-condition", "false");
      set(r, "scaffold", "holding-blocks", "false");
      ScaffoldBridgeProbe.run(r, game, player, items, keys, nativeKeys, peer, x, z);
      ScaffoldMotionProbe.run(r, game, player, items, keys, nativeKeys, peer, x, z);
      set(r, "scaffold", "keep-y", "false");
      set(r, "scaffold", "move-fix", "true");
      set(r, "scaffold", "sprint", "VANILLA");
      for (String mode : new String[] {"NORMAL", "TELLY", "HYPIXEL_TELLY"}) {
        set(r, "scaffold", "mode", mode.equals("HYPIXEL_TELLY") ? "TELLY" : mode);
        set(r, "scaffold", "hypixel-telly", String.valueOf(mode.equals("HYPIXEL_TELLY")));
        for (float camera : new float[] {0, 45, 179, -179})
          for (float offset : new float[] {0, 45, 90, 180, -135})
            for (int[] direction :
                new int[][] {
                  {1, 0}, {1, 1}, {1, -1}, {0, 1}, {0, -1}, {-1, 0}, {-1, 1}, {-1, -1}
                }) {
              double[][] motion = new double[2][];
              int[] jumpCounts = new int[2];
              String label =
                  mode
                      + " camera="
                      + camera
                      + " offset="
                      + offset
                      + " input="
                      + Arrays.toString(direction);
              double[] vanilla = null;
              for (int manual = offset == 0 && mode.equals("NORMAL") ? -1 : 0;
                  manual < 2;
                  manual++) {
                r.modules().setEnabled("scaffold", false);
                HookBridge.gameControls().apply();
                position(player, x, 200, z);
                camera(player, camera + offset, 0);
                // Start the vanilla comparison from the same reported heading. The
                // separate walking probe exercises real +/-360 activation transitions.
                field(player.getClass(), "lastReportedYaw", "field_175164_bL")
                    .setFloat(player, camera + offset);
                field(player.getClass(), "lastReportedPitch", "field_175165_bM")
                    .setFloat(player, 0);
                for (String[] names :
                    new String[][] {
                      {"motionX", "field_70159_w"},
                      {"motionY", "field_70181_x"},
                      {"motionZ", "field_70179_y"}
                    }) field(player.getClass(), names).setDouble(player, 0);
                field(player.getClass(), "onGround", "field_70122_E").setBoolean(player, true);
                field(player.getClass(), "isCollidedHorizontally", "field_70123_F")
                    .setBoolean(player, false);
                field(player.getClass(), "isCollidedVertically", "field_70124_G")
                    .setBoolean(player, true);
                field(player.getClass(), "jumpTicks", "field_70773_bE").setInt(player, 0);
                field(player.getClass(), "sprintToggleTimer", "field_71156_d").setInt(player, 0);
                method(
                        player.getClass(),
                        new Class<?>[] {boolean.class},
                        "setSprinting",
                        "func_70031_b")
                    .invoke(player, false);
                field(input.getClass(), "moveForward", "field_78900_b")
                    .setFloat(input, direction[0]);
                field(input.getClass(), "moveStrafe", "field_78902_a")
                    .setFloat(input, direction[1]);
                field(input.getClass(), "sneak", "field_78899_d").setBoolean(input, false);
                for (InputKey key : keys.keySet()) {
                  boolean down =
                      key == InputKey.FORWARD && direction[0] > 0
                          || key == InputKey.BACK && direction[0] < 0
                          || key == InputKey.LEFT && direction[1] > 0
                          || key == InputKey.RIGHT && direction[1] < 0
                          || key == InputKey.JUMP && manual != 0
                          || key == InputKey.SPRINT && manual < 0;
                  keys.get(key).set(down);
                  nativeKeys.set(key, down);
                }
                r.modules().setEnabled("scaffold", manual >= 0);
                camera(player, camera, 0);
                jumps[0] = 0;
                method(player.getClass(), "onLivingUpdate", "func_70636_d").invoke(player);
                double[] velocity =
                    new double[] {
                      number(player, "motionX", "field_70159_w"),
                      number(player, "motionY", "field_70181_x"),
                      number(player, "motionZ", "field_70179_y")
                    };
                if (manual < 0) vanilla = velocity;
                else {
                  motion[manual] = velocity;
                  jumpCounts[manual] = jumps[0];
                }
                check(velocity[1] > 0, label + " leaves the ground");
                check(jumps[0] <= 1, label + " applies at most one sprint impulse");
                check(
                    Double.isFinite(velocity[0]) && Double.isFinite(velocity[2]),
                    label + " finite horizontal motion");
              }
              for (int axis = 0; axis < 3; axis++)
                check(
                    Math.abs(motion[0][axis] - motion[1][axis]) < 1e-6,
                    label + " automatic/manual axis=" + axis + " " + Arrays.deepToString(motion));
              check(jumpCounts[0] == jumpCounts[1], label + " matching sprint impulses");
              if (vanilla != null)
                for (int axis = 0; axis < 3; axis++)
                  check(
                      Math.abs(vanilla[axis] - motion[1][axis]) < 1e-6,
                      label + " preserves vanilla diagonal jumping axis=" + axis);
              cases++;
            }
      }
      placements(r, game, player, items, keys, nativeKeys, x, z);
      check(peer.failure.get() == null, "loopback remains healthy");
      System.out.println(
          "[PERIHELION_SMOKE] SCAFFOLD_MOVEMENT cases=" + cases + " checks=" + checks + " PASS");
    } finally {
      r.modules().setEnabled("scaffold", false);
      observer.close();
      for (AutoCloseable resource : resources) resource.close();
      owner.close();
      System.arraycopy(savedItems, 0, items, 0, items.length);
      for (Map.Entry<Setting<?>, Object> e : settings.entrySet())
        restore(r, e.getKey(), e.getValue());
      position(player, x, y, z);
      allowFlying.setBoolean(capabilities, oldAllowFlying);
      flying.setBoolean(capabilities, oldFlying);
      for (Map.Entry<Field, Object> e : savedPlayer.entrySet())
        e.getKey().set(player, e.getValue());
      method(player.getClass(), new Class<?>[] {boolean.class}, "setSprinting", "func_70031_b")
          .invoke(player, sprinting);
      screen.set(game, savedScreen);
      for (Map.Entry<String, Boolean> e : enabled.entrySet())
        if (e.getValue()) r.modules().setEnabled(e.getKey(), true);
      HookBridge.gameControls().apply();
    }
  }

  private static void placements(
      ClientRuntime r,
      Object game,
      Object player,
      Object[] items,
      Map<InputKey, ControlRequest<Boolean>> keys,
      B4ManualCombatProbe.NativeKeys nativeKeys,
      double px,
      double pz)
      throws Exception {
    Object world = field(game.getClass(), "theWorld", "field_71441_e").get(game);
    Class<?> blockPos = Class.forName("net.minecraft.util.BlockPos");
    Method get =
        method(world.getClass(), new Class<?>[] {blockPos}, "getBlockState", "func_180495_p");
    Method put =
        method(
            world.getClass(),
            new Class<?>[] {
              blockPos, Class.forName("net.minecraft.block.state.IBlockState"), int.class
            },
            "setBlockState",
            "func_180501_a");
    int x = (int) Math.floor(px), y = 180, z = (int) Math.floor(pz), cornerCases = 0;
    Map<Object, Object> saved = new LinkedHashMap<>();
    for (int dx = -2; dx <= 2; dx++)
      for (int dy = 0; dy <= 3; dy++)
        for (int dz = -2; dz <= 2; dz++) {
          Object p = pos(x + dx, y + dy, z + dz);
          saved.put(p, get.invoke(world, p));
        }
    try {
      set(r, "scaffold", "keep-y", "true");
      set(r, "scaffold", "expand", "0");
      set(r, "scaffold", "search-algorithm", "SECONDARY");
      set(r, "scaffold", "rotations", "NORMAL");
      set(r, "scaffold", "min-rotation", "10");
      set(r, "scaffold", "max-rotation", "10");
      for (InputKey key : keys.keySet()) {
        keys.get(key).set(key == InputKey.FORWARD);
        nativeKeys.set(key, key == InputKey.FORWARD);
      }
      for (String mode : new String[] {"NORMAL", "TELLY", "HYPIXEL_TELLY"})
        for (String ray : new String[] {"NORMAL", "STRICT"})
          for (int yaw : new int[] {45, 135, 225, 315}) {
            r.modules().setEnabled("scaffold", false);
            HookBridge.gameControls().apply();
            for (Object p : saved.keySet()) put.invoke(world, p, state("air"), 2);
            put.invoke(world, pos(x, y, z), state("stone"), 2);
            int sx = Math.sin(Math.toRadians(yaw)) > 0 ? -1 : 1,
                sz = Math.cos(Math.toRadians(yaw)) > 0 ? 1 : -1;
            Object corner = pos(x + sx, y, z + sz);
            position(player, x + .5 + sx * .65, y + 1, z + .5 + sz * .65);
            camera(player, yaw, 0);
            field(player.getClass(), "onGround", "field_70122_E").setBoolean(player, false);
            Object input = field(player.getClass(), "movementInput", "field_71158_b").get(player);
            field(input.getClass(), "moveForward", "field_78900_b").setFloat(input, 1);
            field(input.getClass(), "moveStrafe", "field_78902_a").setFloat(input, 0);
            Arrays.fill(items, null);
            items[0] = stack("stone");
            field(items[0].getClass(), "stackSize", "field_77994_a").setInt(items[0], 64);
            set(r, "scaffold", "mode", mode.equals("HYPIXEL_TELLY") ? "TELLY" : mode);
            set(r, "scaffold", "hypixel-telly", String.valueOf(mode.equals("HYPIXEL_TELLY")));
            set(r, "scaffold", "ray-cast", ray);
            perihelion.integration.minecraft189.control.GroundTickHistory history =
                ((perihelion.integration.minecraft189.control.PlayerGroundAccess) player)
                    .perihelion$groundTicks();
            for (String name : new String[] {"air", "ground", "beforeAir", "beforeGround"})
              field(history.getClass(), name).setInt(history, 0);
            r.modules().setEnabled("scaffold", true);
            for (int turn = 0;
                turn < 24 && state("air").equals(get.invoke(world, corner));
                turn++) {
              history.advance(false);
              HookBridge.gameControls().beforeInput();
              motion(player);
            }
            String label = mode + "/" + ray + " yaw=" + yaw;
            check(
                !state("air").equals(get.invoke(world, corner)),
                label + " fills the diagonal corner");
            check(
                !state("air").equals(get.invoke(world, pos(x + sx, y, z)))
                    || !state("air").equals(get.invoke(world, pos(x, y, z + sz))),
                label + " builds an adjacent support first");
            cornerCases++;
          }
      System.out.println("[PERIHELION_SMOKE] SCAFFOLD_CORNERS cases=" + cornerCases + " PASS");
    } finally {
      r.modules().setEnabled("scaffold", false);
      HookBridge.gameControls().apply();
      for (Map.Entry<Object, Object> e : saved.entrySet())
        put.invoke(world, e.getKey(), e.getValue(), 2);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> void restore(ClientRuntime r, Setting<?> s, Object value) {
    r.modules().setSetting("scaffold", (Setting<T>) s, (T) value);
  }

  private static void check(boolean value, String label) {
    if (!value) throw new AssertionError("Scaffold movement: " + label);
    checks++;
  }
}
