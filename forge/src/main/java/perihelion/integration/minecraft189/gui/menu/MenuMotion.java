package perihelion.integration.minecraft189.gui.menu;

/** Bounded, time-based cursor easing. Controls stay fixed while the scene responds to the mouse. */
public final class MenuMotion {
  private long previous = System.nanoTime();
  public float x, y, delta;

  public void update(int mouseX, int mouseY, int width, int height) {
    long now = System.nanoTime();
    delta = Math.max(0, Math.min(.05f, (now - previous) / 1_000_000_000f));
    previous = now;
    boolean inside = mouseX >= 0 && mouseY >= 0 && mouseX <= width && mouseY <= height;
    float targetX = inside ? mouseX * 2f / Math.max(1, width) - 1 : 0;
    float targetY = inside ? mouseY * 2f / Math.max(1, height) - 1 : 0;
    float weight = 1 - (float) Math.exp(-delta * 4.5);
    x += (targetX - x) * weight;
    y += (targetY - y) * weight;
  }
}
