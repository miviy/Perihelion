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
import perihelion.integration.minecraft189.control.GameplayAccess;
import perihelion.integration.minecraft189.event.*;

/** Actual silent selection, click interception, walking-before-attack and jump ownership. */
public final class B4ManualCombatProbe {
  private static int checks, wireId;

  public static void run(ClientRuntime r) throws Exception {
    checks = wireId = 0;
    Object game =
        method(Class.forName("net.minecraft.client.Minecraft"), "getMinecraft", "func_71410_x")
            .invoke(null);
    Object p = field(game.getClass(), "thePlayer", "field_71439_g").get(game),
        world = field(game.getClass(), "theWorld", "field_71441_e").get(game),
        inventory = field(p.getClass(), "inventory", "field_71071_by").get(p);
    Object[]
        items =
            (Object[]) field(inventory.getClass(), "mainInventory", "field_70462_a").get(inventory),
        oldItems = items.clone();
    Field slot = field(inventory.getClass(), "currentItem", "field_70461_c"),
        screen = field(game.getClass(), "currentScreen", "field_71462_r"),
        focus = field(game.getClass(), "inGameHasFocus", "field_71415_G"),
        mouse = field(game.getClass(), "objectMouseOver", "field_71476_x");
    int oldSlot = slot.getInt(inventory);
    Object oldScreen = screen.get(game), oldMouse = mouse.get(game);
    boolean oldFocus = focus.getBoolean(game);
    Map<Field, Object> oldPlayer = new LinkedHashMap<>();
    for (String[] names :
        new String[][] {
          {"rotationYaw", "field_70177_z"}, {"rotationPitch", "field_70125_A"},
          {"lastReportedYaw", "field_175164_bL"}, {"lastReportedPitch", "field_175165_bM"},
          {"onGround", "field_70122_E"}, {"motionX", "field_70159_w"},
          {"motionY", "field_70181_x"}, {"motionZ", "field_70179_y"}
        }) {
      Field f = field(p.getClass(), names);
      oldPlayer.put(f, f.get(p));
    }
    double x = ((Number) field(p.getClass(), "posX", "field_70165_t").get(p)).doubleValue(),
        y = ((Number) field(p.getClass(), "posY", "field_70163_u").get(p)).doubleValue(),
        z = ((Number) field(p.getClass(), "posZ", "field_70161_v").get(p)).doubleValue();
    Map<String, Boolean> enabled = new LinkedHashMap<>();
    Map<Setting<?>, Object> settings = new LinkedHashMap<>();
    for (String id :
        new String[] {
          "aura",
          "aimassist",
          "displace",
          "jumpreset",
          "velocity",
          "scaffold",
          "blockin",
          "clutch",
          "speed",
          "flight",
          "longjump",
          "stasis",
          "freecam",
          "autoclicker",
          "blockhit",
          "hitselect",
          "antibot",
          "noslow"
        }) {
      enabled.put(id, r.modules().require(id).requestedEnabled());
      r.modules().setEnabled(id, false);
      for (Setting<?> s : r.modules().require(id).module().settings().values())
        settings.put(s, s.get());
    }
    Object profile =
        Class.forName("com.mojang.authlib.GameProfile")
            .getConstructor(UUID.class, String.class)
            .newInstance(UUID.randomUUID(), "B4ManualFixture");
    Object target =
        Class.forName("net.minecraft.client.entity.EntityOtherPlayerMP")
            .getConstructor(Class.forName("net.minecraft.world.World"), profile.getClass())
            .newInstance(world, profile);
    method(
            world.getClass(),
            new Class<?>[] {int.class, Class.forName("net.minecraft.entity.Entity")},
            "addEntityToWorld",
            "func_73027_a")
        .invoke(world, -887750, target);
    EventOwner owner =
        new EventOwner(
            "b4-manual-probe",
            t -> {
              throw new AssertionError(t);
            });
    owner.activate();
    List<AutoCloseable> claims = new ArrayList<>();
    ControlRequest<Rotation> blocker = r.controls().scope(owner, claims::add).rotation(1000);
    Object renderer = field(game.getClass(), "entityRenderer", "field_71460_t").get(game);
    Method ray =
        method(renderer.getClass(), new Class<?>[] {float.class}, "getMouseOver", "func_78473_a");
    try (GameplayLoopback peer = new GameplayLoopback(game)) {
      Arrays.fill(items, null);
      items[0] = stack("diamond_sword");
      slot.setInt(inventory, 0);
      screen.set(game, null);
      focus.setBoolean(game, true);
      position(p, x, 200, z);
      position(target, x + 1, 200, z + 2);
      camera(p, 0, 0);
      field(p.getClass(), "onGround", "field_70122_E").setBoolean(p, true);
      set(r, "aimassist", "mode", "B4_SILENT");
      set(r, "aimassist", "b4-speed", "100");
      set(r, "aimassist", "max-angle", "360");
      set(r, "aimassist", "ignore-teams", "false");
      mobEligibility(r, p, world);
      r.modules().setEnabled("aimassist", true);
      ((perihelion.integration.minecraft189.control.TickMouseOverAccess) renderer)
          .perihelion$tickMouseOver(1f);
      require(
          r.controls().rotation().isPresent()
              && r.controls().rotation().get().mode() == Rotation.Mode.SERVER,
          "B4 aim is server-only");
      require(
          field(p.getClass(), "rotationYaw", "field_70177_z").getFloat(p) == 0
              && field(p.getClass(), "rotationPitch", "field_70125_A").getFloat(p) == 0,
          "silent selection leaves camera unchanged");
      require(
          mouse.get(game) != null
              && field(mouse.get(game).getClass(), "entityHit", "field_72308_g")
                      .get(mouse.get(game))
                  == target,
          "transformed selection uses the silent target");
      Rotation picked = r.controls().rotation().get();
      Object assist = r.modules().require("aimassist").module();
      Field snap = field(assist.getClass(), "b4Snap");
      snap.setBoolean(assist, true);
      for (int frame = 0; frame < 8; frame++) ray.invoke(renderer, frame / 8f);
      require(
          r.controls().rotation().get() == picked,
          "render picks preserve the pending tick rotation identity");
      require(snap.getBoolean(assist), "render picks do not consume a pending Displace snap");
      ((perihelion.integration.minecraft189.control.TickMouseOverAccess) renderer)
          .perihelion$tickMouseOver(1f);
      require(!snap.getBoolean(assist), "tick pick consumes the pending snap once");
      continuousYaw(r, p, target, renderer, ray, peer, x, z);
      discreteInput(r, game, p, target, renderer, owner, claims, x, z);
      livingPhysics(r, p, target, renderer, owner, claims, x, z);
      tellyPhysics(r, p, items, slot, inventory, owner, claims, x, z);
      camera(p, 0, 0);
      field(p.getClass(), "lastReportedYaw", "field_175164_bL").setFloat(p, 0);
      field(p.getClass(), "lastReportedPitch", "field_175165_bM").setFloat(p, 0);
      position(target, x + 1, 200, z + 2);
      blocker.set(Rotation.server(90, 0));
      ((perihelion.integration.minecraft189.control.TickMouseOverAccess) renderer)
          .perihelion$tickMouseOver(1f);
      require(
          mouse.get(game) == null
              || field(mouse.get(game).getClass(), "entityHit", "field_72308_g")
                      .get(mouse.get(game))
                  != target,
          "foreign aim prevents silent selection");
      blocker.clear();
      // A target outside the physical crosshair must survive Displace's higher-priority handoff.
      ((perihelion.integration.minecraft189.control.TickMouseOverAccess) renderer)
          .perihelion$tickMouseOver(1f);
      set(r, "displace", "activation", "B4_MANUAL");
      set(r, "displace", "mode", "LEFT");
      set(r, "displace", "only-knockback-items", "false");
      set(r, "displace", "cooldown", ".2");
      method(p.getClass(), new Class<?>[] {boolean.class}, "setSprinting", "func_70031_b")
          .invoke(p, true);
      r.modules().setEnabled("displace", true);
      forceLook(p);
      motion(p);
      wireBoundary(peer);
      peer.received.clear();
      ((GameplayAccess) game).perihelion$clickMouse();
      wireBoundary(peer);
      require(
          count(peer.received, "C02PacketUseEntity") == 0,
          "manual click waits for its own walking angle");
      ((perihelion.integration.minecraft189.control.TickMouseOverAccess) renderer)
          .perihelion$tickMouseOver(1f);
      require(
          mouse.get(game) != null
              && field(mouse.get(game).getClass(), "entityHit", "field_72308_g")
                      .get(mouse.get(game))
                  == target,
          "silent target survives Displace priority");
      forceLook(p);
      motion(p);
      Rotation submitted = r.controls().rotation().get();
      r.controls().beginTick();
      require(
          !r.controls().rotation().isPresent(),
          "real control tick boundary expires the one-tick manual and Silent claims");
      ((perihelion.integration.minecraft189.control.TickMouseOverAccess) renderer)
          .perihelion$tickMouseOver(1f);
      HookBridge.gameControls().beforeInput();
      wireBoundary(peer);
      require(
          count(peer.received, "C02PacketUseEntity") == 1,
          "manual replay attacks once without Aura");
      ordered(peer.received, "C02PacketUseEntity", submitted, "B4 manual displacement");
      checks += 2;
      Rotation recovery = r.controls().rotation().get();
      require(
          recovery != submitted && Math.abs(recovery.yaw() - submitted.yaw()) > 1,
          "manual replay releases its offset before the recovery walking pass");
      require(
          field(p.getClass(), "rotationYaw", "field_70177_z").getFloat(p) == 0,
          "manual replay restores camera");
      forceLook(p);
      motion(p);
      HookBridge.gameControls().beforeInput();
      wireBoundary(peer);
      Object recoveryPacket = null;
      Class<?> movementType = Class.forName("net.minecraft.network.play.client.C03PacketPlayer");
      for (Object packet : peer.received)
        if (movementType.isInstance(packet)) recoveryPacket = packet;
      require(
          recoveryPacket != null
              && ((Number)
                          method(recoveryPacket.getClass(), "getYaw", "func_149462_g")
                              .invoke(recoveryPacket))
                      .floatValue()
                  == recovery.yaw(),
          "the next walking packet uses Silent recovery rather than another displacement offset");
      require(
          count(peer.received, "C02PacketUseEntity") == 1,
          "completed displacement cannot replay twice");
      r.modules().setEnabled("displace", false);
      r.modules().setEnabled("aimassist", false);
      require(!r.controls().rotation().isPresent(), "manual modules release all rotation claims");

      set(r, "jumpreset", "mode", "B4");
      set(r, "jumpreset", "chance", "100");
      set(r, "jumpreset", "ignore-on-fire", "false");
      r.modules().setEnabled("jumpreset", true);
      camera(p, 0, 0);
      field(p.getClass(), "onGround", "field_70122_E").setBoolean(p, true);
      method(p.getClass(), new Class<?>[] {boolean.class}, "setSprinting", "func_70031_b")
          .invoke(p, true);
      Class<?> velocity =
          Class.forName("net.minecraft.network.play.server.S12PacketEntityVelocity");
      int id = ((Number) method(p.getClass(), "getEntityId", "func_145782_y").invoke(p)).intValue();
      Object good =
          velocity
              .getConstructor(int.class, double.class, double.class, double.class)
              .newInstance(id, 0, .4, -.4);
      r.clientEvents()
          .post(
              ClientPacketEvent.class
                  .getConstructor(Class.forName("net.minecraft.network.Packet"))
                  .newInstance(good));
      HookBridge.gameControls().beforeInput();
      require(r.controls().key(InputKey.JUMP).orElse(false), "B4 jumps for opposing knockback");
      Object bad =
          velocity
              .getConstructor(int.class, double.class, double.class, double.class)
              .newInstance(id, 0, .4, .4);
      r.clientEvents()
          .post(
              ClientPacketEvent.class
                  .getConstructor(Class.forName("net.minecraft.network.Packet"))
                  .newInstance(bad));
      HookBridge.gameControls().beforeInput();
      require(
          !r.controls().key(InputKey.JUMP).isPresent(),
          "B4 rejects a jump that increases knockback");
      for (double excludedY : new double[] {-.1, 0, .5625}) {
        Object excluded =
            velocity
                .getConstructor(int.class, double.class, double.class, double.class)
                .newInstance(id, 0, excludedY, -.4);
        r.clientEvents()
            .post(
                ClientPacketEvent.class
                    .getConstructor(Class.forName("net.minecraft.network.Packet"))
                    .newInstance(excluded));
        HookBridge.gameControls().beforeInput();
        require(
            !r.controls().key(InputKey.JUMP).isPresent(),
            "B4 shared knockback gate rejects vertical velocity " + excludedY);
      }
      r.modules().setEnabled("jumpreset", false);
      require(!r.controls().key(InputKey.JUMP).isPresent(), "jump key releases on disable");
      for (String idName : enabled.keySet())
        require(
            r.modules().require(idName).state() != ModuleState.FAULTED,
            idName + " remains healthy");
    } finally {
      for (String id : enabled.keySet()) r.modules().setEnabled(id, false);
      for (Map.Entry<Setting<?>, Object> e : settings.entrySet()) restore(e.getKey(), e.getValue());
      for (AutoCloseable claim : claims) claim.close();
      owner.close();
      method(world.getClass(), new Class<?>[] {int.class}, "removeEntityFromWorld", "func_73028_b")
          .invoke(world, -887750);
      System.arraycopy(oldItems, 0, items, 0, items.length);
      slot.setInt(inventory, oldSlot);
      position(p, x, y, z);
      for (Map.Entry<Field, Object> e : oldPlayer.entrySet()) e.getKey().set(p, e.getValue());
      screen.set(game, oldScreen);
      mouse.set(game, oldMouse);
      focus.setBoolean(game, oldFocus);
      for (Map.Entry<String, Boolean> e : enabled.entrySet())
        r.modules().setEnabled(e.getKey(), e.getValue());
      HookBridge.gameControls().apply();
    }
    System.out.println("[PERIHELION_SMOKE] B4_MANUAL_COMBAT PASS checks=" + checks);
  }

