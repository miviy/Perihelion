package perihelion.integration.minecraft189.gui.modern;

/** Rounded outside contours for an annular shadow; no sampled texture or retained frame data. */
final class ShadowContour {
  static final int POINTS = 53;
  private static final float[] COS = new float[POINTS], SIN = new float[POINTS];
  // Circumscribe each 7.5-degree arc segment so its chord never enters the live panel.
  private static final float ARC_GUARD = (float) (1 / Math.cos(Math.PI / 48));

  static {
    for (int i = 0; i < POINTS; i++) {
      int p = i % 52;
      double angle = (p / 13 * 90 + p % 13 * 7.5) * Math.PI / 180;
      COS[i] = (float) Math.cos(angle);
      SIN[i] = (float) Math.sin(angle);
    }
  }

  private ShadowContour() {}

  static float radius(float width, float height, float requested) {
    return Math.max(0, Math.min(requested, Math.min(width, height) / 2));
  }

  static float x(float left, float width, float radius, int point, float distance) {
    int corner = point % 52 / 13;
    float center = left + (corner == 0 || corner == 3 ? width - radius : radius);
    return center + COS[point] * (radius + distance) * ARC_GUARD;
  }

  static float y(float top, float height, float radius, int point, float distance) {
    int corner = point % 52 / 13;
    float center = top + (corner < 2 ? height - radius : radius);
    return center + SIN[point] * (radius + distance) * ARC_GUARD;
  }
}
