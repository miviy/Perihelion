package perihelion.integration.minecraft189.hud;

/** Transient screen coordinates. Persistence belongs to the HUD module, once on release/close. */
final class HudDragState {
  HudModule.Element element;
  int x, y, width, height, boxWidth, boxHeight;
  private int offsetX, offsetY;

  void begin(
      HudModule.Element element,
      int mouseX,
      int mouseY,
      int x,
      int y,
      int width,
      int height,
      int boxWidth,
      int boxHeight) {
    this.element = element;
    this.width = width;
    this.height = height;
    this.boxWidth = boxWidth;
    this.boxHeight = boxHeight;
    offsetX = mouseX - x;
    offsetY = mouseY - y;
    move(mouseX, mouseY);
  }

  void move(int mouseX, int mouseY) {
    if (element == null) return;
    x = Math.max(0, Math.min(Math.max(0, width - boxWidth), mouseX - offsetX));
    y = Math.max(0, Math.min(Math.max(0, height - boxHeight), mouseY - offsetY));
  }

  void cancel() {
    element = null;
  }
}
