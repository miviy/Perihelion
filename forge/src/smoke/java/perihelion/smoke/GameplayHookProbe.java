package perihelion.smoke;

import java.lang.reflect.*;
import java.nio.*;
import java.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import perihelion.core.event.*;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.*;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.*;

/** Focused behavior checks on the shipped, transformed game classes in a disposable world. */
public final class GameplayHookProbe {
  private static int checks;

  private GameplayHookProbe() {}

  public static void run(ClientRuntime runtime) throws Exception {
    Class<?> gameType = Class.forName("net.minecraft.client.Minecraft");
    Object game = method(gameType, "getMinecraft", "func_71410_x").invoke(null);
    Object player = field(gameType, "thePlayer", "field_71439_g").get(game);
    Object world = field(gameType, "theWorld", "field_71441_e").get(game);
    Object settings = field(gameType, "gameSettings", "field_71474_y").get(game);
    Object input = field(player.getClass(), "movementInput", "field_71158_b").get(player);
    Object inventory = field(player.getClass(), "inventory", "field_71071_by").get(player);
    Object[] items =
        (Object[]) field(inventory.getClass(), "mainInventory", "field_70462_a").get(inventory);
    Object[] savedItems = items.clone();
    Field screen = field(gameType, "currentScreen", "field_71462_r");
    Object savedScreen = screen.get(game);
    Field ground = field(player.getClass(), "onGround", "field_70122_E");
    boolean grounded = ground.getBoolean(player);
    Field yaw = field(player.getClass(), "rotationYaw", "field_70177_z");
    float savedYaw = yaw.getFloat(player);
    Field forward = field(input.getClass(), "moveForward", "field_78900_b"),
        side = field(input.getClass(), "moveStrafe", "field_78902_a");
    float savedForward = forward.getFloat(input), savedSide = side.getFloat(input);
    Field perspective = field(settings.getClass(), "thirdPersonView", "field_74320_O");
    int savedPerspective = perspective.getInt(settings);
    Field mx = field(player.getClass(), "motionX", "field_70159_w"),
        my = field(player.getClass(), "motionY", "field_70181_x"),
        mz = field(player.getClass(), "motionZ", "field_70179_y");
    double dx = mx.getDouble(player), dy = my.getDouble(player), dz = mz.getDouble(player);
    double x = field(player.getClass(), "posX", "field_70165_t").getDouble(player),
        y = field(player.getClass(), "posY", "field_70163_u").getDouble(player),
        z = field(player.getClass(), "posZ", "field_70161_v").getDouble(player);
    int listeners = runtime.clientEvents().subscriptionCount();
    System.out.println(
        "PERIHELION_SMOKE GAMEPLAY_START listeners="
            + listeners
            + " active="
            + activeModules(runtime));
    List<Integer> fixtureIds = new ArrayList<>();
    try {
      screen.set(game, null);
      ground.setBoolean(player, false);
      yaw.setFloat(player, 0);
      forward.setFloat(input, 1);
      side.setFloat(input, 0);
      enable(runtime, "flight");
      method(
              player.getClass(),
              new Class<?>[] {float.class, float.class, float.class},
              "moveFlying",
              "func_70060_a")
          .invoke(player, 0f, 1f, .1f);
      check(
          Math.abs(mx.getDouble(player)) < 1e-5 && Math.abs(mz.getDouble(player) - .9) < 1e-5,
          "Flight replaces actual moveFlying with configured speed");
      check(my.getDouble(player) == 0, "Flight holds vertical motion");
      disable(runtime, "flight");
      enable(runtime, "stasis");
      method(
              player.getClass(),
              new Class<?>[] {double.class, double.class, double.class},
              "moveEntity",
              "func_70091_d")
          .invoke(player, .4, .2, .3);
      check(
          field(player.getClass(), "posX", "field_70165_t").getDouble(player) == x
              && field(player.getClass(), "posY", "field_70163_u").getDouble(player) == y,
          "Stasis suppresses transformed movement without recursion");
      disable(runtime, "stasis");
      ground.setBoolean(player, true);
      Field height = field(player.getClass(), "stepHeight", "field_70138_W");
      float oldHeight = height.getFloat(player);
      enable(runtime, "step");
      runtime.clientEvents().post(PlayerControlEvent.BEFORE_MOTION);
      check(height.getFloat(player) == 1, "Step applies its height");
      disable(runtime, "step");
      check(height.getFloat(player) == oldHeight, "Step restores original height");

      Method duration = method(player.getClass(), "getArmSwingAnimationEnd", "func_82166_i");
      int vanilla = ((Number) duration.invoke(player)).intValue();
      set(runtime, "camera", "slowdown", "3");
      set(runtime, "camera", "x", "0.5");
      enable(runtime, "camera");
      check(
          ((Number) duration.invoke(player)).intValue() == vanilla * 3,
          "Camera modifies actual swing-duration hook");
      HandTransformEvent hand = new HandTransformEvent();
      runtime.clientEvents().post(hand);
      check(hand.x == .5, "Camera applies configured hand transform");
      OverlayEffectEvent fire = new OverlayEffectEvent(OverlayEffectEvent.Kind.FIRE);
      runtime.clientEvents().post(fire);
      check(fire.isCancelled(), "Camera suppresses fire overlay");
      disable(runtime, "camera");
      check(
          ((Number) duration.invoke(player)).intValue() == vanilla,
          "Swing duration restores on disable");
      enable(runtime, "freelook");
      MouseLookEvent mouse = new MouseLookEvent(100, 50);
      runtime.clientEvents().post(mouse);
      CameraViewEvent view = new CameraViewEvent(0, 0);
      runtime.clientEvents().post(view);
      check(
          mouse.isCancelled()
              && view.isCancelled()
              && Math.abs(view.yaw() - 15) < 1e-4
              && yaw.getFloat(player) == 0,
          "Free Look rotates only camera state");
      disable(runtime, "freelook");

      // Inject owned PLAY snapshots through the production queue; raw socket hooks have their
      // own fixture.
      Method worldTime = method(world.getClass(), "getWorldTime", "func_72820_D");
      Method setTime =
          method(world.getClass(), new Class<?>[] {long.class}, "setWorldTime", "func_72877_b");
      long oldTime = ((Number) worldTime.invoke(world)).longValue();
      enable(runtime, "blink");
      motion(player);
      Class<?> packetType = Class.forName("net.minecraft.network.Packet");
      for (long time : new long[] {2222, 3333}) {
        Object packet =
            Class.forName("net.minecraft.network.play.server.S03PacketTimeUpdate")
                .getConstructor(long.class, long.class, boolean.class)
                .newInstance(0L, time, true);
        CancellableEvent event =
            (CancellableEvent)
                PacketReceiveEvent.class.getConstructor(packetType).newInstance(packet);
        runtime.networkEvents().post(event);
        check(event.isCancelled(), "Blink captures incoming snapshot");
      }
      check(
          ((Number) worldTime.invoke(world)).longValue() == oldTime,
          "Blink holds game delivery while enabled");
      disable(runtime, "blink");
      check(
          ((Number) worldTime.invoke(world)).longValue() == 3333,
          "Blink disable replays snapshots in order on client thread");
      enable(runtime, "blink");
      motion(player);
      Object stalePacket =
          Class.forName("net.minecraft.network.play.server.S03PacketTimeUpdate")
              .getConstructor(long.class, long.class, boolean.class)
              .newInstance(0L, 4444L, true);
      CancellableEvent staleEvent =
          (CancellableEvent)
              PacketReceiveEvent.class.getConstructor(packetType).newInstance(stalePacket);
      runtime.networkEvents().post(staleEvent);
      check(staleEvent.isCancelled(), "Blink captures the stale-world fixture");
      runtime.updateWorld(false, perihelion.core.context.ConnectionContext.DISCONNECTED, true);
      check(
          ((Number) worldTime.invoke(world)).longValue() == 3333,
          "World loss discards buffered snapshots instead of replaying them");
      runtime.updateWorld(
          true,
          new perihelion.core.context.ConnectionContext(
              perihelion.core.context.ConnectionContext.Kind.SINGLEPLAYER, ""),
          true);
      disable(runtime, "blink");
      check(
          ((Number) worldTime.invoke(world)).longValue() == 3333,
          "Fresh activation cannot replay an old-world snapshot");
      setTime.invoke(world, oldTime);
      Object handler = method(gameType, "getNetHandler", "func_147114_u").invoke(game);
      Field handlerWorld = field(handler.getClass(), "clientWorldController", "field_147300_g");
      Object boundWorld = handlerWorld.get(handler);
      try {
        handlerWorld.set(handler, null);
        Class<?> chunk = Class.forName("net.minecraft.network.play.server.S21PacketChunkData");
        method(handler.getClass(), new Class<?>[] {chunk}, "handleChunkData", "func_147263_a")
            .invoke(handler, chunk.newInstance());
        Class<?> bulk = Class.forName("net.minecraft.network.play.server.S26PacketMapChunkBulk");
        method(handler.getClass(), new Class<?>[] {bulk}, "handleMapChunkBulk", "func_147269_a")
            .invoke(handler, bulk.newInstance());
        check(
            true,
            "Retired handler ignores queued chunk and bulk delivery after client-thread"
                + " handoff");
      } finally {
        handlerWorld.set(handler, boundWorld);
      }

      Class<?> entity = Class.forName("net.minecraft.entity.Entity"),
          worldType = Class.forName("net.minecraft.world.World");
      Object pig =
          Class.forName("net.minecraft.entity.passive.EntityPig")
              .getConstructor(worldType)
              .newInstance(world);
      method(
              pig.getClass(),
              new Class<?>[] {double.class, double.class, double.class},
              "setPosition",
              "func_70107_b")
          .invoke(pig, x, y, z + 2);
      add(world, pig, -7654321);
      fixtureIds.add(-7654321);
      set(runtime, "aura", "targets", "ANIMALS");
      set(runtime, "aura", "through-walls", "true");
      set(runtime, "aura", "simulate-mouse-clicks", "false");
      set(runtime, "aura", "ray-cast", "false");
      enable(runtime, "aura");
      runtime.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      Object aura = runtime.modules().require("aura").module();
      check(
          method(aura.getClass(), "target").invoke(aura) == pig,
          "Aura chooses the eligible nearby target");
      set(runtime, "aura", "fov", "30");
      yaw.setFloat(player, 180);
      runtime.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      check(
          method(aura.getClass(), "target").invoke(aura) == null,
          "Aura FOV excludes targets behind the view");
      yaw.setFloat(player, 0);
      set(runtime, "aura", "weapons-only", "true");
      Arrays.fill(items, null);
      runtime.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      check(
          method(aura.getClass(), "target").invoke(aura) == null,
          "Aura weapon filter rejects empty hand");
      set(runtime, "aura", "weapons-only", "false");
      runtime.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      int[] attacks = {0};
      ModuleContext context =
          (ModuleContext)
              field(ModuleEntry.class, "context").get(runtime.modules().require("aura"));
      context.listen(PlayerAttackEvent.class, e -> attacks[0]++);
      field(aura.getClass(), "nextAttack").setLong(aura, 0);
      motion(player);
      HookBridge.gameControls().beforeInput();
      motion(player);
      check(attacks[0] == 1, "Aura reaches transformed controller attack once");
      disable(runtime, "aura");
      check(
          method(aura.getClass(), "target").invoke(aura) == null
              && !runtime.controls().rotation().isPresent(),
          "Aura releases target and rotation");

      Arrays.fill(items, null);
      Object potion = stack("potion");
      method(potion.getClass(), new Class<?>[] {int.class}, "setItemDamage", "func_77964_b")
          .invoke(potion, 16385);
      items[9] = potion;
      Object drinkingPotion = stack("potion");
      items[10] = drinkingPotion;
      screen.set(
          game,
          Class.forName("net.minecraft.client.gui.inventory.GuiInventory")
              .getConstructor(Class.forName("net.minecraft.entity.player.EntityPlayer"))
              .newInstance(player));
      enable(runtime, "refill");
      runtime.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      int refilled = 0;
      for (int i = 0; i < 9; i++)
        if (sameItem(items[i], potion) && count(items[i]) == 1) refilled++;
      check(
          refilled == 1 && items[9] == null && items[10] == drinkingPotion,
          "Refill shift-clicks splash potions into a hotbar slot without dropping drinking"
              + " potions");
      disable(runtime, "refill");
      Arrays.fill(items, null);
      Object sword = stack("diamond_sword");
      items[9] = sword;
      set(runtime, "manager", "instant", "true");
      enable(runtime, "manager");
      runtime.clientEvents().post(PlayerControlEvent.BEFORE_MOTION);
      check(
          sameItem(items[0], sword) && count(items[0]) == 1 && items[9] == null,
          "Manager sorts the best weapon without duplication");
      disable(runtime, "manager");
      screen.set(game, null);

      donorImprovements(runtime, game, player, settings, items);
      Object profile =
          Class.forName("com.mojang.authlib.GameProfile")
              .getConstructor(UUID.class, String.class)
              .newInstance(UUID.fromString("ffeeddcc-bbaa-4998-8776-554433221100"), "PortProbe");
      Object other =
          Class.forName("net.minecraft.client.entity.EntityOtherPlayerMP")
              .getConstructor(worldType, profile.getClass())
              .newInstance(world, profile);
      method(
              other.getClass(),
              new Class<?>[] {double.class, double.class, double.class},
              "setPosition",
              "func_70107_b")
          .invoke(other, x + 1, y, z + 3);
      add(world, other, -7654322);
      fixtureIds.add(-7654322);
      set(runtime, "aimassist", "require-mouse-down", "false");
      set(runtime, "aimassist", "swords-only", "false");
      set(runtime, "aimassist", "check-block-break", "false");
      set(runtime, "aimassist", "distance", "6");
      set(runtime, "aimassist", "max-angle", "360");
      set(runtime, "aimassist", "horizontal-speed", "10");
      Field assistFocus = field(gameType, "inGameHasFocus", "field_71415_G");
      boolean assistFocused = assistFocus.getBoolean(game);
      assistFocus.setBoolean(game, true);
      try {
        yaw.setFloat(player, 90);
        enable(runtime, "aimassist");
        Object assist = runtime.modules().require("aimassist").module();
        Method frame = method(assist.getClass(), "renderFrame");
        Field stamp = field(assist.getClass(), "lastSimpleNanos");
        for (int i = 0; i < 25; i++) {
          stamp.setLong(assist, System.nanoTime() - 30_000_000L);
          frame.invoke(assist);
        }
        check(
            field(assist.getClass(), "target").get(assist) != null,
            "Aim Assist chooses a player independently of Aura");
        check(
            Math.abs(yaw.getFloat(player) - 90) > .001,
            "Simple Aim Assist commits visible frame-based aiming");
        check(
            !runtime.controls().rotation().isPresent(),
            "Aim Assist retires the committed frame request");
        disable(runtime, "aimassist");
        check(!runtime.controls().rotation().isPresent(), "Aim Assist releases its rotation claim");
      } finally {
        disable(runtime, "aimassist");
        assistFocus.setBoolean(game, assistFocused);
        yaw.setFloat(player, 0);
      }
      perspective.setInt(settings, 1);
      for (String id :
          new String[] {
            "shaderesp",
            "esp",
            "nametags",
            "chams",
            "skeletons",
            "blockesp",
            "breadcrumbs",
            "trajectories",
            "targetesp",
            "bedplates"
          }) {
        enable(runtime, id);
        FloatBuffer color = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_COLOR_CLEAR_VALUE, color);
        IntBuffer viewport = BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        int framebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING),
            program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        runtime
            .clientEvents()
            .post(WorldRenderEvent.class.getConstructor(float.class).newInstance(.5f));
        runtime.clientEvents().post(new HudRenderEvent(.5f, 480, 270, 2));
        runtime.modules().processFaults();
        check(
            runtime.modules().require(id).state() == ModuleState.ENABLED,
            id + " renders without quarantine");
        FloatBuffer after = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_COLOR_CLEAR_VALUE, after);
        IntBuffer afterViewport = BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(GL11.GL_VIEWPORT, afterViewport);
        check(
            framebuffer == GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING)
                && program == GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
            id + " restores framebuffer and program");
        for (int i = 0; i < 4; i++)
          check(
              color.get(i) == after.get(i) && viewport.get(i) == afterViewport.get(i),
              id + " restores clear color and viewport");
        disable(runtime, id);
      }
      DeepGameplayProbe.run(runtime);
      QualityGameplayProbe.run(runtime);
      RotationPipelineProbe.run(runtime);
      CombatFlowProbe.run(runtime);
      SecondParityProbe.run(runtime);
      RefillParityProbe.run(runtime);
      MovementParityProbe.run(runtime);
      FinalParityProbe.run(runtime);
      RecoveryParityProbe.run(runtime);
      FireballParityProbe.run(runtime);
      PacketPositionProbe.run(runtime);
      InvMoveProbe.run(runtime);
      check(
          runtime.clientEvents().subscriptionCount() == listeners,
          "Gameplay checks release all listeners: before="
              + listeners
              + " after="
              + runtime.clientEvents().subscriptionCount()
              + " active="
              + activeModules(runtime));
      System.out.println("PERIHELION_SMOKE GAMEPLAY checks=" + checks + " PASS");
    } finally {
      for (ModuleEntry entry : runtime.modules().entries())
        if (entry.info().requiresWorld()) {
          runtime.modules().setEnabled(entry.info().id(), false);
          for (perihelion.core.setting.Setting<?> setting : entry.module().settings().values())
            reset(runtime, entry.info().id(), setting);
        }
      HookBridge.gameControls().apply();
      for (int id : fixtureIds)
        method(
                world.getClass(),
                new Class<?>[] {int.class},
                "removeEntityFromWorld",
                "func_73028_b")
            .invoke(world, id);
      System.arraycopy(savedItems, 0, items, 0, items.length);
      screen.set(game, savedScreen);
      ground.setBoolean(player, grounded);
      yaw.setFloat(player, savedYaw);
      forward.setFloat(input, savedForward);
      side.setFloat(input, savedSide);
      perspective.setInt(settings, savedPerspective);
      mx.setDouble(player, dx);
      my.setDouble(player, dy);
      mz.setDouble(player, dz);
    }
  }

  private static String activeModules(ClientRuntime runtime) {
    return runtime.modules().entries().stream()
        .filter(entry -> entry.state() == perihelion.core.module.ModuleState.ENABLED)
        .map(entry -> entry.info().id())
        .collect(java.util.stream.Collectors.joining(","));
  }

  private static void donorImprovements(
      ClientRuntime runtime, Object game, Object player, Object settings, Object[] items)
      throws Exception {
    Field focus = field(game.getClass(), "inGameHasFocus", "field_71415_G");
    boolean focused = focus.getBoolean(game);
    Field smooth = field(settings.getClass(), "smoothCamera", "field_74326_T");
    boolean savedSmooth = smooth.getBoolean(settings);
    Object food = method(player.getClass(), "getFoodStats", "func_71024_bL").invoke(player);
    Field level = field(food.getClass(), "foodLevel", "field_75127_a");
    int savedFood = level.getInt(food);
    Object capabilities = field(player.getClass(), "capabilities", "field_71075_bZ").get(player);
    Field invulnerable = field(capabilities.getClass(), "disableDamage", "field_75102_a");
    boolean savedInvulnerable = invulnerable.getBoolean(capabilities);
    try {
      focus.setBoolean(game, true);
      Method getView = method(game.getClass(), "getRenderViewEntity", "func_175606_aa");
      Object originalView = getView.invoke(game);
      float originalYaw = field(player.getClass(), "rotationYaw", "field_70177_z").getFloat(player);
      enable(runtime, "freecam");
      Object camera = getView.invoke(game);
      check(camera != player && camera != originalView, "Freecam uses a separate view entity");
      runtime.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      check(
          runtime.controls().hotbar().isPresent(),
          "Freecam claims item selection against automation");
      runtime.clientEvents().post(new MouseLookEvent(30, 10));
      check(
          field(player.getClass(), "rotationYaw", "field_70177_z").getFloat(player) == originalYaw,
          "Freecam mouse look preserves player rotation");
      check(
          field(camera.getClass(), "rotationYaw", "field_70177_z").getFloat(camera) != originalYaw,
          "Freecam mouse look rotates the detached camera");
      check(
          runtime.clientEvents().post(new LocalInteractionEvent()).isCancelled(),
          "Freecam blocks queued mouse interactions");
      perihelion.integration.minecraft189.control.ItemUseDelayAccess delay =
          (perihelion.integration.minecraft189.control.ItemUseDelayAccess) game;
      int oldDelay = delay.perihelion$itemUseDelay();
      delay.perihelion$itemUseDelay(0);
      ((perihelion.integration.minecraft189.control.GameplayAccess) game)
          .perihelion$rightClickMouse();
      check(
          delay.perihelion$itemUseDelay() == 0, "Transformed right click is suppressed in Freecam");
      delay.perihelion$itemUseDelay(oldDelay);
      disable(runtime, "freecam");
      check(getView.invoke(game) == originalView, "Freecam restores the previous view entity");
      check(
          !runtime.clientEvents().post(new LocalInteractionEvent()).isCancelled(),
          "Freecam interaction gate retires");
      HookBridge.gameControls().apply();
      smooth.setBoolean(settings, false);
      Object renderer = field(game.getClass(), "entityRenderer", "field_71460_t").get(game);
      Method fov =
          method(
              renderer.getClass(),
              new Class<?>[] {float.class, boolean.class},
              "getFOVModifier",
              "func_78481_a");
      float baseFov = ((Number) fov.invoke(renderer, 1f, true)).floatValue();
      enable(runtime, "zoom");
      runtime.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      check(smooth.getBoolean(settings), "Zoom applies smooth look");
      float zoomed = ((Number) fov.invoke(renderer, 1f, true)).floatValue();
      check(zoomed < baseFov, "Transformed FOV uses zoom");
      check(
          runtime.clientEvents().post(new MouseScrollEvent(120)).isCancelled(),
          "Zoom consumes scroll in gameplay");
      check(
          ((Number) fov.invoke(renderer, 1f, true)).floatValue() < zoomed,
          "Zoom scroll increases magnification");
      disable(runtime, "zoom");
      check(!smooth.getBoolean(settings), "Zoom restores smooth look");
      check(
          Math.abs(((Number) fov.invoke(renderer, 1f, true)).floatValue() - baseFov) < .001,
          "Zoom restores the original FOV path");
      set(runtime, "camera", "no-nausea", "true");
      set(runtime, "camera", "no-fire-overlay", "false");
      set(runtime, "camera", "fire-alpha", "0.3");
      enable(runtime, "camera");
      check(
          HookBridge.suppressEffect(VisualEffectEvent.Kind.NAUSEA),
          "Camera nausea setting reaches the visual hook");
      FireAppearanceEvent fire = runtime.clientEvents().post(new FireAppearanceEvent());
      check(Math.abs(fire.alpha - .3) < .001, "Camera applies fire appearance settings");
      disable(runtime, "camera");
      Arrays.fill(items, null);
      items[2] = stack("bread");
      level.setInt(food, 10);
      invulnerable.setBoolean(capabilities, false);
      enable(runtime, "autoeat");
      HookBridge.gameControls().beforeInput();
      HookBridge.gameControls().apply();
      motion(player);
      check(
          (Boolean) method(player.getClass(), "isUsingItem", "func_71039_bw").invoke(player),
          "Auto Eat begins eating eligible hotbar food");
      disable(runtime, "autoeat");
      HookBridge.gameControls().apply();
      check(
          !(Boolean) method(player.getClass(), "isUsingItem", "func_71039_bw").invoke(player),
          "Auto Eat cancels its owned use on disable");
      check(!runtime.controls().hotbar().isPresent(), "Auto Eat releases its slot");
      Arrays.fill(items, null);
      items[10] = stack("bread");
      set(runtime, "autoeat", "check-inventory", "true");
      enable(runtime, "autoeat");
      runtime.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      check(
          items[10] == null && sameItem(items[0], stack("bread")),
          "Auto Eat refills an empty hotbar slot with a real inventory swap");
      disable(runtime, "autoeat");
      enable(runtime, "bedplates");
      runtime.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      check(
          runtime.modules().require("bedplates").state() == ModuleState.ENABLED,
          "Bed Plates scans a loaded world without fault");
      disable(runtime, "bedplates");
    } finally {
      for (String id : new String[] {"freecam", "zoom", "camera", "autoeat", "bedplates"})
        disable(runtime, id);
      HookBridge.gameControls().apply();
      focus.setBoolean(game, focused);
      smooth.setBoolean(settings, savedSmooth);
      level.setInt(food, savedFood);
      invulnerable.setBoolean(capabilities, savedInvulnerable);
    }
  }

  private static int count(Object stack) throws Exception {
    return stack == null ? 0 : field(stack.getClass(), "stackSize", "field_77994_a").getInt(stack);
  }

  private static boolean sameItem(Object first, Object second) throws Exception {
    return first != null
        && method(first.getClass(), "getItem", "func_77973_b").invoke(first)
            == method(second.getClass(), "getItem", "func_77973_b").invoke(second);
  }

  private static void add(Object world, Object entity, int id) throws Exception {
    method(
            world.getClass(),
            new Class<?>[] {int.class, Class.forName("net.minecraft.entity.Entity")},
            "addEntityToWorld",
            "func_73027_a")
        .invoke(world, id, entity);
  }

  static Object stack(String name) throws Exception {
    Class<?> item = Class.forName("net.minecraft.item.Item");
    Object value =
        method(item, new Class<?>[] {String.class}, "getByNameOrId", "func_111206_d")
            .invoke(null, name);
    return Class.forName("net.minecraft.item.ItemStack")
        .getConstructor(item, int.class)
        .newInstance(value, 1);
  }

  private static <T> void reset(ClientRuntime r, String id, perihelion.core.setting.Setting<T> s) {
    r.modules().setSetting(id, s, s.defaultValue());
  }

  private static void enable(ClientRuntime r, String id) {
    r.modules().setEnabled(id, true);
    check(r.modules().require(id).state() == ModuleState.ENABLED, id + " enables");
  }

  private static void disable(ClientRuntime r, String id) {
    r.modules().setEnabled(id, false);
  }

  static void set(ClientRuntime r, String id, String key, String value) {
    r.commands().execute("set " + id + " " + key + " " + value, output -> {});
  }

  static Field field(Class<?> type, String... names) throws NoSuchFieldException {
    for (Class<?> c = type; c != null; c = c.getSuperclass())
      for (String n : names)
        try {
          Field f = c.getDeclaredField(n);
          f.setAccessible(true);
          return f;
        } catch (NoSuchFieldException ignored) {
        }
    throw new NoSuchFieldException(type + " " + Arrays.toString(names));
  }

  static Method method(Class<?> type, String... names) throws NoSuchMethodException {
    return method(type, new Class<?>[0], names);
  }

  static Method method(Class<?> type, Class<?>[] args, String... names)
      throws NoSuchMethodException {
    for (Class<?> c = type; c != null; c = c.getSuperclass())
      for (String n : names)
        try {
          Method m = c.getDeclaredMethod(n, args);
          m.setAccessible(true);
          return m;
        } catch (NoSuchMethodException ignored) {
        }
    throw new NoSuchMethodException(type + " " + Arrays.toString(names));
  }

  /** Exercise the real packet-building mixin, rather than fabricating AFTER_MOTION. */
  static void motion(Object player) throws Exception {
    method(player.getClass(), "onUpdateWalkingPlayer", "func_175161_p").invoke(player);
  }

  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
    checks++;
  }
}
