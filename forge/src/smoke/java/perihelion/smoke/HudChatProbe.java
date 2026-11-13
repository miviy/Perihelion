package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;

import java.lang.reflect.*;
import java.util.*;
import org.lwjgl.opengl.GL11;
import perihelion.core.context.ConnectionContext;
import perihelion.core.control.InputKey;
import perihelion.core.event.CancellableEvent;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.event.PlayerControlEvent;

/** Actual GuiChat lifecycle and Forge drawing route, with local pointer/keyboard regressions. */
public final class HudChatProbe {
  private static int checks;
  private static final String EVENT = "perihelion.integration.minecraft189.event.ChatHudEvent";

  public static void run(ClientRuntime r, Object game, Object hud) throws Exception {
    checks = 0;
    Field current = field(game.getClass(), "currentScreen", "field_71462_r");
    Object parent = current.get(game);
    Class<?> chatType = Class.forName("net.minecraft.client.gui.GuiChat");
    Method display =
        method(
            game.getClass(),
            new Class<?>[] {Class.forName("net.minecraft.client.gui.GuiScreen")},
            "displayGuiScreen",
            "func_147108_a");
    boolean enabled = r.modules().require("hud").requestedEnabled();
    boolean oldFreecam = r.modules().require("freecam").requestedEnabled();
    Object oldColumns = value(r, "player-columns"),
        oldPlayers = value(r, "players"),
        oldHide = value(r, "hide-screens"),
        oldLimit = value(r, "player-limit");
    try {
      set(r, "hud", "hide-screens", true);
      set(r, "hud", "players", true);
      r.modules().setEnabled("hud", true);
      Object gui = chatType.newInstance();
      display.invoke(game, gui);
      Object interaction = field(hud.getClass(), "chat").get(hud);
      check(
          field(interaction.getClass(), "screen").get(interaction) == gui,
          "native chat init attaches the HUD session");
      // OptiFine replaces vanilla chat with GuiChatOF on its first render, before normal UI use.
      captureWorld(game, "hud-chat-in-world.png");
      gui = current.get(game);
      check(
          chatType.isInstance(gui)
              && field(interaction.getClass(), "screen").get(interaction) == gui,
          "HUD attaches to the actual chat screen after the native startup frame");
      Object input = field(chatType, "inputField", "field_146415_a").get(gui);
      method(input.getClass(), new Class<?>[] {String.class}, "setText", "func_146180_a")
          .invoke(input, "keep my chat draft");
      draw(game, gui, 0, 0);
      Object renderer = field(hud.getClass(), "renderer").get(hud);
      Map<?, ?> boxes = (Map<?, ?>) field(renderer.getClass(), "boxes").get(renderer);
      Object watermark = box(boxes, "WATERMARK"), board = box(boxes, "PLAYERS");
      check(
          field(watermark.getClass(), "visible").getBoolean(watermark),
          "HUD remains visible in chat with Hide in menus on");
      check(
          field(board.getClass(), "visible").getBoolean(board),
          "player board remains visible in chat");
      check(!r.input().isAllowed(), "chat does not enable gameplay bindings");
      int width = field(gui.getClass(), "width", "field_146294_l").getInt(gui),
          height = field(gui.getClass(), "height", "field_146295_m").getInt(gui);
      int x = field(watermark.getClass(), "x").getInt(watermark) + 3,
          y = field(watermark.getClass(), "y").getInt(watermark) + 3;
      double oldY = ((Number) value(r, "watermark-y")).doubleValue();
      check(mouse(r, gui, x, y, 0, true, 0), "HUD drag press consumes only its own pointer event");
      draw(game, gui, x + 35, y + 50);
      check(
          ((Number) value(r, "watermark-y")).doubleValue() == oldY,
          "chat dragging does not save on each frame");
      check(mouse(r, gui, x + 35, y + 50, 0, false, 0), "HUD drag release is handled");
      check(
          ((Number) value(r, "watermark-y")).doubleValue() != oldY,
          "chat release saves normalized HUD position");
      double moved = ((Number) value(r, "watermark-y")).doubleValue();
      check(
          !mouse(r, gui, x + 35, y + 50, 0, false, 0),
          "repeated release cannot reuse a finished drag");
      check(
          ((Number) value(r, "watermark-y")).doubleValue() == moved,
          "repeated release leaves the saved position intact");
      check(
          !mouse(r, gui, 5, height - 7, 0, true, 0),
          "chat input line is never intercepted by an overlapping HUD");
      check(
          !mouse(r, gui, width - 1, 25, 0, true, 0),
          "clicks outside the HUD fall through to vanilla chat");
      check(!mouse(r, gui, width - 1, 25, -1, false, 120), "ordinary chat scrolling is preserved");
      draw(game, gui, 0, 0);
      int bx = field(board.getClass(), "x").getInt(board),
          by = field(board.getClass(), "y").getInt(board);
      check(!mouse(r, gui, bx + 4, by + 4, 1, true, 0), "board columns stay in GUI settings");
      Object popup = field(interaction.getClass(), "popup").get(interaction);
      boolean ping = contains((Set<?>) value(r, "player-columns"), "PING");
      Set nextColumns = EnumSet.copyOf((Set) value(r, "player-columns"));
      Class<? extends Enum> columnType =
          (Class<? extends Enum>)
              Class.forName("perihelion.integration.minecraft189.hud.HudModule$PlayerColumn");
      Object pingColumn = Enum.valueOf(columnType, "PING");
      if (ping) nextColumns.remove(pingColumn);
      else nextColumns.add(pingColumn);
      set(r, "hud", "player-columns", nextColumns);
      check(
          contains((Set<?>) value(r, "player-columns"), "PING") != ping,
          "columns use the GUI's typed HUD setting");
      draw(game, gui, 0, 0);
      check(mouse(r, gui, bx + 15, by + 25, 1, true, 0), "right-click second player opens actions");
      Object selected = field(interaction.getClass(), "selected").get(interaction);
      UUID selectedId = (UUID) field(selected.getClass(), "id").get(selected);
      List<?> rows = (List<?>) method(interaction.getClass(), "menuRows").invoke(interaction);
      check(
          rows.contains("Tags")
              && rows.contains("Analysis")
              && rows.contains("Hypixel")
              && rows.contains("[-] Whitelist")
              && rows.contains("[-] Target")
              && rows.contains("Spectate")
              && rows.contains("Violations")
              && rows.contains("Statistics"),
          "clicked player menu contains the requested actions");
      check(Boolean.TRUE.equals(value(r, "players")), "right-click never hides the board");
      draw(game, gui, 0, 0);
      captureWorld(game, "hud-chat-player-actions-in-world.png");
      int px = field(popup.getClass(), "x").getInt(popup),
          py = field(popup.getClass(), "y").getInt(popup);
      check(
          mouse(r, gui, px + 8, py + 76 + 3 * 12 + 2, 0, true, 0), "Pin action consumes its event");
      mouse(r, gui, px + 8, py + 76 + 2 * 12 + 2, 0, true, 0);
      check(
          (Boolean)
              method(renderer.getClass(), new Class<?>[] {UUID.class}, "tagged")
                  .invoke(renderer, selectedId),
          "Target tag marks the clicked UUID without changing gameplay target");
      mouse(r, gui, px + 8, py + 76 + 2 * 12 + 2, 0, true, 0);
      check(
          !(Boolean)
              method(renderer.getClass(), new Class<?>[] {UUID.class}, "tagged")
                  .invoke(renderer, selectedId),
          "Target tag toggles off");
      Object friendSetting = r.modules().require("friends").module().settings().get("names");
      String oldFriends = ((Setting<?>) friendSetting).get().toString();
      mouse(r, gui, px + 8, py + 76 + 12 + 2, 0, true, 0);
      check(
          !((Setting<?>) friendSetting).get().toString().equals(oldFriends),
          "Whitelist edits the native Friends setting");
      mouse(r, gui, px + 8, py + 76 + 12 + 2, 0, true, 0);
      check(
          ((Setting<?>) friendSetting).get().toString().equals(oldFriends),
          "Whitelist toggles back without a separate list");
      mouse(r, gui, px + 8, py + 76 + 6 * 12 + 2, 0, true, 0);
      check(
          ((List<?>) method(interaction.getClass(), "menuRows").invoke(interaction))
              .contains("Violation data unavailable"),
          "Violations identifies missing provider without invented detections");
      mouse(r, gui, px + 8, py + 76 + 2, 0, true, 0);
      List<?> players = (List<?>) field(renderer.getClass(), "players").get(renderer);
      check(
          selectedId.equals(field(players.get(0).getClass(), "id").get(players.get(0))),
          "pinned player sorts before nearer players");
      check(
          !mouse(r, gui, px + 8, py + 76 + 3 * 12 + 2, 0, false, 0),
          "button release does not repeat Pin action");
      check(
          (Boolean)
              method(renderer.getClass(), new Class<?>[] {UUID.class}, "pinned")
                  .invoke(renderer, selectedId),
          "release preserves pin");
      set(r, "hud", "player-limit", 1);
      method(renderer.getClass(), new Class<?>[] {boolean.class}, "updatePlayers")
          .invoke(renderer, false);
      check(
          players.size() == 1
              && selectedId.equals(field(players.get(0).getClass(), "id").get(players.get(0))),
          "pin priority applies before limiting rows");
      set(r, "hud", "player-limit", oldLimit);
      method(renderer.getClass(), new Class<?>[] {boolean.class}, "updatePlayers")
          .invoke(renderer, false);
      Object previousView =
          method(game.getClass(), "getRenderViewEntity", "func_175606_aa").invoke(game);
      Object body = field(game.getClass(), "thePlayer", "field_71439_g").get(game);
      double bodyX = field(body.getClass(), "posX", "field_70165_t").getDouble(body),
          bodyY = field(body.getClass(), "posY", "field_70163_u").getDouble(body),
          bodyZ = field(body.getClass(), "posZ", "field_70161_v").getDouble(body);
      check(
          mouse(r, gui, px + 8, py + 76 + 5 * 12 + 2, 0, true, 0),
          "Spectate action starts native Freecam");
      Object camera = method(game.getClass(), "getRenderViewEntity", "func_175606_aa").invoke(game);
      check(
          camera != body
              && camera != previousView
              && r.modules().require("freecam").requestedEnabled(),
          "Spectate uses a detached camera");
      Object spectator = r.modules().require("freecam").module();
      check(
          selectedId.equals(method(spectator.getClass(), "spectating").invoke(spectator)),
          "spectating target is the clicked UUID");
      check(
          field(body.getClass(), "posX", "field_70165_t").getDouble(body) == bodyX
              && field(body.getClass(), "posY", "field_70163_u").getDouble(body) == bodyY
              && field(body.getClass(), "posZ", "field_70161_v").getDouble(body) == bodyZ,
          "spectating preserves body position");
      Object world = field(game.getClass(), "theWorld", "field_71441_e").get(game);
      Object target =
          method(
                  world.getClass(),
                  new Class<?>[] {UUID.class},
                  "getPlayerEntityByUUID",
                  "func_152378_a")
              .invoke(world, selectedId);
      double dx =
          field(camera.getClass(), "posX", "field_70165_t").getDouble(camera)
              - field(target.getClass(), "posX", "field_70165_t").getDouble(target);
      double dz =
          field(camera.getClass(), "posZ", "field_70161_v").getDouble(camera)
              - field(target.getClass(), "posZ", "field_70161_v").getDouble(target);
      check(Math.abs(Math.hypot(dx, dz) - 3) < .001, "camera starts beside clicked player");
      r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      for (InputKey inputKey : InputKey.values())
        check(
            r.controls().key(inputKey).isPresent() && !r.controls().key(inputKey).get(),
            "spectate suppresses body input " + inputKey);
      draw(game, gui, 0, 0);
      py = field(popup.getClass(), "y").getInt(popup);
      mouse(r, gui, px + 8, py + 76 + 5 * 12 + 2, 0, true, 0);
      check(
          method(game.getClass(), "getRenderViewEntity", "func_175606_aa").invoke(game)
                  == previousView
              && !r.modules().require("freecam").requestedEnabled(),
          "Stop spectating restores the previous camera");
      method(spectator.getClass(), new Class<?>[] {UUID.class}, "spectate")
          .invoke(spectator, selectedId);
      Field dead = field(target.getClass(), "isDead", "field_70128_L");
      dead.setBoolean(target, true);
      try {
        r.clientEvents().post(PlayerControlEvent.BEFORE_INPUT);
      } finally {
        dead.setBoolean(target, false);
      }
      check(
          !r.modules().require("freecam").requestedEnabled()
              && method(game.getClass(), "getRenderViewEntity", "func_175606_aa").invoke(game)
                  == previousView,
          "target loss retires spectating and restores camera");
      check(
          !(Boolean)
              method(spectator.getClass(), new Class<?>[] {UUID.class}, "spectate")
                  .invoke(spectator, UUID.randomUUID()),
          "stale player UUID cannot activate a camera");
      draw(game, gui, 0, 0);
      py = field(popup.getClass(), "y").getInt(popup);
      mouse(r, gui, px + 8, py + 76 + 10 * 12 + 2, 0, true, 0);
      check(
          field(popup.getClass(), "visible").getBoolean(popup)
              && field(interaction.getClass(), "message")
                  .get(interaction)
                  .toString()
                  .contains("requires Hypixel"),
          "report shortcut is unavailable on local test worlds");
      Object lookup = field(hud.getClass(), "stats").get(hud);
      Class<?> statsType = Class.forName("perihelion.integration.minecraft189.hud.HypixelStats");
      String response =
          "{\"success\":true,\"player\":{\"uuid\":\""
              + selectedId
              + "\",\"stats\":{\"Bedwars\":{\"kills_bedwars\":20,\"deaths_bedwars\":4,\"wins_bedwars\":9,\"losses_bedwars\":3}}}}";
      Object stats =
          method(statsType, new Class<?>[] {UUID.class, String.class}, "parse")
              .invoke(null, selectedId, response);
      Class<?>
          resultType =
              Class.forName("perihelion.integration.minecraft189.hud.HypixelLookup$Result"),
          entryType = Class.forName("perihelion.integration.minecraft189.hud.HypixelLookup$Entry");
      Constructor<?> resultConstructor =
          resultType.getDeclaredConstructor(statsType, String.class, long.class);
      resultConstructor.setAccessible(true);
      Object result = resultConstructor.newInstance(stats, "", 0L);
      Constructor<?> entryConstructor = entryType.getDeclaredConstructor(resultType, long.class);
      entryConstructor.setAccessible(true);
      ((Map) field(lookup.getClass(), "cache").get(lookup))
          .put(
              selectedId,
              entryConstructor.newInstance(result, System.currentTimeMillis() + 300000L));
      mouse(r, gui, px + 8, py + 76 + 9 * 12 + 2, 0, true, 0);
      rows = (List<?>) method(interaction.getClass(), "menuRows").invoke(interaction);
      check(
          rows.stream().anyMatch(v -> v.toString().equals("K/D: 5.00"))
              && rows.stream().anyMatch(v -> v.toString().equals("W/L: 3.00")),
          "stats menu renders cached API fixture values");
      check(
          rows.stream().anyMatch(v -> v.toString().equals("Win streak: --")),
          "missing API fields stay unavailable");
      draw(game, gui, 0, 0);
      captureWorld(game, "hud-chat-hypixel-stats-in-world.png");
      check(key(r, gui, 1), "Escape closes popup before chat");
      check(current.get(game) == gui, "popup Escape preserves chat");
      draw(game, gui, 0, 0);
      bx = field(board.getClass(), "x").getInt(board);
      by = field(board.getClass(), "y").getInt(board);
      mouse(r, gui, bx + 15, by + 14, 1, true, 0);
      px = field(popup.getClass(), "x").getInt(popup);
      py = field(popup.getClass(), "y").getInt(popup);
      mouse(r, gui, px + 8, py + 76 + 7 * 12 + 2, 0, true, 0);
      rows = (List<?>) method(interaction.getClass(), "menuRows").invoke(interaction);
      check(
          rows.stream().anyMatch(v -> v.toString().startsWith("Health:"))
              && rows.stream().anyMatch(v -> v.toString().startsWith("Ping:")),
          "native player details remain accessible");
      draw(game, gui, 0, 0);
      check(
          mouse(
              r,
              gui,
              field(popup.getClass(), "x").getInt(popup) + 4,
              field(popup.getClass(), "y").getInt(popup) + 25,
              -1,
              false,
              -120),
          "popup scrolling does not scroll chat underneath");
      check(
          method(input.getClass(), "getText", "func_146179_b")
              .invoke(input)
              .equals("keep my chat draft"),
          "dragging and menus preserve chat text");
      key(r, gui, 1);
      draw(game, gui, 0, 0);
      x = field(watermark.getClass(), "x").getInt(watermark) + 3;
      y = field(watermark.getClass(), "y").getInt(watermark) + 3;
      mouse(r, gui, x, y, 0, true, 0);
      draw(game, gui, x + 10, y + 20);
      double beforeClose = ((Number) value(r, "watermark-y")).doubleValue();
      display.invoke(game, new Object[] {null});
      check(
          ((Number) value(r, "watermark-y")).doubleValue() != beforeClose,
          "closing chat mid-drag saves the last preview position");
      check(
          field(interaction.getClass(), "screen").get(interaction) == null,
          "native chat close retires its session");
      for (String kind : new String[] {"KeyboardInputEvent", "MouseInputEvent"}) {
        r.input().setAllowed(true, Collections.emptyList());
        Object event =
            Class.forName("net.minecraftforge.client.event.GuiScreenEvent$" + kind + "$Post")
                .getConstructor(Class.forName("net.minecraft.client.gui.GuiScreen"))
                .newInstance(gui);
        Object bus =
            Class.forName("net.minecraftforge.common.MinecraftForge")
                .getField("EVENT_BUS")
                .get(null);
        method(
                bus.getClass(),
                new Class<?>[] {Class.forName("net.minecraftforge.fml.common.eventhandler.Event")},
                "post")
            .invoke(bus, event);
        check(
            !r.input().isAllowed(), "a closing GUI's " + kind + " cannot turn into gameplay input");
      }
      display.invoke(game, gui);
      draw(game, gui, 0, 0);
      x = field(watermark.getClass(), "x").getInt(watermark) + 3;
      y = field(watermark.getClass(), "y").getInt(watermark) + 3;
      mouse(r, gui, x, y, 0, true, 0);
      draw(game, gui, x + 10, y + 20);
      double beforeResize = ((Number) value(r, "watermark-y")).doubleValue();
      method(gui.getClass(), "initGui", "func_73866_w_").invoke(gui);
      Object drag = field(interaction.getClass(), "drag").get(interaction);
      check(
          field(drag.getClass(), "element").get(drag) == null,
          "resize/init cancels an unfinished chat drag");
      display.invoke(game, new Object[] {null});
      check(
          ((Number) value(r, "watermark-y")).doubleValue() == beforeResize,
          "resize cannot persist stale screen coordinates");
      display.invoke(game, gui);
      draw(game, gui, 0, 0);
      x = field(watermark.getClass(), "x").getInt(watermark) + 3;
      y = field(watermark.getClass(), "y").getInt(watermark) + 3;
      mouse(r, gui, x, y, 0, true, 0);
      draw(game, gui, x + 15, y + 20);
      r.commands().execute("hud reset", line -> {});
      display.invoke(game, new Object[] {null});
      check(
          ((Number) value(r, "watermark-y")).doubleValue() == 0,
          "layout reset cannot be overwritten by closing an earlier drag");
      display.invoke(game, gui);
      draw(game, gui, 0, 0);
      x = field(watermark.getClass(), "x").getInt(watermark) + 3;
      y = field(watermark.getClass(), "y").getInt(watermark) + 3;
      mouse(r, gui, x, y, 0, true, 0);
      draw(game, gui, x + 25, y + 25);
      double beforeDisable = ((Number) value(r, "watermark-y")).doubleValue();
      r.modules().setEnabled("hud", false);
      check(
          field(hud.getClass(), "chat").get(hud) == null
              && field(drag.getClass(), "element").get(drag) == null,
          "HUD deactivation releases chat and drag state");
      display.invoke(game, new Object[] {null});
      check(
          ((Number) value(r, "watermark-y")).doubleValue() == beforeDisable,
          "a retired chat drag cannot save after HUD deactivation");
      r.modules().setEnabled("hud", true);
      display.invoke(game, gui);
      draw(game, gui, 0, 0);
      Object fresh = field(hud.getClass(), "chat").get(hud);
      check(
          fresh != interaction
              && !field(field(fresh.getClass(), "popup").get(fresh).getClass(), "visible")
                  .getBoolean(field(fresh.getClass(), "popup").get(fresh)),
          "reenabling creates fresh chat state without an old menu");
      Object freshRenderer = field(hud.getClass(), "renderer").get(hud);
      check(
          !(Boolean)
              method(freshRenderer.getClass(), new Class<?>[] {UUID.class}, "pinned")
                  .invoke(freshRenderer, selectedId),
          "pins retire with the HUD activation");
      method(spectator.getClass(), new Class<?>[] {UUID.class}, "spectate")
          .invoke(spectator, selectedId);
      Object retiringLookup = field(hud.getClass(), "stats").get(hud);
      ConnectionContext connection = r.connection();
      r.updateWorld(false, ConnectionContext.DISCONNECTED, true);
      check(
          method(game.getClass(), "getRenderViewEntity", "func_175606_aa").invoke(game)
              == previousView,
          "world boundary restores the detached camera");
      check(
          field(retiringLookup.getClass(), "closed").getBoolean(retiringLookup),
          "world boundary closes the stats worker");
      r.updateWorld(true, connection, true);
      check(
          !r.modules().require("freecam").requestedEnabled(),
          "spectating does not reopen Freecam in another world");
      check(
          field(hud.getClass(), "stats").get(hud) != retiringLookup,
          "world boundary starts fresh lookup ownership");
      System.out.println("PERIHELION_SMOKE HUD_CHAT checks=" + checks + " PASS");
    } finally {
      display.invoke(game, new Object[] {null});
      r.modules().setEnabled("freecam", oldFreecam);
      set(r, "hud", "player-columns", oldColumns);
      set(r, "hud", "players", oldPlayers);
      set(r, "hud", "hide-screens", oldHide);
      set(r, "hud", "player-limit", oldLimit);
      r.modules().setEnabled("hud", enabled);
      display.invoke(game, parent);
    }
  }

