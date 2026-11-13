package perihelion.core.setting;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class SettingDisplayNameTest {
    private static final Runnable GUARD = () -> {};
    private enum Mode { FIRST, SECOND }

    @Test public void everyFactoryKeepsAnExplicitLabelSeparateFromIdAndDescription() {
        String id = "stable.setting-id", label = "Readable setting name", description = "Explains the value";
        List<Setting<?>> settings = Arrays.asList(
                Setting.bool(id, label, description, false, GUARD),
                Setting.integer(id, label, description, 2, 0, 10, GUARD),
                Setting.integer(id, label, description, 2, 0, 10, 2, GUARD),
                Setting.decimal(id, label, description, 0.5, 0, 1, GUARD),
                Setting.decimal(id, label, description, 0.5, 0, 1, 0.1, GUARD),
                Setting.enumeration(id, label, description, Mode.FIRST, Mode.class, GUARD),
                Setting.string(id, label, description, "value", 20, GUARD),
                Setting.range(id, label, description, new DoubleRange(0, 1), 0, 2, GUARD),
                Setting.range(id, label, description, new DoubleRange(0, 1), 0, 2, 0.5, GUARD),
                Setting.color(id, label, description, ColorValue.rgb(1, 2, 3), GUARD),
                Setting.multiSelect(id, label, description, EnumSet.of(Mode.FIRST), Mode.class, GUARD));
        for (Setting<?> setting : settings) {
            setting.freeze();
            assertEquals(id, setting.id());
            assertEquals(label, setting.displayName());
            assertEquals(description, setting.description());
        }
    }

    @Test public void missingLabelsAreRejectedAndLabelsDoNotFollowIdentifierRules() {
        assertThrows(NullPointerException.class, () -> Setting.bool("flag", null, "", false, GUARD));
        for (String blank : new String[] { "", " ", "\t\n" }) {
            assertThrows(IllegalArgumentException.class, () -> Setting.bool("flag", blank, "", false, GUARD));
        }
        Setting<Boolean> setting = Setting.bool("flag", "Show FPS / café", "", false, GUARD);
        setting.freeze(); setting.set(true);
        assertEquals("Show FPS / café", setting.displayName());
        assertEquals("flag", setting.id());
        assertTrue(setting.toJson().getAsBoolean());
    }
}
