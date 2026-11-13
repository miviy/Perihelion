// SPDX-License-Identifier: AGPL-3.0-only
// Adapted from Yuri AutoClickerModule/TriggerBotModule, revision
// 1cb86ad94ce1f01aa9b715577163c8ecc47b7def.
package perihelion.integration.minecraft189.module.gameplay;

/** Shared donor CPS distribution; fractional settings intentionally use their integer values. */
final class GhostClickTiming {
  private GhostClickTiming() {}

  static int cps(double minimum, double maximum, double gaussian, double jitter) {
    int min = (int) minimum, max = (int) maximum;
    if (min >= max) return max;
    double value = gaussian * (max - min) / 4.0 + (min + max) / 2.0;
    value = Math.max(min, Math.min(max, value));
    return (int) Math.round(value + jitter);
  }

  static boolean due(long now, long lastClick, int cps) {
    return lastClick < 0 || now - lastClick >= 1000L / cps;
  }
}
