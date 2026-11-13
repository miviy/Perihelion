package perihelion.integration.minecraft189.gui.modern;

/** Frame-rate-independent, bounded GUI easing. No clock reads or allocations per value. */
public final class GuiMotion {
  private GuiMotion() {}

  public static float weight(float seconds, float speed) {
    return 1 - (float) Math.exp(-Math.max(0, Math.min(.05f, seconds)) * speed);
  }

  public static float approach(float value, float target, float weight) {
    float next = value + (target - value) * weight;
    return Math.abs(next - target) < .001f ? target : next;
  }

  public static float height(float value, float target, float weight) {
    float next = value + (target - value) * weight;
    return Math.abs(next - target) < .03f ? target : Math.max(0, next);
  }
}
