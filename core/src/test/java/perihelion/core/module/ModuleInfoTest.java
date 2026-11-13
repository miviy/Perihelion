package perihelion.core.module;

import org.junit.Test;
import static org.junit.Assert.*;

public class ModuleInfoTest {
    @Test public void categoryAndHumanNameAreIndependentOfTheStableId() {
        for (ModuleCategory category : ModuleCategory.values()) {
            ModuleInfo info = new ModuleInfo("test.probe", "Example module", "Description", category, true);
            assertEquals("test.probe", info.id());
            assertEquals("Example module", info.name());
            assertEquals("Description", info.description());
            assertSame(category, info.category());
            assertTrue(info.requiresWorld());
            assertFalse(category.displayName().trim().isEmpty());
        }
    }

    @Test public void invalidIdentityMissingCategoryAndBlankNamesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ModuleInfo("Invalid ID", "Name", "", ModuleCategory.MISC, false));
        assertThrows(NullPointerException.class, () -> new ModuleInfo("probe", "Name", "", null, false));
        assertThrows(NullPointerException.class, () -> new ModuleInfo("probe", null, "", ModuleCategory.MISC, false));
        for (String blank : new String[] { "", " ", "\t\n" }) {
            assertThrows(IllegalArgumentException.class, () -> new ModuleInfo("probe", blank, "", ModuleCategory.MISC, false));
        }
    }
}
