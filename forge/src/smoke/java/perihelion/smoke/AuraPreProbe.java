package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;
import static perihelion.smoke.RotationPipelineProbe.*;

import io.netty.channel.Channel;
import java.lang.reflect.*;
import java.util.*;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleContext;
import perihelion.core.module.ModuleEntry;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.*;

/** Actual default mouse attacks, sprint-hit physics and packet windows on transformed classes. */
public final class AuraPreProbe {
  private static int checks;

  static void run(
      ClientRuntime r,
      Object game,
      Object player,
      Object world,
      Object[] items,
      Channel channel,
      List<Object> packets,
      int x,
      int y,
      int z)
      throws Exception {
    checks = 0;
    Object aura = r.modules().require("aura").module();
    r.modules().setEnabled("aura", false);
    for (Setting<?> setting : r.modules().require("aura").module().settings().values())
      reset(r, setting);
    Object profile =
        Class.forName("com.mojang.authlib.GameProfile")
            .getConstructor(UUID.class, String.class)
            .newInstance(
                UUID.fromString("7d3f8c79-a7a1-4ace-bcb4-490b0323112a"), "AuraOrderFixture");
    Object peer =
        Class.forName("net.minecraft.client.entity.EntityOtherPlayerMP")
            .getConstructor(Class.forName("net.minecraft.world.World"), profile.getClass())
            .newInstance(world, profile);
    Field next = field(aura.getClass(), "nextAttack");
    Field clickCounter = field(game.getClass(), "leftClickCounter", "field_71429_W");
    int oldClickCounter = clickCounter.getInt(game);
    Field mx = field(player.getClass(), "motionX", "field_70159_w"),
        mz = field(player.getClass(), "motionZ", "field_70179_y");
    Method sprint =
        method(player.getClass(), new Class<?>[] {boolean.class}, "setSprinting", "func_70031_b");
    Object inventory = field(player.getClass(), "inventory", "field_71071_by").get(player);
    List<AutoCloseable> subscriptions = new ArrayList<>();
    try {
      position(player, x + .5, y + 1, z + .5);
      position(peer, x + .5, y + 1, z + 2.5);
      method(
              world.getClass(),
              new Class<?>[] {int.class, Class.forName("net.minecraft.entity.Entity")},
              "addEntityToWorld",
              "func_73027_a")
          .invoke(world, -887715, peer);
      camera(player, 110, -5);
      Arrays.fill(items, null);
      items[0] = stack("diamond_sword");
      field(inventory.getClass(), "currentItem", "field_70461_c").setInt(inventory, 0);
      r.modules().setEnabled("aura", true);
      ModuleContext context =
          (ModuleContext) field(ModuleEntry.class, "context").get(r.modules().require("aura"));
      List<String> events = new ArrayList<>();
      subscriptions.add(context.listen(PlayerAttackEvent.class, e -> events.add("attack")));
      subscriptions.add(context.listen(HitSlowdownEvent.class, e -> events.add("slowdown")));
      flush(channel);
      packets.clear();
      next.setDouble(aura, 0);
      HookBridge.gameControls().beforeInput();
      forceLook(player);
      motion(player);
      flush(channel);
      check(
          method(aura.getClass(), "target").invoke(aura) == peer,
          "default Aura selects an actual remote-player fixture");
      check(
          no(packets, "C0APacketAnimation") && no(packets, "C02PacketUseEntity"),
          "first unsent aim produces neither animation nor attack");
      next.setDouble(aura, Double.MAX_VALUE);
      for (int tick = 0; tick < 12; tick++) {
        HookBridge.gameControls().beforeInput();
        forceLook(player);
        motion(player);
      }
      for (boolean mouse : new boolean[] {true, false}) {
        set(r, "aura", "simulate-mouse-clicks", Boolean.toString(mouse));
        next.setDouble(aura, Double.MAX_VALUE);
        HookBridge.gameControls().beforeInput();
        flush(channel);
        packets.clear();
        forceLook(player);
        motion(player);
        flush(channel);
        // The server knows this preceding look; the next aim must not replace action geometry.
        perihelion.core.control.Rotation submitted = r.controls().rotation().get();
        mx.setDouble(player, .5);
        mz.setDouble(player, .4);
        sprint.invoke(player, true);
        events.clear();
        clickCounter.setInt(game, 0);
        next.setDouble(aura, 0);
        HookBridge.gameControls().beforeInput();
        flush(channel);
        check(
            events.equals(Arrays.asList("attack", "slowdown")),
            "real "
                + (mouse ? "default mouse" : "direct")
                + " hit applies slowdown in PRE: "
                + events
                + " motion="
                + mx.getDouble(player)
                + ","
                + mz.getDouble(player)
                + " aim="
                + submitted.yaw()
                + ","
                + submitted.pitch()
                + " target="
                + method(aura.getClass(), "target").invoke(aura)
                + " using="
                + method(player.getClass(), "isUsingItem", "func_71039_bw").invoke(player)
                + " previous="
                + HookBridge.gameControls()
                    .previousRotation(
                        (perihelion.core.control.ControlRequest<perihelion.core.control.Rotation>)
                            field(aura.getClass(), "rotation").get(aura))
                    .isPresent()
                + " packets="
                + packetNames(packets));
        check(
            Math.abs(mx.getDouble(player) - .3) < 1e-6
                && Math.abs(mz.getDouble(player) - .24) < 1e-6,
            "default hit factor is applied before subsequent movement");
        check(
            !(Boolean) method(player.getClass(), "isSprinting", "func_70051_ag").invoke(player),
            "default Aura retains vanilla sprint-hit reset");
        forceLook(player);
        motion(player);
        flush(channel);
        ordered(packets, "C02PacketUseEntity", submitted, "Aura " + (mouse ? "mouse" : "direct"));
        beforeNextWalk(packets, "C0APacketAnimation");
        beforeNextWalk(packets, "C02PacketUseEntity");
        check(
            count(packets, "C0APacketAnimation") == 1 && count(packets, "C02PacketUseEntity") == 1,
            "one due click emits exactly one swing and attack");
        check(
            no(packets, "C08PacketPlayerBlockPlacement") && no(packets, "C07PacketPlayerDigging"),
            "default Fake block remains visual-only");
        // A transaction arriving after this walking packet must see an empty post-action window.
        check(emptyPostWindow(packets), "native trace leaves no animation/attack after walking");
      }
      // Re-entering input without a new walking pass must not repeat a previously authorized hit.
      next.setDouble(aura, Double.MAX_VALUE);
      HookBridge.gameControls().beforeInput();
      flush(channel);
      packets.clear();
      next.setDouble(aura, 0);
      clickCounter.setInt(game, 0);
      HookBridge.gameControls().beforeInput();
      flush(channel);
      check(
          no(packets, "C0APacketAnimation") && no(packets, "C02PacketUseEntity"),
          "a repeated input callback cannot reuse the preceding action window");
      forceLook(player);
      motion(player);
      position(player, x + .6, y + 1, z + .5);
      flush(channel);
      packets.clear();
      HookBridge.gameControls().beforeInput();
      flush(channel);
      check(
          no(packets, "C0APacketAnimation") && no(packets, "C02PacketUseEntity"),
          "movement since the submitted position invalidates PRE action authorization");
      position(player, x + .5, y + 1, z + .5);
      next.setDouble(aura, Double.MAX_VALUE);
      HookBridge.gameControls().beforeInput();
      forceLook(player);
      motion(player);
      subscriptions.add(
          context.listen(
              PlayerAttackEvent.class,
              e -> {
                e.cancel();
                r.modules().setEnabled("aura", false);
              }));
      next.setDouble(aura, 0);
      HookBridge.gameControls().beforeInput();
      flush(channel);
      check(
          !r.modules().require("aura").requestedEnabled()
              && !r.controls().rotation().isPresent()
              && method(aura.getClass(), "target").invoke(aura) == null,
          "disable during the attack callback does not restore a retired rotation claim");
      r.modules().setEnabled("aura", true);
      next.setDouble(aura, 0);
      flush(channel);
      packets.clear();
      HookBridge.gameControls().beforeInput();
      forceLook(player);
      motion(player);
      flush(channel);
      check(
          no(packets, "C0APacketAnimation") && no(packets, "C02PacketUseEntity"),
          "reactivation cannot borrow the previous owner's submitted aim");
      System.out.println(
          "PERIHELION_SMOKE AURA_PRE checks="
              + checks
              + " mouse=true,false slowdown=beforeMovement postWindow=empty PASS");
    } finally {
      for (AutoCloseable subscription : subscriptions) subscription.close();
      r.modules().setEnabled("aura", false);
      for (Setting<?> setting : r.modules().require("aura").module().settings().values())
        reset(r, setting);
      method(world.getClass(), new Class<?>[] {int.class}, "removeEntityFromWorld", "func_73028_b")
          .invoke(world, -887715);
      HookBridge.gameControls().apply();
      clickCounter.setInt(game, oldClickCounter);
    }
  }

