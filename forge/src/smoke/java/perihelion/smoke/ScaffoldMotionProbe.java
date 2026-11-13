package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;
import static perihelion.smoke.RotationPipelineProbe.*;

import java.lang.reflect.*;
import java.util.*;
import perihelion.core.control.*;
import perihelion.core.event.EventOwner;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.integration.minecraft189.event.*;

/** The walking angle must describe the very same update's native jump and acceleration. */
final class ScaffoldMotionProbe {
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
    int x = (int) Math.floor(px),
        y = 190,
        z = (int) Math.floor(pz),
        cases = 0,
        checks = 0,
        sprintJumps = 0;
    Map<Object, Object> terrain = new LinkedHashMap<>();
    for (int dx = -4; dx <= 4; dx++)
      for (int dy = -2; dy <= 5; dy++)
        for (int dz = -4; dz <= 4; dz++) {
          Object p = pos(x + dx, y + dy, z + dz);
          terrain.put(p, get.invoke(world, p));
        }
    List<Float> strafes = new ArrayList<>(), jumps = new ArrayList<>(), walks = new ArrayList<>();
    EventOwner owner =
        new EventOwner(
            "scaffold-motion-consistency",
            failure -> {
              throw new AssertionError(failure);
            });
    owner.activate();
    List<AutoCloseable> listeners = new ArrayList<>();
    listeners.add(
        r.clientEvents()
            .subscribe(
                owner, StrafeEvent.class, Integer.MIN_VALUE, false, e -> strafes.add(e.yaw())));
    listeners.add(
        r.clientEvents()
            .subscribe(
                owner,
                MovementYawEvent.class,
                Integer.MIN_VALUE,
                false,
                e -> {
                  if (e.jump()) jumps.add(e.yaw());
                }));
    Class<?> walking = Class.forName("net.minecraft.network.play.client.C03PacketPlayer");
    float[] serverYaw = {0};
    listeners.add(
        r.networkEvents()
            .subscribe(
                owner,
                PacketSendEvent.class,
                Integer.MIN_VALUE,
                true,
                e -> {
                  Object packet = e.getClass().getMethod("packet").invoke(e);
                  if (e.isCancelled() || !walking.isInstance(packet)) return;
                  if ((Boolean) method(walking, "getRotating", "func_149463_k").invoke(packet))
                    serverYaw[0] =
                        ((Number) method(walking, "getYaw", "func_149462_g").invoke(packet))
                            .floatValue();
                  walks.add(serverYaw[0]);
                }));
    try {
      set(r, "scaffold", "tower", "NONE");
      set(r, "scaffold", "expand", "0");
      set(r, "scaffold", "keep-y", "true");
      set(r, "scaffold", "sprint", "NONE");
      set(r, "scaffold", "min-rotation", "10");
      set(r, "scaffold", "max-rotation", "10");
      set(r, "scaffold", "rotations", "NORMAL");
      set(r, "scaffold", "ray-cast", "STRICT");
      for (String mode :
          new String[] {"NORMAL", "TELLY", "HYPIXEL_TELLY", "HYPIXEL", "BREEZILY", "GOD_BRIDGE"})
        for (String sprintMode : new String[] {"NONE", "VANILLA"})
          for (boolean fix : new boolean[] {true, false})
            for (boolean manual : new boolean[] {false, true})
              for (float heading : new float[] {0, 45, 179, -179, 540, 720}) {
                r.modules().setEnabled("scaffold", false);
                for (Object p : terrain.keySet()) put.invoke(world, p, state("air"), 2);
                put.invoke(world, pos(x, y, z), state("stone"), 2);
                position(player, x + .5, y + 1, z + 1.15);
                camera(player, heading, 0);
                for (String[] n :
                    new String[][] {
                      {"motionX", "field_70159_w"},
                      {"motionY", "field_70181_x"},
                      {"motionZ", "field_70179_y"}
                    }) field(player.getClass(), n).setDouble(player, 0);
                field(player.getClass(), "onGround", "field_70122_E").setBoolean(player, true);
                field(player.getClass(), "isCollidedHorizontally", "field_70123_F")
                    .setBoolean(player, false);
                field(player.getClass(), "jumpTicks", "field_70773_bE").setInt(player, 0);
                field(player.getClass(), "sprintToggleTimer", "field_71156_d").setInt(player, 0);
                method(
                        player.getClass(),
                        new Class<?>[] {boolean.class},
                        "setSprinting",
                        "func_70031_b")
                    .invoke(player, false);
                Arrays.fill(items, null);
                items[0] = stack("stone");
                field(items[0].getClass(), "stackSize", "field_77994_a").setInt(items[0], 64);
                for (InputKey key : keys.keySet()) {
                  boolean down =
                      key == InputKey.FORWARD
                          || key == InputKey.JUMP && manual
                          || key == InputKey.SPRINT && sprintMode.equals("VANILLA");
                  keys.get(key).set(down);
                  nativeKeys.set(key, down);
                }
                set(r, "scaffold", "mode", mode.equals("HYPIXEL_TELLY") ? "TELLY" : mode);
                set(r, "scaffold", "hypixel-telly", String.valueOf(mode.equals("HYPIXEL_TELLY")));
                set(r, "scaffold", "move-fix", String.valueOf(fix));
                set(r, "scaffold", "sprint", sprintMode);
                set(r, "scaffold", "auto-jump", String.valueOf(!mode.equals("NORMAL")));
                forceLook(player);
                serverYaw[0] = -171;
                r.modules().setEnabled("scaffold", true);
                for (int tick = 0; tick < 8; tick++) {
                  strafes.clear();
                  jumps.clear();
                  walks.clear();
                  r.controls().beginTick();
                  method(player.getClass(), "onLivingUpdate", "func_70636_d").invoke(player);
                  motion(player);
                  String label =
                      mode
                          + " sprint="
                          + sprintMode
                          + " fix="
                          + fix
                          + " manual="
                          + manual
                          + " heading="
                          + heading
                          + " update="
                          + tick;
                  require(
                      !strafes.isEmpty() && !walks.isEmpty(),
                      label + " observes real physics and walking");
                  checks++;
                  float sent = walks.get(walks.size() - 1);
                  for (float yaw : strafes) {
                    require(
                        difference(yaw, sent) < 1e-4,
                        label + " strafe yaw=" + yaw + " sent yaw=" + sent);
                    checks++;
                  }
                  for (float yaw : jumps) {
                    require(
                        difference(yaw, sent) < 1e-4,
                        label + " jump yaw=" + yaw + " sent yaw=" + sent);
                    checks++;
                    sprintJumps++;
                  }
                }
                cases++;
              }
      require(sprintJumps > 0, "matrix observes actual horizontal sprint-jump impulses");
      System.out.println(
          "[PERIHELION_SMOKE] SCAFFOLD_MOTION cases="
              + cases
              + " checks="
              + checks
              + " sprintJumps="
              + sprintJumps
              + " PASS");
      r.modules().setEnabled("scaffold", false);
      perihelion.integration.minecraft189.HookBridge.gameControls().apply();
      for (InputKey key : keys.keySet()) {
        keys.get(key).clear();
        nativeKeys.set(key, false);
      }
      for (Object p : terrain.keySet()) put.invoke(world, p, state("air"), 2);
      put.invoke(world, pos(x, y, z), state("stone"), 2);
      set(r, "scaffold", "mode", "NORMAL");
      set(r, "scaffold", "auto-jump", "false");
      set(r, "scaffold", "move-fix", "true");
      set(r, "scaffold", "keep-y", "false");
      List<Object> packets = new java.util.concurrent.CopyOnWriteArrayList<>();
      peer.client
          .pipeline()
          .addLast(
              "scaffold-motion-geometry",
              new io.netty.channel.ChannelOutboundHandlerAdapter() {
                @Override
                public void write(
                    io.netty.channel.ChannelHandlerContext ctx,
                    Object packet,
                    io.netty.channel.ChannelPromise promise)
                    throws Exception {
                  packets.add(packet);
                  ctx.write(packet, promise);
                }
              });
      try {
        RotationPipelineProbe.scaffold(
            r, player, world, items, get, put, peer.client, packets, x, y, z);
        System.out.println("[PERIHELION_SMOKE] SCAFFOLD_ROTATIONS cases=18 PASS");
        ScaffoldTerrainProbe.run(
            r, game, player, world, items, get, put, peer.client, packets, x, y, z);
      } finally {
        peer.client.pipeline().remove("scaffold-motion-geometry");
      }
    } finally {
      r.modules().setEnabled("scaffold", false);
      for (AutoCloseable listener : listeners) listener.close();
      owner.close();
      for (Map.Entry<Object, Object> e : terrain.entrySet())
        put.invoke(world, e.getKey(), e.getValue(), 2);
    }
  }

  private static double difference(float a, float b) {
    double d = (a - b) % 360;
    return Math.abs(d > 180 ? d - 360 : d < -180 ? d + 360 : d);
  }

  private static void require(boolean value, String message) {
    if (!value) throw new AssertionError("Scaffold walking consistency: " + message);
  }
}
