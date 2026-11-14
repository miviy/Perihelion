package perihelion.integration.minecraft189.gui.widget;

import net.minecraft.client.gui.GuiTextField;
import perihelion.integration.minecraft189.gui.font.GuiFonts;

/** Draws a vanilla text field's input state with the active ClickGUI font. */
public final class InlineTextFieldView {
  private int first, lastCursor = -1;
  private String lastText;
  private GuiFonts.Face lastFace;
  private float lastWidth;
  private boolean rightAligned;

  public void rightAligned(boolean aligned) {
    rightAligned = aligned;
  }

  public void reset() {
    first = 0;
    lastCursor = -1;
    lastText = null;
    lastFace = null;
  }

  private static int boundary(String text, int index) {
    index = Math.max(0, Math.min(index, text.length()));
    return index > 0
            && index < text.length()
            && Character.isLowSurrogate(text.charAt(index))
            && Character.isHighSurrogate(text.charAt(index - 1))
        ? index - 1
        : index;
  }

  private static float advance(GuiFonts.Face face, String text, int index) {
    return face.width(text.substring(index, index + Character.charCount(text.codePointAt(index))));
  }

  private void window(GuiFonts.Face face, String text, int cursor, float width) {
    if (cursor == lastCursor && text.equals(lastText) && face == lastFace && width == lastWidth)
      return;
    float room = Math.max(1, width - 2);
    first = boundary(text, Math.min(first, cursor));
    float used = 0;
    for (int i = first; i < cursor; i += Character.charCount(text.codePointAt(i)))
      used += advance(face, text, i);
    while (first < cursor && used > room) {
      used -= advance(face, text, first);
      first += Character.charCount(text.codePointAt(first));
    }
    // Fill unused space only when the remaining text fits. Moving the caret back within a
    // scrolled field must not pull it from the clicked left edge to the opposite edge.
    float tail = face.width(text.substring(first));
    while (first > 0) {
      int before = text.offsetByCodePoints(first, -1);
      float previous = advance(face, text, before);
      if (tail + previous > room) break;
      first = before;
      tail += previous;
    }
    lastCursor = cursor;
    lastText = text;
    lastFace = face;
    lastWidth = width;
  }

  private int end(GuiFonts.Face face, String text, float width) {
    float room = Math.max(1, width - 2), used = 0;
    int end = first;
    while (end < text.length()) {
      float next = advance(face, text, end);
      if (used + next > room) break;
      used += next;
      end += Character.charCount(text.codePointAt(end));
    }
    return end;
  }

  private static float snap(float coordinate, float pixelScale) {
    return Math.round(coordinate * pixelScale) / pixelScale;
  }

  private float textLeft(GuiFonts.Face face, String text, int end, float x, float width) {
    return rightAligned ? x + Math.max(0, width - 3 - face.width(text.substring(first, end))) : x;
  }

  public void draw(
      UiCanvas canvas, GuiTextField field, float x, float y, float width, long blinkEpoch) {
    draw(canvas, field, x, y, width, blinkEpoch, true);
  }

  /** Native menu inputs retain their focus state and hide the caret while unfocused. */
  public void draw(
      UiCanvas canvas,
      GuiTextField field,
      float x,
      float y,
      float width,
      long blinkEpoch,
      boolean focused) {
    GuiFonts.Face face = canvas.font;
    String text = field.getText();
    int cursor = boundary(text, Math.min(field.getCursorPosition(), text.length()));
    int selection = boundary(text, Math.min(field.getSelectionEnd(), text.length()));
    window(face, text, selection, width);
    int end = end(face, text, width);
    float left = snap(textLeft(face, text, end, x, width), canvas.pixelScale),
        top = snap(y + 1, canvas.pixelScale);
    if (selection != cursor) {
      int from = Math.max(first, Math.min(cursor, selection));
      int to = Math.min(end, Math.max(cursor, selection));
      if (from < to) {
        float startX = left + face.width(text.substring(first, from));
        float endX = left + face.width(text.substring(first, to));
        canvas.rect(startX, top, endX - startX, face.height(), canvas.selection);
      }
    }
    int selectedFrom = Math.max(first, Math.min(cursor, selection)),
        selectedTo = Math.min(end, Math.max(cursor, selection));
    if (selectedFrom < selectedTo) {
      canvas.text(text.substring(first, selectedFrom), left, top, canvas.text);
      canvas.text(
          text.substring(selectedFrom, selectedTo),
          left + face.width(text.substring(first, selectedFrom)),
          top,
          canvas.selectedText);
      canvas.text(
          text.substring(selectedTo, end),
          left + face.width(text.substring(first, selectedTo)),
          top,
          canvas.text);
    } else canvas.text(text.substring(first, end), left, top, canvas.text);
    if (focused && (System.currentTimeMillis() - blinkEpoch) % 1000 < 500) {
      float caret =
          left + face.width(text.substring(first, Math.max(first, Math.min(selection, end))));
      canvas.rect(
          snap(Math.min(caret, x + width - 1), canvas.pixelScale),
          top,
          1 / canvas.pixelScale,
          face.height(),
          canvas.text);
    }
  }

  public void placeCursor(UiCanvas canvas, GuiTextField field, float x, float width, float mouseX) {
    GuiFonts.Face face = canvas.font;
    String text = field.getText();
    window(face, text, boundary(text, field.getSelectionEnd()), width);
    int end = end(face, text, width);
    float offset = mouseX - snap(textLeft(face, text, end, x, width), canvas.pixelScale), used = 0;
    int position = first;
    while (position < end) {
      float next = advance(face, text, position);
      if (offset < used + next / 2) break;
      used += next;
      position += Character.charCount(text.codePointAt(position));
    }
    field.setCursorPosition(position);
    field.setSelectionPos(position);
  }
}
