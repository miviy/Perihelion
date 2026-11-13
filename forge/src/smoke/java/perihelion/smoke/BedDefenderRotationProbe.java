package perihelion.smoke;

import static perihelion.smoke.B4ManualCombatProbe.*;
import static perihelion.smoke.GameplayHookProbe.*;
import static perihelion.smoke.RotationPipelineProbe.*;

import java.lang.reflect.*;
import java.util.*;
import perihelion.core.control.Rotation;
import perihelion.core.event.EventOwner;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleEntry;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.PacketSendEvent;

/** Real eight-block shells, submitted-look ordering and planner regressions in a PLAY loopback. */
public final class BedDefenderRotationProbe {
  private static int checks, shells, firstPasses;

  public static void run(ClientRuntime r) throws Exception {
    checks = shells = firstPasses = 0;
    Object game =
        method(Class.forName("net.minecraft.client.Minecraft"), "getMinecraft", "func_71410_x")
            .invoke(null);
    Object p = field(game.getClass(), "thePlayer", "field_71439_g").get(game);
    Object world = field(game.getClass(), "theWorld", "field_71441_e").get(game);
    Object inventory = field(p.getClass(), "inventory", "field_71071_by").get(p);
    Object[]
        items =
            (Object[]) field(inventory.getClass(), "mainInventory", "field_70462_a").get(inventory),
        oldItems = items.clone();
    Object options = field(game.getClass(), "gameSettings", "field_71474_y").get(game);
    Field sensitivity = field(options.getClass(), "mouseSensitivity", "field_74341_c");
    Field fps = field(game.getClass(), "debugFPS", "field_71470_ab");
    int oldFps = fps.getInt(null);
    float oldSensitivity = sensitivity.getFloat(options);
    Field screen = field(game.getClass(), "currentScreen", "field_71462_r");
    Object oldScreen = screen.get(game);
    double px = number(p, "posX", "field_70165_t"),
        py = number(p, "posY", "field_70163_u"),
        pz = number(p, "posZ", "field_70161_v");
    Map<String, Boolean> enabled = new LinkedHashMap<>();
    for (ModuleEntry entry : r.modules().entries())
      if (entry.info().requiresWorld()) {
        enabled.put(entry.info().id(), entry.requestedEnabled());
        r.modules().setEnabled(entry.info().id(), false);
      }
    Map<Setting<?>, Object> settings = new LinkedHashMap<>();
    for (Setting<?> s : r.modules().require("beddefender").module().settings().values())
      settings.put(s, s.get());
    Class<?> bp = Class.forName("net.minecraft.util.BlockPos"),
        bs = Class.forName("net.minecraft.block.state.IBlockState"),
        facing = Class.forName("net.minecraft.util.EnumFacing");
    Method get = method(world.getClass(), new Class<?>[] {bp}, "getBlockState", "func_180495_p");
    Method put =
        method(
            world.getClass(), new Class<?>[] {bp, bs, int.class}, "setBlockState", "func_180501_a");
    Class<?> helper =
        Class.forName("perihelion.integration.minecraft189.module.gameplay.BedDefensePlan");
    Method shell = method(helper, new Class<?>[] {bp, facing}, "shell");
    int x = (int) Math.floor(px), y = 200, z = (int) Math.floor(pz);
    Map<Object, Object> terrain = new LinkedHashMap<>();
    for (int dx = -5; dx <= 5; dx++)
      for (int dy = -1; dy <= 3; dy++)
        for (int dz = -5; dz <= 5; dz++) {
          Object cell = pos(x + dx, y + dy, z + dz);
          terrain.put(cell, get.invoke(world, cell));
        }
    EventOwner owner =
        new EventOwner(
            "beddefender-rotations",
            failure -> {
              throw new AssertionError(failure);
            });
    owner.activate();
    try (GameplayLoopback peer = new GameplayLoopback(game);
        NativeKeys keys = new NativeKeys()) {
      screen.set(game, null);
      set(r, "beddefender", "speed", "30");
      set(r, "beddefender", "place-delay", "0");
      Map<String, Rotation> firstAngles = new LinkedHashMap<>();
      for (int meta = 0; meta < 4; meta++)
        for (float sense : new float[] {.1f, .5f, .9f})
          for (int frameRate : new int[] {30, 240}) {
            r.modules().setEnabled("beddefender", false);
            HookBridge.gameControls().apply();
            for (Object cell : terrain.keySet()) put.invoke(world, cell, state("air"), 2);
            for (int dx = -5; dx <= 5; dx++)
              for (int dz = -5; dz <= 5; dz++)
                put.invoke(world, pos(x + dx, y - 1, z + dz), state("stone"), 2);
            Object direction =
                method(facing, new Class<?>[] {int.class}, "getHorizontal", "func_176731_b")
                    .invoke(null, meta);
            int
                dx =
                    ((Number) method(facing, "getFrontOffsetX", "func_82601_c").invoke(direction))
                        .intValue(),
                dz =
                    ((Number) method(facing, "getFrontOffsetZ", "func_82599_e").invoke(direction))
                        .intValue();
            Object foot = pos(x, y, z);
            put.invoke(world, foot, meta("bed", meta), 2);
            put.invoke(world, pos(x + dx, y, z + dz), meta("bed", meta | 8), 2);
            Object[] cells = (Object[]) shell.invoke(null, foot, direction);
            Arrays.fill(items, null);
            items[0] = stack("wool");
            field(items[0].getClass(), "stackSize", "field_77994_a").setInt(items[0], 64);
            sensitivity.setFloat(options, sense);
            fps.setInt(null, frameRate);
            double cx = x + .5 + dx * .5, cz = z + .5 + dz * .5;
            resetPlayer(p, cx + 3, y, cz);
            camera(p, frameRate == 30 ? 179 : 539, -30);
            field(p.getClass(), "lastReportedYaw", "field_175164_bL").setFloat(p, 179);
            field(p.getClass(), "lastReportedPitch", "field_175165_bM").setFloat(p, -30);
            peer.received.clear();
            r.modules().setEnabled("beddefender", true);
            living(p);
            wireBoundary(peer);
            check(
                count(peer.received, "C08PacketPlayerBlockPlacement") == 0,
                "no placement uses a new unsent aim");
            Rotation first = r.controls().rotation().get();
            String label = meta + "/" + sense;
            if (frameRate == 30) firstAngles.put(label, first);
            else
              check(
                  firstAngles.get(label).yaw() == first.yaw()
                      && firstAngles.get(label).pitch() == first.pitch(),
                  "aim is independent of FPS " + label);
            forceLook(p);
            motion(p);
            wireBoundary(peer);
            check(
                count(peer.received, "C08PacketPlayerBlockPlacement") == 1,
                "first submitted walking pass places immediately " + label);
            ordered(
                peer.received,
                "C08PacketPlayerBlockPlacement",
                first,
                "Bed Defender first placement " + label);
            firstPasses++;
            for (int orbit = 0; orbit < 4 && filled(world, get, cells) < 8; orbit++) {
              double angle = orbit * Math.PI / 2;
              resetPlayer(p, cx + Math.cos(angle) * 3, y, cz + Math.sin(angle) * 3);
              for (int tick = 0; tick < 9 && filled(world, get, cells) < 8; tick++) {
                living(p);
                forceLook(p);
                motion(p);
              }
            }
            // Once side walls surround the bed, stand on its left wool block for a clear roof ray.
            if (filled(world, get, cells) < 8) {
              resetPlayer(p, x + .5 + dz, y + 1, z + .5 - dx);
              for (int tick = 0; tick < 9 && filled(world, get, cells) < 8; tick++) {
                living(p);
                forceLook(p);
                motion(p);
              }
            }
            wireBoundary(peer);
            if (filled(world, get, cells) < 8) {
              Object access = (perihelion.integration.minecraft189.control.PlayerRotationAccess) p;
              Rotation reported =
                  Rotation.serverUnwrapped(
                      ((perihelion.integration.minecraft189.control.PlayerRotationAccess) access)
                          .perihelion$reportedYaw(),
                      ((perihelion.integration.minecraft189.control.PlayerRotationAccess) access)
                          .perihelion$reportedPitch());
              Object roofPlan =
                  method(
                          helper,
                          new Class<?>[] {
                            game.getClass(), bp, boolean.class, double.class, Rotation.class
                          },
                          "at")
                      .invoke(null, game, cells[6], true, 5d, reported);
              System.out.println(
                  "BED_DIAGNOSTIC clicks="
                      + count(peer.received, "C08PacketPlayerBlockPlacement")
                      + " sneak="
                      + method(p.getClass(), "isSneaking", "func_70093_af").invoke(p)
                      + " position="
                      + number(p, "posX", "field_70165_t")
                      + ","
                      + number(p, "posY", "field_70163_u")
                      + ","
                      + number(p, "posZ", "field_70161_v")
                      + " aim="
                      + r.controls().rotation()
                      + " plan="
                      + (roofPlan == null
                          ? "null"
                          : field(roofPlan.getClass(), "hit").get(roofPlan))
                      + " reported="
                      + reported.yaw()
                      + ","
                      + reported.pitch());
            }
            check(
                filled(world, get, cells) == 8,
                "all eight shell blocks finish, including both roofs "
                    + label
                    + " fps="
                    + frameRate
                    + " filled="
                    + filled(world, get, cells));
            boolean serverSneaking = false;
            int roofClicks = 0;
            for (Object packet : peer.received) {
              if (packet.getClass().getSimpleName().equals("C0BPacketEntityAction")) {
                String action =
                    method(packet.getClass(), "getAction", "func_180764_b")
                        .invoke(packet)
                        .toString();
                if (action.equals("START_SNEAKING")) serverSneaking = true;
                if (action.equals("STOP_SNEAKING")) serverSneaking = false;
              } else if (packet
                  .getClass()
                  .getSimpleName()
                  .equals("C08PacketPlayerBlockPlacement")) {
                Object support =
                    method(packet.getClass(), "getPosition", "func_179724_a").invoke(packet);
                if (support.equals(foot) || support.equals(pos(x + dx, y, z + dz))) {
                  check(serverSneaking, "bed-support click has a preceding sneak state");
                  roofClicks++;
                }
              }
            }
            check(roofClicks >= 1, "roof completion exercises a bed-support click");
            living(p);
            check(
                !r.modules().require("beddefender").requestedEnabled(),
                "completion disables Bed Defender");
            check(
                !r.controls().rotation().isPresent() && !r.controls().hotbar().isPresent(),
                "completion releases aim and wool");
            shells++;
          }
      // Rebuild one bed; its first side has no usable support, while other sides do.
      r.modules().setEnabled("beddefender", false);
      HookBridge.gameControls().apply();
      for (Object cell : terrain.keySet()) put.invoke(world, cell, state("air"), 2);
      for (int dx = -2; dx <= 2; dx++)
        for (int dz = -2; dz <= 3; dz++)
          put.invoke(world, pos(x + dx, y - 1, z + dz), state("stone"), 2);
      put.invoke(world, pos(x, y, z), meta("bed", 0), 2);
      put.invoke(world, pos(x, y, z + 1), meta("bed", 8), 2);
      put.invoke(world, pos(x + 1, y - 1, z), state("air"), 2);
      resetPlayer(p, x + 3.5, y, z + .5);
      camera(p, 179, 0);
      Arrays.fill(items, null);
      items[0] = stack("wool");
      r.modules().setEnabled("beddefender", true);
      peer.received.clear();
      living(p);
      forceLook(p);
      motion(p);
      wireBoundary(peer);
      check(
          count(peer.received, "C08PacketPlayerBlockPlacement") == 1,
          "unsupported first side does not stall reachable shell blocks");
      r.modules().setEnabled("beddefender", false);
      HookBridge.gameControls().apply();
      set(r, "beddefender", "place-delay", "300");
      peer.received.clear();
      r.modules().setEnabled("beddefender", true);
      living(p);
      forceLook(p);
      motion(p);
      wireBoundary(peer);
      check(
          count(peer.received, "C08PacketPlayerBlockPlacement") == 1,
          "delay test establishes its first placement");
      living(p);
      forceLook(p);
      motion(p);
      living(p);
      forceLook(p);
      motion(p);
      wireBoundary(peer);
      check(
          count(peer.received, "C08PacketPlayerBlockPlacement") == 1,
          "configured delay still separates subsequent attempts");
      r.modules().setEnabled("beddefender", false);
      HookBridge.gameControls().apply();
      set(r, "beddefender", "place-delay", "0");
      // A cancelled first walking packet must not authorize the new post-walking path.
      AutoCloseable cancel =
          r.networkEvents()
              .subscribe(
                  owner,
                  PacketSendEvent.class,
                  Integer.MAX_VALUE,
                  true,
                  e -> {
                    Object packet = e.getClass().getMethod("packet").invoke(e);
                    if (packet.getClass().getSuperclass().getSimpleName().equals("C03PacketPlayer")
                        || packet.getClass().getSimpleName().equals("C03PacketPlayer")) e.cancel();
                  });
      try {
        peer.received.clear();
        r.modules().setEnabled("beddefender", true);
        living(p);
        forceLook(p);
        motion(p);
        wireBoundary(peer);
        check(
            count(peer.received, "C08PacketPlayerBlockPlacement") == 0,
            "cancelled walking does not authorize a placement");
      } finally {
        cancel.close();
        r.modules().setEnabled("beddefender", false);
        HookBridge.gameControls().apply();
      }
      check(peer.failure.get() == null, "packets serialize through PLAY without failures");
      System.out.println(
          "[PERIHELION_SMOKE] BED_DEFENDER_ROTATIONS checks="
              + checks
              + " shells="
              + shells
              + " firstPassPlacements="
              + firstPasses
              + " PASS");
    } finally {
      r.modules().setEnabled("beddefender", false);
      owner.close();
      HookBridge.gameControls().apply();
      for (Map.Entry<Object, Object> e : terrain.entrySet())
        put.invoke(world, e.getKey(), e.getValue(), 2);
      System.arraycopy(oldItems, 0, items, 0, items.length);
      position(p, px, py, pz);
      sensitivity.setFloat(options, oldSensitivity);
      fps.setInt(null, oldFps);
      screen.set(game, oldScreen);
      for (Map.Entry<Setting<?>, Object> e : settings.entrySet())
        restore(r, e.getKey(), e.getValue());
      for (Map.Entry<String, Boolean> e : enabled.entrySet())
        if (e.getValue()) r.modules().setEnabled(e.getKey(), true);
    }
  }

