package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;

import java.lang.management.ManagementFactory;
import java.lang.reflect.*;
import java.nio.IntBuffer;
import java.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.gui.modern.ModernBackdrop;
import perihelion.integration.minecraft189.gui.panel.PanelLayout;
import perihelion.integration.minecraft189.gui.widget.UiCanvas;
import perihelion.integration.minecraft189.render.RenderState;
import perihelion.integration.minecraft189.render.RenderStyle;

/** Native resource/state checks and draw-submission samples; these samples do not measure FPS. */
public final class MoonGuiProbe {
  private static final int FRAMEBUFFER = 0x8d40, FRAMEBUFFER_BINDING = 0x8ca6;
  private static final Map<Object, int[]> liveResources = new IdentityHashMap<>();
  private static int checks;

  private MoonGuiProbe() {}

  public static void run(Object game, Object modern) throws Exception {
    checks = 0;
    check(
        field(game.getClass(), "theWorld", "field_71441_e").get(game) != null,
        "Moon resource probe requires the live world");
    Object source = method(game.getClass(), "getFramebuffer", "func_147110_a").invoke(game);
    Bindings original = new Bindings();
    ClientRuntime runtime = (ClientRuntime) field(modern.getClass(), "runtime").get(modern);
    @SuppressWarnings("unchecked")
    Setting<String> layout =
        (Setting<String>) method(modern.getClass(), "layoutSetting").invoke(modern);
    @SuppressWarnings("unchecked")
    Setting<String> utilities =
        (Setting<String>) method(modern.getClass(), "utilitySetting").invoke(modern);
    String savedLayout = layout.get(), savedUtilities = utilities.get();
    try {
      boolean framebufferEnabled =
          (Boolean)
              method(
                      Class.forName("net.minecraft.client.renderer.OpenGlHelper"),
                      "isFramebufferEnabled",
                      "func_148822_b")
                  .invoke(null);
      if (framebufferEnabled) {
        method(
                source.getClass(),
                new Class<?>[] {boolean.class},
                "bindFramebuffer",
                "func_147610_a")
            .invoke(source, true);
        check(
            GL11.glGetInteger(FRAMEBUFFER_BINDING)
                == number(source, "framebufferObject", "field_147616_f"),
            "Resource probe binds the actual Minecraft framebuffer");
      }
      Painter painter = new Painter(modern);
      painter.frames(48);
      RoundedGuiProbe.remember(modern);
      Object backdrop = field(modern.getClass(), "backdrop").get(modern);
      if (GLContext.getCapabilities().OpenGL20 && framebufferEnabled) {
        int[] ids = resources(backdrop);
        check(ids[0] != 0, "World blur shader compiles and links on supported hardware");
        int w = (number(source, "framebufferWidth", "field_147621_c") + 3) / 4;
        int h = (number(source, "framebufferHeight", "field_147618_d") + 3) / 4;
        for (String name : new String[] {"first", "second"}) {
          Object target = field(backdrop.getClass(), name).get(backdrop);
          check(
              number(target, "width") == w && number(target, "height") == h,
              "Blur target uses ceil(native framebuffer / 4): " + name);
        }
        for (int i = 0; i < 12; i++) {
          painter.frame();
          check(Arrays.equals(ids, resources(backdrop)), "Warmed frames reuse all blur resources");
        }
        state(modern, backdrop, ids[0]);
        liveResources.put(modern, ids);
      } else {
        check(number(backdrop, "program") == 0, "Unsupported blur leaves no shader allocated");
        System.out.println("[PERIHELION_SMOKE] MOON_BLUR SKIP unsupported framebuffer/GL20");
      }
      for (int i = 0; i < 10; i++) {
        method(modern.getClass(), "resetLayout").invoke(modern);
        painter.frame();
        Map<?, ?> motions = (Map<?, ?>) field(modern.getClass(), "panels").get(modern);
        PanelLayout model = (PanelLayout) method(modern.getClass(), "model").invoke(modern);
        check(motions.size() <= model.panels().size(), "Layout resets release stale panel motions");
      }
      profile(modern, painter);
      check(GL11.glGetError() == GL11.GL_NO_ERROR, "Moon resource/state probe leaves GL clean");
      System.out.println("[PERIHELION_SMOKE] MOON_GUI_NATIVE PASS checks=" + checks);
    } finally {
      runtime.modules().setSetting("clickgui", layout, savedLayout);
      runtime.modules().setSetting("clickgui", utilities, savedUtilities);
      original.restore();
    }
  }

