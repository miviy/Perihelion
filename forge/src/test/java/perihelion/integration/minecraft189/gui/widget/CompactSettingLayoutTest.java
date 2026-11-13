package perihelion.integration.minecraft189.gui.widget;

import static org.junit.Assert.*;

import org.junit.Test;
import perihelion.core.concurrent.ClientTaskQueue;
import perihelion.core.diagnostics.ClientLog;
import perihelion.core.event.EventBus;
import perihelion.core.module.*;
import perihelion.core.setting.*;
import perihelion.integration.minecraft189.gui.GuiValues;
import perihelion.integration.minecraft189.gui.font.GuiFonts;

public class CompactSettingLayoutTest {
  private enum Mode {
    A_VERY_LONG_OPTION_NAME,
    SECOND
  }

  private static final GuiFonts.Face FACE =
      new GuiFonts.Face() {
        public float width(String value) {
          return value.codePointCount(0, value.length()) * 6;
        }

        public float height() {
          return 9;
        }

        public void draw(String value, float x, float y, int color) {
          fail("Measurement must not render");
        }
      };

  private static final class Fixture extends ClientModule {
    final Setting<Boolean> toggle =
        setting(Setting.bool("toggle", "Through walls", "", true, () -> {}));
    final Setting<Mode> mode =
        setting(
            Setting.enumeration(
                "mode", "Target mode", "", Mode.A_VERY_LONG_OPTION_NAME, Mode.class, () -> {}));
    final Setting<String> text =
        setting(Setting.string("text", "Friends", "", "Alice Bob Charlie", 100, () -> {}));
    final Setting<Double> number =
        setting(Setting.decimal("number", "Horizontal speed", "", 0.5, 0, 10, 0.1, () -> {}));
    final Setting<ColorValue> color =
        setting(Setting.color("color", "Enabled modules", "", ColorValue.rgb(255, 0, 0), () -> {}));

    Fixture() {
      super(new ModuleInfo("fixture", "Fixture", "", ModuleCategory.MISC, false));
    }
  }

  private ModuleManager manager(Fixture fixture) {
    ClientTaskQueue tasks = new ClientTaskQueue(Thread.currentThread(), ClientLog.NONE);
    ModuleManager manager =
        new ModuleManager(
            tasks,
            new EventBus(tasks::checkThread, ClientLog.NONE),
            new EventBus(() -> {}, ClientLog.NONE),
            ClientLog.NONE);
    manager.register(fixture);
    return manager;
  }

  private SettingControl control(ModuleManager manager, Setting<?> setting) {
    SettingControl control =
        SettingControl.create(
            manager, "fixture", setting, (label, initial, x, y, width, submit) -> {});
    control.measure(FACE, 69);
    control.place(5, 20, 69);
    return control;
  }

  @Test
  public void labelsValuesAndTextFitTheExistingPanelWidth() {
    Fixture fixture = new Fixture();
    try (ModuleManager manager = manager(fixture)) {
      SettingControl toggle = control(manager, fixture.toggle);
      assertEquals("Through walls", String.join("", toggle.labelLines));
      for (String line : toggle.labelLines) assertTrue(FACE.width(line) <= 69 - 6);
      assertTrue(toggle.height() > 16);
      SettingControl number = control(manager, fixture.number);
      assertTrue(number.stackedValue);
      float height = number.height();
      manager.setSetting("fixture", fixture.number, 10.0);
      number.measure(FACE, 69);
      assertEquals(height, number.height(), 0);
      SettingControl text = control(manager, fixture.text);
      assertTrue(text.height() > 16);
      assertEquals(69, text.w, 0);
    }
  }

  @Test
  public void shortRowsStayCompactAndWrappedRowsReserveOnlyMeasuredTextSpace() {
    Fixture fixture = new Fixture();
    try (ModuleManager manager = manager(fixture)) {
      SettingControl toggle = control(manager, fixture.toggle);
      toggle.measure(FACE, 150);
      assertEquals(14, toggle.height(), 0);
      toggle.measure(FACE, 69);
      assertEquals(24, toggle.height(), 0);
      SettingControl number = control(manager, fixture.number);
      assertTrue(number.stackedValue);
      assertTrue(number.height() < 40);
    }
  }

  @Test
  public void wrappedOptionsUseTheirDrawnHeightForSelection() {
    Fixture fixture = new Fixture();
    try (ModuleManager manager = manager(fixture)) {
      SettingControl mode = control(manager, fixture.mode);
      float header = mode.height();
      mode.press(10, 21, 0);
      float first =
          Math.max(
              SettingControl.ROW,
              (UiCanvas.wrap(FACE, GuiValues.readable(Mode.A_VERY_LONG_OPTION_NAME.name()), 69 - 12)
                              .size()
                          - 1)
                      * 10
                  + FACE.height()
                  + 5);
      mode.press(10, 20 + header + first + 3, 0);
      assertEquals(Mode.SECOND, fixture.mode.get());
      mode.measure(FACE, 69);
      assertEquals(mode.headerHeight, mode.height(), 0);
    }
  }

  @Test
  public void wrappedPickerTitleKeepsHexAndSaturationHitTargetsAligned() {
    Fixture fixture = new Fixture();
    try (ModuleManager manager = manager(fixture)) {
      final float[] editY = {-1};
      SettingControl picker =
          SettingControl.create(
              manager,
              "fixture",
              fixture.color,
              (label, initial, x, y, width, submit) -> editY[0] = y);
      picker.measure(FACE, 69);
      picker.place(5, 20, 69);
      float header = picker.height();
      assertTrue(header > 16);
      picker.press(10, 21, 0);
      picker.press(10, 20 + header + 160 + 2, 1);
      assertEquals(20 + header + 160, editY[0], 0);
      picker.press(71, 20 + header + 24, 0);
      assertEquals(128, fixture.color.get().red(), 1);
      assertEquals(0, fixture.color.get().green());
    }
  }
}
