package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;
import static perihelion.smoke.RotationPipelineProbe.*;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.*;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import perihelion.core.control.InputKey;
import perihelion.core.event.RenderTickEvent;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.event.MouseOverEvent;
import perihelion.integration.minecraft189.event.PlayerControlEvent;

/** Packaged B4 input, manual guard and real TCP queue/release checks. */
public final class B4CombatHookProbe {
  private static int checks;

  public static void run(ClientRuntime runtime) throws Exception {
    checks = 0;
    Object game =
        method(Class.forName("net.minecraft.client.Minecraft"), "getMinecraft", "func_71410_x")
            .invoke(null);
    Object player = field(game.getClass(), "thePlayer", "field_71439_g").get(game);
    Object world = field(game.getClass(), "theWorld", "field_71441_e").get(game);
    Object settings = field(game.getClass(), "gameSettings", "field_71474_y").get(game);
    Object input = field(player.getClass(), "movementInput", "field_71158_b").get(player);
    Object inventory = field(player.getClass(), "inventory", "field_71071_by").get(player);
    Object[] items =
        (Object[]) field(inventory.getClass(), "mainInventory", "field_70462_a").get(inventory);
    Object[] savedItems = items.clone();
    Map<Setting<?>, Object> savedSettings = new LinkedHashMap<>();
    Map<String, Boolean> enabled = new LinkedHashMap<>();
    String[] ids = {
      "autoclicker",
      "blockhit",
      "reach",
      "blink",
      "lagrange",
      "aura",
      "antibot",
      "scaffold",
      "blockin",
      "autoprojectile"
    };
    for (String id : ids) {
      enabled.put(id, runtime.modules().require(id).requestedEnabled());
      runtime.modules().setEnabled(id, false);
      for (Setting<?> setting : runtime.modules().require(id).module().settings().values())
        savedSettings.put(setting, setting.get());
    }
    int listeners = runtime.clientEvents().subscriptionCount(),
        claims = runtime.controls().requestCount();
    Field screen = field(game.getClass(), "currentScreen", "field_71462_r"),
        focus = field(game.getClass(), "inGameHasFocus", "field_71415_G"),
        local = field(game.getClass(), "integratedServerIsRunning", "field_71455_al"),
        currentItem = field(inventory.getClass(), "currentItem", "field_70461_c"),
        forward = field(input.getClass(), "moveForward", "field_78900_b"),
        side = field(input.getClass(), "moveStrafe", "field_78902_a"),
        ground = field(player.getClass(), "onGround", "field_70122_E"),
        hurt = field(player.getClass(), "hurtTime", "field_70737_aN"),
        mx = field(player.getClass(), "motionX", "field_70159_w"),
        mz = field(player.getClass(), "motionZ", "field_70179_y");
    Field selection = field(game.getClass(), "objectMouseOver", "field_71476_x");
    Object oldScreen = screen.get(game), oldSelection = selection.get(game);
    boolean oldFocus = focus.getBoolean(game),
        oldLocal = local.getBoolean(game),
        oldGround = ground.getBoolean(player);
    int oldItem = currentItem.getInt(inventory), oldHurt = hurt.getInt(player);
    float oldForward = forward.getFloat(input), oldSide = side.getFloat(input);
    double oldMx = mx.getDouble(player), oldMz = mz.getDouble(player);
    ByteBuffer mouse = (ByteBuffer) field(Mouse.class, "buttons").get(null);
    ByteBuffer keyboard = (ByteBuffer) field(Keyboard.class, "keyDownBuffer").get(null);
    byte left = mouse.get(0), right = mouse.get(1), shift = keyboard.get(42);
    Object attack = field(settings.getClass(), "keyBindAttack", "field_74312_F").get(settings),
        use = field(settings.getClass(), "keyBindUseItem", "field_74313_G").get(settings),
        forwardKey = field(settings.getClass(), "keyBindForward", "field_74351_w").get(settings);
    Field presses = field(attack.getClass(), "pressTime", "field_151474_i"),
        pressed = field(attack.getClass(), "pressed", "field_74513_e");
    int oldAttackPresses = presses.getInt(attack), oldUsePresses = presses.getInt(use);
    boolean oldForwardPressed = pressed.getBoolean(forwardKey);
    Object target = target(world, player);
    try {
      Arrays.fill(items, null);
      currentItem.setInt(inventory, 0);
      screen.set(game, null);
      focus.setBoolean(game, true);
      selection.set(game, null);
      mouse.put(0, (byte) 1);
      mouse.put(1, (byte) 0);
      items[0] = stack("diamond_sword");
      set(runtime, "autoclicker", "click-mode", "B4_LEFT");
      set(runtime, "autoclicker", "weapons-only", "false");
      enable(runtime, "autoclicker");
      Object auto = runtime.modules().require("autoclicker").module();
      Object b4 = field(auto.getClass(), "b4").get(auto),
          timing = field(b4.getClass(), "timing").get(b4);
      due(timing);
      int count = presses.getInt(attack);
      render(runtime);
      check(presses.getInt(attack) == count + 1, "B4 held left queues one vanilla press");
      check(
          runtime.controls().key(InputKey.ATTACK).orElse(false),
          "B4 left holds an activation-owned attack claim");
      blockedPress(runtime, timing, attack, presses, InputKey.ATTACK);
      field(timing.getClass(), "nextPress").setLong(timing, System.nanoTime() / 1_000_000L + 10000);
      field(timing.getClass(), "nextRelease").setLong(timing, 2);
      render(runtime);
      check(
          !runtime.controls().key(InputKey.ATTACK).orElse(true),
          "B4 half-cycle releases without a second press");
      mouse.put(0, (byte) 0);
      render(runtime);
      check(
          !runtime.controls().key(InputKey.ATTACK).isPresent(),
          "physical release clears synthetic input");
      set(runtime, "autoclicker", "click-mode", "B4_RIGHT");
      items[0] = stack("stone");
      mouse.put(1, (byte) 1);
      due(timing);
      count = presses.getInt(use);
      render(runtime);
      check(presses.getInt(use) == count + 1, "B4 right clicks a held block");
      blockedPress(runtime, timing, use, presses, InputKey.USE_ITEM);
      items[0] = stack("diamond_sword");
      due(timing);
      count = presses.getInt(use);
      render(runtime);
      check(
          presses.getInt(use) == count && !runtime.controls().key(InputKey.USE_ITEM).isPresent(),
          "B4 blocks-only rejects a sword and releases the old synthetic use hold");
      set(runtime, "autoclicker", "click-mode", "B4_BOTH");
      mouse.put(0, (byte) 1);
      mouse.put(1, (byte) 0);
      due(timing);
      render(runtime);
      check(runtime.controls().key(InputKey.ATTACK).orElse(false), "Both starts its left press");
      items[0] = stack("stone");
      mouse.put(0, (byte) 0);
      mouse.put(1, (byte) 1);
      due(timing);
      render(runtime);
      check(
          !runtime.controls().key(InputKey.ATTACK).isPresent()
              && runtime.controls().key(InputKey.USE_ITEM).orElse(false),
          "left-to-right transition retires Attack instead of continuing to mine");
      mouse.put(0, (byte) 1);
      mouse.put(1, (byte) 0);
      due(timing);
      render(runtime);
      check(
          runtime.controls().key(InputKey.ATTACK).orElse(false)
              && !runtime.controls().key(InputKey.USE_ITEM).isPresent(),
          "right-to-left transition retires the previous synthetic use hold");
      mouse.put(0, (byte) 0);
      mouse.put(1, (byte) 0);
      inventory(runtime, game, player, items, auto, timing, mouse, keyboard);
      disable(runtime, "autoclicker");
      screen.set(game, null);
      focus.setBoolean(game, true);
      check(
          !runtime.controls().key(InputKey.ATTACK).isPresent()
              && !runtime.controls().key(InputKey.USE_ITEM).isPresent(),
          "AutoClicker disable drops both claims");
      items[0] = stack("diamond_sword");
      mouse.put(0, (byte) 0);
      mouse.put(1, (byte) 0);
      hurt.setInt(player, 2);
      set(runtime, "blockhit", "block-mode", "B4_GUARD");
      enable(runtime, "blockhit");
      runtime.clientEvents().post(PlayerControlEvent.AFTER_MOTION);
      check(
          runtime.controls().key(InputKey.USE_ITEM).orElse(false),
          "B4 guard blocks nearby enemy without an attack event");
      for (int i = 0; i < 3; i++) runtime.clientEvents().post(PlayerControlEvent.AFTER_MOTION);
      check(
          !runtime.controls().key(InputKey.USE_ITEM).isPresent(),
          "B4 guard releases after three intervals");
      for (int i = 0; i < 4; i++) runtime.clientEvents().post(PlayerControlEvent.AFTER_MOTION);
      check(
          !runtime.controls().key(InputKey.USE_ITEM).isPresent(),
          "guard keeps four cooldown intervals clear");
      runtime.clientEvents().post(PlayerControlEvent.AFTER_MOTION);
      check(
          runtime.controls().key(InputKey.USE_ITEM).orElse(false),
          "guard starts again on fifth cooldown interval");
      set(runtime, "blockhit", "block-mode", "AFTER_ATTACK");
      check(
          !runtime.controls().key(InputKey.USE_ITEM).isPresent(),
          "switching guard mode releases owned blocking");
      set(runtime, "aura", "auto-block", "VANILLA");
      enable(runtime, "aura");
      Object packetAura = runtime.modules().require("aura").module();
      field(packetAura.getClass(), "target").set(packetAura, target);
      set(runtime, "blockhit", "block-mode", "B4_GUARD");
      int beforeGuard = presses.getInt(use);
      runtime.clientEvents().post(PlayerControlEvent.AFTER_MOTION);
      check(
          !runtime.controls().key(InputKey.USE_ITEM).isPresent()
              && presses.getInt(use) == beforeGuard,
          "B4 guard cannot add manual use input over active Vanilla packet Aura");
      disable(runtime, "aura");
      runtime.clientEvents().post(PlayerControlEvent.AFTER_MOTION);
      check(
          runtime.controls().key(InputKey.USE_ITEM).orElse(false)
              && presses.getInt(use) == beforeGuard + 1,
          "B4 guard resumes one ordinary press after packet Aura releases ownership");
      set(runtime, "blockhit", "block-mode", "AFTER_ATTACK");
      disable(runtime, "blockhit");
      presses.setInt(attack, 0);
      reach(runtime, player, input, items);
      projectileFacts(runtime, game, target);
      auraFacts(runtime, game);
      local.setBoolean(game, false);
      try (GameplayLoopback peer = new GameplayLoopback(game)) {
        projectileTransport(runtime, peer);
        guardAction(runtime, game, player, use, presses, mouse, peer);
        blink(runtime, peer, target);
        lagRange(runtime, peer, player, target, input, items, forwardKey, pressed);
      } finally {
        local.setBoolean(game, oldLocal);
      }
      check(
          runtime.clientEvents().subscriptionCount() == listeners,
          "B4 mode teardown restores listener count");
      check(runtime.controls().requestCount() == claims, "B4 mode teardown restores claim count");
      System.out.println("PERIHELION_SMOKE B4_COMBAT_NETWORK checks=" + checks + " PASS");
    } finally {
      for (String id : ids) runtime.modules().setEnabled(id, false);
      runtime.tasks().drain();
      for (String id : ids)
        for (Setting<?> setting : runtime.modules().require(id).module().settings().values())
          restore(runtime, id, setting, savedSettings.get(setting));
      method(world.getClass(), new Class<?>[] {int.class}, "removeEntityFromWorld", "func_73028_b")
          .invoke(world, -887728);
      System.arraycopy(savedItems, 0, items, 0, items.length);
      currentItem.setInt(inventory, oldItem);
      screen.set(game, oldScreen);
      focus.setBoolean(game, oldFocus);
      local.setBoolean(game, oldLocal);
      selection.set(game, oldSelection);
      forward.setFloat(input, oldForward);
      side.setFloat(input, oldSide);
      ground.setBoolean(player, oldGround);
      hurt.setInt(player, oldHurt);
      mx.setDouble(player, oldMx);
      mz.setDouble(player, oldMz);
      mouse.put(0, left);
      mouse.put(1, right);
      keyboard.put(42, shift);
      presses.setInt(attack, oldAttackPresses);
      presses.setInt(use, oldUsePresses);
      pressed.setBoolean(forwardKey, oldForwardPressed);
      for (String id : ids) if (enabled.get(id)) runtime.modules().setEnabled(id, true);
    }
  }

