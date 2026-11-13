package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;

import java.lang.reflect.*;
import java.util.*;
import perihelion.core.control.*;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.*;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.*;

/** Regression checks requiring Forge's transformed Minecraft, not a plain JUnit classloader. */
public final class QualityGameplayProbe {
  private static final String PREFIX = "perihelion.integration.minecraft189.module.gameplay.";
  private static int checks;
  private static Class<?> stackType;

  public static void run(ClientRuntime r) throws Exception {
    checks = 0;
    stackType = Class.forName("net.minecraft.item.ItemStack");
    Object game =
        method(Class.forName("net.minecraft.client.Minecraft"), "getMinecraft", "func_71410_x")
            .invoke(null);
    Object player = field(game.getClass(), "thePlayer", "field_71439_g").get(game);
    Object world = field(game.getClass(), "theWorld", "field_71441_e").get(game);
    Object inv = field(player.getClass(), "inventory", "field_71071_by").get(player);
    Object[] items = (Object[]) field(inv.getClass(), "mainInventory", "field_70462_a").get(inv);
    Object[] saved = items.clone();
    Field screen = field(game.getClass(), "currentScreen", "field_71462_r");
    Object oldScreen = screen.get(game);
    Field focus = field(game.getClass(), "inGameHasFocus", "field_71415_G");
    boolean oldFocus = focus.getBoolean(game);
    Field yaw = field(player.getClass(), "rotationYaw", "field_70177_z");
    float oldYaw = yaw.getFloat(player);
    Field selected = field(inv.getClass(), "currentItem", "field_70461_c");
    int oldSelected = selected.getInt(inv);
    double x = field(player.getClass(), "posX", "field_70165_t").getDouble(player);
    double y = field(player.getClass(), "posY", "field_70163_u").getDouble(player);
    double z = field(player.getClass(), "posZ", "field_70161_v").getDouble(player);
    try {
      Arrays.fill(items, null);
      screen.set(game, null);
      focus.setBoolean(game, true);
      selected.setInt(inv, 0);
      policies();
      GhostCombatProbe.run(r, game, player, world);
      AssistParityProbe.run(r, game, player, world);
      HeadCosmeticsProbe.run(r, game, player);
      manager(r, game, player, items, screen);
      screen.set(game, null);
      bow(r, player, world, items, yaw, x, y, z);
      arcsAndPreview(r, player, world, items, x, y, z);
      pot(r, game, player, items, screen);
      scaffold(r, game, player, items);
      projectile(r, player, world, items, x, y, z);
      clutch(r, player, world, items, x, y, z);
    } finally {
      for (String id :
          new String[] {
            "manager",
            "bowaimbot",
            "autopot",
            "autoprojectile",
            "trajectories",
            "scaffold",
            "clutch"
          }) {
        r.modules().setEnabled(id, false);
        for (perihelion.core.setting.Setting<?> setting :
            r.modules().require(id).module().settings().values()) reset(r, id, setting);
      }
      method(player.getClass(), "clearItemInUse", "func_71041_bz").invoke(player);
      HookBridge.gameControls().apply();
      System.arraycopy(saved, 0, items, 0, items.length);
      selected.setInt(inv, oldSelected);
      screen.set(game, oldScreen);
      focus.setBoolean(game, oldFocus);
      yaw.setFloat(player, oldYaw);
      position(player, x, y, z);
    }
    System.out.println("PERIHELION_SMOKE QUALITY_GAMEPLAY checks=" + checks + " PASS");
  }

