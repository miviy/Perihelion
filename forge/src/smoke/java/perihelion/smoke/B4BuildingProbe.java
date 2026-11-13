package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;
import static perihelion.smoke.RotationPipelineProbe.*;

import java.lang.reflect.*;
import java.util.*;
import perihelion.core.control.*;
import perihelion.core.event.EventOwner;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleState;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.control.BlockBreakAccess;
import perihelion.integration.minecraft189.event.*;

/** Packaged OpenB4 building policies through transformed controllers, controls and PLAY packets. */
public final class B4BuildingProbe {
  private static int checks;

  public static void run(ClientRuntime r) throws Exception {
    checks = 0;
    Object game =
        method(Class.forName("net.minecraft.client.Minecraft"), "getMinecraft", "func_71410_x")
            .invoke(null);
    Object p = field(game.getClass(), "thePlayer", "field_71439_g").get(game),
        world = field(game.getClass(), "theWorld", "field_71441_e").get(game),
        inventory = field(p.getClass(), "inventory", "field_71071_by").get(p),
        controller = field(game.getClass(), "playerController", "field_71442_b").get(game),
        keys = field(game.getClass(), "gameSettings", "field_71474_y").get(game),
        attack = field(keys.getClass(), "keyBindAttack", "field_74312_F").get(keys);
    Object[]
        items =
            (Object[]) field(inventory.getClass(), "mainInventory", "field_70462_a").get(inventory),
        oldItems = items.clone();
    Field selected = field(inventory.getClass(), "currentItem", "field_70461_c"),
        screen = field(game.getClass(), "currentScreen", "field_71462_r"),
        focus = field(game.getClass(), "inGameHasFocus", "field_71415_G"),
        mouse = field(game.getClass(), "objectMouseOver", "field_71476_x"),
        pressed = field(attack.getClass(), "pressed", "field_74513_e"),
        type = field(controller.getClass(), "currentGameType", "field_78779_k");
    Object oldScreen = screen.get(game), oldMouse = mouse.get(game), oldType = type.get(controller);
    boolean oldFocus = focus.getBoolean(game), oldPressed = pressed.getBoolean(attack);
    Map<Object, Boolean> directionStates = new LinkedHashMap<>();
    for (String[] names : new String[][] {
      {"keyBindForward", "field_74351_w"}, {"keyBindBack", "field_74368_y"},
      {"keyBindLeft", "field_74370_x"}, {"keyBindRight", "field_74366_z"}
    }) {
      Object binding = field(keys.getClass(), names).get(keys);
      directionStates.put(binding, pressed.getBoolean(binding));
    }
    int oldSlot = selected.getInt(inventory);
    double px = number(p, "posX", "field_70165_t"),
        py = number(p, "posY", "field_70163_u"),
        pz = number(p, "posZ", "field_70161_v");
    Map<Field, Object> playerFields = new LinkedHashMap<>();
    for (String[] names :
        new String[][] {
          {"rotationYaw", "field_70177_z"}, {"rotationPitch", "field_70125_A"},
          {"lastReportedYaw", "field_175164_bL"}, {"lastReportedPitch", "field_175165_bM"},
          {"onGround", "field_70122_E"}, {"inWater", "field_70171_ac"},
          {"fallDistance", "field_70143_R"}, {"motionX", "field_70159_w"},
          {"motionY", "field_70181_x"}, {"motionZ", "field_70179_y"}
        }) {
      Field f = field(p.getClass(), names);
      playerFields.put(f, f.get(p));
    }
    Map<String, Boolean> enabled = new LinkedHashMap<>();
    Map<Setting<?>, Object> settings = new LinkedHashMap<>();
    for (String id :
        new String[] {
          "fastbreak",
          "fastplace",
          "safewalk",
          "nofall",
          "autoswap",
          "aura",
          "scaffold",
          "freecam",
          "eagle",
          "autopot",
          "autoprojectile"
        }) {
      enabled.put(id, r.modules().require(id).requestedEnabled());
      r.modules().setEnabled(id, false);
      for (Setting<?> s : r.modules().require(id).module().settings().values())
        settings.put(s, s.get());
    }
    Class<?> pos = Class.forName("net.minecraft.util.BlockPos"),
        state = Class.forName("net.minecraft.block.state.IBlockState");
    Method get = method(world.getClass(), new Class<?>[] {pos}, "getBlockState", "func_180495_p"),
        put =
            method(
                world.getClass(),
                new Class<?>[] {pos, state, int.class},
                "setBlockState",
                "func_180501_a"),
        setType =
            method(
                controller.getClass(),
                new Class<?>[] {oldType.getClass()},
                "setGameType",
                "func_78746_a");
    int x = (int) Math.floor(px), z = (int) Math.floor(pz), y = 200;
    Map<Object, Object> blocks = new LinkedHashMap<>();
    for (int a = -2; a <= 2; a++)
      for (int b = -2; b <= 2; b++)
        for (int h = -2; h <= 1; h++) {
          Object at = pos(x + a, y + h, z + b);
          blocks.put(at, get.invoke(world, at));
        }
    BlockBreakAccess breaking = (BlockBreakAccess) controller;
    int oldDelay = breaking.perihelion$hitDelay();
    float oldDamage = breaking.perihelion$damage();
    EventOwner owner =
        new EventOwner(
            "b4-building-probe",
            t -> {
              throw new AssertionError(t);
            });
    owner.activate();
    List<AutoCloseable> resources = new ArrayList<>();
    ControlRequest<Boolean> attackClaim =
        r.controls().scope(owner, resources::add).key(InputKey.ATTACK, 10000);
    try (GameplayLoopback peer = new GameplayLoopback(game)) {
      screen.set(game, null);
      focus.setBoolean(game, true);
      for (Object binding : directionStates.keySet()) pressed.setBoolean(binding, false);
      setType.invoke(controller, oldType.getClass().getField("SURVIVAL").get(null));
      Arrays.fill(items, null);
      selected.setInt(inventory, 0);
      for (Object at : blocks.keySet()) put.invoke(world, at, state("air"), 2);
      Object floor = pos(x, y - 1, z);
      put.invoke(world, floor, state("stone"), 2);
      position(p, x + .5, y, z + .5);
      field(p.getClass(), "onGround", "field_70122_E").setBoolean(p, true);
      field(p.getClass(), "inWater", "field_70171_ac").setBoolean(p, false);
      field(p.getClass(), "fallDistance", "field_70143_R").setFloat(p, 0);
      mouse.set(game, hit(floor, x + .5, y, z + .5));
      attackClaim.set(true);
      pressed.setBoolean(attack, true);
      set(r, "fastbreak", "progress-mode", "PRE");
      set(r, "fastbreak", "break-multiplier", "2");
      r.modules().setEnabled("fastbreak", true);
      breaking.perihelion$damage(.1F);
      breaking.perihelion$hitDelay(5);
      r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      check(
          breaking.perihelion$damage() == .5F && breaking.perihelion$hitDelay() == 0,
          "PRE seeds real controller progress and applies exact zero delay");
      set(r, "fastbreak", "progress-mode", "POST");
      breaking.perihelion$damage(.5F);
      r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      check(breaking.perihelion$damage() == 1, "POST completes at reciprocal threshold");
      breaking.perihelion$damage(.49F);
      r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      check(breaking.perihelion$damage() == .49F, "POST preserves subthreshold progress");
      attackClaim.clear();
      pressed.setBoolean(attack, false);
      breaking.perihelion$damage(.1F);
      r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      check(breaking.perihelion$damage() == .1F, "no held attack means no donor progress change");
      set(r, "fastbreak", "progress-mode", "INCREMENT");
      attackClaim.set(true);
      pressed.setBoolean(attack, true);
      breaking.perihelion$damage(.6F);
      r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      attackClaim.clear();
      pressed.setBoolean(attack, false);
      mouse.set(game, null);
      breaking.perihelion$hitDelay(5);
      r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      check(breaking.perihelion$hitDelay() == 0,
          "B4 mining clamps cooldown while looking at air, before another block is clicked");
      mouse.set(game, hit(floor, x + .5, y, z + .5));
      attackClaim.set(true);
      pressed.setBoolean(attack, true);
      breaking.perihelion$damage(.1F);
      r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      check(breaking.perihelion$damage() < .1F,
          "releasing attack over air clears the previous INCREMENT sample before the next block");

      ControlRequest<HotbarSelection> spoofTool =
          r.controls().scope(owner, resources::add).hotbar(10000);
      items[0] = stack("stick");
      items[3] = stack("diamond_pickaxe");
      selected.setInt(inventory, 0);
      spoofTool.set(HotbarSelection.spoof(3));
      HookBridge.gameControls().apply();
      Object blockState = get.invoke(world, floor);
      Object block = method(blockState.getClass(), "getBlock", "func_177230_c").invoke(blockState);
      float serverHardness = ((Number) method(HookBridge.gameControls().getClass(),
          new Class<?>[] {Class.forName("net.minecraft.block.Block"), Class.forName("net.minecraft.world.World"), pos},
          "miningStrength").invoke(HookBridge.gameControls(), block, world, floor)).floatValue();
      attackClaim.clear();
      pressed.setBoolean(attack, false);
      r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      attackClaim.set(true);
      pressed.setBoolean(attack, true);
      breaking.perihelion$damage(.1F);
      r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      check(selected.getInt(inventory) == 0 && serverHardness > .1F
              && Math.abs(breaking.perihelion$damage() - serverHardness * (2 - .2152857)) < 1E-6,
          "INCREMENT uses the same spoofed tool as vanilla mining while preserving the visible slot");
      spoofTool.clear();
      HookBridge.gameControls().apply();
      r.modules().setEnabled("fastbreak", false);
      breaking.perihelion$damage(.2F);
      HookBridge.gameControls().beforeInput();
      check(breaking.perihelion$damage() == .2F, "disabled mining listener is retired");

      items[0] = counted("stone", 2);
      items[4] = counted("cobblestone", 64);
      items[7] = counted("stone", 16);
      set(r, "fastplace", "block-refill", "SAME_TYPE");
      r.modules().setEnabled("fastplace", true);
      pre(r);
      size(items[0], 1);
      pre(r);
      check(selected.getInt(inventory) == 7, "manual FastPlace refill preserves block item type");
      r.modules().setEnabled("fastplace", false);
      HookBridge.gameControls().apply();
      check(
          selected.getInt(inventory) == 7 && !r.controls().hotbar().isPresent(),
          "refill commits the user's new building stack and releases its claim");
      selected.setInt(inventory, 0);
      size(items[0], 2);
      set(r, "fastplace", "block-refill", "ANY_BLOCK");
      r.modules().setEnabled("fastplace", true);
      pre(r);
      size(items[0], 1);
      pre(r);
      check(selected.getInt(inventory) == 4, "any-block refill selects the larger different stack");
      set(r, "fastplace", "block-refill", "OFF");
      selected.setInt(inventory, 0);
      pre(r);
      pre(r);
      check(selected.getInt(inventory) == 0, "OFF leaves a depleted stack selected");
      set(r, "fastplace", "block-refill", "ANY_BLOCK");
      size(items[0], 2);
      pre(r);
      selected.setInt(inventory, 1);
      pre(r);
      check(
          selected.getInt(inventory) == 1, "a manual hotbar change is not mistaken for depletion");
      r.modules().setEnabled("fastplace", false);
      HookBridge.gameControls().apply();

      selected.setInt(inventory, 0);
      size(items[0], 1);
      field(p.getClass(), "rotationPitch", "field_70125_A").setFloat(p, 90);
      set(r, "safewalk", "mode", "EDGE");
      r.modules().setEnabled("safewalk", true);
      SafeWalkEvent edge = new SafeWalkEvent();
      r.clientEvents().post(edge);
      check(edge.isCancelled(), "EDGE enables native clamp with the donor building conditions");
      field(p.getClass(), "rotationPitch", "field_70125_A").setFloat(p, 0);
      edge = new SafeWalkEvent();
      r.clientEvents().post(edge);
      check(!edge.isCancelled(), "EDGE respects the minimum view pitch");
      field(p.getClass(), "rotationPitch", "field_70125_A").setFloat(p, 90);
      set(r, "safewalk", "mode", "SNEAK");
      field(p.getClass(), "motionX", "field_70159_w").setDouble(p, .8);
      field(p.getClass(), "motionZ", "field_70179_y").setDouble(p, 0);
      HookBridge.gameControls().beforeInput();
      check(
          r.controls().key(InputKey.SNEAK).orElse(false),
          "SNEAK predicts unsupported next position before the player leaves the block");
      field(p.getClass(), "motionX", "field_70159_w").setDouble(p, 0);
      HookBridge.gameControls().beforeInput();
      check(!r.controls().key(InputKey.SNEAK).isPresent(), "supported prediction releases sneak");
      ControlRequest<Boolean> forwardClaim =
          r.controls().scope(owner, resources::add).key(InputKey.FORWARD, 10000);
      for (InputKey direction : new InputKey[] {InputKey.BACK, InputKey.LEFT, InputKey.RIGHT})
        r.controls().scope(owner, resources::add).key(direction, 10000).set(false);
      set(r, "safewalk", "edge-tolerance", "LOW");
      float inputSpeed = ((Number) method(p.getClass(), "getAIMoveSpeed", "func_70689_ay").invoke(p)).floatValue();
      check(inputSpeed > 0 && inputSpeed < 1, "fixture has a positive walking acceleration");
      // Place half an acceleration step inside the tolerance boundary. The real
      // player's movement-speed attribute determines that step, rather than an assumed .1.
      position(p, x + .5, y, z + 1 + .05 - inputSpeed * .98F * .9999998F / 2);
      field(p.getClass(), "rotationYaw", "field_70177_z").setFloat(p, 0);
      field(p.getClass(), "motionX", "field_70159_w").setDouble(p, 0);
      field(p.getClass(), "motionZ", "field_70179_y").setDouble(p, 0);
      Object forwardBinding = field(keys.getClass(), "keyBindForward", "field_74351_w").get(keys);
      // KeyBinding's sampled state is distinct from the physical hardware query.
      // Inactive windows intentionally bypass native claims, so seed the sampled
      // state as well to exercise this distinction deterministically in smoke.
      pressed.setBoolean(forwardBinding, true);
      forwardClaim.set(true);
      HookBridge.gameControls().beforeInput();
      check(r.controls().key(InputKey.SNEAK).orElse(false),
          "SNEAK predicts sampled forward input rather than querying the physical hardware key");
      forwardClaim.set(false);
      HookBridge.gameControls().beforeInput();
      check(r.controls().key(InputKey.SNEAK).orElse(false) == !org.lwjgl.opengl.Display.isActive(),
          "forward suppression applies when active; inactive windows retain vanilla sampled input");
      pressed.setBoolean(forwardBinding, false);
      forwardClaim.clear();
      position(p, x + .5, y, z + .5);
      r.modules().setEnabled("safewalk", false);
      check(!r.controls().key(InputKey.SNEAK).isPresent(), "disable retires sneak ownership");

      Arrays.fill(items, null);
      items[0] = stack("stick");
      items[3] = stack("diamond_pickaxe");
      selected.setInt(inventory, 0);
      attackClaim.set(true);
      pressed.setBoolean(attack, true);
      set(r, "autoswap", "swap-mode", "CLIENT");
      set(r, "autoswap", "sneak-only", "false");
      set(r, "autoswap", "tool-hover-delay", "3");
      set(r, "autoswap", "swap-back", "true");
      r.modules().setEnabled("autoswap", true);
      pre(r);
      attackClaim.clear();
      pressed.setBoolean(attack, false);
      mouse.set(game, null);
      pre(r);
      check(!r.controls().hotbar().isPresent(), "released attack pauses hover even when the ray leaves the block");
      attackClaim.set(true);
      pressed.setBoolean(attack, true);
      mouse.set(game, hit(floor, x + .5, y, z + .5));
      pre(r);
      check(!r.controls().hotbar().isPresent(), "tool hover waits for the third sample");
      pre(r);
      check(selected.getInt(inventory) == 3, "tool hover resumes at its donor count after an eligibility pause");
      set(r, "autoswap", "tool-hover-delay", "5");
      pre(r);
      check(selected.getInt(inventory) == 3 && r.controls().hotbar().isPresent(),
          "a longer hover wait preserves the activation's existing tool claim");
      screen.set(game, Class.forName("net.minecraft.client.gui.GuiChat").getConstructor().newInstance());
      pre(r);
      check(selected.getInt(inventory) == 0 && !r.controls().hotbar().isPresent(),
          "an ineligible GUI releases the tool claim while pausing the hover count");
      screen.set(game, null);
      pre(r);
      check(selected.getInt(inventory) == 3, "hover resumes after the GUI pause without starting over");
      mouse.set(game, null);
      pre(r);
      set(r, "autoswap", "tool-hover-delay", "3");
      mouse.set(game, hit(floor, x + .5, y, z + .5));
      pre(r);
      pre(r);
      check(!r.controls().hotbar().isPresent(), "an eligible missing block ray resets the hover count");
      pre(r);
      check(selected.getInt(inventory) == 3, "a reset hover count reaches its boundary again");

      set(r, "autoswap", "tool-hover-delay", "0");
      items[4] = stack("wooden_pickaxe");
      enchantEfficiency(items[4], 4);
      pre(r);
      check(selected.getInt(inventory) == 4,
          "Efficiency IV wooden pickaxe beats the faster-base unenchanted diamond pickaxe");
      items[5] = stack("diamond_shovel");
      put.invoke(world, floor, state("dirt"), 2);
      pre(r);
      check(selected.getInt(inventory) == 5,
          "Efficiency gives no tool-score bonus when base block strength is at most one");
      set(r, "autoswap", "skip-interactable", "true");
      put.invoke(world, floor, state("crafting_table"), 2);
      pre(r);
      check(
          !r.controls().hotbar().isPresent() && selected.getInt(inventory) == 0,
          "interactable gate releases and restores the tool slot");
      r.modules().setEnabled("autoswap", false);
      attackClaim.clear();
      pressed.setBoolean(attack, false);

      put.invoke(world, floor, state("air"), 2);
      Object landing = pos(x, y - 2, z), liquid = pos(x, y - 1, z);
      put.invoke(world, landing, state("stone"), 2);
      Arrays.fill(items, null);
      items[0] = stack("stick");
      items[3] = stack("water_bucket");
      selected.setInt(inventory, 0);
      position(p, x + .5, y, z + .5);
      field(p.getClass(), "onGround", "field_70122_E").setBoolean(p, false);
      field(p.getClass(), "fallDistance", "field_70143_R").setFloat(p, 4);
      field(p.getClass(), "rotationYaw", "field_70177_z").setFloat(p, 0);
      field(p.getClass(), "rotationPitch", "field_70125_A").setFloat(p, 90);
      set(r, "nofall", "mode", "MLG");
      set(r, "nofall", "pickup-water", "true");
      r.modules().setEnabled("nofall", true);
      for (int i = 0; i < 4 && !item(items[3], "bucket"); i++) {
        HookBridge.gameControls().beforeInput();
        forceLook(p);
        motion(p);
      }
      check(item(items[3], "bucket"), "MLG places actual water and records its empty bucket");
      check(
          get.invoke(world, liquid).equals(state("flowing_water")),
          "MLG placement changed the disposable world");
      position(p, x + .5, y - 1, z + .5);
      field(p.getClass(), "onGround", "field_70122_E").setBoolean(p, true);
      field(p.getClass(), "inWater", "field_70171_ac").setBoolean(p, true);
      field(p.getClass(), "fallDistance", "field_70143_R").setFloat(p, 0);
      // A real landing's position is sent by its walking pass. Teleporting this fixture
      // without that boundary must not authorize a use from the old airborne position.
      forceLook(p);
      motion(p);
      HookBridge.gameControls().beforeInput();
      check(item(items[3], "water_bucket"), "landing pickup refills the same owned bucket");
      check(get.invoke(world, liquid).equals(state("air")), "landing pickup removes source water");
      HookBridge.gameControls().beforeInput();
      check(!r.controls().hotbar().isPresent(), "pickup stage retires on the next input");
      r.modules().setEnabled("nofall", false);
      HookBridge.gameControls().apply();
      check(
          !r.controls().hotbar().isPresent() && !r.controls().rotation().isPresent(),
          "NoFall disable releases item and rotation claims");
      peer.client.eventLoop().submit(() -> {}).sync();
      check(peer.failure.get() == null, "building actions serialize through a real PLAY peer");
      for (String id : enabled.keySet())
        check(r.modules().require(id).state() != ModuleState.FAULTED, id + " did not fault");
      System.out.println("PERIHELION_SMOKE B4_BUILDING checks=" + checks + " PASS");
    } finally {
      for (String id : enabled.keySet()) r.modules().setEnabled(id, false);
      attackClaim.clear();
      for (AutoCloseable resource : resources) resource.close();
      owner.close();
      HookBridge.gameControls().apply();
      for (String id : enabled.keySet())
        for (Setting<?> s : r.modules().require(id).module().settings().values())
          restore(r, id, s, settings.get(s));
      for (Map.Entry<Object, Object> b : blocks.entrySet())
        put.invoke(world, b.getKey(), b.getValue(), 2);
      setType.invoke(controller, oldType);
      System.arraycopy(oldItems, 0, items, 0, items.length);
      selected.setInt(inventory, oldSlot);
      for (Map.Entry<Field, Object> f : playerFields.entrySet()) f.getKey().set(p, f.getValue());
      position(p, px, py, pz);
      breaking.perihelion$damage(oldDamage);
      breaking.perihelion$hitDelay(oldDelay);
      mouse.set(game, oldMouse);
      screen.set(game, oldScreen);
      focus.setBoolean(game, oldFocus);
      pressed.setBoolean(attack, oldPressed);
      for (Map.Entry<Object, Boolean> binding : directionStates.entrySet())
        pressed.setBoolean(binding.getKey(), binding.getValue());
      for (Map.Entry<String, Boolean> e : enabled.entrySet())
        r.modules().setEnabled(e.getKey(), e.getValue());
    }
  }

