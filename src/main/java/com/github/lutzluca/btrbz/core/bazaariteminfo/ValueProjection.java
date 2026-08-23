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
        double padding = Math.max(MINIMUM_PADDING,
            Math.max(finiteScale(span, PADDING_FRACTION), finiteScale(scale, 1e-6)));
        if (span <= padding) {
            padding = Math.max(padding, Math.max(1.0, finiteScale(scale, 0.02)));
        }
        double paddedMinimum = finiteAdd(minimum, -padding);
        double paddedMaximum = finiteAdd(maximum, padding);
        if (paddedMinimum >= paddedMaximum) {
            paddedMinimum = minimum < maximum ? minimum : finitePredecessor(minimum);
            paddedMaximum = minimum < maximum ? maximum : finiteSuccessor(maximum);
        }
        if (paddedMinimum >= paddedMaximum) {
            return Optional.empty();
        }
        return Optional.of(new ValueProjection(paddedMinimum, paddedMaximum, top, height));
    }

    /** Builds a magnitude axis whose baseline remains anchored at zero. */
    public static Optional<ValueProjection> fromZero(Collection<Double> values, int top, int height) {
        Objects.requireNonNull(values, "values");
        double maximum = Double.NEGATIVE_INFINITY;
        for (Double value : values) {
            if (value != null && Double.isFinite(value) && value >= 0) {
                maximum = Math.max(maximum, value);
            }
        }
        if (!Double.isFinite(maximum)) {
            return Optional.empty();
        }
        double paddedMaximum = maximum <= 0
            ? 1
            : finiteAdd(maximum, Math.max(MINIMUM_PADDING, finiteScale(maximum, PADDING_FRACTION)));
        return Optional.of(new ValueProjection(0, paddedMaximum, top, height));
    }

    public int y(double value) {
        double denominator = this.maximum - this.minimum;
        double fraction = (this.maximum - value) / denominator;
        if (!Double.isFinite(denominator)) {
            fraction = (this.maximum / 2 - value / 2) / (this.maximum / 2 - this.minimum / 2);
        }
        fraction = Math.max(0, Math.min(1, fraction));
        return this.top + (int) Math.round(fraction * Math.max(0, this.height - 1));
    }

    private static double finiteScale(double value, double factor) {
        double result = value * factor;
        return Double.isFinite(result) ? Math.abs(result) : Double.MAX_VALUE;
    }

    private static double finiteAdd(double value, double addend) {
        double result = value + addend;
        if (Double.isFinite(result)) {
            return result;
        }
        return addend < 0 ? -Double.MAX_VALUE : Double.MAX_VALUE;
    }

    private static double finitePredecessor(double value) {
        double predecessor = Math.nextDown(value);
        return Double.isFinite(predecessor) ? predecessor : value;
    }

    private static double finiteSuccessor(double value) {
        double successor = Math.nextUp(value);
        return Double.isFinite(successor) ? successor : value;
    }
}
