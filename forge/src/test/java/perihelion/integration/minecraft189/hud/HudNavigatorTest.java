package perihelion.integration.minecraft189.hud;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.lwjgl.input.Keyboard;
import perihelion.core.diagnostics.ClientLog;
import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.*;
import perihelion.core.setting.*;

public class HudNavigatorTest {
  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private ClientRuntime runtime() {
    ClientRuntime r =
        new ClientRuntime(
            folder.getRoot().toPath().resolve("client.json"),
            ClientLog.NONE,
            client -> {
              for (ModuleCategory c : ModuleCategory.values())
                client
                    .modules()
                    .register(
                        new ClientModule(
                            new ModuleInfo(
                                c.name().toLowerCase(), c.displayName(), "fixture", c, false)) {});
              for (String suffix : new String[] {"two", "three"})
                client
                    .modules()
                    .register(
                        new ClientModule(
                            new ModuleInfo(
                                "player-" + suffix,
                                "Player " + suffix,
                                "fixture",
                                ModuleCategory.PLAYER,
                                false)) {});
            });
    r.start();
    return r;
  }

  @Test
  public void nativeGroupsKeepWorldAndMiscDistinctAndRetainAllNonClientDefinitions() {
    ClientRuntime r = runtime();
    try {
      HudNavigator nav = new HudNavigator(r.modules());
      assertArrayEquals(
          new String[] {"Combat", "Movement", "Player", "World", "Render", "Misc"},
          HudNavigator.CATEGORIES);
      assertEquals(6, nav.groups.size());
      assertEquals(3, nav.groups.get(2).size());
      assertEquals(ModuleCategory.WORLD, nav.groups.get(3).get(0).info().category());
      assertEquals(ModuleCategory.MISC, nav.groups.get(5).get(0).info().category());
      assertEquals(8, nav.groups.stream().mapToInt(java.util.List::size).sum());
    } finally {
      r.stop();
    }
  }

  @Test
  public void arrowsWrapAndEnterTogglesThroughNativeLifecycle() {
    ClientRuntime r = runtime();
    try {
      HudNavigator nav = new HudNavigator(r.modules());
      nav.key(Keyboard.KEY_RETURN);
      assertFalse(r.modules().require("combat").requestedEnabled());
      nav.key(Keyboard.KEY_UP);
      assertEquals(5, nav.category);
      nav.key(Keyboard.KEY_DOWN);
      assertEquals(0, nav.category);
      nav.key(Keyboard.KEY_RIGHT);
      assertTrue(nav.expanded);
      nav.key(Keyboard.KEY_RETURN);
      assertEquals(ModuleState.ENABLED, r.modules().require("combat").state());
      nav.key(Keyboard.KEY_RETURN);
      assertEquals(ModuleState.DISABLED, r.modules().require("combat").state());
      nav.key(Keyboard.KEY_LEFT);
      nav.key(Keyboard.KEY_DOWN);
      nav.key(Keyboard.KEY_DOWN);
      nav.key(Keyboard.KEY_RIGHT);
      nav.key(Keyboard.KEY_UP);
      assertEquals(2, nav.selected);
      nav.key(Keyboard.KEY_DOWN);
      assertEquals(0, nav.selected);
      nav.key(Keyboard.KEY_LEFT);
      assertFalse(nav.expanded);
    } finally {
      r.stop();
    }
  }

  private enum Choice {
    FIRST,
    SECOND,
    THIRD
  }

  private static final class Fixture extends ClientModule {
    final Setting<Boolean> toggle = setting(Setting.bool("toggle", "Toggle", "", false, () -> {}));
    final Setting<Integer> integer =
        setting(Setting.integer("integer", "Integer", "", 3, 1, 5, 2, () -> {}));
    final Setting<Double> decimal =
        setting(Setting.decimal("decimal", "Decimal", "", .2, 0, 1, .1, () -> {}));
    final Setting<Choice> choice =
        setting(Setting.enumeration("choice", "Choice", "", Choice.FIRST, Choice.class, () -> {}));
    final Setting<Set<Choice>> multiple =
        setting(
            Setting.multiSelect(
                "multiple", "Multiple", "", EnumSet.noneOf(Choice.class), Choice.class, () -> {}));
    final Setting<DoubleRange> range =
        setting(Setting.range("range", "Range", "", new DoubleRange(2, 4), 0, 6, 1, () -> {}));
    final Setting<ColorValue> color =
        setting(Setting.color("color", "Color", "", ColorValue.rgba(10, 20, 30, 40), () -> {}));
    final Setting<String> text = setting(Setting.string("text", "Text", "", "", 4, () -> {}));
    final Setting<Integer> dependent =
        setting(
            Setting.integer("dependent", "Dependent", "", 0, 0, 2, () -> {})
                .visibleWhen(toggle::get));