  private static void blockedPress(
      ClientRuntime runtime, Object timing, Object binding, Field presses, InputKey key)
      throws Exception {
    perihelion.core.event.EventOwner owner =
        new perihelion.core.event.EventOwner(
            "b4-input-arbitration",
            failure -> {
              throw new AssertionError(failure);
            });
    owner.activate();
    try (perihelion.core.control.ControlRequest<Boolean> blocker =
        runtime.controls().scope(owner, resource -> {}).key(key, 10000)) {
      blocker.set(false);
      int before = presses.getInt(binding);
      due(timing);
      render(runtime);
      check(
          presses.getInt(binding) == before && !runtime.controls().key(key).orElse(true),
          "B4 " + key + " cannot queue a vanilla press through the winning false claim");
    } finally {
      owner.close();
    }
  }

  private static void guardAction(
      ClientRuntime runtime,
      Object game,
      Object player,
      Object use,
      Field presses,
      ByteBuffer mouse,
      GameplayLoopback peer)
      throws Exception {
    Field cooldown = field(game.getClass(), "rightClickDelayTimer", "field_71467_ac");
    int oldCooldown = cooldown.getInt(game);
    Object inventory = field(player.getClass(), "inventory", "field_71071_by").get(player);
    Object[] items =
        (Object[]) field(inventory.getClass(), "mainInventory", "field_70462_a").get(inventory);
    Field selected = field(inventory.getClass(), "currentItem", "field_70461_c"),
        hurt = field(player.getClass(), "hurtTime", "field_70737_aN"),
        yaw = field(player.getClass(), "rotationYaw", "field_70177_z"),
        pitch = field(player.getClass(), "rotationPitch", "field_70125_A");
    int oldSelected = selected.getInt(inventory), oldHurt = hurt.getInt(player);
    Object oldHeld = items[oldSelected];
    float oldYaw = yaw.getFloat(player), oldPitch = pitch.getFloat(player);
    java.util.concurrent.atomic.AtomicInteger
        sent = new java.util.concurrent.atomic.AtomicInteger(),
        blocks = new java.util.concurrent.atomic.AtomicInteger(),
        releases = new java.util.concurrent.atomic.AtomicInteger();
    perihelion.core.event.EventOwner observer =
        new perihelion.core.event.EventOwner(
            "b4-real-guard-action",
            failure -> {
              throw new AssertionError(failure);
            });
    observer.activate();
    AutoCloseable subscription =
        runtime
            .networkEvents()
            .subscribe(
                observer,
                perihelion.integration.minecraft189.event.PacketSendEvent.class,
                Integer.MAX_VALUE,
                false,
                event -> {
                  sent.incrementAndGet();
                  Object packet = event.getClass().getMethod("packet").invoke(event);
                  String name = packet.getClass().getSimpleName();
                  if (name.equals("C08PacketPlayerBlockPlacement")) blocks.incrementAndGet();
                  if (name.equals("C07PacketPlayerDigging")) {
                    Object action =
                        method(packet.getClass(), "getStatus", "func_180762_c").invoke(packet);
                    if (((Enum<?>) action).name().equals("RELEASE_USE_ITEM"))
                      releases.incrementAndGet();
                  }
                });
    try {
      mouse.put(0, (byte) 0);
      mouse.put(1, (byte) 0);
      method(player.getClass(), "clearItemInUse", "func_71041_bz").invoke(player);
      // Own the guard's requirements instead of inheriting Reach's final block item.
      // Looking into the sky also excludes an unrelated interactable-block activation.
      Object sword = stack("diamond_sword");
      items[oldSelected] = sword;
      selected.setInt(inventory, oldSelected);
      hurt.setInt(player, 2);
      pitch.setFloat(player, -90);
      check(
          method(player.getClass(), "getHeldItem", "func_70694_bm").invoke(player) == sword,
          "guard fixture owns the selected sword independently of preceding item tests");
      presses.setInt(use, 0);
      cooldown.setInt(game, 4);
      set(runtime, "blockhit", "block-mode", "B4_GUARD");
      enable(runtime, "blockhit");
      runtime.clientEvents().post(PlayerControlEvent.AFTER_MOTION);
      check(
          presses.getInt(use) == 1 && runtime.controls().key(InputKey.USE_ITEM).orElse(false),
          "guard queues the same one-shot vanilla use as B4 KeyUtils.press");
      int before = peer.received.size();
      method(game.getClass(), "runTick", "func_71407_l").invoke(game);
      flush(peer.client);
      peer.await(before + sent.get());
      check(
          (Boolean) method(player.getClass(), "isBlocking", "func_70632_aY").invoke(player),
          "real vanilla tick starts sword blocking despite the old four-tick cooldown"
              + " (remaining presses="
              + presses.getInt(use)
              + ", C08="
              + blocks.get()
              + ")");
      check(
          blocks.get() > 0
              && peer.received.subList(before, peer.received.size()).stream()
                  .anyMatch(
                      packet ->
                          packet
                              .getClass()
                              .getSimpleName()
                              .equals("C08PacketPlayerBlockPlacement")),
          "queued guard use reaches the serialized TCP peer as C08");
      disable(runtime, "blockhit");
      method(game.getClass(), "runTick", "func_71407_l").invoke(game);
      flush(peer.client);
      peer.await(before + sent.get());
      check(
          !(Boolean) method(player.getClass(), "isUsingItem", "func_71039_bw").invoke(player),
          "guard disable lets the next real tick end sword use");
      boolean wireRelease = false;
      for (Object packet : peer.received.subList(before, peer.received.size())) {
        if (!packet.getClass().getSimpleName().equals("C07PacketPlayerDigging")) continue;
        Object action = method(packet.getClass(), "getStatus", "func_180762_c").invoke(packet);
        wireRelease |= ((Enum<?>) action).name().equals("RELEASE_USE_ITEM");
      }
      check(
          releases.get() > 0 && wireRelease,
          "guard release reaches the serialized TCP peer as C07 RELEASE_USE_ITEM");
    } finally {
      disable(runtime, "blockhit");
      method(player.getClass(), "clearItemInUse", "func_71041_bz").invoke(player);
      items[oldSelected] = oldHeld;
      selected.setInt(inventory, oldSelected);
      hurt.setInt(player, oldHurt);
      yaw.setFloat(player, oldYaw);
      pitch.setFloat(player, oldPitch);
      cooldown.setInt(game, oldCooldown);
      subscription.close();
      observer.close();
    }
  }

