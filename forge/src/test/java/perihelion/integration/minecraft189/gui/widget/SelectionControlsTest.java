package perihelion.integration.minecraft189.gui.widget;

import static org.junit.Assert.*;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.Test;
import perihelion.core.concurrent.ClientTaskQueue;
import perihelion.core.diagnostics.ClientLog;
import perihelion.core.event.EventBus;
import perihelion.core.module.*;
import perihelion.core.setting.*;
import perihelion.integration.minecraft189.gui.font.GuiFonts;

public class SelectionControlsTest {
  private enum Option {
    FIRST,
    SECOND,
    THIRD
  }

  private static GuiFonts.Face face(float advance, float height) {
    return new GuiFonts.Face() {
      public float width(String value) {
        return value.length() * advance;
      }

      public float height() {
        return height;
      }

      public void draw(String value, float x, float y, int color) {
        fail("Input and measurement must not render");
      }
    };
  }

  private static final GuiFonts.Face FACE = face(6, 9);

  private static final class Fixture extends ClientModule {
    final Setting<Option> mode =
        setting(Setting.enumeration("mode", "Mode", "", Option.FIRST, Option.class, () -> {}));
    final Setting<Set<Option>> parts =
        setting(
            Setting.multiSelect(
                "parts", "Parts", "", EnumSet.of(Option.FIRST), Option.class, () -> {}));
    final Setting<ColorValue> color =
        setting(Setting.color("color-1", "Color 1", "", ColorValue.rgb(255, 0, 0), () -> {}));