  /** Call while the actual Config/HUD child screen is open, before returning to this screen. */
  public static void closed(Object modern) throws Exception {
    RoundedGuiProbe.closed(modern);
    Object backdrop = field(modern.getClass(), "backdrop").get(modern);
    int[] old = liveResources.remove(modern), now = resources(backdrop);
    for (int value : now) check(value == 0, "Closed GUI clears its blur resource handles");
    if (old != null) {
      check(!GL20.glIsProgram(old[0]), "Closed GUI deletes its blur shader program");
      check(
          !GL11.glIsTexture(old[2]) && !GL11.glIsTexture(old[4]),
          "Closed GUI deletes blur textures");
      check(
          !framebufferExists(old[1]) && !framebufferExists(old[3]), "Closed GUI deletes blur FBOs");
    }
    Object assets = field(modern.getClass(), "assets").get(modern);
    check(
        field(assets.getClass(), "shadow").get(assets) == null
            && field(assets.getClass(), "icons").get(assets) == null,
        "Closed GUI releases screen-owned icon/shadow texture references");
    check(GL11.glGetError() == GL11.GL_NO_ERROR, "Closing GUI leaves GL clean");
    System.out.println("[PERIHELION_SMOKE] MOON_GUI_CLOSED PASS");
  }

  private static void state(Object modern, Object value, int program) throws Exception {
    ModernBackdrop backdrop = (ModernBackdrop) value;
    UiCanvas canvas = (UiCanvas) field(modern.getClass(), "canvas").get(modern);
    Bindings outer = new Bindings();
    try (RenderState ignored = new RenderState().open()) {
      RenderStyle.text();
      GL20.glUseProgram(program);
      activeTexture(GL13.GL_TEXTURE1);
      if (separateRead()) bind(GL30.GL_READ_FRAMEBUFFER, 0);
      Bindings before = new Bindings();
      backdrop.render(
          canvas,
          field(modern.getClass(), "viewWidth").getFloat(modern),
          field(modern.getClass(), "viewHeight").getFloat(modern),
          .7f);
      check(
          before.same(new Bindings()),
          "Blur restores incoming FBO/read FBO/viewport/program/textures");
      check(GL11.glGetError() == GL11.GL_NO_ERROR, "Supported blur passes leave GL clean");
    } finally {
      outer.restore();
    }
  }

  private static void profile(Object modern, Painter painter) throws Exception {
    Map<?, ?> groups = (Map<?, ?>) field(modern.getClass(), "views").get(modern);
    Object view = null;
    for (Object group : groups.values()) {
      List<?> entries = (List<?>) group;
      if (!entries.isEmpty()) {
        view = entries.get(0);
        break;
      }
    }
    check(view != null, "Motion profile has a module row");
    Field expanded = field(view.getClass(), "expanded");
    boolean saved = expanded.getBoolean(view);
    AllocationCounter allocation = new AllocationCounter();
    try {
      expanded.setBoolean(view, true);
      painter.frames(64);
      samples("settled", painter, allocation, null, null);
      for (int i = 0; i < 24; i++) {
        expanded.setBoolean(view, i % 12 < 6);
        painter.frame();
      }
      samples("continuousMotion", painter, allocation, view, expanded);
    } finally {
      expanded.setBoolean(view, saved);
      painter.frames(32);
    }
  }

  private static void samples(
      String name, Painter painter, AllocationCounter allocation, Object view, Field expanded)
      throws Exception {
    long[] elapsed = new long[48];
    long allocatedBefore = allocation.read();
    for (int i = 0; i < elapsed.length; i++) {
      if (expanded != null) expanded.setBoolean(view, i % 12 < 6);
      long start = System.nanoTime();
      painter.frame();
      elapsed[i] = System.nanoTime() - start;
    }
    long allocatedAfter = allocation.read();
    Arrays.sort(elapsed);
    long bytes = allocatedBefore < 0 || allocatedAfter < 0 ? -1 : allocatedAfter - allocatedBefore;
    System.out.println(
        "[PERIHELION_SMOKE] MOON_GUI_DRAW_CPU mode="
            + name
            + " samples=48 medianMs="
            + elapsed[24] / 1_000_000.0
            + " p95Ms="
            + elapsed[45] / 1_000_000.0
            + " threadAllocatedBytes="
            + bytes
            + " bytesPerFrame="
            + (bytes < 0 ? -1 : bytes / 48)
            + " harnessIncluded=true gpuCompletionWait=false fpsMeasured=false");
  }

  private static final class Painter {
    private final Object screen;
    private final Method draw;
    private final Field previousFrame;
    private final Object[] arguments = {-1, -1, 0f};

