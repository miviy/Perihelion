package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;
import static perihelion.smoke.RotationPipelineProbe.*;

import io.netty.channel.Channel;
import java.lang.reflect.*;
import java.util.*;
import perihelion.core.control.*;
import perihelion.core.event.EventOwner;
import perihelion.core.input.Keybind;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.*;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.*;

/** Exercises release behavior on transformed Minecraft classes and the actual outbound pipeline. */
public final class Yuri13Probe {
  private static final String GAMEPLAY = "perihelion.integration.minecraft189.module.gameplay.";
  private static int checks;
  private static final String[] IDS = {
    "breaker", "autoswap", "freelook", "speed", "scaffold", "aura", "blockesp"
  };

  static void run(
      ClientRuntime r,
      Object game,
      Object player,
      Object world,
      Object[] items,
      Method getState,
      Method putState,
      Channel channel,
      List<Object> packets,
      int x,
      int y,
      int z)
      throws Exception {
    checks = 0;
    Object settings = field(game.getClass(), "gameSettings", "field_71474_y").get(game);
    Object inventory = field(player.getClass(), "inventory", "field_71071_by").get(player);
    Object input = field(player.getClass(), "movementInput", "field_71158_b").get(player);
    Field selected = field(inventory.getClass(), "currentItem", "field_70461_c");
    Field screen = field(game.getClass(), "currentScreen", "field_71462_r");
    Field over = field(game.getClass(), "objectMouseOver", "field_71476_x");
    Map<Field, Object> saved = new LinkedHashMap<>(), savedInput = new LinkedHashMap<>();
    for (String[] names :
        new String[][] {
          {"motionX", "field_70159_w"},
          {"motionY", "field_70181_x"},
          {"motionZ", "field_70179_y"},
          {"onGround", "field_70122_E"},
          {"isInWeb", "field_70134_J"},
          {"inWater", "field_70171_ac"},
          {"hurtTime", "field_70737_aN"},
          {"moveForward", "field_70701_bs"},
          {"moveStrafing", "field_70702_br"}
        }) {
      Field f = field(player.getClass(), names);
      saved.put(f, f.get(player));
    }
    for (String[] names :
        new String[][] {{"moveForward", "field_78900_b"}, {"moveStrafe", "field_78902_a"}}) {
      Field f = field(input.getClass(), names);
      savedInput.put(f, f.get(input));
    }
    Map<Object, Boolean> keys = new LinkedHashMap<>();
    for (String[] names :
        new String[][] {
          {"keyBindForward", "field_74351_w"},
          {"keyBindBack", "field_74368_y"},
          {"keyBindLeft", "field_74370_x"},
          {"keyBindRight", "field_74366_z"},
          {"keyBindJump", "field_74314_A"},
          {"keyBindAttack", "field_74312_F"},
          {"keyBindUseItem", "field_74313_G"}
        }) {
      Object key = field(settings.getClass(), names).get(settings);
      keys.put(key, field(key.getClass(), "pressed", "field_74513_e").getBoolean(key));
      field(key.getClass(), "pressed", "field_74513_e").setBoolean(key, false);
    }
    Keybind binding = r.modules().require("freelook").binding();
    Object oldOver = over.get(game), oldScreen = screen.get(game);
    EventOwner owner =
        new EventOwner(
            "yuri13-fixture",
            failure -> {
              throw new AssertionError(failure);
            });
    owner.activate();
    List<AutoCloseable> resources = new ArrayList<>();
    ControlRequest<HotbarSelection> otherSlot =
        r.controls().scope(owner, resources::add).hotbar(1000);
    ControlRequest<Rotation> otherRotation =
        r.controls().scope(owner, resources::add).rotation(1000);
    ControlRequest<Boolean> otherUse =
        r.controls().scope(owner, resources::add).key(InputKey.USE_ITEM, 1000);
    try {
      for (String id : IDS) r.modules().setEnabled(id, false);
      screen.set(game, null);
      toolSelection(inventory, items);
      freeLook(r, settings, player);
      Object stone =
          method(state("stone").getClass(), "getBlock", "func_177230_c").invoke(state("stone"));
      over.set(
          game,
          Class.forName("net.minecraft.util.MovingObjectPosition")
              .getConstructor(
                  Class.forName("net.minecraft.util.Vec3"),
                  Class.forName("net.minecraft.util.EnumFacing"),
                  Class.forName("net.minecraft.util.BlockPos"))
              .newInstance(
                  Class.forName("net.minecraft.util.Vec3")
                      .getConstructor(double.class, double.class, double.class)
                      .newInstance((double) x, (double) y, (double) z),
                  Enum.valueOf((Class) Class.forName("net.minecraft.util.EnumFacing"), "UP"),
                  pos(x, y, z)));
      putState.invoke(world, pos(x, y, z), state("stone"), 2);
      autoSwap(r, settings, player, inventory, items, selected, otherSlot, channel, packets);
      speed(r, settings, player, input, otherRotation);
      scaffold(
          r,
          settings,
          player,
          world,
          items,
          getState,
          putState,
          otherRotation,
          channel,
          packets,
          x,
          y,
          z);
      breaker(r, game, player, world, items, putState, selected, screen, x, y, z);
      aura(
          r,
          player,
          world,
          items,
          selected,
          otherRotation,
          otherSlot,
          otherUse,
          channel,
          packets,
          x,
          y,
          z);
      storage(r, world, putState, x, y, z);
    } finally {
      for (String id : IDS) {
        r.modules().setEnabled(id, false);
        for (perihelion.core.setting.Setting<?> setting :
            r.modules().require(id).module().settings().values()) reset(r, id, setting);
      }
      r.modules().setBinding("freelook", binding);
      for (AutoCloseable resource : resources) resource.close();
      owner.close();
      HookBridge.gameControls().apply();
      for (Map.Entry<Object, Boolean> e : keys.entrySet())
        field(e.getKey().getClass(), "pressed", "field_74513_e")
            .setBoolean(e.getKey(), e.getValue());
      for (Map.Entry<Field, Object> e : saved.entrySet()) e.getKey().set(player, e.getValue());
      for (Map.Entry<Field, Object> e : savedInput.entrySet()) e.getKey().set(input, e.getValue());
      over.set(game, oldOver);
      screen.set(game, oldScreen);
    }
    System.out.println("PERIHELION_SMOKE YURI13 checks=" + checks + " PASS");
  }

