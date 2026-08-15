package com.github.lutzluca.btrbz.core.bazaariteminfo;

import com.github.lutzluca.coflnet.BazaarHistoryPoint;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/** Pure chart layout used by {@link BazaarHistoryChartComponent}. */
public final class BazaarHistoryChartGeometry {
    private BazaarHistoryChartGeometry() {}

    public static Geometry layout(
        List<BazaarHistoryPoint> history,
        Visibility visibility,
        int left,
        int top,
        int width,
        int height
    ) {
        Objects.requireNonNull(history, "history");
        Objects.requireNonNull(visibility, "visibility");

        var points = history.stream()
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(BazaarHistoryPoint::timestamp))
            .toList();

        if (points.isEmpty() || width <= 0 || height <= 0 || !visibility.anyVisible()) {
            return Geometry.empty();
        }

        long minTime = points.getFirst().timestamp().toEpochMilli();
        long maxTime = points.getLast().timestamp().toEpochMilli();
        var values = visibleValues(points, visibility);
        if (values.isEmpty()) {
            return Geometry.empty();
        }

        double minValue = values.stream().mapToDouble(Double::doubleValue).min().orElseThrow();
        double maxValue = values.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
        Bounds bounds = paddedBounds(minTime, maxTime, minValue, maxValue);
        Projection projection = new Projection(left, top, width, height, bounds);

        Series buy = visibility.buy()
            ? series(points, BazaarHistoryPoint::buy, projection)
            : Series.empty();
        Series sell = visibility.sell()
            ? series(points, BazaarHistoryPoint::sell, projection)
            : Series.empty();

        List<BandSegment> buyBand = visibility.bands()
            ? band(points, BazaarHistoryPoint::minBuy, BazaarHistoryPoint::maxBuy, projection)
            : List.of();
        List<BandSegment> sellBand = visibility.bands()
            ? band(points, BazaarHistoryPoint::minSell, BazaarHistoryPoint::maxSell, projection)
            : List.of();

        return new Geometry(bounds, buy, sell, buyBand, sellBand);
    }

    private static List<Double> visibleValues(
        List<BazaarHistoryPoint> points,
        Visibility visibility
    ) {
        var values = new ArrayList<Double>();
        for (var point : points) {
            if (visibility.buy()) {
                addFinite(values, point.buy());
            }
            if (visibility.sell()) {
                addFinite(values, point.sell());
            }
            if (visibility.bands()) {
                addFinite(values, point.minBuy());
                addFinite(values, point.maxBuy());
                addFinite(values, point.minSell());
                addFinite(values, point.maxSell());
            }
        }
        return values;
    }

    private static void addFinite(List<Double> target, Double value) {
        if (value != null && Double.isFinite(value)) {
            target.add(value);
        }
    }

    private static Bounds paddedBounds(long minTime, long maxTime, double minValue, double maxValue) {
        double valuePadding = 0;
        if (Double.compare(minValue, maxValue) == 0) {
            valuePadding = Math.max(1, Math.abs(minValue) * .02);
        }

        return new Bounds(minTime, maxTime, minValue - valuePadding, maxValue + valuePadding);
    }

    private static Series series(
        List<BazaarHistoryPoint> points,
        ToDoubleFunction<BazaarHistoryPoint> value,
        Projection projection
    ) {
        var pixelPoints = new ArrayList<PixelPoint>();
        var segments = new ArrayList<LineSegment>();
        PixelPoint previous = null;

        for (var point : points) {
            double currentValue = value.applyAsDouble(point);
            if (!Double.isFinite(currentValue)) {
                previous = null;
                continue;
            }

            var current = projection.point(point.timestamp(), currentValue);
            pixelPoints.add(current);
            if (previous != null) {
                segments.add(new LineSegment(previous, current));
            }
            previous = current;
        }

        return new Series(pixelPoints, segments);
    }

    private static List<BandSegment> band(
        List<BazaarHistoryPoint> points,
        Function<BazaarHistoryPoint, Double> minimum,
        Function<BazaarHistoryPoint, Double> maximum,
        Projection projection
    ) {
        var segments = new ArrayList<BandSegment>();
        BandPoint previous = null;

        for (var point : points) {
            Double rawMinimum = minimum.apply(point);
            Double rawMaximum = maximum.apply(point);
            if (rawMinimum == null || rawMaximum == null
                || !Double.isFinite(rawMinimum)
                || !Double.isFinite(rawMaximum)) {
                previous = null;
                continue;
            }

            double low = Math.min(rawMinimum, rawMaximum);
            double high = Math.max(rawMinimum, rawMaximum);
            var current = new BandPoint(
                projection.x(point.timestamp()),
                projection.y(high),
                projection.y(low));

            if (previous == null) {
                segments.add(new BandSegment(current, current));
            } else {
                // Replace the isolated placeholder once it becomes part of a real segment.
                if (segments.getLast().start().equals(previous)
                    && segments.getLast().end().equals(previous)) {
                    segments.removeLast();
                }
                segments.add(new BandSegment(previous, current));
            }
            previous = current;
        }

        return List.copyOf(segments);
    }

    public record Visibility(boolean buy, boolean sell, boolean bands) {
        public boolean anyVisible() {
            return this.buy || this.sell || this.bands;
        }
    }

    public record Bounds(long minTimestamp, long maxTimestamp, double minValue, double maxValue) {
        public Bounds {
            if (!Double.isFinite(minValue) || !Double.isFinite(maxValue) || minValue >= maxValue) {
                throw new IllegalArgumentException("value bounds must be finite and increasing");
            }
        }
    }

    public record PixelPoint(int x, int y) {}

    public record LineSegment(PixelPoint start, PixelPoint end) {}

    public record BandPoint(int x, int top, int bottom) {}

    public record BandSegment(BandPoint start, BandPoint end) {}

    public record Series(List<PixelPoint> points, List<LineSegment> segments) {
        public Series {
            points = List.copyOf(points);
            segments = List.copyOf(segments);
        }

        public static Series empty() {
            return new Series(List.of(), List.of());
        }
    }

    public record Geometry(
        Bounds bounds,
        Series buy,
        Series sell,
        List<BandSegment> buyBand,
        List<BandSegment> sellBand
    ) {
        public Geometry {
            buy = Objects.requireNonNull(buy, "buy");
            sell = Objects.requireNonNull(sell, "sell");
            buyBand = List.copyOf(buyBand);
            sellBand = List.copyOf(sellBand);
        }

        public boolean isEmpty() {
            return this.bounds == null;
        }

        public static Geometry empty() {
            return new Geometry(null, Series.empty(), Series.empty(), List.of(), List.of());
        }
    }

    private record Projection(int left, int top, int width, int height, Bounds bounds) {
        int x(Instant timestamp) {
            long span = this.bounds.maxTimestamp() - this.bounds.minTimestamp();
            if (span <= 0) {
                return this.left + this.width / 2;
            }
            double fraction = (double) (timestamp.toEpochMilli() - this.bounds.minTimestamp()) / span;
            return this.left + (int) Math.round(clamp(fraction) * Math.max(0, this.width - 1));
        }

        int y(double value) {
            double fraction = (this.bounds.maxValue() - value)
                / (this.bounds.maxValue() - this.bounds.minValue());
            return this.top + (int) Math.round(clamp(fraction) * Math.max(0, this.height - 1));
        }

        PixelPoint point(Instant timestamp, double value) {
            return new PixelPoint(this.x(timestamp), this.y(value));
        }

        private static double clamp(double value) {
            return Math.max(0, Math.min(1, value));
        }
    }
}