  private static void tellyPhysics(
      ClientRuntime r,
      Object player,
      Object[] items,
      Field selected,
      Object inventory,
      EventOwner owner,
      List<AutoCloseable> resources,
      double x,
      double z)
      throws Exception {
    int originalSlot = selected.getInt(inventory);
    Object originalStack = items[originalSlot];
    Object input = field(player.getClass(), "movementInput", "field_71158_b").get(player);
    Map<Field, Object> state = new LinkedHashMap<>();
    for (String[] names :
        new String[][] {
          {"motionX", "field_70159_w"}, {"motionY", "field_70181_x"},
          {"motionZ", "field_70179_y"}, {"onGround", "field_70122_E"},
          {"isCollidedHorizontally", "field_70123_F"}, {"isCollidedVertically", "field_70124_G"},
          {"jumpTicks", "field_70773_bE"}, {"moveForward", "field_70701_bs"},
          {"moveStrafing", "field_70702_br"}, {"sprintToggleTimer", "field_71156_d"}
        }) {
      Field f = field(player.getClass(), names);
      state.put(f, f.get(player));
    }
    float oldForward = field(input.getClass(), "moveForward", "field_78900_b").getFloat(input);
    float oldStrafe = field(input.getClass(), "moveStrafe", "field_78902_a").getFloat(input);
    boolean oldJump = field(input.getClass(), "jump", "field_78901_c").getBoolean(input);
    boolean oldSneak = field(input.getClass(), "sneak", "field_78899_d").getBoolean(input);
    boolean oldSprint =
        (Boolean) method(player.getClass(), "isSprinting", "func_70051_ag").invoke(player);
    NativeKeys nativeKeys = new NativeKeys();
    Map<InputKey, ControlRequest<Boolean>> keys = new LinkedHashMap<>();
    for (InputKey key :
        new InputKey[] {
          InputKey.FORWARD,
          InputKey.BACK,
          InputKey.LEFT,
          InputKey.RIGHT,
          InputKey.SNEAK,
          InputKey.JUMP,
          InputKey.SPRINT
        }) {
      ControlRequest<Boolean> request = r.controls().scope(owner, resources::add).key(key, 1001);
      request.set(key == InputKey.FORWARD);
      nativeKeys.set(key, key == InputKey.FORWARD);
      keys.put(key, request);
    }
    int[] sprintJumps = {0};
    AutoCloseable observer =
        r.clientEvents()
            .subscribe(
                owner,
                MovementYawEvent.class,
                -10000,
                false,
                e -> {
                  if (e.jump()) sprintJumps[0]++;
                });
    double[] speeds = new double[2];
    try {
      items[originalSlot] = stack("stone");
      set(r, "scaffold", "mode", "TELLY");
      set(r, "scaffold", "sprint", "VANILLA");
      set(r, "scaffold", "tower", "NONE");
      set(r, "scaffold", "keep-y", "true");
      for (int manual = 0; manual < 2; manual++) {
        r.modules().setEnabled("scaffold", false);
        HookBridge.gameControls().apply();
        position(player, x, 200, z);
        camera(player, 0, 0);
        for (String[] names :
            new String[][] {
              {"motionX", "field_70159_w"},
              {"motionY", "field_70181_x"},
              {"motionZ", "field_70179_y"}
            }) field(player.getClass(), names).setDouble(player, 0);
        field(player.getClass(), "onGround", "field_70122_E").setBoolean(player, true);
        field(player.getClass(), "isCollidedHorizontally", "field_70123_F")
            .setBoolean(player, false);
        field(player.getClass(), "isCollidedVertically", "field_70124_G").setBoolean(player, true);
        field(player.getClass(), "jumpTicks", "field_70773_bE").setInt(player, 0);
        field(player.getClass(), "sprintToggleTimer", "field_71156_d").setInt(player, 0);
        field(input.getClass(), "moveForward", "field_78900_b").setFloat(input, 1);
        field(input.getClass(), "moveStrafe", "field_78902_a").setFloat(input, 0);
        keys.get(InputKey.JUMP).set(manual == 1);
        nativeKeys.set(InputKey.JUMP, manual == 1);
        method(player.getClass(), new Class<?>[] {boolean.class}, "setSprinting", "func_70031_b")
            .invoke(player, false);
        sprintJumps[0] = 0;
        r.modules().setEnabled("scaffold", true);
        method(player.getClass(), "onLivingUpdate", "func_70636_d").invoke(player);
        speeds[manual] = field(player.getClass(), "motionZ", "field_70179_y").getDouble(player);
        require(
            field(player.getClass(), "motionY", "field_70181_x").getDouble(player) > 0,
            "Telly " + (manual == 0 ? "automatic" : "manual") + " jump leaves the ground");
        require(
            sprintJumps[0] == 1,
            "Telly "
                + (manual == 0 ? "automatic" : "manual")
                + " jump receives exactly one sprint impulse; events="
                + sprintJumps[0]
                + " forwardMotion="
                + speeds[manual]);
      }
      require(
          Math.abs(speeds[0] - speeds[1]) < 1e-6,
          "automatic Telly retains the same first-jump forward speed as held manual jump");
    } finally {
      r.modules().setEnabled("scaffold", false);
      observer.close();
      for (ControlRequest<Boolean> key : keys.values()) key.clear();
      nativeKeys.close();
      items[originalSlot] = originalStack;
      selected.setInt(inventory, originalSlot);
      HookBridge.gameControls().apply();
      position(player, x, 200, z);
      for (Map.Entry<Field, Object> entry : state.entrySet())
        entry.getKey().set(player, entry.getValue());
      field(input.getClass(), "moveForward", "field_78900_b").setFloat(input, oldForward);
      field(input.getClass(), "moveStrafe", "field_78902_a").setFloat(input, oldStrafe);
      field(input.getClass(), "jump", "field_78901_c").setBoolean(input, oldJump);
      field(input.getClass(), "sneak", "field_78899_d").setBoolean(input, oldSneak);
      method(player.getClass(), new Class<?>[] {boolean.class}, "setSprinting", "func_70031_b")
          .invoke(player, oldSprint);
    }
  }

