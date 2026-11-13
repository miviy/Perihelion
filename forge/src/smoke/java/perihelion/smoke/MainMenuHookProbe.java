package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;

import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import perihelion.integration.minecraft189.gui.font.GuiFonts;
import perihelion.integration.minecraft189.gui.menu.BlackHoleBackground;
import perihelion.integration.minecraft189.gui.widget.UiCanvas;
import perihelion.integration.minecraft189.render.RenderState;
import perihelion.integration.minecraft189.render.RenderStyle;

/** Actual packaged title navigation, resize, keyboard and GL-state checks. No network access. */
public final class MainMenuHookProbe {
  private static int checks;

  public static void run(Object game) throws Exception {
    checks = 0;
    Class<?> screenType = Class.forName("net.minecraft.client.gui.GuiScreen");
    Class<?> gameType = game.getClass();
    Field current = field(gameType, "currentScreen", "field_71462_r");
    Object menu = current.get(game);
    check(
        menu.getClass().getSimpleName().equals("MainMenuScreen"), "custom title opens at startup");
    int width = number(menu, "width", "field_146294_l");
    int height = number(menu, "height", "field_146295_m");
    Method resize =
        method(
            screenType,
            new Class<?>[] {gameType, int.class, int.class},
            "setWorldAndResolution",
            "func_146280_a");
    Method display =
        method(gameType, new Class<?>[] {screenType}, "displayGuiScreen", "func_147108_a");
    Method readState = RenderHookProbe.class.getDeclaredMethod("readState");
    readState.setAccessible(true);
    try {
      for (int[] size : new int[][] {{320, 240}, {427, 240}, {640, 360}, {854, 480}}) {
        resize.invoke(menu, game, size[0], size[1]);
        List<?> buttons = buttons(menu);
        check(buttons.size() == 8, "eight menu entries, including Client Settings and Accounts");
        Set<Integer> ids = new HashSet<>();
        for (int i = 0; i < buttons.size(); i++) {
          Object button = buttons.get(i);
          check(ids.add(number(button, "id", "field_146127_k")), "unique menu action");
          int x = number(button, "xPosition", "field_146128_h"),
              y = number(button, "yPosition", "field_146129_i");
          int w = number(button, "width", "field_146120_f"),
              h = number(button, "height", "field_146121_g");
          check(
              x >= 0 && y >= 0 && x + w <= size[0] && y + h <= size[1], "entry fits resized menu");
          for (int j = 0; j < i; j++) {
            Object other = buttons.get(j);
            int ox = number(other, "xPosition", "field_146128_h"),
                oy = number(other, "yPosition", "field_146129_i");
            check(
                x + w <= ox
                    || x >= ox + number(other, "width", "field_146120_f")
                    || y + h <= oy
                    || y >= oy + number(other, "height", "field_146121_g"),
                "entries never overlap");
          }
        }
        Object before = readState.invoke(null);
        draw(menu, -1, -1);
        check(
            before.equals(readState.invoke(null)), "title restores GL and Minecraft cached state");
        check(GL11.glGetError() == GL11.GL_NO_ERROR, "title draws without GL errors");
      }
      resize.invoke(menu, game, width, height);
      logoAlignment(game, menu, width, height, readState);
      buttonTypography(game, menu, width, height, readState);
      animation(game, menu, width, height, readState);
      pointerMotion(game, menu, width, height);
      type(menu, Keyboard.KEY_ESCAPE);
      check(current.get(game) == menu, "Escape keeps the main menu open");
      type(menu, Keyboard.KEY_TAB);
      check(field(menu.getClass(), "selected").getInt(menu) == 0, "Tab focuses first action");
      type(menu, Keyboard.KEY_RETURN);
      check(
          current.get(game).getClass().getSimpleName().equals("GuiSelectWorld"),
          "Enter opens focused action");
      clickId(current.get(game), 0);
      check(current.get(game) == menu, "world selector returns to menu");

      int[] actions = {2, 0, 5, 6, 0x5041, 0x5042};
      int[] backs = {0, 200, 6, 6, -1, -1};
      String[] screens = {
        "GuiMultiplayer",
        "GuiOptions",
        "GuiLanguage",
        "GuiModList",
        "AccountScreen",
        "ClientSettingsScreen"
      };
      for (int i = 0; i < actions.length; i++) {
        clickId(menu, actions[i]);
        Object child = current.get(game);
        check(child.getClass().getSimpleName().equals(screens[i]), "menu opens " + screens[i]);
        check(
            number(field(menu.getClass(), "background").get(menu), "program") == 0,
            "leaving the title releases its shader program");
        Object wordmark = field(menu.getClass(), "wordmark").get(menu);
        check(
            field(wordmark.getClass(), "texture").get(wordmark) == null,
            "leaving the title releases the custom wordmark texture");
        Object fontOwner = field(menu.getClass(), "fonts").get(menu);
        check(
            field(fontOwner.getClass(), "menuFace").get(fontOwner) == null,
            "leaving the title releases the menu-label atlas");
        if (backs[i] < 0) type(child, Keyboard.KEY_ESCAPE);
        else clickId(child, backs[i]);
        check(current.get(game) == menu, screens[i] + " returns to same menu");
        draw(menu, -1, -1); // Rebuild closed atlases when returning from a child screen.
      }
      Object first = buttons(menu).get(0);
      draw(
          menu,
          number(first, "xPosition", "field_146128_h") + 2,
          number(first, "yPosition", "field_146129_i") + 2);
      check(
          field(first.getClass(), "hovered", "field_146123_n").getBoolean(first),
          "custom button hover uses hit area");
      check(
          field(first.getClass(), "hoverAmount").getFloat(first) > 0,
          "button hover transitions toward its active style");
      Object motion = field(menu.getClass(), "motion").get(menu);
      Field previous = field(motion.getClass(), "previous");
      for (int frame = 0; frame < 28; frame++) {
        previous.setLong(motion, System.nanoTime() - 16_666_667L);
        draw(
            menu,
            number(first, "xPosition", "field_146128_h") + 2,
            number(first, "yPosition", "field_146129_i") + 2);
      }
      HudChatProbe.captureWorld(game, "menu-button-hover.png");
      type(menu, Keyboard.KEY_UP);
      check(field(menu.getClass(), "selected").getInt(menu) == 7, "Up wraps keyboard focus");
      System.out.println("[PERIHELION_SMOKE] MAIN_MENU checks=" + checks + " PASS");
    } finally {
      resize.invoke(menu, game, width, height);
      display.invoke(game, menu);
      draw(menu, -1, -1);
    }
  }

