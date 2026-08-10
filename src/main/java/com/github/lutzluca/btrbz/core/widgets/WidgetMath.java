package com.github.lutzluca.btrbz.core.widgets;

/** Shared numeric policies used across widget layout and interaction code. */
public final class WidgetMath {
    private WidgetMath() {}

    public static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public static long clamp(long value, long minimum, long maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public static double unit(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return clamp(value, 0.0, 1.0);
    }

    public static int portion(int availableLength, double fraction) {
        return (int) Math.round(Math.max(0, availableLength) * unit(fraction));
    }

    public static double snap(double value, double minimum, double maximum, double step) {
        if (!Double.isFinite(step) || step <= 0.0) {
            throw new IllegalArgumentException("step must be positive");
        }
        double snapped = minimum + Math.round((value - minimum) / step) * step;
        return clamp(snapped, minimum, maximum);
    }
}
