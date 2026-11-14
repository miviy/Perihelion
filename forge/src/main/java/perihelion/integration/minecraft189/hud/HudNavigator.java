package perihelion.integration.minecraft189.hud;

import java.util.*;
import java.util.function.ToIntFunction;
import org.lwjgl.input.Keyboard;
import perihelion.core.diagnostics.Failures;
import perihelion.core.module.*;
import perihelion.core.setting.*;
import perihelion.integration.minecraft189.gui.GuiValues;

/** Keyboard view of the native module/settings definitions; all writes use ModuleManager. */
final class HudNavigator {
  private static final List<ModuleCategory> TYPES =
      Arrays.stream(ModuleCategory.values())
          .filter(category -> category != ModuleCategory.CLIENT)
          .collect(java.util.stream.Collectors.toList());
  static final String[] CATEGORIES =
      TYPES.stream().map(ModuleCategory::displayName).toArray(String[]::new);
  static final int CATEGORY_WIDTH = 65, ROW_HEIGHT = 11;
  static final int PANE_CHROME = ROW_HEIGHT * 3;
  static final int MODULE_CHROME = ROW_HEIGHT * 2;
  final List<List<ModuleEntry>> groups = new ArrayList<>();
  int category, selected, settingSelected, component;
  boolean expanded, settingsOpen, editing;
  String draft = "", error = "";
  private Setting<?> editTarget;
  private Object original;
  private final ModuleManager manager;
  private final Set<Setting<?>> brokenVisibility =
      Collections.newSetFromMap(new IdentityHashMap<>());

  HudNavigator(ModuleManager manager) {
    this.manager = manager;
    for (int i = 0; i < CATEGORIES.length; i++) groups.add(new ArrayList<>());
    for (ModuleEntry entry : manager.entries()) {
      if (entry.info().id().equals("hud") || entry.info().category() == ModuleCategory.CLIENT)
        continue;
      int group = TYPES.indexOf(entry.info().category());
      groups.get(group).add(entry);
    }
    for (List<ModuleEntry> group : groups)
      group.sort(Comparator.comparing(HudNavigator::label, String.CASE_INSENSITIVE_ORDER));
  }

  void key(int key) {
    key(key, '\0');
  }

  void key(int key, char typed) {
    if (settingsOpen) {
      List<Setting<?>> settings = settings();
      if (editing) {
        editKey(key, typed);
        return;
      }
      if (key == Keyboard.KEY_LEFT || key == Keyboard.KEY_ESCAPE) {
        settingsOpen = false;
        error = "";
      } else if (key == Keyboard.KEY_UP || key == Keyboard.KEY_DOWN) {
        if (!settings.isEmpty())
          settingSelected =
              Math.floorMod(settingSelected + (key == Keyboard.KEY_UP ? -1 : 1), settings.size());
        error = "";
      } else if ((key == Keyboard.KEY_RETURN || key == Keyboard.KEY_RIGHT) && !settings.isEmpty()) {
        Setting<?> target = settings.get(settingSelected);
        error = "";
        if (target.metadata().kind() == SettingMetadata.Kind.BOOLEAN)
          put(target, !Boolean.TRUE.equals(target.get()));
        else {
          editTarget = target;
          original = target.get();
          draft =
              target.metadata().kind() == SettingMetadata.Kind.STRING
                  ? (String) target.get()
                  : value(target);
          component = 0;
          editing = true;
        }
      }
      return;
    }
    if (key == Keyboard.KEY_LEFT || key == Keyboard.KEY_ESCAPE) {
      expanded = false;
      return;
    }
    if (key == Keyboard.KEY_RIGHT) {
      if (expanded && !rows().isEmpty()) {
        settingsOpen = true;
        settingSelected = 0;
        error = "";
      } else expanded = true;
      return;
    }
    if (key == Keyboard.KEY_UP || key == Keyboard.KEY_DOWN) {
      int delta = key == Keyboard.KEY_UP ? -1 : 1;
      if (expanded && !rows().isEmpty()) selected = Math.floorMod(selected + delta, rows().size());
      else {
        category = Math.floorMod(category + delta, CATEGORIES.length);
        selected = 0;
      }
    }
    if (key == Keyboard.KEY_RETURN && expanded && !rows().isEmpty())
      manager.toggle(rows().get(selected).info().id());
  }

  List<ModuleEntry> rows() {
    return groups.get(category);
  }