  private static void toolSelection(Object inventory, Object[] items) throws Exception {
    Arrays.fill(items, null);
    items[0] = stack("diamond_pickaxe");
    items[1] = stack("wooden_pickaxe");
    Object efficiency =
        field(Class.forName("net.minecraft.enchantment.Enchantment"), "efficiency", "field_77349_p")
            .get(null);
    method(
            items[1].getClass(),
            new Class<?>[] {efficiency.getClass().getSuperclass(), int.class},
            "addEnchantment",
            "func_77966_a")
        .invoke(items[1], efficiency, 5);
    Method best =
        method(
            Class.forName(GAMEPLAY + "InventoryPolicy"),
            new Class<?>[] {inventory.getClass(), Class.forName("net.minecraft.block.Block")},
            "breakerTool");
    Object stone =
        method(state("stone").getClass(), "getBlock", "func_177230_c").invoke(state("stone"));
    Object bed = method(state("bed").getClass(), "getBlock", "func_177230_c").invoke(state("bed"));
    Object wool =
        method(state("wool").getClass(), "getBlock", "func_177230_c").invoke(state("wool"));
    check(
        (Integer) best.invoke(null, inventory, stone) == 1,
        "Efficiency V beats raw diamond mining speed");
    items[2] = stack("shears");
    check((Integer) best.invoke(null, inventory, bed) == 2, "bed selects shears");
    check((Integer) best.invoke(null, inventory, wool) == 2, "cloth selects shears");
    Arrays.fill(items, null);
    items[3] = stack("wooden_axe");
    check(
        (Integer) best.invoke(null, inventory, bed) == 3, "bed fallback picks first eligible tool");
    check(
        (Integer) best.invoke(null, inventory, stone) == -1,
        "no suitable stone tool leaves selection alone");
  }