  private static void buttonTypography(
      Object game, Object menu, int width, int height, Method readState) throws Exception {
    int w = number(game, "displayWidth", "field_71443_c");
    int h = number(game, "displayHeight", "field_71440_d");
    // Overlay projection uses ScaledResolution's unrounded double dimensions. GuiScreen.width
    // is rounded up, so displayWidth / width is not the actual pixel transform at e.g. 4K Auto.
    float sx = overlayScale(game), sy = sx;
    ByteBuffer pixels = BufferUtils.createByteBuffer(w * h * 4);
    Object before = readState.invoke(null);
    GuiFonts.Face actualFace;
    try (RenderState ignored = new RenderState().open()) {
      RenderStyle.text();
      UiCanvas canvas = (UiCanvas) field(menu.getClass(), "canvas").get(menu);
      canvas.rect(0, 0, width, height, 0xff000000);
      GuiFonts fonts = (GuiFonts) field(menu.getClass(), "fonts").get(menu);
      // Match MainMenuScreen.MenuButton: New uses the selected family; Old uses logo lettering.
      actualFace = canvas.modern ? canvas.font : fonts.menu();
      for (Object button : buttons(menu)) {
        String label =
            (String) field(button.getClass(), "displayString", "field_146126_j").get(button);
        actualFace.drawCentered(
            label,
            number(button, "xPosition", "field_146128_h"),
            number(button, "yPosition", "field_146129_i"),
            number(button, "width", "field_146120_f"),
            number(button, "height", "field_146121_g"),
            0xffeeeeee);
      }
      GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
    }
    for (Object button : buttons(menu)) {
      float x = number(button, "xPosition", "field_146128_h"),
          y = number(button, "yPosition", "field_146129_i");
      int bw = number(button, "width", "field_146120_f"),
          bh = number(button, "height", "field_146121_g");
      int left = w, right = -1, top = h, bottom = -1;
      for (int py = Math.max(0, (int) (y * sy)); py < Math.min(h, (int) ((y + bh) * sy)); py++) {
        for (int px = Math.max(0, (int) (x * sx)); px < Math.min(w, (int) ((x + bw) * sx)); px++) {
          int at = ((h - py - 1) * w + px) * 4;
          if ((pixels.get(at) & 255) < 70) continue;
          left = Math.min(left, px);
          right = Math.max(right, px);
          top = Math.min(top, py);
          bottom = Math.max(bottom, py);
        }
      }
      check(right > left && bottom > top, "each native menu label renders visible text");
      String label =
          (String) field(button.getClass(), "displayString", "field_146126_j").get(button);
      float dx = (left + right + 1) / 2f - (x + bw / 2f) * sx;
      float dy = (top + bottom + 1) / 2f - (y + bh / 2f) * sy;
      String diagnostic =
          " label="
              + label
              + " face="
              + actualFace.getClass().getSimpleName()
              + " ink="
              + left
              + ","
              + top
              + ".."
              + right
              + ","
              + bottom
              + " deltaPx="
              + dx
              + ","
              + dy
              + " pixels="
              + sx
              + ","
              + sy;
      check(
          Math.abs(dx) <= 1.5f,
          "button lettering is horizontally centered from actual ink" + diagnostic);
      check(
          Math.abs(dy) <= 1.5f,
          "button lettering including descenders is vertically centered from actual ink"
              + diagnostic);
    }
    check(before.equals(readState.invoke(null)), "menu typography preserves rendering state");
    draw(menu, -1, -1);
  }

