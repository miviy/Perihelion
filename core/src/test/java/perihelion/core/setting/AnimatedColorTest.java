package perihelion.core.setting;

import org.junit.Test;
import com.google.gson.JsonPrimitive;
import static org.junit.Assert.*;

public class AnimatedColorTest {
    @Test public void fixedColorsKeepTheirOldJsonAndAnimatedColorsRoundtrip() {
        Setting<ColorValue> setting=Setting.color("c","C","",ColorValue.rgba(20,40,60,80),()->{});
        assertEquals(new JsonPrimitive("#14283C50"),setting.toJson());
        ColorValue animated=setting.get().withRainbow(true,2.3);setting.set(animated);
        assertEquals(animated,setting.fromJson(setting.toJson()));assertEquals(80,animated.resolvedArgb(5000)>>>24);
        assertNotEquals(animated.resolvedArgb(0),animated.resolvedArgb(1000));
        assertEquals(0x5014283c,animated.argb());
    }
    @Test public void rainbowSpeedRejectsInvalidValuesAndBaseColorIsRetained() {
        ColorValue value=ColorValue.rgb(1,2,3);
        for(double bad:new double[]{0,11,Double.NaN,Double.POSITIVE_INFINITY})assertThrows(IllegalArgumentException.class,()->value.withRainbow(true,bad));
        assertEquals(value,value.withRainbow(true,1).withRainbow(false,1));
    }
}