  private static void livingPhysics(
      ClientRuntime r,
      Object player,
      Object target,
      Object renderer,
      EventOwner owner,
      List<AutoCloseable> claims,
      double x,
      double z)
      throws Exception {
    Object input = field(player.getClass(), "movementInput", "field_71158_b").get(player);
    Field forward = field(input.getClass(), "moveForward", "field_78900_b"),
        strafe = field(input.getClass(), "moveStrafe", "field_78902_a");
    float oldForward = forward.getFloat(input), oldStrafe = strafe.getFloat(input);
    boolean sprinting =
        (Boolean) method(player.getClass(), "isSprinting", "func_70051_ag").invoke(player);
    require(
        !(Boolean) method(player.getClass(), "isUsingItem", "func_71039_bw").invoke(player),
        "physics fixture begins without a leaked item-use state");
    Map<Field, Object> saved = new LinkedHashMap<>();
    for (String[] names :
        new String[][] {
          {"motionX", "field_70159_w"},
          {"motionY", "field_70181_x"},
          {"motionZ", "field_70179_y"},
          {"onGround", "field_70122_E"},
          {"moveForward", "field_70701_bs"},
          {"moveStrafing", "field_70702_br"}
        }) {
      Field f = field(player.getClass(), names);
      saved.put(f, f.get(player));
    }
    Map<InputKey, ControlRequest<Boolean>> keys = new LinkedHashMap<>();
    NativeKeys vanillaKeys = new NativeKeys();
    for (InputKey key :
        new InputKey[] {
          InputKey.FORWARD,
          InputKey.BACK,
          InputKey.LEFT,
          InputKey.RIGHT,
          InputKey.SNEAK,
          InputKey.JUMP,
          InputKey.SPRINT
        }) {
      ControlRequest<Boolean> request = r.controls().scope(owner, claims::add).key(key, 1001);
      request.set(key == InputKey.FORWARD || key == InputKey.SPRINT);
      vanillaKeys.set(key, key == InputKey.FORWARD || key == InputKey.SPRINT);
      keys.put(key, request);
    }
    float[] sampled = {Float.NaN, Float.NaN};
    AutoCloseable inputObserver =
        r.clientEvents()
            .subscribe(
                owner, MovementInputEvent.class, -1000, false, e -> sampled[0] = e.forward());
    AutoCloseable physicsObserver =
        r.clientEvents()
            .subscribe(owner, StrafeEvent.class, -1000, false, e -> sampled[1] = e.forward());
    try {
      for (int phase = 0; phase < 3; phase++) {
        position(player, x, 200, z);
        position(target, x, 200, z - 2.5);
        camera(player, 0, 0);
        field(player.getClass(), "lastReportedYaw", "field_175164_bL").setFloat(player, 180);
        field(player.getClass(), "lastReportedPitch", "field_175165_bM").setFloat(player, 0);
        for (String[] names :
            new String[][] {
              {"motionX", "field_70159_w"},
              {"motionY", "field_70181_x"},
              {"motionZ", "field_70179_y"}
            }) field(player.getClass(), names).setDouble(player, 0);
        field(player.getClass(), "onGround", "field_70122_E").setBoolean(player, true);
        keys.get(InputKey.SNEAK).set(phase == 1);
        vanillaKeys.set(InputKey.SNEAK, phase == 1);
        method(player.getClass(), new Class<?>[] {boolean.class}, "setSprinting", "func_70031_b")
            .invoke(player, phase == 0);
        if (phase == 2) {
          Object held = method(player.getClass(), "getHeldItem", "func_70694_bm").invoke(player);
          method(
                  player.getClass(),
                  new Class<?>[] {Class.forName("net.minecraft.item.ItemStack"), int.class},
                  "setItemInUse",
                  "func_71008_a")
              .invoke(player, held, 72000);
        }
        ((perihelion.integration.minecraft189.control.TickMouseOverAccess) renderer)
            .perihelion$tickMouseOver(1f);
        sampled[0] = sampled[1] = Float.NaN;
        method(player.getClass(), "onLivingUpdate", "func_70636_d").invoke(player);
        require(
            sampled[0] < 0 && !Float.isNaN(sampled[1]),
            "actual living update reaches corrected input and moveFlying");
        require(
            !(Boolean) method(player.getClass(), "isSprinting", "func_70051_ag").invoke(player),
            "vanilla sprint eligibility rejects the corrected backwards input");
        require(
            field(player.getClass(), "motionZ", "field_70179_y").getDouble(player) > 0,
            "actual movement follows a legal corrected input toward camera-forward");
        if (phase == 1)
          require(
              Math.abs(sampled[0] + .3f) < 1e-6 && Math.abs(sampled[1] + .294f) < 1e-6,
              "sneak input retains source .3 magnitude through real physics");
        if (phase == 2)
          require(
              sampled[0] == -1 && Math.abs(sampled[1] + .196f) < 1e-6,
              "vanilla item slowdown applies once after discrete input correction");
      }
    } finally {
      method(player.getClass(), "clearItemInUse", "func_71041_bz").invoke(player);
      inputObserver.close();
      physicsObserver.close();
      for (ControlRequest<Boolean> key : keys.values()) key.clear();
      vanillaKeys.close();
      HookBridge.gameControls().apply();
      position(player, x, 200, z);
      forward.setFloat(input, oldForward);
      strafe.setFloat(input, oldStrafe);
      for (Map.Entry<Field, Object> e : saved.entrySet()) e.getKey().set(player, e.getValue());
      method(player.getClass(), new Class<?>[] {boolean.class}, "setSprinting", "func_70031_b")
          .invoke(player, sprinting);
    }
  }