  private static void policies() throws Exception {
    Object protection = construct("InventoryProtection", new Class<?>[0]);
    Method configure =
        method(protection.getClass(), new Class<?>[] {String.class, String.class}, "configure");
    Method protects =
        method(
            protection.getClass(),
            new Class<?>[] {int.class, stackType, boolean.class},
            "protects");
    configure.invoke(protection, "1,10", "tnt,minecraft:compass");
    check((Boolean) protects.invoke(protection, 36, null, true), "empty hotbar protected");
    check((Boolean) protects.invoke(protection, 9, null, true), "storage slot protected");
    check(!(Boolean) protects.invoke(protection, 37, null, true), "other slots remain usable");
    check((Boolean) protects.invoke(protection, 38, stack("tnt"), true), "registry ID protected");
    Object named = stack("diamond_sword");
    method(stackType, new Class<?>[] {String.class}, "setStackDisplayName", "func_151001_c")
        .invoke(named, "Keep me");
    check((Boolean) protects.invoke(protection, 38, named, true), "custom named item protected");
    configure.invoke(protection, "", "");
    check(
        !(Boolean) protects.invoke(protection, 36, null, true),
        "protection updates without stale slots");
    Object potion = potion(6, 19);
    check(
        !(Boolean)
            method(
                    Class.forName(PREFIX + "InventoryPolicy"),
                    new Class<?>[] {stackType},
                    "goodPotion")
                .invoke(null, potion),
        "later harmful potion effect rejected");
    Object[] contents = (Object[]) Array.newInstance(stackType, 40);
    contents[0] = stack("iron_sword");
    contents[1] = stack("stone");
    field(stackType, "stackSize", "field_77994_a").setInt(contents[1], 64);
    contents[36] = stack("diamond_chestplate");
    Object loot = construct("LootPolicy", new Class<?>[] {contents.getClass()}, (Object) contents);
    Method priority =
        method(
            loot.getClass(),
            new Class<?>[] {stackType, int.class, int.class, int.class},
            "priority");
    check(
        (Double) priority.invoke(loot, stack("diamond_sword"), 64, 64, 64) > 1,
        "loot prioritizes upgrade");
    check(
        (Double) priority.invoke(loot, stack("wooden_sword"), 64, 64, 64) < 0,
        "loot leaves inferior weapon");
    check(
        (Double) priority.invoke(loot, stack("iron_chestplate"), 64, 64, 64) >= 200,
        "latest Yuri collects armor even with better equipped armor");
    check(
        (Double) priority.invoke(loot, stack("stone"), 64, 64, 64) < 0,
        "loot respects block threshold");
    contents[2] = stack("snowball");
    contents[3] = stack("egg");
    contents[4] = stack("arrow");
    field(stackType, "stackSize", "field_77994_a").setInt(contents[2], 16);
    field(stackType, "stackSize", "field_77994_a").setInt(contents[3], 16);
    field(stackType, "stackSize", "field_77994_a").setInt(contents[4], 32);
    loot = construct("LootPolicy", new Class<?>[] {contents.getClass()}, (Object) contents);
    check(
        (Double) priority.invoke(loot, stack("egg"), 64, 32, 32) < 0,
        "loot combines egg and snowball resource counts");
    check(
        (Double) priority.invoke(loot, stack("arrow"), 64, 32, 32) < 0,
        "loot respects exact arrow threshold");
    check(
        (Double) priority.invoke(loot, stack("arrow"), 64, 32, 33) > 0,
        "loot accepts resources below their threshold");
    contents[1] = contents[2] = contents[3] = contents[4] = null;
    loot = construct("LootPolicy", new Class<?>[] {contents.getClass()}, (Object) contents);
    check(
        (Double) priority.invoke(loot, stack("stone"), 64, 32, 32) > 0,
        "new loot snapshot refreshes counts after an inventory mutation");
    check(
        (Double) priority.invoke(loot, stack("egg"), 64, 0, 32) < 0,
        "zero projectile target leaves all projectiles alone");
    for (int i = 0; i < 36; i++) {
      contents[i] = stack("stone");
      field(stackType, "stackSize", "field_77994_a").setInt(contents[i], 64);
    }
    field(stackType, "stackSize", "field_77994_a").setInt(contents[0], 63);
    loot = construct("LootPolicy", new Class<?>[] {contents.getClass()}, (Object) contents);
    Method fits = method(loot.getClass(), new Class<?>[] {stackType}, "fits");
    check(
        (Boolean) fits.invoke(loot, stack("stone")), "full inventory still merges a partial stack");
    check(
        !(Boolean) fits.invoke(loot, stack("diamond_sword")),
        "full inventory rejects nonmergeable items");
  }