    Fixture() {
      super(new ModuleInfo("fixture", "Fixture", "", ModuleCategory.COMBAT, false));
    }
  }

  private ClientRuntime runtime(Fixture fixture) {
    ClientRuntime runtime =
        new ClientRuntime(
            folder.getRoot().toPath().resolve("settings.json"),
            ClientLog.NONE,
            client -> client.modules().register(fixture));
    runtime.start();
    return runtime;
  }

  private HudNavigator settings(ClientRuntime runtime) {
    HudNavigator nav = new HudNavigator(runtime.modules());
    nav.key(Keyboard.KEY_RIGHT);
    nav.key(Keyboard.KEY_RIGHT);
    assertTrue(nav.settingsOpen);
    return nav;
  }

  private void choose(HudNavigator nav, Setting<?> setting) {
    nav.settingSelected = nav.settings().indexOf(setting);
    assertTrue(nav.settingSelected >= 0);
  }

  @Test
  public void settingsUseNativeVisibilityAndBooleansDoNotToggleTheirModule() {
    Fixture fixture = new Fixture();
    ClientRuntime runtime = runtime(fixture);
    try {
      HudNavigator nav = settings(runtime);
      assertFalse(nav.settings().contains(fixture.dependent));
      int[] changes = {0};
      fixture.toggle.onChange((setting, before, after) -> changes[0]++);
      nav.key(Keyboard.KEY_RETURN);
      assertTrue(fixture.toggle.get());
      assertEquals(1, changes[0]);
      assertFalse(runtime.modules().require("fixture").requestedEnabled());
      assertTrue(nav.settings().contains(fixture.dependent));
      nav.key(Keyboard.KEY_LEFT);
      assertFalse(nav.settingsOpen);
      assertTrue(nav.expanded);
      nav.key(Keyboard.KEY_RETURN);
      assertEquals(ModuleState.ENABLED, runtime.modules().require("fixture").state());
    } finally {
      runtime.stop();
    }
  }

  @Test
  public void numericEditorsRespectStepsBoundsAndCancelRestoresOriginal() {
    Fixture fixture = new Fixture();
    ClientRuntime runtime = runtime(fixture);
    try {
      HudNavigator nav = settings(runtime);
      choose(nav, fixture.integer);
      nav.key(Keyboard.KEY_RIGHT);
      nav.key(Keyboard.KEY_RIGHT);
      nav.key(Keyboard.KEY_RIGHT);
      assertEquals(Integer.valueOf(5), fixture.integer.get());
      nav.key(Keyboard.KEY_ESCAPE);
      assertEquals(Integer.valueOf(3), fixture.integer.get());
      assertTrue(nav.settingsOpen);
      choose(nav, fixture.decimal);
      nav.key(Keyboard.KEY_RETURN);
      nav.key(Keyboard.KEY_LEFT);
      nav.key(Keyboard.KEY_RETURN);
      assertEquals(.1, fixture.decimal.get(), .00001);
      assertFalse(nav.editing);
    } finally {
      runtime.stop();
    }
  }

  @Test
  public void enumAndMultiSelectNavigateInDeclarationOrderIncludingEmptySets() {
    Fixture fixture = new Fixture();
    ClientRuntime runtime = runtime(fixture);
    try {
      HudNavigator nav = settings(runtime);
      choose(nav, fixture.choice);
      nav.key(Keyboard.KEY_RETURN);
      nav.key(Keyboard.KEY_LEFT);
      assertEquals(Choice.THIRD, fixture.choice.get());
      nav.key(Keyboard.KEY_RETURN);
      choose(nav, fixture.multiple);
      nav.key(Keyboard.KEY_RETURN);
      nav.key(Keyboard.KEY_SPACE);
      nav.key(Keyboard.KEY_DOWN);
      nav.key(Keyboard.KEY_RIGHT);
      assertEquals(EnumSet.of(Choice.FIRST, Choice.SECOND), fixture.multiple.get());
      assertEquals("+ Second", nav.value(fixture.multiple));
      nav.key(Keyboard.KEY_LEFT);
      nav.key(Keyboard.KEY_UP);
      nav.key(Keyboard.KEY_SPACE);
      assertTrue(fixture.multiple.get().isEmpty());
      nav.key(Keyboard.KEY_RETURN);
    } finally {
      runtime.stop();
    }
  }

