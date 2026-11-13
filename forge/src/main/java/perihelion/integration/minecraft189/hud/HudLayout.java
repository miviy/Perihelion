package perihelion.integration.minecraft189.hud;

/** Resolution-independent positions in the available area, with a small safe margin. */
public final class HudLayout {
  private HudLayout() {}

  public static int position(double fraction, int screen, int size) {
    int margin = screen >= size + 4 ? 2 : 0;
    return margin + (int) Math.round(clamp(fraction) * Math.max(0, screen - size - margin * 2));
  }

  public static double fraction(int position, int screen, int size) {
    int margin = screen >= size + 4 ? 2 : 0;
    int room = screen - size - margin * 2;
    return room <= 0 ? 0 : clamp((position - margin) / (double) room);
  }

  public static double clamp(double value) {
    return Double.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
  }

  public static String duration(long seconds) {
    seconds = Math.max(0, seconds);
    return seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
  }

  /** A fixed-size physical click history. Synthetic attacks do not enter it. */
  public static final class Clicks {
    private final long[] times = new long[128];
    private int next, size;

    public void add(long nanos) {
      times[next] = nanos;
      next = (next + 1) % times.length;
      size = Math.min(size + 1, times.length);
    }

    public int count(long nanos) {
      int count = 0;
      for (int i = 0; i < size; i++) {
        long age = nanos - times[(next - i - 1 + times.length) % times.length];
        if (age >= 0 && age < 1_000_000_000L) count++;
      }
      return count;
    }
  }
}
