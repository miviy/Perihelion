package perihelion.smoke;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.lwjgl.opengl.Display;
import perihelion.core.context.ConnectionContext;
import perihelion.core.control.ControlRequest;
import perihelion.core.control.HotbarSelection;
import perihelion.core.control.InputKey;
import perihelion.core.control.Rotation;
import perihelion.core.diagnostics.ClientLog;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ClientModule;
import perihelion.core.module.ModuleCategory;
import perihelion.core.module.ModuleContext;
import perihelion.core.module.ModuleInfo;
import perihelion.integration.minecraft189.control.MinecraftControls;
import perihelion.integration.minecraft189.event.PlayerControlEvent;

/**
 * Runs against transformed Minecraft classes in an isolated integrated world, never in the mod JAR.
 */
public final class ControlHookProbe {
  private static int checks;

  private ControlHookProbe() {}

  public static void run() throws Exception {
    Class<?> gameType = Class.forName("net.minecraft.client.Minecraft");
    Object game = method(gameType, "getMinecraft", "func_71410_x").invoke(null);
    Object player = field(gameType, "thePlayer", "field_71439_g").get(game);
    Object inventory = field(player.getClass(), "inventory", "field_71071_by").get(player);
    Object controller = field(gameType, "playerController", "field_71442_b").get(game);
    Object timer = field(gameType, "timer", "field_71428_T").get(game);
    Object settings = field(gameType, "gameSettings", "field_71474_y").get(game);
    Field screen = field(gameType, "currentScreen", "field_71462_r");
    Object initialScreen = screen.get(game);
    screen.set(game, null);
    Object forward = field(settings.getClass(), "keyBindForward", "field_74351_w").get(settings);
    Field slot = field(inventory.getClass(), "currentItem", "field_70461_c");
    Field serverSlot = field(controller.getClass(), "currentPlayerItem", "field_78777_l");
    Field speed = field(timer.getClass(), "timerSpeed", "field_74278_d");
    Field yaw = field(player.getClass(), "rotationYaw", "field_70177_z");
    Field pitch = field(player.getClass(), "rotationPitch", "field_70125_A");
    Field reportedYaw = field(player.getClass(), "lastReportedYaw", "field_175164_bL");
    Field reportedPitch = field(player.getClass(), "lastReportedPitch", "field_175165_bM");
    Field pressed = field(forward.getClass(), "pressed", "field_74513_e");
    Field pressCount = field(forward.getClass(), "pressTime", "field_151474_i");
    Method isDown = method(forward.getClass(), "isKeyDown", "func_151470_d");
    Method walking = method(player.getClass(), "onUpdateWalkingPlayer", "func_175161_p");
    Method living = method(player.getClass(), "onLivingUpdate", "func_70636_d");
    Method isSprinting = method(player.getClass(), "isSprinting", "func_70051_ag");
    Method setSprinting =
        player
            .getClass()
            .getMethod(
                isSprinting.getName().equals("isSprinting") ? "setSprinting" : "func_70031_b",
                boolean.class);
    int initialSlot = slot.getInt(inventory), initialCount = pressCount.getInt(forward);
    float initialSpeed = speed.getFloat(timer),
        initialYaw = yaw.getFloat(player),
        initialPitch = pitch.getFloat(player);
    boolean initialPressed = pressed.getBoolean(forward),
        initialSprint = (Boolean) isSprinting.invoke(player);

    Probe low = new Probe("control_low"), high = new Probe("control_high");
    ClientRuntime runtime =
        new ClientRuntime(
            Paths.get(System.getProperty("perihelion.smoke.output"), "control-fixture.json"),
            ClientLog.NONE,
            r -> {
              r.modules().register(low);
              r.modules().register(high);
            });
    MinecraftControls adapter = new MinecraftControls(runtime);
    Class<?> bridge = Class.forName("perihelion.integration.minecraft189.HookBridge");
    Class<?> targetType = Class.forName("perihelion.integration.minecraft189.HookBridge$Target");
    Field target = field(bridge, "target");
    Object previous = target.get(null);
    AtomicReference<Throwable> hookFailure = new AtomicReference<>();
    Object fixture =
        Proxy.newProxyInstance(
            targetType.getClassLoader(),
            new Class<?>[] {targetType},
            (proxy, called, arguments) -> {
              switch (called.getName()) {
                case "isRunning":
                  return runtime.isRunning();
                case "controls":
                  return runtime.tasks().isClientThread() ? adapter : null;
                case "beforeSend":
                case "beforeReceive":
                  return false;
                // Primitive and text results must not be null: transformed collision and font
                // hooks call these while the fixture is installed.
                case "hasClientListeners":
                  return runtime.clientEvents().hasListeners((Class<?>) arguments[0]);
                case "displayText":
                  return arguments[0];
                case "completeCommand":
                  return java.util.Collections.emptyList();
                case "stop":
                  runtime.stop();
                  return null;
                case "reportHookFailure":
                  hookFailure.set((Throwable) arguments[1]);
                  return null;
                case "toString":
                  return "ControlHookProbeTarget";
                default:
                  return null;
              }
            });
    try {
      runtime.start();
      runtime.updateWorld(
          true, new ConnectionContext(ConnectionContext.Kind.SINGLEPLAYER, ""), true);
      runtime.modules().setEnabled("control_low", true);
      runtime.modules().setEnabled("control_high", true);
      target.set(null, fixture);
      ControlRequest<Float> lowTimer = low.context.controls().timerSpeed(1);
      ControlRequest<Float> highTimer = high.context.controls().timerSpeed(2);
      lowTimer.set(1.25f);
      highTimer.set(1.5f);
      adapter.apply();
      check(speed.getFloat(timer) == 1.5f, "winning timer reaches Minecraft");
      highTimer.clear();
      adapter.apply();
      check(speed.getFloat(timer) == 1.25f, "timer yields to lower owner");

      ControlRequest<Boolean> key = low.context.controls().key(InputKey.FORWARD, 1);
      pressed.setBoolean(forward, false);
      pressCount.setInt(forward, 3);
      key.set(true);
      check(
          (Boolean) isDown.invoke(forward) == Display.isActive(),
          "key hook overrides only focused input");
      check(
          !pressed.getBoolean(forward) && pressCount.getInt(forward) == 3,
          "virtual key leaves physical state and press count intact");
      key.clear();
      pressed.setBoolean(forward, true);
      check((Boolean) isDown.invoke(forward), "yield reads current physical state");
      pressed.setBoolean(forward, false);

      ControlRequest<Boolean> sprint = low.context.controls().sprinting(1);
      setSprinting.invoke(player, false);
      sprint.set(true);
      adapter.apply();
      check((Boolean) isSprinting.invoke(player), "sprint ownership applies actual sprint state");
      sprint.clear();
      adapter.apply();
      check(!(Boolean) isSprinting.invoke(player), "sprint baseline restored");

      ControlRequest<HotbarSelection> visible = low.context.controls().hotbar(1);
      ControlRequest<HotbarSelection> spoof = high.context.controls().hotbar(2);
      int visibleSlot = (initialSlot + 1) % 9, spoofSlot = (initialSlot + 2) % 9;
      visible.set(HotbarSelection.visible(visibleSlot));
      adapter.apply();
      check(
          slot.getInt(inventory) == visibleSlot && serverSlot.getInt(controller) == visibleSlot,
          "visible slot synchronizes vanilla controller");
      spoof.set(HotbarSelection.spoof(spoofSlot));
      adapter.apply();
      check(
          slot.getInt(inventory) == initialSlot && serverSlot.getInt(controller) == spoofSlot,
          "spoof shares arbitration without changing visible slot");
      Method action =
          MinecraftControls.class.getMethod("withHotbar", ControlRequest.class, Consumer.class);
      Consumer<Object> assertSlot =
          stack -> {
            try {
              check(slot.getInt(inventory) == spoofSlot, "spoof action sees its held item");
            } catch (IllegalAccessException failure) {
              throw new AssertionError(failure);
            }
          };
      check((Boolean) action.invoke(adapter, spoof, assertSlot), "winning action runs");
      check(
          slot.getInt(inventory) == initialSlot && serverSlot.getInt(controller) == spoofSlot,
          "action restores local slot and retains server claim");
      check(
          !(Boolean)
              action.invoke(
                  adapter,
                  visible,
                  (Consumer<Object>)
                      stack -> {
                        throw new AssertionError("loser ran");
                      }),
          "losing action is rejected");
      IllegalStateException intended = new IllegalStateException("intentional action failure");
      try {
        action.invoke(
            adapter,
            spoof,
            (Consumer<Object>)
                stack -> {
                  throw intended;
                });
        throw new AssertionError("missing action failure");
      } catch (InvocationTargetException failure) {
        check(failure.getCause() == intended, "action preserves original failure");
      }
      check(slot.getInt(inventory) == initialSlot, "failed action restores slot");
      spoof.clear();
      adapter.apply();
      check(slot.getInt(inventory) == visibleSlot, "spoof yield restores visible winner");
      visible.clear();
      adapter.apply();
      check(
          slot.getInt(inventory) == initialSlot && serverSlot.getInt(controller) == initialSlot,
          "all slot claims yield to vanilla");

      ControlRequest<Rotation> rotation = low.context.controls().rotation(1);
      rotation.set(Rotation.server(73, -24));
      check(
          !adapter.isRotationApplied(rotation), "unsubmitted rotation cannot authorize an action");
      walking.invoke(player);
      check(
          reportedYaw.getFloat(player) == 73 && reportedPitch.getFloat(player) == -24,
          "motion mixin updates vanilla packet rotation cache as a pair");
      check(
          yaw.getFloat(player) == initialYaw && pitch.getFloat(player) == initialPitch,
          "server rotation leaves camera intact");
      check(low.beforeMotion == 1 && low.afterMotion == 1, "owned motion phases dispatched");
      check(adapter.isRotationApplied(rotation), "walking pass records the applied owner");
      check(
          adapter.modelYaw(0, false) == 73 && adapter.modelPitch(0, false) == -24,
          "third-person pose exposes submitted silent rotation");
      check(
          adapter.withRotation(
              rotation,
              () -> {
                try {
                  check(
                      yaw.getFloat(player) == 73 && pitch.getFloat(player) == -24,
                      "local item action sees packet rotation");
                } catch (IllegalAccessException failure) {
                  throw new AssertionError(failure);
                }
              }),
          "applied rotation runs local item action");
      check(
          yaw.getFloat(player) == initialYaw && pitch.getFloat(player) == initialPitch,
          "local item action restores camera");
      try {
        adapter.withRotation(
            rotation,
            () -> {
              throw intended;
            });
        throw new AssertionError("missing rotation action failure");
      } catch (IllegalStateException failure) {
        check(failure == intended, "rotation action preserves failure");
      }
      check(
          yaw.getFloat(player) == initialYaw && pitch.getFloat(player) == initialPitch,
          "failed rotation action restores camera");
      adapter.withRotation(
          rotation,
          () -> {
            try {
              adapter.withRotation(rotation, () -> {});
              throw new AssertionError("nested rotation action ran");
            } catch (IllegalStateException failure) {
              check(failure.getMessage().contains("Nested"), "nested rotation action rejected");
            }
          });
      check(
          yaw.getFloat(player) == initialYaw && pitch.getFloat(player) == initialPitch,
          "nested action rejection restores camera");
      adapter.withRotation(
          rotation,
          () -> {
            try {
              yaw.setFloat(player, 113);
              pitch.setFloat(player, 7);
            } catch (IllegalAccessException failure) {
              throw new AssertionError(failure);
            }
          });
      check(
          yaw.getFloat(player) == 113 && pitch.getFloat(player) == 7,
          "rotation action preserves external camera writes");
      yaw.setFloat(player, initialYaw);
      pitch.setFloat(player, initialPitch);
      adapter.beforeMotion(); // Simulate a vanilla failure before its RETURN hook.
      adapter.beforeInput();
      check(
          adapter.motionYaw(9) == 9 && adapter.motionPitch(8) == 8,
          "next input clears an unfinished walking snapshot");
      check(!adapter.isRotationApplied(rotation), "next input clears action authorization");
      walking.invoke(player);
      rotation.set(Rotation.server(74, -24));
      check(
          !adapter.isRotationApplied(rotation),
          "changing a request invalidates its submitted angle");
      ControlRequest<Rotation> highRotation = high.context.controls().rotation(20);
      highRotation.set(Rotation.server(20, 90));
      walking.invoke(player);
      highRotation.clear();
      check(
          rotation.isWinning() && !adapter.isRotationApplied(rotation),
          "releasing applied winner cannot authorize unsent lower owner");
      check(
          !adapter.withRotation(
              rotation,
              () -> {
                throw new AssertionError("unsent action ran");
              }),
          "unsent lower owner cannot run local action");
      highRotation.close();
      rotation.clear();
      walking.invoke(player);
      check(
          reportedYaw.getFloat(player) == initialYaw
              && reportedPitch.getFloat(player) == initialPitch,
          "next motion restores camera rotation to server");
      check(
          adapter.modelYaw(initialYaw, false) == initialYaw
              && adapter.modelPitch(initialPitch, false) == initialPitch,
          "released pose falls back to vanilla");
      rotation.set(Rotation.view(40, 15));
      int beforeLiving = low.beforeInput;
      living.invoke(player);
      check(low.beforeInput == beforeLiving + 1, "living update collects input requests");
      check(
          pitch.getFloat(player) == 15 && Math.abs(yaw.getFloat(player) % 360 - 40) < 0.001f,
          "view request changes real camera before movement");
      rotation.clear();

      highTimer.set(2f);
      high.context.listenNetwork(
          String.class,
          0,
          false,
          event -> {
            throw new IllegalStateException("intentional owner fault");
          });
      Thread worker = new Thread(() -> runtime.networkEvents().post("fault"));
      worker.start();
      worker.join();
      adapter.apply();
      check(
          speed.getFloat(timer) == 1.25f,
          "network quarantine yields without game writes off thread");
      runtime.tickStart();
      runtime.modules().setEnabled("control_low", false);
      adapter.apply();
      check(speed.getFloat(timer) == initialSpeed, "disable restores timer baseline");
      check(!lowTimer.set(3f), "stale handle cannot revive game override");
      runtime.modules().setEnabled("control_low", true);
      low.context.controls().timerSpeed(1).set(2f);
      ControlRequest<Rotation> worldRotation = low.context.controls().rotation(5);
      worldRotation.set(Rotation.server(12, 13));
      walking.invoke(player);
      check(adapter.isRotationApplied(worldRotation), "fresh world request applies");
      adapter.apply();
      runtime.updateWorld(false, ConnectionContext.DISCONNECTED, true);
      check(!adapter.isRotationApplied(worldRotation), "world change invalidates applied rotation");
      adapter.apply();
      check(speed.getFloat(timer) == initialSpeed, "world generation releases game state");
      runtime.updateWorld(
          true, new ConnectionContext(ConnectionContext.Kind.SINGLEPLAYER, ""), true);
      low.context.controls().timerSpeed(1).set(2f);
      adapter.apply();
      ControlRequest<HotbarSelection> ending = low.context.controls().hotbar(5);
      ending.set(HotbarSelection.spoof(spoofSlot));
      ControlRequest<Rotation> endingRotation = low.context.controls().rotation(5);
      endingRotation.set(Rotation.server(30, 20));
      walking.invoke(player);
      float endingYaw = yaw.getFloat(player), endingPitch = pitch.getFloat(player);
      action.invoke(
          adapter,
          ending,
          (Consumer<Object>) stack -> adapter.withRotation(endingRotation, runtime::stop));
      check(
          yaw.getFloat(player) == endingYaw && pitch.getFloat(player) == endingPitch,
          "shutdown inside item action restores camera");
      check(!adapter.isRotationApplied(endingRotation), "shutdown clears applied rotation");
      check(
          speed.getFloat(timer) == initialSpeed, "direct runtime shutdown restores owned adapter");
      check(
          slot.getInt(inventory) == initialSlot && serverSlot.getInt(controller) == initialSlot,
          "shutdown inside spoof action restores both slots");
      check(runtime.controls().requestCount() == 0, "shutdown leaves no claims");
      if (hookFailure.get() != null)
        throw new AssertionError("transformed control hook failed", hookFailure.get());
      System.out.println(
          "[PERIHELION_SMOKE] PASS sharedControls checks="
              + checks
              + " inputFocused="
              + Display.isActive());
    } finally {
      runtime.stop();
      adapter.close();
      target.set(null, previous);
      slot.setInt(inventory, initialSlot);
      speed.setFloat(timer, initialSpeed);
      yaw.setFloat(player, initialYaw);
      pitch.setFloat(player, initialPitch);
      pressed.setBoolean(forward, initialPressed);
      pressCount.setInt(forward, initialCount);
      setSprinting.invoke(player, initialSprint);
      screen.set(game, initialScreen);
    }
  }

