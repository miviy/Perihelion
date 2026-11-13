package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;

import java.io.File;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/** Native menu controls, using synthetic save metadata and loopback-only server entries. */
public final class MenuFamilyHookProbe {
  private static int checks;
  private static Object main;
  private static Class<?> screenType, buttonType, textType;
  private static final Class<?>[] NONE = new Class<?>[0];

  public static void run(Object game) throws Exception {
    checks = 0;
    screenType = Class.forName("net.minecraft.client.gui.GuiScreen");
    buttonType = Class.forName("net.minecraft.client.gui.GuiButton");
    textType = Class.forName("net.minecraft.client.gui.GuiTextField");
    File directory = (File) field(game.getClass(), "mcDataDir", "field_71412_D").get(game);
    Path isolated = Paths.get(System.getProperty("perihelion.smoke.output")).toRealPath();
    check(
        directory.toPath().toRealPath().equals(isolated),
        "fixtures are confined to isolated smoke directory");
    main = current(game);
    try {
      check(
          !active(
              Class.forName("net.minecraft.client.gui.GuiMultiplayer")
                  .getConstructor(screenType)
                  .newInstance(main)),
          "unopened screens are outside the menu theme scope");
      click(game, main, 1);
      Object worlds = current(game);
      draw(game, worlds, "menu-worlds-empty.png");
      click(game, worlds, 0);
      seedWorlds(game, 12);
      click(game, main, 1);
      worlds = current(game);
      check(active(worlds), "world selector inherits title style");
      Object list = field(worlds.getClass(), "availableWorlds", "field_146638_t").get(worlds);
      check(
          (Integer) call(list, NONE, new String[] {"getSize", "func_148127_b"}) == 12,
          "native world list loads fixture saves");
      selectWorld(list);
      check(enabled(button(worlds, 1)), "world selection enables original Play control");
      identities(worlds);
      draw(game, worlds, "menu-worlds.png");
      call(list, new Class<?>[] {int.class}, new String[] {"scrollBy", "func_148145_f"}, 180);
      check(
          (Integer) call(list, NONE, new String[] {"getAmountScrolled", "func_148148_g"}) > 0,
          "native list scrolling still works");
      draw(game, worlds, "menu-worlds-scrolled.png");
      call(list, new Class<?>[] {int.class}, new String[] {"scrollBy", "func_148145_f"}, -10000);
      click(game, worlds, 6);
      Object rename = current(game);
      check(
          rename.getClass().getSimpleName().equals("GuiRenameWorld"),
          "Rename retains native dialog");
      Object renameField = firstField(rename, textType);
      edit(renameField, "Renamed menu fixture");
      type(rename, '\0', Keyboard.KEY_RIGHT);
      draw(game, rename, "menu-world-rename.png");
      selectedInputFont(game, rename, renameField);
      click(game, rename, 0);
      check(current(game) == worlds, "rename commit returns to original world selector");
      click(game, worlds, 3);
      Object create = current(game);
      check(
          create.getClass().getSimpleName().equals("GuiCreateWorld"),
          "Create retains native world controls");
      draw(game, create, "menu-world-create.png");
      click(game, create, 1);
      check(current(game) == worlds, "create cancel preserves parent world list");
      list = field(worlds.getClass(), "availableWorlds", "field_146638_t").get(worlds);
      selectWorld(list);
      click(game, worlds, 2);
      Object confirmation = current(game);
      for (int i = 0; i < 22; i++)
        call(confirmation, NONE, new String[] {"updateScreen", "func_73876_c"});
      click(game, confirmation, 1);
      check(current(game) == worlds, "delete cancel preserves world and parent");
      int width = number(worlds, "width", "field_146294_l"),
          height = number(worlds, "height", "field_146295_m");
      call(
          worlds,
          new Class<?>[] {game.getClass(), int.class, int.class},
          new String[] {"setWorldAndResolution", "func_146280_a"},
          game,
          320,
          240);
      draw(game, worlds, "menu-worlds-narrow.png");
      for (Object b : buttons(worlds))
        check(
            number(b, "xPosition", "field_146128_h") >= 0
                && number(b, "xPosition", "field_146128_h") + number(b, "width", "field_146120_f")
                    <= 320,
            "world controls remain inside narrow window");
      call(
          worlds,
          new Class<?>[] {game.getClass(), int.class, int.class},
          new String[] {"setWorldAndResolution", "func_146280_a"},
          game,
          width,
          height);
      click(game, worlds, 0);
      servers(game);
      options(game);
      System.out.println("[PERIHELION_SMOKE] MENU_FAMILY checks=" + checks + " PASS");
    } finally {
      display(game, main);
      draw(game, main, null);
    }
  }

