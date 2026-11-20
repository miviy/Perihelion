package perihelion.integration.minecraft189.render;

import java.util.function.ToIntFunction;
import java.util.function.UnaryOperator;

/** Display-only text rewriting that keeps vanilla's index arithmetic on the original string. */
public final class DisplayText {
  private DisplayText() {}

  /** Characters a Minecraft username may contain. */
  static boolean wordChar(char c) {
    return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_';
  }

  /** A formatting code's selector character never joins a name to its neighbour. */
  private static boolean joined(String text, int index) {
    return index > 0
        && index < text.length()
        && wordChar(text.charAt(index - 1))
        && wordChar(text.charAt(index))
        && !(index >= 2 && text.charAt(index - 2) == '§');
  }

  /**
   * Usernames are ASCII, so only ASCII letters fold. Unicode folding would also accept
   * lookalikes, such as dotless U+0131 for 'i' or the Kelvin sign for 'k', that are not the name.
   */
  private static boolean asciiMatches(String text, int offset, String word) {
    for (int i = 0; i < word.length(); i++) {
      char a = text.charAt(offset + i), b = word.charAt(i);
      if (a != b && asciiLower(a) != asciiLower(b)) return false;
    }
    return true;
  }

  private static char asciiLower(char c) {
    return c >= 'A' && c <= 'Z' ? (char) (c + 32) : c;
  }

  /** Replaces whole-word, ASCII case-insensitive occurrences of {@code word}. */
  public static String replaceWord(String text, String word, String replacement) {
    int length = word.length();
    if (length == 0 || text.length() < length) return text;
    StringBuilder out = null;
    int copied = 0;
    // Match in place: lowercasing a copy can change its length (U+0130 becomes two chars), and
    // offsets from that copy would then point at the wrong span of the original.
    for (int found = 0; found <= text.length() - length; found++) {
      // A formatting selector (the char after §) is never part of a name: "§aPlayer" must not
      // match a name "APlayer" and consume the code along with someone else's visible name.
      if (found < copied || found > 0 && text.charAt(found - 1) == '§') continue;
      if (!asciiMatches(text, found, word)) continue;
      int end = found + length;
      if (joined(text, found) || joined(text, end)) continue;
      if (out == null) out = new StringBuilder(text.length());
      out.append(text, copied, found).append(replacement);
      copied = end;
    }
    return out == null ? text : out.append(text, copied, text.length()).toString();
  }

  /**
   * Trims the original text so its rewritten width fits. Callers such as chat wrapping and text
   * fields index the original string with the returned length (a text field maps a click to a
   * cursor this way), so the result must be a prefix (or, reversed, a suffix) of the original,
   * cut at character precision like vanilla. The one exception: a cut never falls inside a
   * rewritten span, so a protected name is kept whole or not at all, and the caller wraps it
   * instead of showing a fragment.
   */
  public static String trim(
      String text,
      int width,
      boolean reverse,
      ToIntFunction<String> measure,
      UnaryOperator<String> display) {
    int n = text.length();
    String shown = display.apply(text);
    // Width is monotonic over allowed cuts. Binary search the kept length, stepping down to the
    // nearest allowed cut, so the rewrite runs only O(log n) times; text fields trim per frame.
    int low = 0, high = n, best = 0;
    while (low <= high) {
      int mid = (low + high) >>> 1, keep = mid;
      while (keep >= low && !allowed(text, shown, keep, reverse, display)) keep--;
      if (keep < low) {
        low = mid + 1;
        continue;
      }
      if (measure.applyAsInt(kept(text, keep, reverse)) <= width) {
        best = keep;
        low = mid + 1;
      } else high = keep - 1;
    }
    return kept(text, best, reverse);
  }

  private static String kept(String text, int keep, boolean reverse) {
    return reverse ? text.substring(text.length() - keep) : text.substring(0, keep);
  }

  private static boolean allowed(
      String text, String shown, int keep, boolean reverse, UnaryOperator<String> display) {
    int n = text.length();
    if (keep == 0 || keep == n) return true;
    int index = reverse ? n - keep : keep;
    if (text.charAt(index - 1) == '\u00a7') return false; // Never split a formatting code.
    // Each side is measured and drawn on its own, so it must rewrite exactly as it does inside
    // the whole text. A cut inside a rewritten span leaves fragments that no longer match ("Yo" +
    // "unger" for a name "Younger"). A cut through a glued word creates a match the whole text
    // never had ("Notchling" + "X" from "NotchlingX" would draw another player as "YouX").
    return (display.apply(text.substring(0, index)) + display.apply(text.substring(index)))
        .equals(shown);
  }
}
