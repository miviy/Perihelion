package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;
import static perihelion.smoke.RotationPipelineProbe.*;

import io.netty.channel.Channel;
import java.lang.reflect.*;
import java.util.*;
import perihelion.core.control.Rotation;
import perihelion.core.event.EventOwner;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.*;

/** Non-cube support, moving inputs, interruption, sensitivity/FPS and search-cost fixtures. */
public final class ScaffoldTerrainProbe {
  private static final String GAMEPLAY = "perihelion.integration.minecraft189.module.gameplay.";
  private static int checks;
  private static int referenceMatches;

  static void run(
      ClientRuntime r,
      Object game,
      Object player,
      Object world,
      Object[] items,
      Method getState,
      Method putState,
      Channel channel,
      List<Object> packets,
      int x,
      int y,
      int z)
      throws Exception {
    checks = 0;
    referenceMatches = 0;
    Object settings = field(game.getClass(), "gameSettings", "field_71474_y").get(game);
    Object input = field(player.getClass(), "movementInput", "field_71158_b").get(player);
    Object inventory = field(player.getClass(), "inventory", "field_71071_by").get(player);
    Field selected = field(inventory.getClass(), "currentItem", "field_70461_c"),
        forward = field(input.getClass(), "moveForward", "field_78900_b"),
        strafe = field(input.getClass(), "moveStrafe", "field_78902_a"),
        ground = field(player.getClass(), "onGround", "field_70122_E"),
        sensitivity = field(settings.getClass(), "mouseSensitivity", "field_74341_c"),
        fps = field(game.getClass(), "debugFPS", "field_71470_ab");
    int oldSelected = selected.getInt(inventory), oldFps = fps.getInt(null);
    float oldForward = forward.getFloat(input),
        oldStrafe = strafe.getFloat(input),
        oldSensitivity = sensitivity.getFloat(settings);
    boolean oldGround = ground.getBoolean(player);
    Map<Object, Boolean> keys = new LinkedHashMap<>();
    for (String[] names :
        new String[][] {
          {"keyBindForward", "field_74351_w"},
          {"keyBindRight", "field_74366_z"},
          {"keyBindJump", "field_74314_A"},
          {"keyBindUseItem", "field_74313_G"}
        }) {
      Object key = field(settings.getClass(), names).get(settings);
      keys.put(key, field(key.getClass(), "pressed", "field_74513_e").getBoolean(key));
      field(key.getClass(), "pressed", "field_74513_e").setBoolean(key, false);
    }
    Object support = pos(x, y, z), target = pos(x, y, z + 1);
    try {
      set(r, "scaffold", "keep-y", "false");
      set(r, "scaffold", "auto-jump", "false");
      set(r, "scaffold", "min-rotation", "10");
      set(r, "scaffold", "max-rotation", "10");
      set(r, "scaffold", "hypixel-telly", "true");
      position(player, x + .5, y + 1, z + 1.15);
      camera(player, 179, -30);
      ground.setBoolean(player, false);
      putState.invoke(world, support, state("stone"), 2);
      putState.invoke(world, target, state("air"), 2);
      profileSearch(game, player, target);
      String[] materials = {
        "stone", "stone_slab", "stone_slab", "oak_stairs", "oak_stairs", "fence"
      };
      int[] metadata = {0, 0, 8, 0, 4, 0};
      int caseIndex = 0;
      for (int shape = 0; shape < materials.length; shape++)
        for (String mode : new String[] {"NORMAL", "HYPIXEL", "TELLY"})
          for (String slot : new String[] {"CLIENT", "SERVER"})
            for (String ray : new String[] {"NORMAL", "STRICT"}) {
              r.modules().setEnabled("scaffold", false);
              HookBridge.gameControls().apply();
              // Each case gets its own terrain; previous camera/placement fixtures can leave
              // neighboring supports that change the closest valid ray.
              for (int dx = -3; dx <= 3; dx++)
                for (int dy = 0; dy <= 5; dy++)
                  for (int dz = -3; dz <= 3; dz++)
                    putState.invoke(world, pos(x + dx, y + dy, z + dz), state("air"), 2);
              putState.invoke(world, target, state("air"), 2);
              putState.invoke(world, support, stateMetadata(materials[shape], metadata[shape]), 2);
              Arrays.fill(items, null);
              items[0] = stack("stone");
              field(items[0].getClass(), "stackSize", "field_77994_a").setInt(items[0], 64);
              selected.setInt(inventory, 4);
              boolean diagonal = (++caseIndex & 1) == 0;
              sensitivity.setFloat(settings, diagonal ? .9f : .1f);
              fps.setInt(null, diagonal ? 10 : 144);
              camera(player, diagonal ? -179 : 179, -30);
              position(player, x + .5, y + 1, z + 1.15);
              ground.setBoolean(player, false);
              forward.setFloat(input, 1);
              strafe.setFloat(input, diagonal ? -1 : 0);
              field(
                      field(settings.getClass(), "keyBindForward", "field_74351_w")
                          .get(settings)
                          .getClass(),
                      "pressed",
                      "field_74513_e")
                  .setBoolean(
                      field(settings.getClass(), "keyBindForward", "field_74351_w").get(settings),
                      true);
              Object right =
                  field(settings.getClass(), "keyBindRight", "field_74366_z").get(settings);
              field(right.getClass(), "pressed", "field_74513_e").setBoolean(right, diagonal);
              set(r, "scaffold", "mode", mode);
              set(r, "scaffold", "slot", slot);
              set(r, "scaffold", "ray-cast", ray);
              set(r, "scaffold", "search-algorithm", "SECONDARY");
              compareSearchOrder(game, target);
              r.modules().setEnabled("scaffold", true);
              flush(channel);
              packets.clear();
              forceLook(player);
              for (int turn = 0;
                  turn < 16 && state("air").equals(getState.invoke(world, target));
                  turn++) {
                HookBridge.gameControls().beforeInput();
                position(
                    player, x + .5 + turn * (diagonal ? .01 : 0), y + 1, z + 1.15 + turn * .015);
                motion(player);
                flush(channel);
              }
              String label =
                  materials[shape] + "/" + metadata[shape] + "/" + mode + "/" + slot + "/" + ray;
              if (state("air").equals(getState.invoke(world, target))) {
                Class<?> planType = Class.forName(GAMEPLAY + "Placement");
                Object plan =
                    method(
                            planType,
                            new Class<?>[] {
                              game.getClass(),
                              Class.forName("net.minecraft.util.BlockPos"),
                              double.class,
                              boolean.class
                            },
                            "at")
                        .invoke(null, game, target, 4.5, false);
                Rotation aim = r.controls().rotation().orElse(Rotation.server(0, 0));
                Object matches =
                    plan == null
                        ? null
                        : method(
                                planType,
                                new Class<?>[] {
                                  game.getClass(), Rotation.class, double.class, boolean.class
                                },
                                "ray")
                            .invoke(plan, game, aim, 4.5, false);
                System.out.println(
                    "PERIHELION_SMOKE TERRAIN_DIAGNOSTIC label="
                        + label
                        + " aim="
                        + aim.yaw()
                        + ","
                        + aim.pitch()
                        + " planRay="
                        + matches
                        + " face="
                        + (plan == null ? null : field(planType, "face").get(plan))
                        + " packets="
                        + packets.stream()
                            .map(p -> p.getClass().getSimpleName())
                            .collect(java.util.stream.Collectors.toList()));
              }
              check(
                  !state("air").equals(getState.invoke(world, target)),
                  "moving Scaffold places from " + label);
              orderedPrePlacement(packets, label);
              if (materials[shape].equals("fence")) {
                int start = -1, end = -1, place = -1;
                for (int i = 0; i < packets.size(); i++) {
                  Object packet = packets.get(i);
                  if (packet.getClass().getSimpleName().equals("C0BPacketEntityAction")) {
                    String action =
                        method(packet.getClass(), "getAction", "func_180764_b")
                            .invoke(packet)
                            .toString();
                    if (action.equals("START_SNEAKING")) start = i;
                    if (action.equals("STOP_SNEAKING")) end = i;
                  }
                  if (packet.getClass().getSimpleName().equals("C08PacketPlayerBlockPlacement"))
                    place = i;
                }
                check(
                    start >= 0 && place > start && end > place,
                    "fence placement brackets its click with sneak packets");
                check(
                    !(Boolean)
                        method(player.getClass(), "isSneaking", "func_70093_af").invoke(player),
                    "temporary fence sneak restores actual input");
              }
              r.modules().setEnabled("scaffold", false);
              HookBridge.gameControls().apply();
              check(
                  selected.getInt(inventory) == 4 && !r.controls().hotbar().isPresent(),
                  "slot and claims restore for " + label);
            }
      interruptions(r, game, player, world, items, putState, channel, packets, x, y, z);
      for (int i = 0; i < 24; i++) {
        r.modules().setEnabled("scaffold", true);
        HookBridge.gameControls().beforeInput();
        r.modules().setEnabled("scaffold", false);
        HookBridge.gameControls().apply();
        check(
            !r.controls().rotation().isPresent() && !r.controls().hotbar().isPresent(),
            "rapid toggle retires all Scaffold controls " + i);
      }
    } finally {
      r.modules().setEnabled("scaffold", false);
      HookBridge.gameControls().apply();
      selected.setInt(inventory, oldSelected);
      forward.setFloat(input, oldForward);
      strafe.setFloat(input, oldStrafe);
      sensitivity.setFloat(settings, oldSensitivity);
      fps.setInt(null, oldFps);
      ground.setBoolean(player, oldGround);
      for (Map.Entry<Object, Boolean> e : keys.entrySet())
        field(e.getKey().getClass(), "pressed", "field_74513_e")
            .setBoolean(e.getKey(), e.getValue());
      putState.invoke(world, support, state("stone"), 2);
      putState.invoke(world, target, state("air"), 2);
    }
    check(referenceMatches >= 12, "search reference covers reachable partial-block geometry");
    System.out.println(
        "PERIHELION_SMOKE SCAFFOLD_TERRAIN checks="
            + checks
            + " cases=72 referenceMatches="
            + referenceMatches
            + " PASS");
  }

