package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;

import java.util.List;
import java.util.Map;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.setting.Setting;

/** Real drag-only arrangement, resize recovery, Escape and close-time cleanup. */
public final class HudArrangementProbe {
  private static int checks;

  @SuppressWarnings({"rawtypes", "unchecked"})
  public static void run(ClientRuntime runtime, Object game, Object screen, Object parent)
      throws Exception {
    checks = 0;
    Map<String, Setting<?>> settings = runtime.modules().require("hud").module().settings();
    Setting x = settings.get("target-x"), y = settings.get("target-y");
    Object savedX = x.get(), savedY = y.get();
    int width = field(screen.getClass(), "width", "field_146294_l").getInt(screen),
        height = field(screen.getClass(), "height", "field_146295_m").getInt(screen);
    boolean targetEnabled = runtime.modules().require("targethud").requestedEnabled();
    runtime.modules().setEnabled("targethud", true);
    try {
      draw(screen, -1, -1);
      check(
          field(screen.getClass(), "arrangeOnly").getBoolean(screen),
          "GUI action opens drag-only arrangement");
      check(
          ((List<?>) field(screen.getClass(), "buttonList", "field_146292_n").get(screen))
              .isEmpty(),
          "Arrange view contains no editor buttons");
      check(
          field(screen.getClass(), "toolbar").get(screen) == null,
          "Arrange view neither creates nor reserves an editor toolbar");
      HudChatProbe.captureWorld(game, "hud-arrange-clean.png");
      Object target = target(screen);
      int px = n(target, "x") + 3, py = n(target, "y") + 3;
      click(screen, px, py, 1);
      check(
          field(screen.getClass(), "customization").get(screen) == null,
          "Right-click cannot reopen editor controls during arrangement");
      check(
          !field(screen.getClass(), "elementsMenu").getBoolean(screen),
          "Arrange view never opens the Elements menu");
      click(screen, px, py, 0);
      check(
          field(screen.getClass(), "dragging").get(screen) != null, "HUD pieces remain draggable");
      draw(screen, px + 25, py - 20);
      check(
          x.get().equals(savedX) && y.get().equals(savedY),
          "Arrange preview does not save every frame");
      release(screen, px + 25, py - 20);
      check(
          !x.get().equals(savedX) || !y.get().equals(savedY),
          "Releasing saves the arranged position");
      draw(screen, -1, -1);
      target = target(screen);
      click(screen, n(target, "x") + 3, n(target, "y") + 3, 0);
      Object beforeResizeX = x.get(), beforeResizeY = y.get();
      resize(screen, game, 320, 240);
      draw(screen, -1, -1);
      check(
          field(screen.getClass(), "dragging").get(screen) == null,
          "Resizing cancels an unfinished arrangement");
      check(
          x.get().equals(beforeResizeX) && y.get().equals(beforeResizeY),
          "Resize preserves saved HUD positions");
      check(
          ((List<?>) field(screen.getClass(), "buttonList", "field_146292_n").get(screen))
              .isEmpty(),
          "Resizing does not bring editor controls back");
      resize(screen, game, width, height);
      draw(screen, -1, -1);
      target = target(screen);
      px = n(target, "x") + 3;
      py = n(target, "y") + 3;
      click(screen, px, py, 0);
      draw(screen, px - 15, py - 15);
      Object beforeCloseX = x.get(), beforeCloseY = y.get();
      method(screen.getClass(), new Class<?>[] {char.class, int.class}, "keyTyped", "func_73869_a")
          .invoke(screen, '\0', Keyboard.KEY_ESCAPE);
      check(
          field(game.getClass(), "currentScreen", "field_71462_r").get(game) == parent,
          "Escape returns directly to the module GUI");
      check(
          !x.get().equals(beforeCloseX) || !y.get().equals(beforeCloseY),
          "Closing mid-drag commits its final position");
      check(
          field(screen.getClass(), "preview").get(screen) == null,
          "Closing releases the preview renderer");
      check(GL11.glGetError() == GL11.GL_NO_ERROR, "Arrangement and cleanup leave GL state clean");
      System.out.println("[PERIHELION_SMOKE] HUD_ARRANGEMENT PASS checks=" + checks);
    } finally {
      runtime.modules().setEnabled("targethud", targetEnabled);
      runtime.modules().setSetting("hud", x, savedX);
      runtime.modules().setSetting("hud", y, savedY);
    }
  }

  private static Object target(Object screen) throws Exception {
    Object preview = field(screen.getClass(), "preview").get(screen);
    for (Map.Entry<?, ?> entry :
        ((Map<?, ?>) field(preview.getClass(), "boxes").get(preview)).entrySet())
      if (((Enum<?>) entry.getKey()).name().equals("TARGET")) return entry.getValue();
    throw new AssertionError("Target preview missing");
  }

  private static int n(Object object, String name) throws Exception {
    return field(object.getClass(), name).getInt(object);
  }

  private static void draw(Object screen, int x, int y) throws Exception {
    Object preview = field(screen.getClass(), "preview").get(screen);
    method(preview.getClass(), new Class<?>[] {boolean.class}, "update").invoke(preview, true);
    method(
            screen.getClass(),
            new Class<?>[] {int.class, int.class, float.class},
            "drawScreen",
            "func_73863_a")
        .invoke(screen, x, y, 0f);
  }

  private static void click(Object screen, int x, int y, int button) throws Exception {
    method(
            screen.getClass(),
            new Class<?>[] {int.class, int.class, int.class},
            "mouseClicked",
            "func_73864_a")
        .invoke(screen, x, y, button);
  }

  private static void release(Object screen, int x, int y) throws Exception {
    method(
            screen.getClass(),
            new Class<?>[] {int.class, int.class, int.class},
            "mouseReleased",
            "func_146286_b")
        .invoke(screen, x, y, 0);
  }

  private static void resize(Object screen, Object game, int width, int height) throws Exception {
    method(
            screen.getClass(),
            new Class<?>[] {game.getClass(), int.class, int.class},
            "setWorldAndResolution",
            "func_146280_a")
        .invoke(screen, game, width, height);
  }

  private static void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }
}