  private static void manager(
      ClientRuntime r, Object game, Object player, Object[] items, Field screen) throws Exception {
    Object manager = r.modules().require("manager").module();
    Method action = method(manager.getClass(), "action");
    for (String role :
        new String[] {
          "weapon",
          "pickaxe",
          "axe",
          "shovel",
          "bow",
          "block",
          "gapple",
          "potion",
          "clutch",
          "projectile"
        }) set(r, "manager", "manage-" + role, "false");
    screen.set(
        game,
        Class.forName("net.minecraft.client.gui.inventory.GuiInventory")
            .getConstructor(Class.forName("net.minecraft.entity.player.EntityPlayer"))
            .newInstance(player));
    items[0] = stack("diamond_sword");
    items[9] = stack("wooden_sword");
    items[10] = stack("tnt");
    items[11] = stack("crafting_table");
    check(
        !(Boolean) action.invoke(manager),
        "sorting-only defaults do not discard equipment or utility blocks");
    set(r, "manager", "drop-items", "true");
    check(
        !(Boolean) action.invoke(manager),
        "disabled equipment role remains protected from dropping");
    set(r, "manager", "manage-weapon", "true");
    set(r, "manager", "protected-slots", "10");
    check(!(Boolean) action.invoke(manager), "protected inventory slot survives inferior cleanup");
    set(r, "manager", "protected-slots", "\"\"");
    check(
        (Boolean) action.invoke(manager) && items[9] == null,
        "explicit inferior cleanup removes only inferior weapon");
    check(items[10] != null && items[11] != null, "utility blocks retained");
    Arrays.fill(items, null);
    items[9] = stack("diamond_sword");
    set(r, "manager", "protected-slots", "1");
    check(
        !(Boolean) action.invoke(manager) && items[9] != null && items[0] == null,
        "empty protected destination cannot be overwritten");
    set(r, "manager", "protected-slots", "\"\"");
  }