  private static void interruptions(
      ClientRuntime r,
      Object game,
      Object player,
      Object world,
      Object[] items,
      Method putState,
      Channel channel,
      List<Object> packets,
      int x,
      int y,
      int z)
      throws Exception {
    Field screen = field(game.getClass(), "currentScreen", "field_71462_r");
    Object originalScreen = screen.get(game);
    for (String change : new String[] {"support", "inventory", "screen"}) {
      putState.invoke(world, pos(x, y, z), state("stone"), 2);
      putState.invoke(world, pos(x, y, z + 1), state("air"), 2);
      Arrays.fill(items, null);
      items[0] = stack("stone");
      field(items[0].getClass(), "stackSize", "field_77994_a").setInt(items[0], 64);
      position(player, x + .5, y + 1, z + 1.15);
      camera(player, 180, 82);
      set(r, "scaffold", "mode", "NORMAL");
      set(r, "scaffold", "ray-cast", "STRICT");
      EventOwner owner =
          new EventOwner(
              "scaffold-stale-" + change,
              failure -> {
                throw new AssertionError(failure);
              });
      owner.activate();
      AutoCloseable callback =
          r.clientEvents()
              .subscribe(
                  owner,
                  PlayerControlEvent.class,
                  90,
                  false,
                  event -> {
                    if (event != PlayerControlEvent.BEFORE_MOTION) return;
                    try {
                      if (change.equals("support"))
                        putState.invoke(world, pos(x, y, z), state("air"), 2);
                      else if (change.equals("inventory")) Arrays.fill(items, null);
                      else
                        screen.set(
                            game, Class.forName("net.minecraft.client.gui.GuiChat").newInstance());
                    } catch (Exception failure) {
                      throw new RuntimeException(failure);
                    }
                  });
      try {
        r.modules().setEnabled("scaffold", true);
        HookBridge.gameControls().beforeInput();
        flush(channel);
        packets.clear();
        forceLook(player);
        motion(player);
        flush(channel);
        HookBridge.gameControls().beforeInput();
        motion(player);
        flush(channel);
        check(
            packets.stream()
                .noneMatch(
                    p -> p.getClass().getSimpleName().equals("C08PacketPlayerBlockPlacement")),
            "Scaffold revalidates changed " + change + " after preparation");
      } finally {
        callback.close();
        owner.close();
        r.modules().setEnabled("scaffold", false);
        HookBridge.gameControls().apply();
        screen.set(game, originalScreen);
      }
    }
  }

