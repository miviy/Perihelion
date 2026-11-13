package perihelion.integration.minecraft189.gui.widget;

import java.util.*;
import java.util.function.Consumer;
import perihelion.core.module.ModuleManager;
import perihelion.core.setting.*;
import perihelion.integration.minecraft189.gui.GuiValues;
import perihelion.integration.minecraft189.gui.font.GuiFonts;

/** Metadata-selected reusable editors. Only transient expansion/drag geometry is local. */
public abstract class SettingControl {
  /** A frontend paints the measured editor without replacing its input or setting ownership. */
  public interface Painter {
    void paint(UiCanvas canvas, PaintView view);
  }

  /** Read-only paint access to the same layout used for scrolling, editing and hit testing. */
  public static final class PaintView {
    private final SettingControl control;

    private PaintView(SettingControl control) {
      this.control = control;
    }

    public void defaultPaint(UiCanvas canvas) {
      control.draw(canvas);
    }

    public Setting<?> setting() {
      return control.setting;
    }

    public String moduleId() {
      return control.module;
    }

    public String label() {
      return control.label;
    }

    public float x() {
      return control.x;
    }

    public float y() {
      return control.y;
    }

    public float width() {
      return control.w;
    }

    public float height() {
      return control.height();
    }

    public float headerHeight() {
      return control.headerHeight;
    }

    public float valueHeight() {
      return control.valueHeight;
    }

    public float lineHeight() {
      return control.lineHeight;
    }

    public boolean expanded() {
      return control.expanded;
    }

    public boolean stackedValue() {
      return control.stackedValue;
    }

    public List<String> labelLines() {
      return Collections.unmodifiableList(control.labelLines);
    }

    public boolean editing(float rowY) {
      return control.editing(rowY);
    }

    public float editingX() {
      return control.editingX;
    }

    public String summary() {
      return ((Choices) control).summary();
    }

    public List<String> valueLines() {
      return Collections.unmodifiableList(((Choices) control).valueLines);
    }

    public int optionCount() {
      return ((Choices) control).optionLabels.size();
    }

    public List<String> optionLines(int index) {
      return Collections.unmodifiableList(((Choices) control).optionLines.get(index));
    }

    public float optionHeight(int index) {
      return ((Choices) control).optionHeight(index);
    }

    public boolean optionSelected(int index) {
      return ((Choices) control).selected(control.setting.metadata().options().get(index));
    }

    public List<String> textLines() {
      return Collections.unmodifiableList(((Text) control).lines);
    }

    public float hue() {
      ColorPicker picker = (ColorPicker) control;
      float[] hsv = picker.hsv();
      if (hsv[1] > 0 && hsv[2] > 0) picker.hue = hsv[0];
      return picker.hue;
    }
  }

  public interface Actions {
    void edit(String label, String initial, float x, float y, float width, Consumer<String> submit);
  }

  public final Setting<?> setting;
  private final PaintView paintView = new PaintView(this);
  protected final String label;
  protected final ModuleManager modules;
  protected final String module;
  protected final Actions actions;
  protected boolean expanded;
  protected float x, y, w;
  private float editingY = Float.NaN, editingX;
  public static final float ROW = 14;
  protected float lineHeight = 10, valueHeight;
  protected List<String> labelLines = Collections.singletonList("");
  protected float headerHeight = ROW;
  protected boolean stackedValue;
  protected GuiFonts.Face measuredFace;
  private float measuredWidth;
  private Object measuredValue;
  private boolean measuredModern;

  public SettingControl(ModuleManager modules, String module, Setting<?> setting, Actions actions) {
    this.modules = modules;
    this.module = module;
    this.setting = setting;
    this.actions = actions;
    this.label = GuiValues.readable(setting.displayName());
  }