  private static void pre(ClientRuntime r) {
    r.clientEvents().post(PlayerControlEvent.BEFORE_MOTION);
    HookBridge.gameControls().apply();
  }

  private static Object counted(String id, int count) throws Exception {
    Object result = stack(id);
    size(result, count);
    return result;
  }

  private static void enchantEfficiency(Object stack, int level) throws Exception {
    Class<?> enchantment = Class.forName("net.minecraft.enchantment.Enchantment");
    Object efficiency = field(enchantment, "efficiency", "field_77349_p").get(null);
    method(stack.getClass(), new Class<?>[] {enchantment, int.class},
        "addEnchantment", "func_77966_a").invoke(stack, efficiency, level);
  }

  private static void size(Object stack, int count) throws Exception {
    field(stack.getClass(), "stackSize", "field_77994_a").setInt(stack, count);
  }

  private static boolean item(Object stack, String id) throws Exception {
    if (stack == null) return false;
    Object expected =
        method(
                Class.forName("net.minecraft.item.Item"),
                new Class<?>[] {String.class},
                "getByNameOrId",
                "func_111206_d")
            .invoke(null, id);
    return method(stack.getClass(), "getItem", "func_77973_b").invoke(stack) == expected;
  }

  private static Object hit(Object pos, double x, double y, double z) throws Exception {
    Class<?> vec = Class.forName("net.minecraft.util.Vec3"),
        facing = Class.forName("net.minecraft.util.EnumFacing");
    return Class.forName("net.minecraft.util.MovingObjectPosition")
        .getConstructor(vec, facing, pos.getClass())
        .newInstance(
            vec.getConstructor(double.class, double.class, double.class).newInstance(x, y, z),
            facing.getField("UP").get(null),
            pos);
  }

  @SuppressWarnings("unchecked")
  private static <T> void restore(ClientRuntime r, String id, Setting<T> s, Object value) {
    r.modules().setSetting(id, s, (T) value);
  }

  private static void check(boolean result, String message) {
    checks++;
    if (!result) throw new AssertionError(message);
  }
}