  ModuleEntry module() {
    return rows().isEmpty() ? null : rows().get(Math.min(selected, rows().size() - 1));
  }

  List<Setting<?>> settings() {
    List<Setting<?>> visible = new ArrayList<>();
    ModuleEntry entry = module();
    if (entry != null)
      for (Setting<?> setting : entry.module().settings().values()) {
        if (brokenVisibility.contains(setting)) continue;
        try {
          if (setting.isVisible()) visible.add(setting);
        } catch (Throwable failure) {
          Failures.rethrowFatal(failure);
          brokenVisibility.add(setting);
          manager.quarantine(entry.info().id(), failure);
          error = "Module faulted; see log";
        }
      }
    if (editing && visible.contains(editTarget)) settingSelected = visible.indexOf(editTarget);
    else settingSelected = Math.max(0, Math.min(settingSelected, visible.size() - 1));
    // A dependent setting can disappear after a value change; never keep a hidden editor alive.
    if (editing && !visible.contains(editTarget)) finish();
    return visible;
  }

  boolean textEditing() {
    return editing && editTarget.metadata().kind() == SettingMetadata.Kind.STRING;
  }

  private void editKey(int key, char typed) {
    if (key == Keyboard.KEY_ESCAPE) {
      cancel();
      return;
    }
    if (key == Keyboard.KEY_RETURN) {
      if (!textEditing() || putParsed(editTarget, draft)) finish();
      return;
    }
    if (textEditing()) {
      if (key == Keyboard.KEY_BACK && !draft.isEmpty())
        draft = draft.substring(0, draft.offsetByCodePoints(draft.length(), -1));
      else if (key == Keyboard.KEY_DELETE) draft = "";
      else if (typed >= 32
          && typed != 127
          && draft.length() < editTarget.metadata().maximumLength().orElse(256)) draft += typed;
      error = "";
      return;
    }
    if (key == Keyboard.KEY_UP || key == Keyboard.KEY_DOWN) {
      int count =
          editTarget.metadata().kind() == SettingMetadata.Kind.RANGE
              ? 2
              : editTarget.metadata().kind() == SettingMetadata.Kind.COLOR
                  ? 6
                  : editTarget.metadata().kind() == SettingMetadata.Kind.MULTI_SELECT
                      ? editTarget.metadata().options().size()
                      : 1;
      component = Math.floorMod(component + (key == Keyboard.KEY_UP ? -1 : 1), Math.max(1, count));
      error = "";
      return;
    }
    int delta = key == Keyboard.KEY_LEFT ? -1 : key == Keyboard.KEY_RIGHT ? 1 : 0;
    if (delta == 0
        && !(key == Keyboard.KEY_SPACE
            && editTarget.metadata().kind() == SettingMetadata.Kind.MULTI_SELECT)) return;
    error = "";
    switch (editTarget.metadata().kind()) {
      case INTEGER:
        put(
            editTarget,
            (int) increment(editTarget, ((Number) editTarget.get()).doubleValue(), delta));
        break;
      case DECIMAL:
        put(editTarget, increment(editTarget, ((Number) editTarget.get()).doubleValue(), delta));
        break;
      case ENUM:
        List<String> options = editTarget.metadata().options();
        int index = options.indexOf(((Enum<?>) editTarget.get()).name().toLowerCase(Locale.ROOT));
        putParsed(editTarget, options.get(Math.floorMod(index + delta, options.size())));
        break;
      case MULTI_SELECT:
        List<String> choices = editTarget.metadata().options();
        if (choices.isEmpty()) break;
        Set<String> active = new LinkedHashSet<>();
        for (Object entry : (Set<?>) editTarget.get())
          active.add(((Enum<?>) entry).name().toLowerCase(Locale.ROOT));
        String choice = choices.get(component);
        if (!active.remove(choice)) active.add(choice);
        putParsed(editTarget, String.join(",", active));
        break;
      case RANGE:
        DoubleRange range = (DoubleRange) editTarget.get();
        double next = increment(editTarget, component == 0 ? range.lower() : range.upper(), delta);
        put(
            editTarget,
            component == 0
                ? new DoubleRange(Math.min(next, range.upper()), range.upper())
                : new DoubleRange(range.lower(), Math.max(range.lower(), next)));
        break;
      case COLOR:
        ColorValue color = (ColorValue) editTarget.get();
        int[] channels = {color.red(), color.green(), color.blue(), color.alpha()};
        if (component < 4)
          channels[component] = Math.max(0, Math.min(255, channels[component] + delta));
        boolean rainbow = component == 4 ? delta > 0 : color.rainbow();
        double speed =
            component == 5
                ? Math.max(
                    .1, Math.min(10, Math.round((color.rainbowSpeed() + delta * .1) * 10) / 10.0))
                : color.rainbowSpeed();
        put(
            editTarget,
            ColorValue.rgba(channels[0], channels[1], channels[2], channels[3])
                .withRainbow(rainbow, speed));
        break;
      default:
        break;
    }
  }

