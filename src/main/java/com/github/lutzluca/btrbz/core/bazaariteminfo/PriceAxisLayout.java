package com.github.lutzluca.btrbz.core.bazaariteminfo;

import com.github.lutzluca.btrbz.utils.Utils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.ToIntFunction;

/** Unique price-axis labels and the measured horizontal space reserved for them. */
record PriceAxisLayout(List<String> labels, int inset) {
    private static final int LABEL_PADDING = 14;
    private static final int MINIMUM_PLOT_WIDTH = 80;
    private static final int MAXIMUM_PRECISION = 16;

    PriceAxisLayout {
        labels = List.copyOf(Objects.requireNonNull(labels, "labels"));
        if (labels.size() != 3) {
            throw new IllegalArgumentException("price axis requires maximum, midpoint, and minimum labels");
        }
        inset = Math.max(1, inset);
    }

    static PriceAxisLayout create(
        ValueProjection values,
        int componentWidth,
        ToIntFunction<String> textWidth
    ) {
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(textWidth, "textWidth");
        double midpoint = values.minimum() + (values.maximum() - values.minimum()) / 2;
        if (!Double.isFinite(midpoint)) {
            midpoint = values.minimum() / 2 + values.maximum() / 2;
        }
        List<Double> ticks = List.of(values.maximum(), midpoint, values.minimum());
        double largestAbsolute = ticks.stream().mapToDouble(Math::abs).max().orElse(0);
        int minimumInset = Math.min(
            BazaarHistoryPanelController.DEFAULT_AXIS_INSET,
            Math.max(1, componentWidth - BazaarHistoryPanelController.RIGHT_INSET - 1));
        int maximumInset = Math.max(
            minimumInset,
            componentWidth - BazaarHistoryPanelController.RIGHT_INSET - MINIMUM_PLOT_WIDTH);
        List<List<String>> candidates = candidates(ticks, largestAbsolute);
        List<String> shortestUnique = null;
        int shortestWidth = Integer.MAX_VALUE;
        for (List<String> candidate : candidates) {
            if (!unique(candidate)) {
                continue;
            }
            int width = candidate.stream().mapToInt(textWidth).max().orElse(0);
            if (width < shortestWidth) {
                shortestUnique = candidate;
                shortestWidth = width;
            }
            int requiredInset = Math.max(minimumInset, width + LABEL_PADDING);
            if (requiredInset <= maximumInset) {
                return new PriceAxisLayout(candidate, requiredInset);
            }
        }
        if (shortestUnique == null) {
            shortestUnique = ticks.stream().map(value -> Double.toString(value)).toList();
            shortestWidth = shortestUnique.stream().mapToInt(textWidth).max().orElse(0);
        }
        return new PriceAxisLayout(shortestUnique,
            Math.min(maximumInset, Math.max(minimumInset, shortestWidth + LABEL_PADDING)));
    }

    private static List<List<String>> candidates(List<Double> ticks, double largestAbsolute) {
        var candidates = new ArrayList<List<String>>();
        if (largestAbsolute >= 1e15) {
            addScientific(candidates, ticks);
            return candidates;
        }
        if (largestAbsolute >= 100_000) {
            int firstPrecision = largestAbsolute >= 1_000_000 ? 2 : 1;
            for (int precision = firstPrecision; precision <= MAXIMUM_PRECISION; precision++) {
                final int places = precision;
                candidates.add(ticks.stream().map(value -> Utils.formatCompact(value, places)).toList());
            }
        }
        double interval = Math.abs(ticks.get(0) - ticks.get(1));
        int firstDecimalPlaces = decimalPlaces(interval);
        for (int precision = firstDecimalPlaces; precision <= MAXIMUM_PRECISION; precision++) {
            final int places = precision;
            candidates.add(ticks.stream().map(value -> Utils.formatDecimal(value, places, true)).toList());
        }
        addScientific(candidates, ticks);
        return candidates;
    }

    private static void addScientific(List<List<String>> candidates, List<Double> ticks) {
        for (int precision = 1; precision <= MAXIMUM_PRECISION; precision++) {
            final int places = precision;
            candidates.add(ticks.stream().map(value -> scientific(value, places)).toList());
        }
    }

    private static int decimalPlaces(double interval) {
        if (!Double.isFinite(interval) || interval <= 0) {
            return 1;
        }
        return Math.max(0, Math.min(MAXIMUM_PRECISION,
            (int) Math.ceil(-Math.log10(interval))));
    }

    private static String scientific(double value, int precision) {
        return String.format(Locale.ROOT, "% ." + precision + "e", value)
            .trim()
            .replace("e+0", "e")
            .replace("e-0", "e-")
            .replace("e+", "e");
    }

    private static boolean unique(List<String> labels) {
        return new HashSet<>(labels).size() == labels.size();
    }
}