  public static SettingControl create(
      ModuleManager modules, String module, Setting<?> setting, Actions actions) {
    switch (setting.metadata().kind()) {
      case BOOLEAN:
        return new Toggle(modules, module, setting, actions);
      case INTEGER:
      case DECIMAL:
        return new NumberSlider(modules, module, setting, actions);
      case ENUM:
      case MULTI_SELECT:
        return new Choices(modules, module, setting, actions);
      case RANGE:
        return new Range(modules, module, setting, actions);
      case COLOR:
        return new ColorPicker(modules, module, setting, actions);
      default:
        return new Text(modules, module, setting, actions);
    }
  }

  public float height() {
    return headerHeight;
  }

  /** Lay out within the panel, before content height, scrolling and hit testing are computed. */
  public void measure(GuiFonts.Face face, float width) {
    measure(face, width, false);
  }

  public void measure(GuiFonts.Face face, float width, boolean modern) {
    Object current = setting.get();
    if (measuredFace == face
        && measuredWidth == width
        && measuredModern == modern
        && Objects.equals(measuredValue, current)) return;
    measuredFace = face;
    measuredWidth = width;
    measuredValue = current;
    measuredModern = modern;
    lineHeight = Math.max(8, (float) Math.ceil(face.height()) + 1);
    valueHeight = 0;
    float reserved = 16;
    String value = "";
    switch (setting.metadata().kind()) {
      case BOOLEAN:
        reserved = modern ? 20 : 6;
        break;
      case INTEGER:
      case DECIMAL:
        // Account for the bounds as well as the current value when deciding to stack.
        NumericConstraints n = setting.metadata().numeric().get();
        float digits =
            Math.max(
                face.width(GuiValues.number(n.minimum())),
                face.width(GuiValues.number(n.maximum())));
        digits =
            Math.max(digits, face.width(GuiValues.number(((Number) setting.get()).doubleValue())));
        stackedValue = face.width(label) + digits + 11 > width;
        reserved = stackedValue ? 6 : digits + 11;
        break;
      case ENUM:
      case MULTI_SELECT:
        value = ((Choices) this).summary();
        float choiceInset = modern ? 21 : 19;
        stackedValue = face.width(label) + face.width(value) + choiceInset > width;
        reserved = stackedValue ? modern ? 18 : 16 : face.width(value) + choiceInset;
        break;
      case COLOR:
        reserved = 30;
        break;
      default:
        break;
    }
    labelLines = UiCanvas.wrap(face, label, Math.max(1, width - reserved));
    headerHeight =
        Math.max(ROW, (float) Math.ceil((labelLines.size() - 1) * lineHeight + face.height() + 5));
    if (stackedValue) {
      if (this instanceof Choices) {
        Choices choices = (Choices) this;
        choices.valueLines = UiCanvas.wrap(face, value, Math.max(1, width - 6));
        valueHeight = choices.valueLines.size() * lineHeight;
      } else valueHeight = ROW - 2;
      headerHeight += valueHeight;
    }
    if (this instanceof Choices) ((Choices) this).measureOptions(face, width, modern);
    if (this instanceof Text) ((Text) this).measureText(face, width);
  }

  protected final void drawLines(UiCanvas c, List<String> lines, float left, float top, int color) {
    for (int i = 0; i < lines.size(); i++) c.text(lines.get(i), left, top + i * lineHeight, color);
  }

  protected final void drawLabel(UiCanvas c) {
    float band = headerHeight - valueHeight - (labelLines.size() - 1) * lineHeight;
    drawLines(c, labelLines, x + 3, c.font.centeredY(y, band), c.text);
  }

  public final void place(float x, float y, float width) {
    this.x = x;
    this.y = y;
    this.w = width;
  }

  public final String moduleId() {
    return module;
  }

  public final void render(UiCanvas canvas, float x, float y, float width) {
    render(canvas, x, y, width, Float.NaN, 0);
  }

