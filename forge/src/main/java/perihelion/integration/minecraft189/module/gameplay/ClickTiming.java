package perihelion.integration.minecraft189.module.gameplay;

/** Keeps sub-tick phase without building a backlog during pauses or stalls. */
final class ClickTiming {
  private ClickTiming() {}

  static double next(double previous, long now, double interval) {
    if (!Double.isFinite(interval) || interval <= 0) throw new IllegalArgumentException("interval");
    // A deadline missed by a full interval is a stall, not a debt to replay.
    return (previous > 0 && now >= previous && now - previous < interval ? previous : now)
        + interval;
  }
}
