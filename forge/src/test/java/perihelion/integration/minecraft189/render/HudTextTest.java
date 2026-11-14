package perihelion.integration.minecraft189.render;

import org.junit.Test;
import static org.junit.Assert.*;

public class HudTextTest {
    @Test public void lowAlphaCannotTriggerVanillasOpaqueFallback() {
        for (int alpha = 1; alpha <= 3; alpha++) {
            assertEquals(0x04123456, HudText.fontColor(alpha << 24 | 0x123456));
        }
        assertEquals(0x04123456, HudText.fontColor(0x04123456));
        assertEquals(0xff123456, HudText.fontColor(0xff123456));
        assertEquals(0x80123456, HudText.fontColor(0x80123456));
    }
}