  private static void profileSearch(Object game, Object player, Object target) throws Exception {
    Class<?> planType = Class.forName(GAMEPLAY + "Placement");
    Object plan =
        method(
                planType,
                new Class<?>[] {
                  game.getClass(),
                  Class.forName("net.minecraft.util.BlockPos"),
                  double.class,
                  boolean.class
                },
                "at")
            .invoke(null, game, target, 4.5, false);
    check(plan != null, "search profile has a reachable support");
    Class<?> facing = Class.forName("net.minecraft.util.EnumFacing");
    field(planType, "face").set(plan, Enum.valueOf((Class) facing, "NORTH"));
    Method search =
        method(
            Class.forName(GAMEPLAY + "ScaffoldRotations"),
            new Class<?>[] {game.getClass(), planType, Rotation.class, boolean.class},
            "watchdog");
    Rotation previous = Rotation.server(179, -30);
    for (int i = 0; i < 8; i++) search.invoke(null, game, plan, previous, false);
    long[] samples = new long[40];
    for (int i = 0; i < samples.length; i++) {
      long start = System.nanoTime();
      search.invoke(null, game, plan, previous, false);
      samples[i] = System.nanoTime() - start;
    }
    Arrays.sort(samples);
    System.out.println(
        "PERIHELION_SMOKE PERF watchdogImpossibleFace samples=40 medianMs="
            + samples[20] / 1e6
            + " p95Ms="
            + samples[37] / 1e6
            + " maxMs="
            + samples[39] / 1e6);
  }