  private static Object target(Object world, Object player) throws Exception {
    Object profile =
        Class.forName("com.mojang.authlib.GameProfile")
            .getConstructor(UUID.class, String.class)
            .newInstance(UUID.randomUUID(), "B4Fixture");
    Object target =
        Class.forName("net.minecraft.client.entity.EntityOtherPlayerMP")
            .getConstructor(Class.forName("net.minecraft.world.World"), profile.getClass())
            .newInstance(world, profile);
    double x = field(player.getClass(), "posX", "field_70165_t").getDouble(player),
        y = field(player.getClass(), "posY", "field_70163_u").getDouble(player),
        z = field(player.getClass(), "posZ", "field_70161_v").getDouble(player);
    position(target, x, y, z + 2.5);
    method(
            world.getClass(),
            new Class<?>[] {int.class, Class.forName("net.minecraft.entity.Entity")},
            "addEntityToWorld",
            "func_73027_a")
        .invoke(world, -887728, target);
    return target;
  }

  private static void inventory(
      ClientRuntime runtime,
      Object game,
      Object player,
      Object[] items,
      Object auto,
      Object timing,
      ByteBuffer mouse,
      ByteBuffer keyboard)
      throws Exception {
    set(runtime, "autoclicker", "click-mode", "B4_LEFT");
    set(runtime, "autoclicker", "inventory-cps", "10");
    Object controller = field(game.getClass(), "playerController", "field_71442_b").get(game);
    Object oldType =
        field(controller.getClass(), "currentGameType", "field_78779_k").get(controller);
    java.lang.reflect.Method setType =
        method(
            controller.getClass(),
            new Class<?>[] {oldType.getClass()},
            "setGameType",
            "func_78746_a");
    Object capabilities = field(player.getClass(), "capabilities", "field_71075_bZ").get(player);
    Map<Field, Object> savedCapabilities = new LinkedHashMap<>();
    for (Field capability : capabilities.getClass().getDeclaredFields()) {
      if (java.lang.reflect.Modifier.isStatic(capability.getModifiers())
          || java.lang.reflect.Modifier.isFinal(capability.getModifiers())) continue;
      capability.setAccessible(true);
      savedCapabilities.put(capability, capability.get(capabilities));
    }
    Field open = field(player.getClass(), "openContainer", "field_71070_bA");
    Object oldOpen = open.get(player);
    Object inventory = field(player.getClass(), "inventory", "field_71071_by").get(player);
    java.lang.reflect.Method cursor =
        method(
            inventory.getClass(),
            new Class<?>[] {Class.forName("net.minecraft.item.ItemStack")},
            "setItemStack",
            "func_70437_b");
    Object oldCursor =
        method(inventory.getClass(), "getItemStack", "func_70445_o").invoke(inventory);
    Field mouseX = field(Mouse.class, "x"), mouseY = field(Mouse.class, "y");
    int oldX = mouseX.getInt(null), oldY = mouseY.getInt(null);
    try {
      // The flat smoke world is creative. B4 excludes GuiContainerCreative, so use
      // a real survival inventory instead of broadening the production GUI predicate.
      setType.invoke(controller, oldType.getClass().getField("SURVIVAL").get(null));
      open.set(
          player, field(player.getClass(), "inventoryContainer", "field_71069_bz").get(player));
      cursor.invoke(inventory, new Object[] {null});
      Object gui =
          Class.forName("net.minecraft.client.gui.inventory.GuiInventory")
              .getConstructor(Class.forName("net.minecraft.entity.player.EntityPlayer"))
              .newInstance(player);
      method(
              game.getClass(),
              new Class<?>[] {Class.forName("net.minecraft.client.gui.GuiScreen")},
              "displayGuiScreen",
              "func_147108_a")
          .invoke(game, gui);
      check(
          field(game.getClass(), "currentScreen", "field_71462_r").get(game) == gui,
          "survival inventory fixture remains the B4-supported GuiInventory");
      Arrays.fill(items, null);
      items[9] = stack("stone");
      int width = field(gui.getClass(), "width", "field_146294_l").getInt(gui),
          height = field(gui.getClass(), "height", "field_146295_m").getInt(gui);
      int left = field(gui.getClass(), "guiLeft", "field_147003_i").getInt(gui),
          top = field(gui.getClass(), "guiTop", "field_147009_r").getInt(gui);
      int displayWidth = field(game.getClass(), "displayWidth", "field_71443_c").getInt(game),
          displayHeight = field(game.getClass(), "displayHeight", "field_71440_d").getInt(game);
      mouseX.setInt(null, (left + 16) * displayWidth / width);
      mouseY.setInt(null, (height - (top + 92) - 1) * displayHeight / height);
      mouse.put(0, (byte) 1);
      keyboard.put(42, (byte) 1);
      due(timing);
      render(runtime);
      check(
          field(field(auto.getClass(), "b4").get(auto).getClass(), "inventoryClick")
              .getBoolean(field(auto.getClass(), "b4").get(auto)),
          "Shift+held inventory mouse schedules one transfer");
      runtime.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      check(
          items[9] == null && Arrays.stream(items).filter(Objects::nonNull).count() == 1,
          "scheduled inventory click invokes vanilla Shift transfer at cursor");
      keyboard.put(42, (byte) 0);
      render(runtime);
      check(
          field(timing.getClass(), "nextPress").getLong(timing) == 0,
          "inventory timing clears when Shift is released");
    } finally {
      mouseX.setInt(null, oldX);
      mouseY.setInt(null, oldY);
      keyboard.put(42, (byte) 0);
      mouse.put(0, (byte) 0);
      setType.invoke(controller, oldType);
      for (Map.Entry<Field, Object> saved : savedCapabilities.entrySet())
        saved.getKey().set(capabilities, saved.getValue());
      open.set(player, oldOpen);
      cursor.invoke(inventory, new Object[] {oldCursor});
    }
  }

