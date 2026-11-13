package perihelion.core.setting;

/** An immutable, finite, inclusive pair of ordered numeric endpoints. */
public final class DoubleRange {
    private final double lower, upper;

    public DoubleRange(double lower, double upper) {
        if (!Double.isFinite(lower) || !Double.isFinite(upper) || lower > upper) {
            throw new IllegalArgumentException("Range endpoints must be finite and ordered");
        }
        this.lower = lower == 0 ? 0 : lower;
        this.upper = upper == 0 ? 0 : upper;
    }
    public double lower() { return lower; }
    public double upper() { return upper; }
    @Override public boolean equals(Object other) {
        if (!(other instanceof DoubleRange)) return false;
        DoubleRange range = (DoubleRange) other;
        return Double.compare(lower, range.lower) == 0 && Double.compare(upper, range.upper) == 0;
    }
    @Override public int hashCode() { return 31 * Double.hashCode(lower) + Double.hashCode(upper); }
    @Override public String toString() { return lower + ".." + upper; }
}
