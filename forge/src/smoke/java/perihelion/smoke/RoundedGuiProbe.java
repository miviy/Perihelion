package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleEntry;
import perihelion.integration.minecraft189.gui.ClickGuiModule;
import perihelion.integration.minecraft189.gui.modern.RoundedClip;
import perihelion.integration.minecraft189.gui.panel.PanelLayout;
import perihelion.integration.minecraft189.gui.widget.UiCanvas;
import perihelion.integration.minecraft189.render.RenderState;
import perihelion.integration.minecraft189.render.RenderStyle;

/** Pixel and shader-lifetime checks on the actual production dropdown's last enabled row. */
public final class RoundedGuiProbe {
  private static final Map<Object, Integer> livePrograms = new IdentityHashMap<>();
  private static int checks;

  private RoundedGuiProbe() {}

  public static void run(ClientRuntime runtime, Object game, Object modern) throws Exception {
    checks = 0;
    ClickGuiModule preferences = (ClickGuiModule) runtime.modules().require("clickgui").module();
    Double savedRadius = preferences.cornerRadius.get();
    Field query = field(modern.getClass(), "query"), opening = field(modern.getClass(), "opening");
    String savedQuery = (String) query.get(modern);
    float savedOpening = opening.getFloat(modern);
    PanelLayout layout = (PanelLayout) method(modern.getClass(), "model").invoke(modern);
    Map<?, ?> groups = (Map<?, ?>) field(modern.getClass(), "views").get(modern);
    Map<?, ?> motions = (Map<?, ?>) field(modern.getClass(), "motions").get(modern);
    Map<?, ?> panelMotions = (Map<?, ?>) field(modern.getClass(), "panels").get(modern);
    Map<Object, Boolean> matches = new IdentityHashMap<>();
    Map<PanelLayout.Panel, float[]> positions = new IdentityHashMap<>();
    Map<Object, float[]> savedPanels = new IdentityHashMap<>();
    Object scaffold = null;
    ModuleEntry entry = null;
    for (Object group : groups.values())
      for (Object view : (List<?>) group) {
        matches.put(view, field(view.getClass(), "matches").getBoolean(view));
        ModuleEntry candidate = (ModuleEntry) field(view.getClass(), "entry").get(view);
        if (candidate.info().id().equals("scaffold")) {
          scaffold = view;
          entry = candidate;
        }
      }
    check(scaffold != null, "Rounded fixture locates the actual Scaffold row");
    Object motion = motions.get(scaffold);
    float[] savedRow = floats(motion, "height", "enabled", "hover", "arrow");
    Field expanded = field(scaffold.getClass(), "expanded");
    boolean savedExpanded = expanded.getBoolean(scaffold);
    boolean savedEnabled = entry.requestedEnabled();
    PanelLayout.Panel panel = layout.find(entry.info().category());
    for (PanelLayout.Panel p : layout.panels()) positions.put(p, new float[] {p.x, p.y, p.scroll});
    for (Object state : panelMotions.values())
      savedPanels.put(state, floats(state, "open", "targetScroll"));
    UiCanvas canvas = (UiCanvas) field(modern.getClass(), "canvas").get(modern);
    RoundedClip clip = (RoundedClip) field(modern.getClass(), "roundedClip").get(modern);
    Method configure = method(modern.getClass(), "configureCanvas");
    Method paint =
        method(
            modern.getClass(),
            new Class<?>[] {PanelLayout.Panel.class, float.class, float.class, boolean.class},
            "drawPanel");
    float scale = field(modern.getClass(), "scale").getFloat(modern);
    float pixels = field(modern.getClass(), "pixels").getFloat(modern);
    int framebufferHeight = field(game.getClass(), "displayHeight", "field_71440_d").getInt(game);
    float row = (Float) method(modern.getClass(), "rowHeight").invoke(modern);
    float totalHeight = panel.headerHeight + row;
    try {
      method(modern.getClass(), new Class<?>[] {String.class}, "setQuery")
          .invoke(modern, "scaffold");
      // Descriptions may mention Scaffold; keep this fixture to its one real production row.
      for (Object view : matches.keySet())
        field(view.getClass(), "matches").setBoolean(view, view == scaffold);
      expanded.setBoolean(scaffold, false);
      setFloats(
          motion, new String[] {"height", "enabled", "hover", "arrow"}, new float[] {0, 1, 0, 0});
      for (Object state : panelMotions.values())
        setFloats(state, new String[] {"open", "targetScroll"}, new float[] {1, 0});
      opening.setFloat(modern, 1);
      panel.scroll = 0;
      panel.x =
          Math.round(
                  (field(modern.getClass(), "viewWidth").getFloat(modern) - panel.width)
                      / 2
                      * pixels)
              / pixels;
      panel.y =
          Math.round(
                  (field(modern.getClass(), "viewHeight").getFloat(modern) - totalHeight)
                      / 2
                      * pixels)
              / pixels;
      for (int radius : new int[] {0, 5, 10}) {
        runtime.modules().setSetting("clickgui", preferences.cornerRadius, (double) radius);
        paintFixture(modern, panel, canvas, scale, configure, paint);
        int center =
            pixel(
                (panel.x + panel.width / 2) * pixels,
                (panel.y + panel.headerHeight + row / 2) * pixels,
                framebufferHeight);
        int left = pixel(panel.x * pixels, (panel.y + totalHeight) * pixels - 1, framebufferHeight);
        int right =
            pixel(
                (panel.x + panel.width) * pixels - 1,
                (panel.y + totalHeight) * pixels - 1,
                framebufferHeight);
        if (GLContext.getCapabilities().OpenGL20) {
          check(
              number(clip, "program") != 0 && GL20.glIsProgram(number(clip, "program")),
              "Rounded content mask compiles on native GL2 hardware");
          check(
              orange(center),
              "Actual last enabled row stays orange in its center at radius "
                  + radius
                  + ": "
                  + rgb(center));
          if (radius == 0)
            check(orange(left) && orange(right), "Zero radius keeps square enabled-row corners");
          else {
            check(
                !orange(left),
                "Left curved corner excludes the enabled fill at radius "
                    + radius
                    + ": "
                    + rgb(left));
            check(
                !orange(right),
                "Right curved corner excludes the enabled fill at radius "
                    + radius
                    + ": "
                    + rgb(right));
          }
        }
        check(
            entry.requestedEnabled() == savedEnabled,
            "Corner fixture never enables Scaffold gameplay");
        check(
            GL11.glGetError() == GL11.GL_NO_ERROR,
            "Rounded production panel leaves GL clean at radius " + radius);
        ProductionHudProbe.capture("gui-corners-" + radius + ".png");
      }
      Object panelMotion = panelMotions.get(panel);
      Field panelOpen = field(panelMotion.getClass(), "open");
      runtime.modules().setSetting("clickgui", preferences.cornerRadius, 10d);
      if (GLContext.getCapabilities().OpenGL20)
        for (int body : new int[] {0, 1, 5, 10}) {
          panelOpen.setFloat(panelMotion, body / row);
          paintFixture(modern, panel, canvas, scale, configure, paint);
          float actualBody =
              (Float)
                  method(modern.getClass(), new Class<?>[] {PanelLayout.Panel.class}, "body")
                      .invoke(modern, panel);
          check(
              Math.abs(actualBody - body) < .001f,
              "Fixture paints the actual partial body " + body);
          float lower = (panel.y + panel.headerHeight + actualBody) * pixels - 1;
          int left = pixel(panel.x * pixels, lower, framebufferHeight);
          int right = pixel((panel.x + panel.width) * pixels - 1, lower, framebufferHeight);
          check(
              !orange(left) && !headerDark(left),
              "Partial body " + body + " masks its left lower header/fill corner: " + rgb(left));
          check(
              !orange(right) && !headerDark(right),
              "Partial body " + body + " masks its right lower header/fill corner: " + rgb(right));
          if (body == 1 || body == 5) {
            // This point lies inside the square header but outside the complete panel's arc.
            float headerTip = (panel.y + panel.headerHeight) * pixels - 1;
            int tipLeft = pixel(panel.x * pixels, headerTip, framebufferHeight);
            int tipRight =
                pixel((panel.x + panel.width) * pixels - 1, headerTip, framebufferHeight);
            check(
                !headerDark(tipLeft) && !headerDark(tipRight),
                "Partial header cannot protrude beyond the outer curve at body " + body);
          }
          check(
              GL11.glGetError() == GL11.GL_NO_ERROR,
              "Partial-collapse mask leaves GL clean at body " + body);
          if (body == 1) ProductionHudProbe.capture("gui-corners-collapse.png");
        }
      panelOpen.setFloat(panelMotion, 1);
      Field disabled = field(clip.getClass(), "disabled");
      boolean savedDisabled = disabled.getBoolean(clip);
      try {
        disabled.setBoolean(clip, true);
        paintFixture(modern, panel, canvas, scale, configure, paint);
        check(canvas.cornerRadius == 0, "Unavailable shader chooses square-corner fallback");
        int center =
            pixel(
                (panel.x + panel.width / 2) * pixels,
                (panel.y + panel.headerHeight + row / 2) * pixels,
                framebufferHeight);
        int left = pixel(panel.x * pixels, (panel.y + totalHeight) * pixels - 1, framebufferHeight);
        int right =
            pixel(
                (panel.x + panel.width) * pixels - 1,
                (panel.y + totalHeight) * pixels - 1,
                framebufferHeight);
        check(
            orange(center) && orange(left) && orange(right),
            "Square fallback preserves the complete enabled row without trimming content");
        check(GL11.glGetError() == GL11.GL_NO_ERROR, "Unavailable-shader fallback leaves GL clean");
        ProductionHudProbe.capture("gui-corners-fallback.png");
      } finally {
        disabled.setBoolean(clip, savedDisabled);
      }
      foreignProgram(clip, panel, pixels, totalHeight, framebufferHeight);
      offscreen4k(game, modern, panel, canvas, configure, paint);
      System.out.println("[PERIHELION_SMOKE] ROUNDED_GUI PASS checks=" + checks + " radii=0,5,10");
    } finally {
      canvas.clip = null;
      clip.end();
      runtime.modules().setSetting("clickgui", preferences.cornerRadius, savedRadius);
      query.set(modern, savedQuery);
      opening.setFloat(modern, savedOpening);
      expanded.setBoolean(scaffold, savedExpanded);
      setFloats(motion, new String[] {"height", "enabled", "hover", "arrow"}, savedRow);
      for (Map.Entry<Object, Boolean> saved : matches.entrySet())
        field(saved.getKey().getClass(), "matches").setBoolean(saved.getKey(), saved.getValue());
      for (Map.Entry<PanelLayout.Panel, float[]> saved : positions.entrySet()) {
        PanelLayout.Panel p = saved.getKey();
        float[] values = saved.getValue();
        p.x = values[0];
        p.y = values[1];
        p.scroll = values[2];
      }
      for (Map.Entry<Object, float[]> saved : savedPanels.entrySet())
        setFloats(saved.getKey(), new String[] {"open", "targetScroll"}, saved.getValue());
      try (RenderState ignored = new RenderState().open()) {
        configure.invoke(modern);
      }
    }
  }

