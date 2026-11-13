package perihelion.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import perihelion.core.concurrent.ClientTaskQueue;
import perihelion.core.diagnostics.ClientLog;
import perihelion.core.event.EventBus;
import perihelion.core.module.ClientModule;
import perihelion.core.module.ModuleCategory;
import perihelion.core.module.ModuleInfo;
import perihelion.core.module.ModuleManager;
import static org.junit.Assert.*;

public class SettingFeaturesTest {
    private static final Runnable UNGUARDED = () -> { };
    private enum Option { FIRST, SECOND, THIRD }
    private enum Other { FIRST }
    private enum Ambiguous { SAME, same }
    private enum UnicodeOption { İ }
    private static JsonElement json(String text) { return new JsonParser().parse(text); }

    @Test public void metadataExposesTypeConstraintsChoicesAndExistingDescriptions() {
        Setting<Integer> count = Setting.integer("count", "Count", "Number of attempts", 1, 1, 9, 2, UNGUARDED);
        assertEquals("Number of attempts", count.description());
        assertEquals(SettingMetadata.Kind.INTEGER, count.metadata().kind());
        NumericConstraints numeric = count.metadata().numeric().get();
        assertEquals(1, numeric.minimum(), 0); assertEquals(9, numeric.maximum(), 0);
        assertEquals(2, numeric.step().getAsDouble(), 0);
        Setting<Double> continuous = Setting.decimal("continuous", "Continuous value", "", 0.12345, 0, 1, UNGUARDED);
        assertFalse(continuous.metadata().numeric().get().step().isPresent());
        assertEquals(SettingMetadata.Kind.DECIMAL, continuous.metadata().kind());
        Setting<String> label = Setting.string("label", "Label", "", "", 20, UNGUARDED);
        assertEquals(20, label.metadata().maximumLength().getAsInt());
        assertEquals(SettingMetadata.Kind.STRING, label.metadata().kind());
        assertFalse(label.metadata().numeric().isPresent());
        Setting<Option> mode = Setting.enumeration("mode", "Mode", "", Option.FIRST, Option.class, UNGUARDED);
        assertEquals(SettingMetadata.Kind.ENUM, mode.metadata().kind());
        assertEquals(Arrays.asList("first", "second", "third"), mode.metadata().options());
        assertThrows(UnsupportedOperationException.class, () -> mode.metadata().options().clear());
        assertFalse(mode.metadata().maximumLength().isPresent());
        assertEquals(SettingMetadata.Kind.BOOLEAN, Setting.bool("toggle", "Toggle", "", false, UNGUARDED).metadata().kind());
    }

    @Test public void integerStepsAreAnchoredAtMinimumAndCheckedEverywhereWithoutOverflow() {
        Setting<Integer> count = Setting.integer("count", "Count", "", -2, -5, 7, 3, UNGUARDED);
        count.set(7); assertEquals(Integer.valueOf(7), count.get());
        assertEquals(Integer.valueOf(1), count.parse("1"));
        assertEquals(Integer.valueOf(4), count.fromJson(json("4.0")));
        for (int invalid : new int[] { -6, -4, 0, 8 }) {
            assertThrows(IllegalArgumentException.class, () -> count.set(invalid));
            assertThrows(IllegalArgumentException.class, () -> count.validate(invalid));
            assertThrows(IllegalArgumentException.class, () -> count.parse(String.valueOf(invalid)));
            assertThrows(IllegalArgumentException.class, () -> count.fromJson(new JsonPrimitive(invalid)));
        }
        assertEquals(Integer.valueOf(7), count.get());
        count.reset(); assertEquals(Integer.valueOf(-2), count.get());
        Setting<Integer> extremes = Setting.integer("extremes", "Extremes", "", Integer.MIN_VALUE,
                Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, UNGUARDED);
        extremes.set(Integer.MAX_VALUE - 1);
        assertThrows(IllegalArgumentException.class, () -> extremes.set(Integer.MAX_VALUE));
        Setting<Integer> all = Setting.integer("all", "All", "", 0, Integer.MIN_VALUE, Integer.MAX_VALUE, UNGUARDED);
        all.set(Integer.MAX_VALUE); all.set(Integer.MIN_VALUE);
    }