  @Test
  public void rangesNeverCrossAndColorChannelsRetainTheirAnimation() {
    Fixture fixture = new Fixture();
    ClientRuntime runtime = runtime(fixture);
    try {
      HudNavigator nav = settings(runtime);
      choose(nav, fixture.range);
      nav.key(Keyboard.KEY_RIGHT);
      for (int i = 0; i < 5; i++) nav.key(Keyboard.KEY_RIGHT);
      assertEquals(new DoubleRange(4, 4), fixture.range.get());
      nav.key(Keyboard.KEY_DOWN);
      nav.key(Keyboard.KEY_RIGHT);
      assertEquals(new DoubleRange(4, 5), fixture.range.get());
      nav.key(Keyboard.KEY_RETURN);
      choose(nav, fixture.color);
      nav.key(Keyboard.KEY_RETURN);
      nav.key(Keyboard.KEY_RIGHT);
      for (int i = 0; i < 4; i++) nav.key(Keyboard.KEY_DOWN);
      nav.key(Keyboard.KEY_RIGHT);
      nav.key(Keyboard.KEY_DOWN);
      nav.key(Keyboard.KEY_RIGHT);
      assertEquals(ColorValue.rgba(11, 20, 30, 40).withRainbow(true, 1.1), fixture.color.get());
      nav.key(Keyboard.KEY_ESCAPE);
      assertEquals(ColorValue.rgba(10, 20, 30, 40), fixture.color.get());
    } finally {
      runtime.stop();
    }
  }

  @Test
  public void textDraftsAreBoundedAndCommitOnlyOnEnter() {
    Fixture fixture = new Fixture();
    ClientRuntime runtime = runtime(fixture);
    try {
      HudNavigator nav = settings(runtime);
      choose(nav, fixture.text);
      nav.key(Keyboard.KEY_RETURN);
      assertTrue(nav.textEditing());
      assertEquals("", nav.draft);
      for (char c : "hello".toCharArray()) nav.key(Keyboard.KEY_A, c);
      assertEquals("hell", nav.draft);
      assertEquals("", fixture.text.get());
      nav.key(Keyboard.KEY_BACK);
      nav.key(Keyboard.KEY_A, 'p');
      nav.key(Keyboard.KEY_RETURN);
      assertEquals("help", fixture.text.get());
      nav.key(Keyboard.KEY_RETURN);
      nav.key(Keyboard.KEY_DELETE);
      nav.key(Keyboard.KEY_ESCAPE);
      assertEquals("help", fixture.text.get());
    } finally {
      runtime.stop();
    }
  }

  @Test
  public void observerFaultIsReportedWithoutEscapingTheKeyboardHandler() {
    Fixture fixture = new Fixture();
    ClientRuntime runtime = runtime(fixture);
    try {
      HudNavigator nav = settings(runtime);
      fixture.toggle.onChange(
          (setting, before, after) -> {
            throw new IllegalStateException("fixture");
          });
      nav.key(Keyboard.KEY_RETURN);
      assertTrue(fixture.toggle.get());
      assertEquals(ModuleState.FAULTED, runtime.modules().require("fixture").state());
      assertEquals("Module faulted; see log", nav.error);
    } finally {
      runtime.stop();
    }
  }

  @Test
  public void hiddenEditorsRetireAndEmptyCategoriesRemainSafe() {
    Fixture fixture = new Fixture();
    ClientRuntime runtime = runtime(fixture);
    try {
      HudNavigator nav = settings(runtime);
      nav.key(Keyboard.KEY_RETURN);
      choose(nav, fixture.dependent);
      nav.key(Keyboard.KEY_RETURN);
      runtime.modules().setSetting("fixture", fixture.toggle, false);
      assertFalse(nav.settings().contains(fixture.dependent));
      assertFalse(nav.editing);
      nav.key(Keyboard.KEY_LEFT);
      nav.key(Keyboard.KEY_LEFT);
      nav.key(Keyboard.KEY_DOWN);
      nav.key(Keyboard.KEY_RIGHT);
      nav.key(Keyboard.KEY_RIGHT);
      nav.key(Keyboard.KEY_RETURN);
      assertFalse(nav.settingsOpen);
    } finally {
      runtime.stop();
    }
  }

