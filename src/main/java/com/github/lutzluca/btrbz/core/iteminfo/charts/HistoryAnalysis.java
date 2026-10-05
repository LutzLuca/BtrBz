package com.github.lutzluca.btrbz.core.iteminfo.charts;

import com.github.lutzluca.coflnet.HistoryPoint;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class HistoryAnalysis {
    private HistoryAnalysis() {}

    /** Each returned sample has equal weight. Missing prices are excluded, never interpolated. */
    public static Optional<Stats> stats(List<HistoryPoint> points, Instant start, Instant end, boolean buy) {
        int count = 0;
        double sum = 0;
        double low = Double.POSITIVE_INFINITY;
        double high = Double.NEGATIVE_INFINITY;
        HistoryPoint first = null;
        HistoryPoint last = null;
        for (var point : points) {
            Double value = buy ? point.buy() : point.sell();
            if (point.timestamp().isBefore(start) || point.timestamp().isAfter(end)) {
                continue;
            }
            Double observedLow = buy ? point.minBuy() : point.minSell();
            Double observedHigh = buy ? point.maxBuy() : point.maxSell();
            if (observedLow == null || !Double.isFinite(observedLow)) {
                observedLow = value;
            }
            if (observedHigh == null || !Double.isFinite(observedHigh)) {
                observedHigh = value;
            }
            if (observedLow != null && Double.isFinite(observedLow)) {
                low = Math.min(low, observedLow);
            }
            if (observedHigh != null && Double.isFinite(observedHigh)) {
                high = Math.max(high, observedHigh);
            }
            if (value == null || !Double.isFinite(value)) {
                continue;
            }
            count++;
            sum += value;
            if (first == null || point.timestamp().isBefore(first.timestamp())) {
                first = point;
            }
            if (last == null || point.timestamp().isAfter(last.timestamp())) {
                last = point;
            }
        }
        if (count == 0) {
            return Optional.empty();
        }
        double initial = buy ? first.buy() : first.sell();
        double latest = buy ? last.buy() : last.sell();
        return Optional.of(new Stats(count, sum / count, low, high, latest - initial,
            initial == 0 ? null : (latest - initial) / initial * 100));
    }

    public static String exact(Double value) {
        return value == null || !Double.isFinite(value) ? "Unavailable" : String.format(Locale.ROOT, "%,.1f", value);
    }

    public static String comparison(Double value, Double pinned) {
        if (value == null || pinned == null || !Double.isFinite(value) || !Double.isFinite(pinned)) {
            return "Comparison unavailable";
        }
        double change = value - pinned;
        String coins = String.format(Locale.ROOT, "%+,.1f coins", change);
        return pinned == 0
            ? coins + " (percentage unavailable)"
            : coins + String.format(Locale.ROOT, " (%+.2f%%)", change / pinned * 100);
    }

    public record Stats(int samples, double average, double low, double high, double change, Double percent) {}
}