  private static double increment(Setting<?> target, double current, int delta) {
    NumericConstraints numeric = target.metadata().numeric().get();
    double span = numeric.maximum() - numeric.minimum();
    if (span == 0) return current;
    double step = numeric.step().orElse(span / 100);
    return GuiValues.snap(numeric, (current + delta * step - numeric.minimum()) / span);
  }

  private boolean putParsed(Setting<?> target, String input) {
    try {
      return put(target, target.parse(input));
    } catch (IllegalArgumentException failure) {
      fail(failure);
      return false;
    }
  }

  @SuppressWarnings("unchecked")
  private boolean put(Setting<?> target, Object candidate) {
    try {
      if (manager.setSetting(module().info().id(), (Setting<Object>) target, candidate))
        return true;
      error = "Module faulted; see log";
    } catch (RuntimeException failure) {
      fail(failure);
    }
    return false;
  }

  private void fail(RuntimeException failure) {
    String message = failure.getMessage();
    error = message == null || message.isEmpty() ? "Value could not be saved" : clean(message);
  }

  void cancel() {
    if (editing && !textEditing()) put(editTarget, original);
    finish();
  }

  private void finish() {
    editing = false;
    editTarget = null;
    original = null;
    draft = "";
  }

  String value(Setting<?> target) {
    if (editing && target == editTarget) {
      switch (target.metadata().kind()) {
        case STRING:
          return clean(draft) + "_";
        case RANGE:
          DoubleRange range = (DoubleRange) target.get();
          return (component == 0 ? "Lower: " : "Upper: ")
              + GuiValues.number(component == 0 ? range.lower() : range.upper());
        case MULTI_SELECT:
          List<String> choices = target.metadata().options();
          if (choices.isEmpty()) return "No choices";
          String option = choices.get(component);
          boolean selected =
              ((Set<?>) target.get())
                  .stream().anyMatch(entry -> ((Enum<?>) entry).name().equalsIgnoreCase(option));
          return (selected ? "+ " : "- ") + GuiValues.readable(option);
        case COLOR:
          ColorValue color = (ColorValue) target.get();
          return new String[] {
                "Red: " + color.red(),
                "Green: " + color.green(),
                "Blue: " + color.blue(),
                "Alpha: " + color.alpha(),
                "Rainbow: " + (color.rainbow() ? "+" : "-"),
                "Speed: " + GuiValues.number(color.rainbowSpeed())
              }
              [component];
        default:
          break;
      }
    }
    Object current = target.get();
    if (current instanceof Enum) return GuiValues.readable(((Enum<?>) current).name());
    if (current instanceof Number) return GuiValues.number(((Number) current).doubleValue());
    if (current instanceof DoubleRange) {
      DoubleRange range = (DoubleRange) current;
      return GuiValues.number(range.lower()) + " - " + GuiValues.number(range.upper());
    }
    if (current instanceof Set)
      return ((Set<?>) current).size() + " / " + target.metadata().options().size() + " selected";
    if (current instanceof Boolean) return Boolean.TRUE.equals(current) ? "+" : "-";
    String value = clean(String.valueOf(current));
    return value.isEmpty() ? "Empty" : value;
  }

  String[] hint() {
    if (!error.isEmpty()) return new String[] {error, "Esc cancel"};
    if (!editing) {
      List<Setting<?>> visible = settings();
      boolean toggle =
          !visible.isEmpty()
              && visible.get(settingSelected).metadata().kind() == SettingMetadata.Kind.BOOLEAN;
      return new String[] {toggle ? "Enter toggle" : "Enter edit", "Left back"};
    }
    if (textEditing()) return new String[] {"Type  Enter save", "Esc cancel"};
    boolean components =
        editTarget.metadata().kind() == SettingMetadata.Kind.RANGE
            || editTarget.metadata().kind() == SettingMetadata.Kind.COLOR
            || editTarget.metadata().kind() == SettingMetadata.Kind.MULTI_SELECT;
    return new String[] {
      components ? "Up/Down part  Left/Right edit" : "Left/Right change", "Enter done  Esc cancel"
    };
  }