  public final void render(
      UiCanvas canvas, float x, float y, float width, float rowY, float textX) {
    measure(canvas.font, width, canvas.modern);
    place(x, y, width);
    editingY = rowY;
    editingX = textX;
    try {
      if (canvas.settingPainter != null) {
        canvas.settingPainter.paint(canvas, paintView);
        return;
      }
      draw(canvas);
    } finally {
      editingY = Float.NaN;
    }
  }

  protected final boolean editing(float rowY) {
    return Math.abs(editingY - rowY) < 0.25f;
  }

  protected final void editingLabel(UiCanvas c, String name, float rowY) {
    c.label(name, x + 3, c.font.centeredY(rowY, 12), Math.max(0, editingX - x - 6), c.text);
  }

  protected abstract void draw(UiCanvas c);

  /** True captures dragging until mouse release, even outside component bounds. */
  public abstract boolean press(float mx, float my, int button);

  public void drag(float mx, float my) {}

  public void release() {}

  protected final boolean modern() {
    return measuredModern;
  }

  protected <T> void put(Setting<T> target, T value) {
    modules.setSetting(module, target, value);
  }

  @SuppressWarnings("unchecked")
  protected void value(Object value) {
    put((Setting<Object>) setting, value);
  }

  protected void parse(String text) {
    value(setting.parse(text));
  }

  protected void edit() {
    editAt(y);
  }

  protected void editAt(float rowY) {
    actions.edit(label, String.valueOf(setting.get()), x, rowY, w, this::parse);
  }

  protected double fraction(float mx) {
    return Math.max(0, Math.min(1, (mx - x - 3) / (w - 6)));
  }

  protected void title(UiCanvas c, String value) {
    drawLabel(c);
    c.text(value, x + w - 9, c.font.centeredY(y, headerHeight), c.accent);
  }

  private static final class Toggle extends SettingControl {
    Toggle(ModuleManager m, String id, Setting<?> s, Actions a) {
      super(m, id, s, a);
    }

    protected void draw(UiCanvas c) {
      boolean on = Boolean.TRUE.equals(setting.get());
      if (!c.modern && on) c.rect(x, y, w, headerHeight, c.selection);
      float band = headerHeight - (labelLines.size() - 1) * lineHeight;
      drawLines(c, labelLines, x + 3, c.font.centeredY(y, band), on ? c.selectedText : c.text);
      if (c.modern) {
        float top = y + (headerHeight - 7) / 2;
        c.outline(x + w - 11, top, 7, 7, on ? c.accent : 0xff5c5c60);
        if (on) c.rect(x + w - 9, top + 2, 3, 3, c.accent);
      }
    }

    public boolean press(float mx, float my, int button) {
      if (button == 0) value(!((Boolean) setting.get()));
      return false;
    }
  }

  private static final class NumberSlider extends SettingControl {
    NumberSlider(ModuleManager m, String id, Setting<?> s, Actions a) {
      super(m, id, s, a);
    }

    protected void draw(UiCanvas c) {
      if (editing(y)) {
        editingLabel(c, label, y);
        return;
      }
      NumericConstraints n = setting.metadata().numeric().get();
      double v = ((Number) setting.get()).doubleValue();
      if (stackedValue) drawLabel(c);
      c.slider(
          stackedValue ? "" : label,
          GuiValues.number(v),
          x,
          y + headerHeight - ROW,
          w,
          n.maximum() == n.minimum() ? 0 : (v - n.minimum()) / (n.maximum() - n.minimum()));
    }

    public boolean press(float mx, float my, int button) {
      if (button == 1) {
        edit();
        return false;
      }
      if (button != 0) return false;
      drag(mx, my);
      return true;
    }

    public void drag(float mx, float my) {
      double v = GuiValues.snap(setting.metadata().numeric().get(), fraction(mx));
      if (setting.metadata().kind() == SettingMetadata.Kind.INTEGER) value((int) v);
      else value(v);
    }
  }

