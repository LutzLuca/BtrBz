package com.github.lutzluca.btrbz.core.widgets;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

final class BazaarNumberFormat {
    private static final DecimalFormat BILLIONS = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.US));
    private static final DecimalFormat COMPACT = new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(Locale.US));

    private BazaarNumberFormat() {}

    static String compact(double value) {
        double absolute = Math.abs(value);
        if (absolute >= 1_000_000_000) return BILLIONS.format(value / 1_000_000_000d) + "B";
        if (absolute >= 1_000_000) return COMPACT.format(value / 1_000_000d) + "M";
        if (absolute >= 1_000) return COMPACT.format(value / 1_000d) + "k";
        return COMPACT.format(value);
    }
}
