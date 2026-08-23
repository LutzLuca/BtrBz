package com.github.lutzluca.btrbz.core.bazaariteminfo;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/** Maps one plot's finite values to its own Y axis. */
public record ValueProjection(double minimum, double maximum, int top, int height) {
    private static final double PADDING_FRACTION = 0.05;
    private static final double MINIMUM_PADDING = 1e-6;

    public ValueProjection {
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || minimum >= maximum) {
            throw new IllegalArgumentException("value bounds must be finite and increasing");
        }
        height = Math.max(1, height);
    }

    public static Optional<ValueProjection> from(Collection<Double> values, int top, int height) {
        Objects.requireNonNull(values, "values");
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        for (Double value : values) {
            if (value != null && Double.isFinite(value)) {
                minimum = Math.min(minimum, value);
                maximum = Math.max(maximum, value);
            }
        }
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum)) {
            return Optional.empty();
        }
        double span = maximum - minimum;
        double scale = Math.max(Math.abs(minimum), Math.abs(maximum));
        double padding = Math.max(MINIMUM_PADDING, Math.max(span * PADDING_FRACTION, scale * 1e-6));
        if (span <= padding) {
            padding = Math.max(padding, Math.max(1.0, scale * 0.02));
        }
        return Optional.of(new ValueProjection(minimum - padding, maximum + padding, top, height));
    }

    public int y(double value) {
        double fraction = (this.maximum - value) / (this.maximum - this.minimum);
        fraction = Math.max(0, Math.min(1, fraction));
        return this.top + (int) Math.round(fraction * Math.max(0, this.height - 1));
    }
}