  private static void logoAlignment(
      Object game, Object menu, int width, int height, Method readState) throws Exception {
    draw(menu, -1, -1);
    Object before = readState.invoke(null);
    int displayWidth = number(game, "displayWidth", "field_71443_c");
    int displayHeight = number(game, "displayHeight", "field_71440_d");
    Object first = buttons(menu).get(0);
    float center =
        number(first, "xPosition", "field_146128_h")
            + number(first, "width", "field_146120_f") / 2f;
    int buttonTop = number(first, "yPosition", "field_146129_i");
    ByteBuffer pixels = BufferUtils.createByteBuffer(displayWidth * displayHeight * 4);
    try (RenderState ignored = new RenderState().open()) {
      RenderStyle.text();
      UiCanvas canvas = (UiCanvas) field(menu.getClass(), "canvas").get(menu);
      canvas.rect(0, 0, width, height, 0xff000000);
      Object wordmark = field(menu.getClass(), "wordmark").get(menu);
      method(
              wordmark.getClass(),
              new Class<?>[] {float.class, float.class, float.class},
              "drawCentered")
          .invoke(wordmark, center, (float) number(menu, "top") + 36, 2f);
      GL11.glReadPixels(
          0, 0, displayWidth, displayHeight, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
    }
    int left = displayWidth, right = -1, top = displayHeight, bottom = -1;
    for (int y = 0; y < displayHeight; y++) {
      for (int x = 0; x < displayWidth; x++) {
        int at = (y * displayWidth + x) * 4;
        if ((pixels.get(at) & 255) < 70) continue;
        left = Math.min(left, x);
        right = Math.max(right, x);
        top = Math.min(top, displayHeight - 1 - y);
        bottom = Math.max(bottom, displayHeight - 1 - y);
      }
    }
    float scale = overlayScale(game);
    check(right > left && bottom > top, "custom letterforms and emblem render visible ink");
    check(
        Math.abs((left + right + 1) / 2f - center * scale) <= 1.5f,
        "actual logo ink is centered over the native button group");
    check(
        left >= (center - 88) * scale && right <= (center + 88) * scale,
        "logo fits within the button group's horizontal bounds");
    check(
        top >= 0 && bottom < (buttonTop - 10) * scale,
        "logo has a clear gap above the controls without clipping the screen");
    check(before.equals(readState.invoke(null)), "animated logo preserves rendering state");
    draw(menu, -1, -1);
  }

  private static float overlayScale(Object game) throws Exception {
    Class<?> resolution = Class.forName("net.minecraft.client.gui.ScaledResolution");
    Object scaled = resolution.getConstructor(game.getClass()).newInstance(game);
    return ((Number) method(resolution, "getScaleFactor", "func_78325_e").invoke(scaled))
        .floatValue();
  }

  private static void animation(Object game, Object menu, int width, int height, Method readState)
      throws Exception {
    BlackHoleBackground backdrop =
        (BlackHoleBackground) field(menu.getClass(), "background").get(menu);
    UiCanvas canvas = (UiCanvas) field(menu.getClass(), "canvas").get(menu);
    Object before = readState.invoke(null);
    int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    ByteBuffer first, second;
    try (RenderState ignored = new RenderState().open()) {
      RenderStyle.text();
      backdrop.render(canvas, width, height, 3);
      check(number(backdrop, "program") != 0, "packaged black hole shader compiles and links");
      first = pixels(game);
      backdrop.render(canvas, width, height, 5);
      second = pixels(game);
      check(changed(first, second) > 128, "accretion detail visibly moves between animation times");
      check(skyPoints(game) > 12, "star cores are visible in the open upper sky");
      backdrop.render(canvas, width, height, 5);
      check(
          changed(second, pixels(game)) == 0,
          "the scene is deterministic at a fixed animation time");
      timedCapture(game, menu, backdrop, canvas, width, height, .55f, "cosmos-meteor.png");
      timedCapture(game, menu, backdrop, canvas, width, height, 6.25f, "cosmos-infall.png");
      timedCapture(game, menu, backdrop, canvas, width, height, 8.25f, "cosmos-infall-later.png");
      backdrop.render(canvas, width, height, 5, 1, 1, false);
      check(
          changed(second, pixels(game)) > 128,
          "cursor view changes black-hole depth and lighting at a fixed time");
      check(
          GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) == previousProgram,
          "background restores previous shader binding");
    }
    check(before.equals(readState.invoke(null)), "shader animation preserves rendering state");
    int program = number(backdrop, "program");
    backdrop.close();
    check(!GL20.glIsProgram(program), "closed title releases its GPU program");
    field(backdrop.getClass(), "attempted").setBoolean(backdrop, true);
    try {
      try (RenderState ignored = new RenderState().open()) {
        RenderStyle.text();
        backdrop.render(canvas, width, height, 3);
        first = pixels(game);
        backdrop.render(canvas, width, height, 5);
        second = pixels(game);
        check(changed(first, second) > 128, "shader-free fallback also animates");
        check(GL11.glGetError() == GL11.GL_NO_ERROR, "fallback draws without GL errors");
      }
      check(before.equals(readState.invoke(null)), "fallback preserves rendering state");
    } finally {
      backdrop.close();
    }
    draw(menu, -1, -1);
    check(number(backdrop, "program") != 0, "title recreates shader after closing");
    HudChatProbe.captureWorld(game, "main-menu-refined.png");
  }

