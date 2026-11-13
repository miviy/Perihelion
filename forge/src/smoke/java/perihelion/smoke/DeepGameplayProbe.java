package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;

import java.lang.reflect.*;
import java.util.*;
import perihelion.core.control.*;
import perihelion.core.event.EventOwner;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.*;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.*;

/** Deterministic edge cases on the packaged game. All fixtures are client-local and restored. */
public final class DeepGameplayProbe {
  private static int checks;
  private static final List<String> failures = new ArrayList<>();
  private static final String GAMEPLAY = "perihelion.integration.minecraft189.module.gameplay.";

  public static void run(ClientRuntime runtime) throws Exception {
    checks = 0;
    failures.clear();
    Object game =
        method(Class.forName("net.minecraft.client.Minecraft"), "getMinecraft", "func_71410_x")
            .invoke(null);
    Object player = field(game.getClass(), "thePlayer", "field_71439_g").get(game);
    Object world = field(game.getClass(), "theWorld", "field_71441_e").get(game);
    Object inventory = field(player.getClass(), "inventory", "field_71071_by").get(player);
    Object[] items =
        (Object[]) field(inventory.getClass(), "mainInventory", "field_70462_a").get(inventory);
    Object[] saved = items.clone();
    Field slot = field(inventory.getClass(), "currentItem", "field_70461_c");
    int oldSlot = slot.getInt(inventory);
    double x = number(player, "posX", "field_70165_t"),
        y = number(player, "posY", "field_70163_u"),
        z = number(player, "posZ", "field_70161_v");
    Field screen = field(game.getClass(), "currentScreen", "field_71462_r");
    Object oldScreen = screen.get(game);
    Class<?> posType = Class.forName("net.minecraft.util.BlockPos");
    Class<?> stateType = Class.forName("net.minecraft.block.state.IBlockState");
    Method getState =
        method(world.getClass(), new Class<?>[] {posType}, "getBlockState", "func_180495_p");
    Method putState =
        method(
            world.getClass(),
            new Class<?>[] {posType, stateType, int.class},
            "setBlockState",
            "func_180501_a");
    Map<Object, Object> states = new LinkedHashMap<>();
    int bx = (int) Math.floor(x),
        by = Math.min(230, (int) Math.floor(y) + 12),
        bz = (int) Math.floor(z);
    try {
      screen.set(game, null);
      Arrays.fill(items, null);
      slot.setInt(inventory, 0);
      for (int dx = -4; dx <= 4; dx++)
        for (int dy = 0; dy <= 5; dy++)
          for (int dz = -4; dz <= 4; dz++) {
            Object pos = pos(bx + dx, by + dy, bz + dz);
            states.put(pos, getState.invoke(world, pos));
            putState.invoke(world, pos, state("air"), 2);
          }
      position(player, bx + .5, by + 1, bz + 1.3);
      aura(runtime, game, player, world, items, bx, by, bz);
      placement(runtime, game, player, world, items, getState, putState, bx, by, bz);
      scaffold(runtime, game, player, world, items, getState, putState, bx, by, bz);
      long[] scanTimes = new long[7];
      set(runtime, "blockesp", "bed-range", "30");
      for (int i = 0; i < scanTimes.length; i++) {
        runtime.modules().setEnabled("blockesp", true);
        long start = System.nanoTime();
        runtime.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
        scanTimes[i] = System.nanoTime() - start;
        check(
            runtime.modules().require("blockesp").state() == ModuleState.ENABLED,
            "Block ESP maximum-range scan stays healthy");
        runtime.modules().setEnabled("blockesp", false);
      }
      Arrays.sort(scanTimes);
      System.out.println(
          "PERIHELION_SMOKE PERF blockEspMaxRangeFirstTick medianMs="
              + scanTimes[3] / 1e6
              + " maxMs="
              + scanTimes[6] / 1e6);
    } finally {
      for (String id : new String[] {"aura", "scaffold", "blockesp"})
        runtime.modules().setEnabled(id, false);
      HookBridge.gameControls().apply();
      for (Map.Entry<Object, Object> entry : states.entrySet())
        putState.invoke(world, entry.getKey(), entry.getValue(), 2);
      method(player.getClass(), "clearItemInUse", "func_71041_bz").invoke(player);
      System.arraycopy(saved, 0, items, 0, saved.length);
      slot.setInt(inventory, oldSlot);
      screen.set(game, oldScreen);
      position(player, x, y, z);
    }
    if (!failures.isEmpty()) throw new AssertionError("Deep gameplay regressions: " + failures);
    System.out.println("PERIHELION_SMOKE DEEP_GAMEPLAY checks=" + checks + " PASS");
  }

