package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;
import static perihelion.smoke.RotationPipelineProbe.*;

import java.lang.reflect.*;
import java.util.*;
import org.lwjgl.opengl.Display;
import perihelion.core.control.ControlRequest;
import perihelion.core.control.InputKey;
import perihelion.core.control.Rotation;
import perihelion.core.event.EventOwner;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleContext;
import perihelion.core.module.ModuleEntry;
import perihelion.core.module.ModuleState;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.*;

/** Actual vanilla S12 handling, owned jump input, combat guards and PRE attack rotation checks. */
public final class CombatFlowProbe {
  private static int checks;
  private static final String[] IDS = {
    "velocity",
    "jumpreset",
    "aura",
    "scaffold",
    "speed",
    "flight",
    "longjump",
    "stasis",
    "freecam",
    "clutch"
  };

  public static void run(ClientRuntime r) throws Exception {
    checks = 0;
    Object game =
        method(Class.forName("net.minecraft.client.Minecraft"), "getMinecraft", "func_71410_x")
            .invoke(null);
    Object p = field(game.getClass(), "thePlayer", "field_71439_g").get(game);
    Object world = field(game.getClass(), "theWorld", "field_71441_e").get(game);
    Object settings = field(game.getClass(), "gameSettings", "field_71474_y").get(game);
    Object jump = field(settings.getClass(), "keyBindJump", "field_74314_A").get(settings);
    Field physical = field(jump.getClass(), "pressed", "field_74513_e");
    boolean oldPhysical = physical.getBoolean(jump);
    Object handler = method(game.getClass(), "getNetHandler", "func_147114_u").invoke(game);
    Class<?> packetType =
        Class.forName("net.minecraft.network.play.server.S12PacketEntityVelocity");
    Method receive =
        method(
            handler.getClass(),
            new Class<?>[] {packetType},
            "handleEntityVelocity",
            "func_147244_a");
    int entityId =
        ((Number) method(p.getClass(), "getEntityId", "func_145782_y").invoke(p)).intValue();
    Field ground = field(p.getClass(), "onGround", "field_70122_E");
    boolean oldGround = ground.getBoolean(p);
    Field web = field(p.getClass(), "isInWeb", "field_70134_J");
    boolean oldWeb = web.getBoolean(p);
    Field screen = field(game.getClass(), "currentScreen", "field_71462_r");
    Object oldScreen = screen.get(game);
    Method sprint =
        method(p.getClass(), new Class<?>[] {boolean.class}, "setSprinting", "func_70031_b");
    boolean oldSprint = (Boolean) method(p.getClass(), "isSprinting", "func_70051_ag").invoke(p);
    Field mx = field(p.getClass(), "motionX", "field_70159_w"),
        my = field(p.getClass(), "motionY", "field_70181_x"),
        mz = field(p.getClass(), "motionZ", "field_70179_y");
    double ox = mx.getDouble(p), oy = my.getDouble(p), oz = mz.getDouble(p);
    EventOwner observer =
        new EventOwner(
            "combat-flow-probe",
            failure -> {
              throw new AssertionError(failure);
            });
    observer.activate();
    int[] attacks = {0};
    AutoCloseable listen =
        r.clientEvents()
            .subscribe(
                observer,
                PlayerAttackEvent.class,
                10000,
                false,
                e -> {
                  attacks[0]++;
                  e.cancel();
                });
    try {
      for (String id : IDS) r.modules().setEnabled(id, false);
      screen.set(game, null);
      physical.setBoolean(jump, false);
      ground.setBoolean(p, true);
      sprint.invoke(p, false);
      set(r, "jumpreset", "require-nearby-player", "false");
      r.modules().setEnabled("jumpreset", true);
      receive.invoke(handler, packet(packetType, entityId, .3, .4, -.2));
      check(
          Math.abs(mx.getDouble(p) - .3) < .0002
              && Math.abs(my.getDouble(p) - .4) < .0002
              && Math.abs(mz.getDouble(p) + .2) < .0002,
          "standalone reset leaves the actual vanilla velocity packet intact");
      input(r);
      check(
          r.controls().key(InputKey.JUMP).orElse(false),
          "standalone reset works without sprint or Aura");
      check(!physical.getBoolean(jump), "owned jump never writes physical key state");
      check(
          (Boolean) method(jump.getClass(), "isKeyDown", "func_151470_d").invoke(jump)
              == Display.isActive(),
          "actual key hook applies the jump only to focused gameplay");
      check(attacks[0] == 0, "standalone reset generates no extra attacks");
      input(r);
      check(!r.controls().key(InputKey.JUMP).isPresent(), "one packet produces one input pulse");
      physical.setBoolean(jump, true);
      check(
          (Boolean) method(jump.getClass(), "isKeyDown", "func_151470_d").invoke(jump),
          "manual jump remains available after pulse release");
      physical.setBoolean(jump, false);
      receive.invoke(handler, packet(packetType, entityId, .3, .4, .2));
      set(r, "jumpreset", "chance", "0");
      input(r);
      check(
          !r.controls().key(InputKey.JUMP).isPresent(),
          "setting changes discard already pending jumps");
      receive.invoke(handler, packet(packetType, entityId, .3, .4, .2));
      input(r);
      check(!r.controls().key(InputKey.JUMP).isPresent(), "zero chance never queues input");
      set(r, "jumpreset", "chance", "100");
      for (String condition :
          new String[] {
            "airborne", "menu", "web", "no-horizontal", "downward", "unrelated", "sprint"
          }) {
        ground.setBoolean(p, !condition.equals("airborne"));
        web.setBoolean(p, condition.equals("web"));
        if (condition.equals("menu"))
          screen.set(game, Class.forName("net.minecraft.client.gui.GuiChat").newInstance());
        if (condition.equals("sprint")) set(r, "jumpreset", "only-sprinting", "true");
        receive.invoke(
            handler,
            packet(
                packetType,
                condition.equals("unrelated") ? -900099 : entityId,
                condition.equals("no-horizontal") ? 0 : .3,
                condition.equals("downward") ? -.1 : .4,
                condition.equals("no-horizontal") ? 0 : .2));
        input(r);
        check(!r.controls().key(InputKey.JUMP).isPresent(), "jump reset rejects " + condition);
        screen.set(game, null);
        ground.setBoolean(p, true);
        web.setBoolean(p, false);
        set(r, "jumpreset", "only-sprinting", "false");
        input(r);
        check(
            !r.controls().key(InputKey.JUMP).isPresent(),
            "rejected " + condition + " cannot fire after conditions improve");
      }
      for (String id :
          new String[] {"scaffold", "speed", "flight", "longjump", "stasis", "freecam", "clutch"}) {
        r.modules().setEnabled(id, true);
        ground.setBoolean(p, true);
        receive.invoke(handler, packet(packetType, entityId, .3, .4, .2));
        input(r);
        check(!r.controls().key(InputKey.JUMP).orElse(false), "jump reset yields to " + id);
        r.modules().setEnabled(id, false);
      }
      r.modules().setEnabled("jumpreset", false);
      set(r, "velocity", "mode", "legit");
      set(r, "velocity", "reduce-ticks", "1");
      r.modules().setEnabled("velocity", true);
      ground.setBoolean(p, true);
      sprint.invoke(p, true);
      receive.invoke(handler, packet(packetType, entityId, .3, .4, .2));
      input(r);
      check(
          r.controls().key(InputKey.JUMP).orElse(false),
          "Legit supplies its donor sprint jump without an Aura target");
      set(r, "velocity", "mode", "custom");
      check(
          !r.controls().key(InputKey.JUMP).isPresent(),
          "switching modes immediately releases the embedded jump");
      receive.invoke(handler, packet(packetType, entityId, .3, .4, .2));
      check(
          mx.getDouble(p) == 0 && Math.abs(my.getDouble(p) - .4) < .0002 && mz.getDouble(p) == 0,
          "Custom keeps saved default horizontal/vertical modifiers");
      set(r, "velocity", "mode", "cancel");
      mx.setDouble(p, .7);
      my.setDouble(p, .8);
      mz.setDouble(p, .9);
      receive.invoke(handler, packet(packetType, entityId, .3, .4, .2));
      check(
          mx.getDouble(p) == .7 && my.getDouble(p) == .8 && mz.getDouble(p) == .9,
          "Cancel prevents the actual vanilla S12 motion write");
      r.modules().setEnabled("jumpreset", true);
      receive.invoke(handler, packet(packetType, entityId, .3, .4, .2));
      input(r);
      check(
          !r.controls().key(InputKey.JUMP).isPresent(),
          "standalone reset does not jump on cancelled knockback");
      set(r, "velocity", "mode", "legit");
      set(r, "velocity", "jump-only-sprinting", "false");
      sprint.invoke(p, false);
      receive.invoke(handler, packet(packetType, entityId, .3, .4, .2));
      input(r);
      check(
          r.controls().key(InputKey.JUMP).orElse(false),
          "standalone takes precedence over embedded Legit jump");
      check(
          r.modules().require("velocity").state() == ModuleState.ENABLED
              && r.modules().require("jumpreset").state() == ModuleState.ENABLED,
          "coexisting controls do not fault");
      r.modules().setEnabled("jumpreset", false);
      check(
          !r.controls().key(InputKey.JUMP).isPresent(), "disable releases owned jump immediately");
      receive.invoke(handler, packet(packetType, entityId, .3, .4, .2));
      r.modules().setEnabled("velocity", false);
      r.modules().setEnabled("velocity", true);
      input(r);
      check(
          !r.controls().key(InputKey.JUMP).isPresent(),
          "reenabling cannot reuse a pending knockback");
      r.modules().setEnabled("velocity", false);
      Object aura = r.modules().require("aura").module();
      check(
          !(Boolean)
              method(aura.getClass(), new Class<?>[] {boolean.class}, "reduceKnockback")
                  .invoke(aura, false),
          "disabled Aura cannot receive Velocity reduction attacks");
      check(attacks[0] == 0, "packet and input-only cases never auto-attack");
      reduction(r, p, world, attacks);
      legitReduction(r, p, world, handler, receive, packetType, entityId, attacks);
      System.out.println("PERIHELION_SMOKE COMBAT_FLOW checks=" + checks + " PASS");
    } finally {
      listen.close();
      observer.close();
      for (String id : IDS) {
        r.modules().setEnabled(id, false);
        for (Setting<?> s : r.modules().require(id).module().settings().values()) reset(r, id, s);
      }
      HookBridge.gameControls().apply();
      screen.set(game, oldScreen);
      ground.setBoolean(p, oldGround);
      web.setBoolean(p, oldWeb);
      sprint.invoke(p, oldSprint);
      mx.setDouble(p, ox);
      my.setDouble(p, oy);
      mz.setDouble(p, oz);
      physical.setBoolean(jump, oldPhysical);
    }
  }