  private static void freeLook(ClientRuntime r, Object settings, Object player) throws Exception {
    Field perspective = field(settings.getClass(), "thirdPersonView", "field_74320_O");
    int old = perspective.getInt(settings);
    try {
      perspective.setInt(settings, 2);
      camera(player, 170, 30);
      r.modules().setBinding("freelook", new Keybind(Keybind.Device.KEYBOARD, 255));
      set(r, "freelook", "hold", "false");
      set(r, "freelook", "invert-pitch", "true");
      r.modules().setEnabled("freelook", true);
      CameraViewEvent initial = new CameraViewEvent(170, 30, 0);
      r.clientEvents().post(initial);
      check(initial.pitch() == 30, "invert keeps initial pitch");
      MouseLookEvent mouse = new MouseLookEvent(100, 40);
      r.clientEvents().post(mouse);
      check(mouse.isCancelled(), "FreeLook consumes mouse rotation");
      CameraViewEvent view = new CameraViewEvent(170, 30, .5f);
      r.clientEvents().post(view);
      check(
          Math.abs(view.yaw() - 177.5) < .001 && Math.abs(view.pitch() - 33) < .001,
          "FreeLook renders interpolated sample");
      check(
          number(player, "rotationYaw", "field_70177_z") == 170
              && number(player, "rotationPitch", "field_70125_A") == 30,
          "camera sampling keeps entity angles");
      r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      check(
          r.modules().require("freelook").state() == ModuleState.ENABLED,
          "toggle survives released binding");
      set(r, "freelook", "hold", "true");
      r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      r.tasks().drain();
      check(
          r.modules().require("freelook").state() != ModuleState.ENABLED,
          "hold disables after release");
      check(perspective.getInt(settings) == 2, "FreeLook restores perspective");
    } finally {
      r.modules().setEnabled("freelook", false);
      perspective.setInt(settings, old);
    }
  }

  private static void autoSwap(
      ClientRuntime r,
      Object settings,
      Object player,
      Object inv,
      Object[] items,
      Field selected,
      ControlRequest<HotbarSelection> other,
      Channel channel,
      List<Object> packets)
      throws Exception {
    Arrays.fill(items, null);
    items[1] = stack("diamond_pickaxe");
    selected.setInt(inv, 0);
    key(settings, "keyBindAttack", "field_74312_F", true);
    r.modules().setEnabled("autoswap", true);
    other.set(HotbarSelection.visible(4));
    HookBridge.gameControls().beforeInput();
    motion(player);
    check(selected.getInt(inv) == 4, "AutoSwap yields to higher slot owner");
    other.clear();
    HookBridge.gameControls().apply();
    HookBridge.gameControls().beforeInput();
    motion(player);
    check(selected.getInt(inv) == 1, "client AutoSwap selects best mining tool");
    key(settings, "keyBindAttack", "field_74312_F", false);
    HookBridge.gameControls().beforeInput();
    motion(player);
    check(selected.getInt(inv) == 1, "default client selection persists");
    r.modules().setEnabled("autoswap", false);
    HookBridge.gameControls().apply();
    check(selected.getInt(inv) == 1, "persistent slot survives disable");
    selected.setInt(inv, 0);
    set(r, "autoswap", "swap-back", "true");
    key(settings, "keyBindAttack", "field_74312_F", true);
    r.modules().setEnabled("autoswap", true);
    HookBridge.gameControls().beforeInput();
    motion(player);
    check(selected.getInt(inv) == 1, "restoring client selects tool");
    key(settings, "keyBindAttack", "field_74312_F", false);
    HookBridge.gameControls().beforeInput();
    motion(player);
    check(selected.getInt(inv) == 0, "Swap Back restores original");
    key(settings, "keyBindAttack", "field_74312_F", true);
    HookBridge.gameControls().beforeInput();
    motion(player);
    selected.setInt(inv, 5);
    key(settings, "keyBindAttack", "field_74312_F", false);
    HookBridge.gameControls().beforeInput();
    motion(player);
    check(selected.getInt(inv) == 5, "Swap Back respects manual selection");
    r.modules().setEnabled("autoswap", false);
    selected.setInt(inv, 0);
    set(r, "autoswap", "swap-mode", "SERVER");
    r.modules().setEnabled("autoswap", true);
    key(settings, "keyBindAttack", "field_74312_F", true);
    flush(channel);
    packets.clear();
    HookBridge.gameControls().beforeInput();
    motion(player);
    flush(channel);
    check(
        selected.getInt(inv) == 0 && r.controls().hotbar().get().slot() == 1,
        "server swap preserves visible slot");
    key(settings, "keyBindAttack", "field_74312_F", false);
    HookBridge.gameControls().beforeInput();
    motion(player);
    flush(channel);
    List<Object> swaps = new ArrayList<>();
    for (Object p : packets)
      if (p.getClass().getSimpleName().equals("C09PacketHeldItemChange")) swaps.add(p);
    check(
        swaps.size() >= 2
            && ((Number)
                        method(swaps.get(swaps.size() - 1).getClass(), "getSlotId", "func_149614_c")
                            .invoke(swaps.get(swaps.size() - 1)))
                    .intValue()
                == 0,
        "server swap restores server slot");
    r.modules().setEnabled("autoswap", false);
    HookBridge.gameControls().apply();
  }