  private static void draw(Object game, Object gui, int x, int y) throws Exception {
    GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    method(
            gui.getClass(),
            new Class<?>[] {int.class, int.class, float.class},
            "drawScreen",
            "func_73863_a")
        .invoke(gui, x, y, 0f);
    Object event =
        Class.forName("net.minecraftforge.client.event.GuiScreenEvent$DrawScreenEvent$Post")
            .getConstructor(
                Class.forName("net.minecraft.client.gui.GuiScreen"),
                int.class,
                int.class,
                float.class)
            .newInstance(gui, x, y, 0f);
    Object bus =
        Class.forName("net.minecraftforge.common.MinecraftForge").getField("EVENT_BUS").get(null);
    Method snapshot = RenderHookProbe.class.getDeclaredMethod("readState");
    snapshot.setAccessible(true);
    Object before = snapshot.invoke(null);
    method(
            bus.getClass(),
            new Class<?>[] {Class.forName("net.minecraftforge.fml.common.eventhandler.Event")},
            "post")
        .invoke(bus, event);
    check(before.equals(snapshot.invoke(null)), "chat HUD drawing restores its GL state");
  }

  static void captureWorld(Object game, String name) throws Exception {
    Object framebuffer = method(game.getClass(), "getFramebuffer", "func_147110_a").invoke(game);
    int previous = GL11.glGetInteger(0x8ca6);
    try {
      method(framebuffer.getClass(), "framebufferClear", "func_147614_f").invoke(framebuffer);
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
      ProductionHudProbe.capture(name);
    } finally {
      method(
              Class.forName("net.minecraft.client.renderer.OpenGlHelper"),
              new Class<?>[] {int.class, int.class},
              "glBindFramebuffer",
              "func_153171_g")
          .invoke(null, 0x8d40, previous);
    }
  }