  private static void bow(
      ClientRuntime r,
      Object player,
      Object world,
      Object[] items,
      Field yaw,
      double x,
      double y,
      double z)
      throws Exception {
    position(player, x, 220, z);
    yaw.setFloat(player, 0);
    Object pig =
        Class.forName("net.minecraft.client.entity.EntityOtherPlayerMP")
            .getConstructor(
                Class.forName("net.minecraft.world.World"), com.mojang.authlib.GameProfile.class)
            .newInstance(
                world,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "BowFixture"));
    position(pig, x + 8, 220, z);
    for (String[] names :
        new String[][] {
          {"prevPosX", "field_70169_q"},
          {"prevPosY", "field_70167_r"},
          {"prevPosZ", "field_70166_s"}
        })
      field(pig.getClass(), names)
          .setDouble(pig, names[0].endsWith("X") ? x + 8 : names[0].endsWith("Y") ? 220 : z);
    method(
            world.getClass(),
            new Class<?>[] {int.class, Class.forName("net.minecraft.entity.Entity")},
            "addEntityToWorld",
            "func_73027_a")
        .invoke(world, -7654390, pig);
    try {
      Arrays.fill(items, null);
      items[0] = stack("bow");
      method(
              player.getClass(),
              new Class<?>[] {stackType, int.class},
              "setItemInUse",
              "func_71008_a")
          .invoke(player, items[0], 71980);
      set(r, "bowaimbot", "through-walls", "true");
      set(r, "bowaimbot", "predict", "false");
      r.modules().setEnabled("bowaimbot", true);
      for (int i = 1; i <= 8; i++) {
        r.controls().beginTick();
        r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
        check(r.controls().rotation().isPresent(), "bow supplies aim tick " + i);
        check(
            Math.abs(r.controls().rotation().get().yaw() + 90) < 2,
            "bow solves current donor view angle tick " + i);
      }
      check(
          yaw.getFloat(player) == 0,
          "bow request does not mutate view before shared controls apply");
      ModuleContext context =
          (ModuleContext) field(ModuleEntry.class, "context").get(r.modules().require("bowaimbot"));
      ControlRequest<Rotation> blocker = context.controls().rotation(1000);
      blocker.set(Rotation.server(0, 0));
      r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      check(blocker.isWinning(), "bow yields to higher-priority rotation");
      blocker.close();
      r.controls().beginTick();
      r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      check(
          Math.abs(r.controls().rotation().get().yaw() + 90) < 2,
          "bow reacquires native view ownership");

      r.modules().setEnabled("bowaimbot", false);
      check(!r.controls().rotation().isPresent(), "bow releases rotation");
    } finally {
      r.modules().setEnabled("bowaimbot", false);
      method(player.getClass(), "clearItemInUse", "func_71041_bz").invoke(player);
      method(world.getClass(), new Class<?>[] {int.class}, "removeEntityFromWorld", "func_73028_b")
          .invoke(world, -7654390);
      position(player, x, y, z);
    }
  }

  private static void arcsAndPreview(
      ClientRuntime r, Object player, Object world, Object[] items, double x, double y, double z)
      throws Exception {
    Class<?> vector = Class.forName("net.minecraft.util.Vec3");
    Object origin =
        vector.getConstructor(double.class, double.class, double.class).newInstance(x + 8, 221, z);
    Object bow = r.modules().require("bowaimbot").module();
    Method clear = method(bow.getClass(), new Class<?>[] {vector, double.class}, "solveFor");
    position(player, x, 220, z);
    set(r, "bowaimbot", "through-walls", "false");
    Class<?> posType = Class.forName("net.minecraft.util.BlockPos");
    Class<?> stateType = Class.forName("net.minecraft.block.state.IBlockState");
    Method get =
        method(world.getClass(), new Class<?>[] {posType}, "getBlockState", "func_180495_p");
    Method put =
        method(
            world.getClass(),
            new Class<?>[] {posType, stateType, int.class},
            "setBlockState",
            "func_180501_a");
    Object block =
        method(
                Class.forName("net.minecraft.block.Block"),
                new Class<?>[] {String.class},
                "getBlockFromName",
                "func_149684_b")
            .invoke(null, "stone");
    Object state = method(block.getClass(), "getDefaultState", "func_176223_P").invoke(block);
    Map<Object, Object> saved = new LinkedHashMap<>();
    Field ticks = field(player.getClass(), "ticksExisted", "field_70173_aa");
    int oldTicks = ticks.getInt(player);
    try {
      check(clear.invoke(bow, origin, 3d) != null, "bow solves unobstructed donor line of sight");
      for (int by = 220; by <= 222; by++) {
        Object pos =
            posType
                .getConstructor(int.class, int.class, int.class)
                .newInstance((int) Math.floor(x + 4), by, (int) Math.floor(z));
        saved.put(pos, get.invoke(world, pos));
        put.invoke(world, pos, state, 2);
      }
      check(clear.invoke(bow, origin, 3d) == null, "bow rejects donor straight-line obstruction");
      Arrays.fill(items, null);
      items[0] = stack("snowball");
      position(player, x, 220, z);
      r.modules().setEnabled("trajectories", true);
      perihelion.core.event.WorldRenderEvent event = new perihelion.core.event.WorldRenderEvent(1);
      r.clientEvents().post(event);
      long cached = System.nanoTime();
      for (int i = 0; i < 100; i++) r.clientEvents().post(event);
      cached = System.nanoTime() - cached;
      long refreshed = System.nanoTime();
      for (int i = 0; i < 100; i++) {
        ticks.setInt(player, oldTicks + i + 1);
        r.clientEvents().post(event);
      }
      refreshed = System.nanoTime() - refreshed;
      check(
          r.modules().require("trajectories").state() == ModuleState.ENABLED,
          "cached and refreshed trajectories stay healthy");
      System.out.println(
          "PERIHELION_SMOKE PERF trajectory100CachedFramesMs="
              + cached / 1e6
              + " trajectory100RefreshesMs="
              + refreshed / 1e6
              + " includesDrawing=true");
    } finally {
      r.modules().setEnabled("trajectories", false);
      ticks.setInt(player, oldTicks);
      for (Map.Entry<Object, Object> entry : saved.entrySet())
        put.invoke(world, entry.getKey(), entry.getValue(), 2);
      position(player, x, y, z);
    }
  }

  private static void pot(ClientRuntime r, Object game, Object player, Object[] items, Field screen)
      throws Exception {
    Object module = r.modules().require("autopot").module();
    Method useful = method(module.getClass(), new Class<?>[] {stackType}, "useful");
    check(!(Boolean) useful.invoke(module, potion(1, 19)), "AutoPot rejects mixed harmful potion");
    check((Boolean) useful.invoke(module, potion(1)), "AutoPot accepts missing useful buff");
    Arrays.fill(items, null);
    items[0] = potion(1);
    Field ground = field(player.getClass(), "onGround", "field_70122_E");
    boolean grounded = ground.getBoolean(player);
    try {
      ground.setBoolean(player, true);
      r.modules().setEnabled("autopot", true);
      for (int i = 0; i < 11; i++) r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      check(r.controls().rotation().isPresent(), "AutoPot arms a valid throw");
      screen.set(game, Class.forName("net.minecraft.client.gui.GuiChat").newInstance());
      motion(player);
      check(
          !r.controls().rotation().isPresent() && !r.controls().hotbar().isPresent(),
          "screen appearing cancels pending potion and releases claims");
    } finally {
      r.modules().setEnabled("autopot", false);
      screen.set(game, null);
      ground.setBoolean(player, grounded);
    }
  }

  private static void scaffold(ClientRuntime r, Object game, Object player, Object[] items)
      throws Exception {
    Arrays.fill(items, null);
    items[0] = stack("stone");
    items[1] = stack("stone");
    field(stackType, "stackSize", "field_77994_a").setInt(items[1], 64);
    Object module = r.modules().require("scaffold").module();
    set(r, "scaffold", "block-selection", "LARGEST");
    check(
        (Integer) method(module.getClass(), "selectBlocks").invoke(module) == 1,
        "Scaffold selects largest stack");
    set(r, "scaffold", "block-selection", "FIRST");
    check(
        (Integer) method(module.getClass(), "selectBlocks").invoke(module) == 0,
        "Scaffold preserves default selection");
    set(r, "scaffold", "holding-blocks", "true");
    items[0] = stack("diamond_sword");
    check(
        !(Boolean) method(module.getClass(), "conditionsMet").invoke(module),
        "Scaffold holding-block condition");
    set(r, "scaffold", "holding-blocks", "false");
  }

  private static void projectile(
      ClientRuntime r, Object player, Object world, Object[] items, double x, double y, double z)
      throws Exception {
    Object pig =
        Class.forName("net.minecraft.entity.passive.EntityPig")
            .getConstructor(Class.forName("net.minecraft.world.World"))
            .newInstance(world);
    position(pig, x + 4, y, z);
    field(pig.getClass(), "lastTickPosX", "field_70142_S").setDouble(pig, x + 4);
    field(pig.getClass(), "lastTickPosY", "field_70137_T").setDouble(pig, y);
    field(pig.getClass(), "lastTickPosZ", "field_70136_U").setDouble(pig, z);
    method(
            world.getClass(),
            new Class<?>[] {int.class, Class.forName("net.minecraft.entity.Entity")},
            "addEntityToWorld",
            "func_73027_a")
        .invoke(world, -7654391, pig);
    Object aura = r.modules().require("aura").module();
    Field target = field(aura.getClass(), "target");
    Object saved = target.get(aura);
    try {
      Arrays.fill(items, null);
      items[0] = stack("snowball");
      r.modules().setEnabled("aura", true);
      target.set(aura, pig);
      set(r, "autoprojectile", "line-of-sight", "false");
      r.modules().setEnabled("autoprojectile", true);
      r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      Object projectile = r.modules().require("autoprojectile").module();
      check(
          (Boolean) method(projectile.getClass(), "busy").invoke(projectile)
              && r.controls().rotation().isPresent()
              && !r.controls().hotbar().isPresent(),
          "Auto Projectile prepares an aim before selecting its throw slot");
      items[0] = stack("stone");
      motion(player);
      check(
          !r.controls().hotbar().isPresent() && !r.controls().rotation().isPresent(),
          "changed projectile stack cancels action and releases controls");
      r.modules().setEnabled("autoprojectile", false);
      Object module = r.modules().require("autoprojectile").module();
      perihelion.core.setting.Setting<?> setting =
          ((ClientModule) module).settings().get("target-range");
      check((Double) setting.parse("10") == 10, "projectile range matches latest Yuri setting");
    } finally {
      r.modules().setEnabled("autoprojectile", false);
      r.modules().setEnabled("aura", false);
      target.set(aura, saved);
      method(world.getClass(), new Class<?>[] {int.class}, "removeEntityFromWorld", "func_73028_b")
          .invoke(world, -7654391);
    }
  }

  private static void clutch(
      ClientRuntime r, Object player, Object world, Object[] items, double x, double y, double z)
      throws Exception {
    Object game =
        method(Class.forName("net.minecraft.client.Minecraft"), "getMinecraft", "func_71410_x")
            .invoke(null);
    Object settings = field(game.getClass(), "gameSettings", "field_71474_y").get(game);
    Object sneak = field(settings.getClass(), "keyBindSneak", "field_74311_E").get(settings);
    Field pressed = field(sneak.getClass(), "pressed", "field_74513_e");
    boolean savedPressed = pressed.getBoolean(sneak);
    Field motion = field(player.getClass(), "motionY", "field_70181_x");
    double oldMotion = motion.getDouble(player);
    Field viewYaw = field(player.getClass(), "rotationYaw", "field_70177_z"),
        viewPitch = field(player.getClass(), "rotationPitch", "field_70125_A");
    float savedViewYaw = viewYaw.getFloat(player), savedViewPitch = viewPitch.getFloat(player);
    Field mx = field(player.getClass(), "motionX", "field_70159_w"),
        mz = field(player.getClass(), "motionZ", "field_70179_y");
    double oldX = mx.getDouble(player), oldZ = mz.getDouble(player);
    Field ground = field(player.getClass(), "onGround", "field_70122_E");
    boolean oldGround = ground.getBoolean(player);
    Field ticks = field(player.getClass(), "ticksExisted", "field_70173_aa");
    int oldTicks = ticks.getInt(player);
    Class<?> posType = Class.forName("net.minecraft.util.BlockPos");
    Class<?> stateType = Class.forName("net.minecraft.block.state.IBlockState");
    int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
    Object support =
        posType.getConstructor(int.class, int.class, int.class).newInstance(bx + 1, 220, bz);
    Object target =
        posType.getConstructor(int.class, int.class, int.class).newInstance(bx, 220, bz);
    Method get =
        method(world.getClass(), new Class<?>[] {posType}, "getBlockState", "func_180495_p");
    Method put =
        method(
            world.getClass(),
            new Class<?>[] {posType, stateType, int.class},
            "setBlockState",
            "func_180501_a");
    Object oldSupport = get.invoke(world, support), oldTarget = get.invoke(world, target);
    Object block =
        method(
                Class.forName("net.minecraft.block.Block"),
                new Class<?>[] {String.class},
                "getBlockFromName",
                "func_149684_b")
            .invoke(null, "stone");
    Object state = method(block.getClass(), "getDefaultState", "func_176223_P").invoke(block);
    try {
      put.invoke(world, support, state, 2);
      position(player, bx + .5, 221, bz + .5);
      motion.setDouble(player, -.2);
      mx.setDouble(player, 0);
      mz.setDouble(player, 0);
      ground.setBoolean(player, false);
      Arrays.fill(items, null);
      items[0] = stack("stone");
      field(stackType, "stackSize", "field_77994_a").setInt(items[0], 16);
      ticks.setInt(player, 100);
      viewYaw.setFloat(player, 0);
      viewPitch.setFloat(player, 0);
      set(r, "clutch", "rotation-speed-min", "10");
      set(r, "clutch", "rotation-speed-max", "10");
      pressed.setBoolean(sneak, false);
      r.modules().setEnabled("clutch", true);
      r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      check(!r.controls().rotation().isPresent(), "Clutch waits for Sneak");
      ModuleContext clutchContext =
          (ModuleContext) field(ModuleEntry.class, "context").get(r.modules().require("clutch"));
      ControlRequest<Boolean> shift = clutchContext.controls().key(InputKey.SNEAK, 1000);
      shift.set(true);
      pressed.setBoolean(sneak, true);
      HookBridge.gameControls().apply();
      r.controls().beginTick();
      r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      check(
          r.controls().rotation().isPresent(), "Clutch finds reachable catch while holding Sneak");
      HookBridge.gameControls().apply();
      motion(player);
      HookBridge.gameControls().beforeInput();
      check(
          !get.invoke(world, target).equals(oldTarget),
          "Clutch commits a block using native strict placement");
      shift.clear();
      pressed.setBoolean(sneak, false);
      HookBridge.gameControls().apply();
      r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      check(
          !r.controls().rotation().isPresent() && !r.controls().hotbar().isPresent(),
          "Clutch releases claims when Sneak ends");
    } finally {
      r.modules().setEnabled("clutch", false);
      HookBridge.gameControls().apply();
      put.invoke(world, support, oldSupport, 2);
      put.invoke(world, target, oldTarget, 2);
      position(player, x, y, z);
      motion.setDouble(player, oldMotion);
      mx.setDouble(player, oldX);
      mz.setDouble(player, oldZ);
      ground.setBoolean(player, oldGround);
      ticks.setInt(player, oldTicks);
      pressed.setBoolean(sneak, savedPressed);
      viewYaw.setFloat(player, savedViewYaw);
      viewPitch.setFloat(player, savedViewPitch);
    }
  }

  private static Object potion(int... ids) throws Exception {
    Object stack = stack("potion");
    method(stackType, new Class<?>[] {int.class}, "setItemDamage", "func_77964_b")
        .invoke(stack, 16384);
    Class<?> compound = Class.forName("net.minecraft.nbt.NBTTagCompound"),
        list = Class.forName("net.minecraft.nbt.NBTTagList"),
        base = Class.forName("net.minecraft.nbt.NBTBase");
    Object data = compound.newInstance(), effects = list.newInstance();
    for (int id : ids) {
      Object effect = compound.newInstance();
      method(compound, new Class<?>[] {String.class, byte.class}, "setByte", "func_74774_a")
          .invoke(effect, "Id", (byte) id);
      method(compound, new Class<?>[] {String.class, int.class}, "setInteger", "func_74768_a")
          .invoke(effect, "Duration", 100);
      method(list, new Class<?>[] {base}, "appendTag", "func_74742_a").invoke(effects, effect);
    }
    method(compound, new Class<?>[] {String.class, base}, "setTag", "func_74782_a")
        .invoke(data, "CustomPotionEffects", effects);
    method(stackType, new Class<?>[] {compound}, "setTagCompound", "func_77982_d")
        .invoke(stack, data);
    return stack;
  }

  private static Object construct(String name, Class<?>[] types, Object... args) throws Exception {
    Constructor<?> c = Class.forName(PREFIX + name).getDeclaredConstructor(types);
    c.setAccessible(true);
    return c.newInstance(args);
  }

  private static void position(Object entity, double x, double y, double z) throws Exception {
    method(
            entity.getClass(),
            new Class<?>[] {double.class, double.class, double.class},
            "setPosition",
            "func_70107_b")
        .invoke(entity, x, y, z);
  }

  private static <T> void reset(ClientRuntime r, String id, perihelion.core.setting.Setting<T> s) {
    r.modules().setSetting(id, s, s.defaultValue());
  }

  private static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
    checks++;
  }
}
