package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;

import com.google.gson.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import javax.imageio.ImageIO;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleState;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.hud.TargetHudModule;
import perihelion.integration.minecraft189.render.RenderState;
import perihelion.integration.minecraft189.render.RenderStyle;

/**
 * Every shipped donor design, on the actual remapped renderer, with captured framebuffer evidence.
 */
public final class TargetHudDesignProbe {
  private static int checks;

  public static void run(ClientRuntime runtime, Object game) throws Exception {
    checks = 0;
    Object world = field(game.getClass(), "theWorld", "field_71441_e").get(game);
    Object player = field(game.getClass(), "thePlayer", "field_71439_g").get(game);
    Object hud = runtime.modules().require("hud").module();
    Object module = runtime.modules().require("targethud").module();
    Map<String, Map<Setting<?>, Object>> saved = new LinkedHashMap<>();
    for (String id : new String[] {"hud", "targethud", "clickgui"}) {
      Map<Setting<?>, Object> values = new LinkedHashMap<>();
      for (Setting<?> setting : runtime.modules().require(id).module().settings().values())
        values.put(setting, setting.get());
      saved.put(id, values);
    }
    boolean oldHud = runtime.modules().require("hud").requestedEnabled();
    boolean oldTarget = runtime.modules().require("targethud").requestedEnabled();
    int listeners = runtime.clientEvents().subscriptionCount();
    List<Integer> ids = new ArrayList<>();
    JsonArray captures = new JsonArray();
    File output = new File(System.getProperty("perihelion.smoke.output"));
    Field screen = field(game.getClass(), "currentScreen", "field_71462_r");
    Object oldScreen = screen.get(game);
    Method display =
        method(
            game.getClass(),
            new Class<?>[] {Class.forName("net.minecraft.client.gui.GuiScreen")},
            "displayGuiScreen",
            "func_147108_a");
    Class<?> entity = Class.forName("net.minecraft.entity.Entity"),
        living = Class.forName("net.minecraft.entity.EntityLivingBase");
    Object resolution =
        Class.forName("net.minecraft.client.gui.ScaledResolution")
            .getConstructor(game.getClass())
            .newInstance(game);
    int width =
        (Integer)
            method(resolution.getClass(), "getScaledWidth", "func_78326_a").invoke(resolution);
    int height =
        (Integer)
            method(resolution.getClass(), "getScaledHeight", "func_78328_b").invoke(resolution);
    int framebufferWidth = field(game.getClass(), "displayWidth", "field_71443_c").getInt(game);
    int framebufferHeight = field(game.getClass(), "displayHeight", "field_71440_d").getInt(game);
    Method add =
        method(
            world.getClass(),
            new Class<?>[] {int.class, entity},
            "addEntityToWorld",
            "func_73027_a");
    Method attack =
        method(
            Class.forName("perihelion.integration.minecraft189.HookBridge"),
            new Class<?>[] {living},
            "attack");
    Method glState = method(RenderHookProbe.class, "readState");
    try (RenderState ignored = new RenderState().open()) {
      display.invoke(game, new Object[] {null});
      runtime.modules().setEnabled("hud", false);
      runtime.modules().setEnabled("targethud", true);
      check(
          runtime.modules().require("targethud").state() == ModuleState.ENABLED,
          "independent Target HUD activates");
      set(runtime, "targethud", "hide-delay", 10.0);
      set(runtime, "targethud", "theme", "ORIGINAL");
      set(runtime, "hud", "target-x", .5);
      set(runtime, "hud", "target-y", .5);
      set(runtime, "hud", "target-background", "AUTO");
      set(runtime, "hud", "target-scale", 1.0);
      Object renderer = field(module.getClass(), "renderer").get(module);
      Object designs = field(renderer.getClass(), "targetDesigns").get(renderer);
      Class<?> element = Class.forName("perihelion.integration.minecraft189.hud.HudModule$Element");
      Object targetElement = Enum.valueOf((Class) element, "TARGET");
      Method render =
          method(
              renderer.getClass(),
              new Class<?>[] {int.class, int.class, boolean.class, element, int.class, int.class},
              "render");
      Method update = method(renderer.getClass(), new Class<?>[] {boolean.class}, "update");
      Object[] subjects = new Object[3];
      for (int i = 0; i < 2; i++) {
        Object profile =
            Class.forName("com.mojang.authlib.GameProfile")
                .getConstructor(UUID.class, String.class)
                .newInstance(
                    new UUID(0, 913000 + i),
                    i == 0 ? "Target_Example" : "An_Extremely_Long_Target_Name_123456789");
        subjects[i] =
            Class.forName("net.minecraft.client.entity.EntityOtherPlayerMP")
                .getConstructor(Class.forName("net.minecraft.world.World"), profile.getClass())
                .newInstance(world, profile);
        Object inventory =
            field(subjects[i].getClass(), "inventory", "field_71071_by").get(subjects[i]);
        Object[] armor =
            (Object[])
                field(inventory.getClass(), "armorInventory", "field_70460_b").get(inventory);
        String[] pieces = {
          "diamond_boots", "diamond_leggings", "diamond_chestplate", "diamond_helmet"
        };
        for (int slot = 0; slot < 4; slot++) armor[slot] = stack(pieces[slot]);
        Object[] main =
            (Object[]) field(inventory.getClass(), "mainInventory", "field_70462_a").get(inventory);
        main[0] = stack("diamond_sword");
      }
      subjects[2] =
          Class.forName("net.minecraft.entity.passive.EntityHorse")
              .getConstructor(Class.forName("net.minecraft.world.World"))
              .newInstance(world);
      for (int i = 0; i < subjects.length; i++) {
        int id = -913000 - i;
        ids.add(id);
        method(
                entity,
                new Class<?>[] {double.class, double.class, double.class},
                "setPosition",
                "func_70107_b")
            .invoke(
                subjects[i],
                number(player, "posX", "field_70165_t") + 2,
                number(player, "posY", "field_70163_u"),
                number(player, "posZ", "field_70161_v"));
        add.invoke(world, id, subjects[i]);
        method(living, new Class<?>[] {float.class}, "setHealth", "func_70606_j")
            .invoke(subjects[i], 13f);
      }
      Map<?, ?> boxes = (Map<?, ?>) field(renderer.getClass(), "boxes").get(renderer);
      for (String visual : new String[] {"OLD", "NEW"}) {
        set(runtime, "clickgui", "visuals", visual);
        set(runtime, "clickgui", "client-style", "ADJUST");
        for (TargetHudModule.Design design : TargetHudModule.Design.values()) {
          set(runtime, "targethud", "design", design.name());
          for (int subject : new int[] {0, 2}) {
            attack.invoke(null, subjects[subject]);
            refresh(update, renderer);
            background();
            Object state = glState.invoke(null);
            int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            render.invoke(renderer, width, height, false, null, 0, 0);
            check(
                state.equals(glState.invoke(null))
                    && program == GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                "GL state restored: " + design + " " + visual);
            Object box = boxes.get(targetElement);
            bounds(box, width, height);
            String file =
                "target-"
                    + design.name().toLowerCase(Locale.ROOT)
                    + "-"
                    + visual.toLowerCase(Locale.ROOT)
                    + (subject == 0 ? "-player.png" : "-mob.png");
            ProductionHudProbe.capture(file);
            BufferedImage painted = ImageIO.read(new File(output, file));
            float sx = framebufferWidth / (float) width, sy = framebufferHeight / (float) height;
            int x = (int) (n(box, "x") * sx), y = (int) (n(box, "y") * sy);
            int bw = (int) (n(box, "w") * sx), bh = (int) (n(box, "h") * sy), ink = 0;
            int blank = painted.getRGB(0, 0);
            for (int yy = y; yy < Math.min(painted.getHeight(), y + bh); yy++)
              for (int xx = x; xx < Math.min(painted.getWidth(), x + bw); xx++)
                if (painted.getRGB(xx, yy) != blank) ink++;
            check(ink > 50, "design paints visible content: " + design);
            check(GL11.glGetError() == GL11.GL_NO_ERROR, "no GL errors: " + design);
            JsonObject capture = new JsonObject();
            capture.addProperty("design", design.name());
            capture.addProperty("visuals", visual);
            capture.addProperty("target", subject == 0 ? "player" : "mob");
            capture.addProperty("file", file);
            capture.addProperty("x", x);
            capture.addProperty("y", y);
            capture.addProperty("width", bw);
            capture.addProperty("height", bh);
            capture.addProperty("paintedPixels", ink);
            captures.add(capture);
          }
          // A donor design is independent of the menu's Raven/Adjust rendering path.
          set(runtime, "clickgui", "client-style", "RAVEN");
          attack.invoke(null, subjects[0]);
          refresh(update, renderer);
          background();
          Object state = glState.invoke(null);
          render.invoke(renderer, width, height, false, null, 0, 0);
          check(state.equals(glState.invoke(null)), "Raven path restores state: " + design);
          bounds(boxes.get(targetElement), width, height);
          set(runtime, "clickgui", "client-style", "ADJUST");
          for (double scale : new double[] {.65, 1.75}) {
            set(runtime, "hud", "target-scale", scale);
            attack.invoke(null, subjects[1]);
            refresh(update, renderer);
            render.invoke(renderer, 320, 240, false, null, 0, 0);
            bounds(boxes.get(targetElement), 320, 240);
            if (design == TargetHudModule.Design.MYAU_NEVERLOSE
                || design == TargetHudModule.Design.ARSENIC_SIMPLE
                || design == TargetHudModule.Design.YURI_EXHIBITION
                || design == TargetHudModule.Design.B4_MODERN) {
              background();
              render.invoke(renderer, width, height, false, null, 0, 0);
              ProductionHudProbe.capture(
                  "target-text-"
                      + design.name().toLowerCase(Locale.ROOT)
                      + "-"
                      + visual.toLowerCase(Locale.ROOT)
                      + "-"
                      + scale
                      + ".png");
            }
          }
          set(runtime, "hud", "target-scale", 1.0);
          for (String theme : new String[] {"CLIENT", "CUSTOM"}) {
            set(runtime, "targethud", "theme", theme);
            for (String bg : new String[] {"AUTO", "SOLID", "GRADIENT", "NONE"}) {
              set(runtime, "hud", "target-background", bg);
              attack.invoke(null, subjects[0]);
              refresh(update, renderer);
              background();
              render.invoke(renderer, width, height, false, null, 0, 0);
              check(
                  GL11.glGetError() == GL11.GL_NO_ERROR,
                  "custom presentation works: " + design + " " + theme + " " + bg);
            }
          }
          set(runtime, "hud", "target-background", "AUTO");
          set(runtime, "targethud", "theme", "ORIGINAL");
        }
      }
      // Health falls and recovers without negative/overflow fills or state from another entity.
      set(runtime, "targethud", "design", "B4_MODERN");
      attack.invoke(null, subjects[0]);
      refresh(update, renderer);
      for (float health : new float[] {5, 18}) {
        method(living, new Class<?>[] {float.class}, "setHealth", "func_70606_j")
            .invoke(subjects[0], health);
        refresh(update, renderer);
        for (int frame = 0; frame < 10; frame++) {
          field(designs.getClass(), "lastFrame").setLong(designs, System.nanoTime() - 50_000_000L);
          render.invoke(renderer, width, height, false, null, 0, 0);
          float shown = field(designs.getClass(), "displayed").getFloat(designs);
          check(shown >= 0 && shown <= 20, "animated health stays bounded");
        }
      }
      field(module.getClass(), "attackedAt").setLong(module, System.nanoTime() - 20_000_000_000L);
      refresh(update, renderer);
      render.invoke(renderer, width, height, false, null, 0, 0);
      check(
          !field(renderer.getClass(), "hasTarget").getBoolean(renderer),
          "hide delay still expires");
      check(
          field(designs.getClass(), "animated").get(designs) == null,
          "expired target animation reference is released");
      runtime.modules().setEnabled("targethud", false);
      check(
          field(module.getClass(), "renderer").get(module) == null,
          "disable releases the renderer");
      Object clip = field(designs.getClass(), "clip").get(designs);
      check(
          field(clip.getClass(), "program").getInt(clip) == 0,
          "owned portrait/gradient shader is deleted");
      check(
          field(designs.getClass(), "animated").get(designs) == null,
          "disable releases animation state");
      JsonObject report = new JsonObject();
      report.addProperty("designs", TargetHudModule.Design.values().length);
      report.addProperty("checks", checks);
      report.add("captures", captures);
      Files.write(
          new File(output, "target-hud-designs.json").toPath(),
          new GsonBuilder()
              .setPrettyPrinting()
              .create()
              .toJson(report)
              .getBytes(StandardCharsets.UTF_8));
    } finally {
      runtime.modules().setEnabled("targethud", false);
      for (Map.Entry<String, Map<Setting<?>, Object>> entry : saved.entrySet())
        for (Map.Entry<Setting<?>, Object> value : entry.getValue().entrySet())
          put(runtime, entry.getKey(), value.getKey(), value.getValue());
      for (int id : ids)
        method(
                world.getClass(),
                new Class<?>[] {int.class},
                "removeEntityFromWorld",
                "func_73028_b")
            .invoke(world, id);
      display.invoke(game, new Object[] {oldScreen});
      runtime.modules().setEnabled("hud", oldHud);
      runtime.modules().setEnabled("targethud", oldTarget);
    }
    check(runtime.clientEvents().subscriptionCount() == listeners, "all owned listeners detach");
    System.out.println(
        "[PERIHELION_SMOKE] TARGET_HUD_DESIGNS PASS checks="
            + checks
            + " designs="
            + TargetHudModule.Design.values().length
            + " captures="
            + captures.size());
  }

