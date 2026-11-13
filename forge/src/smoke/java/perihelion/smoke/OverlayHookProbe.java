package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;

import java.lang.reflect.*;
import java.util.*;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.setting.Setting;

/** Packaged overlay UI with isolated provider fixtures. Never contacts a server or API. */
public final class OverlayHookProbe {
  private static final String PREFIX = "perihelion.integration.minecraft189.hud.";
  private static int checks;

  public static void run(ClientRuntime runtime, Object game, Object hud) throws Exception {
    checks = 0;
    boolean enabled = runtime.modules().require("hud").requestedEnabled();
    runtime.modules().setEnabled("hud", true);
    Object renderer = field(hud.getClass(), "renderer").get(hud);
    Object lookup = field(hud.getClass(), "overlayLookup").get(hud);
    Map cache = (Map) field(lookup.getClass(), "cache").get(lookup);
    Map savedCache = new LinkedHashMap(cache);
    Map<String, Object> saved = new LinkedHashMap<>();
    for (Setting<?> setting : runtime.modules().require("hud").module().settings().values())
      saved.put(setting.id(), setting.get());
    Class profileType = Class.forName(PREFIX + "OverlayProfile"),
        entryType = Class.forName(PREFIX + "OverlayLookup$Entry"),
        columnType = Class.forName(PREFIX + "HudModule$PlayerColumn"),
        modeType = Class.forName(PREFIX + "OverlayProfile$Mode");
    Constructor entry = entryType.getDeclaredConstructor(profileType, long.class);
    entry.setAccessible(true);
    Method parse =
        method(profileType, new Class[] {UUID.class, String.class, String.class}, "parse");
    Method update = method(renderer.getClass(), new Class[] {boolean.class}, "updatePlayers");
    try {
      set(runtime, "hud", "players", true);
      set(runtime, "hud", "overlay-auto-stats", false);
      set(runtime, "hud", "overlay-sessions", false);
      set(runtime, "hud", "player-limit", 32);
      set(runtime, "hud", "player-columns", EnumSet.allOf(columnType));
      update.invoke(renderer, false);
      List<?> players = (List<?>) field(renderer.getClass(), "players").get(renderer);
      check(players.size() >= 3, "fixture has a bounded player roster");
      for (Object row : players) {
        UUID id = (UUID) field(row.getClass(), "id").get(row);
        String body =
            "{\"success\":true,\"player\":{\"uuid\":\""
                + id
                + "\",\"stats\":{\"Bedwars\":{\"Experience\":487000,\"final_kills_bedwars\":12,\"final_deaths_bedwars\":3,\"eight_two_final_kills_bedwars\":8,\"eight_two_final_deaths_bedwars\":1}}}}";
        cache.put(id, entry.newInstance(parse.invoke(null, id, body, "fixture"), Long.MAX_VALUE));
      }
      update.invoke(renderer, false);
      players = (List<?>) field(renderer.getClass(), "players").get(renderer);
      Object first = players.get(0);
      Map<?, ?> values = (Map<?, ?>) field(first.getClass(), "values").get(first);
      check(
          "100".equals(values.get(Enum.valueOf(columnType, "STARS"))),
          "provider stars reach native row snapshots");
      check(
          "4.00".equals(values.get(Enum.valueOf(columnType, "FKD"))),
          "overall FKDR reaches native table");
      set(runtime, "hud", "overlay-bedwars-mode", Enum.valueOf(modeType, "DOUBLES"));
      update.invoke(renderer, false);
      first = ((List<?>) field(renderer.getClass(), "players").get(renderer)).get(0);
      check(
          "8.00"
              .equals(
                  ((Map<?, ?>) field(first.getClass(), "values").get(first))
                      .get(Enum.valueOf(columnType, "FKD"))),
          "mode changes refresh cached row values");
      Class elementType = Class.forName(PREFIX + "HudModule$Element");
      Method render =
          method(
              renderer.getClass(),
              new Class[] {int.class, int.class, boolean.class, elementType, int.class, int.class},
              "render");
      render.invoke(renderer, 300, 160, false, null, 0, 0);
      Map<?, ?> boxes = (Map<?, ?>) field(renderer.getClass(), "boxes").get(renderer);
      Object box = null;
      for (Map.Entry<?, ?> e : boxes.entrySet())
        if (e.getKey().toString().equals("PLAYERS")) box = e.getValue();
      int width = field(box.getClass(), "w").getInt(box),
          x = field(box.getClass(), "x").getInt(box),
          y = field(box.getClass(), "y").getInt(box);
      List<?> columns =
          (List<?>)
              method(renderer.getClass(), new Class[] {box.getClass()}, "visiblePlayerColumns")
                  .invoke(renderer, box);
      int occupied = (Integer) method(renderer.getClass(), "playerNameWidth").invoke(renderer);
      for (Object column : columns)
        occupied +=
            (Integer)
                method(renderer.getClass(), new Class[] {columnType}, "playerColumnWidth")
                    .invoke(renderer, column);
      check(occupied <= width, "small-window columns fit inside table bounds");
      Method scroll =
          method(
              renderer.getClass(), new Class[] {int.class, int.class, int.class}, "scrollPlayers");
      check(
          (Boolean) scroll.invoke(renderer, x + 5, y + 3, -120), "header wheel pages wide columns");
      check(
          field(renderer.getClass(), "playerColumnOffset").getInt(renderer) == 1,
          "one wheel step advances one column");
      check(
          !(Boolean) scroll.invoke(renderer, x + width + 2, y + 3, -120),
          "outside scrolling remains vanilla chat input");
      field(box.getClass(), "h").setInt(box, 31);
      check(
          (Boolean) scroll.invoke(renderer, x + 5, y + 15, -120),
          "body wheel scrolls clipped rows");
      Object clicked =
          method(renderer.getClass(), new Class[] {int.class, int.class}, "playerAt")
              .invoke(renderer, x + 5, y + 15);
      players = (List<?>) field(renderer.getClass(), "players").get(renderer);
      check(clicked == players.get(1), "right-click hit testing follows scrolled row offset");
      String username = (String) field(clicked.getClass(), "username").get(clicked);
      Object overlay = field(hud.getClass(), "overlay").get(hud);
      method(overlay.getClass(), new Class[] {hud.getClass(), String.class}, "hide")
          .invoke(overlay, hud, username);
      update.invoke(renderer, false);
      players = (List<?>) field(renderer.getClass(), "players").get(renderer);
      for (Object row : players)
        check(
            !username.equals(field(row.getClass(), "username").get(row)),
            "hidden row removed from production table");
      runtime.commands().execute("hud unhide", line -> {});
      update.invoke(renderer, false);
      boolean found = false;
      for (Object row : (List<?>) field(renderer.getClass(), "players").get(renderer))
        found |= username.equals(field(row.getClass(), "username").get(row));
      check(found, "unhide command restores the row");
      Set statsColumns = EnumSet.noneOf(columnType);
      for (String name : new String[] {"STARS", "FKD", "WS", "WL", "TAGS", "NICK", "SESSION"})
        statsColumns.add(Enum.valueOf(columnType, name));
      set(runtime, "hud", "player-columns", statsColumns);
      field(renderer.getClass(), "playerColumnOffset").setInt(renderer, 0);
      field(renderer.getClass(), "playerRowOffset").setInt(renderer, 0);
      update.invoke(renderer, false);
      Field screen = field(game.getClass(), "currentScreen", "field_71462_r");
      Object oldScreen = screen.get(game);
      Method display =
          method(
              game.getClass(),
              new Class[] {Class.forName("net.minecraft.client.gui.GuiScreen")},
              "displayGuiScreen",
              "func_147108_a");
      try {
        display.invoke(game, Class.forName("net.minecraft.client.gui.GuiChat").newInstance());
        HudChatProbe.captureWorld(game, "modern-lazify-overlay.png");
      } finally {
        display.invoke(game, oldScreen);
      }
      runtime.commands().execute("hud refresh", line -> {});
      check(cache.isEmpty(), "refresh retires cached provider snapshots");
      check(
          ((Number) method(lookup.getClass(), "pendingCount").invoke(lookup)).intValue() == 0,
          "offline fixture enqueued no API work");
      System.out.println("PERIHELION_SMOKE OVERLAY checks=" + checks + " PASS");
    } finally {
      for (Map.Entry<String, Object> e : saved.entrySet())
        set(runtime, "hud", e.getKey(), e.getValue());
      cache.clear();
      cache.putAll(savedCache);
      field(renderer.getClass(), "playerColumnOffset").setInt(renderer, 0);
      field(renderer.getClass(), "playerRowOffset").setInt(renderer, 0);
      update.invoke(renderer, false);
      runtime.modules().setEnabled("hud", enabled);
    }
  }

  private static void set(ClientRuntime r, String module, String id, Object value) {
    Setting s = r.modules().require(module).module().settings().get(id);
    r.modules().setSetting(module, s, value);
  }

  private static void check(boolean success, String message) {
    checks++;
    if (!success) throw new AssertionError("OVERLAY " + message);
  }
}