  private static void living(Object player) throws Exception {
    method(player.getClass(), "onLivingUpdate", "func_70636_d").invoke(player);
  }

  private static void resetPlayer(Object p, double x, int y, double z) throws Exception {
    position(p, x, y, z);
    field(p.getClass(), "onGround", "field_70122_E").setBoolean(p, true);
    for (String[] n :
        new String[][] {
          {"motionX", "field_70159_w"}, {"motionY", "field_70181_x"}, {"motionZ", "field_70179_y"}
        }) field(p.getClass(), n).setDouble(p, 0);
  }

  private static int filled(Object world, Method get, Object[] positions) throws Exception {
    int count = 0;
    for (Object p : positions) if (!state("air").equals(get.invoke(world, p))) count++;
    return count;
  }

  private static Object meta(String name, int metadata) throws Exception {
    Object block = method(state(name).getClass(), "getBlock", "func_177230_c").invoke(state(name));
    return method(block.getClass(), new Class<?>[] {int.class}, "getStateFromMeta", "func_176203_a")
        .invoke(block, metadata);
  }

  @SuppressWarnings("unchecked")
  private static <T> void restore(ClientRuntime r, Setting<?> s, Object v) {
    r.modules().setSetting("beddefender", (Setting<T>) s, (T) v);
  }

  private static void check(boolean value, String label) {
    if (!value) throw new AssertionError("Bed Defender rotations: " + label);
    checks++;
  }
}