  private static void background() {
    GL11.glDisable(GL11.GL_SCISSOR_TEST);
    GL11.glClearColor(.12f, .14f, .18f, 1);
    GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    RenderStyle.text();
  }

  private static void refresh(Method update, Object renderer) throws Exception {
    for (int i = 0; i < 4; i++) update.invoke(renderer, false);
  }

  private static float n(Object object, String name) throws Exception {
    return ((Number) field(object.getClass(), name).get(object)).floatValue();
  }

  private static double number(Object object, String named, String srg) throws Exception {
    return ((Number) field(object.getClass(), named, srg).get(object)).doubleValue();
  }

  private static void bounds(Object box, int width, int height) throws Exception {
    check(field(box.getClass(), "visible").getBoolean(box), "selected card is visible");
    check(
        n(box, "x") >= 0
            && n(box, "y") >= 0
            && n(box, "x") + n(box, "w") <= width
            && n(box, "y") + n(box, "h") <= height,
        "drawing and drag bounds remain on screen");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void set(ClientRuntime runtime, String module, String id, Object value) {
    Setting setting = runtime.modules().require(module).module().settings().get(id);
    runtime
        .modules()
        .setSetting(
            module, setting, value instanceof String ? setting.parse((String) value) : value);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void put(ClientRuntime runtime, String module, Setting setting, Object value) {
    runtime.modules().setSetting(module, setting, value);
  }

  private static void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }
}
