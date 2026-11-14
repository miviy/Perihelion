package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;

import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.render.RenderState;
import perihelion.integration.minecraft189.render.RenderStyle;

/** Real-editor appearance, scaled interaction, blur ownership and screenshot checkpoints. */
public final class HudStyleProbe {
  private static int checks;

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static void checkpoint(ClientRuntime runtime, Object game) throws Exception {
    Setting visuals = runtime.modules().require("clickgui").module().settings().get("visuals");
    Object saved = visuals.get();
    boolean enabled = runtime.modules().require("hud").requestedEnabled();
    Field current = field(game.getClass(), "currentScreen", "field_71462_r");
    Object previous = current.get(game);
    try {
      runtime.modules().setSetting("clickgui", visuals, visuals.parse("NEW"));
      runtime.modules().setEnabled("hud", true);
      runtime.commands().execute("hud edit", line -> {});
      Object editor = current.get(game);
      Object preview = field(editor.getClass(), "preview").get(editor);
      method(preview.getClass(), new Class<?>[] {boolean.class}, "update").invoke(preview, true);
      run(runtime, game, editor, preview);
    } finally {
      method(
              game.getClass(),
              new Class<?>[] {Class.forName("net.minecraft.client.gui.GuiScreen")},
              "displayGuiScreen",
              "func_147108_a")
          .invoke(game, previous);
      runtime.modules().setSetting("clickgui", visuals, saved);
      runtime.modules().setEnabled("hud", enabled);
    }
  }