  private static final class Choices extends SettingControl {
    private final List<String> optionLabels = new ArrayList<>();
    private final List<List<String>> optionLines = new ArrayList<>();
    private List<String> valueLines = Collections.singletonList("");

    private String summary() {
      Object current = setting.get();
      if (current instanceof perihelion.integration.minecraft189.gui.ClickGuiModule.Font)
        return ((perihelion.integration.minecraft189.gui.ClickGuiModule.Font) current)
            .displayName();
      if (current instanceof Set)
        return ((Set<?>) current).size() + "/" + setting.metadata().options().size();
      int index = setting.metadata().options().indexOf(String.valueOf(current));
      return index >= 0 ? optionLabels.get(index) : GuiValues.readable(String.valueOf(current));
    }

    private void measureOptions(GuiFonts.Face face, float width, boolean modern) {
      optionLines.clear();
      for (String option : optionLabels)
        optionLines.add(UiCanvas.wrap(face, option, Math.max(1, width - (modern ? 25 : 12))));
    }

    private float optionHeight(int index) {
      return Math.max(
          ROW, (optionLines.get(index).size() - 1) * lineHeight + measuredFace.height() + 5);
    }

    Choices(ModuleManager m, String id, Setting<?> s, Actions a) {
      super(m, id, s, a);
      for (String option : s.metadata().options())
        optionLabels.add(
            s.get() instanceof perihelion.integration.minecraft189.gui.ClickGuiModule.Font
                ? perihelion.integration.minecraft189.gui.ClickGuiModule.Font.valueOf(
                        option.toUpperCase(Locale.ROOT))
                    .displayName()
                : GuiValues.optionLabel(s, option));
    }

    public float height() {
      float height = headerHeight;
      if (expanded) for (int i = 0; i < optionLines.size(); i++) height += optionHeight(i);
      return height;
    }

    protected void draw(UiCanvas c) {
      String value = summary();
      drawLabel(c);
      if (stackedValue)
        drawLines(
            c,
            valueLines,
            x + 3,
            c.font.centeredY(y + headerHeight - valueHeight, lineHeight),
            c.enabled);
      else
        c.text(
            value, x + w - c.font.width(value) - 13, c.font.centeredY(y, headerHeight), c.enabled);
      c.text(expanded ? "-" : "+", x + w - 9, c.font.centeredY(y, headerHeight), c.accent);
      if (expanded) {
        float rowY = y + headerHeight;
        for (int i = 0; i < optionLabels.size(); i++) {
          boolean selected = selected(setting.metadata().options().get(i));
          float height = optionHeight(i);
          if (selected) c.rect(x + 3, rowY, w - 6, height, c.selection);
          int color = selected ? c.selectedText : c.text;
          float band = height - (optionLines.get(i).size() - 1) * lineHeight;
          drawLines(c, optionLines.get(i), x + 6, c.font.centeredY(rowY, band), color);
          rowY += height;
        }
      }
    }

    private boolean selected(String option) {
      Object value = setting.get();
      if (value instanceof Set) {
        for (Object item : (Set<?>) value)
          if (((Enum<?>) item).name().equalsIgnoreCase(option)) return true;
        return false;
      }
      return ((Enum<?>) value).name().equalsIgnoreCase(option);
    }

    public boolean press(float mx, float my, int button) {
      if (button != 0 && button != 1) return false;
      if (my < y + headerHeight) {
        expanded = modern() && button == 0 ? true : !expanded;
        return false;
      }
      if (!expanded) return false;
      if (modern() && button == 1) {
        expanded = false;
        return false;
      }
      float offset = my - y - headerHeight;
      int row = 0;
      while (row < optionLines.size() && offset >= optionHeight(row)) offset -= optionHeight(row++);
      if (row >= optionLines.size()) return false;
      String option = setting.metadata().options().get(row);
      if (setting.metadata().kind() == SettingMetadata.Kind.ENUM) {
        parse(option);
        if (!modern()) expanded = false;
      } else {
        List<String> names = new ArrayList<>();
        for (String candidate : setting.metadata().options())
          if (selected(candidate) ^ candidate.equals(option)) names.add(candidate);
        parse(String.join(",", names));
      }
      return false;
    }
  }