  public static void world(Object game) throws Exception {
    check(
        field(game.getClass(), "theWorld", "field_71441_e").get(game) != null,
        "world guard checked in an actual world");
    check(!active(main), "menu presentation is disabled while playing");
    System.out.println("[PERIHELION_SMOKE] MENU_WORLD_SCOPE PASS");
  }

  private static void seedWorlds(Object game, int count) throws Exception {
    Object saves = call(game, NONE, new String[] {"getSaveLoader", "func_71359_d"});
    Class<?> nbtType = Class.forName("net.minecraft.nbt.NBTTagCompound");
    Class<?> infoType = Class.forName("net.minecraft.world.storage.WorldInfo");
    for (int i = 0; i < count; i++) {
      String folder = String.format(java.util.Locale.ROOT, "MenuFixture_%02d", i);
      Object nbt = nbtType.newInstance();
      call(
          nbt,
          new Class<?>[] {String.class, int.class},
          new String[] {"setInteger", "func_74768_a"},
          "version",
          19133);
      call(
          nbt,
          new Class<?>[] {String.class, int.class},
          new String[] {"setInteger", "func_74768_a"},
          "GameType",
          1);
      call(
          nbt,
          new Class<?>[] {String.class, String.class},
          new String[] {"setString", "func_74778_a"},
          "LevelName",
          "Orbit " + String.format(java.util.Locale.ROOT, "%02d", i));
      call(
          nbt,
          new Class<?>[] {String.class, String.class},
          new String[] {"setString", "func_74778_a"},
          "generatorName",
          "flat");
      Object info = infoType.getConstructor(nbtType).newInstance(nbt);
      Object handler =
          call(
              saves,
              new Class<?>[] {String.class, boolean.class},
              new String[] {"getSaveLoader", "func_75804_a"},
              folder,
              false);
      call(
          handler, new Class<?>[] {infoType}, new String[] {"saveWorldInfo", "func_75761_a"}, info);
    }
  }

  private static void selectWorld(Object list) throws Exception {
    call(
        list,
        new Class<?>[] {int.class, boolean.class, int.class, int.class},
        new String[] {"elementClicked", "func_148144_a"},
        0,
        false,
        0,
        0);
  }

  private static void servers(Object game) throws Exception {
    click(game, main, 2);
    Object servers = current(game);
    check(active(servers), "server list inherits title style");
    for (int i = 0; i < 2; i++) {
      click(game, servers, 3);
      Object add = current(game);
      check(
          add.getClass().getSimpleName().equals("GuiScreenAddServer"),
          "Add uses native server editor");
      edit(
          field(add.getClass(), "serverNameField", "field_146309_g").get(add),
          "Local fixture " + i);
      edit(field(add.getClass(), "serverIPField", "field_146308_f").get(add), "127.0.0.1:1");
      type(add, '\0', Keyboard.KEY_RIGHT);
      click(game, add, 0);
      check(current(game) == servers, "Add saves and returns to same server list");
    }
    Object saved = call(servers, NONE, new String[] {"getServerList", "func_146795_p"});
    check(
        (Integer) call(saved, NONE, new String[] {"countServers", "func_78856_c"}) == 2,
        "native server CRUD persists additions");
    call(servers, new Class<?>[] {int.class}, new String[] {"selectServer", "func_146790_a"}, 0);
    identities(servers);
    check(
        enabled(button(servers, 1)) && enabled(button(servers, 7)) && enabled(button(servers, 2)),
        "selection updates original Join Edit Delete controls");
    draw(game, servers, "menu-servers.png");
    click(game, servers, 7);
    Object edit = current(game);
    edit(
        field(edit.getClass(), "serverNameField", "field_146309_g").get(edit),
        "Local fixture updated");
    type(edit, '\0', Keyboard.KEY_RIGHT);
    draw(game, edit, "menu-server-edit.png");
    click(game, edit, 0);
    check(current(game) == servers, "Edit saves and preserves server list parent");
    Object data =
        call(saved, new Class<?>[] {int.class}, new String[] {"getServerData", "func_78850_a"}, 0);
    check(
        field(data.getClass(), "serverName", "field_78847_a")
            .get(data)
            .equals("Local fixture updated"),
        "native edit updates selected server");
    click(game, servers, 4);
    Object direct = current(game);
    check(
        direct.getClass().getSimpleName().equals("GuiScreenServerList"),
        "Direct Connect retains native address editor");
    Object address = firstField(direct, textType);
    edit(address, "127.0.0.1:1");
    call(
        address,
        new Class<?>[] {int.class},
        new String[] {"setCursorPosition", "func_146190_e"},
        0);
    call(address, new Class<?>[] {int.class}, new String[] {"setSelectionPos", "func_146199_i"}, 3);
    check(
        call(address, NONE, new String[] {"getSelectedText", "func_146207_c"}).equals("127"),
        "text selection geometry remains native");
    call(
        address, new Class<?>[] {String.class}, new String[] {"writeText", "func_146191_b"}, "127");
    check(
        call(address, NONE, new String[] {"getText", "func_146179_b"}).equals("127.0.0.1:1"),
        "selected text replacement preserves address");
    draw(game, direct, "menu-direct-connect.png");
    click(game, direct, 1);
    check(current(game) == servers, "Direct Connect cancel returns without connecting");
    call(servers, new Class<?>[] {int.class}, new String[] {"selectServer", "func_146790_a"}, 0);
    click(game, servers, 2);
    Object confirm = current(game);
    for (int i = 0; i < 22; i++) call(confirm, NONE, new String[] {"updateScreen", "func_73876_c"});
    click(game, confirm, 1);
    check(current(game) == servers, "server delete cancel preserves parent");
    click(game, servers, 0);
    check(current(game) == main, "server list returns to title");
  }