  private static final class Probe extends ClientModule {
    ModuleContext context;
    int beforeInput, beforeMotion, afterMotion;

    Probe(String id) {
      super(new ModuleInfo(id, id, "isolated control fixture", ModuleCategory.MISC, true));
    }

    @Override
    protected void onEnable(ModuleContext scope) {
      context = scope;
      scope.listen(
          PlayerControlEvent.class,
          event -> {
            if (event == PlayerControlEvent.BEFORE_INPUT) beforeInput++;
            if (event == PlayerControlEvent.BEFORE_MOTION) beforeMotion++;
            if (event == PlayerControlEvent.AFTER_MOTION) afterMotion++;
          });
    }
  }

  private static Field field(Class<?> type, String... names) throws Exception {
    for (Class<?> current = type; current != null; current = current.getSuperclass()) {
      for (String name : names)
        try {
          Field found = current.getDeclaredField(name);
          found.setAccessible(true);
          return found;
        } catch (NoSuchFieldException ignored) {
        }
    }
    throw new NoSuchFieldException(type.getName() + " " + java.util.Arrays.toString(names));
  }

  private static Method method(Class<?> type, String... names) throws Exception {
    for (String name : names)
      try {
        Method found = type.getMethod(name);
        found.setAccessible(true);
        return found;
      } catch (NoSuchMethodException ignored) {
      }
    throw new NoSuchMethodException(type.getName() + " " + java.util.Arrays.toString(names));
  }

  private static void check(boolean passed, String label) {
    if (!passed) throw new AssertionError(label);
    checks++;
  }
}