  private static void continuousYaw(
      ClientRuntime r,
      Object player,
      Object target,
      Object renderer,
      Method renderPick,
      GameplayLoopback peer,
      double x,
      double z)
      throws Exception {
    camera(player, 179, 0);
    field(player.getClass(), "lastReportedYaw", "field_175164_bL").setFloat(player, 179);
    field(player.getClass(), "lastReportedPitch", "field_175165_bM").setFloat(player, 0);
    float previous = 179;
    boolean crossed = false;
    Class<?> movement = Class.forName("net.minecraft.network.play.client.C03PacketPlayer");
    for (int tick = 0; tick < 24; tick++) {
      double heading = Math.toRadians(179 + tick);
      position(target, x - Math.sin(heading) * 2.5, 200, z + Math.cos(heading) * 2.5);
      ((perihelion.integration.minecraft189.control.TickMouseOverAccess) renderer)
          .perihelion$tickMouseOver(1f);
      Rotation selected = r.controls().rotation().get();
      for (int frame = 0; frame < 3; frame++) renderPick.invoke(renderer, frame / 3f);
      require(
          r.controls().rotation().get() == selected,
          "extra render frames cannot replace the selected walking angle");
      forceLook(player);
      int start = peer.received.size();
      motion(player);
      wireBoundary(peer);
      Object packet = null;
      for (int i = start; i < peer.received.size(); i++)
        if (movement.isInstance(peer.received.get(i))) packet = peer.received.get(i);
      require(packet != null, "walking submits the selected Silent rotation");
      float yaw =
          ((Number) method(packet.getClass(), "getYaw", "func_149462_g").invoke(packet))
              .floatValue();
      require(
          yaw == selected.yaw() && Math.abs(yaw - previous) < 10,
          "wire yaw remains continuous at the 180-degree boundary");
      require(
          field(player.getClass(), "rotationYaw", "field_70177_z").getFloat(player) == 179,
          "continuous Silent stream leaves the camera unchanged");
      crossed |= yaw > 180;
      previous = yaw;
    }
    require(crossed, "walking packet stream actually crosses +180 without normalization");
  }

