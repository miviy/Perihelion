package perihelion.integration.minecraft189.hud;

import perihelion.core.module.ModuleEntry;
import perihelion.core.module.ModuleState;
import perihelion.core.setting.Setting;
import perihelion.core.setting.SettingMetadata;

/** One pane layout and drawing path for module lists and their live settings, in every style. */
final class TabGuiView {
  enum Tone {
    TEXT,
    MUTED,
    ENABLED,
    EDITING,
    ERROR
  }

  interface Painter {
    void surface(int x, int y, int width, int height);

    void highlight(
        int x, int y, int width, int height, boolean selected, boolean editing, boolean enabled);

    void label(String value, int x, int y, int width, int height, Tone tone);

    void checkbox(int x, int y, int width, int height, boolean on);

    int checkboxWidth();

    int textWidth(String value);
  }

  private TabGuiView() {}

  static void draw(
      HudNavigator nav,
      HudNavigator.Layout layout,
      HudNavigator.ModuleRows modules,
      HudNavigator.SettingRows settings,
      Painter paint) {
    int x = layout.x, y = layout.y, row = HudNavigator.ROW_HEIGHT;
    paint.surface(x, y, HudNavigator.CATEGORY_WIDTH, HudNavigator.CATEGORIES.length * row);
    for (int i = 0; i < HudNavigator.CATEGORIES.length; i++) {
      if (i == nav.category)
        paint.highlight(x, y + i * row, HudNavigator.CATEGORY_WIDTH, row, true, false, false);
      paint.label(
          HudNavigator.CATEGORIES[i],
          x + 3,
          y + i * row,
          HudNavigator.CATEGORY_WIDTH - 6,
          row,
          i == nav.category ? Tone.TEXT : Tone.MUTED);
    }
    if (!nav.expanded || layout.paneWidth <= 0) return;
    x = layout.paneX;
    int width = layout.paneWidth;
    int height = nav.settingsOpen ? settings.height : modules.height;
    paint.surface(x, y, width, height);
    int top = y;
    if (nav.settingsOpen) {
      paint.label(HudNavigator.label(nav.module()), x + 3, y, width - 6, row, Tone.TEXT);
      top += row;
      if (settings.settings.isEmpty())
        paint.label("No settings", x + 3, top, width - 6, row, Tone.MUTED);
      for (int i = 0; i < settings.count; i++) {
        int index = settings.first + i, heightOfRow = settings.heights[index];
        Setting<?> setting = settings.settings.get(index);
        boolean selected = index == nav.settingSelected;
        if (selected) paint.highlight(x, top, width, heightOfRow, true, nav.editing, false);
        boolean bool = setting.metadata().kind() == SettingMetadata.Kind.BOOLEAN;
        boolean stacked = settings.stacked[index];
        String value = settings.values.get(index);
        int valueWidth = paint.textWidth(value), band = stacked ? heightOfRow / 2 : heightOfRow;
        paint.label(
            settings.labels.get(index),
            x + 3,
            top,
            width - (bool ? paint.checkboxWidth() : stacked ? 6 : valueWidth + 9),
            band,
            Tone.TEXT);
        if (bool) paint.checkbox(x, top, width, heightOfRow, Boolean.TRUE.equals(setting.get()));
        else {
          if (selected && nav.textEditing())
            value = HudNavigator.tail(value, width - 6, paint::textWidth);
          paint.label(
              value,
              stacked ? x + 3 : x + width - valueWidth - 3,
              top + (stacked ? band : 0),
              width - 6,
              stacked ? heightOfRow - band : heightOfRow,
              selected && nav.editing ? Tone.EDITING : Tone.MUTED);
        }
        top += heightOfRow;
      }
    } else {
      if (modules.modules.isEmpty())
        paint.label("No modules", x + 3, top, width - 6, row, Tone.MUTED);
      for (int i = 0; i < modules.count; i++) {
        int index = modules.first + i;
        ModuleEntry module = modules.modules.get(index);
        boolean selected = index == nav.selected, on = module.requestedEnabled();
        if (selected || on) paint.highlight(x, top, width, row, selected, false, on);
        paint.label(
            HudNavigator.label(module),
            x + 3,
            top,
            width - 6,
            row,
            module.state() == ModuleState.FAULTED ? Tone.ERROR : on ? Tone.ENABLED : Tone.TEXT);
        top += row;
      }
    }
    if (!nav.error.isEmpty())
      paint.label(nav.error, x + 3, y + height - row, width - 6, row, Tone.ERROR);
  }
}