  @SuppressWarnings("unchecked")
  private static void reduction(ClientRuntime r, Object p, Object world, int[] attacks)
      throws Exception {
    Object pig =
        Class.forName("net.minecraft.entity.passive.EntityPig")
            .getConstructor(Class.forName("net.minecraft.world.World"))
            .newInstance(world);
    double x = field(p.getClass(), "posX", "field_70165_t").getDouble(p),
        y = field(p.getClass(), "posY", "field_70163_u").getDouble(p),
        z = field(p.getClass(), "posZ", "field_70161_v").getDouble(p);
    position(pig, x, y, z + 1.8);
    Method add =
        method(
            world.getClass(),
            new Class<?>[] {int.class, Class.forName("net.minecraft.entity.Entity")},
            "addEntityToWorld",
            "func_73027_a");
    Method remove =
        method(
            world.getClass(), new Class<?>[] {int.class}, "removeEntityFromWorld", "func_73028_b");
    add.invoke(world, -7654399, pig);
    Object aura = r.modules().require("aura").module();
    try {
      set(r, "aura", "targets", "ANIMALS");
      set(r, "aura", "sort", "DISTANCE");
      set(r, "aura", "fov", "360");
      set(r, "aura", "weapons-only", "false");
      set(r, "aura", "through-walls", "true");
      set(r, "aura", "auto-block", "NONE");
      set(r, "aura", "simulate-mouse-clicks", "false");
      set(r, "aura", "ray-cast", "false");
      set(r, "aura", "rotations", "NORMAL");
      r.modules().setEnabled("aura", true);
      HookBridge.gameControls().beforeInput();
      check(
          method(aura.getClass(), "target").invoke(aura) == pig,
          "Aura selects the close reduction fixture");
      field(aura.getClass(), "nextAttack").setDouble(aura, Double.MAX_VALUE);
      Method reduce = method(aura.getClass(), new Class<?>[] {boolean.class}, "reduceKnockback");
      check(
          !(Boolean) reduce.invoke(aura, false),
          "new unsent Aura aim cannot authorize a PRE reduction attack");
      set(r, "aura", "rotations", "NONE");
      method(aura.getClass(), "prepare").invoke(aura);
      int before = attacks[0];
      check(
          (Boolean) reduce.invoke(aura, false) && attacks[0] == before + 1,
          "explicit no-rotation Aura mode retains its normal attack-hook path");
      check(
          !(Boolean) reduce.invoke(aura, true) && attacks[0] == before + 1,
          "Respect Aura Delay prevents premature extra attacks");
      set(r, "aura", "rotations", "NORMAL");
      method(aura.getClass(), "prepare").invoke(aura);
      Class<?> vec = Class.forName("net.minecraft.util.Vec3");
      Object point =
          vec.getConstructor(double.class, double.class, double.class)
              .newInstance(x, y + .45, z + 1.8);
      Rotation aim =
          (Rotation)
              method(
                      Class.forName(
                          "perihelion.integration.minecraft189.module.gameplay.GameActions"),
                      new Class<?>[] {p.getClass(), vec},
                      "lookAt")
                  .invoke(null, p, point);
      ControlRequest<Rotation> rotation =
          (ControlRequest<Rotation>) field(aura.getClass(), "rotation").get(aura);
      field(aura.getClass(), "aim").set(aura, aim);
      rotation.setForTicks(aim, 1);
      forceLook(p);
      motion(p);
      HookBridge.gameControls().beforeInput();
      Rotation desired = rotation.value().get();
      before = attacks[0];
      check(
          (Boolean) reduce.invoke(aura, false) && attacks[0] == before + 1,
          "previously submitted Aura rotation authorizes exactly one PRE attack hook");
      check(
          rotation.value().orElse(null) == desired,
          "PRE reduction restores the next desired rotation after the action");
      ModuleContext context =
          (ModuleContext) field(ModuleEntry.class, "context").get(r.modules().require("aura"));
      ControlRequest<Rotation> competing = context.controls().rotation(100);
      competing.set(Rotation.server(90, 0));
      before = attacks[0];
      check(
          !(Boolean) reduce.invoke(aura, false) && attacks[0] == before,
          "competing rotation ownership prevents reduction attacks");
      competing.close();
      position(pig, x + 2.8, y, z);
      check(
          !(Boolean) reduce.invoke(aura, false) && attacks[0] == before,
          "a moved target outside the previously sent ray cannot be attacked");
    } finally {
      r.modules().setEnabled("aura", false);
      remove.invoke(world, -7654399);
    }
  }