  private static void timedCapture(
      Object game,
      Object menu,
      BlackHoleBackground backdrop,
      UiCanvas canvas,
      int width,
      int height,
      float seconds,
      String name)
      throws Exception {
    backdrop.render(canvas, width, height, seconds);
    Object wordmark = field(menu.getClass(), "wordmark").get(menu);
    method(
            wordmark.getClass(),
            new Class<?>[] {float.class, float.class, float.class},
            "drawCentered")
        .invoke(
            wordmark, (float) number(menu, "left") + 88, (float) number(menu, "top") + 36, seconds);
    for (Object button : buttons(menu)) {
      method(
              button.getClass(),
              new Class<?>[] {game.getClass(), int.class, int.class},
              "drawButton",
              "func_146112_a")
          .invoke(button, game, -1, -1);
    }
    ProductionHudProbe.capture(name);
  }

  private static int skyPoints(Object game) throws Exception {
    int width = number(game, "displayWidth", "field_71443_c"),
        height = number(game, "displayHeight", "field_71440_d");
    int rows = height / 5;
    ByteBuffer pixels = BufferUtils.createByteBuffer(width * rows * 4);
    GL11.glReadPixels(0, height - rows, width, rows, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
    int points = 0;
    for (int y = 3; y < rows - 3; y++) {
      for (int x = 3; x < width - 3; x++) {
        int at = (y * width + x) * 4;
        int peak = pixels.get(at + 2) & 255;
        int neighbors = ((pixels.get(at + 2 - 12) & 255) + (pixels.get(at + 2 + 12) & 255)) / 2;
        if (peak - neighbors > 24) points++;
      }
    }
    return points;
  }

  private static ByteBuffer pixels(Object game) throws Exception {
    int width = number(game, "displayWidth", "field_71443_c"),
        height = number(game, "displayHeight", "field_71440_d");
    int rightHalf = width / 2;
    ByteBuffer pixels = BufferUtils.createByteBuffer(rightHalf * height * 4);
    GL11.glReadPixels(
        width - rightHalf, 0, rightHalf, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
    return pixels;
  }

  private static void pointerMotion(Object game, Object menu, int width, int height)
      throws Exception {
    Object motion = field(menu.getClass(), "motion").get(menu);
    Field x = field(motion.getClass(), "x"), y = field(motion.getClass(), "y");
    Field previous = field(motion.getClass(), "previous");
    List<String> bounds = new ArrayList<>();
    for (Object button : buttons(menu)) bounds.add(buttonBounds(button));
    x.setFloat(motion, 0);
    y.setFloat(motion, 0);
    previous.setLong(motion, System.nanoTime() - 16_666_667L);
    draw(menu, width, height);
    check(
        x.getFloat(motion) > 0 && x.getFloat(motion) < .3f,
        "pointer motion eases into its target without jumping");
    for (int i = 0; i < 30; i++) {
      previous.setLong(motion, System.nanoTime() - 16_666_667L);
      draw(menu, width, height);
    }
    check(
        x.getFloat(motion) > .85f
            && x.getFloat(motion) <= 1
            && y.getFloat(motion) > .85f
            && y.getFloat(motion) <= 1,
        "pointer response converges within bounded scene travel");
    HudChatProbe.captureWorld(game, "menu-mouse-right.png");
    int index = 0;
    for (Object button : buttons(menu))
      check(
          bounds.get(index++).equals(buttonBounds(button)),
          "pointer movement leaves control hitboxes fixed");
    float before = x.getFloat(motion);
    previous.setLong(motion, System.nanoTime() - 16_666_667L);
    draw(menu, 0, 0);
    check(
        x.getFloat(motion) < before && x.getFloat(motion) > -1, "pointer reversal remains smooth");
    x.setFloat(motion, 0);
    y.setFloat(motion, 0);
    draw(menu, -1, -1);
  }

  private static String buttonBounds(Object button) throws Exception {
    return number(button, "xPosition", "field_146128_h")
        + ":"
        + number(button, "yPosition", "field_146129_i")
        + ":"
        + number(button, "width", "field_146120_f")
        + ":"
        + number(button, "height", "field_146121_g");
  }

  private static int changed(ByteBuffer first, ByteBuffer second) {
    int changed = 0;
    for (int i = 0; i < first.capacity(); i += 4) {
      int difference = 0;
      for (int channel = 0; channel < 3; channel++)
        difference += Math.abs((first.get(i + channel) & 255) - (second.get(i + channel) & 255));
      if (difference > 8) changed++;
    }
    return changed;
  }

  private static List<?> buttons(Object screen) throws Exception {
    return (List<?>) field(screen.getClass(), "buttonList", "field_146292_n").get(screen);
  }

  private static int number(Object object, String... names) throws Exception {
    return field(object.getClass(), names).getInt(object);
  }

  private static void clickId(Object screen, int id) throws Exception {
    for (Object button : buttons(screen))
      if (number(button, "id", "field_146127_k") == id) {
        method(
                screen.getClass(),
                new Class<?>[] {int.class, int.class, int.class},
                "mouseClicked",
                "func_73864_a")
            .invoke(
                screen,
                number(button, "xPosition", "field_146128_h") + 2,
                number(button, "yPosition", "field_146129_i") + 2,
                0);
        return;
      }
    throw new AssertionError("Missing button " + id);
  }

  private static void type(Object screen, int key) throws Exception {
    method(screen.getClass(), new Class<?>[] {char.class, int.class}, "keyTyped", "func_73869_a")
        .invoke(screen, '\0', key);
  }

  private static void draw(Object screen, int x, int y) throws Exception {
    method(
            screen.getClass(),
            new Class<?>[] {int.class, int.class, float.class},
            "drawScreen",
            "func_73863_a")
        .invoke(screen, x, y, 0f);
  }

  private static void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }
}
