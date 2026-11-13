package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;
import static perihelion.smoke.RotationPipelineProbe.*;

import java.lang.reflect.*;
import java.util.*;
import perihelion.core.control.*;
import perihelion.core.event.EventOwner;
import perihelion.core.event.WorldRenderEvent;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.event.PlayerControlEvent;

/** Exercises the actual target ray tests and rendered projectile path against a bounded fixture. */
public final class PredictionParityProbe {
  private static int checks;

  public static void run(ClientRuntime r) throws Exception {
    checks = 0;
    Object game =
        method(Class.forName("net.minecraft.client.Minecraft"), "getMinecraft", "func_71410_x")
            .invoke(null);
    Object world = field(game.getClass(), "theWorld", "field_71441_e").get(game);
    Object player = field(game.getClass(), "thePlayer", "field_71439_g").get(game);
    Class<?> worldType = Class.forName("net.minecraft.world.World"),
        entityType = Class.forName("net.minecraft.entity.Entity"),
        livingType = Class.forName("net.minecraft.entity.EntityLivingBase"),
        vecType = Class.forName("net.minecraft.util.Vec3"),
        posType = Class.forName("net.minecraft.util.BlockPos"),
        stateType = Class.forName("net.minecraft.block.state.IBlockState");
    Constructor<?> vector = vecType.getConstructor(double.class, double.class, double.class),
        blockPos = posType.getConstructor(int.class, int.class, int.class);
    Method
        getState =
            method(world.getClass(), new Class<?>[] {posType}, "getBlockState", "func_180495_p"),
        putState =
            method(
                world.getClass(),
                new Class<?>[] {posType, stateType, int.class},
                "setBlockState",
                "func_180501_a"),
        add =
            method(
                world.getClass(),
                new Class<?>[] {int.class, entityType},
                "addEntityToWorld",
                "func_73027_a"),
        remove =
            method(
                world.getClass(),
                new Class<?>[] {int.class},
                "removeEntityFromWorld",
                "func_73028_b");
    Object air = state("air", "field_150350_a"), stone = state("stone", "field_150348_b");
    Object inventory = field(player.getClass(), "inventory", "field_71071_by").get(player);
    Object[] items =
        (Object[]) field(inventory.getClass(), "mainInventory", "field_70462_a").get(inventory);
    Field slot = field(inventory.getClass(), "currentItem", "field_70461_c"),
        screen = field(game.getClass(), "currentScreen", "field_71462_r"),
        itemInUse = field(player.getClass(), "itemInUse", "field_71074_e"),
        itemCount = field(player.getClass(), "itemInUseCount", "field_71072_f");
    Object[] oldItems = items.clone();
    Object oldScreen = screen.get(game), oldUse = itemInUse.get(player);
    Field openContainer = field(player.getClass(), "openContainer", "field_71070_bA");
    Object oldContainer = openContainer.get(player);
    Method getCursor = method(inventory.getClass(), "getItemStack", "func_70445_o"),
        setCursor =
            method(
                inventory.getClass(),
                new Class<?>[] {Class.forName("net.minecraft.item.ItemStack")},
                "setItemStack",
                "func_70437_b");
    Object oldCursor = getCursor.invoke(inventory);
    int oldSlot = slot.getInt(inventory), oldCount = itemCount.getInt(player);
    Map<Field, Object> playerState = new LinkedHashMap<>();
    for (String[] names :
        new String[][] {
          {"posX", "field_70165_t"},
          {"posY", "field_70163_u"},
          {"posZ", "field_70161_v"},
          {"lastTickPosX", "field_70142_S"},
          {"lastTickPosY", "field_70137_T"},
          {"lastTickPosZ", "field_70136_U"},
          {"prevPosX", "field_70169_q"},
          {"prevPosY", "field_70167_r"},
          {"prevPosZ", "field_70166_s"},
          {"rotationYaw", "field_70177_z"},
          {"rotationPitch", "field_70125_A"},
          {"prevRotationYaw", "field_70126_B"},
          {"prevRotationPitch", "field_70127_C"}
        }) {
      Field f = field(player.getClass(), names);
      playerState.put(f, f.get(player));
    }
    Map<String, Boolean> enabled = new LinkedHashMap<>();
    for (String id :
        new String[] {
          "minigameaim",
          "trajectories",
          "scaffold",
          "freecam",
          "aimassist",
          "aura",
          "bowaimbot",
          "displace"
        }) enabled.put(id, r.modules().require(id).requestedEnabled());
    Map<Setting<?>, Object> settings = new LinkedHashMap<>();
    for (Setting<?> setting : r.modules().require("minigameaim").module().settings().values())
      settings.put(setting, setting.get());
    Map<Object, Object> blocks = new LinkedHashMap<>();
    int bx = (int) Math.floor(number(player, "posX", "field_70165_t")),
        bz = (int) Math.floor(number(player, "posZ", "field_70161_v"));
    double x = bx + .5, y = 200, z = bz + .5;
    Object target =
        Class.forName("net.minecraft.entity.monster.EntityZombie")
            .getConstructor(worldType)
            .newInstance(world);
    EventOwner owner =
        new EventOwner(
            "prediction-parity-probe",
            failure -> {
              throw new AssertionError(failure);
            });
    owner.activate();
    List<AutoCloseable> resources = new ArrayList<>();
    ControlRequest<Rotation> aim = r.controls().scope(owner, resources::add).rotation(10000);
    ControlRequest<Boolean> sneak =
        r.controls().scope(owner, resources::add).key(InputKey.SNEAK, 10000);
    try (GameplayLoopback peer = new GameplayLoopback(game)) {
      for (String id : enabled.keySet()) r.modules().setEnabled(id, false);
      screen.set(game, null);
      for (int dx = 0; dx <= 4; dx++)
        for (int dy = 199; dy <= 202; dy++)
          for (int dz = 0; dz <= 4; dz++) {
            Object pos = blockPos.newInstance(bx + dx, dy, bz + dz);
            blocks.put(pos, getState.invoke(world, pos));
            putState.invoke(world, pos, dy == 199 ? stone : air, 3);
          }
      position(player, x, y, z);
      position(target, x, y, z + 4);
      set(r, "minigameaim", "hitbox", "HITSCAN_HEAD");
      Object module = r.modules().require("minigameaim").module();
      Method hitPoint = method(module.getClass(), new Class<?>[] {livingType, vecType}, "hitPoint");
      Object zero = vector.newInstance(0, 0, 0), offset = vector.newInstance(4, 0, 0);
      Object point = hitPoint.invoke(module, target, zero);
      check(
          point != null && close(number(point, "xCoord", "field_72450_a"), x),
          "zero prediction retains the visible current candidate");
      point = hitPoint.invoke(module, target, offset);
      check(
          point != null && close(number(point, "xCoord", "field_72450_a"), x + 4),
          "nonzero prediction is applied exactly once to the candidate");
      wall(putState, world, blockPos, bx + 2, bz + 2, stone);
      check(
          hitPoint.invoke(module, target, zero) != null,
          "fixture current candidate stays visible beside predicted obstruction");
      check(
          hitPoint.invoke(module, target, offset) == null,
          "all predicted hitscan candidates behind the wall are rejected");
      wall(putState, world, blockPos, bx + 2, bz + 2, air);
      wall(putState, world, blockPos, bx, bz + 2, stone);
      check(
          hitPoint.invoke(module, target, zero) == null,
          "fixture current hitscan candidates are occluded");
      check(
          hitPoint.invoke(module, target, offset) != null,
          "a visible predicted candidate is accepted when the current point is occluded");
      wall(putState, world, blockPos, bx, bz + 2, air);

      // The production selection callback must aim at that returned point, without adding lead
      // again.
      add.invoke(world, -887799, target);
      field(target.getClass(), "lastTickPosX", "field_70142_S").setDouble(target, x - 1);
      field(target.getClass(), "lastTickPosY", "field_70137_T").setDouble(target, y);
      field(target.getClass(), "lastTickPosZ", "field_70136_U").setDouble(target, z + 4);
      for (String key : new String[] {"auto-heal", "auto-ammo", "auto-revive"})
        set(r, "minigameaim", key, "false");
      set(r, "minigameaim", "game", "ZOMBIE");
      set(r, "minigameaim", "hitbox", "HEAD");
      set(r, "minigameaim", "fov", "180");
      set(r, "minigameaim", "prediction-ticks", "2");
      set(r, "minigameaim", "prediction-scale", "1");
      float yaw = (float) -Math.toDegrees(Math.atan2(1, 4));
      double
          eye =
              ((Number) method(player.getClass(), "getEyeHeight", "func_70047_e").invoke(player))
                  .doubleValue(),
          targetEye =
              ((Number) method(target.getClass(), "getEyeHeight", "func_70047_e").invoke(target))
                  .doubleValue();
      float pitch = (float) -Math.toDegrees(Math.atan2(targetEye - eye, Math.sqrt(17)));
      camera(player, yaw, pitch);
      r.modules().setEnabled("minigameaim", true);
      r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      check(
          r.controls().rotation().isPresent()
              && Math.abs(r.controls().rotation().get().yaw() - yaw) < .001
              && Math.abs(r.controls().rotation().get().pitch() - pitch) < .001,
          "actual selection submits the single-lead angle rather than applying its offset twice");
      r.modules().setEnabled("minigameaim", false);

      Arrays.fill(items, null);
      items[0] = stack("bow");
      slot.setInt(inventory, 0);
      Object small = stack("arrow"), large = stack("arrow"), tie = stack("arrow");
      field(large.getClass(), "stackSize", "field_77994_a").setInt(large, 64);
      field(tie.getClass(), "stackSize", "field_77994_a").setInt(tie, 64);
      items[9] = small;
      items[10] = large;
      items[11] = tie;
      setCursor.invoke(inventory, new Object[] {null});
      openContainer.set(
          player, field(player.getClass(), "inventoryContainer", "field_71069_bz").get(player));
      set(r, "minigameaim", "auto-ammo", "true");
      Method support = method(module.getClass(), new Class<?>[] {ControlRequest.class}, "support");
      support.invoke(module, sneak);
      check(
          items[1] != null
              && method(items[1].getClass(), "getItem", "func_77973_b").invoke(items[1])
                  == method(large.getClass(), "getItem", "func_77973_b").invoke(large)
              && field(items[1].getClass(), "stackSize", "field_77994_a").getInt(items[1]) == 64
              && items[9] == small
              && items[10] == null
              && items[11] == tie,
          "Auto Ammo swaps the largest stack with stable first-slot ties through the actual"
              + " container");
      support.invoke(module, sneak);
      check(items[11] == tie && items[2] == null, "existing hotbar arrows prevent another refill");
      items[1] = null;
      setCursor.invoke(inventory, small);
      support.invoke(module, sneak);
      check(
          items[1] == null && items[11] == tie,
          "a carried cursor item blocks the ammo inventory action");
      setCursor.invoke(inventory, new Object[] {null});
      for (int i = 1; i < 9; i++) items[i] = stack("stone");
      support.invoke(module, sneak);
      check(items[11] == tie && items[1] != tie, "a full hotbar leaves inventory arrows untouched");
      items[1] = null;
      screen.set(game, Class.forName("net.minecraft.client.gui.GuiChat").newInstance());
      r.modules().setEnabled("minigameaim", true);
      r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      check(
          items[1] == null && items[11] == tie,
          "an open GUI blocks the actual MinigameAim support callback");
      r.modules().setEnabled("minigameaim", false);
      screen.set(game, null);

      items[0] = stack("snowball");
      slot.setInt(inventory, 0);
      position(player, x + 1, y, z);
      field(player.getClass(), "lastTickPosX", "field_70142_S").setDouble(player, x);
      field(player.getClass(), "lastTickPosY", "field_70137_T").setDouble(player, y);
      field(player.getClass(), "lastTickPosZ", "field_70136_U").setDouble(player, z);
      aim.set(Rotation.server(0, 0));
      r.modules().setEnabled("trajectories", true);
      Object trajectory = r.modules().require("trajectories").module();
      @SuppressWarnings("unchecked")
      List<Object> points = (List<Object>) field(trajectory.getClass(), "points").get(trajectory);
      r.clientEvents().post(new WorldRenderEvent(.25f));
      check(
          points.size() > 1
              && close(number(points.get(0), "xCoord", "field_72450_a"), x + .25 - .16),
          "launch origin interpolates the moving player at render partial .25");
      Object first = points.get(0);
      r.clientEvents().post(new WorldRenderEvent(.25f));
      check(
          points.get(0) == first,
          "unchanged rendered origin and aim retain the bounded cached path");
      r.clientEvents().post(new WorldRenderEvent(.75f));
      check(
          points.get(0) != first
              && close(number(points.get(0), "xCoord", "field_72450_a"), x + .75 - .16),
          "same-tick render movement invalidates the origin cache");

      position(player, x, y, z);
      field(player.getClass(), "lastTickPosX", "field_70142_S").setDouble(player, x);
      items[0] = stack("bow");
      method(
              player.getClass(),
              new Class<?>[] {Class.forName("net.minecraft.item.ItemStack"), int.class},
              "setItemInUse",
              "func_71008_a")
          .invoke(player, items[0], 71998);
      r.clientEvents().post(new WorldRenderEvent(0));
      check(points.isEmpty(), "two whole charge ticks remain below the donor minimum flight power");
      r.clientEvents().post(new WorldRenderEvent(.9f));
      check(
          points.size() > 1,
          "fractional charge crosses the donor preview threshold within the same tick");
      double charge = 2 + (double) .9f, expected = charge / 20 * (charge / 20 + 2);
      check(
          close(segmentLength(points.get(0), points.get(1)), expected),
          "first actual bow segment uses fractional launch speed");
      first = points.get(0);
      r.clientEvents().post(new WorldRenderEvent(.9f));
      check(
          points.get(0) == first, "unchanged fractional bow charge retains its cached flight path");
      r.clientEvents().post(new WorldRenderEvent(.95f));
      check(
          points.get(0) != first, "changed fractional bow charge invalidates the same-tick cache");
      r.modules().setEnabled("trajectories", false);
      check(points.isEmpty(), "disable discards the interpolated path and fractional-charge cache");
      r.modules().setEnabled("trajectories", true);
      r.clientEvents().post(new WorldRenderEvent(.95f));
      check(
          points.size() > 1 && points.get(0) != first, "reactivation rebuilds a clean flight path");
      items[0] = null;
      r.clientEvents().post(new WorldRenderEvent(.95f));
      check(points.isEmpty(), "removing the held projectile clears the rendered path");
      System.out.println("PERIHELION_SMOKE PREDICTION_PARITY checks=" + checks + " PASS");
    } finally {
      r.modules().setEnabled("minigameaim", false);
      r.modules().setEnabled("trajectories", false);
      aim.clear();
      for (AutoCloseable resource : resources) resource.close();
      owner.close();
      remove.invoke(world, -887799);
      for (Map.Entry<Object, Object> block : blocks.entrySet())
        putState.invoke(world, block.getKey(), block.getValue(), 3);
      for (Map.Entry<Field, Object> value : playerState.entrySet())
        value.getKey().set(player, value.getValue());
      position(
          player,
          number(player, "posX", "field_70165_t"),
          number(player, "posY", "field_70163_u"),
          number(player, "posZ", "field_70161_v"));
      System.arraycopy(oldItems, 0, items, 0, items.length);
      slot.setInt(inventory, oldSlot);
      openContainer.set(player, oldContainer);
      setCursor.invoke(inventory, oldCursor);
      itemInUse.set(player, oldUse);
      itemCount.setInt(player, oldCount);
      screen.set(game, oldScreen);
      for (Map.Entry<Setting<?>, Object> setting : settings.entrySet())
        restore(r, setting.getKey(), setting.getValue());
      for (Map.Entry<String, Boolean> module : enabled.entrySet())
        r.modules().setEnabled(module.getKey(), module.getValue());
    }
  }

  private static Object state(String name, String srg) throws Exception {
    Object block = field(Class.forName("net.minecraft.init.Blocks"), name, srg).get(null);
    return method(block.getClass(), "getDefaultState", "func_176223_P").invoke(block);
  }

  private static void wall(Method put, Object world, Constructor<?> pos, int x, int z, Object state)
      throws Exception {
    for (int y = 200; y <= 202; y++) put.invoke(world, pos.newInstance(x, y, z), state, 3);
  }

  private static double segmentLength(Object a, Object b) throws Exception {
    double x = number(b, "xCoord", "field_72450_a") - number(a, "xCoord", "field_72450_a"),
        y = number(b, "yCoord", "field_72448_b") - number(a, "yCoord", "field_72448_b"),
        z = number(b, "zCoord", "field_72449_c") - number(a, "zCoord", "field_72449_c");
    return Math.sqrt(x * x + y * y + z * z);
  }

  private static boolean close(double a, double b) {
    return Math.abs(a - b) < .00001;
  }

  @SuppressWarnings("unchecked")
  private static <T> void restore(ClientRuntime r, Setting<T> setting, Object value) {
    r.modules().setSetting("minigameaim", setting, (T) value);
  }

  private static void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }
}
