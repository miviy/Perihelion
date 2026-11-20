package perihelion.integration.minecraft189.render;

import static org.junit.Assert.*;

import java.util.function.ToIntFunction;
import org.junit.Test;

public class DisplayTextTest {
  private static final String NAME = "Notchling";

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
    String unchanged = "no name here";
    assertSame(unchanged, DisplayText.replaceWord(unchanged, NAME, "You"));
  }

  @Test
  public void trimmingReturnsOriginalPrefixSizedByProtectedWidth() {
    String line = "Notchling killed Steve";
    // "You killed" is 10 wide; the original prefix is longer, but callers index the original.
    String kept = DisplayText.trim(line, 10, false, WIDTH);
    assertEquals("Notchling killed", kept);
    assertTrue(line.startsWith(kept));
  }

  @Test
  public void trimmingNeverCutsInsideAProtectedName() {
    String line = "hello Notchling";
    // Raw "hello No" would be 8 wide; the name must appear whole or not at all.
    assertEquals("hello ", DisplayText.trim(line, 8, false, WIDTH));
    assertEquals("hello Notchling", DisplayText.trim(line, 9, false, WIDTH));
  }

  @Test
  public void reverseTrimmingReturnsOriginalSuffix() {
    String line = "Steve killed Notchling";
    assertEquals("killed Notchling", DisplayText.trim(line, 10, true, WIDTH));
    assertEquals("Notchling", DisplayText.trim(line, 3, true, WIDTH));
  }

  @Test
  public void aWordWiderThanTheLimitStillTrims() {
    assertEquals("abcd", DisplayText.trim("abcdefgh", 4, false, WIDTH));
    assertEquals("", DisplayText.trim("", 4, false, WIDTH));
  }

  @Test
  public void neverSplitsAFormattingCode() {
    String kept = DisplayText.trim("ab§cdef", 2, false, WIDTH);
    assertFalse(kept.endsWith("§"));
  }
}