  private static void compareSearchOrder(Object game, Object target) throws Exception {
    Class<?> planType = Class.forName(GAMEPLAY + "Placement");
    Object plan =
        method(
                planType,
                new Class<?>[] {
                  game.getClass(),
                  Class.forName("net.minecraft.util.BlockPos"),
                  double.class,
                  boolean.class
                },
                "at")
            .invoke(null, game, target, 4.5, false);
    Method ray =
        method(
            planType,
            new Class<?>[] {game.getClass(), Rotation.class, double.class, boolean.class},
            "ray");
    float base =
        ((Number)
                    method(
                            Class.forName(GAMEPLAY + "RotationDynamics"),
                            new Class<?>[] {game.getClass()},
                            "movementYaw")
                        .invoke(null, game))
                .floatValue()
            + 90;
    Rotation reference = null;
    // Independent original 111-by-41 search, including all clamped duplicate pitches.
    outer:
    for (int i = 0; i <= 110; i++) {
      int magnitude = i == 0 ? 0 : (i + 1) / 2;
      float offset = magnitude <= 20 ? magnitude : 20 + (magnitude - 20) * 2;
      if ((i & 1) == 1) offset = -offset;
      for (int j = 0; j <= 40; j++) {
        int delta = j == 0 ? 0 : (j + 1) / 2 * ((j & 1) == 1 ? -1 : 1);
        Rotation candidate =
            Rotation.server(base + offset, Math.max(-90, Math.min(90, 90 + delta)));
        if ((Boolean) ray.invoke(plan, game, candidate, 4.5, true)) {
          reference = candidate;
          break outer;
        }
      }
    }
    if (reference == null)
      return; // The original randomized fallback is not a deterministic comparison.
    Rotation actual =
        (Rotation)
            method(
                    Class.forName(GAMEPLAY + "ScaffoldRotations"),
                    new Class<?>[] {game.getClass(), planType, Rotation.class, boolean.class},
                    "watchdog")
                .invoke(null, game, plan, Rotation.server(179, -30), false);
    check(
        actual.yaw() == reference.yaw() && actual.pitch() == reference.pitch(),
        "optimized search preserves its first matching ordered ray");
    referenceMatches++;
  }

  private static Object stateMetadata(String material, int metadata) throws Exception {
    Object block =
        method(state(material).getClass(), "getBlock", "func_177230_c").invoke(state(material));
    return method(block.getClass(), new Class<?>[] {int.class}, "getStateFromMeta", "func_176203_a")
        .invoke(block, metadata);
  }

  private static void check(boolean ok, String message) {
    checks++;
    if (!ok) throw new AssertionError(message);
  }
}