  /** A real 4K attachment avoids macOS's native-window size clamp. No window is resized. */
  private static void offscreen4k(
      Object game,
      Object modern,
      PanelLayout.Panel panel,
      UiCanvas canvas,
      Method configure,
      Method paint)
      throws Exception {
    Class<?> helper = Class.forName("net.minecraft.client.renderer.OpenGlHelper");
    if (!(Boolean) method(helper, "isFramebufferEnabled", "func_148822_b").invoke(null)) {
      System.out.println("[PERIHELION_SMOKE] ROUNDED_GUI_4K SKIP framebuffer unsupported");
      return;
    }
    final int w = 3840, h = 2160, framebuffer = 0x8d40, binding = 0x8ca6;
    final float autoScale = 9, menuScale = 1.2f, pixels = autoScale * menuScale;
    check(
        GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE) >= w,
        "Native context supports a real 4K attachment");
    int oldFramebuffer = GL11.glGetInteger(binding);
    boolean separateRead =
        GLContext.getCapabilities().OpenGL30
            || GLContext.getCapabilities().GL_ARB_framebuffer_object;
    int oldRead =
        separateRead ? GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING) : oldFramebuffer;
    int oldProgram = currentProgram();
    IntBuffer viewport = BufferUtils.createIntBuffer(16);
    GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
    FloatBuffer clear = BufferUtils.createFloatBuffer(16);
    GL11.glGetFloat(GL11.GL_COLOR_CLEAR_VALUE, clear);
    Field gameW = field(game.getClass(), "displayWidth", "field_71443_c"),
        gameH = field(game.getClass(), "displayHeight", "field_71440_d");
    int oldW = gameW.getInt(game), oldH = gameH.getInt(game);
    Field scaleField = field(modern.getClass(), "scale"),
        pixelsField = field(modern.getClass(), "pixels"),
        viewW = field(modern.getClass(), "viewWidth"),
        viewH = field(modern.getClass(), "viewHeight");
    float oldScale = scaleField.getFloat(modern),
        oldPixels = pixelsField.getFloat(modern),
        oldViewW = viewW.getFloat(modern),
        oldViewH = viewH.getFloat(modern);
    float oldX = panel.x, oldY = panel.y;
    RoundedClip clip = (RoundedClip) field(modern.getClass(), "roundedClip").get(modern);
    Method bind =
        method(helper, new Class<?>[] {int.class, int.class}, "glBindFramebuffer", "func_153171_g");
    Object target = null;
    try (RenderState ignored = new RenderState().open()) {
      try {
        Class<?> type = Class.forName("net.minecraft.client.shader.Framebuffer");
        target = type.getConstructor(int.class, int.class, boolean.class).newInstance(w, h, false);
        method(type, new Class<?>[] {boolean.class}, "bindFramebuffer", "func_147610_a")
            .invoke(target, true);
        check(
            field(type, "framebufferWidth", "field_147621_c").getInt(target) == w
                && field(type, "framebufferHeight", "field_147618_d").getInt(target) == h,
            "High-resolution fixture allocates an actual 3840x2160 framebuffer");
        check(
            GL11.glGetInteger(binding)
                == field(type, "framebufferObject", "field_147616_f").getInt(target),
            "High-resolution fixture binds its actual framebuffer attachment");
        gameW.setInt(game, w);
        gameH.setInt(game, h);
        scaleField.setFloat(modern, menuScale);
        pixelsField.setFloat(modern, pixels);
        viewW.setFloat(modern, w / pixels);
        viewH.setFloat(modern, h / pixels);
        panel.x = panel.y = 20;
        GL11.glViewport(0, 0, w, h);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(0, w / (double) autoScale, h / (double) autoScale, 0, -1000, 1000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GL11.glScalef(menuScale, menuScale, 1);
        RenderStyle.text();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glClearColor(.86f, .87f, .85f, 1);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        configure.invoke(modern);
        paint.invoke(modern, panel, -1f, -1f, false);
        Object key = field(canvas.font.getClass(), "key").get(canvas.font);
        int rasterSize = number(key, "size");
        check(
            rasterSize > 64,
            "Real 4K Auto / Menu scale 1.5 renders body glyphs above the old 64px ceiling");
        check(
            canvas.clip == null && currentProgram() == oldProgram,
            "High-resolution panel releases its mask and restores its incoming program");
        float row = (Float) method(modern.getClass(), "rowHeight").invoke(modern);
        float lower = (panel.y + panel.headerHeight + row) * pixels - 1;
        int center =
            pixel(
                (panel.x + panel.width / 2) * pixels,
                (panel.y + panel.headerHeight + row / 2) * pixels,
                h);
        int left = pixel(panel.x * pixels, lower, h);
        int right = pixel((panel.x + panel.width) * pixels - 1, lower, h);
        check(orange(center), "Real 4K panel retains its enabled-row center: " + rgb(center));
        check(
            !orange(left) && !orange(right),
            "Real 4K panel clips both rounded enabled-row corners");
        check(
            GL11.glGetError() == GL11.GL_NO_ERROR,
            "Real 4K panel and font atlas render without GL errors");
        capture(w, h, "gui-corners-4k.png");
        System.out.println(
            "[PERIHELION_SMOKE] ROUNDED_GUI_4K PASS width="
                + w
                + " height="
                + h
                + " fontPixels="
                + rasterSize
                + " autoScale=9 menuSettingEquivalent=1.5 menuTransform=1.2 framebufferOnly=true");
      } finally {
        canvas.clip = null;
        clip.end();
        gameW.setInt(game, oldW);
        gameH.setInt(game, oldH);
        scaleField.setFloat(modern, oldScale);
        pixelsField.setFloat(modern, oldPixels);
        viewW.setFloat(modern, oldViewW);
        viewH.setFloat(modern, oldViewH);
        panel.x = oldX;
        panel.y = oldY;
        try {
          if (target != null)
            method(target.getClass(), "deleteFramebuffer", "func_147608_a").invoke(target);
        } finally {
          bind.invoke(null, framebuffer, oldFramebuffer);
          if (separateRead) bind.invoke(null, GL30.GL_READ_FRAMEBUFFER, oldRead);
          GL11.glViewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
          GL11.glClearColor(clear.get(0), clear.get(1), clear.get(2), clear.get(3));
          if (GLContext.getCapabilities().OpenGL20) GL20.glUseProgram(oldProgram);
        }
      }
    } finally {
      try (RenderState ignored = new RenderState().open()) {
        configure.invoke(modern);
      }
    }
    check(
        gameW.getInt(game) == oldW
            && gameH.getInt(game) == oldH
            && scaleField.getFloat(modern) == oldScale
            && pixelsField.getFloat(modern) == oldPixels,
        "High-resolution fixture restores the native window and menu fields");
    check(
        GL11.glGetInteger(binding) == oldFramebuffer
            && (!separateRead || GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING) == oldRead),
        "High-resolution fixture restores the incoming draw/read framebuffer");
    IntBuffer restoredViewport = BufferUtils.createIntBuffer(16);
    GL11.glGetInteger(GL11.GL_VIEWPORT, restoredViewport);
    check(
        viewport.get(0) == restoredViewport.get(0)
            && viewport.get(1) == restoredViewport.get(1)
            && viewport.get(2) == restoredViewport.get(2)
            && viewport.get(3) == restoredViewport.get(3),
        "High-resolution fixture restores the native viewport");
    check(GL11.glGetError() == GL11.GL_NO_ERROR, "High-resolution fixture cleanup leaves GL clean");
  }

  private static void capture(int width, int height, String name) throws Exception {
    ByteBuffer rgba = BufferUtils.createByteBuffer(width * height * 4);
    GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, rgba);
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    try {
      int[] rgb = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
      for (int y = 0; y < height; y++) {
        int source = (height - y - 1) * width * 4, destination = y * width;
        for (int x = 0; x < width; x++, source += 4)
          rgb[destination + x] =
              (rgba.get(source) & 255) << 16
                  | (rgba.get(source + 1) & 255) << 8
                  | rgba.get(source + 2) & 255;
      }
      check(
          ImageIO.write(
              image, "png", new File(System.getProperty("perihelion.smoke.output"), name)),
          "Actual high-resolution attachment is captured as PNG");
    } finally {
      image.flush();
    }
  }

  private static void paintFixture(
      Object modern,
      PanelLayout.Panel panel,
      UiCanvas canvas,
      float scale,
      Method configure,
      Method paint)
      throws Exception {
    int before = currentProgram();
    try (RenderState ignored = new RenderState().open()) {
      RenderStyle.text();
      GL11.glDisable(GL11.GL_SCISSOR_TEST);
      GL11.glClearColor(.86f, .87f, .85f, 1);
      GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
      GL11.glScalef(scale, scale, 1);
      configure.invoke(modern);
      paint.invoke(modern, panel, -1f, -1f, false);
      check(canvas.clip == null, "Production panel releases its canvas mask");
      check(currentProgram() == before, "Production panel restores its shader program");
    }
  }

  private static void foreignProgram(
      RoundedClip clip, PanelLayout.Panel panel, float pixels, float height, int framebufferHeight)
      throws Exception {
    if (!GLContext.getCapabilities().OpenGL20) return;
    int saved = currentProgram(), foreign = number(clip, "program");
    try (RenderState ignored = new RenderState().open()) {
      GL20.glUseProgram(foreign);
      check(
          clip.begin(panel.x, panel.y, panel.width, height, 5, pixels, framebufferHeight),
          "Rounded mask begins with a nonzero incoming program");
      clip.texture(true);
      clip.texture(false);
      clip.end();
      check(currentProgram() == foreign, "Rounded mask restores a nonzero incoming program");
      check(
          !clip.begin(panel.x, panel.y, 0, height, 5, pixels, framebufferHeight)
              && currentProgram() == foreign,
          "Rejected mask bounds preserve the incoming program");
      check(GL11.glGetError() == GL11.GL_NO_ERROR, "Nonzero-program mask probe leaves GL clean");
    } finally {
      clip.end();
      GL20.glUseProgram(saved);
    }
  }

  static void remember(Object modern) throws Exception {
    Object clip = field(modern.getClass(), "roundedClip").get(modern);
    livePrograms.put(modern, number(clip, "program"));
  }

  static void closed(Object modern) throws Exception {
    Object clip = field(modern.getClass(), "roundedClip").get(modern);
    Integer previous = livePrograms.remove(modern);
    check(number(clip, "program") == 0, "Closed GUI clears its rounded-mask program handle");
    if (previous != null && previous != 0)
      check(!GL20.glIsProgram(previous), "Closed GUI deletes the actual rounded-mask program");
    UiCanvas canvas = (UiCanvas) field(modern.getClass(), "canvas").get(modern);
    check(canvas.clip == null, "Closed GUI releases the canvas mask callback");
  }

  private static int pixel(float x, float top, int height) {
    ByteBuffer rgba = BufferUtils.createByteBuffer(4);
    GL11.glReadPixels(
        (int) Math.floor(x),
        height - 1 - (int) Math.floor(top),
        1,
        1,
        GL11.GL_RGBA,
        GL11.GL_UNSIGNED_BYTE,
        rgba);
    return (rgba.get(0) & 255) << 16 | (rgba.get(1) & 255) << 8 | rgba.get(2) & 255;
  }

  private static boolean orange(int rgb) {
    int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
    return r > 150 && r - g > 45 && g - b > 30;
  }

  private static boolean headerDark(int rgb) {
    return (rgb >> 16 & 255) < 65 && (rgb >> 8 & 255) < 65 && (rgb & 255) < 65;
  }

  private static String rgb(int value) {
    return Integer.toHexString(0xff000000 | value);
  }

  private static int currentProgram() {
    return GLContext.getCapabilities().OpenGL20 ? GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) : 0;
  }

  private static int number(Object object, String name) throws Exception {
    return field(object.getClass(), name).getInt(object);
  }

  private static float[] floats(Object object, String... names) throws Exception {
    float[] values = new float[names.length];
    for (int i = 0; i < names.length; i++)
      values[i] = field(object.getClass(), names[i]).getFloat(object);
    return values;
  }

  private static void setFloats(Object object, String[] names, float[] values) throws Exception {
    for (int i = 0; i < names.length; i++)
      field(object.getClass(), names[i]).setFloat(object, values[i]);
  }

  private static void check(boolean condition, String message) {
    checks++;
    if (!condition) throw new AssertionError(message);
  }
}
