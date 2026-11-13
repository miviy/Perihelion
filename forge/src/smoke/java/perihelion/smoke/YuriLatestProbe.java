package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;
import static perihelion.smoke.RotationPipelineProbe.*;

import io.netty.channel.Channel;
import java.lang.reflect.*;
import java.util.*;
import perihelion.core.control.*;
import perihelion.core.event.ClientTickEvent;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.*;

/** Current-main regressions through shipped definitions and transformed game hooks. */
public final class YuriLatestProbe {
  private static int checks, syntheticStarts, syntheticEnds;

  public static int syntheticStarts() {
    return syntheticStarts;
  }

  public static int syntheticEnds() {
    return syntheticEnds;
  }

  private static void fixtureTick(ClientRuntime runtime, ClientTickEvent phase) {
    if (phase == ClientTickEvent.START) syntheticStarts++;
    else syntheticEnds++;
    runtime.clientEvents().post(phase);
  }

  private static <T> void reset(
      ClientRuntime r, String id, perihelion.core.setting.Setting<T> setting) {
    r.modules().setSetting(id, setting, setting.defaultValue());
  }

  private static void verify(boolean value, String label) {
    if (!value) throw new AssertionError("Yuri latest: " + label);
    checks++;
  }

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
    checks = syntheticStarts = syntheticEnds = 0;
    Object inv = field(player.getClass(), "inventory", "field_71071_by").get(player);
    Object settings = field(game.getClass(), "gameSettings", "field_71474_y").get(game);
    Field selected = field(inv.getClass(), "currentItem", "field_70461_c");
    Field perspective = field(settings.getClass(), "thirdPersonView", "field_74320_O");
    Field hurt = field(player.getClass(), "hurtTime", "field_70737_aN");
    Field screen = field(game.getClass(), "currentScreen", "field_71462_r");
    Object[] saved = items.clone();
    int oldSlot = selected.getInt(inv),
        oldPerspective = perspective.getInt(settings),
        oldHurt = hurt.getInt(player);
    Object oldScreen = screen.get(game);
    Object pig =
        Class.forName("net.minecraft.entity.passive.EntityPig")
            .getConstructor(Class.forName("net.minecraft.world.World"))
            .newInstance(world);
    position(player, x + .5, y + 1, z + .5);
    position(pig, x + 4.5, y + 1, z + .5);
    field(pig.getClass(), "lastTickPosX", "field_70142_S").setDouble(pig, x + 4.5);
    field(pig.getClass(), "lastTickPosY", "field_70137_T").setDouble(pig, y + 1);
    field(pig.getClass(), "lastTickPosZ", "field_70136_U").setDouble(pig, z + .5);
    method(
            world.getClass(),
            new Class<?>[] {int.class, Class.forName("net.minecraft.entity.Entity")},
            "addEntityToWorld",
            "func_73027_a")
        .invoke(world, -9876123, pig);
    try {
      for (String id : new String[] {"aura", "autoprojectile", "hitselect", "derp"})
        r.modules().setEnabled(id, false);
      Arrays.fill(items, null);
      items[0] = stack("diamond_sword");
      items[1] = stack("snowball");
      selected.setInt(inv, 0);
      screen.set(game, null);
      hurt.setInt(player, 0);
      set(r, "aura", "targets", "ANIMALS");
      set(r, "aura", "auto-disable", "false");
      set(r, "aura", "auto-block", "NONE");
      set(r, "aura", "simulate-mouse-clicks", "false");
      set(r, "aura", "ray-cast", "false");
      r.modules().setEnabled("aura", true);
      Object aura = r.modules().require("aura").module();
      field(aura.getClass(), "target").set(aura, pig);
      field(aura.getClass(), "nextAttack").setDouble(aura, System.nanoTime() / 1e6 + 100000);

      r.modules().setEnabled("hitselect", true);
      Constructor<?> attack =
          PlayerAttackEvent.class.getConstructor(
              Class.forName("net.minecraft.entity.EntityLivingBase"));
      Object hitSelect = r.modules().require("hitselect").module();
      set(r, "hitselect", "counter-hit", "false");
      set(r, "hitselect", "trade-timeout", "0");
      set(r, "hitselect", "search-range", "6");
      Field targetHurt = field(pig.getClass(), "hurtTime", "field_70737_aN");
      targetHurt.setInt(pig, 4);
      fixtureTick(r, ClientTickEvent.END);
      verify(
          field(hitSelect.getClass(), "allowClick").getBoolean(hitSelect),
          "Hit Select does not evaluate at tick END");
      fixtureTick(r, ClientTickEvent.START);
      verify(
          !field(hitSelect.getClass(), "allowClick").getBoolean(hitSelect),
          "Hit Select evaluates at tick START before player input");
      targetHurt.setInt(pig, 0);
      fixtureTick(r, ClientTickEvent.START);
      verify(
          !((PlayerAttackEvent) r.clientEvents().post(attack.newInstance(pig))).isCancelled(),
          "unhurt target establishes current attack damage");
      targetHurt.setInt(pig, 4);
      fixtureTick(r, ClientTickEvent.START);
      verify(
          ((PlayerAttackEvent) r.clientEvents().post(attack.newInstance(pig))).isCancelled(),
          "same damage is held above configured hurt time");
      targetHurt.setInt(pig, 3);
      fixtureTick(r, ClientTickEvent.START);
      verify(
          !((PlayerAttackEvent) r.clientEvents().post(attack.newInstance(pig))).isCancelled(),
          "hurt time boundary permits attack");
      targetHurt.setInt(pig, 4);
      fixtureTick(r, ClientTickEvent.START);
      items[0] = stack("wooden_sword");
      verify(
          ((PlayerAttackEvent) r.clientEvents().post(attack.newInstance(pig))).isCancelled(),
          "unupgraded damage does not bypass the hold");
      r.modules().setEnabled("hitselect", false);
      r.modules().setEnabled("hitselect", true);
      verify(
          !((PlayerAttackEvent) r.clientEvents().post(attack.newInstance(pig))).isCancelled(),
          "new activation forgets the preceding hold");
      fixtureTick(r, ClientTickEvent.START);
      items[0] = stack("diamond_sword");
      Class<?> enchantment = Class.forName("net.minecraft.enchantment.Enchantment");
      Object sharpness =
          method(enchantment, new Class<?>[] {int.class}, "getEnchantmentById", "func_180306_c")
              .invoke(null, 16);
      method(
              items[0].getClass(),
              new Class<?>[] {enchantment, int.class},
              "addEnchantment",
              "func_77966_a")
          .invoke(items[0], sharpness, 1);
      verify(
          !((PlayerAttackEvent) r.clientEvents().post(attack.newInstance(pig))).isCancelled(),
          "increased enchantment damage bypasses a held trade once");
      verify(
          ((PlayerAttackEvent) r.clientEvents().post(attack.newInstance(pig))).isCancelled(),
          "the same upgraded damage cannot repeatedly bypass the hold");
      r.modules().setEnabled("hitselect", false);
      targetHurt.setInt(pig, 0);

      set(r, "autoprojectile", "line-of-sight", "false");
      r.modules().setEnabled("autoprojectile", true);
      HookBridge.gameControls().beforeInput();
      verify(
          !r.controls().hotbar().isPresent() && r.controls().rotation().isPresent(),
          "projectile prepares its aim before claiming a slot");
      verify(
          (Boolean)
              method(r.modules().require("autoprojectile").module().getClass(), "busy")
                  .invoke(r.modules().require("autoprojectile").module()),
          "projectile suspends combat");
      verify(
          !((Boolean) method(aura.getClass(), "attackEligible").invoke(aura)),
          "Aura and Auto Swap yield to projectile");
      miningSwap(r, game, player, world, items, x, y, z, true);
      verify(
          ((PlayerAttackEvent) r.clientEvents().post(attack.newInstance(pig))).isCancelled(),
          "manual attacks held during throw");
      Class<?> packet = Class.forName("net.minecraft.network.Packet");
      Class<?> stackType = Class.forName("net.minecraft.item.ItemStack");
      Object swordUse =
          Class.forName("net.minecraft.network.play.client.C08PacketPlayerBlockPlacement")
              .getConstructor(stackType)
              .newInstance(items[0]);
      PacketSendEvent useEvent =
          (PacketSendEvent) PacketSendEvent.class.getConstructor(packet).newInstance(swordUse);
      r.networkEvents().post(useEvent);
      verify(useEvent.isCancelled(), "foreign sword block suppressed during throw");
      flush(channel);
      packets.clear();
      forceLook(player);
      motion(player);
      flush(channel);
      HookBridge.gameControls().beforeInput();
      forceLook(player);
      motion(player);
      flush(channel);
      boolean projectileSent = false;
      for (Object sent : packets) {
        if (!sent.getClass().getSimpleName().equals("C08PacketPlayerBlockPlacement")) continue;
        Object held = method(sent.getClass(), "getStack", "func_149574_g").invoke(sent);
        if (held == null) continue;
        Object item = method(held.getClass(), "getItem", "func_77973_b").invoke(held);
        if (item.getClass().getSimpleName().equals("ItemSnowball")) projectileSent = true;
      }
      verify(projectileSent, "actual projectile packet emitted");
      verify(
          r.controls().hotbar().isPresent() && r.controls().hotbar().get().slot() == 1,
          "projectile claims the throw slot only for execution and recovery");
      Rotation walking = null;
      for (Object sent : packets) {
        if (sent.getClass().getSimpleName().equals("C05PacketPlayerLook")
            || sent.getClass().getSimpleName().equals("C06PacketPlayerPosLook")) {
          walking =
              Rotation.serverUnwrapped(
                  ((Number) method(sent.getClass(), "getYaw", "func_149462_g").invoke(sent))
                      .floatValue(),
                  ((Number) method(sent.getClass(), "getPitch", "func_149470_h").invoke(sent))
                      .floatValue());
          break;
        }
      }
      verify(walking != null, "throw has preceding walking rotation");
      ordered(packets, "C08PacketPlayerBlockPlacement", walking, "latest Auto Projectile");
      HookBridge.gameControls().beforeInput();
      verify(r.controls().hotbar().isPresent(), "snowball slot retained for first recovery tick");
      HookBridge.gameControls().beforeInput();
      verify(!r.controls().hotbar().isPresent(), "slot restored after second recovery tick");
      HookBridge.gameControls().beforeInput();
      verify(
          !((Boolean)
              method(r.modules().require("autoprojectile").module().getClass(), "busy")
                  .invoke(r.modules().require("autoprojectile").module())),
          "cooldown releases combat suppression");
      r.modules().setEnabled("autoprojectile", false);

      miningSwap(r, game, player, world, items, x, y, z, false);

      // A blocked sword must be released on a preceding walking interval.
      selected.setInt(inv, 0);
      items[1] = stack("snowball");
      method(
              player.getClass(),
              new Class<?>[] {stackType, int.class},
              "setItemInUse",
              "func_71008_a")
          .invoke(player, items[0], 72000);
      r.modules().setEnabled("autoprojectile", true);
      field(aura.getClass(), "target").set(aura, pig);
      flush(channel);
      packets.clear();
      HookBridge.gameControls().beforeInput();
      verify(!r.controls().hotbar().isPresent(), "blocked sword defers slot change");
      verify(
          !((Boolean) method(player.getClass(), "isUsingItem", "func_71039_bw").invoke(player)),
          "pre-release clears local sword use");
      forceLook(player);
      motion(player);
      flush(channel);
      verify(
          packets.stream()
              .anyMatch(p -> p.getClass().getSimpleName().equals("C07PacketPlayerDigging")),
          "pre-release emits sword release");
      verify(
          packets.stream()
              .noneMatch(p -> p.getClass().getSimpleName().equals("C08PacketPlayerBlockPlacement")),
          "release interval contains no projectile throw");
      HookBridge.gameControls().beforeInput();
      verify(r.controls().hotbar().isPresent(), "following interval prepares projectile");
      screen.set(game, Class.forName("net.minecraft.client.gui.GuiChat").newInstance());
      HookBridge.gameControls().beforeInput();
      motion(player);
      verify(
          !r.controls().hotbar().isPresent() && !r.controls().rotation().isPresent(),
          "menu input retires completed throw recovery claims");
      verify(
          !((Boolean)
              method(r.modules().require("autoprojectile").module().getClass(), "busy")
                  .invoke(r.modules().require("autoprojectile").module())),
          "menu interrupt restores combat");
      screen.set(game, null);
      r.modules().setEnabled("autoprojectile", false);

      set(r, "derp", "mode", "CLIENT");
      set(r, "derp", "pitch", "67");
      r.modules().setEnabled("aura", false);
      HookBridge.gameControls().apply();
      perspective.setInt(settings, 1);
      float yaw = (float) number(player, "rotationYaw", "field_70177_z");
      int priorDerpYaw =
          field(r.modules().require("derp").module().getClass(), "spinningYaw")
              .getInt(r.modules().require("derp").module());
      r.modules().setEnabled("derp", true);
      HookBridge.gameControls().beforeInput();
      verify(!r.controls().rotation().isPresent(), "client Derp has no packet rotation claim");
      verify(
          HookBridge.gameControls().modelYaw(yaw, false) == priorDerpYaw + 10,
          "client Derp displays advancing model yaw");
      verify(
          HookBridge.gameControls().modelPitch(0, false) == 67,
          "client Derp displays configured pitch");
      verify(number(player, "rotationYaw", "field_70177_z") == yaw, "client Derp preserves camera");
      r.modules().setEnabled("derp", false);
      verify(
          HookBridge.gameControls().modelYaw(yaw, false) == yaw,
          "Derp renderer restores immediately on disable");
    } finally {
      for (String id : new String[] {"aura", "autoprojectile", "hitselect", "derp"}) {
        r.modules().setEnabled(id, false);
        for (perihelion.core.setting.Setting<?> setting :
            r.modules().require(id).module().settings().values()) reset(r, id, setting);
      }
      HookBridge.gameControls().apply();
      System.arraycopy(saved, 0, items, 0, items.length);
      selected.setInt(inv, oldSlot);
      perspective.setInt(settings, oldPerspective);
      hurt.setInt(player, oldHurt);
      screen.set(game, oldScreen);
      method(world.getClass(), new Class<?>[] {int.class}, "removeEntityFromWorld", "func_73028_b")
          .invoke(world, -9876123);
    }
    System.out.println("PERIHELION_SMOKE YURI_LATEST checks=" + checks + " PASS");
  }

  private static void miningSwap(
      ClientRuntime runtime,
      Object game,
      Object player,
      Object world,
      Object[] items,
      int x,
      int y,
      int z,
      boolean busy)
      throws Exception {
    Object inventory = field(player.getClass(), "inventory", "field_71071_by").get(player);
    Field selected = field(inventory.getClass(), "currentItem", "field_70461_c");
    int oldSlot = selected.getInt(inventory);
    Field mouse = field(game.getClass(), "objectMouseOver", "field_71476_x");
    Field focus = field(game.getClass(), "inGameHasFocus", "field_71415_G");
    Object oldMouse = mouse.get(game), oldItem = items[2];
    boolean oldFocus = focus.getBoolean(game);
    Object settings = field(game.getClass(), "gameSettings", "field_71474_y").get(game);
    Object attack = field(settings.getClass(), "keyBindAttack", "field_74312_F").get(settings);
    Field pressed = field(attack.getClass(), "pressed", "field_74513_e");
    boolean oldPressed = pressed.getBoolean(attack);
    Class<?> posType = Class.forName("net.minecraft.util.BlockPos"),
        stateType = Class.forName("net.minecraft.block.state.IBlockState");
    Object pos = pos(x, y, z);
    Method
        get = method(world.getClass(), new Class<?>[] {posType}, "getBlockState", "func_180495_p"),
        put =
            method(
                world.getClass(),
                new Class<?>[] {posType, stateType, int.class},
                "setBlockState",
                "func_180501_a");
    Object oldBlock = get.invoke(world, pos);
    Map<perihelion.core.setting.Setting<?>, Object> saved = new LinkedHashMap<>();
    boolean enabled = runtime.modules().require("autoswap").requestedEnabled();
    runtime.modules().setEnabled("autoswap", false);
    for (perihelion.core.setting.Setting<?> setting :
        runtime.modules().require("autoswap").module().settings().values())
      saved.put(setting, setting.get());
    perihelion.core.event.EventOwner owner =
        new perihelion.core.event.EventOwner(
            "projectile-mining-swap-probe",
            failure -> {
              throw new AssertionError(failure);
            });
    owner.activate();
    List<AutoCloseable> claims = new ArrayList<>();
    ControlRequest<Boolean> key =
        runtime.controls().scope(owner, claims::add).key(InputKey.ATTACK, 10000);
    try {
      put.invoke(world, pos, state("stone"), 2);
      items[2] = stack("diamond_pickaxe");
      Class<?> vec = Class.forName("net.minecraft.util.Vec3"),
          facing = Class.forName("net.minecraft.util.EnumFacing");
      mouse.set(
          game,
          Class.forName("net.minecraft.util.MovingObjectPosition")
              .getConstructor(vec, facing, posType)
              .newInstance(
                  vec.getConstructor(double.class, double.class, double.class)
                      .newInstance(x + .5, y + 1.0, z + .5),
                  facing.getField("UP").get(null),
                  pos));
      focus.setBoolean(game, true);
      pressed.setBoolean(attack, true);
      key.set(true);
      set(runtime, "autoswap", "swap-mode", "CLIENT");
      set(runtime, "autoswap", "swap-back", "true");
      set(runtime, "autoswap", "sneak-only", "false");
      runtime.modules().setEnabled("autoswap", true);
      HookBridge.gameControls().beforeInput();
      motion(player);
      if (busy)
        verify(
            !runtime.controls().hotbar().isPresent() && selected.getInt(inventory) == oldSlot,
            "mining Auto Swap cannot change tools during actual projectile aiming");
      else
        verify(
            runtime.controls().hotbar().isPresent()
                && runtime.controls().hotbar().get().slot() == 2
                && selected.getInt(inventory) == 2,
            "mining Auto Swap resumes after projectile suppression ends");
    } finally {
      runtime.modules().setEnabled("autoswap", false);
      for (AutoCloseable claim : claims) claim.close();
      owner.close();
      for (perihelion.core.setting.Setting<?> setting : saved.keySet())
        restoreSwap(runtime, setting, saved.get(setting));
      put.invoke(world, pos, oldBlock, 2);
      mouse.set(game, oldMouse);
      pressed.setBoolean(attack, oldPressed);
      focus.setBoolean(game, oldFocus);
      items[2] = oldItem;
      selected.setInt(inventory, oldSlot);
      HookBridge.gameControls().apply();
      if (enabled) runtime.modules().setEnabled("autoswap", true);
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void restoreSwap(
      ClientRuntime runtime, perihelion.core.setting.Setting setting, Object value) {
    runtime.modules().setSetting("autoswap", setting, value);
  }
}
