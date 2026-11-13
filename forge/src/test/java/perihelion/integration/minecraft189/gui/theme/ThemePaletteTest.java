package perihelion.integration.minecraft189.gui.theme;

import static org.junit.Assert.*;

import org.junit.Test;

public class ThemePaletteTest {
  @Test
  public void oneTwoAndThreeStopsUseExactEndpointsAndClampedLinearInterpolation() {
    ThemePalette one = ThemePalette.of(0x010203);
    assertEquals(1, one.size());
    assertEquals(0xff010203, one.color(.8f));
    ThemePalette two = ThemePalette.of(0xffff0000, 0xff0000ff);
    assertEquals(2, two.size());
    assertEquals(0xffff0000, two.color(-2));
    assertEquals(0xff800080, two.color(.5f));
    assertEquals(0xff0000ff, two.color(2));
    ThemePalette three = ThemePalette.of(0xffff0000, 0xff00ff00, 0xff0000ff);
    assertEquals(3, three.size());
    assertEquals(0xffff0000, three.color(0));
    assertEquals(0xff808000, three.color(.25f));
    assertEquals(0xff00ff00, three.color(.5f));
    assertEquals(0xff008080, three.color(.75f));
    assertEquals(0xff0000ff, three.color(1));
    assertEquals(three.color(0), three.color(Float.NaN));
    assertEquals(three.color(0), three.color(Float.NEGATIVE_INFINITY));
    assertEquals(three.color(1), three.color(Float.POSITIVE_INFINITY));
  }

  @Test
  public void paletteCopiesItsInputsAndAlwaysSamplesOpaqueColors() {
    int[] stops = {0x11010203, 0x44050607};
    ThemePalette palette = ThemePalette.of(stops);
    stops[0] = 0xffffffff;
    assertEquals(0xff010203, palette.color(0));
    assertEquals(0xff050607, palette.color(1));
    assertEquals(255, palette.color(.37f) >>> 24);
    assertThrows(IllegalArgumentException.class, () -> ThemePalette.of());
    assertThrows(IllegalArgumentException.class, () -> ThemePalette.of(1, 2, 3, 4));
    assertThrows(NullPointerException.class, () -> ThemePalette.of((int[]) null));
  }

  @Test
  public void darkCustomAccentSamplesRemainReadableWithoutAlteringTheirRawStops() {
    for (int color : new int[] {0xff000000, 0xff000003, 0xff0000ff, 0xff003300, 0xff330000}) {
      ThemePalette palette = ThemePalette.of(color);
      assertEquals(color, palette.color(0));
      assertTrue(contrast(palette.accent(0), 0xff242322) >= 4.5);
    }
    ThemePalette palette = ThemePalette.of(0xff000000, 0xff0000ff, 0xffb01877);
    for (int step = 0; step <= 100; step++)
      assertTrue(contrast(palette.accent(step / 100f), 0xff242322) >= 4.5);
    assertEquals(0xffef883b, ThemePalette.DEFAULT.accent(0));
    assertEquals(0xffffffff, ThemePalette.of(0xffffffff).accent(0));
  }

  @Test
  public void enabledRowsKeepWhiteLabelsReadableForDarkAndPaleCustomColors() {
    ThemePalette palette = ThemePalette.of(0xff000000, 0xffffffff, 0xffef883b);
    for (int step = 0; step <= 100; step++) {
      int fill = palette.fill(step / 100f);
      assertEquals(255, fill >>> 24);
      assertTrue(
          "White labels retain contrast against each fill", contrast(0xffffffff, fill) >= 4.5);
      assertTrue("New near-white labels retain contrast", contrast(0xfff2f2f2, fill) >= 4.5);
    }
    assertEquals(0xff000000, palette.fill(0));
    assertEquals(0xffffffff, palette.color(.5f));
    assertTrue(ThemePalette.DEFAULT.fill(0) != ThemePalette.DEFAULT.color(0));
  }

  private static double contrast(int first, int second) {
    double a = luminance(first), b = luminance(second);
    return (Math.max(a, b) + .05) / (Math.min(a, b) + .05);
  }

  private static double luminance(int color) {
    return .2126 * channel(color >> 16 & 255)
        + .7152 * channel(color >> 8 & 255)
        + .0722 * channel(color & 255);
  }

  private static double channel(int value) {
    double c = value / 255.0;
    return c <= .04045 ? c / 12.92 : Math.pow((c + .055) / 1.055, 2.4);
  }
}
