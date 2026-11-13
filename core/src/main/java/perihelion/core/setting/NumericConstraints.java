package perihelion.core.setting;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.OptionalDouble;

/** Immutable bounds and an optional step grid anchored at the minimum. */
public final class NumericConstraints {
    private final double minimum, maximum;
    private final BigDecimal origin, step;

    private NumericConstraints(double minimum, double maximum, BigDecimal step) {
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || minimum > maximum) {
            throw new IllegalArgumentException("Numeric bounds must be finite and ordered");
        }
        this.minimum = minimum;
        this.maximum = maximum;
        this.origin = BigDecimal.valueOf(minimum);
        this.step = step;
    }
    static NumericConstraints continuous(double minimum, double maximum) {
        return new NumericConstraints(minimum, maximum, null);
    }
    static NumericConstraints stepped(double minimum, double maximum, double step) {
        if (!Double.isFinite(step) || step <= 0) throw new IllegalArgumentException("Step must be finite and positive");
        return new NumericConstraints(minimum, maximum, BigDecimal.valueOf(step));
    }
    public double minimum() { return minimum; }
    public double maximum() { return maximum; }
    public OptionalDouble step() { return step == null ? OptionalDouble.empty() : OptionalDouble.of(step.doubleValue()); }

    double normalize(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Value must be finite");
        double normalized = value;
        if (step != null) {
            BigDecimal index = BigDecimal.valueOf(value).subtract(origin).divide(step, 0, RoundingMode.HALF_UP);
            normalized = origin.add(index.multiply(step)).doubleValue();
            // Accept floating-point arithmetic noise, never meaningful off-grid values.
            double ulp = Math.max(Math.ulp(value), Math.max(Math.ulp(minimum), Math.ulp(step.doubleValue())));
            double tolerance = Math.min(4 * ulp, step.doubleValue() * 0.000001);
            if (Math.abs(normalized - value) > tolerance) {
                throw new IllegalArgumentException("Expected a step of " + step.toPlainString() + " from " + minimum);
            }
        }
        if (!Double.isFinite(normalized) || normalized < minimum || normalized > maximum) {
            throw new IllegalArgumentException("Expected " + minimum + " through " + maximum);
        }
        return normalized;
    }
}
