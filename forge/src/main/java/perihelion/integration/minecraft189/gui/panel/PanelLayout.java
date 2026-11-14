package perihelion.integration.minecraft189.gui.panel;

import com.google.gson.*;
import java.util.*;
import perihelion.core.module.ModuleCategory;

/** This frontend's persistent geometry and disclosures; never module or setting values. */
public final class PanelLayout {
  public static final float WIDTH = 132, HEADER = 13;

  public static final class Panel {
    public final ModuleCategory category;
    public float x, y, scroll, width = WIDTH;
    public float headerHeight = HEADER;
    public boolean collapsed;
    public final Set<String> expandedRows = new LinkedHashSet<>();
    private float bodyFraction, anchorX, anchorY;

    Panel(ModuleCategory category) {
      this.category = category;
    }

    public void limitBody(float height, float screenHeight) {
      bodyFraction = Math.max(0, Math.min(1, height / Math.max(1, screenHeight)));
      anchorX = x;
      anchorY = y;
    }

    public boolean limited() {
      return bodyFraction > 0 && x == anchorX && y == anchorY;
    }

    public float bodyHeight(float screenHeight, float contentHeight, boolean searching) {
      return bodyHeight(screenHeight, contentHeight, searching, false);
    }

    public float bodyHeight(
        float screenHeight, float contentHeight, boolean searching, boolean expanded) {
      if (collapsed && !searching) return 0;
      float available =
          Math.max(16, Math.min(screenHeight * 0.64f, screenHeight - y - headerHeight - 6));
      // Default stacks reserve room for the other headers. Settings may grow beyond that
      // initial budget, up to the same viewport cap used by every floating panel.
      if (limited() && !expanded)
        available = Math.min(available, Math.max(16, screenHeight * bodyFraction));
      return Math.min(contentHeight, available);
    }

    public void scroll(float delta, float content, float viewport) {
      scroll = Math.max(0, Math.min(Math.max(0, content - viewport), scroll + delta));
    }

    public void recover(float width, float height, float top) {
      x = Math.max(0, Math.min(Math.max(0, width - this.width), x));
      y = Math.max(top, Math.min(Math.max(top, height - headerHeight - 20), y));
    }
  }

  private final List<Panel> panels = new ArrayList<>();

  public List<Panel> panels() {
    return Collections.unmodifiableList(panels);
  }

  public void front(Panel panel) {
    panels.remove(panel);
    panels.add(panel);
  }

  public Panel find(ModuleCategory category) {
    for (Panel p : panels) if (p.category == category) return p;
    throw new IllegalArgumentException();
  }

  public void reset(float width, float height, float top) {
    panels.clear();
    int columns = Math.max(1, (int) ((width - 8) / (WIDTH + 6)));
    int rows = (ModuleCategory.values().length + columns - 1) / columns;
    float band = Math.max(36, (height - top - 12) / rows);
    int index = 0;
    for (ModuleCategory category : ModuleCategory.values()) {
      Panel panel = new Panel(category);
      panel.x = 6 + (index % columns) * (WIDTH + 6);
      panel.y = top + (index / columns) * band;
      panel.recover(width, height, top);
      panels.add(panel);
      index++;
    }
  }

  public void load(String encoded, float width, float height, float top) {
    reset(width, height, top);
    try {
      JsonArray array = new JsonParser().parse(encoded).getAsJsonArray();
      Set<ModuleCategory> seen = EnumSet.noneOf(ModuleCategory.class);
      for (JsonElement item : array) {
        try {
          JsonObject object = item.getAsJsonObject();
          ModuleCategory category = ModuleCategory.valueOf(object.get("category").getAsString());
          if (!seen.add(category)) continue;
          float x = object.get("x").getAsFloat(), y = object.get("y").getAsFloat();
          if (!Float.isFinite(x) || !Float.isFinite(y)) continue;
          Panel panel = find(category);
          panel.x = x;
          panel.y = y;
          panel.collapsed = object.get("collapsed").getAsBoolean();
          panel.recover(width, height, top);
          if (object.has("body-fraction")) {
            float fraction = object.get("body-fraction").getAsFloat();
            if (Float.isFinite(fraction) && fraction > 0 && fraction <= 1)
              panel.limitBody(height * fraction, height);
          }
          front(panel);
          if (object.has("expanded-rows") && object.get("expanded-rows").isJsonArray())
            for (JsonElement row : object.getAsJsonArray("expanded-rows"))
              if (row.isJsonPrimitive() && row.getAsJsonPrimitive().isString())
                panel.expandedRows.add(row.getAsString());
        } catch (RuntimeException ignored) {
          /* Unknown/new categories and malformed entries use defaults. */
        }
      }
    } catch (RuntimeException ignored) {
      /* Corrupt layout cannot make the screen inaccessible. */
    }
  }

  public String encode() {
    JsonArray array = new JsonArray();
    for (Panel panel : panels) {
      JsonObject object = new JsonObject();
      object.addProperty("category", panel.category.name());
      object.addProperty("x", panel.x);
      object.addProperty("y", panel.y);
      object.addProperty("collapsed", panel.collapsed);
      if (!panel.expandedRows.isEmpty()) {
        JsonArray rows = new JsonArray();
        for (String row : panel.expandedRows) rows.add(new JsonPrimitive(row));
        object.add("expanded-rows", rows);
      }
      if (panel.limited()) object.addProperty("body-fraction", panel.bodyFraction);
      array.add(object);
    }
    return array.toString();
  }
}
