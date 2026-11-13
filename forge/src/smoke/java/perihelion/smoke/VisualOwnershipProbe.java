package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;
import static perihelion.smoke.RotationPipelineProbe.*;

import io.netty.channel.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import perihelion.core.control.InputKey;
import perihelion.core.event.EventOwner;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.event.*;

/** Actual Freecam cleanup and MinigameAim interaction ownership, using a controlled packet peer. */
public final class VisualOwnershipProbe {
  private static int checks;

  public static void run(ClientRuntime r) throws Exception {
    checks = 0;
    Object
        game =
            method(Class.forName("net.minecraft.client.Minecraft"), "getMinecraft", "func_71410_x")
                .invoke(null),
        world = field(game.getClass(), "theWorld", "field_71441_e").get(game),
        player = field(game.getClass(), "thePlayer", "field_71439_g").get(game);
    Class<?> entity = Class.forName("net.minecraft.entity.Entity"),
        worldType = Class.forName("net.minecraft.world.World");
    Method getView = method(game.getClass(), "getRenderViewEntity", "func_175606_aa"),
        setView =
            method(
                game.getClass(), new Class<?>[] {entity}, "setRenderViewEntity", "func_175607_a"),
        add =
            method(
                world.getClass(),
                new Class<?>[] {int.class, entity},
                "addEntityToWorld",
                "func_73027_a"),
        remove =
            method(
                world.getClass(),
                new Class<?>[] {int.class},
                "removeEntityFromWorld",
                "func_73028_b"),
        getHealth = method(player.getClass(), "getHealth", "func_110143_aJ"),
        setHealth =
            method(player.getClass(), new Class<?>[] {float.class}, "setHealth", "func_70606_j");
    Object oldView = getView.invoke(game);
    Field screen = field(game.getClass(), "currentScreen", "field_71462_r");
    Object oldScreen = screen.get(game);
    double x = number(player, "posX", "field_70165_t"),
        y = number(player, "posY", "field_70163_u"),
        z = number(player, "posZ", "field_70161_v");
    float oldYaw = field(player.getClass(), "rotationYaw", "field_70177_z").getFloat(player),
        oldPitch = field(player.getClass(), "rotationPitch", "field_70125_A").getFloat(player),
        health = ((Number) getHealth.invoke(player)).floatValue();
    Object inventory = field(player.getClass(), "inventory", "field_71071_by").get(player);
    Object[]
        items =
            (Object[]) field(inventory.getClass(), "mainInventory", "field_70462_a").get(inventory),
        savedItems = items.clone();
    Field selected = field(inventory.getClass(), "currentItem", "field_70461_c"),
        open = field(player.getClass(), "openContainer", "field_71070_bA");
    int oldSlot = selected.getInt(inventory);
    Object oldOpen = open.get(player);
    Method getCursor = method(inventory.getClass(), "getItemStack", "func_70445_o"),
        setCursor =
            method(
                inventory.getClass(),
                new Class<?>[] {Class.forName("net.minecraft.item.ItemStack")},
                "setItemStack",
                "func_70437_b");
    Object oldCursor = getCursor.invoke(inventory);
    Map<String, Boolean> enabled = new LinkedHashMap<>();
    for (String id :
        new String[] {
          "freecam",
          "minigameaim",
          "scaffold",
          "aura",
          "aimassist",
          "bowaimbot",
          "displace",
          "freelook",
          "autof5"
        }) enabled.put(id, r.modules().require(id).requestedEnabled());
    Map<Setting<?>, Object> settings = new LinkedHashMap<>();
    for (Setting<?> setting : r.modules().require("minigameaim").module().settings().values())
      settings.put(setting, setting.get());
    EventOwner observer =
        new EventOwner(
            "visual-ownership-probe",
            failure -> {
              throw new AssertionError(failure);
            });
    observer.activate();
    int[] uses = {0};
    r.clientEvents().subscribe(observer, LocalInteractionEvent.class, 10000, true, e -> uses[0]++);
    Object previous = remote(world, "PriorView"),
        replacement = remote(world, "ReplacementView"),
        other = remote(world, "OtherView");
    List<Object> outbound = new CopyOnWriteArrayList<>();
    try (GameplayLoopback peer = new GameplayLoopback(game)) {
      peer.client
          .pipeline()
          .addLast(
              "visual-ownership-packets",
              new ChannelOutboundHandlerAdapter() {
                @Override
                public void write(
                    ChannelHandlerContext context, Object packet, ChannelPromise promise)
                    throws Exception {
                  outbound.add(packet);
                  context.write(packet, promise);
                }
              });
      for (String id : enabled.keySet()) r.modules().setEnabled(id, false);
      screen.set(game, null);
      position(player, x, 200, z);
      add.invoke(world, -887790, previous);
      add.invoke(world, -887791, other);
      setView.invoke(game, previous);
      r.modules().setEnabled("freecam", true);
      check(
          getView.invoke(game) != previous && getView.invoke(game) != player,
          "Freecam owns a detached camera before cleanup");
      r.modules().setEnabled("freecam", false);
      check(getView.invoke(game) == previous, "a live registered previous render view is restored");

      r.modules().setEnabled("freecam", true);
      field(previous.getClass(), "isDead", "field_70128_L").setBoolean(previous, true);
      r.modules().setEnabled("freecam", false);
      check(
          getView.invoke(game) == player,
          "a dead previous view falls back to the current local player");
      field(previous.getClass(), "isDead", "field_70128_L").setBoolean(previous, false);
      setView.invoke(game, previous);
      r.modules().setEnabled("freecam", true);
      remove.invoke(world, -887790);
      field(previous.getClass(), "isDead", "field_70128_L").setBoolean(previous, false);
      r.modules().setEnabled("freecam", false);
      check(
          getView.invoke(game) == player,
          "an unloaded previous view cannot be restored even if its death flag is clear");

      add.invoke(world, -887790, previous);
      setView.invoke(game, previous);
      r.modules().setEnabled("freecam", true);
      add.invoke(world, -887790, replacement);
      field(previous.getClass(), "isDead", "field_70128_L").setBoolean(previous, false);
      r.modules().setEnabled("freecam", false);
      check(
          getView.invoke(game) == player,
          "a previous view replaced under the same entity ID falls back to local view");
      setView.invoke(game, replacement);
      r.modules().setEnabled("freecam", true);
      setView.invoke(game, other);
      r.modules().setEnabled("freecam", false);
      check(getView.invoke(game) == other, "cleanup leaves a camera it no longer owns unchanged");

      setView.invoke(game, player);
      Object
          zombie =
              Class.forName("net.minecraft.entity.monster.EntityZombie")
                  .getConstructor(worldType)
                  .newInstance(world),
          stand =
              Class.forName("net.minecraft.entity.item.EntityArmorStand")
                  .getConstructor(worldType)
                  .newInstance(world);
      position(zombie, x, 200, z + 3);
      position(stand, x + 1, 200, z);
      method(stand.getClass(), new Class<?>[] {String.class}, "setCustomNameTag", "func_96094_a")
          .invoke(stand, "Downed");
      add.invoke(world, -887792, zombie);
      add.invoke(world, -887793, stand);
      camera(player, 0, 0);
      for (String key : new String[] {"auto-heal", "auto-ammo"})
        set(r, "minigameaim", key, "false");
      set(r, "minigameaim", "auto-revive", "true");
      set(r, "minigameaim", "game", "ZOMBIE");
      set(r, "minigameaim", "aimbot-mode", "AUTO_FIRE");
      set(r, "minigameaim", "prediction-ticks", "0");
      set(r, "minigameaim", "fov", "180");
      r.modules().setEnabled("minigameaim", true);
      r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      check(
          r.controls().rotation().isPresent() && r.controls().key(InputKey.SNEAK).orElse(false),
          "the actual MinigameAim callback initially holds aim and revive claims");
      Arrays.fill(items, null);
      items[0] = stack("bow");
      items[9] = stack("golden_apple");
      items[10] = stack("arrow");
      selected.setInt(inventory, 0);
      setCursor.invoke(inventory, new Object[] {null});
      open.set(
          player, field(player.getClass(), "inventoryContainer", "field_71069_bz").get(player));
      setHealth.invoke(player, 2f);
      set(r, "minigameaim", "health", "3");
      set(r, "minigameaim", "auto-heal", "true");
      set(r, "minigameaim", "auto-ammo", "true");
      Object[] before = items.clone();
      int clicks = packets(outbound, "C0EPacketClickWindow"),
          placed = packets(outbound, "C08PacketPlayerBlockPlacement"),
          attempts = uses[0];
      r.modules().setEnabled("freecam", true);
      r.clientEvents().post(PlayerControlEvent.BEFORE_ACTIONS);
      r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      flush(peer.client);
      check(
          Arrays.equals(before, items),
          "Freecam blocks actual heal/ammo callbacks before any inventory mutation");
      check(
          packets(outbound, "C0EPacketClickWindow") == clicks
              && packets(outbound, "C08PacketPlayerBlockPlacement") == placed
              && uses[0] == attempts,
          "blocked callbacks emit no inventory clicks, item-use packet, or local use attempt");
      r.modules().setEnabled("freecam", false);
      check(
          !r.controls().rotation().isPresent() && !r.controls().key(InputKey.SNEAK).isPresent(),
          "releasing Freecam cannot revive stale MinigameAim aim/sneak claims");
      r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      flush(peer.client);
      check(
          items[1] != null && items[10] == null && items[9] != null,
          "heal/ammo support resumes after local interaction ownership is released");
      check(
          packets(outbound, "C0EPacketClickWindow") == clicks + 3
              && packets(outbound, "C08PacketPlayerBlockPlacement") == placed + 1
              && uses[0] == attempts + 1,
          "resumed support performs its two heal swaps, one ammo swap and one use attempt");

      items[1] = null;
      items[10] = stack("arrow");
      before = items.clone();
      clicks = packets(outbound, "C0EPacketClickWindow");
      attempts = uses[0];
      setView.invoke(game, other);
      r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      flush(peer.client);
      check(
          Arrays.equals(before, items)
              && packets(outbound, "C0EPacketClickWindow") == clicks
              && uses[0] == attempts,
          "another detached render view also blocks support without requiring the Freecam module"
              + " flag");
      System.out.println("PERIHELION_SMOKE VISUAL_OWNERSHIP checks=" + checks + " PASS");
    } finally {
      r.modules().setEnabled("minigameaim", false);
      r.modules().setEnabled("freecam", false);
      observer.close();
      for (int id = -887790; id >= -887793; id--) remove.invoke(world, id);
      setView.invoke(game, oldView);
      position(player, x, y, z);
      camera(player, oldYaw, oldPitch);
      setHealth.invoke(player, health);
      System.arraycopy(savedItems, 0, items, 0, items.length);
      selected.setInt(inventory, oldSlot);
      open.set(player, oldOpen);
      setCursor.invoke(inventory, oldCursor);
      screen.set(game, oldScreen);
      for (Map.Entry<Setting<?>, Object> setting : settings.entrySet())
        restore(r, setting.getKey(), setting.getValue());
      for (Map.Entry<String, Boolean> module : enabled.entrySet())
        r.modules().setEnabled(module.getKey(), module.getValue());
    }
  }

  private static Object remote(Object world, String name) throws Exception {
    Object profile =
        Class.forName("com.mojang.authlib.GameProfile")
            .getConstructor(UUID.class, String.class)
            .newInstance(UUID.randomUUID(), name);
    return Class.forName("net.minecraft.client.entity.EntityOtherPlayerMP")
        .getConstructor(Class.forName("net.minecraft.world.World"), profile.getClass())
        .newInstance(world, profile);
  }

  private static int packets(List<Object> packets, String name) {
    int count = 0;
    for (Object packet : packets) if (packet.getClass().getSimpleName().equals(name)) count++;
    return count;
  }

  @SuppressWarnings("unchecked")
  private static <T> void restore(ClientRuntime r, Setting<T> setting, Object value) {
    r.modules().setSetting("minigameaim", setting, (T) value);
  }

  private static void check(boolean condition, String message) {
    checks++;
    if (!condition) throw new AssertionError(message);
  }
}