  public static void run(ClientRuntime runtime, Object game, Object editor, Object preview)
      throws Exception {
    checks = 0;
    Map<Setting<?>, Object> saved = new LinkedHashMap<>();
    for (Setting<?> setting : runtime.modules().require("hud").module().settings().values())
      saved.put(setting, setting.get());
    Map<String, Boolean> enabled = new LinkedHashMap<>();
    Object hud = runtime.modules().require("hud").module();
    Class<?> elements = Class.forName("perihelion.integration.minecraft189.hud.HudModule$Element");
    Object modules = Enum.valueOf((Class) elements, "MODULES");
    try {
      for (String id :
          new String[] {
            "sprint",
            "safewalk",
            "waterwalk",
            "nofov",
            "nobob",
            "fastplace",
            "fullbright",
            "nohurtcam",
            "antiblind",
            "esp",
            "nametags",
            "targetesp"
          }) {
        enabled.put(id, runtime.modules().require(id).requestedEnabled());
        runtime.modules().setEnabled(id, true);
      }
      set(runtime, "module-spacing", 0);
      set(runtime, "modules-scale", 1.0);
      method(preview.getClass(), new Class<?>[] {boolean.class}, "update").invoke(preview, true);
      method(preview.getClass(), "updateModules").invoke(preview);
      frame(game, editor);
      ProductionHudProbe.capture("hud-clean-checkpoint.png");
      worldPhoto(game, editor, "hud-refined-in-world.png");
      Object box = box(preview, modules);
      int originalWidth = field(box.getClass(), "w").getInt(box);
      int originalHeight = field(box.getClass(), "h").getInt(box);
      set(runtime, "modules-scale", 1.5);
      frame(game, editor);
      System.out.println("[PERIHELION_SMOKE] HUD_SIZE base=" + originalWidth + "x" + originalHeight
          + " scaled=" + field(box.getClass(), "w").getInt(box) + "x" + field(box.getClass(), "h").getInt(box)
          + " scale=" + field(box.getClass(), "scale").getFloat(box)
          + " row=" + method(preview.getClass(), "moduleRowHeight").invoke(preview));
      check(
          field(box.getClass(), "w").getInt(box) >= originalWidth * 1.4,
          "Size expands actual drag width");
      check(
          field(box.getClass(), "h").getInt(box) >= originalHeight * 1.4,
          "Size expands actual drag height");
      int oldX = field(box.getClass(), "x").getInt(box),
          oldY = field(box.getClass(), "y").getInt(box);
      click(editor, oldX + 3, oldY + 3, 0);
      method(
              editor.getClass(),
              new Class<?>[] {int.class, int.class, float.class},
              "drawScreen",
              "func_73863_a")
          .invoke(editor, oldX - 25, oldY + 25, 0f);
      method(
              editor.getClass(),
              new Class<?>[] {int.class, int.class, int.class},
              "mouseReleased",
              "func_146286_b")
          .invoke(editor, oldX - 25, oldY + 25, 0);
      check(
          ((Number) value(runtime, "modules-x")).doubleValue() < 1,
          "Scaled Array list remains draggable");
      set(runtime, "modules-scale", 1.0);
      set(runtime, "modules-x", 1.0);
      set(runtime, "modules-y", 0.0);
      parsed(runtime, "modules-font", "CLIENT");
      parsed(runtime, "modules-colors", "GRADIENT");
      parsed(runtime, "modules-background", "GRADIENT");
      set(runtime, "modules-padding", 0);
      set(runtime, "modules-radius", 2);
      set(runtime, "modules-panel-shadow", true);
      set(runtime, "modules-shadow-opacity", .15);
      set(runtime, "modules-shadow-spread", 3);
      set(runtime, "modules-opacity", .3);
      set(runtime, "modules-blur", true);
      set(runtime, "modules-blur-strength", 7);
      set(runtime, "modules-y", .78);
      frame(game, editor);
      Object blur = field(preview.getClass(), "blur").get(preview);
      check(
          field(blur.getClass(), "ready").getBoolean(blur),
          "Blur scene capture is available on the native framebuffer");
      int texture = field(blur.getClass(), "texture").getInt(blur);
      check(texture != 0 && GL11.glIsTexture(texture), "Enabled blur owns a live scene texture");
      check(
          !Boolean.TRUE.equals(value(runtime, "target-blur")),
          "Array list blur does not enable other elements");
      ProductionHudProbe.capture("hud-arraylist-gradient-blur.png");
      parsed(runtime, "modules-font", "INTER");
      parsed(runtime, "modules-background", "SOLID");
      parsed(runtime, "modules-colors", "CLIENT");
      frame(game, editor);
      ProductionHudProbe.capture("hud-arraylist-inter.png");
      set(runtime, "modules-y", 0.0);
      frame(game, editor);
      check(
          !runtime.modules().require("hud").module().settings().containsKey("module-row-backgrounds"),
          "Array list always uses a connected text-fitted silhouette");
      parsed(runtime, "target-background", "SOLID");
      set(runtime, "target-opacity", .25);
      set(runtime, "target-y", .72);
      frame(game, editor);
      ProductionHudProbe.capture("hud-target-blur-off.png");
      set(runtime, "target-blur", true);
      set(runtime, "target-blur-strength", 10);
      frame(game, editor);
      ProductionHudProbe.capture("hud-target-blur-on.png");
      set(runtime, "target-blur", false);
      method(editor.getClass(), new Class<?>[] {elements}, "openCustomization")
          .invoke(editor, modules);
      frame(game, editor);
      Object popup = field(editor.getClass(), "customization").get(editor);
      check(popup != null, "Array list opens native customization");
      ProductionHudProbe.capture("hud-customize-text.png");
      int px = (int) field(popup.getClass(), "x").getFloat(popup),
          py = (int) field(popup.getClass(), "y").getFloat(popup);
      int pw = (int) field(popup.getClass(), "w").getFloat(popup);
      click(editor, px + pw / 2, py + 19, 0);
      frame(game, editor);
      check(
          field(popup.getClass(), "section").getInt(popup) == 1,
          "Native Panel tab opens background and blur options");
      ProductionHudProbe.capture("hud-settings-simple.png");
      check(
          !((boolean[]) field(popup.getClass(), "advanced").get(popup))[1],
          "Secondary panel controls start collapsed");
      float footer = field(popup.getClass(), "footer").getFloat(popup);
      float ph = field(popup.getClass(), "h").getFloat(popup);
      click(editor, px + 12, py + (int) (ph - footer) + 7, 0);
      frame(game, editor);
      check(
          ((boolean[]) field(popup.getClass(), "advanced").get(popup))[1],
          "Advanced panel controls remain accessible");
      ProductionHudProbe.capture("hud-customize-panel-blur.png");
      px = (int) field(popup.getClass(), "x").getFloat(popup);
      py = (int) field(popup.getClass(), "y").getFloat(popup);
      ph = field(popup.getClass(), "h").getFloat(popup);
      footer = field(popup.getClass(), "footer").getFloat(popup);
      int footerY = py + (int) (ph - footer) + 7;
      click(editor, px + pw / 2, footerY, 0);
      check(Boolean.TRUE.equals(value(runtime, "modules-blur")),
          "Footer gutter does not reset the appearance");
      check(((boolean[]) field(popup.getClass(), "advanced").get(popup))[1],
          "Footer gutter does not collapse Advanced");
      double savedX = ((Number) value(runtime, "modules-x")).doubleValue();
      click(editor, px + pw - 12, footerY, 0);
      frame(game, editor);
      check(Boolean.FALSE.equals(value(runtime, "modules-blur"))
              && ((Number) value(runtime, "modules-radius")).intValue() == 1
              && ((Number) value(runtime, "module-padding")).intValue() == 2,
          "Reset look restores the compact appearance defaults");
      check(((Number) value(runtime, "modules-x")).doubleValue() == savedX,
          "Reset look preserves the saved HUD position");
      method(editor.getClass(), "closeCustomization").invoke(editor);
      // Every element uses the same saved presentation controls and remains inside the screen.
      int width = field(editor.getClass(), "width", "field_146294_l").getInt(editor);
      int height = field(editor.getClass(), "height", "field_146295_m").getInt(editor);
      for (Object element : elements.getEnumConstants()) {
        String id = element.toString().toLowerCase(Locale.ROOT);
        set(runtime, id, true);
        set(runtime, id + "-scale", 1.25);
        parsed(runtime, id + "-font", "ROBOTO");
        parsed(runtime, id + "-background", "SOLID");
        frame(game, editor);
        Object b = box(preview, element);
        check(
            field(b.getClass(), "x").getInt(b) >= 0
                && field(b.getClass(), "y").getInt(b) >= 0
                && field(b.getClass(), "x").getInt(b) + field(b.getClass(), "w").getInt(b) <= width
                && field(b.getClass(), "y").getInt(b) + field(b.getClass(), "h").getInt(b)
                    <= height,
            "Independent font and size fit: " + id);
      }
      set(runtime, "overlay-include-self", true);
      parsed(runtime, "overlay-source", "NEARBY");
      set(runtime, "players-padding", 6);
      method(preview.getClass(), new Class<?>[] {boolean.class}, "updatePlayers")
          .invoke(preview, true);
      frame(game, editor);
      Object playerElement = Enum.valueOf((Class) elements, "PLAYERS");
      Object playerBox = box(preview, playerElement);
      float playerScale = field(playerBox.getClass(), "scale").getFloat(playerBox);
      int ax = field(playerBox.getClass(), "anchorX").getInt(playerBox),
          ay = field(playerBox.getClass(), "anchorY").getInt(playerBox);
      Object selected =
          method(preview.getClass(), new Class<?>[] {int.class, int.class}, "playerAt")
              .invoke(preview, ax + (int) (20 * playerScale), ay + (int) (22 * playerScale));
      check(selected != null, "Scaled and padded player rows retain their click target");
      check(
          method(preview.getClass(), new Class<?>[] {int.class, int.class}, "playerAt")
                  .invoke(preview, ax + (int) (20 * playerScale), ay + (int) (10 * playerScale))
              == null,
          "Scaled table header cannot select a player");
      Setting<?> visuals = runtime.modules().require("clickgui").module().settings().get("visuals");
      Setting<?> clientStyle =
          runtime.modules().require("clickgui").module().settings().get("client-style");
      Object savedVisuals = visuals.get(), savedStyle = clientStyle.get();
      try {
        gui(runtime, visuals, visuals.parse("OLD"));
        for (String choice : new String[] {"ADJUST", "RAVEN"}) {
          gui(runtime, clientStyle, clientStyle.parse(choice));
          frame(game, editor);
          check(
              GL11.glGetError() == GL11.GL_NO_ERROR,
              "Independent appearance settings render in " + choice);
        }
      } finally {
        gui(runtime, visuals, savedVisuals);
        gui(runtime, clientStyle, savedStyle);
        frame(game, editor);
      }
      check(GL11.glGetError() == GL11.GL_NO_ERROR, "HUD style and blur leave no GL errors");
      blurPixels(game, blur);
      frame(game, editor);
      // Explicit close/recreation is safe and releases shader and texture resources.
      method(blur.getClass(), "close").invoke(blur);
      check(
          field(blur.getClass(), "texture").getInt(blur) == 0
              && field(blur.getClass(), "program").getInt(blur) == 0
              && field(blur.getClass(), "filterProgram").getInt(blur) == 0,
          "HUD blur deletes its scene texture and both shaders");
      for (String id : new String[] {"horizontal", "vertical"}) {
        Object target = field(blur.getClass(), id).get(blur);
        check(field(target.getClass(), "texture").getInt(target) == 0
                && field(target.getClass(), "framebuffer").getInt(target) == 0,
            "HUD blur releases " + id + " filter target");
      }
      System.out.println("[PERIHELION_SMOKE] HUD_STYLE PASS checks=" + checks);
    } finally {
      method(editor.getClass(), "closeCustomization").invoke(editor);
      for (Map.Entry<Setting<?>, Object> item : saved.entrySet())
        set(runtime, item.getKey(), item.getValue());
      for (Map.Entry<String, Boolean> item : enabled.entrySet())
        runtime.modules().setEnabled(item.getKey(), item.getValue());
      frame(game, editor);
    }
  }

