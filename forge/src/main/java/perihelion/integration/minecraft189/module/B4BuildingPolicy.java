package perihelion.integration.minecraft189.module;

/** Small, source-derived OpenB4 building calculations; no client state or global ownership. */
public final class B4BuildingPolicy {
  public enum Mining {
    NATIVE,
    PRE,
    POST,
    INCREMENT
  }

  private B4BuildingPolicy() {}

  public static float mining(
      Mining mode, float damage, float previous, float hardness, double speed) {
    if (speed <= 1 || damage >= 1) return damage;
    switch (mode) {
      case PRE:
        double seed = 1 - 1 / speed;
        return damage > 0 && damage < seed ? (float) seed : damage;
      case POST:
        return damage >= 1 / speed ? 1 : damage;
      case INCREMENT:
        return damage > 0 && damage > previous
            ? (float) (previous + hardness * (speed - .2152857 * (speed - 1)))
            : damage;
      default:
        return damage;
    }
  }

  /** OpenB4 samples the previous motion plus the next input acceleration. */
  public static double[] projectedMotion(
      double x, double z, float forward, float strafe, float speed, float yaw) {
    forward *= .98F;
    strafe *= .98F;
    float squared = forward * forward + strafe * strafe;
    if (squared >= 1E-4F) {
      float length = (float) Math.sqrt(squared);
      speed *= .9999998F;
      if (length >= 1) speed /= length;
      float angle = yaw * ((float) Math.PI / 180);
      float sin = sine((int) (angle * 10430.378F) & 65535),
          cos = sine((int) (angle * 10430.378F + 16384F) & 65535);
      x += strafe * speed * cos - forward * speed * sin;
      z += forward * speed * cos + strafe * speed * sin;
    }
    return new double[] {x, z};
  }

  // Minecraft 1.8.9 MathHelper's 65536-entry sine lookup, without loading Minecraft in unit tests.
  private static float sine(int index) {
    return (float) Math.sin(index * Math.PI * 2 / 65536);
  }

  /** Descending hotbar iteration means equal-size candidates retain the higher index. */
  public static int blockStack(int current, int heldCount, int[] counts, boolean[] eligible) {
    int best = -1, slot = -1;
    for (int i = 8; i >= 0; i--) {
      if (i == current || !eligible[i] || counts[i] <= best || counts[i] <= heldCount) continue;
      best = counts[i];
      slot = i;
    }
    return slot;
  }
}
