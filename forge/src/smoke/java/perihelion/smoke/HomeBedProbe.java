package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;
import static perihelion.smoke.RotationPipelineProbe.*;

import io.netty.channel.Channel;
import java.lang.reflect.*;
import java.util.*;
import perihelion.core.context.ConnectionContext;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.PlayerControlEvent;

/** Native match lifetime, delayed bed acquisition and actual protected/enemy digging checks. */
public final class HomeBedProbe {
  private static int checks;

  static void run(ClientRuntime r, Object game, Object player, Object world, Object[] items,
      Method get, Method put, Channel channel, List<Object> packets, int x, int y, int z)
      throws Exception {
    checks = 0;
    Object breaker = r.modules().require("breaker").module();
    Object tracker = field(breaker.getClass(), "homeBeds").get(breaker);
    Method acquired = method(tracker.getClass(), "acquired");
    Method protects = method(tracker.getClass(), new Class<?>[] {
        Class.forName("net.minecraft.util.BlockPos")}, "protects");
    Method revision = method(tracker.getClass(), "revision");
    Object home = pos(x + 1, y + 1, z + 1), homeHead = pos(x + 1, y + 1, z + 2);
    Object enemy = pos(x + 25, y + 1, z + 1), enemyHead = pos(x + 25, y + 1, z + 2);
    Map<Object, Object> saved = new LinkedHashMap<>();
    for (Object p : new Object[] {home, homeHead, enemy, enemyHead}) saved.put(p, get.invoke(world, p));
    ConnectionContext connection = r.connection();
    int subscriptions = r.clientEvents().subscriptionCount();
    try {
      r.modules().setEnabled("breaker", false);
      set(r, "breaker", "whitelist", "true");
      set(r, "breaker", "mode", "LEGIT");
      items[0] = stack("shears");
      position(player, x + .5, y + 1, z + .5);
      resetWorld(r, connection);
      bed(world, put, home, homeHead);
      bed(world, put, enemy, enemyHead);
      step(r, player, channel, packets, 10);
      check((Boolean) acquired.invoke(tracker), "captures home while Breaker is disabled");
      check((Boolean) protects.invoke(tracker, home), "protects home foot");
      check((Boolean) protects.invoke(tracker, homeHead), "protects home head");
      check(!(Boolean) protects.invoke(tracker, enemy), "enemy remains outside whitelist");
      long known = (Long) revision.invoke(tracker);

      position(player, x + 24.5, y + 1, z + .5);
      r.modules().setEnabled("breaker", true);
      List<Object> dig = step(r, player, channel, packets, 20);
      check(started(dig, enemy) || started(dig, enemyHead),
          "enabling next to enemy bed emits native START digging for the enemy");
      check((Long) revision.invoke(tracker) == known, "late enable does not recapture the enemy");
      r.modules().setEnabled("breaker", false);
      position(player, x + .5, y + 1, z + .5);
      r.modules().setEnabled("breaker", true);
      dig = step(r, player, channel, packets, 15);
      check(!started(dig, home) && !started(dig, homeHead),
          "repeated enable preserves both home halves without native START digging");
      check(method(get.invoke(world, home).getClass(), "getBlock", "func_177230_c")
          .invoke(get.invoke(world, home))
          == method(state("bed").getClass(), "getBlock", "func_177230_c").invoke(state("bed")),
          "home remains in the client world");
      r.modules().setEnabled("breaker", false);
      put.invoke(world, home, state("air"), 2);
      put.invoke(world, homeHead, state("air"), 2);
      step(r, player, channel, packets, 25);
      check((Long) revision.invoke(tracker) == known,
          "lost home blocks do not retarget acquisition to another bed in the match");

      // Start before the home bed arrives, then move beside an enemy during the retry.
      position(player, x + .5, y + 1, z + .5);
      resetWorld(r, connection);
      bed(world, put, enemy, enemyHead);
      r.modules().setEnabled("breaker", true);
      dig = step(r, player, channel, packets, 10);
      check(!(Boolean) acquired.invoke(tracker), "missing home stays pending");
      position(player, x + 24.5, y + 1, z + .5);
      dig.addAll(step(r, player, channel, packets, 12));
      check(!(Boolean) acquired.invoke(tracker), "retry remains anchored at spawn after movement");
      check(!started(dig, enemy) && !started(dig, enemyHead),
          "unknown home prevents digging an unclassified bed");
      bed(world, put, home, homeHead);
      dig = step(r, player, channel, packets, 35);
      check((Boolean) protects.invoke(tracker, home), "late bed arrival captures original home");
      check(!(Boolean) protects.invoke(tracker, enemy), "late arrival does not whitelist enemy");
      check(started(dig, enemy) || started(dig, enemyHead),
          "Breaker resumes enemy digging after delayed home acquisition");

      // Only system announcements reset; normal chat with the same words does not.
      known = (Long) revision.invoke(tracker);
      message(r, 0);
      check((Long) revision.invoke(tracker) == known, "player chat cannot reset home acquisition");
      bed(world, put, enemy, enemyHead);
      HookBridge.gameControls().beforeInput();
      flush(channel);
      packets.clear();
      message(r, 1);
      r.clientEvents().post(PlayerControlEvent.BEFORE_ACTIONS);
      flush(channel);
      check(!(Boolean) acquired.invoke(tracker), "system match start retires old home immediately");
      check(!started(packets, enemy) && !started(packets, enemyHead),
          "match reset between preparation and actions prevents old-match digging");
      check(!r.controls().rotation().isPresent() && !r.controls().hotbar().isPresent(),
          "match reset clears Breaker's pending aim and tool");
      r.modules().setEnabled("breaker", false);
      step(r, player, channel, packets, 10);
      check((Boolean) protects.invoke(tracker, enemy), "next match acquires new spawn bed");
      check(!(Boolean) protects.invoke(tracker, home), "previous match bed is no longer protected");
      known = (Long) revision.invoke(tracker);
      message(r, 1);
      check((Long) revision.invoke(tracker) == known, "duplicate start announcement preserves home");

      r.updateWorld(false, ConnectionContext.DISCONNECTED, true);
      check(!(Boolean) acquired.invoke(tracker), "world loss discards captured home");
      check(field(tracker.getClass(), "world").get(tracker) == null, "world loss releases world reference");
      r.updateWorld(true, connection, true);
      check(!(Boolean) acquired.invoke(tracker), "replacement world starts without stale home");
    } finally {
      r.modules().setEnabled("breaker", false);
      set(r, "breaker", "whitelist", "true");
      for (Map.Entry<Object, Object> e : saved.entrySet()) put.invoke(world, e.getKey(), e.getValue(), 2);
      position(player, x + .5, y + 1, z + .5);
      resetWorld(r, connection);
      HookBridge.gameControls().apply();
    }
    check(r.clientEvents().subscriptionCount() == subscriptions,
        "toggles and match/world resets retain exactly the runtime-owned observer baseline");
    System.out.println("PERIHELION_SMOKE HOME_BED checks=" + checks
        + " disabledCapture=true lateEnable=true delayedBed=true matchReset=true PASS");
  }

