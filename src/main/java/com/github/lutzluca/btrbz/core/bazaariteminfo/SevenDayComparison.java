package com.github.lutzluca.btrbz.core.bazaariteminfo;

import com.github.lutzluca.btrbz.data.LiveProductSnapshot;
import com.github.lutzluca.coflnet.BazaarHistoryPoint;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.function.ToDoubleFunction;

/** Pure seven-day arithmetic means and live deltas. */
public final class SevenDayComparison {
    private static final double CADENCE_TOLERANCE = 0.2;

    private SevenDayComparison() {}

    public static Result calculate(List<BazaarHistoryPoint> points, LiveProductSnapshot live) {
        Objects.requireNonNull(points, "points");
        Objects.requireNonNull(live, "live");
        return new Result(
            side(points, BazaarHistoryPoint::buy, optional(live.buyPrice())),
            side(points, BazaarHistoryPoint::sell, optional(live.sellPrice())),
            regularCadence(points) ? "7d avg" : "7d sample avg");
    }

    public static boolean regularCadence(List<BazaarHistoryPoint> points) {
        var timestamps = points.stream()
            .filter(Objects::nonNull)
            .map(BazaarHistoryPoint::timestamp)
            .sorted()
            .toList();
        if (timestamps.size() < 3) {
            return true;
        }
        var gaps = new long[timestamps.size() - 1];
        for (int index = 0; index < gaps.length; index++) {
            gaps[index] = Math.max(0, Duration.between(timestamps.get(index), timestamps.get(index + 1)).toMillis());
        }
        var sorted = gaps.clone();
        Arrays.sort(sorted);
        double median = sorted[sorted.length / 2];
        if (median <= 0) {
            return false;
        }
        return Arrays.stream(gaps).allMatch(gap -> Math.abs(gap - median) / median <= CADENCE_TOLERANCE);
    }

    private static Side side(
        List<BazaarHistoryPoint> points,
        ToDoubleFunction<BazaarHistoryPoint> extractor,
        OptionalDouble current
    ) {
        double sum = 0;
        int count = 0;
        for (var point : points) {
            if (point == null) {
                continue;
            }
            double value = extractor.applyAsDouble(point);
            if (Double.isFinite(value) && value > 0) {
                sum += value;
                count++;
            }
        }
        if (count == 0 || !Double.isFinite(sum)) {
            return new Side(OptionalDouble.empty(), current, OptionalDouble.empty(), OptionalDouble.empty());
        }
        double average = sum / count;
        if (!Double.isFinite(average) || average <= 0) {
            return new Side(OptionalDouble.empty(), current, OptionalDouble.empty(), OptionalDouble.empty());
        }
        if (current.isEmpty() || !Double.isFinite(current.getAsDouble())) {
            return new Side(OptionalDouble.of(average), current, OptionalDouble.empty(), OptionalDouble.empty());
        }
        double delta = current.getAsDouble() - average;
        return new Side(
            OptionalDouble.of(average),
            current,
            OptionalDouble.of(delta),
            OptionalDouble.of(delta / average * 100));
    }

    private static OptionalDouble optional(java.util.Optional<Double> value) {
        return value.isPresent() ? OptionalDouble.of(value.orElseThrow()) : OptionalDouble.empty();
    }

    public record Result(Side buy, Side sell, String label) {
        public Result {
            buy = Objects.requireNonNull(buy, "buy");
            sell = Objects.requireNonNull(sell, "sell");
            label = Objects.requireNonNull(label, "label");
        }
    }

    public record Side(
        OptionalDouble average,
        OptionalDouble current,
        OptionalDouble delta,
        OptionalDouble deltaPercent
    ) {
        public Side {
            average = Objects.requireNonNull(average, "average");
            current = Objects.requireNonNull(current, "current");
            delta = Objects.requireNonNull(delta, "delta");
            deltaPercent = Objects.requireNonNull(deltaPercent, "deltaPercent");
        }
    }
}