    Fixture(String id) {
      super(new ModuleInfo(id, "Fixture", "", ModuleCategory.MISC, false));
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

  private SettingControl control(
      ModuleManager manager, Fixture fixture, Setting<?> setting, boolean modern) {
    return control(manager, fixture, setting, modern, (label, initial, x, y, width, submit) -> {});
  }

  private SettingControl control(
      ModuleManager manager,
      Fixture fixture,
      Setting<?> setting,
      boolean modern,
      SettingControl.Actions actions) {
    SettingControl result = SettingControl.create(manager, fixture.info().id(), setting, actions);
    result.measure(FACE, 102, modern);
    result.place(5, 20, 102);
    return result;
  }

  @Test
  public void newSelectionRemainsOpenAcrossChangesAndFontRemeasureUntilRightClick() {
    Fixture fixture = new Fixture("themes");
    try (ModuleManager manager = manager(fixture)) {
      SettingControl menu = control(manager, fixture, fixture.mode, true);
      menu.press(10, 21, 0);
      assertTrue(menu.expanded);
      menu.press(10, 21, 0);
      assertTrue("Left-clicking an open header does not collapse it", menu.expanded);
      menu.press(10, menu.y + menu.headerHeight + 14 + 1, 0);
      assertEquals(Option.SECOND, fixture.mode.get());
      assertTrue("Selecting an item keeps its list open", menu.expanded);
      menu.measure(face(8, 12), 102, true);
      assertTrue("Changing the selected font does not reset open choices", menu.expanded);
      assertTrue(menu.height() > menu.headerHeight);
      menu.press(10, menu.y + menu.headerHeight + 1, 1);
      assertFalse("Right-clicking an option region closes it", menu.expanded);
      assertEquals(
          "Closing does not select the row under the cursor", Option.SECOND, fixture.mode.get());
      menu.press(10, 21, 1);
      assertTrue(menu.expanded);
      menu.press(10, 21, 1);
      assertFalse(menu.expanded);
    }
  }

  @Test
  public void newMultiSelectionStaysOpenAndLegacyEnumKeepsItsExistingBehavior() {
    Fixture fixture = new Fixture("themes");
    try (ModuleManager manager = manager(fixture)) {
      SettingControl menu = control(manager, fixture, fixture.parts, true);
      menu.press(10, 21, 1);
      menu.press(10, menu.y + menu.headerHeight + 14 + 1, 0);
      assertEquals(EnumSet.of(Option.FIRST, Option.SECOND), fixture.parts.get());
      assertTrue(menu.expanded);
      menu.press(10, menu.y + menu.headerHeight + 1, 1);
      assertFalse(menu.expanded);
      assertEquals(EnumSet.of(Option.FIRST, Option.SECOND), fixture.parts.get());
      SettingControl legacy = control(manager, fixture, fixture.mode, false);
      legacy.press(10, 21, 0);
      legacy.press(10, legacy.y + legacy.headerHeight + 14 + 1, 1);
      assertEquals(Option.SECOND, fixture.mode.get());
      assertFalse("Old selection retains automatic close after selection", legacy.expanded);
    }
  }

  @Test
  public void compactPickerHasBoundedCaptureAndReleaseAndProducesOpaqueStaticColors() {
    Fixture fixture = new Fixture("themes");
    try (ModuleManager manager = manager(fixture)) {
      manager.setSetting(
          "themes", fixture.color, ColorValue.rgba(255, 0, 0, 77).withRainbow(true, 2));
      SettingControl picker = control(manager, fixture, fixture.color, true);
      picker.press(10, 21, 1);
      assertTrue(picker.height() < 130);
      float top = picker.y + picker.headerHeight, left = picker.x + 3, width = picker.w - 6;
      assertTrue(picker.press(left + width / 2, top + 29, 0));
      assertEquals(128, fixture.color.get().red(), 1);
      assertEquals(64, fixture.color.get().green(), 1);
      assertEquals(64, fixture.color.get().blue(), 1);
      assertEquals(255, fixture.color.get().alpha());
      assertFalse(fixture.color.get().rainbow());
      picker.drag(left + width * 2, top - 20);
      assertEquals(ColorValue.rgb(255, 0, 0), fixture.color.get());
      picker.release();
      ColorValue saved = fixture.color.get();
      picker.drag(left, top + 100);
      assertEquals("Mouse release ends color capture", saved, fixture.color.get());
      assertFalse(
          "The gap between SV and hue never starts a drag", picker.press(left + 10, top + 57, 0));
      picker.drag(left, top + 100);
      assertEquals(saved, fixture.color.get());
      assertFalse(
          "Clicking outside the picker never starts a drag", picker.press(left - 20, top + 10, 0));
      picker.drag(left, top + 100);
      assertEquals(saved, fixture.color.get());
    }
  }

  @Test
  public void compactPickerRetainsChosenHueWhileTheColorIsBlack() {
    Fixture fixture = new Fixture("themes");
    try (ModuleManager manager = manager(fixture)) {
      manager.setSetting("themes", fixture.color, ColorValue.rgb(0, 0, 255));
      SettingControl picker = control(manager, fixture, fixture.color, true);
      picker.press(10, 21, 0);
      float top = picker.y + picker.headerHeight, left = picker.x + 3, width = picker.w - 6;
      assertTrue(picker.press(left + width / 2, top + 29, 0));
      picker.drag(left + width, top + 100);
      picker.release();
      assertEquals(ColorValue.rgb(0, 0, 0), fixture.color.get());
      assertTrue(picker.press(left + width / 3, top + 63, 0));
      picker.release();
      assertEquals(ColorValue.rgb(0, 0, 0), fixture.color.get());
      assertTrue(picker.press(left + width - .01f, top + 3, 0));
      picker.release();
      assertEquals(
          "Green hue survives while black hides its RGB hue", 255, fixture.color.get().green());
      assertTrue(fixture.color.get().red() <= 1 && fixture.color.get().blue() <= 1);
    }
  }

  @Test
  public void compactHexFieldTracksWrappedHeadersAndRejectsInvalidInputWithoutMutation() {
    Fixture fixture = new Fixture("themes");
    try (ModuleManager manager = manager(fixture)) {
      final float[] editY = {-1};
      final String[] initial = {null};
      final Consumer<String>[] submit = new Consumer[1];
      SettingControl picker =
          control(
              manager,
              fixture,
              fixture.color,
              true,
              (label, value, x, y, width, accept) -> {
                editY[0] = y;
                initial[0] = value;
                submit[0] = accept;
              });
      picker.measure(FACE, 69, true);
      picker.place(5, 20, 69);
      picker.press(10, 21, 1);
      assertTrue("Narrow rows wrap the picker label", picker.headerHeight > 14);
      picker.press(10, picker.y + picker.headerHeight + 75, 0);
      assertEquals(
          "Hex display and shared inline editor use the same text baseline",
          FACE.centeredY(picker.y + picker.headerHeight + 72, 16),
          FACE.centeredY(editY[0], 12) - 1,
          0);
      assertEquals("#FF0000", initial[0]);
      assertNotNull(submit[0]);
      submit[0].accept("1a2b3c");
      assertEquals(ColorValue.rgb(26, 43, 60), fixture.color.get());
      ColorValue saved = fixture.color.get();
      assertThrows(IllegalArgumentException.class, () -> submit[0].accept("#11223380"));
      assertThrows(IllegalArgumentException.class, () -> submit[0].accept("invalid"));
      assertEquals(saved, fixture.color.get());
      assertTrue("Editing the hex field keeps the picker open", picker.expanded);
      picker.press(10, 21, 1);
      assertFalse(picker.expanded);
    }
  }

  @Test
  public void compactPresentationIsLimitedToNewThemeColors() {
    Fixture fixture = new Fixture("fixture");
    try (ModuleManager manager = manager(fixture)) {
      SettingControl other = control(manager, fixture, fixture.color, true);
      other.press(10, 21, 0);
      assertEquals(176, other.height() - other.headerHeight, 0);
      SettingControl old = control(manager, fixture, fixture.color, false);
      old.press(10, 21, 0);
      assertEquals(176, old.height() - old.headerHeight, 0);
    }
  }
}