  /** Await decoded delivery of every packet preceding a unique marker on this TCP stream. */
  static void wireBoundary(GameplayLoopback peer) throws Exception {
    int first = peer.received.size(), marker = 0x4b340000 + ++wireId;
    Object packet =
        Class.forName("net.minecraft.network.play.client.C00PacketKeepAlive")
            .getConstructor(int.class)
            .newInstance(marker);
    method(
            peer.manager.get().getClass(),
            new Class<?>[] {Class.forName("net.minecraft.network.Packet")},
            "sendPacket",
            "func_179290_a")
        .invoke(peer.manager.get(), packet);
    flush(peer.client);
    long until = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
    do {
      for (int i = first; i < peer.received.size(); i++) {
        Object received = peer.received.get(i);
        if (received.getClass().getSimpleName().equals("C00PacketKeepAlive")
            && ((Number) method(received.getClass(), "getKey", "func_149460_c").invoke(received))
                    .intValue()
                == marker) return;
      }
      Thread.sleep(2);
    } while (System.nanoTime() < until);
    throw new AssertionError("manual combat wire-boundary marker did not reach the TCP peer");
  }

  private static void discreteInput(
      ClientRuntime r,
      Object game,
      Object player,
      Object target,
      Object renderer,
      EventOwner owner,
      List<AutoCloseable> claims,
      double x,
      double z)
      throws Exception {
    Object input = field(player.getClass(), "movementInput", "field_71158_b").get(player);
    Field forward = field(input.getClass(), "moveForward", "field_78900_b"),
        strafe = field(input.getClass(), "moveStrafe", "field_78902_a");
    float oldForward = forward.getFloat(input), oldStrafe = strafe.getFloat(input);
    List<ControlRequest<Boolean>> keys = new ArrayList<>();
    NativeKeys vanillaKeys = new NativeKeys();
    for (InputKey key :
        new InputKey[] {
          InputKey.FORWARD, InputKey.BACK, InputKey.LEFT, InputKey.RIGHT, InputKey.SNEAK
        }) {
      ControlRequest<Boolean> request = r.controls().scope(owner, claims::add).key(key, 1000);
      request.set(key == InputKey.FORWARD);
      vanillaKeys.set(key, key == InputKey.FORWARD);
      keys.add(request);
    }
    int[] observed = {0};
    AutoCloseable observer =
        r.clientEvents()
            .subscribe(owner, MovementInputEvent.class, -1000, false, e -> observed[0]++);
    try {
      camera(player, 0, 0);
      field(player.getClass(), "lastReportedYaw", "field_175164_bL").setFloat(player, 90);
      field(player.getClass(), "lastReportedPitch", "field_175165_bM").setFloat(player, 0);
      position(target, x - 2.5, 200, z);
      ((perihelion.integration.minecraft189.control.TickMouseOverAccess) renderer)
          .perihelion$tickMouseOver(1f);
      HookBridge.gameControls().beforeInput();
      method(input.getClass(), "updatePlayerMoveState", "func_78898_a").invoke(input);
      require(observed[0] == 1, "actual local movement input dispatches the post-input hook once");
      require(
          forward.getFloat(input) == 0 && strafe.getFloat(input) == 1,
          "Silent uses a legal digital strafe before sprint eligibility instead of analog"
              + " rotation");
      StrafeEvent physics = r.clientEvents().post(new StrafeEvent(1, 0, .1f, 0));
      require(
          physics.strafe() == 1 && physics.forward() == 0,
          "Silent does not rotate its corrected inputs a second time at moveFlying");
      require(
          physics.yaw() == r.controls().rotation().get().yaw(),
          "movement physics uses the same server yaw as the Silent request");
      Class<?> actions =
          Class.forName("perihelion.integration.minecraft189.module.gameplay.GameActions");
      Method direction = method(actions, new Class<?>[] {player.getClass()}, "direction"),
          directStrafe =
              method(actions, new Class<?>[] {player.getClass(), double.class}, "strafe");
      require(
          Math.abs(((Number) direction.invoke(null, player)).doubleValue()) < 1e-6,
          "direct movement helpers retain the physical forward direction after digital correction");
      Field mx = field(player.getClass(), "motionX", "field_70159_w"),
          mz = field(player.getClass(), "motionZ", "field_70179_y");
      double oldX = mx.getDouble(player), oldZ = mz.getDouble(player);
      try {
        directStrafe.invoke(null, player, .4);
        require(
            Math.abs(mx.getDouble(player)) < 1e-6 && Math.abs(mz.getDouble(player) - .4) < 1e-6,
            "direct strafe uses the winning server basis exactly once");
      } finally {
        mx.setDouble(player, oldX);
        mz.setDouble(player, oldZ);
      }
      ControlRequest<Rotation> competing = r.controls().scope(owner, claims::add).rotation(2000);
      competing.set(Rotation.server(-45, 0));
      try {
        method(input.getClass(), "updatePlayerMoveState", "func_78898_a").invoke(input);
        require(
            forward.getFloat(input) == 1 && strafe.getFloat(input) == 0,
            "Silent yields digital movement correction when another rotation owns the player");
        require(
            r.clientEvents().post(new MovementYawEvent(0)).yaw() == 0,
            "Silent cannot supply its helper yaw after losing rotation ownership");
      } finally {
        competing.clear();
      }
      Object options = field(game.getClass(), "gameSettings", "field_71474_y").get(game);
      Object unrelated = input.getClass().getConstructor(options.getClass()).newInstance(options);
      method(unrelated.getClass(), "updatePlayerMoveState", "func_78898_a").invoke(unrelated);
      require(observed[0] == 2, "unrelated movement input cannot borrow local module correction");
      require(
          forward.getFloat(unrelated) == 1 && strafe.getFloat(unrelated) == 0,
          "unrelated input retains ordinary vanilla controls");
    } finally {
      observer.close();
      for (ControlRequest<Boolean> request : keys) request.clear();
      vanillaKeys.close();
      HookBridge.gameControls().apply();
      forward.setFloat(input, oldForward);
      strafe.setFloat(input, oldStrafe);
    }
  }

