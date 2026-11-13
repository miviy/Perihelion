package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;
import static perihelion.smoke.RotationPipelineProbe.*;

import java.lang.reflect.Field;
import java.util.*;
import perihelion.core.control.ControlRequest;
import perihelion.core.control.HotbarSelection;
import perihelion.core.control.InputKey;
import perihelion.core.event.EventOwner;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.HookBridge;
import perihelion.integration.minecraft189.event.ClientSendFactEvent;
import perihelion.integration.minecraft189.event.PlayerControlEvent;

/** Same-pass hotbar handoff, stack replacement and normal owned apple-use retirement. */
public final class AutoAppleOwnershipProbe {
  private static int checks;

  public static void run(ClientRuntime runtime) throws Exception {
    checks = 0;
    Object game =
        method(Class.forName("net.minecraft.client.Minecraft"), "getMinecraft", "func_71410_x")
            .invoke(null);
    Object player = field(game.getClass(), "thePlayer", "field_71439_g").get(game);
    Object world = field(game.getClass(), "theWorld", "field_71441_e").get(game);
    Object capabilities = field(player.getClass(), "capabilities", "field_71075_bZ").get(player);
    Field invulnerable = field(capabilities.getClass(), "disableDamage", "field_75102_a");
    boolean oldInvulnerable = invulnerable.getBoolean(capabilities);
    Object inventory = field(player.getClass(), "inventory", "field_71071_by").get(player);
    Object[] items =
        (Object[]) field(inventory.getClass(), "mainInventory", "field_70462_a").get(inventory);
    Object[] savedItems = items.clone();
    Field selected = field(inventory.getClass(), "currentItem", "field_70461_c");
    Field ground = field(player.getClass(), "onGround", "field_70122_E");
    Field screen = field(game.getClass(), "currentScreen", "field_71462_r");
    Field focus = field(game.getClass(), "inGameHasFocus", "field_71415_G");
    Object settings = field(game.getClass(), "gameSettings", "field_71474_y").get(game);
    Object use = field(settings.getClass(), "keyBindUseItem", "field_74313_G").get(settings);
    Field pressed = field(use.getClass(), "pressed", "field_74513_e");
    Object savedScreen = screen.get(game);
    int savedSlot = selected.getInt(inventory);
    boolean savedGround = ground.getBoolean(player),
        savedFocus = focus.getBoolean(game),
        savedPressed = pressed.getBoolean(use);
    float savedHealth =
        ((Number) method(player.getClass(), "getHealth", "func_110143_aJ").invoke(player))
            .floatValue();
    Object savedUse = method(player.getClass(), "getItemInUse", "func_71011_bu").invoke(player);
    int savedUseCount =
        ((Number) method(player.getClass(), "getItemInUseCount", "func_71052_bv").invoke(player))
            .intValue();
    Map<String, Boolean> enabled = new LinkedHashMap<>();
    String[] ids = {
      "autoapple",
      "scaffold",
      "aura",
      "autoclicker",
      "autopot",
      "autoextinguish",
      "nofall",
      "longjump",
      "clutch",
      "blockin",
      "beddefender",
      "freecam",
      "autoeat"
    };
    for (String id : ids) {
      enabled.put(id, runtime.modules().require(id).requestedEnabled());
      runtime.modules().setEnabled(id, false);
    }
    Map<Setting<?>, Object> config = new LinkedHashMap<>();
    for (Setting<?> value : runtime.modules().require("autoapple").module().settings().values())
      config.put(value, value.get());
    EventOwner owner =
        new EventOwner(
            "autoapple-ownership-probe",
            failure -> {
              throw new AssertionError(failure);
            });
    owner.activate();
    List<AutoCloseable> owned = new ArrayList<>();
    ControlRequest<HotbarSelection> higher =
        runtime.controls().scope(owner, owned::add).hotbar(100);
    boolean[] handoff = {false};
    int[] chargeUses = {0};
    Object charge = stack("fire_charge");
    Object chargeItem = method(charge.getClass(), "getItem", "func_77973_b").invoke(charge);
    runtime
        .clientEvents()
        .subscribe(
            owner,
            PlayerControlEvent.class,
            0,
            false,
            event -> {
              if (event == PlayerControlEvent.BEFORE_INPUT && handoff[0])
                higher.set(HotbarSelection.visible(3));
            });
    runtime
        .clientEvents()
        .subscribe(
            owner,
            ClientSendFactEvent.class,
            event -> {
              if (event.action() != ClientSendFactEvent.Action.USE_ITEM) return;
              try {
                Object used = method(event.getClass(), "stack").invoke(event);
                if (used != null
                    && method(used.getClass(), "getItem", "func_77973_b").invoke(used)
                        == chargeItem) chargeUses[0]++;
              } catch (Exception failure) {
                throw new AssertionError(failure);
              }
            });
    try {
      Arrays.fill(items, null);
      items[2] = stack("golden_apple");
      items[3] = charge;
      selected.setInt(inventory, 0);
      ground.setBoolean(player, true);
      screen.set(game, null);
      focus.setBoolean(game, true);
      pressed.setBoolean(use, false);
      method(player.getClass(), new Class<?>[] {float.class}, "setHealth", "func_70606_j")
          .invoke(player, 10f);
      set(runtime, "autoapple", "delay", "0");
      start(runtime);
      check(
          runtime.controls().key(InputKey.USE_ITEM).orElse(false),
          "apple owns synthetic use before handoff");
      handoff[0] = true;
      input();
      check(
          selected.getInt(inventory) == 3 && !runtime.controls().key(InputKey.USE_ITEM).isPresent(),
          "lower-priority input callback taking hotbar100 retires apple use before actions");
      if ((Boolean) method(use.getClass(), "isKeyDown", "func_151470_d").invoke(use))
        method(
                Class.forName("perihelion.integration.minecraft189.control.GameplayAccess"),
                "perihelion$rightClickMouse")
            .invoke(game);
      check(
          chargeUses[0] == 0,
          "losing apple ownership cannot synthesize a fire-charge item-use attempt");
      pressed.setBoolean(use, true);
      check(
          (Boolean) method(use.getClass(), "isKeyDown", "func_151470_d").invoke(use),
          "ownership retirement leaves physical use input available");
      pressed.setBoolean(use, false);
      handoff[0] = false;
      higher.clear();
      runtime.modules().setEnabled("autoapple", false);
      items[2] = stack("golden_apple");
      start(runtime);
      check(
          runtime.controls().key(InputKey.USE_ITEM).orElse(false),
          "replacement case begins a new apple routine");
      items[2] = stack("golden_apple");
      input();
      check(
          !runtime.controls().key(InputKey.USE_ITEM).isPresent()
              && !runtime.controls().hotbar().isPresent(),
          "same-slot replacement by another apple object retires both owned claims");
      runtime.modules().setEnabled("autoapple", false);
      items[2] = stack("golden_apple");
      start(runtime);
      // Vanilla 1.8.9 canEat also rejects creative/invulnerable players, even for gold apples.
      invulnerable.setBoolean(capabilities, false);
      Object controller = field(game.getClass(), "playerController", "field_71442_b").get(game);
      method(
              controller.getClass(),
              new Class<?>[] {
                Class.forName("net.minecraft.entity.player.EntityPlayer"),
                Class.forName("net.minecraft.world.World"),
                Class.forName("net.minecraft.item.ItemStack")
              },
              "sendUseItem",
              "func_78769_a")
          .invoke(controller, player, world, items[2]);
      check(
          (Boolean) method(player.getClass(), "isUsingItem", "func_71039_bw").invoke(player),
          "uninterrupted apple routine starts real vanilla item use");
      input();
      method(player.getClass(), "clearItemInUse", "func_71041_bz").invoke(player);
      input();
      check(
          !runtime.controls().key(InputKey.USE_ITEM).isPresent()
              && !runtime.controls().hotbar().isPresent(),
          "normal vanilla use-end signal retires apple controls");
      runtime.modules().setEnabled("autoapple", false);
      start(runtime);
      runtime.modules().setEnabled("autoapple", false);
      check(
          !runtime.controls().key(InputKey.USE_ITEM).isPresent()
              && !runtime.controls().hotbar().isPresent(),
          "disable immediately retires apple controls");
      check(chargeUses[0] == 0, "all ownership cases leave fire charges untouched");
    } finally {
      invulnerable.setBoolean(capabilities, oldInvulnerable);
      runtime.modules().setEnabled("autoapple", false);
      higher.clear();
      owner.close();
      for (int at = owned.size() - 1; at >= 0; at--) owned.get(at).close();
      System.arraycopy(savedItems, 0, items, 0, items.length);
      selected.setInt(inventory, savedSlot);
      ground.setBoolean(player, savedGround);
      screen.set(game, savedScreen);
      focus.setBoolean(game, savedFocus);
      pressed.setBoolean(use, savedPressed);
      method(player.getClass(), new Class<?>[] {float.class}, "setHealth", "func_70606_j")
          .invoke(player, savedHealth);
      method(player.getClass(), "clearItemInUse", "func_71041_bz").invoke(player);
      if (savedUse != null)
        method(
                player.getClass(),
                new Class<?>[] {Class.forName("net.minecraft.item.ItemStack"), int.class},
                "setItemInUse",
                "func_71008_a")
            .invoke(player, savedUse, savedUseCount);
      for (Map.Entry<Setting<?>, Object> value : config.entrySet())
        restore(runtime, value.getKey(), value.getValue());
      HookBridge.gameControls().apply();
      for (String id : ids) runtime.modules().setEnabled(id, enabled.get(id));
    }
    System.out.println("PERIHELION_SMOKE AUTOAPPLE_OWNERSHIP checks=" + checks + " PASS");
  }

  private static void start(ClientRuntime runtime) {
    runtime.modules().setEnabled("autoapple", true);
    for (int count = 0; count < 10; count++) input();
  }

  private static void input() {
    HookBridge.gameControls().beforeInput();
  }

  private static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
    checks++;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void restore(ClientRuntime runtime, Setting setting, Object value) {
    runtime.modules().setSetting("autoapple", setting, value);
  }
}
