package perihelion.core.setting;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.*;

public class SettingTest {
    private static final Runnable UNGUARDED = () -> { };
    private enum Mode { SLOW, FAST }

    @Test public void validatesMetadataDefaultsAndNulls() {
        assertThrows(IllegalArgumentException.class, () -> Setting.bool("Invalid ID", "Invalid id", "", false, UNGUARDED));
        assertThrows(IllegalArgumentException.class, () -> Setting.integer("count", "Count", "", 6, 0, 5, UNGUARDED));
        assertThrows(IllegalArgumentException.class, () -> Setting.decimal("rate", "Rate", "", Double.NaN, 0, 1, UNGUARDED));
        assertThrows(IllegalArgumentException.class, () -> Setting.decimal("rate", "Rate", "", 0, 0, Double.POSITIVE_INFINITY, UNGUARDED));
        assertThrows(IllegalArgumentException.class, () -> Setting.string("label", "Label", "", "long", 3, UNGUARDED));
        Setting<Boolean> enabled = Setting.bool("enabled", "Enabled", "Description", false, UNGUARDED);
        assertEquals("enabled", enabled.id());
        assertEquals("Description", enabled.description());
        assertThrows(IllegalArgumentException.class, () -> enabled.set(null));
    }

    @Test public void primitiveTypesRejectJsonCoercion() {
        Setting<Boolean> bool = Setting.bool("bool", "Boolean value", "", false, UNGUARDED);
        assertEquals(Boolean.TRUE, bool.parse(" TRUE "));
        assertThrows(IllegalArgumentException.class, () -> bool.parse("yes"));
        assertThrows(IllegalArgumentException.class, () -> bool.fromJson(new JsonPrimitive("true")));
        Setting<Integer> integer = Setting.integer("integer", "Integer", "", 0, -4, 4, UNGUARDED);
        assertEquals(Integer.valueOf(3), integer.fromJson(new JsonPrimitive(3.0)));
        assertThrows(IllegalArgumentException.class, () -> integer.fromJson(new JsonPrimitive(1.5)));
        assertThrows(IllegalArgumentException.class, () -> integer.fromJson(new JsonPrimitive(Long.MAX_VALUE)));
        assertThrows(IllegalArgumentException.class, () -> integer.fromJson(new JsonPrimitive("3")));
        assertThrows(IllegalArgumentException.class, () -> integer.parse("5"));
        Setting<String> string = Setting.string("string", "String", "", "", 5, UNGUARDED);
        assertEquals("  hi ", string.parse("  hi "));
        assertThrows(IllegalArgumentException.class, () -> string.fromJson(new JsonPrimitive(5)));
        assertThrows(IllegalArgumentException.class, () -> string.fromJson(JsonNull.INSTANCE));
        assertThrows(IllegalArgumentException.class, () -> string.fromJson(new JsonObject()));
    }

    @Test public void finiteDecimalAndEnumValidationIsSharedByParsingAndUpdates() {
        Setting<Double> decimal = Setting.decimal("rate", "Rate", "", 0.5, 0, 1, UNGUARDED);
        for (double value : new double[] { Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1, 2 }) {
            assertThrows(IllegalArgumentException.class, () -> decimal.set(value));
            assertThrows(IllegalArgumentException.class, () -> decimal.parse(String.valueOf(value)));
            assertThrows(IllegalArgumentException.class, () -> decimal.fromJson(new JsonPrimitive(value)));
        }
        assertEquals(Double.valueOf(0.5), decimal.get());
        Setting<Mode> mode = Setting.enumeration("mode", "Mode", "", Mode.SLOW, Mode.class, UNGUARDED);
        assertEquals(Mode.FAST, mode.parse("fAsT"));
        mode.set(Mode.FAST);
        assertEquals("fast", mode.toJson().getAsString());
        assertThrows(IllegalArgumentException.class, () -> mode.parse("unknown"));
        mode.reset();
        assertEquals(Mode.SLOW, mode.get());
    }

    @Test public void observersAreOrderedRemovableAndNotCalledForEqualValues() {
        Setting<Integer> number = Setting.integer("number", "Number", "", 1, 0, 5, UNGUARDED);
        List<String> calls = new ArrayList<>();
        ChangeSubscription[] second = new ChangeSubscription[1];
        number.onChange((setting, before, after) -> {
            assertEquals(Integer.valueOf(after), setting.get());
            calls.add(before + "->" + after);
            second[0].close();
            number.onChange((ignored, oldValue, newValue) -> calls.add("late"));
        });
        second[0] = number.onChange((setting, before, after) -> calls.add("removed"));
        number.set(2);
        number.set(2);
        assertEquals(Arrays.asList("1->2"), calls);
        number.set(3);
        assertEquals(Arrays.asList("1->2", "2->3", "late"), calls);
        second[0].close();
    }

    @Test public void observerFailureIsIsolatedAndRecursiveMutationIsRejected() {
        Setting<Integer> number = Setting.integer("number", "Number", "", 0, 0, 5, UNGUARDED);
        List<Integer> delivered = new ArrayList<>();
        number.onChange((setting, before, after) -> { throw new IllegalArgumentException("broken"); });
        number.onChange((setting, before, after) -> setting.set(3));
        number.onChange((setting, before, after) -> delivered.add(after));
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> number.set(2));
        assertEquals("broken", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertEquals(Arrays.asList(2), delivered);
        assertEquals(Integer.valueOf(2), number.get());
    }

    @Test public void mutationAndSubscriptionChangesUseClientThreadGuard() throws Exception {
        Thread client = Thread.currentThread();
        Runnable guard = () -> { if (Thread.currentThread() != client) throw new IllegalStateException("Wrong thread"); };
        Setting<Boolean> setting = Setting.bool("bool", "Boolean value", "", false, guard);
        ChangeSubscription subscription = setting.onChange((source, before, after) -> { });
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread other = new Thread(() -> {
            try {
                assertEquals(Boolean.FALSE, setting.get());
                assertEquals(Boolean.TRUE, setting.parse("true"));
                assertThrows(IllegalStateException.class, () -> setting.set(true));
                assertThrows(IllegalStateException.class, () -> setting.onChange((source, before, after) -> { }));
                assertThrows(IllegalStateException.class, subscription::close);
            } catch (Throwable ex) { failure.set(ex); }
        });
        other.start(); other.join();
        if (failure.get() != null) throw new AssertionError(failure.get());
        assertFalse(setting.get());
    }
}
