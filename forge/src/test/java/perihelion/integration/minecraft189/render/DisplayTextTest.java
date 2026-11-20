package perihelion.integration.minecraft189.render;

import static org.junit.Assert.*;

import java.util.function.ToIntFunction;
import java.util.function.UnaryOperator;
import org.junit.Test;

public class DisplayTextTest {
  private static final String NAME = "Notchling";

  private static final UnaryOperator<String> DISPLAY =
      text -> DisplayText.replaceWord(text, NAME, "You");

  /** One unit per visible character after protection; formatting codes are free. */
  private static final ToIntFunction<String> WIDTH =
      text -> {
        String shown = DisplayText.replaceWord(text, NAME, "You");
        int width = 0;
        for (int i = 0; i < shown.length(); i++) {
          if (shown.charAt(i) == '§') i++;
          else width++;
        }
        return width;
      };

  @Test
  public void replacesWholeNamesIgnoringCase() {
    assertEquals("<You> hi You", DisplayText.replaceWord("<Notchling> hi notchling", NAME, "You"));
    assertEquals("§aYou§r", DisplayText.replaceWord("§aNotchling§r", NAME, "You"));
    assertEquals("Notchlings", DisplayText.replaceWord("Notchlings", NAME, "You"));
    assertEquals("xNotchling", DisplayText.replaceWord("xNotchling", NAME, "You"));
    assertEquals("NOTCHLINGs You", DisplayText.replaceWord("NOTCHLINGs NOTCHLING", NAME, "You"));
    String unchanged = "no name here";
    assertSame(unchanged, DisplayText.replaceWord(unchanged, NAME, "You"));
  }

  @Test
  public void lengthChangingCaseFoldsCannotShiftOrBreakMatches() {
    // U+0130 lowercases to two chars; offsets from a lowercased copy used to land mid-name and,
    // with enough of them, past the end of the original.
    assertEquals("İİİİ You", DisplayText.replaceWord("İİİİ Notchling", NAME, "You"));
    StringBuilder many = new StringBuilder();
    for (int i = 0; i < 20; i++) many.append('İ');
    assertEquals(many + " You", DisplayText.replaceWord(many + " Notchling", NAME, "You"));
  }

  @Test
  public void unicodeLookalikesAreNotMatchedSoTrimmingStaysConsistent() {
    // Dotless U+0131 folds to 'i' under String.regionMatches(true, ...) but is a word boundary
    // for trimming; it is a different name, not the protected one.
    String lookalike = "Notchlıng";
    assertSame(lookalike, DisplayText.replaceWord(lookalike, NAME, "You"));
    assertEquals("Not", DisplayText.trim(lookalike, 3, false, WIDTH, DISPLAY));
    // Kelvin sign U+212A folds to 'k' the same way.
    assertSame("Key", DisplayText.replaceWord("Key", "key", "You"));
  }

  @Test
  public void trimmingReturnsOriginalPrefixSizedByProtectedWidth() {
    String line = "Notchling killed Steve";
    // "You killed" is 10 wide; the original prefix is longer, but callers index the original.
    String kept = DisplayText.trim(line, 10, false, WIDTH, DISPLAY);
    assertEquals("Notchling killed", kept);
    assertTrue(line.startsWith(kept));
  }

  @Test
  public void trimmingNeverCutsInsideAProtectedName() {
    String line = "hello Notchling";
    // Raw "hello No" would be 8 wide; the name must appear whole or not at all.
    assertEquals("hello ", DisplayText.trim(line, 8, false, WIDTH, DISPLAY));
    assertEquals("hello Notchling", DisplayText.trim(line, 9, false, WIDTH, DISPLAY));
  }

  @Test
  public void reverseTrimmingReturnsOriginalSuffix() {
    String line = "Steve killed Notchling";
    assertEquals("killed Notchling", DisplayText.trim(line, 10, true, WIDTH, DISPLAY));
    assertEquals("Notchling", DisplayText.trim(line, 3, true, WIDTH, DISPLAY));
  }

  @Test
  public void fallbackTrimmingNeverShowsANameFragment() {
    // Narrower than "You": nothing fits without splitting the name, so the caller wraps it whole.
    assertEquals("", DisplayText.trim("Notchling", 2, false, WIDTH, DISPLAY));
    assertEquals("", DisplayText.trim("Notchling", 2, true, WIDTH, DISPLAY));
    assertEquals("Notchling", DisplayText.trim("Notchling", 3, false, WIDTH, DISPLAY));
    // A long unprotected word still trims by character.
    assertEquals("xy", DisplayText.trim("xyNotchling", 2, false, WIDTH, DISPLAY));
  }

  @Test
  public void aWordWiderThanTheLimitStillTrims() {
    assertEquals("abcd", DisplayText.trim("abcdefgh", 4, false, WIDTH, DISPLAY));
    assertEquals("", DisplayText.trim("", 4, false, WIDTH, DISPLAY));
  }

  @Test
  public void neverSplitsAFormattingCode() {
    String kept = DisplayText.trim("ab§cdef", 2, false, WIDTH, DISPLAY);
    assertFalse(kept.endsWith("§"));
  }
}
