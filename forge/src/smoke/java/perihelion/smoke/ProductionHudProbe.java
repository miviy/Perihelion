package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;

import java.lang.reflect.*;
import java.util.*;
import org.lwjgl.opengl.GL11;
import perihelion.core.event.*;
import perihelion.core.input.Keybind;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.*;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.render.RenderState;

/** Packaged, transformed-game HUD/editor regressions, with real GL and native item rendering. */
public final class ProductionHudProbe {
  private static void cleanEditor(Method draw, Object editor) throws Exception {
    GL11.glClearColor(.045f, .05f, .06f, 1);
    GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    draw.invoke(editor, 0, 0, 0f);
  }

  private static int checks;
  private static final String PACKAGE = "perihelion.integration.minecraft189.hud.";

  public static void run(ClientRuntime r) throws Exception {
    checks = 0;
    Object game =
        method(Class.forName("net.minecraft.client.Minecraft"), "getMinecraft", "func_71410_x")
            .invoke(null);
    Object player = field(game.getClass(), "thePlayer", "field_71439_g").get(game);
    Object world = field(game.getClass(), "theWorld", "field_71441_e").get(game);
    Object inventory = field(player.getClass(), "inventory", "field_71071_by").get(player);
    Object[] armor =
        (Object[]) field(inventory.getClass(), "armorInventory", "field_70460_b").get(inventory);
    Object[] savedArmor = armor.clone();
    Class<?> effectType = Class.forName("net.minecraft.potion.PotionEffect");
    Map potionMap =
        (Map) field(player.getClass(), "activePotionsMap", "field_70713_bf").get(player);
    Map savedPotions = new HashMap(potionMap);
    Field screen = field(game.getClass(), "currentScreen", "field_71462_r");
    Object oldScreen = screen.get(game);
    Field mouseOver = field(game.getClass(), "objectMouseOver", "field_71476_x");
    Object oldMouse = mouseOver.get(game);
    Object hud = r.modules().require("hud").module();
    Method display =
        method(
            game.getClass(),
            new Class<?>[] {Class.forName("net.minecraft.client.gui.GuiScreen")},
            "displayGuiScreen",
            "func_147108_a");
    Object scaled =
        Class.forName("net.minecraft.client.gui.ScaledResolution")
            .getConstructor(game.getClass())
            .newInstance(game);
    int width =
        (Integer) method(scaled.getClass(), "getScaledWidth", "func_78326_a").invoke(scaled);
    int height =
        (Integer) method(scaled.getClass(), "getScaledHeight", "func_78328_b").invoke(scaled);
    Class<?> entityType = Class.forName("net.minecraft.entity.Entity");
    List<Integer> ids = new ArrayList<>();
    Object scoreboard = method(world.getClass(), "getScoreboard", "func_96441_U").invoke(world);
    List<Object> teams = new ArrayList<>();
    String[] visuals = {"fullbright", "nofov", "nobob", "fastplace", "sprint"};
    int baseline = r.clientEvents().subscriptionCount();
    Map<String, Object> originals = new HashMap<>();
    for (Setting<?> setting : r.modules().require("hud").module().settings().values())
      originals.put(setting.id(), setting.get());
    try (RenderState state = new RenderState().open()) {
      display.invoke(game, new Object[] {null});
      for (String id : new String[] {"keys", "players", "notifications"}) set(r, "hud", id, true);
      for (String id : visuals) r.modules().setEnabled(id, true);
      r.modules().setEnabled("hud", true);
      check(r.modules().require("hud").state() == ModuleState.ENABLED, "HUD activates");
      Class<?> headType =
          Class.forName("net.minecraft.network.play.server.S19PacketEntityHeadLook");
      Field headYaw = field(player.getClass(), "rotationYawHead", "field_70759_as");
      float savedHead = headYaw.getFloat(player);
      Object handler = method(game.getClass(), "getNetHandler", "func_147114_u").invoke(game);
      try {
        Object headPacket =
            headType.getConstructor(entityType, byte.class).newInstance(player, (byte) 32);
        method(
                handler.getClass(),
                new Class<?>[] {headType},
                "handleEntityHeadLook",
                "func_147267_a")
            .invoke(handler, headPacket);
        check(
            Math.abs(headYaw.getFloat(player) - 45f) < .001f,
            "current-world head rotation remains delivered");
      } finally {
        headYaw.setFloat(player, savedHead);
      }
      Object renderer = field(hud.getClass(), "renderer").get(hud);
      Object font = field(renderer.getClass(), "text").get(renderer);
      Method fontWidth = method(font.getClass(), new Class<?>[] {String.class}, "width");
      check(
          fontWidth
              .invoke(font, "Flight \u00a77Hypixel")
              .equals(fontWidth.invoke(font, "Flight Hypixel")),
          "formatting uses the same smooth font metrics");
      for (int i = 0; i < 600; i++) fontWidth.invoke(font, "bounded text " + i);
      check(
          ((Map<?, ?>) field(font.getClass(), "runs").get(font)).size() <= 256,
          "formatted text cache is bounded");
      check((Integer) fontWidth.invoke(font, "100%") <= 25, "armor percentage fits its column");
      Class<?> elementType = Class.forName(PACKAGE + "HudModule$Element");
      Method render =
          method(
              renderer.getClass(),
              new Class<?>[] {
                int.class, int.class, boolean.class, elementType, int.class, int.class
              },
              "render");
      Method update = method(renderer.getClass(), new Class<?>[] {boolean.class}, "update");
      for (int i = 0; i < 4; i++)
        armor[i] =
            stack(
                new String[] {
                      "diamond_boots", "diamond_leggings", "diamond_chestplate", "diamond_helmet"
                    }
                    [i]);
      method(player.getClass(), new Class<?>[] {effectType}, "addPotionEffect", "func_70690_d")
          .invoke(
              player,
              effectType.getConstructor(int.class, int.class, int.class).newInstance(1, 2400, 1));
      Class<?> colorType = Class.forName("net.minecraft.util.EnumChatFormatting");
      String[] teamColors = {"BLUE", "GREEN", "RED", "YELLOW"};
      for (String color : teamColors) {
        Object team =
            method(
                    scoreboard.getClass(),
                    new Class<?>[] {String.class},
                    "createTeam",
                    "func_96527_f")
                .invoke(scoreboard, "hud_" + color);
        teams.add(team);
        method(team.getClass(), new Class<?>[] {colorType}, "setChatFormat", "func_178774_a")
            .invoke(team, Enum.valueOf((Class) colorType, color));
      }
      Object first = null;
      for (int i = 0; i < 15; i++) {
        Object profile =
            Class.forName("com.mojang.authlib.GameProfile")
                .getConstructor(UUID.class, String.class)
                .newInstance(new UUID(0, 81000 + i), "HUD_Player_" + i);
        Object other =
            Class.forName("net.minecraft.client.entity.EntityOtherPlayerMP")
                .getConstructor(Class.forName("net.minecraft.world.World"), profile.getClass())
                .newInstance(world, profile);
        method(
                entityType,
                new Class<?>[] {double.class, double.class, double.class},
                "setPosition",
                "func_70107_b")
            .invoke(
                other,
                field(player.getClass(), "posX", "field_70165_t").getDouble(player) + i + 2,
                field(player.getClass(), "posY", "field_70163_u").getDouble(player),
                field(player.getClass(), "posZ", "field_70161_v").getDouble(player));
        int id = -891000 - i;
        ids.add(id);
        method(
                world.getClass(),
                new Class<?>[] {int.class, entityType},
                "addEntityToWorld",
                "func_73027_a")
            .invoke(world, id, other);
        method(
                scoreboard.getClass(),
                new Class<?>[] {String.class, String.class},
                "addPlayerToTeam",
                "func_151392_a")
            .invoke(scoreboard, "HUD_Player_" + i, "hud_" + teamColors[(i / 2) % 4]);
        if (i == 1)
          method(other.getClass(), new Class<?>[] {float.class}, "setHealth", "func_70606_j")
              .invoke(other, 19f);
        if (i == 0) first = other;
      }
      mouseOver.set(
          game,
          Class.forName("net.minecraft.util.MovingObjectPosition")
              .getConstructor(entityType)
              .newInstance(first));
      refresh(update, renderer);
      check(
          field(renderer.getClass(), "hasTarget").getBoolean(renderer), "crosshair target appears");
      check(
          ((List<?>) field(renderer.getClass(), "players").get(renderer)).size() == 8,
          "nearby rows bounded to 8 among 15 players");
      List<?> playerRows = (List<?>) field(renderer.getClass(), "players").get(renderer);
      Object firstRow = playerRows.get(0), secondRow = playerRows.get(1);
      check(
          field(firstRow.getClass(), "team").get(firstRow).equals("\u00a79Blue"),
          "player table uses scoreboard team color");
      check(
          field(secondRow.getClass(), "health").get(secondRow).equals("95%"),
          "player table health uses max-health percentage");
      set(r, "hud", "player-limit", 3);
      refresh(update, renderer);
      check(
          ((List<?>) field(renderer.getClass(), "players").get(renderer)).size() == 3,
          "player limit changes live");
      set(r, "hud", "player-limit", 8);
      r.clientEvents().post(new InputEvent(new Keybind(Keybind.Device.MOUSE, 0), true));
      refresh(update, renderer);
      check(
          field(renderer.getClass(), "cps").get(renderer).equals("1 / 0 CPS"),
          "physical click history");
      check(field(renderer.getClass(), "hasArmor").getBoolean(renderer), "armor snapshot");
      check(
          ((List<?>) field(renderer.getClass(), "effects").get(renderer))
              .stream().anyMatch(line -> line.toString().contains("2:00")),
          "potion duration snapshot");
      r.modules().setEnabled("sprint", false);
      refresh(update, renderer);
      check(
          !((Deque<?>) field(renderer.getClass(), "notices").get(renderer)).isEmpty(),
          "toggle notification records transition");
      for (int i = 0; i < 12; i++) {
        r.modules().setEnabled("sprint", i % 2 == 0);
        refresh(update, renderer);
      }
      check(
          ((Deque<?>) field(renderer.getClass(), "notices").get(renderer)).size() <= 4,
          "notifications remain bounded");
      GL11.glEnable(GL11.GL_DEPTH_TEST);
      GL11.glLineWidth(2);
      boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
      int before = field(renderer.getClass(), "updates").getInt(renderer);
      long start = System.nanoTime();
      for (int i = 0; i < 100; i++) render.invoke(renderer, width, height, false, null, 0, 0);
      long elapsed = System.nanoTime() - start;
      check(
          field(renderer.getClass(), "updates").getInt(renderer) == before,
          "100 render passes do not resample or sort game state");
      check(
          GL11.glIsEnabled(GL11.GL_DEPTH_TEST) == depth && GL11.glGetFloat(GL11.GL_LINE_WIDTH) == 2,
          "HUD restores GL state with armor items");
      check(GL11.glGetError() == GL11.GL_NO_ERROR, "HUD GL clean");
      System.out.println(
          "[PERIHELION_SMOKE] HUD_RENDER_100_NS=" + elapsed + " snapshots=0 players=8 armor=4");
      set(r, "hud", "notifications", false);
      refresh(update, renderer);
      check(
          ((Deque<?>) field(renderer.getClass(), "notices").get(renderer)).isEmpty(),
          "hiding notifications clears pending messages");
      set(r, "hud", "modules", false);
      r.modules().setEnabled("sprint", true);
      refresh(update, renderer);
      set(r, "hud", "notifications", true);
      refresh(update, renderer);
      check(
          ((Deque<?>) field(renderer.getClass(), "notices").get(renderer)).isEmpty(),
          "reenabling notifications does not announce old state changes");
      set(r, "hud", "modules", true);
      set(r, "hud", "players", false);
      set(r, "hud", "keys", false);
      set(r, "hud", "notifications", false);
      // Default production panels in a clean, native GUI projection.
      GL11.glClearColor(.07f, .075f, .09f, 1);
      GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
      render.invoke(renderer, width, height, false, null, 0, 0);
      capture("hud-production.png");
      Map<?, ?> productionBoxes = (Map<?, ?>) field(renderer.getClass(), "boxes").get(renderer);
      Object navBox = productionBoxes.get(Enum.valueOf((Class) elementType, "TAB_GUI"));
      Object logoBox = productionBoxes.get(Enum.valueOf((Class) elementType, "WATERMARK"));
      check(
          field(logoBox.getClass(), "h").getInt(logoBox) > 11
              && field(navBox.getClass(), "y").getInt(navBox)
                  >= field(logoBox.getClass(), "y").getInt(logoBox)
                      + field(logoBox.getClass(), "h").getInt(logoBox),
          "larger independent logo has matching bounds and leaves the navigator clear");
      check(
          field(navBox.getClass(), "h").getInt(navBox) == 66,
          "all six native HUD categories fit inside the navigator bounds");
      Object effectsBox = productionBoxes.get(Enum.valueOf((Class) elementType, "EFFECTS"));
      check(
          field(effectsBox.getClass(), "y").getInt(effectsBox)
              >= field(navBox.getClass(), "y").getInt(navBox)
                  + field(navBox.getClass(), "h").getInt(navBox),
          "default effects panel clears category navigator");
      Object savedClientStyle =
          r.modules().require("clickgui").module().settings().get("client-style").get();
      Object savedLogoDesign = value(r, "logo-design");
      Object savedDetails = value(r, "watermark-info");
      Class<?> logoType = savedLogoDesign.getClass();
      try {
        for (String style : new String[] {"ADJUST", "RAVEN"}) {
          set(
              r,
              "clickgui",
              "client-style",
              Enum.valueOf((Class) savedClientStyle.getClass(), style));
          for (String design : new String[] {"EMBLEM", "EMBLEM_AND_NAME"}) {
            set(r, "hud", "logo-design", Enum.valueOf((Class) logoType, design));
            set(r, "hud", "watermark-info", false);
            refresh(update, renderer);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            render.invoke(renderer, width, height, false, null, 0, 0);
            check(
                field(logoBox.getClass(), "h").getInt(logoBox)
                    == 26 + ((Number) value(r, "watermark-padding")).intValue() * 2,
                "24px emblem has matching drag bounds: " + style + "/" + design);
            check(
                field(navBox.getClass(), "y").getInt(navBox)
                    >= field(logoBox.getClass(), "y").getInt(logoBox) + 26,
                "emblem leaves navigation clear: " + style + "/" + design);
            check(
                GL11.glGetError() == GL11.GL_NO_ERROR, "emblem GL clean: " + style + "/" + design);
            capture(
                "hud-logo-"
                    + style.toLowerCase(Locale.ROOT)
                    + "-"
                    + design.toLowerCase(Locale.ROOT)
                    + ".png");
          }
          set(r, "hud", "watermark-info", true);
          refresh(update, renderer);
          render.invoke(renderer, width, height, true, null, 0, 0);
          check(
              field(logoBox.getClass(), "h").getInt(logoBox)
                  == 37 + ((Number) value(r, "watermark-padding")).intValue() * 2,
              "logo detail line expands production bounds: " + style);
        }
      } finally {
        set(r, "clickgui", "client-style", savedClientStyle);
        set(r, "hud", "logo-design", savedLogoDesign);
        set(r, "hud", "watermark-info", savedDetails);
        refresh(update, renderer);
      }
      set(r, "hud", "target", false);
      set(r, "hud", "players", true);
      refresh(update, renderer);
      GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
      render.invoke(renderer, width, height, false, null, 0, 0);
      capture("hud-reference-layout.png");
      captureWorld(game);
      set(r, "hud", "target", true);
      set(r, "hud", "players", false);
      field(first.getClass(), "isDead", "field_70128_L").setBoolean(first, true);
      refresh(update, renderer);
      check(!field(renderer.getClass(), "hasTarget").getBoolean(renderer), "dead target cleared");
      mouseOver.set(game, null);
      refresh(update, renderer);
      check(field(renderer.getClass(), "skin").get(renderer) == null, "target skin cleared");
      for (int[] size : new int[][] {{320, 240}, {427, 240}, {1920, 1080}}) {
        render.invoke(renderer, size[0], size[1], true, null, 0, 0);
        for (Object box :
            ((Map<?, ?>) field(renderer.getClass(), "boxes").get(renderer)).values()) {
          if (!field(box.getClass(), "visible").getBoolean(box)) continue;
          int x = field(box.getClass(), "x").getInt(box),
              y = field(box.getClass(), "y").getInt(box);
          check(
              x >= 0
                  && y >= 0
                  && x + field(box.getClass(), "w").getInt(box) <= size[0]
                  && y + field(box.getClass(), "h").getInt(box) <= size[1],
              "panel clamped at " + size[0]);
        }
      }
      TabGuiProbe.run(r, game, hud);
      r.commands().execute("hud edit", line -> {});
      Object editor = screen.get(game);
      check(editor.getClass().getName().endsWith("HudEditor"), ".hud edit opens native editor");
      Method draw =
          method(
              editor.getClass(),
              new Class<?>[] {int.class, int.class, float.class},
              "drawScreen",
              "func_73863_a");
      method(editor.getClass(), "updateScreen", "func_73876_c").invoke(editor);
      GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
      cleanEditor(draw, editor);
      capture("hud-editor.png");
      Object preview = field(editor.getClass(), "preview").get(editor);
      Object watermark = Enum.valueOf((Class) elementType, "WATERMARK");
      Object box = ((Map<?, ?>) field(preview.getClass(), "boxes").get(preview)).get(watermark);
      int x = field(box.getClass(), "x").getInt(box) + 3,
          y = field(box.getClass(), "y").getInt(box) + 3;
      Method click =
          method(
              editor.getClass(),
              new Class<?>[] {int.class, int.class, int.class},
              "mouseClicked",
              "func_73864_a");
      Method release =
          method(
              editor.getClass(),
              new Class<?>[] {int.class, int.class, int.class},
              "mouseReleased",
              "func_146286_b");
      Object toolbar = field(editor.getClass(), "toolbar").get(editor);
      int tx = (int) field(toolbar.getClass(), "x").getFloat(toolbar),
          ty = (int) field(toolbar.getClass(), "y").getFloat(toolbar);
      click.invoke(editor, tx + 4, ty + 4, 0);
      draw.invoke(editor, 84, 14, 0f);
      release.invoke(editor, 84, 14, 0);
      check(
          field(toolbar.getClass(), "x").getFloat(toolbar) == 80
              && field(toolbar.getClass(), "y").getFloat(toolbar) == 10,
          "HUD editor toolbar drags and commits through native settings");
      click.invoke(editor, 84, 14, 1);
      check(
          field(toolbar.getClass(), "collapsed").getBoolean(toolbar),
          "right-click HUD editor collapses controls");
      click.invoke(editor, 84, 14, 1);
      check(
          !field(toolbar.getClass(), "collapsed").getBoolean(toolbar),
          "right-click HUD editor restores controls");
      double old = ((Number) value(r, "watermark-y")).doubleValue();
      click.invoke(editor, x, y, 0);
      draw.invoke(editor, x, y + 90, 0f);
      check(
          ((Number) value(r, "watermark-y")).doubleValue() == old,
          "drag does not write settings every frame");
      release.invoke(editor, x, y + 90, 0);
      check(
          ((Number) value(r, "watermark-y")).doubleValue() != old,
          "drag release persists position");
      cleanEditor(draw, editor);
      y = field(box.getClass(), "y").getInt(box) + 3;
      click.invoke(editor, x, y, 1);
      check(
          Boolean.TRUE.equals(value(r, "watermark")),
          "right click opens customization without hiding panel");
      check(
          field(editor.getClass(), "customization").get(editor) != null,
          "visible element has native customization");
      cleanEditor(draw, editor);
      Object logoCustomization = field(editor.getClass(), "customization").get(editor);
      field(logoCustomization.getClass(), "section").setInt(logoCustomization, 2);
      cleanEditor(draw, editor);
      Object logoControl = null;
      for (Object candidate :
          (List<?>) field(logoCustomization.getClass(), "controls").get(logoCustomization)) {
        Setting<?> setting = (Setting<?>) field(candidate.getClass(), "setting").get(candidate);
        if (setting.id().equals("logo-design")) logoControl = candidate;
      }
      check(logoControl != null, "Logo popup exposes the native design selector");
      int logoX = (int) field(logoControl.getClass(), "x").getFloat(logoControl) + 4;
      int logoY = (int) field(logoControl.getClass(), "y").getFloat(logoControl) + 4;
      click.invoke(editor, logoX, logoY, 0);
      release.invoke(editor, logoX, logoY, 0);
      cleanEditor(draw, editor);
      int optionY =
          (int) field(logoControl.getClass(), "y").getFloat(logoControl)
              + (int) field(logoControl.getClass(), "headerHeight").getFloat(logoControl)
              + ((Float)
                      method(logoControl.getClass(), new Class<?>[] {int.class}, "optionHeight")
                          .invoke(logoControl, 0))
                  .intValue()
              + 4;
      click.invoke(editor, logoX, optionY, 0);
      release.invoke(editor, logoX, optionY, 0);
      check(
          value(r, "logo-design").toString().equals("EMBLEM"),
          "native Logo dropdown selects emblem");
      cleanEditor(draw, editor);
      check(
          field(box.getClass(), "h").getInt(box)
              == 26 + ((Number) value(r, "watermark-padding")).intValue() * 2,
          "editor preview matches emblem bounds immediately");
      capture("hud-logo-editor-option.png");
      set(
          r,
          "hud",
          "logo-design",
          Enum.valueOf((Class) value(r, "logo-design").getClass(), "TEXT"));
      click.invoke(editor, 1, 1, 0);
      check(
          field(editor.getClass(), "customization").get(editor) == null,
          "clicking outside element customization closes it");
      set(r, "hud", "watermark", false);
      check(
          Boolean.TRUE.equals(value(r, "tab_gui")),
          "hiding Logo preserves independent Tab GUI visibility");
      cleanEditor(draw, editor);
      java.util.List<?> editorButtons =
          (java.util.List<?>) field(editor.getClass(), "buttonList", "field_146292_n").get(editor);
      check(editorButtons.size() == 2, "toolbar contains only HUD and Elements");
      for (Object candidate : editorButtons) {
        int id = field(candidate.getClass(), "id", "field_146127_k").getInt(candidate);
        check(id == 0 || id == 3, "Basic/Custom, Reset and Done buttons removed");
      }
      Object elementsButton = null;
      for (Object candidate : editorButtons)
        if (field(candidate.getClass(), "id", "field_146127_k").getInt(candidate) == 3)
          elementsButton = candidate;
      click.invoke(
          editor,
          field(elementsButton.getClass(), "xPosition", "field_146128_h").getInt(elementsButton)
              + 4,
          field(elementsButton.getClass(), "yPosition", "field_146129_i").getInt(elementsButton)
              + 4,
          0);
      cleanEditor(draw, editor);
      click.invoke(
          editor,
          field(editor.getClass(), "elementsX").getInt(editor) + 8,
          field(editor.getClass(), "elementsY").getInt(editor) + 18,
          0);
      check(Boolean.TRUE.equals(value(r, "watermark")), "hidden preview can be restored");
      GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
      cleanEditor(draw, editor);
      capture("hud-elements.png");
      click.invoke(
          editor,
          field(editor.getClass(), "elementsX").getInt(editor) + 8,
          field(editor.getClass(), "elementsY").getInt(editor) + 13 + 2 * 16 + 4,
          1);
      Object customization = field(editor.getClass(), "customization").get(editor);
      check(customization != null, "right-click Options array-list entry opens customization");
      cleanEditor(draw, editor);
      capture("hud-arraylist-options.png");
      java.util.List<?> optionControls =
          (java.util.List<?>) field(customization.getClass(), "controls").get(customization);
      check(
          optionControls.size() > 20,
          "array list exposes independent text, panel, blur and content options");
      field(customization.getClass(), "section").setInt(customization, 2);
      cleanEditor(draw, editor);
      check(
          !field(editor.getClass(), "elementsMenu").getBoolean(editor),
          "only one HUD option surface is visible");
      Object spacing = null;
      for (Object control : optionControls) {
        Setting<?> setting = (Setting<?>) field(control.getClass(), "setting").get(control);
        if (setting.id().equals("module-spacing")) spacing = control;
      }
      check(spacing != null, "row-spacing setting retained");
      click.invoke(
          editor,
          (int) field(spacing.getClass(), "x").getFloat(spacing) + 6,
          (int) field(spacing.getClass(), "y").getFloat(spacing) + 3,
          1);
      check(
          field(customization.getClass(), "field").get(customization) != null,
          "compact numeric setting opens its inline editor");
      method(customization.getClass(), new Class<?>[] {int.class}, "wheel")
          .invoke(customization, -1);
      check(
          field(customization.getClass(), "field").get(customization) == null,
          "scroll commits and retires inline edit");
      click.invoke(editor, 1, 1, 0);
      setParsed(r, "module-sort", "ALPHABETICAL");
      setParsed(r, "module-alignment", "LEFT");
      set(r, "hud", "module-spacing", 4);
      method(preview.getClass(), "updateModules").invoke(preview);
      cleanEditor(draw, editor);
      Object modulesElement = Enum.valueOf((Class) elementType, "MODULES");
      Object modulesBox =
          ((Map<?, ?>) field(preview.getClass(), "boxes").get(preview)).get(modulesElement);
      java.util.List<?> moduleRows =
          (java.util.List<?>) field(preview.getClass(), "modules").get(preview);
      int maxWidth = 0;
      String previousLabel = "";
      for (Object row : moduleRows) {
        maxWidth =
            Math.max(
                maxWidth,
                field(row.getClass(), "width").getInt(row)
                    + ((Number) value(r, "module-padding")).intValue() * 2);
        String label = (String) field(row.getClass(), "label").get(row);
        check(previousLabel.compareToIgnoreCase(label) <= 0, "alphabetical array-list order");
        previousLabel = label;
      }
      check(
          field(modulesBox.getClass(), "w").getInt(modulesBox) == Math.min(width - 4, maxWidth),
          "alphabetical list bounds use widest row");
      check(
          field(modulesBox.getClass(), "h").getInt(modulesBox)
              == Math.min(
                  height - 4,
                  Math.max(1, moduleRows.size())
                      * (Integer) method(preview.getClass(), "moduleRowHeight").invoke(preview)),
          "spacing expands measured drag bounds");
      cleanEditor(draw, editor);
      capture("hud-arraylist-custom.png");
      cleanEditor(draw, editor);
      check(
          value(r, "module-spacing").equals(4),
          "layout settings remain active without a mode switch");
      Object savedFont = r.modules().require("clickgui").module().settings().get("font").get();
      try {
        for (String choice : new String[] {"MINECRAFT", "SANS", "MONO"}) {
          Setting<?> fontSetting = r.modules().require("clickgui").module().settings().get("font");
          set(r, "clickgui", "font", fontSetting.parse(choice));
          set(r, "hud", "module-spacing", 0);
          cleanEditor(draw, editor);
          int compactRow = (Integer) method(preview.getClass(), "moduleRowHeight").invoke(preview);
          check(compactRow >= 9 && compactRow <= 16, "compact Array list rows: " + choice);
          capture("hud-arraylist-spacing-" + choice.toLowerCase(java.util.Locale.ROOT) + ".png");
          set(r, "hud", "module-spacing", 4);
          cleanEditor(draw, editor);
          check(
              (Integer) method(preview.getClass(), "moduleRowHeight").invoke(preview)
                  == compactRow + 4,
              "Array list spacing applies uniformly: " + choice);
          check(
              field(modulesBox.getClass(), "h").getInt(modulesBox)
                  == Math.min(height - 4, Math.max(1, moduleRows.size()) * (compactRow + 4)),
              "Array list drag bounds follow current font: " + choice);
        }
      } finally {
        set(r, "clickgui", "font", savedFont);
      }
      r.commands().execute("hud reset", line -> {});
      check(((Number) value(r, "watermark-y")).doubleValue() == 0, "reset restores anchors");
      display.invoke(game, new Object[] {null});
      check(
          field(editor.getClass(), "preview").get(editor) == null,
          "editor releases snapshots on close");
      r.modules().setEnabled("hud", false);
      check(field(hud.getClass(), "renderer").get(hud) == null, "disable drops all HUD state");
      Object brandOwner = field(renderer.getClass(), "brand").get(renderer);
      for (String artwork : new String[] {"emblem", "name"}) {
        Object asset = field(brandOwner.getClass(), artwork).get(brandOwner);
        check(
            field(asset.getClass(), "texture").get(asset) == null,
            "deactivation releases brand texture: " + artwork);
      }
      check(
          field(font.getClass(), "closed").getBoolean(font),
          "deactivation closes the HUD font owner");
      Object fonts = field(font.getClass(), "fonts").get(font);
      check(
          ((Map<?, ?>) field(fonts.getClass(), "atlases").get(fonts)).isEmpty(),
          "deactivation deletes HUD font atlas textures");
      r.modules().setEnabled("hud", true);
      Object fresh = field(hud.getClass(), "renderer").get(hud);
      check(
          fresh != renderer && ((Deque<?>) field(fresh.getClass(), "notices").get(fresh)).isEmpty(),
          "reactivation seeds state without stale notifications");
      for (String id : visuals) r.modules().setEnabled(id, false);
      r.modules().setEnabled("hud", false);
      check(r.clientEvents().subscriptionCount() == baseline, "HUD subscriptions detach");
      r.commands().execute("gui", line -> {});
      Object menu = screen.get(game);
      Object oldSearch = r.modules().require("clickgui").module().settings().get("search").get();
      Object oldScale = r.modules().require("clickgui").module().settings().get("scale").get();
      Field menuWidth = field(menu.getClass(), "width", "field_146294_l");
      int oldWidth = menuWidth.getInt(menu);
      try {
        set(r, "clickgui", "search", true);
        set(r, "clickgui", "scale", 1.5);
        menuWidth.setInt(menu, 320);
        method(menu.getClass(), new Class<?>[] {boolean.class}, "geometry").invoke(menu, false);
        check(
            field(menu.getClass(), "viewWidth").getFloat(menu) >= 269.9f,
            "small enlarged menu keeps search and HUD button separate");
      } finally {
        menuWidth.setInt(menu, oldWidth);
        set(r, "clickgui", "search", oldSearch);
        set(r, "clickgui", "scale", oldScale);
        method(menu.getClass(), new Class<?>[] {boolean.class}, "geometry").invoke(menu, false);
      }
      float vw = field(menu.getClass(), "viewWidth").getFloat(menu);
      Object hudTab = field(menu.getClass(), "hudTab").get(menu);
      method(menu.getClass(), new Class<?>[] {float.class, float.class, int.class}, "click")
          .invoke(
              menu,
              field(hudTab.getClass(), "x").getFloat(hudTab) + 4,
              field(hudTab.getClass(), "y").getFloat(hudTab) + 4,
              0);
      method(menu.getClass(), new Class<?>[0], "release").invoke(menu);
      check(
          screen.get(game).getClass().getName().endsWith("HudEditor"),
          "GUI HUD button opens editor");
      Object fromMenu = screen.get(game);
      method(
              fromMenu.getClass(),
              new Class<?>[] {char.class, int.class},
              "keyTyped",
              "func_73869_a")
          .invoke(fromMenu, '\0', 1);
      check(screen.get(game) == menu, "editor Escape returns to parent GUI");
      UiThemeProbe.run(r, game, hud);
      GuiRegressionProbe.run(r, game, hud);
      ClientStyleProbe.run(r, game, hud);
      AccountHookProbe.world(r, game);
      OverlayHookProbe.run(r, game, hud);
      HudChatProbe.run(r, game, hud);
    } finally {
      display.invoke(game, new Object[] {oldScreen});
      mouseOver.set(game, oldMouse);
      System.arraycopy(savedArmor, 0, armor, 0, armor.length);
      potionMap.clear();
      potionMap.putAll(savedPotions);
      for (Object team : teams)
        method(
                scoreboard.getClass(),
                new Class<?>[] {team.getClass()},
                "removeTeam",
                "func_96511_d")
            .invoke(scoreboard, team);
      for (int id : ids)
        method(
                world.getClass(),
                new Class<?>[] {int.class},
                "removeEntityFromWorld",
                "func_73028_b")
            .invoke(world, id);
      r.modules().setEnabled("hud", false);
      for (String id : visuals) r.modules().setEnabled(id, false);
      for (Map.Entry<String, Object> entry : originals.entrySet())
        set(r, "hud", entry.getKey(), entry.getValue());
    }
    System.out.println("[PERIHELION_SMOKE] PRODUCTION_HUD PASS checks=" + checks);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void set(ClientRuntime r, String id, String key, Object value) {
    Setting setting = r.modules().require(id).module().settings().get(key);
    r.modules().setSetting(id, setting, value);
  }

  private static void setParsed(ClientRuntime r, String key, String text) {
    Setting<?> setting = r.modules().require("hud").module().settings().get(key);
    set(r, "hud", key, setting.parse(text));
  }

  private static Object value(ClientRuntime r, String key) {
    return r.modules().require("hud").module().settings().get(key).get();
  }

  private static void refresh(Method update, Object renderer) throws Exception {
    for (int i = 0; i < 4; i++) update.invoke(renderer, false);
  }

  private static void captureWorld(Object game) throws Exception {
    Object settings = field(game.getClass(), "gameSettings", "field_71474_y").get(game);
    Field chatVisibility = field(settings.getClass(), "chatVisibility", "field_74343_n");
    Object oldVisibility = chatVisibility.get(settings);
    Object framebuffer = method(game.getClass(), "getFramebuffer", "func_147110_a").invoke(game);
    int oldFramebuffer = GL11.glGetInteger(0x8ca6); // GL_FRAMEBUFFER_BINDING
    try {
      chatVisibility.set(settings, Enum.valueOf((Class) oldVisibility.getClass(), "HIDDEN"));
      method(framebuffer.getClass(), "framebufferClear", "func_147614_f").invoke(framebuffer);
      method(
              framebuffer.getClass(),
              new Class<?>[] {boolean.class},
              "bindFramebuffer",
              "func_147610_a")
          .invoke(framebuffer, true);
      Object entityRenderer = field(game.getClass(), "entityRenderer", "field_71460_t").get(game);
      method(
              entityRenderer.getClass(),
              new Class<?>[] {float.class, long.class},
              "updateCameraAndRender",
              "func_181560_a")
          .invoke(entityRenderer, 0f, System.nanoTime());
      capture("hud-in-world.png");
    } finally {
      chatVisibility.set(settings, oldVisibility);
      method(
              Class.forName("net.minecraft.client.renderer.OpenGlHelper"),
              new Class<?>[] {int.class, int.class},
              "glBindFramebuffer",
              "func_153171_g")
          .invoke(null, 0x8d40, oldFramebuffer); // GL_FRAMEBUFFER
    }
  }

  static void capture(String name) throws Exception {
    method(GuiHookProbe.class, new Class<?>[] {String.class}, "capture").invoke(null, name);
  }

  private static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
    checks++;
  }
}