  private static void aura(
      ClientRuntime r,
      Object game,
      Object player,
      Object world,
      Object[] items,
      int x,
      int y,
      int z)
      throws Exception {
    Object pig =
        Class.forName("net.minecraft.entity.passive.EntityPig")
            .getConstructor(Class.forName("net.minecraft.world.World"))
            .newInstance(world);
    position(pig, x + .5, y + 1, z + 3.3);
    Method add =
        method(
            world.getClass(),
            new Class<?>[] {int.class, Class.forName("net.minecraft.entity.Entity")},
            "addEntityToWorld",
            "func_73027_a");
    Method remove =
        method(
            world.getClass(), new Class<?>[] {int.class}, "removeEntityFromWorld", "func_73028_b");
    add.invoke(world, -7654340, pig);
    set(r, "aura", "targets", "ANIMALS");
    set(r, "aura", "fov", "360");
    set(r, "aura", "weapons-only", "false");
    set(r, "aura", "through-walls", "true");
    set(r, "aura", "rotations", "NONE");
    set(r, "aura", "auto-block", "NONE");
    set(r, "aura", "simulate-mouse-clicks", "false");
    set(r, "aura", "ray-cast", "false");
    r.modules().setEnabled("aura", true);
    Object aura = r.modules().require("aura").module();
    Method prepare = method(aura.getClass(), "prepare"),
        perform = method(aura.getClass(), "perform"),
        target = method(aura.getClass(), "target");
    Field next = field(aura.getClass(), "nextAttack");
    int[] attacks = {0};
    ModuleContext context =
        (ModuleContext) field(ModuleEntry.class, "context").get(r.modules().require("aura"));
    perihelion.core.event.Subscription attackObservation =
        context.listen(
            PlayerAttackEvent.class,
            e -> {
              attacks[0]++;
              e.cancel();
            });
    try {
      prepare.invoke(aura);
      next.setLong(aura, 0);
      perform.invoke(aura);
      check(attacks[0] == 1, "Aura live target reaches the real attack hook");
      for (String reason : new String[] {"dead", "removed", "screen", "weapon"}) {
        items[0] = stack("diamond_sword");
        prepare.invoke(aura);
        check(target.invoke(aura) == pig, "Aura selects fixture before " + reason);
        int before = attacks[0];
        next.setLong(aura, 0);
        if (reason.equals("dead"))
          field(pig.getClass(), "isDead", "field_70128_L").setBoolean(pig, true);
        if (reason.equals("removed")) remove.invoke(world, -7654340);
        if (reason.equals("screen"))
          field(game.getClass(), "currentScreen", "field_71462_r")
              .set(game, Class.forName("net.minecraft.client.gui.GuiChat").newInstance());
        if (reason.equals("weapon")) {
          set(r, "aura", "weapons-only", "true");
          items[0] = null;
        }
        perform.invoke(aura);
        check(
            attacks[0] == before,
            "Aura rejects target/action invalidated by " + reason + " after preparation");
        field(pig.getClass(), "isDead", "field_70128_L").setBoolean(pig, false);
        if (reason.equals("removed")) {
          ((List<?>) field(world.getClass(), "loadedEntityList", "field_72996_f").get(world))
              .removeIf(entity -> entity == pig);
          add.invoke(world, -7654340, pig);
        }
        field(game.getClass(), "currentScreen", "field_71462_r").set(game, null);
        set(r, "aura", "weapons-only", "false");
      }
      items[0] = null;
      method(
              player.getClass(),
              new Class<?>[] {Class.forName("net.minecraft.item.ItemStack"), int.class},
              "setItemInUse",
              "func_71008_a")
          .invoke(player, stack("apple"), 32);
      try {
        prepare.invoke(aura);
        check(
            target.invoke(aura) == null,
            "Aura pauses for item use after the held stack disappears");
      } catch (InvocationTargetException failure) {
        check(false, "Aura empty held item during use throws " + failure.getCause());
      } finally {
        method(player.getClass(), "clearItemInUse", "func_71041_bz").invoke(player);
      }
      items[0] = stack("diamond_sword");
      for (String mode : new String[] {"FAKE", "VANILLA", "NCP", "LEGIT", "PREDICTIVE", "NONE"}) {
        set(r, "aura", "auto-block", mode);
        prepare.invoke(aura);
        next.setLong(aura, Long.MAX_VALUE);
        perform.invoke(aura);
        check(
            (Boolean) method(aura.getClass(), "blocking").invoke(aura) == !mode.equals("NONE"),
            "Aura " + mode + " establishes expected blocking state");
        items[0] = null;
        prepare.invoke(aura);
        check(
            !(Boolean) method(aura.getClass(), "blocking").invoke(aura),
            "Aura " + mode + " releases block after sword loss");
        items[0] = stack("diamond_sword");
      }
      set(r, "aura", "auto-block", "NCP");
      set(r, "aura", "only-block-if-hurt", "true");
      Field hurt = field(player.getClass(), "hurtTime", "field_70737_aN");
      int savedHurt = hurt.getInt(player);
      try {
        hurt.setInt(player, 0);
        prepare.invoke(aura);
        perform.invoke(aura);
        check(
            !(Boolean) method(aura.getClass(), "blocking").invoke(aura),
            "NCP respects Only Block If Hurt");
        hurt.setInt(player, 10);
        prepare.invoke(aura);
        perform.invoke(aura);
        check(
            (Boolean) method(aura.getClass(), "blocking").invoke(aura),
            "NCP blocks once the hurt threshold is reached");
      } finally {
        hurt.setInt(player, savedHurt);
        set(r, "aura", "only-block-if-hurt", "false");
      }
      set(r, "aura", "auto-block", "PREDICTIVE");
      Field progress = field(pig.getClass(), "swingProgress", "field_70733_aJ");
      for (int tick = 1; tick <= 12; tick++) {
        next.setLong(aura, 0);
        progress.setFloat(pig, (tick % 6) / 6f);
        prepare.invoke(aura);
      }
      Object prediction = field(aura.getClass(), "prediction").get(aura);
      check(
          ((Number) method(prediction.getClass(), "samples").invoke(prediction)).intValue() == 1,
          "Predictive Aura records wraps even when every tick is attack-ready");
      set(r, "aura", "auto-block", "NONE");
      backtrack(r, aura, pig, attackObservation);
      Object second =
          Class.forName("net.minecraft.entity.passive.EntityPig")
              .getConstructor(Class.forName("net.minecraft.world.World"))
              .newInstance(world);
      position(second, x + 1.5, y + 1, z + 3.3);
      add.invoke(world, -7654341, second);
      try {
        set(r, "aura", "mode", "SWITCH");
        set(r, "aura", "switch-delay", "2");
        Object cycle = field(aura.getClass(), "targetCycle").get(aura);
        Field switchedAt = field(cycle.getClass(), "switchedAt");
        switchedAt.setLong(cycle, System.nanoTime() / 1_000_000L);
        prepare.invoke(aura);
        check(target.invoke(aura) == second, "Aura Switch starts in stable entity-ID order");
        prepare.invoke(aura);
        check(target.invoke(aura) == second, "Aura Switch retains its target before delay");
        switchedAt.setLong(cycle, System.nanoTime() / 1_000_000L - 101);
        prepare.invoke(aura);
        check(target.invoke(aura) == pig, "Aura Switch advances after two 50ms units");
        remove.invoke(world, -7654340);
        prepare.invoke(aura);
        check(
            target.invoke(aura) == second,
            "Aura Switch retains a live target when another candidate disappears");
      } finally {
        remove.invoke(world, -7654341);
        add.invoke(world, -7654340, pig);
      }
    } finally {
      r.modules().setEnabled("aura", false);
      remove.invoke(world, -7654340);
    }
  }

