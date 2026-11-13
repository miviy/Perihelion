package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;

import java.lang.reflect.*;
import java.util.*;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.HookBridge;

/** Refill's persistent click cooldown, immediate full-hotbar close and native cursor guard. */
public final class RefillParityProbe {
  private static int checks;

  static void run(ClientRuntime runtime) throws Exception {
    checks = 0;
    Object game =
        method(Class.forName("net.minecraft.client.Minecraft"), "getMinecraft", "func_71410_x")
            .invoke(null);
    Object player = field(game.getClass(), "thePlayer", "field_71439_g").get(game);
    Object inventory = field(player.getClass(), "inventory", "field_71071_by").get(player);
    Object[] items =
        (Object[]) field(inventory.getClass(), "mainInventory", "field_70462_a").get(inventory);
    Object[] savedItems = items.clone();
    Field screen = field(game.getClass(), "currentScreen", "field_71462_r");
    Field focus = field(game.getClass(), "inGameHasFocus", "field_71415_G");
    Object savedScreen = screen.get(game);
    boolean savedFocus = focus.getBoolean(game);
    Class<?> stackType = Class.forName("net.minecraft.item.ItemStack");
    Method cursor =
        method(inventory.getClass(), new Class<?>[] {stackType}, "setItemStack", "func_70437_b");
    Object savedCursor =
        method(inventory.getClass(), "getItemStack", "func_70445_o").invoke(inventory);
    Object controller = field(game.getClass(), "playerController", "field_71442_b").get(game);
    Field gameType = field(controller.getClass(), "currentGameType", "field_78779_k");
    Object savedType = gameType.get(controller);
    Method setType =
        method(
            controller.getClass(),
            new Class<?>[] {savedType.getClass()},
            "setGameType",
            "func_78746_a");
    Object capabilities = field(player.getClass(), "capabilities", "field_71075_bZ").get(player);
    Map<Field, Object> savedCapabilities = new LinkedHashMap<>();
    for (Field f : capabilities.getClass().getDeclaredFields()) {
      if (Modifier.isStatic(f.getModifiers()) || Modifier.isFinal(f.getModifiers())) continue;
      f.setAccessible(true);
      savedCapabilities.put(f, f.get(capabilities));
    }
    Field open = field(player.getClass(), "openContainer", "field_71070_bA");
    Object savedOpen = open.get(player);
    Constructor<?> gui =
        Class.forName("net.minecraft.client.gui.inventory.GuiInventory")
            .getConstructor(Class.forName("net.minecraft.entity.player.EntityPlayer"));
    boolean enabled = runtime.modules().require("refill").requestedEnabled();
    Object module = runtime.modules().require("refill").module();
    Field last = field(module.getClass(), "lastClicked");
    long savedLast = last.getLong(module);
    Map<Setting<?>, Object> settings = new LinkedHashMap<>();
    for (Setting<?> setting : runtime.modules().require("refill").module().settings().values())
      settings.put(setting, setting.get());
    runtime.modules().setEnabled("refill", false);
    try {
      // The isolated world is creative. A real survival inventory screen must not
      // redirect itself to GuiContainerCreative and replace the click container.
      setType.invoke(controller, savedType.getClass().getField("SURVIVAL").get(null));
      open.set(
          player, field(player.getClass(), "inventoryContainer", "field_71069_bz").get(player));
      Arrays.fill(items, null);
      for (int i = 0; i < 8; i++) items[i] = stack("stone");
      Object splash = stack("potion");
      method(stackType, new Class<?>[] {int.class}, "setItemDamage", "func_77964_b")
          .invoke(splash, 16421);
      items[9] = splash;
      cursor.invoke(inventory, new Object[] {null});
      screen.set(game, inventoryScreen(game, gui, player));
      set(runtime, "refill", "auto-close", "true");
      runtime.modules().setEnabled("refill", true);
      // Force a pending deadline without relying on scheduling within a 50ms wall window.
      long pending = System.nanoTime() / 1_000_000L + 60_000;
      last.setLong(module, pending);
      HookBridge.gameControls().beforeInput();
      check(items[8] == null && items[9] == splash, "cooldown prevents another shift-click");
      runtime.modules().setEnabled("refill", false);
      runtime.modules().setEnabled("refill", true);
      check(last.getLong(module) == pending, "disable/re-enable retains the donor click timestamp");
      HookBridge.gameControls().beforeInput();
      check(items[8] == null && items[9] == splash, "toggle cannot bypass the pending click delay");
      items[8] = stack("stone");
      HookBridge.gameControls().beforeInput();
      check(
          screen.get(game) == null,
          "a full hotbar closes even with a pending click deadline: screen="
              + (screen.get(game) == null ? "null" : screen.get(game).getClass().getName())
              + " state="
              + runtime.modules().require("refill").state()
              + " cursor="
              + method(inventory.getClass(), "getItemStack", "func_70445_o").invoke(inventory)
              + " hotbar="
              + java.util.Arrays.stream(items).limit(9).filter(java.util.Objects::nonNull).count()
              + " health="
              + method(player.getClass(), "getHealth", "func_110143_aJ").invoke(player));
      screen.set(game, inventoryScreen(game, gui, player));
      set(runtime, "refill", "auto-close", "false");
      HookBridge.gameControls().beforeInput();
      check(screen.get(game) != null, "Auto Close off keeps the full inventory open");
      set(runtime, "refill", "auto-close", "true");
      cursor.invoke(inventory, stack("stone"));
      HookBridge.gameControls().beforeInput();
      check(
          screen.get(game) != null, "native cursor protection prevents closing with a held stack");
      items[8] = null;
      last.setLong(module, 0);
      HookBridge.gameControls().beforeInput();
      check(
          items[8] == null && items[9] == splash,
          "native cursor protection also blocks refill clicks");
      cursor.invoke(inventory, new Object[] {null});
      HookBridge.gameControls().beforeInput();
      check(
          items[8] != null && items[9] == null,
          "actual shift-click moves a splash potion into the last hotbar slot");
      check(last.getLong(module) > 0, "successful click records its monotonic timestamp");
      HookBridge.gameControls().beforeInput();
      check(screen.get(game) == null, "the next update closes the newly full hotbar");
      items[8] = null;
      items[9] = stack("potion");
      last.setLong(module, 0);
      screen.set(game, inventoryScreen(game, gui, player));
      HookBridge.gameControls().beforeInput();
      check(items[8] == null && items[9] != null, "ordinary drinkable potions are not refilled");
      System.out.println("PERIHELION_SMOKE REFILL_PARITY checks=" + checks + " PASS");
    } finally {
      runtime.modules().setEnabled("refill", false);
      for (Setting<?> setting : settings.keySet()) restore(runtime, setting, settings.get(setting));
      last.setLong(module, savedLast);
      setType.invoke(controller, savedType);
      for (Map.Entry<Field, Object> entry : savedCapabilities.entrySet())
        entry.getKey().set(capabilities, entry.getValue());
      open.set(player, savedOpen);
      System.arraycopy(savedItems, 0, items, 0, items.length);
      cursor.invoke(inventory, savedCursor);
      screen.set(game, savedScreen);
      focus.setBoolean(game, savedFocus);
      HookBridge.gameControls().apply();
      if (enabled) runtime.modules().setEnabled("refill", true);
    }
  }

  private static Object inventoryScreen(Object game, Constructor<?> constructor, Object player)
      throws Exception {
    Object screen = constructor.newInstance(player);
    method(
            screen.getClass(),
            new Class<?>[] {Class.forName("net.minecraft.client.Minecraft"), int.class, int.class},
            "setWorldAndResolution",
            "func_146280_a")
        .invoke(screen, game, 320, 240);
    return screen;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void restore(ClientRuntime runtime, Setting setting, Object value) {
    runtime.modules().setSetting("refill", setting, value);
  }

  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError("Refill parity: " + message);
    checks++;
  }
}