  private static void options(Object game) throws Exception {
    Object settings = field(game.getClass(), "gameSettings", "field_71474_y").get(game);
    Field fov = field(settings.getClass(), "fovSetting", "field_74334_X");
    float original = fov.getFloat(settings);
    click(game, main, 0);
    Object options = current(game);
    Object slider = null;
    for (Object b : buttons(options))
      if (b.getClass().getSimpleName().equals("GuiOptionSlider")) {
        slider = b;
        break;
      }
    check(slider != null, "native FOV slider remains present");
    try {
      drag(game, options, slider);
      check(fov.getFloat(settings) != original, "native slider drag still updates game settings");
      draw(game, options, "menu-options.png");
    } finally {
      fov.setFloat(settings, original);
    }
    Object soundButton = null;
    for (Object b : buttons(options))
      if (((String) field(b.getClass(), "displayString", "field_146126_j").get(b))
          .contains("Music")) soundButton = b;
    check(soundButton != null, "sound options entry remains present");
    click(game, options, number(soundButton, "id", "field_146127_k"));
    Object sounds = current(game), master = null;
    for (Object b : buttons(sounds))
      if (((String) field(b.getClass(), "displayString", "field_146126_j").get(b))
          .startsWith("Master")) master = b;
    check(master != null, "native audio slider remains present");
    Class<?> categoryType = Class.forName("net.minecraft.client.audio.SoundCategory");
    Object category = Enum.valueOf((Class) categoryType, "MASTER");
    float volume =
        (Float)
            call(
                settings,
                new Class<?>[] {categoryType},
                new String[] {"getSoundLevel", "func_151438_a"},
                category);
    try {
      drag(game, sounds, master);
      float changed =
          (Float)
              call(
                  settings,
                  new Class<?>[] {categoryType},
                  new String[] {"getSoundLevel", "func_151438_a"},
                  category);
      check(changed != volume, "native audio drag callback is preserved");
      draw(game, sounds, "menu-sounds.png");
    } finally {
      call(
          settings,
          new Class<?>[] {categoryType, float.class},
          new String[] {"setSoundLevel", "func_151439_a"},
          category,
          volume);
    }
    click(game, sounds, 200);
    click(game, options, 200);
    check(current(game) == main, "options hierarchy returns to title");
  }