  private static void speed(
      ClientRuntime r, Object settings, Object player, Object input, ControlRequest<Rotation> other)
      throws Exception {
    camera(player, 30, 20);
    field(input.getClass(), "moveForward", "field_78900_b").setFloat(input, 1);
    field(input.getClass(), "moveStrafe", "field_78902_a").setFloat(input, 1);
    field(player.getClass(), "onGround", "field_70122_E").setBoolean(player, true);
    field(player.getClass(), "inWater", "field_70171_ac").setBoolean(player, false);
    field(player.getClass(), "isInWeb", "field_70134_J").setBoolean(player, false);
    key(settings, "keyBindForward", "field_74351_w", true);
    key(settings, "keyBindLeft", "field_74370_x", true);
    set(r, "speed", "mode", "LEGIT");
    r.modules().setEnabled("speed", true);
    HookBridge.gameControls().beforeInput();
    check(
        Math.abs(r.controls().rotation().get().yaw() + 15) < .3,
        "Legit speed aims along diagonal movement keys; actual="
            + r.controls().rotation().get().yaw());
    boolean sprinting =
        (Boolean) method(player.getClass(), "isSprinting", "func_70051_ag").invoke(player);
    method(player.getClass(), new Class<?>[] {boolean.class}, "setSprinting", "func_70031_b")
        .invoke(player, true);
    field(player.getClass(), "motionX", "field_70159_w").setDouble(player, 0);
    field(player.getClass(), "motionZ", "field_70179_y").setDouble(player, 0);
    float jumpYaw = r.controls().rotation().get().yaw();
    method(player.getClass(), "jump", "func_70664_aZ").invoke(player);
    check(
        Math.abs(
                    number(player, "motionX", "field_70159_w")
                        + Math.sin(Math.toRadians(jumpYaw)) * .2)
                < 1e-4
            && Math.abs(
                    number(player, "motionZ", "field_70179_y")
                        - Math.cos(Math.toRadians(jumpYaw)) * .2)
                < 1e-4,
        "Legit speed actual sprint jump uses the requested movement yaw");
    method(player.getClass(), new Class<?>[] {boolean.class}, "setSprinting", "func_70031_b")
        .invoke(player, sprinting);
    other.set(Rotation.server(70, 0));
    HookBridge.gameControls().beforeInput();
    MovementYawEvent yaw = new MovementYawEvent(30);
    r.clientEvents().post(yaw);
    check(yaw.yaw() == 30, "Legit speed yields jump yaw when rotation loses");
    other.clear();
    field(player.getClass(), "isInWeb", "field_70134_J").setBoolean(player, true);
    HookBridge.gameControls().beforeInput();
    check(!r.controls().rotation().isPresent(), "web guard releases speed rotation");
    field(player.getClass(), "isInWeb", "field_70134_J").setBoolean(player, false);
    field(player.getClass(), "inWater", "field_70171_ac").setBoolean(player, true);
    HookBridge.gameControls().beforeInput();
    check(!r.controls().rotation().isPresent(), "water guard releases speed rotation");
    field(player.getClass(), "inWater", "field_70171_ac").setBoolean(player, false);
    key(settings, "keyBindJump", "field_74314_A", true);
    HookBridge.gameControls().beforeInput();
    check(!r.controls().rotation().isPresent(), "manual jump releases speed rotation");
    key(settings, "keyBindJump", "field_74314_A", false);
    r.modules().setEnabled("speed", false);
    HookBridge.gameControls().apply();
    key(settings, "keyBindLeft", "field_74370_x", false);
  }