  /** Keep physics deterministic when macOS leaves the isolated window inactive. */
  static final class NativeKeys implements AutoCloseable {
    private final Map<InputKey, Object> bindings = new LinkedHashMap<>();
    private final Map<Object, Boolean> saved = new LinkedHashMap<>();
    private final Field pressed;

    NativeKeys() throws Exception {
      Object game =
          method(Class.forName("net.minecraft.client.Minecraft"), "getMinecraft", "func_71410_x")
              .invoke(null);
      Object options = field(game.getClass(), "gameSettings", "field_71474_y").get(game);
      String[][] names = {
        {"keyBindForward", "field_74351_w"}, {"keyBindBack", "field_74368_y"},
        {"keyBindLeft", "field_74370_x"}, {"keyBindRight", "field_74366_z"},
        {"keyBindSneak", "field_74311_E"}, {"keyBindJump", "field_74314_A"},
        {"keyBindSprint", "field_151444_V"}
      };
      InputKey[] keys = {
        InputKey.FORWARD,
        InputKey.BACK,
        InputKey.LEFT,
        InputKey.RIGHT,
        InputKey.SNEAK,
        InputKey.JUMP,
        InputKey.SPRINT
      };
      pressed =
          field(
              Class.forName("net.minecraft.client.settings.KeyBinding"),
              "pressed",
              "field_74513_e");
      for (int i = 0; i < keys.length; i++) {
        Object binding = field(options.getClass(), names[i]).get(options);
        bindings.put(keys[i], binding);
        saved.put(binding, pressed.getBoolean(binding));
        pressed.setBoolean(binding, false);
      }
    }