    @Test public void stepDefinitionsRejectInvalidStepsAndOffGridDefaults() {
        for (double step : new double[] { 0, -1, Double.NaN, Double.POSITIVE_INFINITY }) {
            assertThrows(IllegalArgumentException.class, () -> Setting.decimal("d", "Decimal value", "", 0, 0, 1, step, UNGUARDED));
            assertThrows(IllegalArgumentException.class, () -> Setting.range("r", "Interval", "", new DoubleRange(0, 1), 0, 1, step, UNGUARDED));
        }
        assertThrows(IllegalArgumentException.class, () -> Setting.integer("i", "Integer value", "", 0, 0, 1, 0, UNGUARDED));
        assertThrows(IllegalArgumentException.class, () -> Setting.integer("i", "Integer value", "", 0, 0, 1, -1, UNGUARDED));
        assertThrows(IllegalArgumentException.class, () -> Setting.integer("i", "Integer value", "", 1, 0, 4, 2, UNGUARDED));
        assertThrows(IllegalArgumentException.class, () -> Setting.decimal("d", "Decimal value", "", 0.15, 0, 1, 0.1, UNGUARDED));
        assertThrows(IllegalArgumentException.class, () -> Setting.range("r", "Interval", "", new DoubleRange(0, 0.15), 0, 1, 0.1, UNGUARDED));
        assertThrows(IllegalArgumentException.class, () -> Setting.decimal("d", "Decimal value", "", 0, 2, 1, 0.1, UNGUARDED));
    }

    @Test public void decimalStepsNormalizeOnlyFloatingPointNoiseAndNotifyWithCanonicalValues() {
        Setting<Double> decimal = Setting.decimal("d", "Decimal value", "", 0.1 + 0.2, 0, 1, 0.1, UNGUARDED);
        assertEquals(Double.valueOf(0.3), decimal.defaultValue());
        AtomicInteger events = new AtomicInteger();
        decimal.onChange((setting, before, after) -> {
            assertEquals(Double.valueOf(0.6), after);
            assertSame(after, setting.get());
            events.incrementAndGet();
        });
        decimal.set(0.3); assertEquals(0, events.get());
        decimal.set(0.2 + 0.4); assertEquals(1, events.get());
        assertEquals(Double.valueOf(0.3), decimal.parse("0.30000000000000004"));
        assertEquals(Double.valueOf(0.3), decimal.fromJson(json("0.30000000000000004")));
        assertEquals(Double.valueOf(0), decimal.validate((0.1 + 0.2) - 0.3));
        assertEquals(Double.valueOf(1), decimal.validate(Math.nextUp(1.0)));
        for (double invalid : new double[] { 0.35, 0.3001, -0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY }) {
            assertThrows(IllegalArgumentException.class, () -> decimal.set(invalid));
            assertThrows(IllegalArgumentException.class, () -> decimal.parse(String.valueOf(invalid)));
            assertThrows(IllegalArgumentException.class, () -> decimal.fromJson(new JsonPrimitive(invalid)));
        }
        assertEquals(1, events.get());
        Setting<Double> offset = Setting.decimal("offset", "Offset", "", 0.15, 0.05, 1.05, 0.1, UNGUARDED);
        assertEquals(Double.valueOf(0.25), offset.parse("0.25"));
        assertThrows(IllegalArgumentException.class, () -> offset.set(0.2));
    }

