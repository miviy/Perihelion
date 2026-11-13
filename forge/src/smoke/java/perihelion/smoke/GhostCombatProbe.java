package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;
import static perihelion.smoke.RotationPipelineProbe.*;

import java.lang.reflect.*;
import java.util.*;
import perihelion.core.control.*;
import perihelion.core.event.ClientTickEvent;
import perihelion.core.event.EventOwner;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleState;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.*;

/** Transformed ray selection, right-click policy, and single queued-click regression coverage. */
public final class GhostCombatProbe {
  private static int checks;

  public static void run(ClientRuntime runtime, Object game, Object player, Object world)
      throws Exception {
    checks = 0;
    String[] ids = {"reach", "hitboxexpand", "triggerbot", "autoclicker"};
    Map<String, Boolean> enabled = new LinkedHashMap<>();
    Map<Setting<?>, Object> settingValues = new LinkedHashMap<>();
    for (String id : ids) {
      enabled.put(id, runtime.modules().require(id).state() == ModuleState.ENABLED);
      for (Setting<?> setting : runtime.modules().require(id).module().settings().values())
        settingValues.put(setting, setting.get());
      runtime.modules().setEnabled(id, false);
    }
    EventOwner owner =
        new EventOwner(
            "ghost-combat-probe",
            failure -> {
              throw new AssertionError(failure);
            });
    owner.activate();
    List<AutoCloseable> claims = new ArrayList<>();
    ControlRequest<Rotation> aim = runtime.controls().scope(owner, claims::add).rotation(10000);
    Field yaw = field(player.getClass(), "rotationYaw", "field_70177_z");
    Field pitch = field(player.getClass(), "rotationPitch", "field_70125_A");
    float oldYaw = yaw.getFloat(player), oldPitch = pitch.getFloat(player);
    Field mouse = field(game.getClass(), "objectMouseOver", "field_71476_x");
    Object oldMouse = mouse.get(game);
    Field focus = field(game.getClass(), "inGameHasFocus", "field_71415_G");
    boolean oldFocus = focus.getBoolean(game);
    Object settings = field(game.getClass(), "gameSettings", "field_71474_y").get(game);
    Object attackKey = field(settings.getClass(), "keyBindAttack", "field_74312_F").get(settings);
    Field pressTime = field(attackKey.getClass(), "pressTime", "field_151474_i");
    int oldPresses = pressTime.getInt(attackKey);
    Field delay = field(game.getClass(), "rightClickDelayTimer", "field_71467_ac");
    int oldDelay = delay.getInt(game);
    Object target =
        Class.forName("net.minecraft.entity.monster.EntityZombie")
            .getConstructor(Class.forName("net.minecraft.world.World"))
            .newInstance(world);
    int targetId = -9876129;
    double x = field(player.getClass(), "posX", "field_70165_t").getDouble(player);
    double y = field(player.getClass(), "posY", "field_70163_u").getDouble(player);
    double z = field(player.getClass(), "posZ", "field_70161_v").getDouble(player);
    double originalY = y;
    y = 220;
    position(player, x, y, z);
    position(target, x, y, z + 4);
    method(
            world.getClass(),
            new Class<?>[] {int.class, Class.forName("net.minecraft.entity.Entity")},
            "addEntityToWorld",
            "func_73027_a")
        .invoke(world, targetId, target);
    Field headYaw = field(player.getClass(), "rotationYawHead", "field_70759_as");
    Field previousHeadYaw = field(player.getClass(), "prevRotationYawHead", "field_70758_at");
    float savedHeadYaw = headYaw.getFloat(player),
        savedPreviousHeadYaw = previousHeadYaw.getFloat(player);
    Field[] previousPosition = {
      field(player.getClass(), "prevPosX", "field_70169_q"),
      field(player.getClass(), "prevPosY", "field_70167_r"),
      field(player.getClass(), "prevPosZ", "field_70166_s")
    };
    double[] savedPreviousPosition = new double[3], fixturePosition = {x, y, z};
    for (int i = 0; i < 3; i++) savedPreviousPosition[i] = previousPosition[i].getDouble(player);
    Object renderer = field(game.getClass(), "entityRenderer", "field_71460_t").get(game);
    Method pick =
        method(renderer.getClass(), new Class<?>[] {float.class}, "getMouseOver", "func_78473_a");
    try {
      camera(player, 0, 0);
      headYaw.setFloat(player, 0);
      previousHeadYaw.setFloat(player, 0);
      for (int i = 0; i < 3; i++) previousPosition[i].setDouble(player, fixturePosition[i]);
      focus.setBoolean(game, true);
      aim.set(Rotation.server(0, 0));
      forceLook(player);
      motion(player);
      verify(HookBridge.gameControls().isRotationApplied(aim), "fixture sends its walking aim");
      position(target, x, y, z + 4);
      set(runtime, "reach", "min-range", "4");
      set(runtime, "reach", "max-range", "4");
      runtime.modules().setEnabled("reach", true);
      pick.invoke(renderer, 1f);
      if (hit(mouse.get(game)) != target)
        System.out.println(
            "PERIHELION_SMOKE GHOST_SELECTION actual="
                + mouse.get(game)
                + " player="
                + x
                + ","
                + y
                + ","
                + z
                + " view="
                + method(game.getClass(), "getRenderViewEntity", "func_175606_aa").invoke(game));
      verify(hit(mouse.get(game)) == target, "raised native entity cutoff reaches the target");
      set(runtime, "reach", "min-range", "3.1");
      set(runtime, "reach", "max-range", "3.1");
      pick.invoke(renderer, 1f);
      verify(hit(mouse.get(game)) != target, "custom cutoff caps survival and creative selection");
      Object capped = mouse.get(game);
      aim.set(Rotation.server(90, 0));
      Method normal =
          method(
              Class.forName("perihelion.integration.minecraft189.control.ManualRaycast"),
              new Class<?>[] {game.getClass()},
              "normalRightClick");
      verify(
          hit(normal.invoke(null, game)) == target,
          "right-click ray uses submitted rather than pending aim");
      method(game.getClass(), "rightClickMouse", "func_147121_ag").invoke(game);
      verify(
          mouse.get(game) == capped, "right-click restores its temporary normal-range selection");
      int[] rightClicks = {0};
      runtime.clientEvents().subscribe(owner, RightClickEvent.class, event -> rightClicks[0]++);
      perihelion.core.event.Subscription local =
          runtime
              .clientEvents()
              .subscribe(owner, LocalInteractionEvent.class, 10000, false, event -> event.cancel());
      method(game.getClass(), "rightClickMouse", "func_147121_ag").invoke(game);
      verify(rightClicks[0] == 0, "local interaction cancellation precedes the right-click hook");
      local.close();
      runtime.modules().setEnabled("reach", false);
      verify(
          !runtime.clientEvents().post(new RightClickEvent()).normalRangeRay(),
          "right-click normalization detaches when Reach disables");

      position(target, x + .9, y, z + 2.5);
      Object box =
          method(target.getClass(), "getEntityBoundingBox", "func_174813_aQ").invoke(target);
      pick.invoke(renderer, 1f);
      verify(hit(mouse.get(game)) != target, "ordinary selection misses the off-axis box");
      set(runtime, "hitboxexpand", "amount", "1");
      runtime.modules().setEnabled("hitboxexpand", true);
      pick.invoke(renderer, 1f);
      if (hit(mouse.get(game)) != target)
        System.out.println(
            "PERIHELION_SMOKE GHOST_EXPANSION actual="
                + mouse.get(game)
                + " headYaw="
                + headYaw.getFloat(player)
                + " cameraYaw="
                + yaw.getFloat(player)
                + " viewInvisible="
                + method(player.getClass(), "isInvisible", "func_82150_aj").invoke(player)
                + " requested="
                + runtime.clientEvents().post(new MouseOverEvent()).expand()
                + " targetBox="
                + box);
      verify(hit(mouse.get(game)) == target, "native expansion selects the off-axis entity");
      verify(
          method(target.getClass(), "getEntityBoundingBox", "func_174813_aQ").invoke(target) == box,
          "selection expansion leaves real entity bounds intact");
      verify(
          hit(normal.invoke(null, game)) != target,
          "right-click ray excludes expanded selection borders");
      runtime.modules().setEnabled("hitboxexpand", false);
      pick.invoke(renderer, 1f);
      verify(hit(mouse.get(game)) != target, "selection expansion clears on disable");

      position(target, x, y, z + 2);
      pick.invoke(renderer, 1f);
      verify(hit(mouse.get(game)) == target, "trigger fixture has a live entity selection");
      pressTime.setInt(attackKey, 0);
      int[] attacks = {0};
      runtime.clientEvents().subscribe(owner, PlayerAttackEvent.class, event -> attacks[0]++);
      runtime.modules().setEnabled("triggerbot", true);
      runtime.clientEvents().post(ClientTickEvent.START);
      verify(pressTime.getInt(attackKey) == 1, "Trigger queues exactly one vanilla press");
      verify(attacks[0] == 0, "Trigger does not also attack before vanilla consumes its press");
      runtime.clientEvents().post(ClientTickEvent.START);
      verify(
          pressTime.getInt(attackKey) == 1,
          "same-period trigger checks do not duplicate queued presses");
      verify(
          !runtime.controls().key(InputKey.ATTACK).isPresent(),
          "Trigger does not claim held Attack");
      runtime.modules().setEnabled("triggerbot", false);
    } finally {
      for (String id : ids) runtime.modules().setEnabled(id, false);
      for (String id : ids)
        for (Setting<?> setting : runtime.modules().require(id).module().settings().values())
          restore(runtime, id, setting, settingValues.get(setting));
      pressTime.setInt(attackKey, oldPresses);
      delay.setInt(game, oldDelay);
      mouse.set(game, oldMouse);
      focus.setBoolean(game, oldFocus);
      position(player, x, originalY, z);
      camera(player, oldYaw, oldPitch);
      headYaw.setFloat(player, savedHeadYaw);
      previousHeadYaw.setFloat(player, savedPreviousHeadYaw);
      for (int i = 0; i < 3; i++) previousPosition[i].setDouble(player, savedPreviousPosition[i]);
      aim.set(Rotation.server(oldYaw, oldPitch));
      forceLook(player);
      motion(player);
      for (AutoCloseable claim : claims) claim.close();
      owner.close();
      method(world.getClass(), new Class<?>[] {int.class}, "removeEntityFromWorld", "func_73028_b")
          .invoke(world, targetId);
      for (String id : ids) runtime.modules().setEnabled(id, enabled.get(id));
    }
    System.out.println("PERIHELION_SMOKE GHOST_COMBAT checks=" + checks + " PASS");
  }

  private static Object hit(Object selection) throws Exception {
    return selection == null
        ? null
        : field(selection.getClass(), "entityHit", "field_72308_g").get(selection);
  }

  private static <T> void restore(
      ClientRuntime runtime, String module, Setting<T> setting, Object value) {
    @SuppressWarnings("unchecked")
    T restored = (T) value;
    runtime.modules().setSetting(module, setting, restored);
  }

  private static void verify(boolean condition, String label) {
    if (!condition) throw new AssertionError("Ghost combat: " + label);
    checks++;
  }
}
