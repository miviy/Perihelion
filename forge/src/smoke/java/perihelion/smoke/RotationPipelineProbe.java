package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;

import io.netty.channel.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.lwjgl.opengl.GL11;
import perihelion.core.control.*;
import perihelion.core.event.*;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.*;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.PlayerControlEvent;

/** Native walking/interaction ordering and item prediction in the disposable local world. */
public final class RotationPipelineProbe {
  private static int checks;
  private static final String HANDLER = "perihelion-rotation-probe";
  private static final String GAMEPLAY = "perihelion.integration.minecraft189.module.gameplay.";

  public static void run(ClientRuntime r) throws Exception {
    checks = 0;
    Object game =
        method(Class.forName("net.minecraft.client.Minecraft"), "getMinecraft", "func_71410_x")
            .invoke(null);
    Object player = field(game.getClass(), "thePlayer", "field_71439_g").get(game);
    Object world = field(game.getClass(), "theWorld", "field_71441_e").get(game);
    Object inv = field(player.getClass(), "inventory", "field_71071_by").get(player);
    Object[] items = (Object[]) field(inv.getClass(), "mainInventory", "field_70462_a").get(inv);
    Object[] savedItems = items.clone();
    Map<Field, Object> savedPlayer = new LinkedHashMap<>();
    for (String[] names :
        new String[][] {
          {"rotationYaw", "field_70177_z"},
          {"rotationPitch", "field_70125_A"},
          {"fallDistance", "field_70143_R"},
          {"onGround", "field_70122_E"},
          {"lastReportedYaw", "field_175164_bL"},
          {"lastReportedPitch", "field_175165_bM"}
        }) {
      Field f = field(player.getClass(), names);
      savedPlayer.put(f, f.get(player));
    }
    Field screen = field(game.getClass(), "currentScreen", "field_71462_r"),
        focus = field(game.getClass(), "inGameHasFocus", "field_71415_G"),
        selected = field(inv.getClass(), "currentItem", "field_70461_c"),
        mouseOver = field(game.getClass(), "objectMouseOver", "field_71476_x");
    Object oldScreen = screen.get(game), oldMouse = mouseOver.get(game);
    boolean oldFocus = focus.getBoolean(game);
    int oldSelected = selected.getInt(inv);
    double px = number(player, "posX", "field_70165_t"),
        py = number(player, "posY", "field_70163_u"),
        pz = number(player, "posZ", "field_70161_v");
    int x = (int) Math.floor(px),
        y = Math.min(220, (int) Math.floor(py) + 20),
        z = (int) Math.floor(pz);
    Class<?> posType = Class.forName("net.minecraft.util.BlockPos"),
        stateType = Class.forName("net.minecraft.block.state.IBlockState");
    Method
        getState =
            method(world.getClass(), new Class<?>[] {posType}, "getBlockState", "func_180495_p"),
        putState =
            method(
                world.getClass(),
                new Class<?>[] {posType, stateType, int.class},
                "setBlockState",
                "func_180501_a");
    Map<Object, Object> savedBlocks = new LinkedHashMap<>();
    Object handler = method(game.getClass(), "getNetHandler", "func_147114_u").invoke(game);
    Object network =
        method(handler.getClass(), "getNetworkManager", "func_147298_b").invoke(handler);
    Channel channel = (Channel) field(network.getClass(), "channel", "field_150746_k").get(network);
    List<Object> packets = new CopyOnWriteArrayList<>();
    channel
        .pipeline()
        .addLast(
            HANDLER,
            new ChannelOutboundHandlerAdapter() {
              @Override
              public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise)
                  throws Exception {
                if (packets.size() < 256) packets.add(msg);
                ctx.write(msg, promise);
              }
            });
    int listeners = r.clientEvents().subscriptionCount();
    try {
      screen.set(game, null);
      focus.setBoolean(game, true);
      Arrays.fill(items, null);
      selected.setInt(inv, 4);
      for (int dx = -3; dx <= 3; dx++)
        for (int dy = 0; dy <= 5; dy++)
          for (int dz = -3; dz <= 3; dz++) {
            Object pos = pos(x + dx, y + dy, z + dz);
            savedBlocks.put(pos, getState.invoke(world, pos));
            putState.invoke(world, pos, state("air"), 2);
          }
      Object support = pos(x, y, z), water = pos(x, y + 1, z);
      putState.invoke(world, support, state("stone"), 2);
      for (String item : new String[] {"water_bucket", "web"}) {
        putState.invoke(world, water, state("air"), 2);
        position(player, x + .5, y + 2, z + .5);
        camera(player, 173, -70);
        field(player.getClass(), "fallDistance", "field_70143_R").setFloat(player, 4);
        field(player.getClass(), "onGround", "field_70122_E").setBoolean(player, false);
        items[0] = stack(item);
        selected.setInt(inv, 4);
        set(r, "nofall", "mode", "MLG");
        r.modules().setEnabled("nofall", true);
        HookBridge.gameControls().beforeInput();
        flush(channel);
        packets.clear();
        forceLook(player);
        motion(player);
        flush(channel);
        HookBridge.gameControls().beforeInput();
        forceLook(player);
        motion(player);
        flush(channel);
        check(
            !state("air").equals(getState.invoke(world, water)),
            "MLG locally places " + item + " while camera looks up");
        check(
            number(player, "rotationYaw", "field_70177_z") == 173
                && number(player, "rotationPitch", "field_70125_A") == -70,
            "MLG restores camera after " + item);
        check(mouseOver.get(game) == oldMouse, "MLG restores picked target after " + item);
        ordered(
            packets, "C08PacketPlayerBlockPlacement", r.controls().rotation().get(), "MLG " + item);
        model(game, player);
        r.modules().setEnabled("nofall", false);
        HookBridge.gameControls().apply();
        check(
            !r.controls().rotation().isPresent() && !r.controls().hotbar().isPresent(),
            "MLG releases claims after " + item);
      }
      putState.invoke(world, water, state("air"), 2);
      items[0] = stack("water_bucket");
      r.modules().setEnabled("nofall", true);
      HookBridge.gameControls().beforeInput();
      Arrays.fill(items, null);
      HookBridge.gameControls().beforeInput();
      check(
          !r.controls().rotation().isPresent() && !r.controls().hotbar().isPresent(),
          "MLG clears removed item claims");
      items[0] = stack("water_bucket");
      screen.set(game, Class.forName("net.minecraft.client.gui.GuiChat").newInstance());
      HookBridge.gameControls().beforeInput();
      check(!r.controls().rotation().isPresent(), "MLG pauses in menus");
      screen.set(game, null);
      r.modules().setEnabled("nofall", false);
      HookBridge.gameControls().apply();
      // An existing web already provides the catch. Water must not replace it.
      Object occupiedWeb = pos(x, y + 2, z);
      putState.invoke(world, occupiedWeb, state("web"), 2);
      position(player, x + .5, y + 2, z + .5);
      r.modules().setEnabled("nofall", true);
      HookBridge.gameControls().beforeInput();
      flush(channel);
      packets.clear();
      forceLook(player);
      motion(player);
      flush(channel);
      check(
          packets.stream()
              .noneMatch(p -> p.getClass().getSimpleName().equals("C08PacketPlayerBlockPlacement")),
          "MLG preserves an existing web catch");
      r.modules().setEnabled("nofall", false);
      HookBridge.gameControls().apply();
      putState.invoke(world, occupiedWeb, state("air"), 2);

      scaffold(r, player, world, items, getState, putState, channel, packets, x, y, z);
      aura(r, player, world, items, channel, packets, x, y, z);
      AuraPreProbe.run(r, game, player, world, items, channel, packets, x, y, z);
      DisplaceHookProbe.run(r, game, player, world, items, channel, packets, x, y, z);
      antiFireball(r, player, world, items, channel, packets, x, y, z);
      ModuleActionProbe.run(
          r, game, player, world, items, getState, putState, channel, packets, x, y, z);
      Yuri13Probe.run(r, game, player, world, items, getState, putState, channel, packets, x, y, z);
      YuriLatestProbe.run(r, game, player, world, items, channel, packets, x, y, z);
      CompatibilityQolProbe.run(
          r, game, player, world, items, getState, putState, channel, packets, x, y, z);
    } finally {
      for (String id : new String[] {"nofall", "scaffold", "aura", "antifireball"}) {
        r.modules().setEnabled(id, false);
        for (perihelion.core.setting.Setting<?> setting :
            r.modules().require(id).module().settings().values()) reset(r, id, setting);
      }
      HookBridge.gameControls().apply();
      channel.pipeline().remove(HANDLER);
      flush(channel);
      for (Map.Entry<Object, Object> entry : savedBlocks.entrySet())
        putState.invoke(world, entry.getKey(), entry.getValue(), 2);
      System.arraycopy(savedItems, 0, items, 0, items.length);
      selected.setInt(inv, oldSelected);
      screen.set(game, oldScreen);
      focus.setBoolean(game, oldFocus);
      mouseOver.set(game, oldMouse);
      position(player, px, py, pz);
      for (Map.Entry<Field, Object> entry : savedPlayer.entrySet())
        entry.getKey().set(player, entry.getValue());
    }
    check(r.clientEvents().subscriptionCount() == listeners, "Rotation probe releases listeners");
    System.out.println("PERIHELION_SMOKE ROTATIONS checks=" + checks + " PASS");
  }

  static void scaffold(
      ClientRuntime r,
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
    Object input = field(player.getClass(), "movementInput", "field_71158_b").get(player);
    Field forward = field(input.getClass(), "moveForward", "field_78900_b");
    float oldForward = forward.getFloat(input);
    Object target = pos(x, y, z + 1);
    set(r, "scaffold", "min-rotation", "10");
    set(r, "scaffold", "max-rotation", "10");
    set(r, "scaffold", "keep-y", "false");
    set(r, "scaffold", "auto-jump", "false");
    try {
      for (String rotations : new String[] {"OLD", "NORMAL", "RANDOMIZED"})
        for (String search : new String[] {"NORMAL", "SECONDARY", "ULTRA_SAFE"})
          for (String ray : new String[] {"NORMAL", "STRICT"}) {
            set(r, "scaffold", "rotations", rotations);
            set(r, "scaffold", "search-algorithm", search);
            putState.invoke(world, target, state("air"), 2);
            items[0] = stack("stone");
            field(items[0].getClass(), "stackSize", "field_77994_a").setInt(items[0], 64);
            position(player, x + .5, y + 1, z + 1.15);
            camera(player, 145, -40);
            forward.setFloat(input, 1);
            field(player.getClass(), "fallDistance", "field_70143_R").setFloat(player, 0);
            set(r, "scaffold", "ray-cast", ray);
            r.modules().setEnabled("scaffold", true);
            HookBridge.gameControls().beforeInput();
            method(
                    player.getClass(),
                    new Class<?>[] {double.class, double.class, double.class},
                    "moveEntity",
                    "func_70091_d")
                .invoke(player, .15, 0., .4);
            flush(channel);
            packets.clear();
            forceLook(player);
            motion(player);
            flush(channel);
            check(
                state("air").equals(getState.invoke(world, target)),
                "Scaffold does not place after its walking packet");
            HookBridge.gameControls().beforeInput();
            motion(player);
            flush(channel);
            // Yuri prepares before movement. If that initial ray is lost during the
            // simulated move, the fresh PRE search may need one more submitted aim.
            if (state("air").equals(getState.invoke(world, target))) {
              HookBridge.gameControls().beforeInput();
              motion(player);
              flush(channel);
            }
            // Randomized rotations can need another quantized aim before the strict ray fits.
            // Keep the immediate assertion for deterministic modes and bound random convergence.
            if (rotations.equals("RANDOMIZED"))
              for (int turn = 0;
                  turn < 6 && state("air").equals(getState.invoke(world, target));
                  turn++) {
                HookBridge.gameControls().beforeInput();
                motion(player);
                flush(channel);
              }
            check(
                !state("air").equals(getState.invoke(world, target)),
                "Scaffold "
                    + rotations
                    + "/"
                    + search
                    + "/"
                    + ray
                    + " aims from final moved position");
            check(
                number(player, "rotationYaw", "field_70177_z") == 145
                    && number(player, "rotationPitch", "field_70125_A") == -40,
                "Scaffold keeps camera");
            orderedPrePlacement(packets, "Scaffold " + ray);
            r.modules().setEnabled("scaffold", false);
            HookBridge.gameControls().apply();
          }
    } finally {
      r.modules().setEnabled("scaffold", false);
      forward.setFloat(input, oldForward);
      putState.invoke(world, target, state("air"), 2);
    }
  }

  private static void aura(
      ClientRuntime r,
      Object player,
      Object world,
      Object[] items,
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
    position(pig, x + 2.5, y + 1, z + .5);
    camera(player, 170, -35);
    items[0] = stack("diamond_sword");
    Object inv = field(player.getClass(), "inventory", "field_71071_by").get(player);
    field(inv.getClass(), "currentItem", "field_70461_c").setInt(inv, 0);
    add(world, -887710, pig);
    for (String[] setting :
        new String[][] {
          {"targets", "ANIMALS"},
          {"fov", "360"},
          {"rotations", "NORMAL"},
          {"ray-cast", "true"},
          {"simulate-mouse-clicks", "false"},
          {"auto-block", "NONE"},
          {"through-walls", "true"},
          {"min-rotation-speed", "10"},
          {"max-rotation-speed", "10"}
        }) set(r, "aura", setting[0], setting[1]);
    EventOwner owner =
        new EventOwner(
            "rotation-order-fixture",
            failure -> {
              throw new AssertionError(failure);
            });
    owner.activate();
    List<AutoCloseable> resources = new ArrayList<>();
    ControlRequest<Rotation> blocker = r.controls().scope(owner, resources::add).rotation(1000);
    Subscription release =
        r.clientEvents()
            .subscribe(
                owner,
                PlayerControlEvent.class,
                1000,
                false,
                e -> {
                  if (e == PlayerControlEvent.AFTER_MOTION) blocker.clear();
                });
    try {
      r.modules().setEnabled("aura", true);
      Object aura = r.modules().require("aura").module();
      HookBridge.gameControls().beforeInput();
      field(aura.getClass(), "nextAttack").setLong(aura, 0);
      flush(channel);
      packets.clear();
      forceLook(player);
      motion(player);
      flush(channel);
      check(
          packets.stream()
              .noneMatch(
                  p ->
                      p.getClass().getSimpleName().equals("C02PacketUseEntity")
                          || p.getClass().getSimpleName().equals("C0APacketAnimation")),
          "Aura cannot attack or swing with its initial unsent rotation");
      Rotation submitted = r.controls().rotation().get();
      HookBridge.gameControls().beforeInput();
      forceLook(player);
      motion(player);
      flush(channel);
      ordered(packets, "C02PacketUseEntity", submitted, "Aura PRE");
      int precedingWalk = 0;
      Class<?> walking = Class.forName("net.minecraft.network.play.client.C03PacketPlayer");
      while (!walking.isInstance(packets.get(precedingWalk))) precedingWalk++;
      orderedBeforeWalking(
          packets.subList(precedingWalk + 1, packets.size()), "C02PacketUseEntity", "Aura PRE");
      check(
          number(player, "rotationYaw", "field_70177_z") == 170
              && number(player, "rotationPitch", "field_70125_A") == -35,
          "Aura keeps camera");
      movement(r, player);
      blocker.set(Rotation.server(60, 10));
      field(aura.getClass(), "nextAttack").setLong(aura, 0);
      flush(channel);
      packets.clear();
      HookBridge.gameControls().beforeInput();
      forceLook(player);
      motion(player);
      HookBridge.gameControls().beforeInput();
      flush(channel);
      check(
          packets.stream()
              .noneMatch(p -> p.getClass().getSimpleName().equals("C02PacketUseEntity")),
          "Aura cannot attack after another submitted owner releases");
    } finally {
      release.close();
      for (AutoCloseable resource : resources) resource.close();
      owner.close();
      r.modules().setEnabled("aura", false);
      remove(world, -887710);
      HookBridge.gameControls().apply();
    }
  }

  private static void antiFireball(
      ClientRuntime r,
      Object player,
      Object world,
      Object[] items,
      Channel channel,
      List<Object> packets,
      int x,
      int y,
      int z)
      throws Exception {
    Object ball =
        Class.forName("net.minecraft.entity.projectile.EntityLargeFireball")
            .getConstructor(Class.forName("net.minecraft.world.World"))
            .newInstance(world);
    position(player, x + .5, y + 1, z + .5);
    position(ball, x + .5, y + 2, z + 2.5);
    items[0] = stack("diamond_sword");
    add(world, -887711, ball);
    try {
      // Prior item/attack fixtures can leave action facts dirty. Begin a coherent new
      // walking window before testing this module's own preparation/action pair.
      forceLook(player);
      motion(player);
      flush(channel);
      r.modules().setEnabled("antifireball", true);
      field(ball.getClass(), "isDead", "field_70128_L").setBoolean(ball, true);
      HookBridge.gameControls().beforeInput();
      check(!r.controls().rotation().isPresent(), "Anti Fireball ignores dead loaded entities");
      field(ball.getClass(), "isDead", "field_70128_L").setBoolean(ball, false);
      HookBridge.gameControls().beforeInput();
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
      ordered(packets, "C02PacketUseEntity", submitted, "Anti Fireball PRE");
    } finally {
      r.modules().setEnabled("antifireball", false);
      remove(world, -887711);
      HookBridge.gameControls().apply();
    }
  }

  private static void movement(ClientRuntime r, Object player) throws Exception {
    Field mx = field(player.getClass(), "motionX", "field_70159_w"),
        my = field(player.getClass(), "motionY", "field_70181_x"),
        mz = field(player.getClass(), "motionZ", "field_70179_y");
    double x = mx.getDouble(player), y = my.getDouble(player), z = mz.getDouble(player);
    Method sprint =
        method(player.getClass(), new Class<?>[] {boolean.class}, "setSprinting", "func_70031_b");
    boolean sprinting =
        (Boolean) method(player.getClass(), "isSprinting", "func_70051_ag").invoke(player);
    Method flying =
        method(
            player.getClass(),
            new Class<?>[] {float.class, float.class, float.class},
            "moveFlying",
            "func_70060_a");
    try {
      for (String mode : new String[] {"SILENT", "STRICT"}) {
        set(r, "aura", "move-fix", mode);
        perihelion.integration.minecraft189.event.MovementInputEvent input =
            r.clientEvents()
                .post(new perihelion.integration.minecraft189.event.MovementInputEvent(0, .3f));
        if (mode.equals("SILENT")) {
          check(
              Math.abs(Math.max(Math.abs(input.strafe()), Math.abs(input.forward())) - .3) < 1e-6
                  && (input.strafe() == 0 || Math.abs(input.strafe()) == .3f)
                  && (input.forward() == 0 || Math.abs(input.forward()) == .3f),
              "Actual Aura SILENT chooses digital input before physics while preserving sneak"
                  + " magnitude");
          double heading =
              Math.toDegrees(Math.atan2(-input.strafe(), input.forward()))
                  + r.controls().rotation().get().yaw();
          double difference = (heading - number(player, "rotationYaw", "field_70177_z")) % 360;
          if (difference > 180) difference -= 360;
          if (difference < -180) difference += 360;
          check(
              Math.abs(difference) <= 22.501,
              "Aura NORMAL input chooses the nearest legal world direction");
        } else
          check(
              input.strafe() == 0 && input.forward() == .3f,
              "Aura STRICT retains uncorrected input for traditional movement");
        mx.setDouble(player, 0);
        mz.setDouble(player, 0);
        flying.invoke(player, input.strafe(), input.forward(), .1f);
        double yaw = Math.toRadians(r.controls().rotation().get().yaw());
        check(
            Math.abs(
                        mx.getDouble(player)
                            - (input.strafe() * Math.cos(yaw) - input.forward() * Math.sin(yaw))
                                * .1)
                    < 1e-5
                && Math.abs(
                        mz.getDouble(player)
                            - (input.forward() * Math.cos(yaw) + input.strafe() * Math.sin(yaw))
                                * .1)
                    < 1e-5,
            "Actual Aura " + mode + " applies the input basis once at movement physics");
        mx.setDouble(player, 0);
        mz.setDouble(player, 0);
        sprint.invoke(player, true);
        method(player.getClass(), "jump", "func_70664_aZ").invoke(player);
        double jumpYaw = Math.toRadians(r.controls().rotation().get().yaw());
        check(
            Math.abs(mx.getDouble(player) + Math.sin(jumpYaw) * .2) < 1e-4
                && Math.abs(mz.getDouble(player) - Math.cos(jumpYaw) * .2) < 1e-4,
            "Actual Aura " + mode + " aligns sprint jump consistently");
      }
    } finally {
      mx.setDouble(player, x);
      my.setDouble(player, y);
      mz.setDouble(player, z);
      sprint.invoke(player, sprinting);
      set(r, "aura", "move-fix", "SILENT");
    }
  }

  private static void model(Object game, Object player) throws Exception {
    Object manager = method(game.getClass(), "getRenderManager", "func_175598_ae").invoke(game);
    Object renderer =
        method(
                manager.getClass(),
                new Class<?>[] {Class.forName("net.minecraft.entity.Entity")},
                "getEntityRenderObject",
                "func_78713_a")
            .invoke(manager, player);
    GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
    GL11.glPushMatrix();
    try {
      method(
              renderer.getClass(),
              new Class<?>[] {
                Class.forName("net.minecraft.client.entity.AbstractClientPlayer"),
                double.class,
                double.class,
                double.class,
                float.class,
                float.class
              },
              "doRender",
              "func_76986_a")
          .invoke(renderer, player, 0., 0., 0., 0f, 1f);
      Object model = method(renderer.getClass(), "getMainModel", "func_177087_b").invoke(renderer);
      Object head = field(model.getClass(), "bipedHead", "field_78116_c").get(model);
      check(
          Math.abs(number(head, "rotateAngleX", "field_78795_f") - Math.PI / 2) < 1e-5,
          "Actual player renderer uses submitted pitch");
    } finally {
      GL11.glPopMatrix();
      GL11.glPopAttrib();
    }
  }

  static void orderedPrePlacement(List<Object> packets, String label) throws Exception {
    Class<?> walking = Class.forName("net.minecraft.network.play.client.C03PacketPlayer");
    int priorLook = -1, placement = -1, nextWalk = -1;
    for (int i = 0; i < packets.size(); i++) {
      Object packet = packets.get(i);
      if (packet.getClass().getSimpleName().equals("C08PacketPlayerBlockPlacement")) {
        if (placement < 0) placement = i;
      } else if (walking.isInstance(packet)) {
        if (placement >= 0) {
          nextWalk = i;
          break;
        }
        if ((Boolean) method(walking, "getRotating", "func_149463_k").invoke(packet)) priorLook = i;
      }
    }
    check(priorLook >= 0 && placement > priorLook, label + " uses a preceding submitted look");
    check(nextWalk > placement, label + " places before the next walking packet");
  }

  static void orderedBeforeWalking(List<Object> packets, String action, String label)
      throws Exception {
    Class<?> walking = Class.forName("net.minecraft.network.play.client.C03PacketPlayer");
    int use = -1, nextWalk = -1;
    for (int i = 0; i < packets.size(); i++) {
      Object packet = packets.get(i);
      if (packet.getClass().getSimpleName().equals(action) && use < 0) use = i;
      if (walking.isInstance(packet) && nextWalk < 0) nextWalk = i;
    }
    check(use >= 0 && nextWalk > use, label + " acts before the next walking packet");
    for (int i = nextWalk + 1; i < packets.size(); i++)
      check(
          !packets.get(i).getClass().getSimpleName().equals(action),
          label + " has no action after its walking packet");
  }

  static void ordered(List<Object> packets, String action, Rotation rotation, String label)
      throws Exception {
    int look = -1, use = -1;
    Object packet = null;
    Class<?> c03 = Class.forName("net.minecraft.network.play.client.C03PacketPlayer");
    for (int i = 0; i < packets.size(); i++) {
      Object p = packets.get(i);
      if (c03.isInstance(p) && (Boolean) method(c03, "getRotating", "func_149463_k").invoke(p)) {
        look = i;
        packet = p;
      }
      if (p.getClass().getSimpleName().equals(action)) {
        use = i;
        break;
      }
    }
    check(look >= 0 && use > look, label + " submits look before action");
    check(
        Math.abs(
                    ((Number) method(c03, "getYaw", "func_149462_g").invoke(packet)).floatValue()
                        - rotation.yaw())
                < 1e-4
            && Math.abs(
                    ((Number) method(c03, "getPitch", "func_149470_h").invoke(packet)).floatValue()
                        - rotation.pitch())
                < 1e-4,
        label + " packet carries matching yaw/pitch");
  }

  static void forceLook(Object player) throws Exception {
    field(player.getClass(), "lastReportedYaw", "field_175164_bL").setFloat(player, -171);
    field(player.getClass(), "lastReportedPitch", "field_175165_bM").setFloat(player, -72);
  }

  static void camera(Object player, float yaw, float pitch) throws Exception {
    field(player.getClass(), "rotationYaw", "field_70177_z").setFloat(player, yaw);
    field(player.getClass(), "rotationPitch", "field_70125_A").setFloat(player, pitch);
  }

  static void flush(Channel channel) {
    channel.eventLoop().submit(() -> {}).syncUninterruptibly();
  }

  static double number(Object object, String... names) throws Exception {
    return ((Number) field(object.getClass(), names).get(object)).doubleValue();
  }

  static void position(Object entity, double x, double y, double z) throws Exception {
    method(
            entity.getClass(),
            new Class<?>[] {double.class, double.class, double.class},
            "setPosition",
            "func_70107_b")
        .invoke(entity, x, y, z);
  }

  static Object pos(int x, int y, int z) throws Exception {
    return Class.forName("net.minecraft.util.BlockPos")
        .getConstructor(int.class, int.class, int.class)
        .newInstance(x, y, z);
  }

  static Object state(String name) throws Exception {
    Object block =
        method(
                Class.forName("net.minecraft.block.Block"),
                new Class<?>[] {String.class},
                "getBlockFromName",
                "func_149684_b")
            .invoke(null, name);
    return method(block.getClass(), "getDefaultState", "func_176223_P").invoke(block);
  }

  private static void add(Object world, int id, Object entity) throws Exception {
    method(
            world.getClass(),
            new Class<?>[] {int.class, Class.forName("net.minecraft.entity.Entity")},
            "addEntityToWorld",
            "func_73027_a")
        .invoke(world, id, entity);
  }

  private static void remove(Object world, int id) throws Exception {
    method(world.getClass(), new Class<?>[] {int.class}, "removeEntityFromWorld", "func_73028_b")
        .invoke(world, id);
  }

  private static <T> void reset(
      ClientRuntime r, String id, perihelion.core.setting.Setting<T> setting) {
    r.modules().setSetting(id, setting, setting.defaultValue());
  }

  private static void check(boolean result, String label) {
    if (!result) throw new AssertionError(label);
    checks++;
  }
}
