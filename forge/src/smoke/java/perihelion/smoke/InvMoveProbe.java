package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
import perihelion.core.control.InputKey;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleState;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.event.PlayerControlEvent;

/** Exercises the shipped module with physical keyboard state in an integrated world. */
public final class InvMoveProbe {
  private static int checks;

  public static void run(ClientRuntime r) throws Exception {
    checks = 0;
    Object game =
        method(Class.forName("net.minecraft.client.Minecraft"), "getMinecraft", "func_71410_x")
            .invoke(null);
    Object player = field(game.getClass(), "thePlayer", "field_71439_g").get(game);
    Object inventory = field(player.getClass(), "inventory", "field_71071_by").get(player);
    Field container = field(player.getClass(), "openContainer", "field_71070_bA");
    Object oldContainer = container.get(player);
    Field screen = field(game.getClass(), "currentScreen", "field_71462_r");
    Object oldScreen = screen.get(game);
    Field motion = field(player.getClass(), "motionZ", "field_70179_y");
    double oldMotion = motion.getDouble(player);
    Object input = field(player.getClass(), "movementInput", "field_71158_b").get(player);
    Field forward = field(input.getClass(), "moveForward", "field_78900_b");
    float oldForward = forward.getFloat(input);
    ByteBuffer keyboard = (ByteBuffer) field(Keyboard.class, "keyDownBuffer").get(null);
    int[] codes = {Keyboard.KEY_W, Keyboard.KEY_SPACE, Keyboard.KEY_LSHIFT};
    byte[] oldKeys = new byte[codes.length];
    for (int i = 0; i < codes.length; i++) oldKeys[i] = keyboard.get(codes[i]);
    boolean oldEnabled = r.modules().require("invmove").requestedEnabled();
    Map<Setting<?>, Object> values = new LinkedHashMap<>();
    for (Setting<?> s : r.modules().require("invmove").module().settings().values())
      values.put(s, s.get());
    r.modules().setEnabled("invmove", false);
    int listeners = r.clientEvents().subscriptionCount();
    try {
      for (int code : codes) keyboard.put(code, (byte) 1);
      set(r, "invmove", "inventory", "VANILLA");
      set(r, "invmove", "chest", "VANILLA");
      set(r, "invmove", "allow-movement", "true");
      set(r, "invmove", "allow-jumping", "false");
      set(r, "invmove", "allow-in-guis", "false");
      screen.set(game, Class.forName("net.minecraft.client.gui.GuiIngameMenu").newInstance());
      r.modules().setEnabled("invmove", true);
      pre(r);
      check(!r.controls().key(InputKey.FORWARD).isPresent(), "GUI movement is opt-in");
      set(r, "invmove", "allow-in-guis", "true");
      pre(r);
      check(r.controls().key(InputKey.FORWARD).orElse(false), "GUI polls physical forward input");
      check(
          r.controls().key(InputKey.JUMP).orElse(false),
          "GUI jump is independent of container jump restriction");
      check(r.controls().key(InputKey.SNEAK).orElse(false), "GUI polls physical sneak input");
      keyboard.put(Keyboard.KEY_W, (byte) 0);
      pre(r);
      check(
          !r.controls().key(InputKey.FORWARD).orElse(true),
          "releasing a physical key releases GUI movement");
      keyboard.put(Keyboard.KEY_W, (byte) 1);
      set(r, "invmove", "inventory", "DISABLED");
      set(r, "invmove", "chest", "DISABLED");
      set(r, "invmove", "motion", "0.5");
      forward.setFloat(input, 1);
      motion.setDouble(player, .8);
      pre(r);
      check(
          r.controls().key(InputKey.FORWARD).orElse(false),
          "GUI toggle is independent of container modes");
      check(
          motion.getDouble(player) == .8, "container motion scaling does not affect ordinary GUIs");
      Class<?> playerType = Class.forName("net.minecraft.entity.player.EntityPlayer");
      screen.set(
          game,
          Class.forName("net.minecraft.client.gui.inventory.GuiInventory")
              .getConstructor(playerType)
              .newInstance(player));
      pre(r);
      check(
          !r.controls().key(InputKey.FORWARD).isPresent(),
          "GUI toggle respects disabled inventory mode");
      set(r, "invmove", "inventory", "VANILLA");
      pre(r);
      check(
          r.controls().key(InputKey.FORWARD).orElse(false),
          "survival inventory still polls physical movement");
      check(
          !r.controls().key(InputKey.JUMP).isPresent(),
          "container jump restriction remains effective");
      check(
          !r.controls().key(InputKey.SNEAK).isPresent(), "containers retain donor sneak exclusion");
      check(motion.getDouble(player) == .4, "container motion scaling still applies");
      Class<?> inventoryType = Class.forName("net.minecraft.inventory.IInventory");
      screen.set(
          game,
          Class.forName("net.minecraft.client.gui.inventory.GuiChest")
              .getConstructor(inventoryType, inventoryType)
              .newInstance(inventory, inventory));
      pre(r);
      check(
          !r.controls().key(InputKey.FORWARD).isPresent(),
          "GUI toggle respects disabled chest mode");
      set(r, "invmove", "chest", "VANILLA");
      pre(r);
      check(r.controls().key(InputKey.FORWARD).orElse(false), "chest movement remains available");
      screen.set(
          game,
          Class.forName("net.minecraft.client.gui.inventory.GuiContainerCreative")
              .getConstructor(playerType)
              .newInstance(player));
      pre(r);
      check(
          !r.controls().key(InputKey.FORWARD).isPresent(),
          "creative inventory remains excluded like Yuri");
      screen.set(game, Class.forName("net.minecraft.client.gui.GuiChat").newInstance());
      pre(r);
      check(!r.controls().key(InputKey.FORWARD).isPresent(), "chat remains excluded");
      screen.set(game, null);
      pre(r);
      check(!r.controls().key(InputKey.FORWARD).isPresent(), "closing the GUI clears input claims");
      screen.set(game, Class.forName("net.minecraft.client.gui.GuiIngameMenu").newInstance());
      pre(r);
      set(r, "invmove", "allow-in-guis", "false");
      pre(r);
      check(
          !r.controls().key(InputKey.FORWARD).isPresent(),
          "turning GUI movement off clears input claims");
      set(r, "invmove", "allow-in-guis", "true");
      pre(r);
      check(
          r.modules().require("invmove").state() == ModuleState.ENABLED, "module remains healthy");
      r.modules().setEnabled("invmove", false);
      check(
          !r.controls().key(InputKey.FORWARD).isPresent()
              && !r.controls().key(InputKey.SNEAK).isPresent(),
          "disable retires GUI input claims");
      check(r.clientEvents().subscriptionCount() == listeners, "disable removes owned listeners");
      System.out.println(
          "PERIHELION_SMOKE INV_MOVE checks="
              + checks
              + " PASS displayActive="
              + Display.isActive());
    } finally {
      r.modules().setEnabled("invmove", false);
      for (Map.Entry<Setting<?>, Object> value : values.entrySet())
        restore(r, value.getKey(), value.getValue());
      screen.set(game, oldScreen);
      container.set(player, oldContainer);
      motion.setDouble(player, oldMotion);
      forward.setFloat(input, oldForward);
      for (int i = 0; i < codes.length; i++) keyboard.put(codes[i], oldKeys[i]);
      r.modules().setEnabled("invmove", oldEnabled);
    }
  }

  private static void pre(ClientRuntime r) {
    r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
    r.modules().processFaults();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void restore(ClientRuntime r, Setting setting, Object value) {
    r.modules().setSetting("invmove", setting, value);
  }

  private static void check(boolean condition, String message) {
    checks++;
    if (!condition) throw new AssertionError("Inv Move: " + message);
  }
}
