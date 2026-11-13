package perihelion.integration.minecraft189.gui.font;

import static org.junit.Assert.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import perihelion.integration.minecraft189.gui.ClickGuiModule;

public class FormattedFontRunsTest {
  @Test
  public void colorsAndResetRestoreTextStylesWithoutChangingVisibleCharacters() {
    List<GuiFonts.FormattedFontRuns.Run> runs = GuiFonts.FormattedFontRuns.parse("A§lB§cC§rD");
    assertEquals(4, runs.size());
    assertEquals("ABCD", visible(runs));
    assertEquals(-1, runs.get(0).color);
    assertTrue(runs.get(1).bold);
    assertEquals(0xff5555, runs.get(2).color);
    assertFalse("Minecraft color codes reset run styles", runs.get(2).bold);
    assertEquals(-1, runs.get(3).color);
    assertFalse(runs.get(3).bold);
  }

  @Test
  public void uppercaseDecorationsAndTrailingLiteralSectionSignRemainUsable() {
    List<GuiFonts.FormattedFontRuns.Run> runs =
        GuiFonts.FormattedFontRuns.parse("§N§M§OText§r plain 😀§");
    assertEquals("Text plain 😀§", visible(runs));
    assertTrue(runs.get(0).underline);
    assertTrue(runs.get(0).strike);
    assertTrue(runs.get(0).italic);
    assertFalse(runs.get(1).italic);
    assertFalse(runs.get(1).underline);
    assertFalse(runs.get(1).strike);
    assertEquals(0xffffff, GuiFonts.FormattedFontRuns.parse("§xwhite").get(0).color);
    assertTrue(GuiFonts.FormattedFontRuns.parse("§a§l").isEmpty());
  }

  @Test
  public void formattedMeasurementsKeepTheSelectedFamilyAndWeightWithoutGlUploads()
      throws Exception {
    GuiFonts owner = new GuiFonts();
    GuiFonts.Face regular = metrics(owner, false), bold = metrics(owner, true);
    assertEquals(
        "Color codes keep the selected font's measured advances",
        regular.width("Server Online 12"),
        regular.width("Server §aOnline§r 12"),
        .001f);
    assertEquals(
        "Inline bold advances come from the selected family's real Bold face",
        regular.width("Ping ") + bold.width("strong") + regular.width(" plain"),
        regular.width("Ping §lstrong§r plain"),
        .001f);
    assertEquals(
        "The global Bold preference survives run colors and reset",
        bold.width("ABC"),
        bold.width("A§cB§rC"),
        .001f);
    assertEquals(regular.width("Line"), regular.width("§n§mLine"), .001f);
    Field resident = GuiFonts.class.getDeclaredField("pixelFaces");
    resident.setAccessible(true);
    assertTrue(
        "Measurement does not upload or evict GPU textures",
        ((Map<?, ?>) resident.get(owner)).isEmpty());
  }

  private static String visible(List<GuiFonts.FormattedFontRuns.Run> runs) {
    StringBuilder visible = new StringBuilder();
    for (GuiFonts.FormattedFontRuns.Run run : runs) visible.append(run.text);
    return visible.toString();
  }

  private static GuiFonts.Face metrics(GuiFonts owner, boolean bold) throws Exception {
    Class<?> key = Class.forName(GuiFonts.class.getName() + "$PixelKey");
    Constructor<?> makeKey =
        key.getDeclaredConstructor(
            ClickGuiModule.Font.class,
            boolean.class,
            boolean.class,
            int.class,
            float.class,
            float.class);
    makeKey.setAccessible(true);
    Object value =
        makeKey.newInstance(ClickGuiModule.Font.ROBOTO_CONDENSED, bold, false, 15, 1.6f, 1.3f);
    Constructor<?> face =
        Class.forName(GuiFonts.class.getName() + "$PixelFace")
            .getDeclaredConstructor(GuiFonts.class, key, boolean.class);
    face.setAccessible(true);
    return (GuiFonts.Face) face.newInstance(owner, value, false);
  }
}
