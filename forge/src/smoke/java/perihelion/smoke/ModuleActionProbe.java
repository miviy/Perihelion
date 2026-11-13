package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;
import static perihelion.smoke.RotationPipelineProbe.*;

import io.netty.channel.Channel;
import java.lang.reflect.*;
import java.util.*;
import perihelion.core.control.*;
import perihelion.core.event.EventOwner;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.HookBridge;

/** Real native bed digging, placements, chest/item use and prior-aim ownership regressions. */
public final class ModuleActionProbe {
  private static int checks, cases;
  private static final String[] IDS = {
    "breaker",
    "beddefender",
    "blockin",
    "chestaura",
    "autopot",
    "autoeat",
    "longjump",
    "autoextinguish",
    "noslow",
    "nofall"
  };

  static void run(
      ClientRuntime r,
      Object game,
      Object player,
      Object world,
      Object[] items,
      Method get,
      Method put,
      Channel channel,
      List<Object> packets,
      int x,
      int y,
      int z)
      throws Exception {
    checks = cases = 0;
    Map<Object, Object> states = new LinkedHashMap<>();
    Object inventory = field(player.getClass(), "inventory", "field_71071_by").get(player);
    Object settings = field(game.getClass(), "gameSettings", "field_71474_y").get(game);
    Object food = method(player.getClass(), "getFoodStats", "func_71024_bL").invoke(player);
    Field hunger = field(food.getClass(), "foodLevel", "field_75127_a");
    int oldHunger = hunger.getInt(food);
    Object capabilities = field(player.getClass(), "capabilities", "field_71075_bZ").get(player);
    Field immune = field(capabilities.getClass(), "disableDamage", "field_75102_a");
    boolean oldImmune = immune.getBoolean(capabilities);
    Field focus = field(game.getClass(), "inGameHasFocus", "field_71415_G");
    boolean oldFocus = focus.getBoolean(game);
    Map<Field, Object> savedPlayer = new LinkedHashMap<>();
    for (String[] names :
        new String[][] {
          {"motionX", "field_70159_w"},
          {"motionY", "field_70181_x"},
          {"motionZ", "field_70179_y"},
          {"onGround", "field_70122_E"},
          {"fallDistance", "field_70143_R"}
        }) {
      Field f = field(player.getClass(), names);
      savedPlayer.put(f, f.get(player));
    }
    try {
      for (String id : IDS) r.modules().setEnabled(id, false);
      focus.setBoolean(game, true);
      for (int dx = -5; dx <= 5; dx++)
        for (int dy = 0; dy <= 4; dy++)
          for (int dz = -5; dz <= 5; dz++) {
            Object p = pos(x + dx, y + dy, z + dz);
            states.put(p, get.invoke(world, p));
            put.invoke(world, p, state(dy == 0 ? "stone" : "air"), 2);
          }
      priorAim(r, player);
      clean(r, player, items, inventory, x + .5, y + 1, z + .5);
      Object raisedBed = pos(x + 1, y + 4, z + 1);
      put.invoke(world, raisedBed, state("bed"), 2);
      Method nearestBed =
          method(
              Class.forName("perihelion.integration.minecraft189.module.gameplay.BuildingModules"),
              new Class<?>[] {
                game.getClass(),
                double.class,
                Class.forName("net.minecraft.util.BlockPos"),
                boolean.class
              },
              "nearestBed");
      check(
          raisedBed.equals(nearestBed.invoke(null, game, 6., null, false)),
          "bed search finds reachable beds more than two levels above the player");
      check(
          nearestBed.invoke(null, game, 3., null, false) == null,
          "bed search keeps the configured distance bound");
      put.invoke(world, raisedBed, state("air"), 2);
      HomeBedProbe.run(r, game, player, world, items, get, put, channel, packets, x, y, z);
      Object bed = pos(x + 1, y + 1, z + 1);
      for (String mode : new String[] {"LEGIT", "VANILLA", "HYPIXEL"}) {
        clean(r, player, items, inventory, x + .5, y + 1, z + .5);
        put.invoke(world, bed, state("bed"), 2);
        items[3] = stack("shears");
        set(r, "breaker", "whitelist", "false");
        set(r, "breaker", "mode", mode);
        r.modules().setEnabled("breaker", true);
        boolean acted = until(r, player, channel, packets, "C07PacketPlayerDigging", 20, true);
        check(acted, "Breaker " + mode + " emits native digging");
        check(
            has(packets, "C0APacketAnimation"), "Breaker " + mode + " swings after submitted aim");
        check(
            !has(packets, "C08PacketPlayerBlockPlacement"),
            "Breaker does not place or use an item");
        check(
            field(inventory.getClass(), "currentItem", "field_70461_c").getInt(inventory) == 0,
            "Breaker preserves the visible tool slot");
        if (r.controls().rotation().isPresent()) movement(r, player, "Breaker " + mode);
        else
          check(
              !(Boolean)
                  method(r.modules().require("breaker").module().getClass(), "activeTask")
                      .invoke(r.modules().require("breaker").module()),
              "completed creative break releases its movement claim");
        r.modules().setEnabled("breaker", false);
        HookBridge.gameControls().apply();
        cases++;
      }
      put.invoke(world, bed, state("air"), 2);
      clean(r, player, items, inventory, x + .5, y + 1, z + .5);
      items[3] = stack("stone");
      r.modules().setEnabled("blockin", true);
      HookBridge.gameControls().beforeInput();
      check(!r.controls().rotation().isPresent(), "Block In respects its default creative guard");
      set(r, "blockin", "disable-in-creative", "false");
      check(
          until(r, player, channel, packets, "C08PacketPlayerBlockPlacement", 12),
          "Block In places in PRE");
      check(
          packets.stream()
                  .filter(p -> p.getClass().getSimpleName().equals("C0APacketAnimation"))
                  .count()
              == 1,
          "Block In emits one animation for its placement");
      movement(r, player, "Block In");
      r.modules().setEnabled("blockin", false);
      cases++;
      // Restore a clear shell so earlier enclosure blocks do not intercept bed/chest rays.
      clearAbove(world, put, x, y, z);
      // Yuri plans the first missing shell block, even when a later block is more visible.
      // View the default north-facing bed's first (west) target from its reachable side.
      clean(r, player, items, inventory, x - 1.5, y + 1, z + 1.5);
      items[3] = stack("wool");
      put.invoke(world, bed, state("bed"), 2);
      r.modules().setEnabled("beddefender", true);
      check(
          until(r, player, channel, packets, "C08PacketPlayerBlockPlacement", 12, true),
          "Bed Defender places after submitting its aim");
      movement(r, player, "Bed Defender");
      r.modules().setEnabled("beddefender", false);
      cases++;
      clearAbove(world, put, x, y, z);
      clean(r, player, items, inventory, x + .5, y + 1, z + .5);
      items[0] = stack("diamond_sword");
      Object chest = pos(x + 1, y + 1, z + 1);
      put.invoke(world, chest, state("chest"), 2);
      r.modules().setEnabled("chestaura", true);
      check(
          until(r, player, channel, packets, "C08PacketPlayerBlockPlacement", 12),
          "Chest Aura opens in PRE");
      movement(r, player, "Chest Aura");
      r.modules().setEnabled("chestaura", false);
      cases++;
      clearAbove(world, put, x, y, z);
      clean(r, player, items, inventory, x + .5, y + 1, z + .5);
      items[3] =
          method(QualityGameplayProbe.class, new Class<?>[] {int[].class}, "potion")
              .invoke(null, (Object) new int[] {1});
      r.modules().setEnabled("autopot", true);
      check(
          until(r, player, channel, packets, "C08PacketPlayerBlockPlacement", 18),
          "AutoPot throws in PRE");
      r.modules().setEnabled("autopot", false);
      cases++;
      clean(r, player, items, inventory, x + .5, y + 1, z + .5);
      items[3] = stack("bread");
      hunger.setInt(food, 10);
      immune.setBoolean(capabilities, false);
      r.modules().setEnabled("autoeat", true);
      check(
          until(r, player, channel, packets, "C08PacketPlayerBlockPlacement", 4),
          "Auto Eat starts in PRE");
      check(
          (Boolean) method(player.getClass(), "isUsingItem", "func_71039_bw").invoke(player),
          "Auto Eat actually starts native item use");
      r.modules().setEnabled("autoeat", false);
      cases++;
      noSlowOptions(r, player, items, inventory, channel, packets, x, y, z);
      clean(r, player, items, inventory, x + .5, y + 1, z + .5);
      items[3] = stack("fire_charge");
      set(r, "longjump", "fireball", "true");
      r.modules().setEnabled("longjump", true);
      check(
          until(r, player, channel, packets, "C08PacketPlayerBlockPlacement", 4),
          "Fireball Long Jump uses in PRE");
      set(r, "longjump", "fireball", "false");
      check(!r.controls().rotation().isPresent(), "Long Jump mode switch immediately releases aim");
      check(!r.controls().hotbar().isPresent(), "Long Jump mode switch immediately releases slot");
      HookBridge.gameControls().apply();
      check(
          field(inventory.getClass(), "currentItem", "field_70461_c").getInt(inventory) == 0,
          "Long Jump mode switch restores the visible slot");
      set(r, "longjump", "fireball", "true");
      check(
          until(r, player, channel, packets, "C08PacketPlayerBlockPlacement", 4),
          "Long Jump mode switch restarts the throw sequence");
      r.modules().setEnabled("longjump", false);
      cases++;
      clean(r, player, items, inventory, x + .5, y + 1, z + .5);
      items[3] = stack("water_bucket");
      Method flag =
          method(
              player.getClass(),
              new Class<?>[] {int.class, boolean.class},
              "setFlag",
              "func_70052_a");
      Method getFlag =
          method(player.getClass(), new Class<?>[] {int.class}, "getFlag", "func_70083_f");
      boolean burning = (Boolean) getFlag.invoke(player, 0);
      try {
        flag.invoke(player, 0, true);
        r.modules().setEnabled("autoextinguish", true);
        check(
            until(r, player, channel, packets, "C08PacketPlayerBlockPlacement", 9),
            "Auto Extinguish uses water in PRE");
        check(
            get.invoke(world, pos(x, y + 1, z)).equals(state("flowing_water")),
            "Auto Extinguish actually places water");
        cases++;
      } finally {
        r.modules().setEnabled("autoextinguish", false);
        flag.invoke(player, 0, burning);
        clearAbove(world, put, x, y, z);
      }
      clean(r, player, items, inventory, x + .5, y + 1, z + .5);
      items[3] = stack("bread");
      hunger.setInt(food, 10);
      immune.setBoolean(capabilities, false);
      field(player.getClass(), "fallDistance", "field_70143_R").setFloat(player, 4);
      set(r, "noslow", "mode", "NCP");
      set(r, "nofall", "mode", "VANILLA");
      r.modules().setEnabled("autoeat", true);
      r.modules().setEnabled("noslow", true);
      r.modules().setEnabled("nofall", true);
      check(
          until(r, player, channel, packets, "C08PacketPlayerBlockPlacement", 4),
          "item-use overlap still emits its use in PRE");
      int useIndex = index(packets, "C08PacketPlayerBlockPlacement");
      int releaseIndex = index(packets, "C07PacketPlayerDigging");
      int walkIndex = index(packets, "C03PacketPlayer");
      check(
          useIndex >= 0 && releaseIndex > useIndex && walkIndex > releaseIndex,
          "No Slow releases newly started item use before No Fall movement");
      cases++;
      System.out.println(
          "PERIHELION_SMOKE MODULE_ACTIONS checks="
              + checks
              + " cases="
              + cases
              + " breaker=afterWalking beddefender=submittedAim PASS");
    } finally {
      for (String id : IDS) {
        r.modules().setEnabled(id, false);
        for (Setting<?> s : r.modules().require(id).module().settings().values()) reset(r, id, s);
      }
      HookBridge.gameControls().apply();
      for (Map.Entry<Object, Object> e : states.entrySet())
        put.invoke(world, e.getKey(), e.getValue(), 2);
      hunger.setInt(food, oldHunger);
      immune.setBoolean(capabilities, oldImmune);
      focus.setBoolean(game, oldFocus);
      for (Map.Entry<Field, Object> e : savedPlayer.entrySet())
        e.getKey().set(player, e.getValue());
    }
  }