  private static void scaffold(
      ClientRuntime r,
      Object settings,
      Object player,
      Object world,
      Object[] items,
      Method getState,
      Method putState,
      ControlRequest<Rotation> other,
      Channel channel,
      List<Object> packets,
      int x,
      int y,
      int z)
      throws Exception {
    Object support = pos(x, y, z), target = pos(x, y, z + 1);
    for (String mode : new String[] {"HYPIXEL", "TELLY"})
      for (String swap : new String[] {"CLIENT", "SERVER"})
        for (String ray : new String[] {"NORMAL", "STRICT"}) {
          Arrays.fill(items, null);
          items[0] = stack("stone");
          field(items[0].getClass(), "stackSize", "field_77994_a").setInt(items[0], 64);
          putState.invoke(world, support, state("stone"), 2);
          putState.invoke(world, target, state("air"), 2);
          position(player, x + .5, y + 1, z + 1.3);
          camera(player, 0, -30);
          field(player.getClass(), "onGround", "field_70122_E").setBoolean(player, false);
          set(r, "scaffold", "mode", mode);
          set(r, "scaffold", "rotations", "NORMAL");
          set(r, "scaffold", "hypixel-telly", "true");
          set(r, "scaffold", "ray-cast", ray);
          set(r, "scaffold", "swap-mode", swap);
          r.modules().setEnabled("scaffold", true);
          Field swingState = field(player.getClass(), "isSwingInProgress", "field_82175_bq");
          swingState.setBoolean(player, false);
          flush(channel);
          packets.clear();
          for (int i = 0; i < 8 && state("air").equals(getState.invoke(world, target)); i++) {
            HookBridge.gameControls().beforeInput();
            forceLook(player);
            motion(player);
            flush(channel);
          }
          check(
              !state("air").equals(getState.invoke(world, target)),
              mode + "/" + swap + "/" + ray + " places after converging");
          orderedPrePlacement(packets, mode + " placement");
          check(
              number(player, "rotationYaw", "field_70177_z") == 0
                  && number(player, "rotationPitch", "field_70125_A") == -30,
              "Scaffold keeps camera");
          if (ray.equals("STRICT")) {
            check(!swingState.getBoolean(player), "latest strict Scaffold keeps local arm still");
            check(
                packets.stream()
                    .anyMatch(p -> p.getClass().getSimpleName().equals("C0APacketAnimation")),
                "latest strict Scaffold still sends server animation");
          }
          if (mode.equals("HYPIXEL")) {
            MovementYawEvent geometry = new MovementYawEvent(0);
            r.clientEvents().post(geometry);
            check(
                geometry.yaw() == r.controls().rotation().get().yaw(),
                "Scaffold movement helpers follow their winning corrected-input basis");
            Object scaffold = r.modules().require("scaffold").module();
            check(
                Math.abs(
                        ((Number)
                                method(scaffold.getClass(), "expansionDirection").invoke(scaffold))
                            .doubleValue())
                    < 1e-6,
                "Scaffold expansion geometry explicitly retains camera and raw-key heading");
            sprintJump(r, player, "Hypixel Scaffold actual sprint jump uses its winning rotation");
            check(
                Math.abs(HookBridge.gameControls().modelYaw(0, false) - 125) < .001,
                "Hypixel Larp uses model-only yaw");
            check(r.controls().rotation().get().yaw() != 125, "Larp does not replace packet yaw");
            field(player.getClass(), "onGround", "field_70122_E").setBoolean(player, true);
            for (int tick = 0; tick < 4; tick++) {
              HookBridge.gameControls().beforeInput();
              forceLook(player);
              motion(player);
            }
            check(
                Math.abs(Math.IEEEremainder(r.controls().rotation().get().yaw() - 50, 360)) < .3,
                "Hypixel placement timer steers toward movement yaw plus 50 on ground: "
                    + r.controls().rotation().get().yaw()
                    + "/"
                    + swap
                    + "/"
                    + ray);
          }
          r.modules().setEnabled("scaffold", false);
          HookBridge.gameControls().apply();
        }
    putState.invoke(world, target, state("air"), 2);
    set(r, "scaffold", "mode", "HYPIXEL");
    r.modules().setEnabled("scaffold", true);
    other.set(Rotation.server(70, 0));
    flush(channel);
    packets.clear();
    HookBridge.gameControls().beforeInput();
    forceLook(player);
    motion(player);
    flush(channel);
    check(
        packets.stream()
            .noneMatch(p -> p.getClass().getSimpleName().equals("C08PacketPlayerBlockPlacement")),
        "Scaffold cannot place under a different rotation owner");
    MovementYawEvent losingJump = new MovementYawEvent(0, true);
    r.clientEvents().post(losingJump);
    check(
        losingJump.yaw() == 0,
        "Scaffold cannot change jump direction after losing rotation ownership");
    other.clear();
    r.modules().setEnabled("scaffold", false);
    HookBridge.gameControls().apply();
    putState.invoke(world, target, state("air"), 2);
    key(settings, "keyBindForward", "field_74351_w", false);
    position(player, x + .5, y + 1, z + .5);
    field(player.getClass(), "onGround", "field_70122_E").setBoolean(player, false);
    field(player.getClass(), "motionX", "field_70159_w").setDouble(player, 0);
    field(player.getClass(), "motionZ", "field_70179_y").setDouble(player, 0);
    field(player.getClass(), "motionY", "field_70181_x").setDouble(player, .1);
    set(r, "scaffold", "tower", "HYPIXEL");
    Object module = r.modules().require("scaffold").module();
    Method tower =
        method(
            module.getClass(), new Class<?>[] {int.class, boolean.class, boolean.class}, "tower");
    Object jump =
        field(Class.forName("net.minecraft.potion.Potion"), "jump", "field_76430_j").get(null);
    int potionId =
        ((Number) method(jump.getClass(), "getId", "func_76396_c").invoke(jump)).intValue();
    Class<?> effectType = Class.forName("net.minecraft.potion.PotionEffect");
    Object effect =
        effectType.getConstructor(int.class, int.class, int.class).newInstance(potionId, 200, 1);
    method(player.getClass(), new Class<?>[] {effectType}, "addPotionEffect", "func_70690_d")
        .invoke(player, effect);
    try {
      tower.invoke(module, 4, false, true);
      check(
          Math.abs(number(player, "motionY", "field_70181_x") - .1) < 1e-8,
          "Hypixel tower leaves jump boost motion alone");
    } finally {
      method(player.getClass(), new Class<?>[] {int.class}, "removePotionEffect", "func_82170_o")
          .invoke(player, potionId);
    }
    tower.invoke(module, 4, false, true);
    check(
        Math.abs(number(player, "motionY", "field_70181_x") - .07) < 1e-8,
        "stationary Hypixel tower tick four subtracts .03");
    tower.invoke(module, 5, false, true);
    check(
        Math.abs(number(player, "motionY", "field_70181_x") + .43) < 1e-8,
        "stationary Hypixel tower tick five subtracts .5");
  }

