package perihelion.integration.minecraft189.event;

import net.minecraft.client.gui.GuiScreen;
import perihelion.core.event.CancellableEvent;

/** Client-thread GUI mouse input before vanilla handles it, in scaled screen coordinates. */
public final class GuiMouseEvent extends CancellableEvent {
  private final GuiScreen screen;
  private final int x, y, button, wheel;
  private final boolean down;

  public GuiMouseEvent(GuiScreen screen, int x, int y, int button, boolean down, int wheel) {
    this.screen = screen;
    this.x = x;
    this.y = y;
    this.button = button;
    this.down = down;
    this.wheel = wheel;
  }

  public GuiScreen screen() {
    return screen;
  }

  public int x() {
    return x;
  }

  public int y() {
    return y;
  }

  public int button() {
    return button;
  }

  public boolean down() {
    return down;
  }

  public int wheel() {
    return wheel;
  }
}