  private static void reach(ClientRuntime runtime, Object player, Object input, Object[] items)
      throws Exception {
    set(runtime, "reach", "min-range", "4");
    set(runtime, "reach", "max-range", "4");
    set(runtime, "reach", "activation", "WEAPON,MOVING");
    enable(runtime, "reach");
    Field forward = field(input.getClass(), "moveForward", "field_78900_b"),
        side = field(input.getClass(), "moveStrafe", "field_78902_a");
    forward.setFloat(input, 0);
    side.setFloat(input, 0);
    items[0] = stack("diamond_sword");
    check(
        runtime.clientEvents().post(new MouseOverEvent()).range() == 3,
        "Reach moving gate rejects stationary input");
    forward.setFloat(input, 1);
    check(
        runtime.clientEvents().post(new MouseOverEvent()).range() == 4,
        "Reach applies when every selected gate passes");
    items[0] = stack("stone");
    check(
        runtime.clientEvents().post(new MouseOverEvent()).range() == 3,
        "Reach weapon gate rejects blocks");
    disable(runtime, "reach");
  }

  private static Object projectileGate(ClientRuntime runtime) throws Exception {
    Object module = runtime.modules().require("autoprojectile").module();
    Object sequence = field(module.getClass(), "sequence").get(module);
    return field(sequence.getClass(), "gate").get(sequence);
  }

