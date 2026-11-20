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
   * Usernames are ASCII, so only ASCII letters fold. Unicode folding would also accept lookalikes
   * such as dotless U+0131 for 'i', which {@link #wordChar} treats as a boundary; trimming could
   * then cut inside an accepted match.
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
      if (found < copied || !asciiMatches(text, found, word)) continue;
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
   * fields index the original string with the returned length, so the result must be a prefix
   * (or, reversed, a suffix) of the original. A rewritten span is kept whole or not at all:
   * cuts fall between words, or, when even the first word does not fit, only where the kept
   * text rewrites to a prefix (suffix) of the whole rewritten text. The result may then be
   * empty, and the caller wraps the span to its next line instead of showing a fragment.
   */
  public static String trim(
      String text,
      int width,
      boolean reverse,
      ToIntFunction<String> measure,
      UnaryOperator<String> display) {
    int n = text.length();
    int cut = search(text, width, reverse, measure, null);
    if (reverse ? cut == n && n > 0 : cut == 0 && n > 0)
      cut = search(text, width, reverse, measure, display);
    return reverse ? text.substring(cut) : text.substring(0, cut);
  }

  /**
   * Width is monotonic in the kept length, so binary search the allowed cuts for the most text.
   * Without {@code display} cuts fall between words; with it, at any character whose kept text
   * leaves every rewritten span intact.
   */
  private static int search(
      String text,
      int width,
      boolean reverse,
      ToIntFunction<String> measure,
      UnaryOperator<String> display) {
    int n = text.length();
    String shown = display == null ? null : display.apply(text);
    int[] keeps = new int[n + 1];
    int count = 0;
    for (int keep = 0; keep <= n; keep++) {
      int index = reverse ? n - keep : keep;
      if (index > 0 && index < n && text.charAt(index - 1) == '\u00a7') continue;
      if (display == null) {
        if (joined(text, index)) continue;
      } else if (keep > 0 && keep < n) {
        String kept = display.apply(reverse ? text.substring(index) : text.substring(0, index));
        if (reverse ? !shown.endsWith(kept) : !shown.startsWith(kept)) continue;
      }
      keeps[count++] = keep;
    }
    int low = 0, high = count - 1, best = 0;
    while (low <= high) {
      int mid = (low + high) >>> 1, keep = keeps[mid];
      if (measure.applyAsInt(reverse ? text.substring(n - keep) : text.substring(0, keep)) <= width) {
        best = keep;
        low = mid + 1;
      } else high = mid - 1;
    }
    return reverse ? n - best : best;
  }
}