  private static void backtrack(
      ClientRuntime r,
      Object aura,
      Object pig,
      perihelion.core.event.Subscription attackObservation)
      throws Exception {
    double x = number(pig, "posX", "field_70165_t"),
        y = number(pig, "posY", "field_70163_u"),
        z = number(pig, "posZ", "field_70161_v");
    Object game =
        method(Class.forName("net.minecraft.client.Minecraft"), "getMinecraft", "func_71410_x")
            .invoke(null);
    Object local = field(game.getClass(), "thePlayer", "field_71439_g").get(game);
    Field[] previous = {
      field(local.getClass(), "prevPosX", "field_70169_q"),
      field(local.getClass(), "prevPosY", "field_70167_r"),
      field(local.getClass(), "prevPosZ", "field_70166_s")
    };
    double[] savedPrevious = new double[3];
    String[][] currentNames = {
      {"posX", "field_70165_t"}, {"posY", "field_70163_u"}, {"posZ", "field_70161_v"}
    };
    for (int i = 0; i < 3; i++) {
      savedPrevious[i] = previous[i].getDouble(local);
      previous[i].setDouble(local, field(local.getClass(), currentNames[i]).getDouble(local));
    }
    int id = -7654340;
    int sx = (int) Math.floor(x * 32), sy = (int) Math.floor(y * 32), sz = (int) Math.floor(z * 32);
    field(pig.getClass(), "serverPosX", "field_70118_ct").setInt(pig, sx);
    field(pig.getClass(), "serverPosY", "field_70117_cu").setInt(pig, sy);
    field(pig.getClass(), "serverPosZ", "field_70116_cv").setInt(pig, sz);
    set(r, "backtrack", "style", "OLD");
    set(r, "backtrack", "min-range", "0");
    set(r, "backtrack", "max-range", "10");
    set(r, "backtrack", "max-delay", "2000");
    r.modules().setEnabled("backtrack", true);
    Object session = backtrackSession(r);
    Object state = field(session.getClass(), "state").get(session);
    Method point = method(state.getClass(), "position");
    Class<?> packet = Class.forName("net.minecraft.network.Packet");
    Constructor<?> received = PacketReceiveEvent.class.getConstructor(packet);
    Constructor<?> movement =
        Class.forName("net.minecraft.network.play.server.S14PacketEntity$S15PacketEntityRelMove")
            .getConstructor(int.class, byte.class, byte.class, byte.class, boolean.class);
    Constructor<?> teleport =
        Class.forName("net.minecraft.network.play.server.S18PacketEntityTeleport")
            .getConstructor(
                int.class, int.class, int.class, int.class, byte.class, byte.class, boolean.class);
    try {
      r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      check(
          field(state.getClass(), "holding").getBoolean(state),
          "Back Track selects its eligible Aura target");
      for (int i = 0; i < 2; i++) {
        Object event =
            received.newInstance(movement.newInstance(id, (byte) 32, (byte) 0, (byte) 0, true));
        r.networkEvents().post(event);
        check(
            ((perihelion.core.event.CancellableEvent) event).isCancelled(),
            "Back Track captures an inbound movement snapshot");
      }
      check(
          number(point.invoke(state), "x") == sx / 32.0 + 2,
          "Back Track accumulates two relative moves");
      r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      check(
          number(point.invoke(state), "x") == sx / 32.0 + 2,
          "Back Track keeps buffered position across client ticks");
      r.networkEvents()
          .post(
              received.newInstance(movement.newInstance(id, (byte) -16, (byte) 0, (byte) 0, true)));
      check(
          number(point.invoke(state), "x") == sx / 32.0 + 1.5,
          "Back Track applies signed deltas to pending position");
      r.networkEvents()
          .post(
              received.newInstance(
                  teleport.newInstance(id, 320, 640, 960, (byte) 0, (byte) 0, true)));
      r.networkEvents()
          .post(
              received.newInstance(movement.newInstance(id, (byte) 32, (byte) 0, (byte) 0, true)));
      check(
          number(point.invoke(state), "x") == 11 && number(point.invoke(state), "y") == 20,
          "Back Track resumes relative motion from teleport position");
      r.networkEvents()
          .post(
              received.newInstance(
                  movement.newInstance(-7654399, (byte) 32, (byte) 0, (byte) 0, true)));
      check(
          number(point.invoke(state), "x") == 11,
          "Unrelated entities cannot overwrite Back Track target position");
      attackObservation.close();
      Class<?> action =
          Class.forName("net.minecraft.network.play.client.C02PacketUseEntity$Action");
      Object attack =
          Class.forName("net.minecraft.network.play.client.C02PacketUseEntity")
              .getConstructor(Class.forName("net.minecraft.entity.Entity"), action)
              .newInstance(pig, action.getField("ATTACK").get(null));
      r.networkEvents().post(PacketSendEvent.class.getConstructor(packet).newInstance(attack));
      check(
          number(pig, "posX", "field_70165_t") == 11,
          "Back Track legacy attack callback uses cumulative position");
      r.modules().setEnabled("backtrack", false);
      check(
          point.invoke(state) == null && field(session.getClass(), "closed").getBoolean(session),
          "Back Track disable retires its position and buffer session");
      position(pig, x, y, z);
      field(pig.getClass(), "serverPosX", "field_70118_ct").setInt(pig, sx);
      field(pig.getClass(), "serverPosY", "field_70117_cu").setInt(pig, sy);
      field(pig.getClass(), "serverPosZ", "field_70116_cv").setInt(pig, sz);
      r.modules().setEnabled("backtrack", true);
      Object replacement = backtrackSession(r);
      Object fresh = field(replacement.getClass(), "state").get(replacement);
      check(
          replacement != session && method(fresh.getClass(), "position").invoke(fresh) == null,
          "Back Track re-enable starts without stale deltas");
    } finally {
      r.modules().setEnabled("backtrack", false);
      position(pig, x, y, z);
      for (int i = 0; i < 3; i++) previous[i].setDouble(local, savedPrevious[i]);
    }
  }

