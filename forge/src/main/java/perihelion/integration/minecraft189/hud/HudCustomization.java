package perihelion.integration.minecraft189.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.gui.GuiTheme;
import perihelion.integration.minecraft189.gui.modern.ModernTheme;
import perihelion.integration.minecraft189.gui.widget.*;

/** Screen-owned popup, sharing the native setting controls and transparent inline editor. */
final class HudCustomization implements SettingControl.Actions {
  private static final float INSET = 5, TOP_PAD = 22;
  private final boolean[] advanced = new boolean[3];
  private boolean hasAdvanced;
  private int section;
  private final int sections;
  private final Tooltip tooltip = new Tooltip();
  private final HudModule hud;
  private final Minecraft mc = Minecraft.getMinecraft();
  private final List<SettingControl> controls = new ArrayList<>();
  private final InlineTextFieldView inline = new InlineTextFieldView();
  private final UiCanvas canvas;
  final HudModule.Element element;
  private SettingControl captured, pressed, editing;
  private GuiTextField field;
  private Consumer<String> submit;
  private float x, y, w, h, scroll, editX, editY, editWidth, footer;
  private long caretEpoch;
  private String error = "";

  HudCustomization(HudModule hud, HudModule.Element element, UiCanvas canvas) {
    this.hud = hud;
    this.element = element;
    hud.customizing = element;
    this.canvas = canvas;
    for (Setting<?> setting : hud.options(element))
      controls.add(SettingControl.create(hud.runtime.modules(), "hud", setting, this));
    sections = controls.size() > hud.styles.get(element).settings.size() ? 3 : 2;
  }

  private boolean inSection(Setting<?> setting) {
    HudElementStyle style = hud.styles.get(element);
    if (setting == hud.moduleRows) return section == 1;
    boolean common = style.settings.contains(setting);
    return section == 0
        ? common && style.textOption(setting)
        : section == 1 ? common && !style.textOption(setting) : !common;
  }

  private boolean extra(Setting<?> setting) {
    HudElementStyle style = hud.styles.get(element);
    if (section == 0) return setting == style.bold || setting == style.shadow;
    if (section == 1)
      return setting == style.padding
          || setting == style.direction
          || setting == style.panelShadow
          || setting == style.shadowOpacity
          || setting == style.shadowSpread;
    if (element == HudModule.Element.MODULES)
      return setting == hud.moduleAlignment
          || setting == hud.moduleSort
          || setting == hud.moduleBorder
          || setting == hud.moduleSuffix
          || setting == hud.moduleLowercase;
    if (element == HudModule.Element.PLAYERS)
      return !(setting == hud.playerColumns
          || setting == hud.playerLimit
          || setting == hud.playerRange
          || setting == hud.overlay.source
          || setting == hud.overlay.includeSelf
          || setting == hud.overlay.sort);
    return false;
  }

  private boolean visible(SettingControl control) {
    return control.setting.isVisible()
        && inSection(control.setting)
        && (!extra(control.setting) || advanced[section]);
  }

  private float contentHeight() {
    float result = 0;
    for (SettingControl control : controls) if (visible(control)) result += control.height();
    return result;
  }