  private static void breaker(
      ClientRuntime r,
      Object game,
      Object player,
      Object world,
      Object[] items,
      Method putState,
      Field selected,
      Field screen,
      int x,
      int y,
      int z)
      throws Exception {
    putState.invoke(world, pos(x, y + 1, z), state("bed"), 2);
    position(player, x + .5, y + 1, z + 1.3);
    Arrays.fill(items, null);
    items[1] = stack("shears");
    Object inv = field(player.getClass(), "inventory", "field_71071_by").get(player);
    selected.setInt(inv, 0);
    set(r, "breaker", "whitelist", "false");
    set(r, "breaker", "auto-tool", "false");
    r.modules().setEnabled("breaker", true);
    for (int i = 0; i < 7; i++) HookBridge.gameControls().beforeInput();
    check(r.controls().hotbar().get().slot() == 0, "Breaker Auto Tool off keeps current slot");
    set(r, "breaker", "auto-tool", "true");
    HookBridge.gameControls().beforeInput();
    check(r.controls().hotbar().get().slot() == 1, "Breaker uses scored shears claim");
    check(selected.getInt(inv) == 0, "Breaker tool is server-only");
    screen.set(game, Class.forName("net.minecraft.client.gui.GuiChat").newInstance());
    HookBridge.gameControls().beforeInput();
    check(
        !r.controls().hotbar().isPresent() && !r.controls().rotation().isPresent(),
        "Breaker interruption releases tool and rotation");
    screen.set(game, null);
    r.modules().setEnabled("breaker", false);
    HookBridge.gameControls().apply();
    putState.invoke(world, pos(x, y + 1, z), state("air"), 2);
  }