  private static boolean mouse(
      ClientRuntime r, Object gui, int x, int y, int button, boolean pressed, int wheel)
      throws Exception {
    Object event =
        method(
                Class.forName(EVENT),
                new Class<?>[] {
                  Class.forName("net.minecraft.client.gui.GuiChat"),
                  int.class,
                  int.class,
                  int.class,
                  boolean.class,
                  int.class
                },
                "mouse")
            .invoke(null, gui, x, y, button, pressed, wheel);
    return ((CancellableEvent) r.clientEvents().post(event)).isCancelled();
  }

  private static boolean key(ClientRuntime r, Object gui, int key) throws Exception {
    Object event =
        method(
                Class.forName(EVENT),
                new Class<?>[] {
                  Class.forName("net.minecraft.client.gui.GuiChat"), int.class, boolean.class
                },
                "key")
            .invoke(null, gui, key, true);
    return ((CancellableEvent) r.clientEvents().post(event)).isCancelled();
  }

  private static Object box(Map<?, ?> boxes, String name) {
    for (Map.Entry<?, ?> e : boxes.entrySet())
      if (((Enum<?>) e.getKey()).name().equals(name)) return e.getValue();
    throw new AssertionError(name);
  }

  private static boolean contains(Set<?> columns, String name) {
    return columns.stream().anyMatch(c -> ((Enum<?>) c).name().equals(name));
  }

  private static Object value(ClientRuntime r, String key) {
    return r.modules().require("hud").module().settings().get(key).get();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void set(ClientRuntime r, String module, String key, Object value) {
    Setting setting = r.modules().require(module).module().settings().get(key);
    r.modules().setSetting(module, setting, value);
  }

  private static void check(boolean yes, String message) {
    checks++;
    if (!yes) throw new AssertionError(message);
  }
}