  private static boolean canThrow(Object gate) throws Exception {
    return (Boolean) method(gate.getClass(), "canThrow").invoke(gate);
  }

  private static Object walkPacket() throws Exception {
    return Class.forName("net.minecraft.network.play.client.C03PacketPlayer")
        .getConstructor(boolean.class)
        .newInstance(true);
  }

  private static Object releasePacket() throws Exception {
    return digPacket("RELEASE_USE_ITEM");
  }

  private static Object digPacket(String name) throws Exception {
    Class<?> action =
        Class.forName("net.minecraft.network.play.client.C07PacketPlayerDigging$Action");
    Class<?> pos = Class.forName("net.minecraft.util.BlockPos");
    Class<?> face = Class.forName("net.minecraft.util.EnumFacing");
    return Class.forName("net.minecraft.network.play.client.C07PacketPlayerDigging")
        .getConstructor(action, pos, face)
        .newInstance(
            action.getField(name).get(null),
            field(pos, "ORIGIN", "field_177992_a").get(null),
            face.getField("DOWN").get(null));
  }

  private static Object swordUsePacket() throws Exception {
    return Class.forName("net.minecraft.network.play.client.C08PacketPlayerBlockPlacement")
        .getConstructor(Class.forName("net.minecraft.item.ItemStack"))
        .newInstance(stack("diamond_sword"));
  }

  private static void projectileFacts(ClientRuntime runtime, Object game, Object target)
      throws Exception {
    Object handler = method(game.getClass(), "getNetHandler", "func_147114_u").invoke(game);
    Object network =
        method(handler.getClass(), "getNetworkManager", "func_147298_b").invoke(handler);
    check(
        (Boolean) method(network.getClass(), "isLocalChannel", "func_150731_c").invoke(network),
        "projectile facts exercise the real local PLAY channel");
    enable(runtime, "autoprojectile");
    Object gate = projectileGate(runtime);
    try {
      send(walkPacket());
      send(useEntity(target, "ATTACK"));
      check(!canThrow(gate), "real local attack closes AutoProjectile's current action window");
      send(walkPacket());
      check(canThrow(gate), "real local walking attempt starts the next projectile window");
      Object attack = useEntity(target, "ATTACK");
      perihelion.integration.minecraft189.network.RawHookBridge.replay(
          () -> {
            try {
              send(attack);
            } catch (Exception failure) {
              throw new AssertionError(failure);
            }
          });
      check(canThrow(gate), "silent attack replay cannot create a fresh projectile action fact");
      Object controls = perihelion.integration.minecraft189.HookBridge.gameControls();
      java.lang.reflect.Method observe =
          method(
              controls.getClass(),
              new Class<?>[] {
                Class.forName("net.minecraft.network.NetworkManager"),
                Class.forName("net.minecraft.network.Packet")
              },
              "observeClientSend");
      java.util.concurrent.atomic.AtomicReference<Throwable> failure =
          new java.util.concurrent.atomic.AtomicReference<>();
      Thread foreign =
          new Thread(
              () -> {
                try {
                  observe.invoke(controls, network, attack);
                } catch (Throwable problem) {
                  failure.set(problem);
                }
              },
              "projectile-fact-thread-guard");
      foreign.start();
      foreign.join(5000);
      check(
          !foreign.isAlive() && failure.get() == null && canThrow(gate),
          "foreign-thread observations cannot alter the client projectile window");
      Class<?> direction = Class.forName("net.minecraft.network.EnumPacketDirection");
      Object stale =
          network
              .getClass()
              .getConstructor(direction)
              .newInstance(direction.getField("CLIENTBOUND").get(null));
      observe.invoke(controls, stale, attack);
      check(canThrow(gate), "a different connection cannot contribute projectile action facts");
      send(swordUsePacket());
      check(
          !canThrow(gate) && (Boolean) method(gate.getClass(), "serverBlocking").invoke(gate),
          "local sword use records both blocking and its one-walking-pass gate");
      send(walkPacket());
      check(canThrow(gate), "walking clears sword-use delay while preserving server block state");
      send(releasePacket());
      check(
          !canThrow(gate) && !(Boolean) method(gate.getClass(), "serverBlocking").invoke(gate),
          "local release clears server block state and waits one walking pass");
      send(walkPacket());
      send(
          Class.forName("net.minecraft.network.play.client.C0DPacketCloseWindow")
              .getConstructor(int.class)
              .newInstance(0));
      check(!canThrow(gate), "local inventory traffic also closes the projectile action window");
      send(walkPacket());
      check(canThrow(gate), "the inventory action window clears on the next real walking attempt");
      send(digPacket("START_DESTROY_BLOCK"));
      check(canThrow(gate), "Yuri's projectile gate deliberately excludes ordinary mining traffic");
      send(walkPacket());
      check(
          canThrow(gate),
          "walking preserves an already eligible ordinary-mining projectile window");
    } finally {
      disable(runtime, "autoprojectile");
    }
  }