  static Object backtrackSession(ClientRuntime r) throws Exception {
    ModuleContext context =
        (ModuleContext) field(ModuleEntry.class, "context").get(r.modules().require("backtrack"));
    for (Object resource : (Iterable<?>) field(ModuleContext.class, "resources").get(context))
      if (resource.getClass().getName().endsWith("BackTrackModule$Session")) return resource;
    throw new AssertionError("Back Track activation owns no packet session");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void placement(
      ClientRuntime r,
      Object game,
      Object player,
      Object world,
      Object[] items,
      Method getState,
      Method putState,
      int x,
      int y,
      int z)
      throws Exception {
    Class<?> placement = Class.forName(GAMEPLAY + "Placement"),
        posType = Class.forName("net.minecraft.util.BlockPos");
    Object support = pos(x, y, z), target = pos(x, y, z + 1);
    putState.invoke(world, support, state("stone"), 2);
    Method at =
        method(
            placement,
            new Class<?>[] {game.getClass(), posType, double.class, boolean.class},
            "at");
    Object plan = at.invoke(null, game, target, 4.5, false);
    check(plan != null, "Placement finds a reachable side support");
    Method look =
        method(
            Class.forName(GAMEPLAY + "GameActions"),
            new Class<?>[] {
              Class.forName("net.minecraft.client.entity.EntityPlayerSP"),
              Class.forName("net.minecraft.util.Vec3")
            },
            "lookAt");
    Rotation aim = (Rotation) look.invoke(null, player, field(placement, "hit").get(plan));
    Method ray =
        method(
            placement,
            new Class<?>[] {game.getClass(), Rotation.class, double.class, boolean.class},
            "ray");
    check(
        (Boolean) ray.invoke(plan, game, aim, 4.5, true),
        "Placement strict ray reaches intended face");
    Class<?> search = Class.forName(GAMEPLAY + "ScaffoldRotations$Search");
    Method find =
        method(
            Class.forName(GAMEPLAY + "ScaffoldRotations"),
            new Class<?>[] {game.getClass(), placement, Rotation.class, search, boolean.class},
            "find");
    for (String mode : new String[] {"NORMAL", "SECONDARY", "ULTRA_SAFE"}) {
      Rotation found =
          (Rotation) find.invoke(null, game, plan, aim, Enum.valueOf((Class) search, mode), true);
      check(
          (Boolean) ray.invoke(plan, game, found, 4.5, true),
          "Scaffold " + mode + " finds a valid unobstructed face");
    }
    // Force an impossible face to quantify the donor-compatible fallback search.
    Object oldFace = field(placement, "face").get(plan);
    Class<?> facing = oldFace.getClass();
    field(placement, "face").set(plan, Enum.valueOf((Class) facing, "NORTH"));
    long[] times = new long[7];
    for (int i = 0; i < times.length; i++) {
      long start = System.nanoTime();
      Rotation fallback =
          (Rotation)
              find.invoke(null, game, plan, aim, Enum.valueOf((Class) search, "ULTRA_SAFE"), true);
      times[i] = System.nanoTime() - start;
      check(
          (Boolean) ray.invoke(plan, game, fallback, 4.5, false),
          "Ultra Safe fallback still reaches support");
    }
    Arrays.sort(times);
    System.out.println(
        "PERIHELION_SMOKE PERF ultraSafeImpossibleFace medianMs="
            + times[3] / 1e6
            + " maxMs="
            + times[6] / 1e6);
    for (String material : new String[] {"stone", "stone_slab"}) {
      putState.invoke(world, support, state(material), 2);
      for (String face : new String[] {"SOUTH", "NORTH"}) {
        field(placement, "face").set(plan, Enum.valueOf((Class) facing, face));
        for (Rotation previous :
            new Rotation[] {Rotation.server(179.25f, -89), Rotation.server(-179.25f, 89)}) {
          Rotation expected = referenceSearch(game, player, plan, previous, aim);
          Rotation actual =
              (Rotation)
                  find.invoke(
                      null, game, plan, previous, Enum.valueOf((Class) search, "ULTRA_SAFE"), true);
          check(
              actual.yaw() == expected.yaw() && actual.pitch() == expected.pitch(),
              "Ultra Safe preserves exhaustive search result for "
                  + material
                  + "/"
                  + face
                  + "/"
                  + previous.pitch());
        }
      }
    }
    putState.invoke(world, support, state("stone"), 2);
    field(placement, "face").set(plan, oldFace);
    EventOwner owner =
        new EventOwner(
            "deep-placement",
            failure -> {
              throw new AssertionError(failure);
            });
    owner.activate();
    List<AutoCloseable> resources = new ArrayList<>();
    SharedControls.Scope scope = r.controls().scope(owner, resources::add);
    ControlRequest<HotbarSelection> slot = scope.hotbar(200);
    ControlRequest<Rotation> rotation = scope.rotation(200);
    Method place =
        method(
            placement,
            new Class<?>[] {
              game.getClass(), ControlRequest.class, ControlRequest.class, boolean.class
            },
            "place");
    try {
      slot.set(HotbarSelection.spoof(0));
      rotation.set(aim);
      motion(player);
      items[0] = stack("stone");
      position(player, x + .5, y + 1, z + 10.3);
      check(
          !(Boolean) place.invoke(plan, game, slot, rotation, true),
          "Placement rejects a plan after moving out of reach");
      putState.invoke(world, target, state("air"), 2);
      position(player, x + .5, y + 1, z + 1.3);
      items[0] = stack("diamond_sword");
      check(
          !(Boolean) place.invoke(plan, game, slot, rotation, true),
          "Placement rejects a non-block substituted into its slot");
      items[0] = stack("stone");
      ControlRequest<Rotation> blocker = scope.rotation(300);
      blocker.set(aim);
      check(
          !(Boolean) place.invoke(plan, game, slot, rotation, true),
          "Placement yields to a higher rotation claim");
      blocker.close();
      putState.invoke(world, support, state("air"), 2);
      check(
          !(Boolean) place.invoke(plan, game, slot, rotation, true),
          "Placement rejects a removed support");
      putState.invoke(world, support, state("stone"), 2);
      Object oldScreen = field(game.getClass(), "currentScreen", "field_71462_r").get(game);
      field(game.getClass(), "currentScreen", "field_71462_r")
          .set(game, Class.forName("net.minecraft.client.gui.GuiChat").newInstance());
      check(
          !(Boolean) place.invoke(plan, game, slot, rotation, true),
          "Placement pauses if a screen opens after planning");
      field(game.getClass(), "currentScreen", "field_71462_r").set(game, oldScreen);
      putState.invoke(world, support, state("crafting_table"), 2);
      check(
          !(Boolean) place.invoke(plan, game, slot, rotation, true),
          "Container activation is not counted as a placed block");
      putState.invoke(world, support, state("stone"), 2);
      perihelion.core.event.Subscription gate =
          r.clientEvents().subscribe(owner, LocalInteractionEvent.class, e -> e.cancel());
      check(
          !(Boolean) place.invoke(plan, game, slot, rotation, true),
          "Placement respects the immediate Freecam interaction gate");
      gate.close();
      check(
          (Boolean) place.invoke(plan, game, slot, rotation, true),
          "Placement commits a valid block through the real controller");
      check(
          !state("air").equals(getState.invoke(world, target)),
          "Successful placement changes the intended target block");
      check(
          !(Boolean) place.invoke(plan, game, slot, rotation, true),
          "Placement rejects an already-filled target");
    } finally {
      for (AutoCloseable resource : resources) resource.close();
      owner.close();
      HookBridge.gameControls().apply();
    }
  }

  /** Slow independent oracle retaining the original exhaustive loop and exact cube test. */
  private static Rotation referenceSearch(
      Object game, Object player, Object plan, Rotation previous, Rotation center)
      throws Exception {
    Class<?> vec = Class.forName("net.minecraft.util.Vec3"),
        aabb = Class.forName("net.minecraft.util.AxisAlignedBB");
    Object eye =
        method(player.getClass(), new Class<?>[] {float.class}, "getPositionEyes", "func_174824_e")
            .invoke(player, 1f);
    Object world = field(game.getClass(), "theWorld", "field_71441_e").get(game);
    Object support = field(plan.getClass(), "support").get(plan),
        desiredFace = field(plan.getClass(), "face").get(plan);
    int
        x =
            ((Number) method(support.getClass(), "getX", "func_177958_n").invoke(support))
                .intValue(),
        y =
            ((Number) method(support.getClass(), "getY", "func_177956_o").invoke(support))
                .intValue(),
        z =
            ((Number) method(support.getClass(), "getZ", "func_177952_p").invoke(support))
                .intValue();
    Object cube =
        aabb.getConstructor(
                double.class, double.class, double.class, double.class, double.class, double.class)
            .newInstance((double) x, (double) y, (double) z, x + 1., y + 1., z + 1.);
    boolean inside =
        (Boolean)
            method(aabb, new Class<?>[] {vec}, "isVecInside", "func_72318_a").invoke(cube, eye);
    Method intercept =
        method(aabb, new Class<?>[] {vec, vec}, "calculateIntercept", "func_72327_a");
    Method ray =
        method(
            world.getClass(),
            new Class<?>[] {vec, vec, boolean.class, boolean.class, boolean.class},
            "rayTraceBlocks",
            "func_147447_a");
    Class<?> hitType = Class.forName("net.minecraft.util.MovingObjectPosition");
    Field type = field(hitType, "typeOfHit", "field_72313_a"),
        side = field(hitType, "sideHit", "field_178784_b");
    Method blockPos = method(hitType, "getBlockPos", "func_178782_a");
    Constructor<?> vector = vec.getConstructor(double.class, double.class, double.class);
    double ex = number(eye, "xCoord", "field_72450_a"),
        ey = number(eye, "yCoord", "field_72448_b"),
        ez = number(eye, "zCoord", "field_72449_c");
    Rotation[] first = new Rotation[6];
    for (int radius = 0; radius <= 270; radius++)
      for (int yaw = -radius; yaw <= radius; yaw++) {
        int remainder = radius - Math.abs(yaw);
        for (int sign = 0; sign < (remainder == 0 ? 1 : 2); sign++) {
          Rotation test =
              Rotation.server(
                  previous.yaw() + yaw,
                  Math.max(
                      -90, Math.min(90, previous.pitch() + (sign == 0 ? remainder : -remainder))));
          double yr = Math.toRadians(test.yaw()), pr = Math.toRadians(test.pitch());
          Object end =
              vector.newInstance(
                  ex - Math.sin(yr) * Math.cos(pr) * 4.5,
                  ey - Math.sin(pr) * 4.5,
                  ez + Math.cos(yr) * Math.cos(pr) * 4.5);
          if (!inside && intercept.invoke(cube, eye, end) == null) continue;
          Object hit = ray.invoke(world, eye, end, false, false, true);
          if (hit == null
              || !((Enum<?>) type.get(hit)).name().equals("BLOCK")
              || !support.equals(blockPos.invoke(hit))) continue;
          Object face = side.get(hit);
          if (face == desiredFace) return test;
          int index = ((Enum<?>) face).ordinal();
          if (first[index] == null) first[index] = test;
        }
      }
    for (Rotation rotation : first) if (rotation != null) return rotation;
    return center;
  }

  private static void scaffold(
      ClientRuntime r,
      Object game,
      Object player,
      Object world,
      Object[] items,
      Method getState,
      Method putState,
      int x,
      int y,
      int z)
      throws Exception {
    Object input = field(player.getClass(), "movementInput", "field_71158_b").get(player);
    Field forward = field(input.getClass(), "moveForward", "field_78900_b");
    float savedForward = forward.getFloat(input);
    Field ground = field(player.getClass(), "onGround", "field_70122_E");
    boolean savedGround = ground.getBoolean(player);
    Field yaw = field(player.getClass(), "rotationYaw", "field_70177_z");
    float savedYaw = yaw.getFloat(player);
    Object support = pos(x, y, z), target = pos(x, y, z + 1);
    try {
      set(r, "scaffold", "rotations", "OLD");
      set(r, "scaffold", "min-rotation", "10");
      set(r, "scaffold", "max-rotation", "10");
      set(r, "scaffold", "auto-jump", "false");
      set(r, "scaffold", "expand", "0");
      set(r, "scaffold", "keep-y", "false");
      for (String mode : new String[] {"NORMAL", "TELLY", "BREEZILY", "GOD_BRIDGE"})
        for (String swap : new String[] {"CLIENT", "SERVER"})
          for (String ray : new String[] {"NONE", "NORMAL", "STRICT"}) {
            putState.invoke(world, support, state("stone"), 2);
            putState.invoke(world, target, state("air"), 2);
            items[0] = stack("stone");
            field(items[0].getClass(), "stackSize", "field_77994_a").setInt(items[0], 64);
            position(player, x + .5, y + 1, z + 1.3);
            ground.setBoolean(player, false);
            forward.setFloat(input, 1);
            yaw.setFloat(player, 0);
            set(r, "scaffold", "mode", mode);
            set(r, "scaffold", "swap-mode", swap);
            set(r, "scaffold", "ray-cast", ray);
            r.modules().setEnabled("scaffold", true);
            for (int tick = 0;
                tick < 8 && state("air").equals(getState.invoke(world, target));
                tick++) {
              HookBridge.gameControls().beforeInput();
              motion(player);
            }
            check(
                !state("air").equals(getState.invoke(world, target)),
                "Scaffold places in " + mode + "/" + swap + "/" + ray);
            r.modules().setEnabled("scaffold", false);
            HookBridge.gameControls().apply();
            check(
                !r.controls().hotbar().isPresent() && !r.controls().rotation().isPresent(),
                "Scaffold matrix case releases claims");
          }
      set(r, "scaffold", "mode", "NORMAL");
      set(r, "scaffold", "ray-cast", "NONE");
      set(r, "scaffold", "expand", "2");
      putState.invoke(world, target, state("air"), 2);
      position(player, x + .5, y + 1, z + .5);
      ground.setBoolean(player, true);
      items[0] = stack("stone");
      r.modules().setEnabled("scaffold", true);
      HookBridge.gameControls().beforeInput();
      motion(player);
      HookBridge.gameControls().beforeInput();
      motion(player);
      check(
          !state("air").equals(getState.invoke(world, target)),
          "Scaffold Expand places ahead while the player remains on support");
      r.modules().setEnabled("scaffold", false);
      set(r, "scaffold", "expand", "0");
      set(r, "scaffold", "keep-y", "true");
      putState.invoke(world, target, state("air"), 2);
      position(player, x + .5, y + 1, z + 1.3);
      ground.setBoolean(player, false);
      items[0] = stack("stone");
      r.modules().setEnabled("scaffold", true);
      position(player, x + .5, y + 2.6, z + 1.3);
      forward.setFloat(input, 0);
      HookBridge.gameControls().beforeInput();
      forward.setFloat(input, 1);
      HookBridge.gameControls().beforeInput();
      motion(player);
      HookBridge.gameControls().beforeInput();
      motion(player);
      check(
          !state("air").equals(getState.invoke(world, target)),
          "Scaffold Keep Y survives a stationary midair tick");
      r.modules().setEnabled("scaffold", false);
    } finally {
      r.modules().setEnabled("scaffold", false);
      HookBridge.gameControls().apply();
      ground.setBoolean(player, savedGround);
      forward.setFloat(input, savedForward);
      yaw.setFloat(player, savedYaw);
    }
  }

  private static Object pos(int x, int y, int z) throws Exception {
    return Class.forName("net.minecraft.util.BlockPos")
        .getConstructor(int.class, int.class, int.class)
        .newInstance(x, y, z);
  }

  private static Object state(String name) throws Exception {
    Class<?> block = Class.forName("net.minecraft.block.Block");
    Object value =
        method(block, new Class<?>[] {String.class}, "getBlockFromName", "func_149684_b")
            .invoke(null, name);
    return method(block, "getDefaultState", "func_176223_P").invoke(value);
  }

  private static void position(Object entity, double x, double y, double z) throws Exception {
    method(
            entity.getClass(),
            new Class<?>[] {double.class, double.class, double.class},
            "setPosition",
            "func_70107_b")
        .invoke(entity, x, y, z);
  }

  private static double number(Object entity, String... names) throws Exception {
    return field(entity.getClass(), names).getDouble(entity);
  }

  private static void check(boolean condition, String message) {
    checks++;
    if (!condition) {
      failures.add(message);
      System.out.println("PERIHELION_SMOKE AUDIT_FINDING " + message);
    }
  }
}