  /** Verify actual filtering and mask coverage against high-contrast input on the native GPU. */
  private static void blurPixels(Object game, Object blur) throws Exception {
    int width = field(game.getClass(), "displayWidth", "field_71443_c").getInt(game);
    int height = field(game.getClass(), "displayHeight", "field_71440_d").getInt(game);
    int x = width / 2 - 128, y = height / 2 - 64;
    try (RenderState ignored = new RenderState().open()) {
      RenderStyle.text();
      GL11.glDisable(GL11.GL_TEXTURE_2D);
      GL11.glDisable(GL11.GL_SCISSOR_TEST);
      GL11.glMatrixMode(GL11.GL_PROJECTION);
      GL11.glPushMatrix();
      GL11.glLoadIdentity();
      GL11.glOrtho(0, width, height, 0, -1, 1);
      GL11.glMatrixMode(GL11.GL_MODELVIEW);
      GL11.glPushMatrix();
      GL11.glLoadIdentity();
      try {
        GL11.glBegin(GL11.GL_QUADS);
        for (int row = -2; row < 18; row++)
          for (int col = -2; col < 34; col++) {
            float c = ((row + col) & 1) == 0 ? .05f : .95f;
            GL11.glColor4f(c, c, c, 1);
            float l = x + col * 8, top = y + row * 8;
            GL11.glVertex2f(l, top);
            GL11.glVertex2f(l + 8, top);
            GL11.glVertex2f(l + 8, top + 8);
            GL11.glVertex2f(l, top + 8);
          }
        GL11.glEnd();
        ByteBuffer before = readPixels(x, height - y - 128, 256, 128);
        method(blur.getClass(), new Class<?>[] {game.getClass()}, "capture").invoke(blur, game);
        GL11.glColor4f(1, 1, 1, 1);
        method(blur.getClass(), new Class<?>[] {
          float.class, float.class, float.class, float.class, float.class, float.class,
          int.class, int.class, float.class
        }, "draw").invoke(blur, (float) x, (float) y, 256f, 128f, 12f, 10f, width, height, 1f);
        ByteBuffer after = readPixels(x, height - y - 128, 256, 128);
        double raw = contrast(before), filtered = contrast(after);
        check(filtered < raw * .15, "Gaussian blur removes high-frequency checker contrast");
        int corner = ((127 * 256) * 4);
        check(Math.abs((before.get(corner) & 255) - (after.get(corner) & 255)) <= 1,
            "Rounded blur leaves pixels outside the corner unchanged");
        int partial = 0;
        for (int py = 0; py < 12; py++)
          for (int px = 0; px < 12; px++) {
            int at = ((127 - py) * 256 + px) * 4;
            int changed = Math.abs((before.get(at) & 255) - (after.get(at) & 255));
            if (changed > 3 && changed < 85) partial++;
          }
        check(partial >= 3, "Rounded blur has fractional coverage along the corner");
        check(GL11.glGetError() == GL11.GL_NO_ERROR, "Gaussian blur pixel checks leave no GL errors");
        ProductionHudProbe.capture("hud-blur-coverage.png");
        System.out.println("[PERIHELION_SMOKE] HUD_BLUR_PIXELS raw=" + raw
            + " filtered=" + filtered + " partial=" + partial);
      } finally {
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
      }
    }
  }