  /** Rendering and mouse placement share the selected metrics, including scrolled long text. */
  private static void selectedInputFont(Object game, Object screen, Object input) throws Exception {
    Class<?> themeType = Class.forName("perihelion.integration.minecraft189.gui.menu.MenuTheme");
    Object theme = field(themeType, "current").get(null);
    perihelion.core.lifecycle.ClientRuntime runtime =
        (perihelion.core.lifecycle.ClientRuntime) field(themeType, "runtime").get(theme);
    if (!perihelion.integration.minecraft189.gui.GuiTheme.modern(runtime)) return;
    perihelion.integration.minecraft189.gui.widget.UiCanvas canvas =
        (perihelion.integration.minecraft189.gui.widget.UiCanvas)
            field(themeType, "canvas").get(theme);
    String saved = (String) call(input, NONE, new String[] {"getText", "func_146179_b"});
    int savedLimit =
        (Integer) call(input, NONE, new String[] {"getMaxStringLength", "func_146208_g"});
    try {
      call(
          input,
          new Class<?>[] {int.class},
          new String[] {"setMaxStringLength", "func_146203_f"},
          256);
      edit(input, "Wiilll 01234");
      call(
          input,
          new Class<?>[] {int.class},
          new String[] {"setCursorPosition", "func_146190_e"},
          0);
      draw(game, screen, null);
      java.util.Map<?, ?> fields = (java.util.Map<?, ?>) field(themeType, "fields").get(theme);
      check(fields.containsKey(input), "Native menu input renders through the selected font view");
      int left = number(input, "xPosition", "field_146209_f") + 4;
      int middle =
          number(input, "yPosition", "field_146210_g")
              + number(input, "height", "field_146219_i") / 2;
      call(
          input,
          new Class<?>[] {int.class, int.class, int.class},
          new String[] {"mouseClicked", "func_146192_a"},
          left + Math.round(canvas.font.width("Wiil")),
          middle,
          0);
      check(
          (Integer) call(input, NONE, new String[] {"getCursorPosition", "func_146198_h"}) == 4,
          "Native input click uses selected glyph advances");
      edit(
          input,
          "Wide words WWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWW");
      draw(game, screen, null);
      Object view = fields.get(input);
      int first = field(view.getClass(), "first").getInt(view);
      check(first > 0, "Long selected-font input scrolls to its native editing cursor");
      call(
          input,
          new Class<?>[] {int.class, int.class, int.class},
          new String[] {"mouseClicked", "func_146192_a"},
          left,
          middle,
          0);
      check(
          (Integer) call(input, NONE, new String[] {"getCursorPosition", "func_146198_h"}) == first,
          "Clicking a scrolled input keeps the viewport that was drawn");
      draw(game, screen, "menu-selected-font-input.png");
      check(
          field(view.getClass(), "first").getInt(view) == first,
          "Rendering after a left-edge click does not jump the horizontal viewport");
      String longText = (String) call(input, NONE, new String[] {"getText", "func_146179_b"});
      call(
          input,
          new Class<?>[] {int.class},
          new String[] {"setCursorPosition", "func_146190_e"},
          0);
      call(
          input,
          new Class<?>[] {int.class},
          new String[] {"setSelectionPos", "func_146199_i"},
          longText.length());
      draw(game, screen, null);
      check(
          field(view.getClass(), "first").getInt(view) > 0,
          "Extending a selection to the end scrolls its moving endpoint into view");
      call(
          input,
          new Class<?>[] {int.class},
          new String[] {"setCursorPosition", "func_146190_e"},
          longText.length());
      call(input, new Class<?>[] {int.class}, new String[] {"setSelectionPos", "func_146199_i"}, 0);
      draw(game, screen, null);
      check(
          field(view.getClass(), "first").getInt(view) == 0,
          "Extending a selection to the start scrolls left without invalid caret substrings");
      call(
          input,
          new Class<?>[] {int.class, int.class, int.class},
          new String[] {"mouseClicked", "func_146192_a"},
          left + Math.round(canvas.font.width("Wide")),
          middle,
          0);
      check(
          (Integer) call(input, NONE, new String[] {"getCursorPosition", "func_146198_h"}) == 4,
          "Clicking during a long selection uses its displayed viewport");
    } finally {
      call(
          input,
          new Class<?>[] {int.class},
          new String[] {"setMaxStringLength", "func_146203_f"},
          savedLimit);
      edit(input, saved);
    }
  }