    @Test public void rangesAreOrderedBoundedSteppedAndRoundTripWithoutMutableAliases() {
        Setting<DoubleRange> range = Setting.range("range", "Range", "Interval", new DoubleRange(0.2, 0.8), 0, 1, 0.1, UNGUARDED);
        assertEquals(SettingMetadata.Kind.RANGE, range.metadata().kind());
        assertEquals(new DoubleRange(0.3, 0.7), range.parse("0.30000000000000004..0.7"));
        DoubleRange previous = range.get();
        range.set(new DoubleRange(0.1, 0.9));
        assertEquals(new DoubleRange(0.2, 0.8), previous);
        assertEquals(range.get(), range.fromJson(range.toJson()));
        JsonElement encoded = range.toJson();
        encoded.getAsJsonObject().addProperty("lower", 0.7);
        assertEquals(new DoubleRange(0.1, 0.9), range.get());
        range.reset(); assertEquals(previous, range.get());
        assertEquals(new DoubleRange(-0.0, 0), new DoubleRange(0, 0));
        assertEquals(new DoubleRange(0, 0).hashCode(), new DoubleRange(-0.0, 0).hashCode());
        assertThrows(IllegalArgumentException.class, () -> new DoubleRange(1, 0));
        assertThrows(IllegalArgumentException.class, () -> new DoubleRange(Double.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> new DoubleRange(0, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> range.set(new DoubleRange(0.15, 0.9)));
        for (String invalid : Arrays.asList("1..0", "-1..0", "0..2", "0.25..0.9", "0", "0..1..2")) {
            assertThrows(IllegalArgumentException.class, () -> range.parse(invalid));
        }
        for (String invalid : Arrays.asList("[]", "0", "null", "{}", "{\"lower\":0}",
                "{\"lower\":0,\"upper\":1,\"extra\":true}", "{\"lower\":\"0\",\"upper\":1}",
                "{\"lower\":0,\"upper\":false}", "{\"lower\":0.25,\"upper\":1}", "{\"lower\":1,\"upper\":0}")) {
            assertThrows(IllegalArgumentException.class, () -> range.fromJson(json(invalid)));
        }
        Setting<DoubleRange> continuous = Setting.range("continuous", "Continuous value", "", new DoubleRange(0, 1), 0, 1, UNGUARDED);
        assertEquals(new DoubleRange(0.12345, 0.98765), continuous.parse("0.12345..0.98765"));
    }

    @Test public void colorsHaveUnambiguousAlphaAndStrictTextJsonAndChannels() {
        ColorValue color = ColorValue.rgba(0x12, 0x34, 0x56, 0x78);
        assertEquals(0x12, color.red()); assertEquals(0x34, color.green());
        assertEquals(0x56, color.blue()); assertEquals(0x78, color.alpha());
        assertEquals(0x78123456, color.argb()); assertEquals(color, ColorValue.fromArgb(color.argb()));
        assertEquals(ColorValue.rgb(18, 52, 86), ColorValue.parse("#123456"));
        assertEquals(ColorValue.rgba(255, 0, 128, 0), ColorValue.parse("#ff008000"));
        assertEquals(ColorValue.rgba(255, 255, 255, 255), ColorValue.fromArgb(-1));
        Setting<ColorValue> setting = Setting.color("color", "Color", "Accent", color, UNGUARDED);
        assertEquals(SettingMetadata.Kind.COLOR, setting.metadata().kind());
        assertEquals("#12345678", setting.toJson().getAsString());
        assertEquals(color, setting.fromJson(setting.toJson()));
        assertEquals(color.hashCode(), ColorValue.parse("#12345678").hashCode());
        for (String invalid : Arrays.asList("red", "123456", "#123", "#1234567", "#GG0000", "#123456789")) {
            assertThrows(IllegalArgumentException.class, () -> setting.parse(invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> setting.fromJson(json("123456")));
        assertThrows(IllegalArgumentException.class, () -> setting.fromJson(json("[]")));
        assertThrows(IllegalArgumentException.class, () -> ColorValue.rgb(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> ColorValue.rgba(0, 0, 0, 256));
        assertThrows(IllegalArgumentException.class, () -> setting.set(null));
    }

    @Test public void multiSelectCopiesDefaultsUpdatesParsingAndObserverValues() {
        Set<Option> defaults = EnumSet.of(Option.FIRST);
        Setting<Set<Option>> selected = Setting.multiSelect("selected", "Selected modes", "Modes", defaults, Option.class, UNGUARDED);
        defaults.clear();
        assertEquals(EnumSet.of(Option.FIRST), selected.defaultValue());
        assertEquals(SettingMetadata.Kind.MULTI_SELECT, selected.metadata().kind());
        assertEquals(Arrays.asList("first", "second", "third"), selected.metadata().options());
        List<Set<Option>> observed = new ArrayList<>();
        selected.onChange((setting, before, after) -> { observed.add(before); observed.add(after); });
        Set<Option> next = EnumSet.of(Option.THIRD, Option.SECOND);
        selected.set(next); next.clear();
        assertEquals(EnumSet.of(Option.SECOND, Option.THIRD), selected.get());
        assertEquals("[\"second\",\"third\"]", selected.toJson().toString());
        selected.set(EnumSet.of(Option.THIRD, Option.SECOND)); assertEquals(2, observed.size());
        assertEquals(EnumSet.of(Option.FIRST), observed.get(0));
        assertEquals(selected.get(), observed.get(1));
        for (Set<Option> immutable : Arrays.asList(selected.get(), selected.defaultValue(), observed.get(0), observed.get(1),
                selected.parse("third, FIRST"), selected.fromJson(json("[\"first\"]")), selected.validate(EnumSet.of(Option.FIRST)))) {
            assertThrows(UnsupportedOperationException.class, () -> immutable.add(Option.SECOND));
            assertThrows(UnsupportedOperationException.class, immutable::clear);
        }
        JsonElement encoded = selected.toJson(); encoded.getAsJsonArray().add(new JsonPrimitive("first"));
        assertEquals(EnumSet.of(Option.SECOND, Option.THIRD), selected.get());
        assertEquals(Collections.emptySet(), selected.parse(""));
        assertEquals(Collections.emptySet(), selected.fromJson(json("[]")));
        selected.reset(); assertEquals(EnumSet.of(Option.FIRST), selected.get());
        assertEquals(EnumSet.of(Option.SECOND, Option.THIRD), observed.get(1));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test public void multiSelectRejectsUnknownDuplicateAndIncorrectTypesWithoutChangingState() {
        Setting<Set<Option>> setting = Setting.multiSelect("selected", "Selected modes", "", EnumSet.of(Option.FIRST), Option.class, UNGUARDED);
        for (String invalid : Arrays.asList("unknown", "first,FIRST", "first,", ",", ",first")) {
            assertThrows(IllegalArgumentException.class, () -> setting.parse(invalid));
        }
        for (String invalid : Arrays.asList("\"first\"", "{}", "null", "[true]", "[1]", "[null]", "[[]]",
                "[\"unknown\"]", "[\"first\",\"FIRST\"]", "[\"first\",\"second\",\"third\",\"first\"]")) {
            assertThrows(IllegalArgumentException.class, () -> setting.fromJson(json(invalid)));
        }
        Set withNull = new HashSet(); withNull.add(null);
        assertThrows(IllegalArgumentException.class, () -> setting.set(withNull));
        assertThrows(IllegalArgumentException.class, () -> setting.set((Set) EnumSet.of(Other.FIRST)));
        assertThrows(IllegalArgumentException.class, () -> setting.set(null));
        assertEquals(EnumSet.of(Option.FIRST), setting.get());
        assertThrows(IllegalArgumentException.class, () -> Setting.multiSelect("ambiguous", "Ambiguous", "", EnumSet.noneOf(Ambiguous.class), Ambiguous.class, UNGUARDED));
        assertThrows(IllegalArgumentException.class, () -> Setting.enumeration("ambiguous", "Ambiguous", "", Ambiguous.SAME, Ambiguous.class, UNGUARDED));
    }

    @Test public void enumOptionIdsRoundTripWhenLowercasingExpandsUnicodeCharacters() {
        Setting<UnicodeOption> single = Setting.enumeration("single", "Single", "", UnicodeOption.İ, UnicodeOption.class, UNGUARDED);
        Setting<Set<UnicodeOption>> multiple = Setting.multiSelect("multiple", "Multiple", "", EnumSet.of(UnicodeOption.İ), UnicodeOption.class, UNGUARDED);
        assertEquals(single.get(), single.fromJson(single.toJson()));
        assertEquals(multiple.get(), multiple.fromJson(multiple.toJson()));
        assertEquals(single.get(), single.parse(single.metadata().options().get(0)));
        assertEquals(multiple.get(), multiple.parse(multiple.metadata().options().get(0)));
    }

    @Test public void visibilityTracksDependenciesWithoutMutatingOrDiscardingHiddenValues() {
        Setting<Boolean> toggle = Setting.bool("toggle", "Toggle", "", false, UNGUARDED);
        Setting<Integer> hidden = Setting.integer("hidden", "Hidden", "Still persisted", 2, 0, 10, UNGUARDED);
        assertTrue(hidden.isVisible());
        assertSame(hidden, hidden.visibleWhen(toggle::get));
        assertFalse(hidden.isVisible());
        AtomicInteger changes = new AtomicInteger();
        hidden.onChange((setting, before, after) -> changes.incrementAndGet());
        hidden.set(4); assertEquals(1, changes.get());
        assertEquals(4, hidden.toJson().getAsInt());
        toggle.set(true); assertTrue(hidden.isVisible());
        toggle.set(false); assertFalse(hidden.isVisible());
        assertEquals(Integer.valueOf(4), hidden.get()); assertEquals(1, changes.get());
        assertThrows(IllegalArgumentException.class, () -> hidden.set(20));
        assertThrows(NullPointerException.class, () -> hidden.visibleWhen(null));
    }

    @Test public void registeringAModuleFreezesVisibilityButAllowsValueUpdates() {
        ClientTaskQueue tasks = new ClientTaskQueue(Thread.currentThread(), ClientLog.NONE);
        EventBus events = new EventBus(tasks::checkThread, ClientLog.NONE);
        ModuleManager modules = new ModuleManager(tasks, events, events, ClientLog.NONE);
        Setting<Boolean> setting = Setting.bool("flag", "Flag", "", false, tasks::checkThread).visibleWhen(() -> true);
        modules.register(new ClientModule(new ModuleInfo("probe", "Probe", "test-only", ModuleCategory.MISC, false)) {
            { setting(setting); }
        });
        assertThrows(IllegalStateException.class, () -> setting.visibleWhen(() -> false));
        setting.set(true); assertTrue(setting.get()); assertTrue(setting.isVisible());
        setting.freeze(); // Idempotent.
        modules.close(); events.close(); tasks.close();
    }

    @Test public void compoundValuesAndMetadataCanBeReadOffThreadButVisibilityAndMutationAreGuarded() throws Exception {
        ClientTaskQueue tasks = new ClientTaskQueue(Thread.currentThread(), ClientLog.NONE);
        Setting<Set<Option>> selected = Setting.multiSelect("selected", "Selected modes", "", EnumSet.of(Option.FIRST), Option.class, tasks::checkThread);
        Setting<DoubleRange> range = Setting.range("range", "Range", "", new DoubleRange(0, 1), 0, 2, tasks::checkThread);
        selected.set(EnumSet.of(Option.SECOND)); range.set(new DoubleRange(1, 2));
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                assertEquals(EnumSet.of(Option.SECOND), selected.get());
                assertEquals(new DoubleRange(1, 2), range.get());
                assertEquals(selected.get(), selected.fromJson(selected.toJson()));
                assertEquals(range.get(), range.fromJson(range.toJson()));
                assertEquals(SettingMetadata.Kind.RANGE, range.metadata().kind());
                assertThrows(UnsupportedOperationException.class, () -> selected.get().clear());
                assertThrows(IllegalStateException.class, selected::isVisible);
                assertThrows(IllegalStateException.class, () -> selected.visibleWhen(() -> false));
                assertThrows(IllegalStateException.class, selected::freeze);
                assertThrows(IllegalStateException.class, () -> selected.set(EnumSet.of(Option.FIRST)));
                assertThrows(IllegalStateException.class, () -> range.set(new DoubleRange(0, 0)));
            } catch (Throwable thrown) { failure.set(thrown); }
        });
        worker.start(); worker.join(); tasks.close();
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
}
