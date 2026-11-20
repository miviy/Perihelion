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
    // Dotless U+0131 folds to 'i' under String.regionMatches(true, ...); it is a different name,
    // not the protected one, and trims like any unprotected text.
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
  public void unprotectedWordsTrimByCharacterForCursorPlacement() {
    // Text fields map a click to a cursor with the trimmed length, so ordinary words beside a
    // protected name must still cut at any character.
    String line = "Notchling killed Steve";
    assertEquals("Notchling ", DisplayText.trim(line, 4, false, WIDTH, DISPLAY));
    assertEquals("Notchling k", DisplayText.trim(line, 5, false, WIDTH, DISPLAY));
    assertEquals("Notchling kil", DisplayText.trim(line, 7, false, WIDTH, DISPLAY));
    assertEquals("eve", DisplayText.trim(line, 3, true, WIDTH, DISPLAY));
  }

  @Test
  public void aFormattingOnlyPrefixIsNeverTheWholeResult() {
    // Chat wrapping restores the formatting prefix on the remainder; returning only "§a" at the
    // start of a line would hand it the same input forever.
    assertEquals("§aabcd", DisplayText.trim("§aabcdefgh Notchling", 4, false, WIDTH, DISPLAY));
    StringBuilder wide = new StringBuilder("§a");
    for (int i = 0; i < 100; i++) wide.append('W');
    assertEquals(
        "§aWWWWWWWWWW", DisplayText.trim(wide + " Notchling", 10, false, WIDTH, DISPLAY));
  }

  @Test
  public void longUnbrokenTextRewritesOnlyLogarithmicallyManyCandidates() {
    StringBuilder line = new StringBuilder();
    for (int i = 0; i < 10_000; i++) line.append('W');
    line.append(" Notchling");
    int[] rewrites = {0};
    UnaryOperator<String> counting =
        text -> {
          rewrites[0]++;
          return DISPLAY.apply(text);
        };
    String kept = DisplayText.trim(line.toString(), 300, false, WIDTH, counting);
    assertEquals(300, kept.length());
    assertTrue("rewrites=" + rewrites[0], rewrites[0] <= 64);
  }

  @Test
  public void aFormattingSelectorIsNeverPartOfAMatch() {
    assertEquals("§aPlayer", DisplayText.replaceWord("§aPlayer", "APlayer", "You"));
    assertEquals("§aYou", DisplayText.replaceWord("§aAPlayer", "APlayer", "You"));
  }

  @Test
  public void namesThatStartLikeTheReplacementAreStillKeptWhole() {
    UnaryOperator<String> display = text -> DisplayText.replaceWord(text, "Younger", "You");
    ToIntFunction<String> width = text -> display.apply(text).length();
    // "Yo" reads like the start of "You" but is a fragment of the real name.
    assertEquals("", DisplayText.trim("Younger", 2, false, width, display));
    assertEquals("", DisplayText.trim("Younger", 2, true, width, display));
    assertEquals("Younger", DisplayText.trim("Younger", 3, false, width, display));
    assertEquals("Younger", DisplayText.trim("Younger", 3, true, width, display));
    assertEquals("hi ", DisplayText.trim("hi Younger", 5, false, width, display));
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
  public void trimmingNeverShowsANameFragment() {
    // Narrower than "You": nothing fits without splitting the name, so the caller wraps it whole.
    assertEquals("", DisplayText.trim("Notchling", 2, false, WIDTH, DISPLAY));
    assertEquals("", DisplayText.trim("Notchling", 2, true, WIDTH, DISPLAY));
    assertEquals("Notchling", DisplayText.trim("Notchling", 3, false, WIDTH, DISPLAY));
    // A long unprotected word still trims by character, but never right before a glued name:
    // "Notchling" drawn on its own would show another player's "xyNotchling" as "xyYou".
    assertEquals("x", DisplayText.trim("xyNotchling", 2, false, WIDTH, DISPLAY));
    assertEquals("Notchli", DisplayText.trim("NotchlingX Notchling", 7, false, WIDTH, DISPLAY));
    assertEquals("Notchlin", DisplayText.trim("NotchlingX Notchling", 8, false, WIDTH, DISPLAY));
    assertEquals("NotchlingX", DisplayText.trim("NotchlingX Notchling", 10, false, WIDTH, DISPLAY));
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