  private static final class Range extends SettingControl {
    private int endpoint;

    Range(ModuleManager m, String id, Setting<?> s, Actions a) {
      super(m, id, s, a);
    }

    public float height() {
      return headerHeight + (expanded ? 32 : 0);
    }

    protected void draw(UiCanvas c) {
      if (editing(y)) editingLabel(c, label, y);
      else title(c, expanded ? "-" : "+");
      if (expanded) {
        float contentY = y + headerHeight - 16;
        DoubleRange r = (DoubleRange) setting.get();
        NumericConstraints n = setting.metadata().numeric().get();
        double span = n.maximum() - n.minimum();
        if (editing(contentY + 16)) editingLabel(c, label, contentY + 16);
        else
          c.slider(
              "Min",
              GuiValues.number(r.lower()),
              x,
              contentY + 16,
              w,
              span == 0 ? 0 : (r.lower() - n.minimum()) / span);
        if (editing(contentY + 32)) editingLabel(c, label, contentY + 32);
        else
          c.slider(
              "Max",
              GuiValues.number(r.upper()),
              x,
              contentY + 32,
              w,
              span == 0 ? 0 : (r.upper() - n.minimum()) / span);
      }
    }

    public boolean press(float mx, float my, int button) {
      if (button == 1) {
        editAt(
            my < y + headerHeight ? y : y + headerHeight + (my < y + headerHeight + 16 ? 0 : 16));
        return false;
      }
      if (button != 0) return false;
      if (my < y + headerHeight) {
        expanded = !expanded;
        return false;
      }
      endpoint = my < y + headerHeight + 16 ? 0 : 1;
      drag(mx, my);
      return true;
    }

    public void drag(float mx, float my) {
      DoubleRange r = (DoubleRange) setting.get();
      double v = GuiValues.snap(setting.metadata().numeric().get(), fraction(mx));
      value(
          endpoint == 0
              ? new DoubleRange(Math.min(v, r.upper()), r.upper())
              : new DoubleRange(r.lower(), Math.max(v, r.lower())));
    }
  }

  private static final class Text extends SettingControl {
    private List<String> lines = Collections.singletonList("");

    Text(ModuleManager m, String id, Setting<?> s, Actions a) {
      super(m, id, s, a);
    }

    private void measureText(GuiFonts.Face face, float width) {
      lines = UiCanvas.wrap(face, label + ": " + setting.get(), Math.max(1, width - 6));
    }

    @Override
    public float height() {
      return Math.max(ROW, (lines.size() - 1) * lineHeight + measuredFace.height() + 5);
    }

    protected void draw(UiCanvas c) {
      if (editing(y)) editingLabel(c, label, y);
      else
        drawLines(
            c,
            lines,
            x + 3,
            c.font.centeredY(y, height() - (lines.size() - 1) * lineHeight),
            c.text);
    }

    public boolean press(float mx, float my, int button) {
      if (button == 0 || button == 1) edit();
      return false;
    }
  }

  /** Reusable for every ColorValue setting, including future gameplay settings. */
  public static final class ColorPicker extends SettingControl {
    private int part = -1;
    private float hue;
    private int compactRgbSeen;
    private final float[] hsvValues = new float[3];
    private int compactHexRgb = -1;
    private String compactHexText;

    ColorPicker(ModuleManager m, String id, Setting<?> s, Actions a) {
      super(m, id, s, a);
      hue = hsv()[0];
      compactRgbSeen = color().argb() & 0xffffff;
    }

    public float height() {
      return headerHeight + (expanded ? compact() ? 75 + compactHexHeight() : 176 : 0);
    }

    private boolean compact() {
      return modern() && module.equals("themes");
    }

