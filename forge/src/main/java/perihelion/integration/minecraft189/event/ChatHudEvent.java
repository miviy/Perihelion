package perihelion.integration.minecraft189.event;

import net.minecraft.client.gui.GuiChat;
import perihelion.core.event.CancellableEvent;

/** Client-thread chat overlay/input, owned by the normal module event lifecycle. */
public final class ChatHudEvent extends CancellableEvent {
  public enum Kind {
    OPEN,
    CLOSE,
    DRAW,
    MOUSE,
    KEY
  }

  public final Kind kind;
  public final GuiChat screen;
  public final int width, height, x, y, button, wheel, key;
  public final boolean pressed;

  private ChatHudEvent(
      Kind kind, GuiChat screen, int x, int y, int button, boolean pressed, int wheel, int key) {
    this.kind = kind;
    this.screen = screen;
    width = screen.width;
    height = screen.height;
    this.x = x;
    this.y = y;
    this.button = button;
    this.pressed = pressed;
    this.wheel = wheel;
    this.key = key;
  }

  public static ChatHudEvent lifecycle(Kind kind, GuiChat screen) {
    return new ChatHudEvent(kind, screen, 0, 0, -1, false, 0, 0);
  }

  public static ChatHudEvent draw(GuiChat screen, int x, int y) {
    return new ChatHudEvent(Kind.DRAW, screen, x, y, -1, false, 0, 0);
  }

  public static ChatHudEvent mouse(
      GuiChat screen, int x, int y, int button, boolean pressed, int wheel) {
    return new ChatHudEvent(Kind.MOUSE, screen, x, y, button, pressed, wheel, 0);
  }

  public static ChatHudEvent key(GuiChat screen, int key, boolean pressed) {
    return new ChatHudEvent(Kind.KEY, screen, 0, 0, -1, pressed, 0, key);
  }
}