    void set(InputKey key, boolean down) throws Exception {
      pressed.setBoolean(bindings.get(key), down);
    }

    @Override
    public void close() throws Exception {
      for (Map.Entry<Object, Boolean> entry : saved.entrySet())
        pressed.setBoolean(entry.getKey(), entry.getValue());
    }
  }

  private static void mobEligibility(ClientRuntime r, Object player, Object world)
      throws Exception {
    Object module = r.modules().require("aimassist").module();
    Class<?> entity = Class.forName("net.minecraft.entity.Entity"),
        living = Class.forName("net.minecraft.entity.EntityLivingBase"),
        worldType = Class.forName("net.minecraft.world.World"),
        stackType = Class.forName("net.minecraft.item.ItemStack");
    Method eligible = method(module.getClass(), new Class<?>[] {living}, "b4Target"),
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
                "func_73028_b");
    Map<Setting<?>, Object> saved = new LinkedHashMap<>();
    for (String key : new String[] {"mobs", "invisibles", "distance"}) {
      Setting<?> setting = r.modules().require("aimassist").module().settings().get(key);
      saved.put(setting, setting.get());
    }
    List<Integer> ids = new ArrayList<>();
    List<Object> fixtures = new ArrayList<>();
    double x = field(player.getClass(), "posX", "field_70165_t").getDouble(player),
        y = field(player.getClass(), "posY", "field_70163_u").getDouble(player),
        z = field(player.getClass(), "posZ", "field_70161_v").getDouble(player);
    try {
      set(r, "aimassist", "mobs", "true");
      set(r, "aimassist", "invisibles", "false");
      set(r, "aimassist", "distance", "3");
      String[] types = {
        "monster.EntityZombie",
        "monster.EntityIronGolem",
        "monster.EntitySlime",
        "passive.EntityPig",
        "passive.EntityVillager"
      };
      Object zombie = null;
      for (int i = 0; i < types.length; i++) {
        Object target =
            Class.forName("net.minecraft.entity." + types[i])
                .getConstructor(worldType)
                .newInstance(world);
        fixtures.add(target);
        position(target, x, y, z + 2);
        int id = -887751 - i;
        ids.add(id);
        add.invoke(world, id, target);
        require(
            (Boolean) eligible.invoke(module, target) == (i < 3),
            "B4 Mobs eligibility accepts only hostile/golem/slime family: " + types[i]);
        if (i == 0) zombie = target;
      }
      set(r, "aimassist", "mobs", "false");
      require(!(Boolean) eligible.invoke(module, zombie), "Mobs off rejects a valid hostile mob");
      set(r, "aimassist", "mobs", "true");
      position(zombie, x, y, z + 3);
      require(
          (Boolean) eligible.invoke(module, zombie),
          "B4 mob range includes the exact configured boundary");
      position(zombie, x, y, z + 3.001);
      require(
          !(Boolean) eligible.invoke(module, zombie),
          "B4 mob range rejects targets beyond the boundary");
      position(zombie, x, y, z + 2);
      Method
          equipment =
              method(
                  zombie.getClass(),
                  new Class<?>[] {int.class, stackType},
                  "setCurrentItemOrArmor",
                  "func_70062_b"),
          invisible =
              method(
                  zombie.getClass(),
                  new Class<?>[] {boolean.class},
                  "setInvisible",
                  "func_82142_c");
      for (int i = 0; i <= 4; i++) equipment.invoke(zombie, i, null);
      invisible.invoke(zombie, true);
      require(
          !(Boolean) eligible.invoke(module, zombie),
          "B4 invisible mob without any equipment is rejected");
      String[] gear = {
        "stone_sword", "diamond_boots", "diamond_leggings", "diamond_chestplate", "diamond_helmet"
      };
      for (int i = 0; i <= 4; i++) {
        equipment.invoke(zombie, i, stack(gear[i]));
        require(
            (Boolean) eligible.invoke(module, zombie),
            "equipment slot " + i + " independently makes an invisible B4 mob eligible");
        equipment.invoke(zombie, i, null);
        require(
            !(Boolean) eligible.invoke(module, zombie),
            "clearing equipment slot " + i + " removes invisible-mob eligibility");
      }
      set(r, "aimassist", "invisibles", "true");
      require(
          (Boolean) eligible.invoke(module, zombie),
          "Invisibles on accepts an unequipped invisible mob");
      invisible.invoke(zombie, false);
      Object replacement =
          Class.forName("net.minecraft.entity.monster.EntityZombie")
              .getConstructor(worldType)
              .newInstance(world);
      fixtures.add(replacement);
      position(replacement, x, y, z + 2);
      add.invoke(world, -887751, replacement);
      // Isolate registry identity from the death flag set by replacing the old world entity.
      field(zombie.getClass(), "isDead", "field_70128_L").setBoolean(zombie, false);
      require(
          (Boolean) eligible.invoke(module, replacement),
          "replacement entity registered under the same ID is eligible");
      require(
          !(Boolean) eligible.invoke(module, zombie),
          "stale replaced entity cannot remain eligible under a reused ID");
      remove.invoke(world, -887751);
      field(replacement.getClass(), "isDead", "field_70128_L").setBoolean(replacement, false);
      require(
          !(Boolean) eligible.invoke(module, replacement),
          "unregistered live entity cannot remain an aim target");
    } finally {
      for (int id : ids) remove.invoke(world, id);
      Method removeLoaded =
          method(
              world.getClass(),
              new Class<?>[] {entity},
              "removePlayerEntityDangerously",
              "func_72973_f");
      for (Object fixture : fixtures) removeLoaded.invoke(world, fixture);
      for (Map.Entry<Setting<?>, Object> value : saved.entrySet())
        restore(value.getKey(), value.getValue());
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void restore(Setting s, Object value) {
    s.set(value);
  }

  static long count(List<Object> packets, String name) {
    return packets.stream().filter(p -> p.getClass().getSimpleName().equals(name)).count();
  }

  private static void require(boolean yes, String message) {
    if (!yes) throw new AssertionError(message);
    checks++;
  }
}