  private static void auraFacts(ClientRuntime runtime, Object game) throws Exception {
    enable(runtime, "aura");
    Object aura = runtime.modules().require("aura").module();
    java.util.concurrent.atomic.AtomicBoolean window =
        (java.util.concurrent.atomic.AtomicBoolean)
            field(aura.getClass(), "blockSinceWalk").get(aura);
    try {
      send(walkPacket());
      check(!window.get(), "Aura's fresh local walking attempt clears the block window");
      send(swordUsePacket());
      check(window.get(), "Aura observes an actual local C08 before its next walking attempt");
      send(walkPacket());
      check(!window.get(), "Aura's local C08 window lasts exactly until walking");
      send(digPacket("START_DESTROY_BLOCK"));
      check(window.get(), "Aura observes local mining as source BadPackets block traffic");
      send(walkPacket());
      send(releasePacket());
      check(window.get(), "Aura observes a foreign local sword release as block traffic");
      send(walkPacket());
      Object owned = field(aura.getClass(), "ownedUse").get(aura);
      field(aura.getClass(), "ownedUse").set(aura, stack("diamond_sword"));
      try {
        method(aura.getClass(), "releaseHypixel").invoke(aura);
        check(!window.get(), "Aura's own Hypixel release preserves its explicit source exception");
      } finally {
        field(aura.getClass(), "ownedUse").set(aura, owned);
      }
      perihelion.integration.minecraft189.network.RawHookBridge.replay(
          () -> {
            try {
              send(swordUsePacket());
            } catch (Exception error) {
              throw new AssertionError(error);
            }
          });
      check(!window.get(), "replaying a held packet cannot create a fresh Aura block fact");
    } finally {
      disable(runtime, "aura");
    }
  }

  private static void projectileTransport(ClientRuntime runtime, GameplayLoopback peer)
      throws Exception {
    enable(runtime, "autoprojectile");
    try {
      int first = peer.received.size();
      send(swordUsePacket());
      send(releasePacket());
      peer.await(first + 2);
      check(
          peer.received
                  .get(first)
                  .getClass()
                  .getSimpleName()
                  .equals("C08PacketPlayerBlockPlacement")
              && peer.received
                  .get(first + 1)
                  .getClass()
                  .getSimpleName()
                  .equals("C07PacketPlayerDigging"),
          "recording facts before raw interception still lets one valid remote sword release"
              + " through");
      send(releasePacket());
      flush(peer.client);
      check(
          peer.received.size() == first + 2,
          "raw interception still cancels a redundant remote sword release");
      wireBoundary(peer);
    } finally {
      disable(runtime, "autoprojectile");
    }
  }

  private static void blink(ClientRuntime runtime, GameplayLoopback peer, Object target)
      throws Exception {
    set(runtime, "blink", "cancel-received-packets", "false");
    set(runtime, "blink", "release-on", "ATTACK");
    enable(runtime, "blink");
    runtime.clientEvents().post(PlayerControlEvent.AFTER_MOTION);
    Object movement =
        Class.forName("net.minecraft.network.play.client.C03PacketPlayer")
            .getConstructor(boolean.class)
            .newInstance(true);
    Object hit = useEntity(target, "ATTACK");
    int before = peer.received.size();
    send(movement);
    send(hit);
    flush(peer.client);
    check(
        peer.received.size() == before,
        "Blink holds movement and triggering attack before client handoff");
    runtime.tasks().drain();
    peer.await(before + 2);
    check(
        runtime.modules().require("blink").state() == perihelion.core.module.ModuleState.DISABLED,
        "queued attack disables Blink on client thread");
    check(
        peer.received.get(before).getClass().getSimpleName().equals("C03PacketPlayer")
            && peer.received
                .get(before + 1)
                .getClass()
                .getSimpleName()
                .equals("C02PacketUseEntity"),
        "Blink flush preserves movement-before-attack TCP order");
  }