  private static void beforeNextWalk(List<Object> packets, String action) throws Exception {
    Class<?> walking = Class.forName("net.minecraft.network.play.client.C03PacketPlayer");
    int first = -1;
    for (int i = 0; i < packets.size(); i++)
      if (walking.isInstance(packets.get(i))) {
        first = i;
        break;
      }
    check(first >= 0, "trace contains the preceding walking aim");
    orderedBeforeWalking(packets.subList(first + 1, packets.size()), action, "Aura " + action);
  }

  private static boolean emptyPostWindow(List<Object> packets) throws Exception {
    Class<?> walking = Class.forName("net.minecraft.network.play.client.C03PacketPlayer");
    boolean post = false;
    for (Object p : packets) {
      if (walking.isInstance(p)) post = false;
      else if (p.getClass().getSimpleName().equals("C0APacketAnimation")
          || p.getClass().getSimpleName().equals("C02PacketUseEntity")) post = true;
    }
    return !post;
  }

  private static boolean no(List<Object> packets, String name) {
    return count(packets, name) == 0;
  }

  private static String packetNames(List<Object> packets) {
    List<String> names = new ArrayList<>();
    for (Object p : packets) names.add(p.getClass().getSimpleName());
    return names.toString();
  }

  private static long count(List<Object> packets, String name) {
    return packets.stream().filter(p -> p.getClass().getSimpleName().equals(name)).count();
  }

  private static <T> void reset(ClientRuntime r, Setting<T> setting) {
    r.modules().setSetting("aura", setting, setting.defaultValue());
  }

  private static void check(boolean yes, String message) {
    checks++;
    if (!yes) throw new AssertionError(message);
  }
}