  private static ByteBuffer readPixels(int x, int y, int width, int height) {
    ByteBuffer result = BufferUtils.createByteBuffer(width * height * 4);
    GL11.glReadPixels(x, y, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, result);
    return result;
  }

  private static double contrast(ByteBuffer pixels) {
    double sum = 0;
    int count = 0;
    for (int y = 16; y < 112; y++)
      for (int x = 16; x < 240; x++) {
        double d = (pixels.get((y * 256 + x) * 4) & 255) - 127.5;
        sum += d * d;
        count++;
      }
    return sum / count;
  }

  private static Object box(Object preview, Object element) throws Exception {
    return ((Map<?, ?>) field(preview.getClass(), "boxes").get(preview)).get(element);
  }

  private static void worldPhoto(Object game, Object editor, String name) throws Exception {
    Field screen = field(game.getClass(), "currentScreen", "field_71462_r");
    Object saved = screen.get(game);
    try {
      screen.set(game, null);
      frame(game, editor);
      ProductionHudProbe.capture(name);
    } finally {
      screen.set(game, saved);
      frame(game, editor);
    }
  }

  private static Object value(ClientRuntime r, String id) {
    return r.modules().require("hud").module().settings().get(id).get();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void set(ClientRuntime r, Setting setting, Object value) {
    r.modules().setSetting("hud", setting, value);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void gui(ClientRuntime r, Setting setting, Object value) {
    r.modules().setSetting("clickgui", setting, value);
  }

  private static void set(ClientRuntime r, String id, Object value) {
    set(r, r.modules().require("hud").module().settings().get(id), value);
  }

  private static void parsed(ClientRuntime r, String id, String value) {
    Setting<?> setting = r.modules().require("hud").module().settings().get(id);
    set(r, setting, setting.parse(value));
  }

  private static void click(Object editor, int x, int y, int button) throws Exception {
    method(
            editor.getClass(),
            new Class<?>[] {int.class, int.class, int.class},
            "mouseClicked",
            "func_73864_a")
        .invoke(editor, x, y, button);
  }

  private static void frame(Object game, Object editor) throws Exception {
    Object framebuffer = method(game.getClass(), "getFramebuffer", "func_147110_a").invoke(game);
    method(
            framebuffer.getClass(),
            new Class<?>[] {boolean.class},
            "bindFramebuffer",
            "func_147610_a")
        .invoke(framebuffer, true);
    Object renderer = field(game.getClass(), "entityRenderer", "field_71460_t").get(game);
    method(
            renderer.getClass(),
            new Class<?>[] {float.class, long.class},
            "updateCameraAndRender",
            "func_181560_a")
        .invoke(renderer, 0f, System.nanoTime());
  }

  private static void check(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
    checks++;
  }
}