    private float compactHexHeight() {
      return Math.max(16, measuredFace == null ? 16 : (float) Math.ceil(measuredFace.height()) + 6);
    }

    private float compactTop() {
      return y + headerHeight;
    }

    private float compactHexEditTop() {
      return compactTop() + 72 + (compactHexHeight() - 12) / 2 + 1;
    }

    private float[] compactHsv() {
      float[] hsv = hsv();
      int rgb = color().argb() & 0xffffff;
      if (compactRgbSeen != rgb) {
        if (hsv[1] > 0 && hsv[2] > 0) hue = hsv[0];
        compactRgbSeen = rgb;
      }
      return hsv;
    }

    private ColorValue color() {
      return (ColorValue) setting.get();
    }

    private float[] hsv() {
      ColorValue c = color();
      return java.awt.Color.RGBtoHSB(c.red(), c.green(), c.blue(), hsvValues);
    }

    protected void draw(UiCanvas c) {
      if (compact()) {
        drawCompact(c);
        return;
      }
      if (editing(y)) editingLabel(c, label, y);
      else {
        title(c, expanded ? "-" : "+");
        c.rect(
            x + w - 25,
            y + (headerHeight - 7) / 2,
            9,
            7,
            color().resolvedArgb(System.currentTimeMillis()));
      }
      if (!expanded) return;
      float contentY = y + headerHeight - 16;
      float[] hsv = hsv();
      if (hsv[1] > 0 && hsv[2] > 0) hue = hsv[0];
      int hueColor = java.awt.Color.HSBtoRGB(hue, 1, 1);
      c.gradient(x + 3, contentY + 18, w - 6, 44, 0xffffffff, hueColor, hueColor, 0xffffffff);
      c.gradient(x + 3, contentY + 18, w - 6, 44, 0x00000000, 0x00000000, 0xff000000, 0xff000000);
      float px = x + 3 + hsv[1] * (w - 6), py = contentY + 18 + (1 - hsv[2]) * 44;
      c.rect(px - 2, py - 2, 4, 4, 0xff202020);
      c.rect(px - 1, py - 1, 2, 2, 0xffffffff);
      for (int i = 0; i < 6; i++)
        c.gradient(
            x + 3 + (w - 6) * i / 6,
            contentY + 65,
            (w - 6) / 6 + 0.2f,
            9,
            java.awt.Color.HSBtoRGB(i / 6f, 1, 1),
            java.awt.Color.HSBtoRGB((i + 1) / 6f, 1, 1),
            java.awt.Color.HSBtoRGB((i + 1) / 6f, 1, 1),
            java.awt.Color.HSBtoRGB(i / 6f, 1, 1));
      c.rect(x + 3 + hue * (w - 6), contentY + 64, 1, 11, 0xffffffff);
      int[] channels = {color().red(), color().green(), color().blue(), color().alpha()};
      String[] labels = {"Red", "Green", "Blue", "Alpha"};
      for (int i = 0; i < 4; i++) {
        float rowY = contentY + 78 + i * 16;
        if (editing(rowY)) editingLabel(c, labels[i], rowY);
        else c.slider(labels[i], String.valueOf(channels[i]), x, rowY, w, channels[i] / 255.0);
      }
      if (color().rainbow()) c.rect(x, contentY + 142, w, 16, c.selection);
      c.label(
          "Rainbow",
          x + 3,
          c.font.centeredY(contentY + 142, 16),
          w - 6,
          color().rainbow() ? c.selectedText : c.text);
      if (editing(contentY + 158)) editingLabel(c, "Speed", contentY + 158);
      else
        c.slider(
            "Speed",
            GuiValues.number(color().rainbowSpeed()),
            x,
            contentY + 158,
            w,
            (color().rainbowSpeed() - 0.1) / 9.9);
      if (editing(contentY + 176)) editingLabel(c, "Hex:", contentY + 176);
      else
        c.label(
            color().toString().toLowerCase(Locale.ROOT),
            x + 3,
            c.font.centeredY(contentY + 176, 12),
            w - 6,
            c.text);
    }

