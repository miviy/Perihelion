package perihelion.smoke;

import static perihelion.smoke.B4ManualCombatProbe.*;
import static perihelion.smoke.GameplayHookProbe.*;
import static perihelion.smoke.RotationPipelineProbe.*;

import java.lang.reflect.*;
import java.util.*;
import perihelion.core.control.*;
import perihelion.core.event.EventOwner;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.HookBridge;

/** Actual Bed Defender planning, rotation ownership, packet order and retirement. */
public final class BuildingAuditProbe {
  private static int checks;

  public static void run(ClientRuntime r) throws Exception {
    checks = 0;
    Object game =
        method(Class.forName("net.minecraft.client.Minecraft"), "getMinecraft", "func_71410_x")
            .invoke(null);
    Object p = field(game.getClass(), "thePlayer", "field_71439_g").get(game),
        world = field(game.getClass(), "theWorld", "field_71441_e").get(game),
        inv = field(p.getClass(), "inventory", "field_71071_by").get(p);
    Object[] items = (Object[]) field(inv.getClass(), "mainInventory", "field_70462_a").get(inv),
        oldItems = items.clone();
    Field selected = field(inv.getClass(), "currentItem", "field_70461_c"),
        screen = field(game.getClass(), "currentScreen", "field_71462_r"),
        focus = field(game.getClass(), "inGameHasFocus", "field_71415_G");
    int oldSlot = selected.getInt(inv);
    Object oldScreen = screen.get(game);
    boolean oldFocus = focus.getBoolean(game);
    double px = number(p, "posX", "field_70165_t"),
        py = number(p, "posY", "field_70163_u"),
        pz = number(p, "posZ", "field_70161_v");
    Map<Field, Object> playerState = new LinkedHashMap<>();
    for (String[] n :
        new String[][] {
          {"rotationYaw", "field_70177_z"},
          {"rotationPitch", "field_70125_A"},
          {"lastReportedYaw", "field_175164_bL"},
          {"lastReportedPitch", "field_175165_bM"}
        }) {
      Field f = field(p.getClass(), n);
      playerState.put(f, f.get(p));
    }
    Map<String, Boolean> enabled = new LinkedHashMap<>();
    for (String id :
        new String[] {
          "beddefender", "scaffold", "aura", "aimassist", "blockin", "clutch", "freecam"
        }) {
      enabled.put(id, r.modules().require(id).requestedEnabled());
      r.modules().setEnabled(id, false);
    }
    Map<Setting<?>, Object> saved = new LinkedHashMap<>();
    for (Setting<?> s : r.modules().require("beddefender").module().settings().values())
      saved.put(s, s.get());
    Class<?> bp = Class.forName("net.minecraft.util.BlockPos"),
        bs = Class.forName("net.minecraft.block.state.IBlockState");
    Method get = method(world.getClass(), new Class<?>[] {bp}, "getBlockState", "func_180495_p"),
        put =
            method(
                world.getClass(),
                new Class<?>[] {bp, bs, int.class},
                "setBlockState",
                "func_180501_a");
    int x = (int) Math.floor(px) + 20, y = 200, z = (int) Math.floor(pz);
    Map<Object, Object> blocks = new LinkedHashMap<>();
    for (int dx = -5; dx <= 3; dx++)
      for (int dy = -1; dy <= 4; dy++)
        for (int dz = -2; dz <= 4; dz++) {
          Object at = pos(x + dx, y + dy, z + dz);
          blocks.put(at, get.invoke(world, at));
          put.invoke(world, at, state("air"), 2);
        }
    EventOwner owner =
        new EventOwner(
            "building-audit",
            t -> {
              throw new AssertionError(t);
            });
    owner.activate();
    List<AutoCloseable> resources = new ArrayList<>();
    ControlRequest<Rotation> competing = r.controls().scope(owner, resources::add).rotation(2000);
    try (GameplayLoopback peer = new GameplayLoopback(game)) {
      screen.set(game, null);
      focus.setBoolean(game, true);
      Arrays.fill(items, null);
      items[0] = stack("wool");
      selected.setInt(inv, 4);
      position(p, x + 3.5, y, z + .5);
      camera(p, 0, 0);
      field(p.getClass(), "lastReportedYaw", "field_175164_bL").setFloat(p, 0);
      field(p.getClass(), "lastReportedPitch", "field_175165_bM").setFloat(p, 0);
      Object foot = pos(x, y, z), head = pos(x, y, z + 1);
      put.invoke(world, foot, stateMetadata("bed", 0), 2);
      put.invoke(world, head, stateMetadata("bed", 8), 2);
      for (int dx = -1; dx <= 1; dx++)
        for (int dz = -1; dz <= 2; dz++)
          put.invoke(world, pos(x + dx, y - 1, z + dz), state("stone"), 2);
      Class<?> helper =
          Class.forName("perihelion.integration.minecraft189.module.gameplay.BedDefensePlan");
      Method nearest = method(helper, new Class<?>[] {game.getClass()}, "nearest");
      check(foot.equals(nearest.invoke(null, game)), "Bed Defender finds the nearby bed foot");
      put.invoke(world, foot, state("air"), 2);
      put.invoke(world, head, state("air"), 2);
      put.invoke(world, pos(x, y + 3, z), stateMetadata("bed", 0), 2);
      check(
          nearest.invoke(null, game) == null,
          "bed search excludes beds more than two vertical blocks away");
      put.invoke(world, pos(x, y + 3, z), state("air"), 2);
      put.invoke(world, foot, stateMetadata("bed", 0), 2);
      put.invoke(world, head, stateMetadata("bed", 8), 2);
      set(r, "beddefender", "speed", "30");
      set(r, "beddefender", "place-delay", "0");
      r.modules().setEnabled("beddefender", true);
      HookBridge.gameControls().beforeInput();
      Rotation aim = r.controls().rotation().get();
      check(aim.pitch() > 0, "minimum Bed Defender speed acquires a downward placement aim");
      Object planned =
          method(
                  helper,
                  new Class<?>[] {game.getClass(), bp, boolean.class, double.class, Rotation.class},
                  "at")
              .invoke(null, game, pos(x + 1, y, z), false, 5d, Rotation.server(0, 0));
      Object eyes =
          method(p.getClass(), new Class<?>[] {float.class}, "getPositionEyes", "func_174824_e")
              .invoke(p, 1f);
      Rotation goal =
          (Rotation)
              method(helper, new Class<?>[] {eyes.getClass(), eyes.getClass()}, "angle")
                  .invoke(null, eyes, field(planned.getClass(), "hit").get(planned));
      check(
          Math.hypot(aim.yaw(), aim.pitch()) <= Math.hypot(goal.yaw(), goal.pitch()) + .3,
          "turn-minimizing planning does not exceed the old first-target turn");
      check(
          r.controls().hotbar().get().slot() == 0,
          "wool selection uses the owned native hotbar scope");
      check(
          !r.controls().key(InputKey.SNEAK).orElse(true),
          "first side target is not treated as a roof");
      peer.received.clear();
      competing.set(Rotation.server(160, 0));
      forceLook(p);
      motion(p);
      wireBoundary(peer);
      check(
          count(peer.received, "C08PacketPlayerBlockPlacement") == 0,
          "foreign rotation prevents a bed placement packet");
      competing.clear();
      HookBridge.gameControls().beforeInput();
      forceLook(p);
      motion(p);
      wireBoundary(peer);
      check(
          count(peer.received, "C08PacketPlayerBlockPlacement") == 1,
          "matching submitted aim permits exactly one bed placement");
      Object packet = null;
      for (Object value : peer.received)
        if (value.getClass().getSimpleName().equals("C08PacketPlayerBlockPlacement"))
          packet = value;
      check(
          packet != null
              && ((Number)
                          method(
                                  method(packet.getClass(), "getPosition", "func_179724_a")
                                      .invoke(packet)
                                      .getClass(),
                                  "getY",
                                  "func_177956_o")
                              .invoke(
                                  method(packet.getClass(), "getPosition", "func_179724_a")
                                      .invoke(packet)))
                      .intValue()
                  == y - 1,
          "nearest-turn side placement uses its floor support");
      r.modules().setEnabled("beddefender", false);
      HookBridge.gameControls().apply();
      wireBoundary(peer);
      check(
          !r.controls().rotation().isPresent()
              && !r.controls().hotbar().isPresent()
              && !r.controls().key(InputKey.SNEAK).isPresent(),
          "disable releases Bed Defender rotation, wool and sneak claims");
      screen.set(
          game,
          Class.forName("net.minecraft.client.gui.inventory.GuiInventory")
              .getConstructor(Class.forName("net.minecraft.entity.player.EntityPlayer"))
              .newInstance(p));
      peer.received.clear();
      r.modules().setEnabled("beddefender", true);
      HookBridge.gameControls().beforeInput();
      wireBoundary(peer);
      check(
          !r.controls().rotation().isPresent()
              && !r.controls().hotbar().isPresent()
              && !r.controls().key(InputKey.SNEAK).isPresent(),
          "an open inventory prevents Bed Defender's aim, wool and sneak claims");
      check(
          count(peer.received, "C09PacketHeldItemChange") == 0
              && count(peer.received, "C08PacketPlayerBlockPlacement") == 0,
          "an open inventory prevents Bed Defender slot and placement sends");
    } finally {
      r.modules().setEnabled("beddefender", false);
      competing.clear();
      for (AutoCloseable resource : resources) resource.close();
      owner.close();
      for (Map.Entry<Setting<?>, Object> e : saved.entrySet()) restore(e.getKey(), e.getValue());
      for (Map.Entry<Object, Object> e : blocks.entrySet())
        put.invoke(world, e.getKey(), e.getValue(), 2);
      System.arraycopy(oldItems, 0, items, 0, items.length);
      selected.setInt(inv, oldSlot);
      position(p, px, py, pz);
      for (Map.Entry<Field, Object> e : playerState.entrySet()) e.getKey().set(p, e.getValue());
      screen.set(game, oldScreen);
      focus.setBoolean(game, oldFocus);
      for (Map.Entry<String, Boolean> e : enabled.entrySet())
        r.modules().setEnabled(e.getKey(), e.getValue());
      HookBridge.gameControls().apply();
    }
    System.out.println("[PERIHELION_SMOKE] BUILDING_AUDIT PASS checks=" + checks);
  }

  @SuppressWarnings("unchecked")
  private static <T> void restore(Setting<T> s, Object v) {
    s.set((T) v);
  }

  private static void check(boolean pass, String detail) {
    checks++;
    if (!pass) throw new AssertionError(detail);
  }

  private static Object stateMetadata(String name, int metadata) throws Exception {
    Object block = method(state(name).getClass(), "getBlock", "func_177230_c").invoke(state(name));
    return method(block.getClass(), new Class<?>[] {int.class}, "getStateFromMeta", "func_176203_a")
        .invoke(block, metadata);
  }
}
