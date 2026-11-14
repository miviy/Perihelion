package perihelion.integration.minecraft189.gui.widget;

import perihelion.core.module.ModuleManager;
import perihelion.core.setting.Setting;
import perihelion.integration.minecraft189.gui.GuiValues;
import perihelion.integration.minecraft189.gui.font.GuiFonts;
import perihelion.integration.minecraft189.gui.modern.GuiMotion;

/** A measured disclosure row. Its state belongs to the screen, never the working config. */
public final class SettingSection extends SettingControl {
  private final Setting<Boolean> enabled;
  private final float rowHeight;
  private final String modernLabel;
  private float enabledMotion, hoverMotion, arrowMotion;
  private long previousFrame;
  private SettingSection parent;

  public SettingSection(
      ModuleManager modules,
      String module,
      String title,
      String description,
      float rowHeight,
      Setting<Boolean> enabled) {
    super(modules, module, Setting.bool("section", title, description, false, () -> {}), null);
    this.rowHeight = rowHeight;
    this.enabled = enabled;
    enabledMotion = enabled != null && enabled.get() ? 1 : 0;
    modernLabel = GuiValues.moduleLabel(title);
  }

  public boolean isOpen() {
    return isVisible() && expanded;
  }

  public void parent(SettingSection parent) {
    this.parent = parent;
    visibleWhen(parent::isOpen);
  }

  @Override
  public String expansionKey() {
    return (parent == null ? "" : parent.expansionKey() + "/") + "section:" + setting.displayName();
  }

  public void open(boolean open) {
    expanded = open;
  }

  @Override
  public void measure(GuiFonts.Face face, float width, boolean modern) {
    super.measure(face, width, modern);
    headerHeight = rowHeight;
  }

  @Override
  protected boolean useFrontendPainter() {
    return false;
  }

  @Override
  protected void draw(UiCanvas c) {
    float on = enabled != null && enabled.get() ? 1 : 0;
    long now = System.nanoTime();
    float dt = previousFrame == 0 ? 1 / 60f : (now - previousFrame) / 1_000_000_000f;
    previousFrame = now;
    enabledMotion = GuiMotion.approach(enabledMotion, on, GuiMotion.weight(dt, 24));
    hoverMotion = GuiMotion.approach(hoverMotion, hovered ? 1 : 0, GuiMotion.weight(dt, 24));
    arrowMotion = GuiMotion.approach(arrowMotion, expanded ? 1 : 0, GuiMotion.weight(dt, 18));
    ModuleRowPainter.draw(
        c,
        c.modern ? modernLabel : label,
        x - 4,
        y,
        w + 8,
        headerHeight,
        c.modern ? enabledMotion : on,
        c.modern ? arrowMotion : expanded ? 1 : 0,
        c.modern ? hoverMotion : hovered ? 1 : 0,
        false,
        true,
        1);
  }

  @Override
  public boolean press(float mx, float my, int button) {
    if (button == 0 && enabled != null) put(enabled, !enabled.get());
    else if (button == 1 || button == 0 && enabled == null) expanded = !expanded;
    return false;
  }

  public static SettingControl action(
      ModuleManager modules,
      String module,
      String title,
      String description,
      float rowHeight,
      Runnable action) {
    return new SettingControl(
        modules, module, Setting.bool("action", title, description, false, () -> {}), null) {
      @Override
      protected boolean useFrontendPainter() {
        return false;
      }

      @Override
      public void measure(GuiFonts.Face face, float width, boolean modern) {
        super.measure(face, width, modern);
        headerHeight = rowHeight;
      }

      @Override
      protected void draw(UiCanvas c) {
        ModuleRowPainter.draw(
            c,
            c.modern ? GuiValues.moduleLabel(label) : label,
            x - 4,
            y,
            w + 8,
            headerHeight,
            0,
            0,
            hovered ? 1 : 0,
            false,
            false,
            1);
      }

      @Override
      public boolean press(float mx, float my, int button) {
        if (button == 0) action.run();
        return false;
      }
    };
  }
}