  private static void lagRange(
      ClientRuntime runtime,
      GameplayLoopback peer,
      Object player,
      Object target,
      Object input,
      Object[] items,
      Object forwardKey,
      Field pressed)
      throws Exception {
    Object capabilities = field(player.getClass(), "capabilities", "field_71075_bZ").get(player);
    Field allowEdit = field(capabilities.getClass(), "allowEdit", "field_75099_e"),
        allowFlying = field(capabilities.getClass(), "allowFlying", "field_75101_c");
    boolean oldEdit = allowEdit.getBoolean(capabilities),
        oldFlying = allowFlying.getBoolean(capabilities);
    try {
      allowEdit.setBoolean(capabilities, true);
      allowFlying.setBoolean(capabilities, false);
      field(player.getClass(), "onGround", "field_70122_E").setBoolean(player, true);
      field(player.getClass(), "motionX", "field_70159_w").setDouble(player, 0);
      field(player.getClass(), "motionZ", "field_70179_y").setDouble(player, .1);
      field(input.getClass(), "moveForward", "field_78900_b").setFloat(input, 1);
      field(input.getClass(), "moveStrafe", "field_78902_a").setFloat(input, 0);
      pressed.setBoolean(forwardKey, true);
      items[0] = stack("diamond_sword");
      set(runtime, "lagrange", "mode", "B4");
      enable(runtime, "lagrange");
      runtime.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      Object b4 =
          field(runtime.modules().require("lagrange").module().getClass(), "b4")
              .get(runtime.modules().require("lagrange").module());
      Object state = field(b4.getClass(), "state").get(b4);
      check(
          (Boolean) method(state.getClass(), "active").invoke(state),
          "B4 Lag Range starts toward an eligible player without Aura");
      long delay = ((Number) method(state.getClass(), "delay").invoke(state)).longValue();
      check(delay >= 88 && delay <= 112, "close Lag Range samples donor 100ms plus/minus12");
      int before = peer.received.size();
      send(
          Class.forName("net.minecraft.network.play.client.C01PacketChatMessage")
              .getConstructor(String.class)
              .newInstance("B4 fixture"));
      send(
          Class.forName("net.minecraft.network.play.client.C03PacketPlayer")
              .getConstructor(boolean.class)
              .newInstance(true));
      flush(peer.client);
      runtime.tasks().drain();
      peer.await(before + 2);
      check(
          !(Boolean) method(state.getClass(), "active").invoke(state),
          "interaction flush stops Lag Range after the following movement");
      runtime.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      check(
          (Boolean) method(state.getClass(), "active").invoke(state),
          "Lag Range can start a fresh source-defined approach session");
      send(
          Class.forName("net.minecraft.network.play.client.C01PacketChatMessage")
              .getConstructor(String.class)
              .newInstance("B4 stale callback fixture"));
      send(
          Class.forName("net.minecraft.network.play.client.C03PacketPlayer")
              .getConstructor(boolean.class)
              .newInstance(true));
      flush(peer.client);
      set(runtime, "lagrange", "mode", "LAG");
      set(runtime, "lagrange", "mode", "B4");
      runtime.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      long replacement = ((Number) method(state.getClass(), "session").invoke(state)).longValue();
      runtime.tasks().drain();
      check(
          (Boolean) method(state.getClass(), "active").invoke(state)
              && ((Number) method(state.getClass(), "session").invoke(state)).longValue()
                  == replacement,
          "an old deferred interaction cannot stop the replacement Lag Range session");
      attributionRace(runtime, peer, state);
      items[0] = stack("apple");
      runtime.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      check(
          !(Boolean) method(state.getClass(), "active").invoke(state),
          "holding a consumable immediately stops B4 Lag Range");
      items[0] = stack("diamond_sword");
      runtime.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      field(player.getClass(), "motionZ", "field_70179_y").setDouble(player, -.1);
      for (int i = 0; i < 3; i++) runtime.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      check(
          (Boolean) method(state.getClass(), "active").invoke(state),
          "Lag Range tolerates three retreat intervals");
      runtime.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      check(
          !(Boolean) method(state.getClass(), "active").invoke(state),
          "fourth retreat interval stops B4 Lag Range");
      field(player.getClass(), "motionZ", "field_70179_y").setDouble(player, .1);
      runtime.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      check(
          (Boolean) method(state.getClass(), "active").invoke(state),
          "Lag Range starts the entity identity fixture");
      Object world = field(player.getClass(), "worldObj", "field_70170_p").get(player);
      method(world.getClass(), new Class<?>[] {int.class}, "removeEntityFromWorld", "func_73028_b")
          .invoke(world, -887728);
      target(world, player); // A different player instance deliberately reuses the same numeric ID.
      runtime.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      runtime.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      check(
          (Boolean) method(state.getClass(), "active").invoke(state),
          "replaced target follows the two-interval missing grace");
      runtime.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      check(
          !(Boolean) method(state.getClass(), "active").invoke(state),
          "reused entity ID cannot keep the old target instance alive");
      disable(runtime, "lagrange");
      foreignBufferAttribution(runtime, peer, target);
    } finally {
      allowEdit.setBoolean(capabilities, oldEdit);
      allowFlying.setBoolean(capabilities, oldFlying);
    }
  }

  private static void attributionRace(ClientRuntime runtime, GameplayLoopback peer, Object state)
      throws Exception {
    Object movement =
        Class.forName("net.minecraft.network.play.client.C03PacketPlayer")
            .getConstructor(boolean.class)
            .newInstance(true);
    Object manager = peer.manager.get();
    java.lang.reflect.Method rawSend =
        method(
            manager.getClass(),
            new Class<?>[] {Class.forName("net.minecraft.network.Packet")},
            "sendPacket",
            "func_179290_a");
    java.util.concurrent.CountDownLatch captured = new java.util.concurrent.CountDownLatch(1),
        resume = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.atomic.AtomicReference<Throwable> failure =
        new java.util.concurrent.atomic.AtomicReference<>();
    perihelion.core.event.EventOwner observer =
        new perihelion.core.event.EventOwner(
            "b4-attribution-race", problem -> failure.compareAndSet(null, problem));
    observer.activate();
    AutoCloseable subscription =
        runtime
            .networkEvents()
            .subscribe(
                observer,
                perihelion.integration.minecraft189.event.PacketSendEvent.class,
                -1050,
                true,
                event -> {
                  if (event.getClass().getMethod("packet").invoke(event) == movement
                      && event.isDeferred()) {
                    captured.countDown();
                    if (!resume.await(3, java.util.concurrent.TimeUnit.SECONDS))
                      throw new AssertionError("B4 attribution fixture release timed out");
                  }
                });
    Thread sender =
        new Thread(
            () -> {
              try {
                rawSend.invoke(manager, movement);
              } catch (Throwable problem) {
                failure.compareAndSet(null, problem);
              }
            },
            "B4-attribution-fixture");
    sender.setDaemon(true);
    try {
      send(
          Class.forName("net.minecraft.network.play.client.C01PacketChatMessage")
              .getConstructor(String.class)
              .newInstance("B4 attribution fixture"));
      sender.start();
      check(
          captured.await(3, java.util.concurrent.TimeUnit.SECONDS),
          "foreign send pauses after own-buffer capture and before later observers");
      set(runtime, "lagrange", "mode", "LAG");
      set(runtime, "lagrange", "mode", "B4");
      runtime.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      long replacement = ((Number) method(state.getClass(), "session").invoke(state)).longValue();
      resume.countDown();
      sender.join(3000);
      check(
          !sender.isAlive() && failure.get() == null,
          "latched foreign raw callback completes without faults: " + failure.get());
      runtime.tasks().drain();
      check(
          (Boolean) method(state.getClass(), "active").invoke(state)
              && ((Number) method(state.getClass(), "session").invoke(state)).longValue()
                  == replacement,
          "a mode switch between capture and later observation cannot misattribute an old packet");
    } finally {
      resume.countDown();
      sender.join(3000);
      subscription.close();
      observer.close();
    }
  }