  private static Object packet(Class<?> type, int id, double x, double y, double z)
      throws Exception {
    return type.getConstructor(int.class, double.class, double.class, double.class)
        .newInstance(id, x, y, z);
  }

  private static void legitReduction(
      ClientRuntime r,
      Object p,
      Object world,
      Object handler,
      Method receive,
      Class<?> packetType,
      int entityId,
      int[] attacks)
      throws Exception {
    Object pig =
        Class.forName("net.minecraft.entity.passive.EntityPig")
            .getConstructor(Class.forName("net.minecraft.world.World"))
            .newInstance(world);
    double x = field(p.getClass(), "posX", "field_70165_t").getDouble(p),
        y = field(p.getClass(), "posY", "field_70163_u").getDouble(p),
        z = field(p.getClass(), "posZ", "field_70161_v").getDouble(p);
    Field lived = field(p.getClass(), "ticksExisted", "field_70173_aa");
    int originalTicks = lived.getInt(p);
    double originalY = y;
    y = 240;
    position(p, x, y, z);
    position(pig, x, y, z + 1.8);
    Method add =
        method(
            world.getClass(),
            new Class<?>[] {int.class, Class.forName("net.minecraft.entity.Entity")},
            "addEntityToWorld",
            "func_73027_a");
    Method remove =
        method(
            world.getClass(), new Class<?>[] {int.class}, "removeEntityFromWorld", "func_73028_b");
    add.invoke(world, -7654398, pig);
    try {
      r.modules().setEnabled("aura", false);
      r.modules().setEnabled("velocity", false);
      set(r, "velocity", "mode", "LEGIT");
      set(r, "velocity", "reduce-ticks", "1");
      r.modules().setEnabled("velocity", true);
      int before = attacks[0];
      receive.invoke(handler, packet(packetType, entityId, .3, .4, .2));
      HookBridge.gameControls().beforeInput();
      check(attacks[0] == before, "Legit waits for its first submitted walking aim");
      check(r.controls().rotation().isPresent(), "Legit independently prepares close-range aim");
      forceLook(p);
      motion(p);
      HookBridge.gameControls().beforeInput();
      check(
          attacks[0] == before + 1,
          "one Reduce Tick survives the walking gate and attacks without Aura");
      forceLook(p);
      motion(p);
      HookBridge.gameControls().beforeInput();
      check(attacks[0] == before + 1, "one Reduce Tick cannot repeat after consumption");

      receive.invoke(handler, packet(packetType, entityId, .3, .4, .2));
      HookBridge.gameControls().beforeInput();
      forceLook(p);
      motion(p);
      position(pig, x + 2.8, y, z);
      HookBridge.gameControls().beforeInput();
      check(attacks[0] == before + 1, "Legit drops a target moved outside its submitted ray");

      position(pig, x, y, z + 1.8);
      receive.invoke(handler, packet(packetType, entityId, .3, .4, .2));
      HookBridge.gameControls().beforeInput();
      forceLook(p);
      motion(p);
      r.modules().setEnabled("velocity", false);
      r.modules().setEnabled("velocity", true);
      HookBridge.gameControls().beforeInput();
      check(
          attacks[0] == before + 1 && !r.controls().rotation().isPresent(),
          "new Legit activation cannot reuse the preceding prepared attack");
      lived.setInt(p, originalTicks + 1);
      receive.invoke(handler, packet(packetType, entityId, .3, .4, .2));
      HookBridge.gameControls().beforeInput();
      check(r.controls().rotation().isPresent(), "next tick prepares a fresh Legit attack opportunity");
      forceLook(p);
      motion(p);
      Class<?> action = Class.forName("net.minecraft.network.play.client.C02PacketUseEntity$Action");
      Object manual = Class.forName("net.minecraft.network.play.client.C02PacketUseEntity")
          .getConstructor(Class.forName("net.minecraft.entity.Entity"), action)
          .newInstance(pig, action.getField("ATTACK").get(null));
      method(Class.forName("perihelion.integration.minecraft189.module.gameplay.GameActions"),
          new Class<?>[] {Class.forName("net.minecraft.network.Packet")}, "send").invoke(null, manual);
      HookBridge.gameControls().beforeInput();
      check(attacks[0] == before + 1,
          "an actual local manual attack suppresses the same tick's prepared Legit extra attack");
    } finally {
      r.modules().setEnabled("velocity", false);
      remove.invoke(world, -7654398);
      position(p, x, originalY, z);
      lived.setInt(p, originalTicks);
    }
  }

  private static void input(ClientRuntime r) {
    r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
  }

  private static <T> void reset(ClientRuntime r, String id, Setting<T> s) {
    r.modules().setSetting(id, s, s.defaultValue());
  }

  private static void check(boolean yes, String message) {
    checks++;
    if (!yes) throw new AssertionError(message);
  }
}