    private void drawCompact(UiCanvas c) {
      drawLabel(c);
      c.rect(x + w - 25, y + (headerHeight - 7) / 2, 9, 7, color().argb() | 0xff000000);
      perihelion.integration.minecraft189.gui.modern.ModernControls.disclosure(
          c, x + w - 5, y + headerHeight / 2, expanded);
      if (!expanded) return;
      float top = compactTop(), left = x + 3, width = w - 6;
      c.rect(x, top, w, height() - headerHeight, 0xff191817);
      float[] hsv = compactHsv();
      int hueColor = java.awt.Color.HSBtoRGB(hue, 1, 1);
      c.gradient(left, top + 3, width, 52, 0xffffffff, hueColor, hueColor, 0xffffffff);
      c.gradient(left, top + 3, width, 52, 0x00000000, 0x00000000, 0xff000000, 0xff000000);
      float px = left + hsv[1] * width, py = top + 3 + (1 - hsv[2]) * 52;
      c.rect(px - 2, py - 2, 4, 4, 0xff161616);
      c.rect(px - 1, py - 1, 2, 2, 0xffeeeeee);
      for (int i = 0; i < 6; i++)
        c.gradient(
            left + width * i / 6,
            top + 60,
            width / 6 + .25f / Math.max(.01f, c.pixelScale),
            7,
            java.awt.Color.HSBtoRGB(i / 6f, 1, 1),
            java.awt.Color.HSBtoRGB((i + 1) / 6f, 1, 1),
            java.awt.Color.HSBtoRGB((i + 1) / 6f, 1, 1),
            java.awt.Color.HSBtoRGB(i / 6f, 1, 1));
      c.verticalHairline(left + hue * width, top + 59, 9, 0xffeeeeee);
      float hexTop = top + 72, hexHeight = compactHexHeight();
      c.rect(left, hexTop, width, hexHeight, 0xff121211);
      if (editing(compactHexEditTop())) editingLabel(c, "Hex", compactHexEditTop());
      else c.label(compactHex(), x + 6, c.font.centeredY(hexTop, hexHeight), w - 12, c.text);
    }

    private String compactHex() {
      int rgb = color().argb() & 0xffffff;
      if (compactHexText == null || compactHexRgb != rgb) {
        compactHexRgb = rgb;
        compactHexText = String.format(Locale.ROOT, "#%06X", rgb);
      }
      return compactHexText;
    }

    private boolean pressCompact(float mx, float my, int button) {
      part = -1;
      if ((button != 0 && button != 1) || !UiCanvas.inside(mx, my, x, y, w, height())) return false;
      if (my < y + headerHeight) {
        expanded = button == 1 ? !expanded : true;
        return false;
      }
      if (!expanded) return false;
      float top = compactTop(), left = x + 3, width = w - 6;
      if (UiCanvas.inside(mx, my, left, top + 72, width, compactHexHeight())) {
        actions.edit(
            "Hex",
            compactHex(),
            x,
            compactHexEditTop(),
            w,
            text -> {
              String hex = text.trim();
              if (!hex.startsWith("#")) hex = "#" + hex;
              if (!hex.matches("#[0-9a-fA-F]{6}"))
                throw new IllegalArgumentException("Use #RRGGBB");
              value(ColorValue.parse(hex));
            });
        return false;
      }
      if (button != 0) return false;
      if (UiCanvas.inside(mx, my, left, top + 3, width, 52)) part = 0;
      else if (UiCanvas.inside(mx, my, left, top + 60, width, 7)) part = 1;
      else return false;
      drag(mx, my);
      return true;
    }