  private static void noSlowOptions(
      ClientRuntime r,
      Object player,
      Object[] items,
      Object inventory,
      Channel channel,
      List<Object> packets,
      int x,
      int y,
      int z)
      throws Exception {
    clean(r, player, items, inventory, x + .5, y + 1, z + .5);
    set(r, "noslow", "mode", "NCP");
    r.modules().setEnabled("noslow", true);
    String[][] types = {
      {"food-items", "bread"}, {"sword-items", "diamond_sword"},
      {"bow-items", "bow"}, {"potion-items", "potion"}
    };
    Class<?> stackType = Class.forName("net.minecraft.item.ItemStack");
    Method using =
        method(
            player.getClass(),
            new Class<?>[] {stackType, int.class},
            "setItemInUse",
            "func_71008_a");
    try {
      for (String[] type : types) {
        items[0] = stack(type[1]);
        using.invoke(player, items[0], 32);
        set(r, "noslow", type[0], "false");
        until(r, player, channel, packets, "C07PacketPlayerDigging", 1);
        Class<?> walking = Class.forName("net.minecraft.network.play.client.C03PacketPlayer");
        check(
            packets.stream().anyMatch(walking::isInstance), "No Slow test submits a walking pass");
        check(
            !has(packets, "C07PacketPlayerDigging"),
            "No Slow disabled " + type[0] + " preserves server item use");
        check(
            !r.clientEvents()
                .post(new perihelion.integration.minecraft189.event.ItemSlowdownEvent())
                .isCancelled(),
            "No Slow disabled " + type[0] + " preserves local slowdown");
        set(r, "noslow", type[0], "true");
        check(
            r.clientEvents()
                .post(new perihelion.integration.minecraft189.event.ItemSlowdownEvent())
                .isCancelled(),
            "No Slow enabled " + type[0] + " removes local slowdown");
        check(
            until(r, player, channel, packets, "C07PacketPlayerDigging", 1),
            "No Slow enabled " + type[0] + " releases in PRE");
      }
      cases++;
    } finally {
      r.modules().setEnabled("noslow", false);
      method(player.getClass(), "clearItemInUse", "func_71041_bz").invoke(player);
    }
  }