  @Test
  public void sidePaneLayoutUsesThePermanentCategoryAnchorAtEverySavedPosition() {
    for (int width : new int[] {320, 427, 1920}) {
      for (double fraction : new double[] {0, .25, .5, .75, 1}) {
        int x = HudLayout.position(fraction, width, HudNavigator.CATEGORY_WIDTH);
        int y = HudLayout.position(fraction, 240, HudNavigator.CATEGORIES.length * 11);
        HudNavigator.Layout layout = new HudNavigator.Layout(x, y, width, 240);
        assertEquals(x, layout.x);
        assertEquals(y, layout.y);
        assertTrue(layout.paneX >= 2);
        assertTrue(layout.paneX + layout.paneWidth <= width - 2);
        assertTrue(
            layout.paneX + layout.paneWidth <= x - 2
                || layout.paneX >= x + HudNavigator.CATEGORY_WIDTH + 2);
        assertTrue(layout.y + layout.availableHeight <= 238);
      }
    }
  }

  @Test
  public void compactRowsShareScrollAndPaneBoundsForMixedSettingKinds() {
    Fixture fixture = new Fixture();
    ClientRuntime runtime = runtime(fixture);
    try {
      HudNavigator nav = settings(runtime);
      runtime.modules().setSetting("fixture", fixture.text, "long");
      HudNavigator.SettingRows roomy = nav.settingRows(176, 240, value -> value.length() * 4);
      assertEquals(14, roomy.heights[roomy.settings.indexOf(fixture.toggle)]);
      assertEquals(14, roomy.heights[roomy.settings.indexOf(fixture.integer)]);
      HudNavigator.SettingRows narrow = nav.settingRows(68, 240, value -> value.length() * 4);
      assertEquals(24, narrow.heights[narrow.settings.indexOf(fixture.multiple)]);
      choose(nav, fixture.text);
      HudNavigator.SettingRows scrolled = nav.settingRows(68, 80, value -> value.length() * 4);
      assertTrue(scrolled.first <= nav.settingSelected);
      assertTrue(scrolled.first + scrolled.count > nav.settingSelected);
      int content = 0;
      for (int i = scrolled.first; i < scrolled.first + scrolled.count; i++)
        content += scrolled.heights[i];
      assertEquals(content, scrolled.contentHeight);
      assertEquals(33 + content, scrolled.height);
      assertTrue(scrolled.height <= 80);
    } finally {
      runtime.stop();
    }
  }

  @Test
  public void unicodeBackspaceAndTrailingTextNeverSplitCodepoints() {
    Fixture fixture = new Fixture();
    ClientRuntime runtime = runtime(fixture);
    try {
      HudNavigator nav = settings(runtime);
      choose(nav, fixture.text);
      nav.key(Keyboard.KEY_RETURN);
      nav.draft = "A\ud83d\ude00";
      nav.key(Keyboard.KEY_BACK);
      assertEquals("A", nav.draft);
      String tail =
          HudNavigator.tail(
              "long prefix \ud83d\ude00Z_", 8, value -> value.codePointCount(0, value.length()));
      assertTrue(tail.endsWith("\ud83d\ude00Z_"));
      assertTrue(tail.codePointCount(0, tail.length()) <= 8);
      assertFalse(Character.isLowSurrogate(tail.charAt(0)));
    } finally {
      runtime.stop();
    }
  }

  @Test
  public void brokenVisibilityQuarantinesItsModuleOnceAndLeavesNavigationUsable() {
    Fixture fixture = new Fixture();
    int[] calls = {0};
    fixture.dependent.visibleWhen(
        () -> {
          calls[0]++;
          throw new IllegalStateException("broken visibility");
        });
    ClientRuntime runtime = runtime(fixture);
    try {
      HudNavigator nav = settings(runtime);
      assertFalse(nav.settings().contains(fixture.dependent));
      assertFalse(nav.settings().contains(fixture.dependent));
      assertEquals(1, calls[0]);
      assertEquals(ModuleState.FAULTED, runtime.modules().require("fixture").state());
      assertEquals("Module faulted; see log", nav.error);
      nav.key(Keyboard.KEY_LEFT);
      assertFalse(nav.settingsOpen);
    } finally {
      runtime.stop();
    }
  }
}
