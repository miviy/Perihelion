package perihelion.integration.minecraft189.gui.panel;

import com.google.gson.*;
import java.util.*;

/** Screen chrome geometry, stored through the normal ClickGUI settings. */
public final class UtilityLayout {
  public static final class Box {
    public float x, y;
    public boolean collapsed;

    public void recover(float width, float height, float w, float h) {
      x = Math.max(2, Math.min(Math.max(2, width - w - 2), x));
      y = Math.max(2, Math.min(Math.max(2, height - h - 2), y));
    }
  }

  private final Map<String, Box> boxes = new LinkedHashMap<>();

  public Box box(String id, float x, float y) {
    Box box = boxes.get(id);
    if (box == null) {
      box = new Box();
      box.x = x;
      box.y = y;
      boxes.put(id, box);
    }
    return box;
  }

  public void load(String encoded) {
    boxes.clear();
    try {
      JsonObject root = new JsonParser().parse(encoded).getAsJsonObject();
      for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
        JsonObject data = entry.getValue().getAsJsonObject();
        float x = data.get("x").getAsFloat(), y = data.get("y").getAsFloat();
        if (!Float.isFinite(x) || !Float.isFinite(y)) continue;
        Box box = box(entry.getKey(), x, y);
        box.collapsed = data.has("collapsed") && data.get("collapsed").getAsBoolean();
      }
    } catch (RuntimeException ignored) {
      /* Inaccessible chrome recovers to defaults. */
    }
  }

  public void resize(float width, float height) {
    resize(width, height, 150, 48);
  }

  public void resize(float width, float height, float hudAnchor, float configAnchor) {
    Box viewport = boxes.get("viewport");
    // A redraw at the same size must not accumulate floating-point position drift.
    if (viewport != null && viewport.x == width && viewport.y == height) return;
    if (viewport != null && viewport.x > 0 && viewport.y > 0) {
      for (Map.Entry<String, Box> entry : boxes.entrySet()) {
        String id = entry.getKey();
        if (id.equals("viewport")
            || id.equals("hud-editor")
            || id.equals("configs-editor")
            || id.endsWith("-viewport")) continue;
        Box box = entry.getValue();
        float offset =
            entry.getKey().equals("hud")
                ? hudAnchor
                : entry.getKey().equals("configs") ? configAnchor : 0;
        boolean anchored = offset > 0 && Math.abs(box.x - (viewport.x - offset)) < .01f;
        box.x = anchored ? width - offset : box.x * width / viewport.x;
        box.y = anchored && Math.abs(box.y - 7) <= 2 ? 7 : box.y * height / viewport.y;
      }
    }
    viewport = box("viewport", width, height);
    viewport.x = width;
    viewport.y = height;
  }

  /** Editors use different logical scales; their viewport must not transform menu tabs. */
  public void resizeEditor(String id, float width, float height) {
    String viewportId = id + "-viewport";
    Box viewport = boxes.get(viewportId);
    // Read old layouts using their original shared viewport once, without rewriting it.
    if (viewport == null) viewport = boxes.get("viewport");
    Box editor = boxes.get(id);
    if (editor != null && viewport != null && viewport.x > 0 && viewport.y > 0) {
      if (viewport.x != width || viewport.y != height) {
        editor.x *= width / viewport.x;
        editor.y *= height / viewport.y;
      }
    }
    Box next = box(viewportId, width, height);
    next.x = width;
    next.y = height;
  }

  public String encode() {
    JsonObject root = new JsonObject();
    for (Map.Entry<String, Box> entry : boxes.entrySet()) {
      JsonObject data = new JsonObject();
      Box box = entry.getValue();
      data.addProperty("x", box.x);
      data.addProperty("y", box.y);
      data.addProperty("collapsed", box.collapsed);
      root.add(entry.getKey(), data);
    }
    return root.toString();
  }
}
