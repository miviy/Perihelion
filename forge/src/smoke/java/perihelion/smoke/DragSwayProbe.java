package perihelion.smoke;

import static perihelion.smoke.GameplayHookProbe.*;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.lwjgl.opengl.GL11;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ModuleCategory;
import perihelion.integration.minecraft189.gui.ClickGuiModule;
import perihelion.integration.minecraft189.gui.panel.PanelLayout;

/** Real pointer movement plus rendered lean, reversal, release and re-grab. */
public final class DragSwayProbe {
  private static int checks;

  public static void run(ClientRuntime runtime, Object screen) throws Exception {
    checks = 0;
    PanelLayout layout = (PanelLayout) method(screen.getClass(), "model").invoke(screen);
    PanelLayout.Panel panel = layout.find(ModuleCategory.COMBAT);
    PanelLayout.Panel other = layout.find(ModuleCategory.MOVEMENT);
    ClickGuiModule preferences = (ClickGuiModule) runtime.modules().require("clickgui").module();
    String saved = preferences.modernLayout.get();
    float otherX = other.x, otherY = other.y;
    Object sway = field(screen.getClass(), "dragSway").get(screen);
    try {
      click(screen, panel.x + 8, panel.y + 8);
      for (int i = 0; i < 12; i++) move(screen, panel, 5, 0);
      check(angle(sway) < -.5f && angle(sway) >= -1.6f, "Dragging right leans in that direction");
      ProductionHudProbe.capture("drag-sway-right.png");
      for (int i = 0; i < 16; i++) move(screen, panel, -2, 0);
      check(angle(sway) > .1f && angle(sway) <= 1.6f, "Reversing direction reverses the sway");
      ProductionHudProbe.capture("drag-sway-left.png");
      check(other.x == otherX && other.y == otherY, "Other panels do not move or lean");
      float x = panel.x, y = panel.y, lean = Math.abs(angle(sway));
      method(screen.getClass(), "release").invoke(screen);
      PanelLayout restored = new PanelLayout();
      restored.load(
          preferences.modernLayout.get(),
          number(screen, "viewWidth"),
          number(screen, "viewHeight"),
          9);
      check(
          restored.find(panel.category).x == x && restored.find(panel.category).y == y,
          "Only exact pointer-owned positions are persisted");
      for (int i = 0; i < 8; i++) frame(screen);
      check(panel.x == x && panel.y == y, "Release has no positional trailing");
      check(Math.abs(angle(sway)) < lean, "Release gently returns the panel to flat");
      click(screen, panel.x + 8, panel.y + 8);
      check(
          angle(sway) == 0 && panel.x == x && panel.y == y,
          "Re-grabbing a settling panel preserves its anchor");
      for (int i = 0; i < 10; i++) move(screen, panel, 0, -1);
      check(angle(sway) < 0, "Vertical dragging has a small directional response");
      method(screen.getClass(), "release").invoke(screen);
      x = panel.x;
      y = panel.y;
      for (int i = 0; i < 60; i++) frame(screen);
      check(
          angle(sway) == 0 && panel.x == x && panel.y == y,
          "The pose fully settles without drifting");
      check(
          field(screen.getClass(), "swayPanel").get(screen) == null,
          "Idle frames retain no active sway target");
      check(
          GL11.glGetError() == GL11.GL_NO_ERROR,
          "Rotated fonts, rounded masks and row clips leave GL clean");
      ProductionHudProbe.capture("drag-sway-rest.png");
      clickRouting(screen, panel, sway);
      System.out.println(
          "[PERIHELION_SMOKE] DRAG_SWAY PASS checks=" + checks + " positionalTrailing=false");
    } finally {
      method(screen.getClass(), "release").invoke(screen);
      method(screen.getClass(), "clearSway").invoke(screen);
      runtime.modules().setSetting("clickgui", preferences.modernLayout, saved);
      method(screen.getClass(), new Class<?>[] {boolean.class}, "geometry").invoke(screen, false);
    }
  }