  private static void priorAim(ClientRuntime r, Object player) throws Exception {
    EventOwner owner =
        new EventOwner(
            "previous-aim-fixture",
            failure -> {
              throw new AssertionError(failure);
            });
    owner.activate();
    List<AutoCloseable> resources = new ArrayList<>();
    ControlRequest<Rotation> request = r.controls().scope(owner, resources::add).rotation(2000);
    try {
      Rotation sent = Rotation.server(23, 15), next = Rotation.server(90, 30);
      request.set(sent);
      HookBridge.gameControls().beforeInput();
      forceLook(player);
      motion(player);
      request.set(next);
      HookBridge.gameControls().beforeInput();
      float view = (float) number(player, "rotationYaw", "field_70177_z");
      check(
          HookBridge.gameControls()
              .withPreviousAim(
                  request,
                  previous -> {
                    check(
                        previous == sent && request.value().get() == sent,
                        "action uses immutable submitted aim");
                    check(
                        HookBridge.gameControls()
                            .withPreviousRotation(
                                request,
                                () -> {
                                  try {
                                    check(
                                        Math.abs(
                                                number(player, "rotationYaw", "field_70177_z") - 23)
                                            < 1e-5,
                                        "nested native direction scope uses previous yaw");
                                  } catch (Exception e) {
                                    throw new RuntimeException(e);
                                  }
                                }),
                        "previous direction scope is authorized");
                  }),
          "previous aim helper accepts genuine walking ownership");
      check(request.value().get() == next, "helper restores exact next desired value");
      check(number(player, "rotationYaw", "field_70177_z") == view, "helper restores camera");
      Rotation changed = Rotation.server(45, 40);
      HookBridge.gameControls().withPreviousAim(request, previous -> request.set(changed));
      check(request.value().get() == changed, "callback mutation is not overwritten");
      HookBridge.gameControls().withPreviousAim(request, previous -> request.clear());
      check(!request.value().isPresent(), "callback clear is not resurrected");
      request.set(next);
      HookBridge.gameControls().beforeInput();
      check(
          !HookBridge.gameControls()
              .withPreviousAim(
                  request,
                  previous -> {
                    throw new AssertionError();
                  }),
          "repeated input cannot borrow an already consumed walking window");
    } finally {
      for (AutoCloseable resource : resources) resource.close();
      owner.close();
      HookBridge.gameControls().apply();
    }
  }