  private static String clean(String value) {
    return value.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
  }

  static String label(ModuleEntry entry) {
    return entry.info().id().equals("aura") ? "Kill Aura" : GuiValues.readable(entry.info().name());
  }

  SettingRows settingRows(int paneWidth, int availableHeight, ToIntFunction<String> measure) {
    return new SettingRows(this, paneWidth, availableHeight, measure);
  }

  ModuleRows moduleRows(int availableHeight) {
    return new ModuleRows(this, availableHeight);
  }

  static final class ModuleRows {
    final List<ModuleEntry> modules;
    final int first, count, contentHeight, height;

    ModuleRows(HudNavigator nav, int availableHeight) {
      modules = nav.rows();
      int visible = Math.max(1, (availableHeight - MODULE_CHROME) / ROW_HEIGHT);
      first = Math.max(0, nav.selected - visible + 1);
      count = Math.min(visible, modules.size() - first);
      contentHeight = Math.max(1, count) * ROW_HEIGHT;
      height = Math.min(availableHeight, MODULE_CHROME + contentHeight);
    }
  }

  /** One measured geometry shared by scroll, bounds and drawing. */
  static final class SettingRows {
    final List<Setting<?>> settings;
    final List<String> labels = new ArrayList<>(), values = new ArrayList<>();
    final int[] heights;
    final boolean[] stacked;
    final int first, count, contentHeight, height;

    SettingRows(HudNavigator nav, int width, int availableHeight, ToIntFunction<String> measure) {
      settings = nav.settings();
      heights = new int[settings.size()];
      stacked = new boolean[settings.size()];
      int throughSelected = 0;
      for (int i = 0; i < settings.size(); i++) {
        Setting<?> setting = settings.get(i);
        String label = GuiValues.readable(setting.displayName()), value = nav.value(setting);
        labels.add(label);
        values.add(value);
        stacked[i] =
            setting.metadata().kind() != SettingMetadata.Kind.BOOLEAN
                && measure.applyAsInt(label) + measure.applyAsInt(value) + 16 > width;
        heights[i] = stacked[i] ? ROW_HEIGHT * 2 : ROW_HEIGHT;
        if (i <= nav.settingSelected) throughSelected += heights[i];
      }
      int viewport = Math.max(ROW_HEIGHT, availableHeight - PANE_CHROME), start = 0;
      while (start < nav.settingSelected && throughSelected > viewport)
        throughSelected -= heights[start++];
      first = start;
      int rows = 0, content = 0;
      while (first + rows < settings.size()) {
        int next = heights[first + rows];
        if (rows > 0 && content + next > viewport) break;
        content += next;
        rows++;
      }
      count = rows;
      contentHeight = Math.max(ROW_HEIGHT, content);
      height = Math.min(availableHeight, PANE_CHROME + contentHeight);
    }
  }

  /** Keep the active text suffix and caret visible without splitting a Unicode codepoint. */
  static String tail(String value, int maximum, ToIntFunction<String> measure) {
    if (maximum <= 0) return "";
    if (measure.applyAsInt(value) <= maximum) return value;
    String prefix = measure.applyAsInt("...") < maximum ? "..." : "";
    int low = 0, high = value.codePointCount(0, value.length());
    while (low < high) {
      int middle = (low + high) / 2;
      String candidate = prefix + value.substring(value.offsetByCodePoints(0, middle));
      if (measure.applyAsInt(candidate) <= maximum) high = middle;
      else low = middle + 1;
    }
    return prefix + value.substring(value.offsetByCodePoints(0, low));
  }

  /** Side panes can grow or scroll without feeding their dimensions back into the saved anchor. */
  static final class Layout {
    final int x, y, paneX, paneWidth, availableHeight;

    Layout(int x, int y, int width, int height) {
      this(x, y, width, height, 0);
    }

    Layout(int x, int y, int width, int height, int minimumX) {
      this.x = x;
      this.y = y;
      int right = Math.max(0, width - x - CATEGORY_WIDTH - 4), left = Math.max(0, x - minimumX - 4);
      boolean opensRight = right >= left;
      paneWidth = Math.min(176, opensRight ? right : left);
      paneX = opensRight ? x + CATEGORY_WIDTH + 2 : x - paneWidth - 2;
      availableHeight = Math.max(ROW_HEIGHT, height - y - 2);
    }
  }
}