  void draw(int anchorX, int anchorY, int width, int height, int mx, int my) {
    w = Math.min(172, width - 12);
    for (SettingControl control : controls)
      control.measure(canvas.font, w - INSET * 2, canvas.modern);
    hasAdvanced = false;
    for (SettingControl control : controls)
      if (inSection(control.setting) && extra(control.setting)) hasAdvanced = true;
    footer = (hasAdvanced ? 20 : 4) + (error.isEmpty() ? 0 : 14);
    h = Math.min(height - 8, contentHeight() + GuiTheme.HEADER + TOP_PAD + footer);
    x = Math.max(6, Math.min(width - w - 6, anchorX));
    y = Math.max(4, Math.min(height - h - 4, anchorY));
    float available = Math.max(1, h - GuiTheme.HEADER - TOP_PAD - footer);
    scroll = Math.max(0, Math.min(Math.max(0, contentHeight() - available), scroll));
    if (editing != null && !visible(editing)) cancelEdit();
    if (canvas.classic)
      perihelion.integration.minecraft189.gui.raven.RavenTheme.frame(canvas, x, y, w, h, true);
    else if (canvas.modern) ModernTheme.surface(canvas, x, y, w, h, ModernTheme.PANEL);
    else canvas.rect(x, y, w, h, GuiTheme.overlay(canvas));
    GuiTheme.header(canvas, element.label, x, y, w);
    canvas.text(
        "<",
        x + w - 11,
        canvas.modern ? canvas.font.centeredY(y, GuiTheme.HEADER) : y + 1,
        canvas.text);
    String[] tabs = {"Text", "Panel", element == HudModule.Element.MODULES ? "Layout" : "Content"};
    for (int i = 0; i < sections; i++)
      GuiTheme.button(
          canvas,
          tabs[i],
          x + INSET + i * (w - INSET * 2) / sections,
          y + GuiTheme.HEADER + 2,
          (w - INSET * 2) / sections - 2,
          16,
          section == i,
          true,
          UiCanvas.inside(
              mx,
              my,
              x + INSET + i * (w - INSET * 2) / sections,
              y + GuiTheme.HEADER + 2,
              (w - INSET * 2) / sections - 2,
              16));
    double sx = mc.displayWidth / (double) width, sy = mc.displayHeight / (double) height;
    GL11.glEnable(GL11.GL_SCISSOR_TEST);
    GL11.glScissor(
        (int) (x * sx),
        (int) ((height - y - h + footer) * sy),
        (int) (w * sx),
        (int) (available * sy));
    float top = y + GuiTheme.HEADER + TOP_PAD - scroll;
    String hint = null;
    for (SettingControl control : controls) {
      if (!visible(control)) continue;
      control.place(x + INSET, top, w - INSET * 2);
      if (top + control.height() > y + GuiTheme.HEADER + TOP_PAD && top < y + h - footer) {
        if (my >= y + GuiTheme.HEADER + TOP_PAD
            && my < y + h - footer
            && UiCanvas.inside(mx, my, x + INSET, top, w - INSET * 2, control.height())) {
          hint = control.setting.description();
          canvas.rect(x + INSET, top, w - INSET * 2, control.height(), canvas.hover);
        }
        control.render(
            canvas, x + INSET, top, w - INSET * 2, control == editing ? editY : Float.NaN, editX);
      }
      top += control.height();
    }
    if (field != null)
      inline.draw(
          canvas, field, editX, canvas.font.centeredY(editY, 12) - 1, editWidth, caretEpoch);
    GL11.glDisable(GL11.GL_SCISSOR_TEST);
    canvas.label(error.isEmpty() ? "" : error, x + 4, y + h - 14, w - 8, canvas.muted);
    if (contentHeight() > available) {
      float thumb = available * available / contentHeight();
      canvas.verticalHairline(
          x + w - 2,
          y
              + GuiTheme.HEADER
              + TOP_PAD
              + (available - thumb) * scroll / (contentHeight() - available),
          thumb,
          canvas.accent);
    }
    if (hasAdvanced) {
      float fy = y + h - footer + 2;
      float primaryWidth =
          advanced[section] ? (w - INSET * 2 - 4) / 2 : w - INSET * 2;
      GuiTheme.button(
          canvas,
          advanced[section] ? "Less options" : "Advanced",
          x + INSET,
          fy,
          primaryWidth,
          15,
          advanced[section],
          true,
          UiCanvas.inside(mx, my, x + INSET, fy, primaryWidth, 15));
      if (advanced[section])
        GuiTheme.button(
            canvas,
            "Reset look",
            x + w / 2 + 2,
            fy,
            (w - INSET * 2 - 4) / 2,
            15,
            false,
            true,
            UiCanvas.inside(mx, my, x + w / 2 + 2, fy, (w - INSET * 2 - 4) / 2, 15));
    }
    if (field == null && captured == null) tooltip.draw(canvas, hint, mx, my, width, height);
    else tooltip.clear();
  }

  boolean contains(int mx, int my) {
    return UiCanvas.inside(mx, my, x, y, w, h);
  }

  boolean back(int mx, int my) {
    return UiCanvas.inside(mx, my, x, y, w, GuiTheme.HEADER);
  }