  private static void drag(Object game, Object screen, Object button) throws Exception {
    int x = number(button, "xPosition", "field_146128_h"),
        y = number(button, "yPosition", "field_146129_i") + 8;
    int width = number(button, "width", "field_146120_f");
    call(
        screen,
        new Class<?>[] {int.class, int.class, int.class},
        new String[] {"mouseClicked", "func_73864_a"},
        x + width - 8,
        y,
        0);
    call(
        button,
        new Class<?>[] {game.getClass(), int.class, int.class},
        new String[] {"drawButton", "func_146112_a"},
        game,
        x + 8,
        y);
    call(
        screen,
        new Class<?>[] {int.class, int.class, int.class},
        new String[] {"mouseReleased", "func_146286_b"},
        x + 8,
        y,
        0);
  }

  private static void identities(Object screen) throws Exception {
    for (Field f : screen.getClass().getDeclaredFields())
      if (f.getType() == buttonType) {
        f.setAccessible(true);
        if (f.get(screen) != null)
          check(
              buttons(screen).contains(f.get(screen)),
              "native stored button reference is retained");
      }
  }

  private static Object firstField(Object screen, Class<?> type) throws Exception {
    for (Field f : screen.getClass().getDeclaredFields())
      if (f.getType() == type) {
        f.setAccessible(true);
        return f.get(screen);
      }
    throw new AssertionError("Missing native field " + type);
  }

  private static void edit(Object field, String value) throws Exception {
    call(field, new Class<?>[] {String.class}, new String[] {"setText", "func_146180_a"}, value);
  }

  private static void type(Object screen, char c, int key) throws Exception {
    call(
        screen,
        new Class<?>[] {char.class, int.class},
        new String[] {"keyTyped", "func_73869_a"},
        c,
        key);
  }

  private static Object current(Object game) throws Exception {
    return field(game.getClass(), "currentScreen", "field_71462_r").get(game);
  }

  private static void display(Object game, Object screen) throws Exception {
    call(
        game,
        new Class<?>[] {screenType},
        new String[] {"displayGuiScreen", "func_147108_a"},
        screen);
  }

  private static boolean active(Object screen) throws Exception {
    return (Boolean)
        method(
                Class.forName("perihelion.integration.minecraft189.gui.menu.MenuTheme"),
                new Class<?>[] {screenType},
                "active")
            .invoke(null, screen);
  }

  private static boolean enabled(Object button) throws Exception {
    return field(button.getClass(), "enabled", "field_146124_l").getBoolean(button);
  }

  private static int number(Object o, String... names) throws Exception {
    return field(o.getClass(), names).getInt(o);
  }

  private static List<?> buttons(Object screen) throws Exception {
    return (List<?>) field(screen.getClass(), "buttonList", "field_146292_n").get(screen);
  }

  private static Object button(Object screen, int id) throws Exception {
    for (Object b : buttons(screen)) if (number(b, "id", "field_146127_k") == id) return b;
    throw new AssertionError("Missing button " + id);
  }

  private static Object call(Object target, Class<?>[] signature, String[] names, Object... args)
      throws Exception {
    return method(target.getClass(), signature, names).invoke(target, args);
  }

  private static void click(Object game, Object screen, int id) throws Exception {
    Object b = button(screen, id);
    check(enabled(b), "native action enabled: " + id + " on " + screen.getClass().getSimpleName());
    call(
        screen,
        new Class<?>[] {int.class, int.class, int.class},
        new String[] {"mouseClicked", "func_73864_a"},
        number(b, "xPosition", "field_146128_h") + number(b, "width", "field_146120_f") / 2,
        number(b, "yPosition", "field_146129_i") + number(b, "height", "field_146121_g") / 2,
        0);
  }

  private static void draw(Object game, Object screen, String capture) throws Exception {
    Class<?> themeType = Class.forName("perihelion.integration.minecraft189.gui.menu.MenuTheme");
    Object theme = field(themeType, "current").get(null);
    if (theme != null)
      method(themeType, new Class<?>[] {screenType, int.class, int.class}, "beginFrame")
          .invoke(theme, screen, -1, -1);
    call(
        screen,
        new Class<?>[] {int.class, int.class, float.class},
        new String[] {"drawScreen", "func_73863_a"},
        -1,
        -1,
        0f);
    check(GL11.glGetError() == GL11.GL_NO_ERROR, "native menu draws without GL errors");
    if (capture != null) HudChatProbe.captureWorld(game, capture);
  }

  private static void check(boolean condition, String message) {
    checks++;
    if (!condition) throw new AssertionError(message);
  }
}