  private static void foreignBufferAttribution(
      ClientRuntime runtime, GameplayLoopback peer, Object target) throws Exception {
    set(runtime, "blink", "release-on", "");
    enable(runtime, "blink");
    runtime.clientEvents().post(PlayerControlEvent.AFTER_MOTION);
    enable(runtime, "lagrange");
    runtime.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
    Object b4 =
        field(runtime.modules().require("lagrange").module().getClass(), "b4")
            .get(runtime.modules().require("lagrange").module());
    Object state = field(b4.getClass(), "state").get(b4);
    try {
      check(
          (Boolean) method(state.getClass(), "active").invoke(state),
          "B4 session is active behind an earlier Blink scope");
      send(
          Class.forName("net.minecraft.network.play.client.C01PacketChatMessage")
              .getConstructor(String.class)
              .newInstance("B4 foreign buffer fixture"));
      send(
          Class.forName("net.minecraft.network.play.client.C03PacketPlayer")
              .getConstructor(boolean.class)
              .newInstance(true));
      flush(peer.client);
      runtime.tasks().drain();
      check(
          (Boolean) method(state.getClass(), "active").invoke(state),
          "packets deferred only by Blink cannot release B4 Lag Range");
    } finally {
      disable(runtime, "blink");
      disable(runtime, "lagrange");
    }
    wireBoundary(peer);
    // Reverse registration and ownership: Blink must not claim another scope's
    // deferred attack as its own selected release interaction.
    enable(runtime, "lagrange");
    runtime.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
    Object inverseB4 =
        field(runtime.modules().require("lagrange").module().getClass(), "b4")
            .get(runtime.modules().require("lagrange").module());
    Object inverseState = field(inverseB4.getClass(), "state").get(inverseB4);
    set(runtime, "blink", "release-on", "ATTACK");
    enable(runtime, "blink");
    runtime.clientEvents().post(PlayerControlEvent.AFTER_MOTION);
    try {
      check(
          (Boolean) method(inverseState.getClass(), "active").invoke(inverseState),
          "earlier Lag Range owns the inverse attribution session");
      int before = peer.received.size();
      send(
          Class.forName("net.minecraft.network.play.client.C03PacketPlayer")
              .getConstructor(boolean.class)
              .newInstance(true));
      send(useEntity(target, "ATTACK"));
      flush(peer.client);
      runtime.tasks().drain();
      check(
          runtime.modules().require("blink").state() == perihelion.core.module.ModuleState.ENABLED,
          "an attack captured only by Lag Range cannot disable later Blink");
      disable(runtime, "lagrange");
      peer.await(before + 2);
      check(
          peer.received.get(before).getClass().getSimpleName().equals("C03PacketPlayer")
              && peer.received
                  .get(before + 1)
                  .getClass()
                  .getSimpleName()
                  .equals("C02PacketUseEntity"),
          "inverse ownership keeps movement and attack ordered in the original scope");
    } finally {
      disable(runtime, "blink");
      disable(runtime, "lagrange");
    }
  }

  private static void wireBoundary(GameplayLoopback peer) throws Exception {
    // Closing the preceding scopes enqueues their flushes on the TCP event loop.
    // Its local write barrier cannot guarantee the receiving peer has decoded them.
    // A marker received on the same ordered stream establishes that boundary.
    int first = peer.received.size(), marker = 0x4b34c017;
    send(
        Class.forName("net.minecraft.network.play.client.C00PacketKeepAlive")
            .getConstructor(int.class)
            .newInstance(marker));
    flush(peer.client);
    long until = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
    do {
      for (int i = first; i < peer.received.size(); i++) {
        Object packet = peer.received.get(i);
        if (packet.getClass().getSimpleName().equals("C00PacketKeepAlive")
            && ((Number) method(packet.getClass(), "getKey", "func_149460_c").invoke(packet))
                    .intValue()
                == marker) {
          check(
              true, "previous ownership phase fully reaches the TCP peer before the next baseline");
          return;
        }
      }
      Thread.sleep(2);
    } while (System.nanoTime() < until);
    throw new AssertionError("ownership wire-boundary marker did not reach the TCP peer");
  }

  private static Object useEntity(Object target, String action) throws Exception {
    Class<?> type = Class.forName("net.minecraft.network.play.client.C02PacketUseEntity$Action");
    return Class.forName("net.minecraft.network.play.client.C02PacketUseEntity")
        .getConstructor(Class.forName("net.minecraft.entity.Entity"), type)
        .newInstance(target, type.getField(action).get(null));
  }

  private static void send(Object packet) throws Exception {
    method(
            Class.forName("perihelion.integration.minecraft189.module.gameplay.GameActions"),
            new Class<?>[] {Class.forName("net.minecraft.network.Packet")},
            "send")
        .invoke(null, packet);
  }

  private static void due(Object timing) throws Exception {
    field(timing.getClass(), "nextPress").setLong(timing, 2);
    field(timing.getClass(), "nextRelease").setLong(timing, 2);
  }

  private static void render(ClientRuntime runtime) {
    runtime.clientEvents().post(new RenderTickEvent(RenderTickEvent.Phase.START, 0));
  }

  private static void enable(ClientRuntime runtime, String id) {
    runtime.modules().setEnabled(id, true);
  }

  private static void disable(ClientRuntime runtime, String id) {
    runtime.modules().setEnabled(id, false);
  }

  private static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
    checks++;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void restore(ClientRuntime runtime, String id, Setting setting, Object value) {
    runtime.modules().setSetting(id, setting, value);
  }
}