  void click(int mx, int my, int button) {
    tooltip.clear();
    float fy = y + h - footer + 2;
    float primaryWidth =
        advanced[section] ? (w - INSET * 2 - 4) / 2 : w - INSET * 2;
    boolean primary = UiCanvas.inside(mx, my, x + INSET, fy, primaryWidth, 15);
    boolean reset =
        advanced[section]
            && UiCanvas.inside(mx, my, x + w / 2 + 2, fy, (w - INSET * 2 - 4) / 2, 15);
    if (hasAdvanced && button == 0 && (primary || reset)) {
      cancelEdit();
      release();
      scroll = 0;
      if (reset) resetLook();
      else advanced[section] = !advanced[section];
      return;
    }
    if (my >= y + GuiTheme.HEADER && my < y + GuiTheme.HEADER + TOP_PAD && button == 0) {
      int next =
          Math.max(
              0, Math.min(sections - 1, (int) ((mx - x - INSET) / ((w - INSET * 2) / sections))));
      if (next != section) {
        cancelEdit();
        release();
        section = next;
        scroll = 0;
      }
      return;
    }
    if (field != null) {
      if (UiCanvas.inside(mx, my, editX, editY, editWidth, 16)) {
        inline.placeCursor(canvas, field, editX, editWidth, mx);
        caretEpoch = System.currentTimeMillis();
        return;
      }
      if (!apply()) return;
    }
    if (my < y + GuiTheme.HEADER + TOP_PAD || my >= y + h - footer) return;
    float top = y + GuiTheme.HEADER + TOP_PAD - scroll;
    for (SettingControl control : controls) {
      if (!visible(control)) continue;
      if (UiCanvas.inside(mx, my, x + INSET, top, w - INSET * 2, control.height())) {
        control.place(x + INSET, top, w - INSET * 2);
        pressed = control;
        try {
          if (control.press(mx, my, button)) captured = control;
        } finally {
          pressed = null;
        }
        return;
      }
      top += control.height();
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void resetLook() {
    for (Setting setting : hud.styles.get(element).settings)
      hud.runtime.modules().setSetting("hud", setting, setting.defaultValue());
    if (element == HudModule.Element.MODULES)
      for (Setting setting :
          new Setting[] {
            hud.moduleSpacing,
            hud.modulePadding,
            hud.moduleRows,
            hud.moduleAlignment,
            hud.moduleSort,
            hud.moduleBorder,
            hud.moduleSuffix,
            hud.moduleLowercase
          }) hud.runtime.modules().setSetting("hud", setting, setting.defaultValue());
  }

  void drag(int mx, int my) {
    if (captured != null) {
      if (!visible(captured)) captured = null;
      else captured.drag(mx, my);
    }
  }

  void release() {
    captured = null;
  }

  void wheel(int delta) {
    tooltip.clear();
    if (delta == 0) return;
    if (field != null && !apply()) return;
    captured = null;
    scroll = Math.max(0, scroll + (delta > 0 ? -32 : 32));
  }

  @Override
  public void edit(
      String label, String initial, float x, float y, float width, Consumer<String> submit) {
    this.submit = submit;
    editing = pressed;
    editY = y;
    editX = x + Math.min(canvas.font.width(label) + 9, width - 64);
    editWidth = Math.max(12, width - (editX - x) - 2);
    field = new GuiTextField(2, mc.fontRendererObj, (int) editX, (int) y + 2, (int) editWidth, 12);
    field.setEnableBackgroundDrawing(false);
    field.setMaxStringLength(Math.max(2048, initial.length()));
    field.setText(initial);
    field.setFocused(true);
    field.setCursorPositionEnd();
    field.setSelectionPos(0);
    captured = null;
    inline.reset();
    caretEpoch = System.currentTimeMillis();
  }

  void tick() {
    if (field != null) field.updateCursorCounter();
  }

  /** Returns true while a text edit consumes the key, including its first Escape. */
  boolean type(char character, int code) {
    if (field == null) return false;
    if (code == Keyboard.KEY_ESCAPE) cancelEdit();
    else if (code == Keyboard.KEY_RETURN || code == Keyboard.KEY_NUMPADENTER) apply();
    else if (field.textboxKeyTyped(character, code)) {
      error = "";
      caretEpoch = System.currentTimeMillis();
    }
    return true;
  }

  private boolean apply() {
    try {
      submit.accept(field.getText());
      cancelEdit();
      return true;
    } catch (IllegalArgumentException invalid) {
      error = invalid.getMessage() == null ? "Invalid value" : invalid.getMessage();
      return false;
    }
  }

  private void cancelEdit() {
    field = null;
    editing = null;
    submit = null;
    error = "";
    inline.reset();
  }

  void close() {
    tooltip.clear();
    if (hud.customizing == element) hud.customizing = null;
    release();
    cancelEdit();
  }
}
