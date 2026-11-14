package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.*;
import javax.imageio.ImageIO;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.*;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.gui.*;
import perihelion.integration.minecraft189.gui.widget.*;
import perihelion.integration.minecraft189.render.RenderState;

/** Compares the real painted text before and after opening exact numeric editing. */
public final class InlineEditingProbe {
  public static void run(ClientRuntime runtime, Object game) throws Exception {
    ClickGuiModule prefs = (ClickGuiModule) runtime.modules().require("clickgui").module();
    Map<Setting<?>, Object> saved = new LinkedHashMap<>();
    for (Setting<?> setting : prefs.settings().values()) saved.put(setting, setting.get());
    Object original = field(game.getClass(), "currentScreen", "field_71462_r").get(game);
    int cases = 0;
    try {
      for (int style = 0; style < 3; style++) {
        runtime
            .modules()
            .setSetting(
                "clickgui",
                prefs.visuals,
                style == 0 ? ClickGuiModule.Visuals.NEW : ClickGuiModule.Visuals.OLD);
        runtime
            .modules()
            .setSetting(
                "clickgui",
                prefs.clientStyle,
                style == 2 ? ClickGuiModule.ClientStyle.RAVEN : ClickGuiModule.ClientStyle.ADJUST);
        for (ClickGuiModule.Font font :
            new ClickGuiModule.Font[] {ClickGuiModule.Font.MINECRAFT, ClickGuiModule.Font.SANS}) {
          runtime.modules().setSetting("clickgui", prefs.font, font);
          for (double scale : new double[] {1, 1.25}) {
            runtime.modules().setSetting("clickgui", prefs.scale, scale);
            runtime.commands().execute("gui", line -> {});
            Object screen = field(game.getClass(), "currentScreen", "field_71462_r").get(game);
            method(
                    screen.getClass(),
                    new Class<?>[] {int.class, int.class, float.class},
                    "drawScreen",
                    "func_73863_a")
                .invoke(screen, -100, -100, 0f);
            UiCanvas canvas = (UiCanvas) field(screen.getClass(), "canvas").get(screen);
            canvas.clip = null;
            canvas.text = canvas.enabled = canvas.selectedText = 0xffffffff;
            SettingControl control = find(screen);
            control.named("Size");
            field(screen.getClass(), "pressedControl").set(screen, control);
            for (float width : new float[] {150, 34}) {
              control.measure(canvas.font, width, canvas.modern);
              control.place(24, 40, width);
              BufferedImage before = paint(screen, control, canvas, width, false);
              control.press(25, 41, 1);
              Object editor = field(screen.getClass(), "editor").get(screen);
              if (editor == null) throw new AssertionError("Right-click did not open editing");
              BufferedImage after = paint(screen, control, canvas, width, true);
              int ink = 0;
              for (int y = 0; y < before.getHeight(); y++)
                for (int x = 0; x < before.getWidth(); x++) {
                  boolean a = ink(before.getRGB(x, y)), b = ink(after.getRGB(x, y));
                  if (a) ink++;
                  if (a != b)
                    throw new AssertionError(
                        "Text moved: style="
                            + style
                            + " font="
                            + font
                            + " scale="
                            + scale
                            + " width="
                            + width
                            + " pixel="
                            + x
                            + ","
                            + y);
                }
              if (ink == 0) throw new AssertionError("Empty text comparison");
              if (style == 0
                  && font == ClickGuiModule.Font.MINECRAFT
                  && scale == 1
                  && width == 150) {
                File out = new File(System.getProperty("perihelion.smoke.output"));
                ImageIO.write(before, "png", new File(out, "inline-before.png"));
                ImageIO.write(after, "png", new File(out, "inline-editing.png"));
              }
              method(screen.getClass(), new Class<?>[] {char.class, int.class}, "type")
                  .invoke(screen, '\0', Keyboard.KEY_ESCAPE);
              if (field(screen.getClass(), "editor").get(screen) != null)
                throw new AssertionError("Escape did not cancel editing");
              cases++;
            }
            control.place(24, 40, 150);
            control.measure(canvas.font, 150, canvas.modern);
            control.press(25, 41, 1);
            for (char ch : "1.05".toCharArray())
              method(screen.getClass(), new Class<?>[] {char.class, int.class}, "type")
                  .invoke(screen, ch, 0);
            method(screen.getClass(), new Class<?>[] {char.class, int.class}, "type")
                .invoke(screen, '\0', Keyboard.KEY_RETURN);
            if (Math.abs(prefs.scale.get() - 1.05) > .001)
              throw new AssertionError("Numeric input did not commit");
          }
        }
      }
      System.out.println(
          "[PERIHELION_SMOKE] INLINE_EDITING PASS pixelComparisons="
              + cases
              + " typing=true escape=true");
    } finally {
      for (Map.Entry<Setting<?>, Object> e : saved.entrySet())
        restore(runtime, e.getKey(), e.getValue());
      method(
              game.getClass(),
              new Class<?>[] {Class.forName("net.minecraft.client.gui.GuiScreen")},
              "displayGuiScreen",
              "func_147108_a")
          .invoke(game, original);
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void restore(ClientRuntime runtime, Setting setting, Object value) {
    runtime.modules().setSetting("clickgui", setting, value);
  }

  private static SettingControl find(Object screen) throws Exception {
    for (Object group : ((Map<?, ?>) field(screen.getClass(), "views").get(screen)).values())
      for (Object view : (List<?>) group)
        for (Object item : (List<?>) field(view.getClass(), "controls").get(view)) {
          SettingControl control = (SettingControl) item;
          if (control.moduleId().equals("clickgui") && control.setting.id().equals("scale")) {
            field(view.getClass(), "expanded").setBoolean(view, true);
            field(view.getClass(), "matches").setBoolean(view, true);
            return control;
          }
        }
    throw new AssertionError("Missing numeric control");
  }

  private static boolean ink(int rgb) {
    return (rgb >> 16 & 255) > 100 && (rgb >> 8 & 255) > 100 && (rgb & 255) > 100;
  }

  private static BufferedImage paint(
      Object screen, SettingControl control, UiCanvas canvas, float width, boolean editing)
      throws Exception {
    GL11.glDisable(GL11.GL_SCISSOR_TEST);
    GL11.glClearColor(0, 0, 0, 1);
    GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    GL11.glPushMatrix();
    float scale = field(screen.getClass(), "scale").getFloat(screen);
    GL11.glScalef(scale, scale, 1);
    try (RenderState state = new RenderState().open()) {
      float row = editing ? field(screen.getClass(), "editY").getFloat(screen) : Float.NaN;
      float left = field(screen.getClass(), "editX").getFloat(screen);
      control.render(canvas, 24, 40, width, row, left);
      if (editing) {
        Object input = field(screen.getClass(), "editor").get(screen);
        // Reflection keeps this fixture compatible with the packaged SRG text field.
        Object inline = field(screen.getClass(), "inlineEditor").get(screen);
        method(
                inline.getClass(),
                new Class<?>[] {
                  UiCanvas.class,
                  input.getClass(),
                  float.class,
                  float.class,
                  float.class,
                  long.class
                },
                "draw")
            .invoke(
                inline,
                canvas,
                input,
                left,
                field(screen.getClass(), "editTextY").getFloat(screen) - 1,
                field(screen.getClass(), "editWidth").getFloat(screen),
                System.currentTimeMillis() - 600);
      }
    } finally {
      GL11.glPopMatrix();
    }
    float pixels = field(screen.getClass(), "pixels").getFloat(screen);
    int x = (int) Math.floor(24 * pixels), top = (int) Math.floor(40 * pixels);
    int w = (int) Math.ceil(width * pixels), h = (int) Math.ceil(control.height() * pixels);
    ByteBuffer bytes = BufferUtils.createByteBuffer(w * h * 4);
    GL11.glReadPixels(
        x, Display.getHeight() - top - h, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, bytes);
    BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < h; y++)
      for (int xx = 0; xx < w; xx++) {
        int at = (xx + y * w) * 4;
        result.setRGB(
            xx,
            h - y - 1,
            (bytes.get(at) & 255) << 16 | (bytes.get(at + 1) & 255) << 8 | bytes.get(at + 2) & 255);
      }
    return result;
  }
}
