package perihelion.integration.minecraft189.module.gameplay;

import java.util.ArrayDeque;

/** Activation-local swing history, sampled on every eligible predictive-block update. */
final class SwingPrediction {
  private final ArrayDeque<Integer> intervals = new ArrayDeque<>();
  private long tick, lastSwing = -1;
  private float previous;

  void reset() {
    intervals.clear();
    tick = 0;
    lastSwing = -1;
    previous = 0;
  }

  boolean update(float progress, int history, int lead, int hold, double scale) {
    tick++;
    if (progress < previous - .25f) {
      long interval = tick - lastSwing;
      if (lastSwing >= 0 && interval > 0 && interval < 40) intervals.addLast((int) interval);
      lastSwing = tick;
    }
    previous = progress;
    while (intervals.size() > history) intervals.removeFirst();
    if (intervals.isEmpty()) return true;
    double mean = 0;
    for (int interval : intervals) mean += interval;
    mean /= intervals.size();
    double variance = 0;
    for (int interval : intervals) variance += (interval - mean) * (interval - mean);
    int pad = (int) Math.round(Math.sqrt(variance / intervals.size()) * scale);
    long step = Math.max(1, Math.round(mean)), projected = lastSwing + step;
    long overdue = tick - projected - hold - pad;
    if (overdue > 0) projected += ((overdue + step - 1) / step) * step;
    return tick >= projected - lead - pad && tick <= projected + hold + pad;
  }

  int samples() {
    return intervals.size();
  }
}