    public boolean press(float mx, float my, int button) {
      if (compact()) return pressCompact(mx, my, button);
      if (my < y + headerHeight) {
        if (button == 0) expanded = !expanded;
        else if (button == 1) hex(y);
        return false;
      }
      float contentY = y + headerHeight - 16;
      float dy = my - contentY;
      if (dy >= 176) {
        hex(contentY + 176);
        return false;
      }
      if (dy >= 142 && dy < 158) {
        if (button == 0) value(color().withRainbow(!color().rainbow(), color().rainbowSpeed()));
        return false;
      }
      part = dy < 64 ? 0 : dy < 78 ? 1 : dy < 142 ? 2 + (int) ((dy - 78) / 16) : 6;
      if (button == 1 && part >= 2) {
        final int channel = part;
        float rowY = channel == 6 ? contentY + 158 : contentY + 78 + (channel - 2) * 16;
        String name =
            channel == 6 ? "Speed" : new String[] {"Red", "Green", "Blue", "Alpha"}[channel - 2];
        actions.edit(
            name,
            channel == 6
                ? GuiValues.number(color().rainbowSpeed())
                : String.valueOf(channelValue(channel - 2)),
            x,
            rowY,
            w,
            s -> {
              double v = Double.parseDouble(s);
              if (channel == 6) value(color().withRainbow(color().rainbow(), v));
              else {
                if (v != Math.rint(v) || v < 0 || v > 255)
                  throw new IllegalArgumentException("Use 0 through 255");
                setChannel(channel - 2, (int) v);
              }
            });
        return false;
      }
      if (button != 0) return false;
      drag(mx, my);
      return true;
    }

    private void hex(float rowY) {
      actions.edit(
          rowY == y ? label : "Hex",
          color().toString().toLowerCase(Locale.ROOT),
          x,
          rowY,
          w,
          s -> {
            ColorValue parsed = ColorValue.parse(s);
            value(parsed.withRainbow(color().rainbow(), color().rainbowSpeed()));
          });
    }

    private int channelValue(int index) {
      ColorValue c = color();
      return index == 0 ? c.red() : index == 1 ? c.green() : index == 2 ? c.blue() : c.alpha();
    }

    private void setChannel(int index, int v) {
      ColorValue c = color();
      value(
          ColorValue.rgba(
                  index == 0 ? v : c.red(),
                  index == 1 ? v : c.green(),
                  index == 2 ? v : c.blue(),
                  index == 3 ? v : c.alpha())
              .withRainbow(c.rainbow(), c.rainbowSpeed()));
    }

    public void drag(float mx, float my) {
      if (part < 0) return;
      if (compact()) {
        float[] hsv = compactHsv();
        float f = (float) fraction(mx);
        if (part == 0) {
          hsv[1] = f;
          hsv[2] = Math.max(0, Math.min(1, 1 - (my - compactTop() - 3) / 52));
        } else hue = f;
        int rgb = java.awt.Color.HSBtoRGB(hue, hsv[1], hsv[2]);
        value(ColorValue.fromArgb(rgb | 0xff000000));
        compactRgbSeen = rgb & 0xffffff;
        return;
      }
      ColorValue c = color();
      float f = (float) fraction(mx);
      if (part == 6) {
        value(c.withRainbow(c.rainbow(), Math.round((0.1 + 9.9 * f) * 10) / 10.0));
        return;
      }
      if (part >= 2) {
        setChannel(part - 2, Math.round(f * 255));
        return;
      }
      float[] hsv = hsv();
      if (part == 0) {
        hsv[1] = f;
        hsv[2] = Math.max(0, Math.min(1, 1 - (my - y - headerHeight - 2) / 44));
      } else hue = f;
      int rgb = java.awt.Color.HSBtoRGB(hue, hsv[1], hsv[2]);
      value(
          ColorValue.fromArgb((c.alpha() << 24) | (rgb & 0xffffff))
              .withRainbow(c.rainbow(), c.rainbowSpeed()));
    }

    @Override
    public void release() {
      part = -1;
    }
  }
}