  private static void movement(ClientRuntime r, Object p, String label) throws Exception {
    Field mx = field(p.getClass(), "motionX", "field_70159_w"),
        mz = field(p.getClass(), "motionZ", "field_70179_y");
    perihelion.integration.minecraft189.event.MovementInputEvent input =
        r.clientEvents()
            .post(new perihelion.integration.minecraft189.event.MovementInputEvent(0, .3f));
    check(
        (input.strafe() == 0 || Math.abs(input.strafe()) == .3f)
            && (input.forward() == 0 || Math.abs(input.forward()) == .3f)
            && Math.abs(Math.max(Math.abs(input.strafe()), Math.abs(input.forward())) - .3) < 1e-6,
        label + " chooses legal NORMAL input while preserving sneak magnitude");
    double yaw = Math.toRadians(r.controls().rotation().get().yaw());
    mx.setDouble(p, 0);
    mz.setDouble(p, 0);
    method(
            p.getClass(),
            new Class<?>[] {float.class, float.class, float.class},
            "moveFlying",
            "func_70060_a")
        .invoke(p, input.strafe(), input.forward(), .1f);
    check(
        Math.abs(
                    mx.getDouble(p)
                        - (input.strafe() * Math.cos(yaw) - input.forward() * Math.sin(yaw)) * .1)
                < 1e-5
            && Math.abs(
                    mz.getDouble(p)
                        - (input.forward() * Math.cos(yaw) + input.strafe() * Math.sin(yaw)) * .1)
                < 1e-5,
        label + " applies the corrected input basis once at movement physics");
    double jump = Math.toRadians(r.controls().rotation().get().yaw());
    mx.setDouble(p, 0);
    mz.setDouble(p, 0);
    method(p.getClass(), new Class<?>[] {boolean.class}, "setSprinting", "func_70031_b")
        .invoke(p, true);
    method(p.getClass(), "jump", "func_70664_aZ").invoke(p);
    check(
        Math.abs(mx.getDouble(p) + Math.sin(jump) * .2) < 1e-4
            && Math.abs(mz.getDouble(p) - Math.cos(jump) * .2) < 1e-4,
        label + " aligns actual sprint-jump boost");
  }

