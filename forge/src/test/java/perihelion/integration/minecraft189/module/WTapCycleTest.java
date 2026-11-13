package perihelion.integration.minecraft189.module;

import org.junit.Test;
import static org.junit.Assert.*;

public class WTapCycleTest {
    @Test public void recentlyHurtSprintingTargetArmsExactlyOneReset() {
        WTapCycle cycle = new WTapCycle();
        assertFalse(cycle.attack(5, true, 100, 0));
        assertFalse(cycle.update(100, 0));
        assertFalse(cycle.attack(6, false, 100, 0));
        assertTrue(cycle.attack(6, true, 100, 0.999));
        assertTrue(cycle.update(100, 0.999));
        assertFalse(cycle.update(100, 0));
    }
    @Test public void chanceRollsAtAttackAndUpdateAndFailedUpdateCanRetry() {
        WTapCycle cycle = new WTapCycle();
        assertFalse(cycle.attack(10, true, 0, 0));
        assertFalse(cycle.attack(10, true, 50, 0.5));
        assertTrue(cycle.attack(10, true, 50, 0.499));
        assertFalse(cycle.update(50, 0.5));
        assertTrue(cycle.update(50, 0.499));
    }
    @Test public void ineligibleAttackAndExplicitClearDiscardPendingReset() {
        WTapCycle cycle = new WTapCycle();
        assertTrue(cycle.attack(10, true, 100, 0));
        assertFalse(cycle.attack(0, true, 100, 0));
        assertFalse(cycle.update(100, 0));
        cycle.attack(10, true, 100, 0); cycle.clear();
        assertFalse(cycle.update(100, 0));
    }
}