  private static void resetWorld(ClientRuntime r, ConnectionContext connection) {
    r.updateWorld(false, ConnectionContext.DISCONNECTED, true);
    r.updateWorld(true, connection, true);
  }

  private static void bed(Object world, Method put, Object foot, Object head) throws Exception {
    Object base = state("bed");
    Object block = method(base.getClass(), "getBlock", "func_177230_c").invoke(base);
    Method fromMeta = method(block.getClass(), new Class<?>[] {int.class},
        "getStateFromMeta", "func_176203_a");
    Object footState = fromMeta.invoke(block, 0), headState = fromMeta.invoke(block, 8);
    put.invoke(world, foot, footState, 2);
    put.invoke(world, head, headState, 2);
  }

  private static void message(ClientRuntime r, int type) throws Exception {
    Object component = Class.forName("net.minecraft.util.ChatComponentText")
        .getConstructor(String.class).newInstance("Protect your bed and destroy the enemy beds.");
    Object packet = Class.forName("net.minecraft.network.play.server.S02PacketChat")
        .getConstructor(Class.forName("net.minecraft.util.IChatComponent"), byte.class)
        .newInstance(component, (byte) type);
    Object event = Class.forName("perihelion.integration.minecraft189.event.ClientPacketEvent")
        .getConstructor(Class.forName("net.minecraft.network.Packet")).newInstance(packet);
    r.clientEvents().post(event);
  }

  private static List<Object> step(ClientRuntime r, Object player, Channel channel,
      List<Object> packets, int count) throws Exception {
    List<Object> result = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      r.controls().beginTick();
      flush(channel);
      packets.clear();
      HookBridge.gameControls().beforeInput();
      forceLook(player);
      motion(player);
      flush(channel);
      result.addAll(packets);
    }
    return result;
  }

  private static boolean started(List<Object> packets, Object pos) throws Exception {
    for (Object packet : packets) {
      if (!packet.getClass().getSimpleName().equals("C07PacketPlayerDigging")) continue;
      Object action = method(packet.getClass(), "getStatus", "func_180762_c").invoke(packet);
      Object target = method(packet.getClass(), "getPosition", "func_179715_a").invoke(packet);
      if (((Enum<?>) action).name().equals("START_DESTROY_BLOCK") && pos.equals(target)) return true;
    }
    return false;
  }

  private static void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }
}