    Painter(Object screen) throws Exception {
      this.screen = screen;
      draw =
          method(
              screen.getClass(),
              new Class<?>[] {int.class, int.class, float.class},
              "drawScreen",
              "func_73863_a");
      previousFrame = field(screen.getClass(), "previousFrame");
    }

    void frame() throws Exception {
      previousFrame.setLong(screen, System.nanoTime() - 16_666_667L);
      draw.invoke(screen, arguments);
    }

    void frames(int count) throws Exception {
      for (int i = 0; i < count; i++) frame();
    }
  }

  private static final class AllocationCounter {
    private final Object bean = ManagementFactory.getThreadMXBean();
    private Method read;
    private final Object[] arguments = {Thread.currentThread().getId()};

    AllocationCounter() {
      try {
        Class<?> type = Class.forName("com.sun.management.ThreadMXBean");
        if (!(Boolean) type.getMethod("isThreadAllocatedMemorySupported").invoke(bean)) return;
        if (!(Boolean) type.getMethod("isThreadAllocatedMemoryEnabled").invoke(bean)) return;
        read = type.getMethod("getThreadAllocatedBytes", long.class);
      } catch (ReflectiveOperationException unavailable) {
        read = null;
      }
    }

    long read() {
      try {
        return read == null ? -1 : ((Number) read.invoke(bean, arguments)).longValue();
      } catch (ReflectiveOperationException unavailable) {
        return -1;
      }
    }
  }

  private static final class Bindings {
    final int framebuffer = framebufferSupported() ? GL11.glGetInteger(FRAMEBUFFER_BINDING) : 0;
    final int readFramebuffer =
        separateRead() ? GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING) : framebuffer;
    final int program =
        GLContext.getCapabilities().OpenGL20 ? GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) : 0;
    final int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    final int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
    final int defaultTexture;
    final int[] viewport = new int[4];

    Bindings() {
      IntBuffer values = BufferUtils.createIntBuffer(16);
      GL11.glGetInteger(GL11.GL_VIEWPORT, values);
      for (int i = 0; i < 4; i++) viewport[i] = values.get(i);
      GL13.glActiveTexture(GL13.GL_TEXTURE0);
      defaultTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
      GL13.glActiveTexture(active);
    }

    boolean same(Bindings other) {
      return framebuffer == other.framebuffer
          && readFramebuffer == other.readFramebuffer
          && program == other.program
          && active == other.active
          && texture == other.texture
          && defaultTexture == other.defaultTexture
          && Arrays.equals(viewport, other.viewport);
    }

    void restore() throws Exception {
      if (framebufferSupported()) bind(FRAMEBUFFER, framebuffer);
      if (separateRead()) bind(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
      if (GLContext.getCapabilities().OpenGL20) GL20.glUseProgram(program);
      GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
      activeTexture(active);
    }
  }

  private static int[] resources(Object backdrop) throws Exception {
    Object first = field(backdrop.getClass(), "first").get(backdrop);
    Object second = field(backdrop.getClass(), "second").get(backdrop);
    return new int[] {
      number(backdrop, "program"),
      number(first, "framebuffer"),
      number(first, "texture"),
      number(second, "framebuffer"),
      number(second, "texture")
    };
  }

  private static boolean separateRead() {
    return GLContext.getCapabilities().OpenGL30
        || GLContext.getCapabilities().GL_ARB_framebuffer_object;
  }

  private static boolean framebufferSupported() {
    return separateRead() || GLContext.getCapabilities().GL_EXT_framebuffer_object;
  }

  private static boolean framebufferExists(int framebuffer) {
    if (GLContext.getCapabilities().OpenGL30) return GL30.glIsFramebuffer(framebuffer);
    if (GLContext.getCapabilities().GL_ARB_framebuffer_object)
      return ARBFramebufferObject.glIsFramebuffer(framebuffer);
    return EXTFramebufferObject.glIsFramebufferEXT(framebuffer);
  }

  private static void bind(int target, int framebuffer) throws Exception {
    method(
            Class.forName("net.minecraft.client.renderer.OpenGlHelper"),
            new Class<?>[] {int.class, int.class},
            "glBindFramebuffer",
            "func_153171_g")
        .invoke(null, target, framebuffer);
  }

  private static void activeTexture(int unit) throws Exception {
    method(
            Class.forName("net.minecraft.client.renderer.GlStateManager"),
            new Class<?>[] {int.class},
            "setActiveTexture",
            "func_179138_g")
        .invoke(null, unit);
    GL13.glActiveTexture(unit);
  }

  private static int number(Object value, String... names) throws Exception {
    return field(value.getClass(), names).getInt(value);
  }

  private static void check(boolean condition, String message) {
    checks++;
    if (!condition) throw new AssertionError(message);
  }
}