  private static void clickRouting(Object screen, PanelLayout.Panel panel, Object sway)
      throws Exception {
    Map<Object, Boolean> expanded = new IdentityHashMap<>();
    for (Object group : ((Map<?, ?>) field(screen.getClass(), "views").get(screen)).values())
      for (Object view : (List<?>) group)
        expanded.put(view, field(view.getClass(), "expanded").getBoolean(view));
    field(screen.getClass(), "swayPanel").set(screen, panel);
    field(sway.getClass(), "angle").setFloat(sway, 1.4f);
    float body =
        (Float)
            method(screen.getClass(), new Class<?>[] {PanelLayout.Panel.class}, "body")
                .invoke(screen, panel);
    float x = panel.x + panel.width - .25f, y = panel.y + 22 + body - 3;
    double radians = Math.toRadians(1.4), cx = panel.x + 8, cy = panel.y + 8;
    check(
        cx + (x - cx) * Math.cos(radians) + (y - cy) * Math.sin(radians) > panel.x + panel.width,
        "Edge fixture lies outside the visible tilted panel");
    press(screen, x, y, 1);
    boolean unchanged = true;
    for (Map.Entry<Object, Boolean> entry : expanded.entrySet())
      unchanged &=
          field(entry.getKey().getClass(), "expanded").getBoolean(entry.getKey())
              == entry.getValue();
    check(unchanged, "Clicking the vacant edge cannot activate a hidden module row");
    Object configs = field(screen.getClass(), "configs").get(screen);
    float savedX = panel.x, savedY = panel.y;
    boolean collapsed = field(screen.getClass(), "configCollapsed").getBoolean(screen);
    try {
      panel.x = number(screen, "configX");
      panel.y = number(screen, "configY");
      field(screen.getClass(), "configCollapsed").setBoolean(screen, false);
      field(screen.getClass(), "swayPanel").set(screen, panel);
      field(sway.getClass(), "angle").setFloat(sway, 1.4f);
      int presets =
          Math.max(1, ((List<?>) field(configs.getClass(), "profiles").get(configs)).size());
      press(screen, panel.x + panel.width - 10, panel.y + 22 + presets * 17 + 1, 1);
      check(
          field(configs.getClass(), "creating").getBoolean(configs),
          "Visible config controls above a tilted panel keep their raw pointer coordinates");
    } finally {
      method(configs.getClass(), "close").invoke(configs);
      panel.x = savedX;
      panel.y = savedY;
      field(screen.getClass(), "configCollapsed").setBoolean(screen, collapsed);
      method(screen.getClass(), "clearSway").invoke(screen);
    }
  }

  private static void move(Object screen, PanelLayout.Panel panel, float dx, float dy)
      throws Exception {
    float x = panel.x, y = panel.y;
    method(screen.getClass(), new Class<?>[] {float.class, float.class}, "drag")
        .invoke(screen, x + 8 + dx, y + 8 + dy);
    check(
        Math.abs(panel.x - x - dx) < .001f && Math.abs(panel.y - y - dy) < .001f,
        "Panel follows the pointer immediately");
    frame(screen);
  }

  private static void click(Object screen, float x, float y) throws Exception {
    press(screen, x, y, 0);
  }

  private static void press(Object screen, float x, float y, int button) throws Exception {
    method(screen.getClass(), new Class<?>[] {float.class, float.class, int.class}, "click")
        .invoke(screen, x, y, button);
  }

  private static void frame(Object screen) throws Exception {
    method(NewVisualsProbe.class, new Class<?>[] {Object.class, float.class}, "frame")
        .invoke(null, screen, 1 / 60f);
  }

  private static float number(Object object, String name) throws Exception {
    return field(object.getClass(), name).getFloat(object);
  }

  private static float angle(Object sway) throws Exception {
    return field(sway.getClass(), "angle").getFloat(sway);
  }

  private static void check(boolean value, String description) {
    checks++;
    if (!value) throw new AssertionError(description);
  }
}