  private static void aura(
      ClientRuntime r,
      Object player,
      Object world,
      Object[] items,
      Field selected,
      ControlRequest<Rotation> other,
      ControlRequest<HotbarSelection> otherSlot,
      ControlRequest<Boolean> otherUse,
      Channel channel,
      List<Object> packets,
      int x,
      int y,
      int z)
      throws Exception {
    Object pig =
        Class.forName("net.minecraft.entity.passive.EntityPig")
            .getConstructor(Class.forName("net.minecraft.world.World"))
            .newInstance(world);
    position(player, x + .5, y + 1, z + .5);
    position(pig, x + 2, y + 1, z + .5);
    camera(player, 170, -35);
    method(
            world.getClass(),
            new Class<?>[] {int.class, Class.forName("net.minecraft.entity.Entity")},
            "addEntityToWorld",
            "func_73027_a")
        .invoke(world, -887712, pig);
    Object inv = field(player.getClass(), "inventory", "field_71071_by").get(player);
    Arrays.fill(items, null);
    items[0] = stack("diamond_sword");
    selected.setInt(inv, 0);
    for (String[] s :
        new String[][] {
          {"targets", "ANIMALS"},
          {"fov", "360"},
          {"through-walls", "true"},
          {"simulate-mouse-clicks", "false"},
          {"ray-cast", "false"},
          {"min-rotation-speed", "10"},
          {"max-rotation-speed", "10"},
          {"auto-block", "HYPIXEL"}
        }) set(r, "aura", s[0], s[1]);
    Object module = r.modules().require("aura").module();
    Field next = field(module.getClass(), "nextAttack");
    try {
      r.modules().setEnabled("aura", true);
      next.setDouble(module, System.nanoTime() / 1e6 + 25);
      HookBridge.gameControls().beforeInput();
      next.setDouble(module, 0);
      flush(channel);
      packets.clear();
      forceLook(player);
      motion(player);
      flush(channel);
      Rotation submitted = r.controls().rotation().get();
      HookBridge.gameControls().beforeInput();
      forceLook(player);
      motion(player);
      flush(channel);
      ordered(packets, "C02PacketUseEntity", submitted, "Hypixel Aura PRE attack");
      check(
          packets.stream()
              .noneMatch(p -> p.getClass().getSimpleName().equals("C09PacketHeldItemChange")),
          "optimized Hypixel does not spoof random slots");
      next.setDouble(module, System.nanoTime() / 1e6 + 1000);
      flush(channel);
      packets.clear();
      HookBridge.gameControls().beforeInput();
      forceLook(player);
      motion(player);
      flush(channel);
      orderedBeforeWalking(packets, "C08PacketPlayerBlockPlacement", "Hypixel Aura PRE block");
      check(
          field(module.getClass(), "ownedUse").get(module) == items[0],
          "Hypixel owns local blocking item");
      flush(channel);
      packets.clear();
      HookBridge.gameControls().beforeInput();
      motion(player);
      flush(channel);
      check(
          field(module.getClass(), "ownedUse").get(module) == null
              && !(Boolean)
                  method(player.getClass(), "isUsingItem", "func_71039_bw").invoke(player),
          "next tick releases owned local use");
      check(
          packets.stream()
              .anyMatch(p -> p.getClass().getSimpleName().equals("C07PacketPlayerDigging")),
          "next tick sends release packet");
      field(module.getClass(), "hypixelArmed").setBoolean(module, true);
      other.set(Rotation.server(60, 0));
      flush(channel);
      packets.clear();
      HookBridge.gameControls().beforeInput();
      forceLook(player);
      motion(player);
      flush(channel);
      check(
          packets.stream()
              .noneMatch(p -> p.getClass().getSimpleName().equals("C08PacketPlayerBlockPlacement")),
          "Hypixel block yields to competing rotation");
      other.clear();
      field(module.getClass(), "hypixelArmed").setBoolean(module, true);
      next.setDouble(module, System.nanoTime() / 1e6 + 1000);
      otherUse.set(true);
      flush(channel);
      packets.clear();
      HookBridge.gameControls().beforeInput();
      forceLook(player);
      motion(player);
      flush(channel);
      check(
          packets.stream()
              .noneMatch(p -> p.getClass().getSimpleName().equals("C08PacketPlayerBlockPlacement")),
          "Hypixel block yields to another use owner");
      otherUse.clear();
      HookBridge.gameControls().apply();
      field(module.getClass(), "hypixelArmed").setBoolean(module, true);
      otherSlot.set(HotbarSelection.spoof(4));
      flush(channel);
      packets.clear();
      HookBridge.gameControls().beforeInput();
      forceLook(player);
      motion(player);
      flush(channel);
      check(
          packets.stream()
              .noneMatch(p -> p.getClass().getSimpleName().equals("C08PacketPlayerBlockPlacement")),
          "Hypixel block yields to a conflicting server slot");
      otherSlot.clear();
      HookBridge.gameControls().apply();
      set(r, "aura", "auto-block", "LEGIT");
      check(
          ((Number) method(module.getClass(), "attackDelay").invoke(module)).doubleValue() == 200,
          "Legit attacks use fixed five CPS");
      for (int hurt : new int[] {11, 10, 6, 5, 0}) {
        field(player.getClass(), "hurtTime", "field_70737_aN").setInt(player, hurt);
        HookBridge.gameControls().beforeInput();
        check(
            r.controls().key(InputKey.USE_ITEM).get() == (hurt >= 6 && hurt <= 10),
            "Legit hurt-window " + hurt);
      }
      position(pig, x + 4, y + 1, z + .5);
      field(player.getClass(), "hurtTime", "field_70737_aN").setInt(player, 8);
      HookBridge.gameControls().beforeInput();
      check(!r.controls().key(InputKey.USE_ITEM).get(), "Legit three-block range boundary");
    } finally {
      r.modules().setEnabled("aura", false);
      other.clear();
      HookBridge.gameControls().apply();
      method(world.getClass(), new Class<?>[] {int.class}, "removeEntityFromWorld", "func_73028_b")
          .invoke(world, -887712);
    }
  }