  private static boolean until(
      ClientRuntime r, Object p, Channel channel, List<Object> packets, String expected, int limit)
      throws Exception {
    return until(r, p, channel, packets, expected, limit, false);
  }

  private static boolean until(
      ClientRuntime r,
      Object p,
      Channel channel,
      List<Object> packets,
      String expected,
      int limit,
      boolean afterWalking)
      throws Exception {
    for (int tick = 0; tick < limit; tick++) {
      r.controls().beginTick();
      flush(channel);
      packets.clear();
      HookBridge.gameControls().beforeInput();
      forceLook(p);
      motion(p);
      flush(channel);
      interactionOrder(packets, afterWalking);
      if (has(packets, expected)) return true;
    }
    return false;
  }

  private static void interactionOrder(List<Object> packets, boolean afterWalking)
      throws Exception {
    Class<?> walking = Class.forName("net.minecraft.network.play.client.C03PacketPlayer");
    boolean after = false;
    for (Object p : packets) {
      if (walking.isInstance(p)) after = true;
      // A spoofed slot can be synchronized before walking, then restored after the action.
      // The submitted-angle contract constrains the interaction and animation packets.
      else if (afterWalking && p.getClass().getSimpleName().equals("C09PacketHeldItemChange"))
        continue;
      else if (p.getClass().getSimpleName().equals("C0APacketAnimation")
          || p.getClass().getSimpleName().equals("C07PacketPlayerDigging")
          || p.getClass().getSimpleName().equals("C08PacketPlayerBlockPlacement")
          || p.getClass().getSimpleName().equals("C09PacketHeldItemChange"))
        check(
            after == afterWalking,
            "native interaction/slot/animation "
                + (afterWalking ? "follows" : "precedes")
                + " walking: "
                + p.getClass().getSimpleName());
    }
  }

  private static void clean(
      ClientRuntime r, Object p, Object[] items, Object inventory, double x, double y, double z)
      throws Exception {
    for (String id : IDS) r.modules().setEnabled(id, false);
    HookBridge.gameControls().apply();
    Arrays.fill(items, null);
    field(inventory.getClass(), "currentItem", "field_70461_c").setInt(inventory, 0);
    method(p.getClass(), "clearItemInUse", "func_71041_bz").invoke(p);
    method(p.getClass(), new Class<?>[] {boolean.class}, "setSprinting", "func_70031_b")
        .invoke(p, false);
    position(p, x, y, z);
    camera(p, 150, -25);
    field(p.getClass(), "onGround", "field_70122_E").setBoolean(p, true);
    field(p.getClass(), "motionX", "field_70159_w").setDouble(p, 0);
    field(p.getClass(), "motionY", "field_70181_x").setDouble(p, 0);
    field(p.getClass(), "motionZ", "field_70179_y").setDouble(p, 0);
  }

  private static void clearAbove(Object world, Method put, int x, int y, int z) throws Exception {
    for (int dx = -5; dx <= 5; dx++)
      for (int dy = 1; dy <= 4; dy++)
        for (int dz = -5; dz <= 5; dz++)
          put.invoke(world, pos(x + dx, y + dy, z + dz), state("air"), 2);
  }

  private static boolean has(List<Object> packets, String name) {
    return packets.stream().anyMatch(p -> p.getClass().getSimpleName().equals(name));
  }

  private static int index(List<Object> packets, String name) {
    for (int i = 0; i < packets.size(); i++)
      if (packets.get(i).getClass().getSimpleName().equals(name)) return i;
    return -1;
  }

  private static <T> void reset(ClientRuntime r, String id, Setting<T> s) {
    r.modules().setSetting(id, s, s.defaultValue());
  }

  private static void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }
}
