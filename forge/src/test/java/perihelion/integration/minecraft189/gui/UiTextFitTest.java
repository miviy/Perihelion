package perihelion.integration.minecraft189.gui;

import static org.junit.Assert.*;

import org.junit.Test;
import perihelion.integration.minecraft189.gui.font.GuiFonts;
import perihelion.integration.minecraft189.gui.widget.UiCanvas;

public class UiTextFitTest {
  private UiCanvas canvas() {
    UiCanvas canvas = new UiCanvas();
    canvas.font =
        new GuiFonts.Face() {
          public float width(String text) {
            return text.codePointCount(0, text.length()) * 6;
          }

          public float height() {
            return 9;
          }

          public void draw(String text, float x, float y, int color) {
            fail("Measuring must not draw");
          }
        };
    return canvas;
  }

  @Test
  public void longLabelsFitWithoutDistortingTheSelectedFace() {
    UiCanvas canvas = canvas();
    assertEquals("Minecraft", canvas.fit("Minecraft", 54));
    assertEquals("Mine...", canvas.fit("Minecraft", 42));
    assertEquals("", canvas.fit("Minecraft", 17));
    assertEquals("", canvas.fit("Minecraft", 0));
    assertEquals("...", canvas.fit("Minecraft", 18));
  }

  @Test
  public void ellipsisDoesNotSplitUnicodeCodepoints() {
    UiCanvas canvas = canvas();
    String value = "A\ud83d\ude03BCDE";
    assertEquals("A\ud83d\ude03...", canvas.fit(value, 30));
    assertEquals(value, canvas.fit(value, 36));
  }

  @Test
  public void wrappedContentKeepsTheEntireValueAndUnicodeCodepoints() {
    UiCanvas canvas = canvas();
    String value = "Friends: Alice Bob A\ud83d\ude03BCDE";
    java.util.List<String> lines = UiCanvas.wrap(canvas.font, value, 42);
    assertEquals(value, String.join("", lines));
    assertTrue(lines.size() > 1);
    for (String line : lines) {
      assertTrue(canvas.font.width(line) <= 42);
      assertFalse(Character.isHighSurrogate(line.charAt(line.length() - 1)));
      assertFalse(Character.isLowSurrogate(line.charAt(0)));
      assertFalse(line.contains("..."));
    }
  }
}