  private static void storage(ClientRuntime r, Object world, Method putState, int x, int y, int z)
      throws Exception {
    Object a = pos(x + 1, y, z), b = pos(x + 2, y, z);
    putState.invoke(world, a, state("chest"), 2);
    putState.invoke(world, b, state("chest"), 2);
    Object module = r.modules().require("blockesp").module();
    Method bounds = method(module.getClass(), new Class<?>[] {a.getClass()}, "bounds");
    Object box = bounds.invoke(module, a);
    check(
        box != null
            && Math.abs(
                    number(box, "maxX", "field_72336_d")
                        - number(box, "minX", "field_72340_a")
                        - 1.875)
                < 1e-6,
        "double chest has joined bounds");
    check(bounds.invoke(module, b) == null, "double chest is rendered once");
    putState.invoke(world, b, state("air"), 2);
    box = bounds.invoke(module, a);
    check(
        Math.abs(number(box, "maxX", "field_72336_d") - number(box, "minX", "field_72340_a") - .875)
            < 1e-6,
        "broken chest partner immediately shrinks bounds");
    perihelion.core.setting.Setting<perihelion.core.setting.ColorValue> accent =
        (perihelion.core.setting.Setting<perihelion.core.setting.ColorValue>)
            (Object) r.modules().require("hud").module().settings().get("accent");
    perihelion.core.setting.ColorValue previous = accent.get();
    try {
      r.modules().setSetting("hud", accent, perihelion.core.setting.ColorValue.rgb(16, 32, 48));
      set(r, "blockesp", "client-color", "true");
      Object block =
          method(state("chest").getClass(), "getBlock", "func_177230_c").invoke(state("chest"));
      check(
          (Integer)
                  method(
                          module.getClass(),
                          new Class<?>[] {Class.forName("net.minecraft.block.Block")},
                          "color")
                      .invoke(module, block)
              == 0xff102030,
          "BlockESP client color follows the native HUD accent");
    } finally {
      r.modules().setSetting("hud", accent, previous);
    }
  }

  private static void key(Object settings, String mcp, String srg, boolean down) throws Exception {
    Object key = field(settings.getClass(), mcp, srg).get(settings);
    field(key.getClass(), "pressed", "field_74513_e").setBoolean(key, down);
  }

  private static void sprintJump(ClientRuntime r, Object player, String label) throws Exception {
    boolean sprinting =
        (Boolean) method(player.getClass(), "isSprinting", "func_70051_ag").invoke(player);
    double mx = number(player, "motionX", "field_70159_w"),
        mz = number(player, "motionZ", "field_70179_y");
    try {
      method(player.getClass(), new Class<?>[] {boolean.class}, "setSprinting", "func_70031_b")
          .invoke(player, true);
      field(player.getClass(), "motionX", "field_70159_w").setDouble(player, 0);
      field(player.getClass(), "motionZ", "field_70179_y").setDouble(player, 0);
      float yaw = r.controls().rotation().get().yaw();
      method(player.getClass(), "jump", "func_70664_aZ").invoke(player);
      check(
          Math.abs(number(player, "motionX", "field_70159_w") + Math.sin(Math.toRadians(yaw)) * .2)
                  < 1e-4
              && Math.abs(
                      number(player, "motionZ", "field_70179_y")
                          - Math.cos(Math.toRadians(yaw)) * .2)
                  < 1e-4,
          label);
    } finally {
      method(player.getClass(), new Class<?>[] {boolean.class}, "setSprinting", "func_70031_b")
          .invoke(player, sprinting);
      field(player.getClass(), "motionX", "field_70159_w").setDouble(player, mx);
      field(player.getClass(), "motionZ", "field_70179_y").setDouble(player, mz);
    }
  }

  private static <T> void reset(ClientRuntime r, String id, perihelion.core.setting.Setting<T> s) {
    r.modules().setSetting(id, s, s.defaultValue());
  }

  private static void check(boolean ok, String label) {
    if (!ok) throw new AssertionError(label);
    checks++;
  }
}
