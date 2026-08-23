package com.github.lutzluca.btrbz.core.bazaariteminfo;

import com.github.lutzluca.coflnet.BazaarHistoryPoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/** Pure price-line, band, and adaptive-marker geometry. */
public final class PriceChartGeometry {
    public static final int ORDINARY_MARKER_MINIMUM_SPACING = 4;

    private PriceChartGeometry() {}

    public static Geometry layout(
        List<BazaarHistoryPoint> points,
        Visibility visibility,
        TimeProjection time,
        int top,
        int height,
        OptionalInt selectedIndex
    ) {
        Objects.requireNonNull(points, "points");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(selectedIndex, "selectedIndex");
        if (!visibility.buy() && !visibility.sell()) {
            return Geometry.empty(time);
        }
        var projection = valueProjection(points, visibility, top, height);
        if (projection.isEmpty()) {
            return Geometry.empty(time);
        }
        var value = projection.orElseThrow();
        return new Geometry(
            time,
            value,
            visibility.buy() ? series(points, BazaarHistoryPoint::buy, time, value, selectedIndex) : Series.empty(),
            visibility.sell() ? series(points, BazaarHistoryPoint::sell, time, value, selectedIndex) : Series.empty(),
            visibility.buy() && visibility.bands()
                ? band(points, BazaarHistoryPoint::minBuy, BazaarHistoryPoint::maxBuy, time, value)
                : List.of(),
            visibility.sell() && visibility.bands()
                ? band(points, BazaarHistoryPoint::minSell, BazaarHistoryPoint::maxSell, time, value)
                : List.of());
    }

    public static Optional<ValueProjection> valueProjection(
        List<BazaarHistoryPoint> points,
        Visibility visibility,
        int top,
        int height
    ) {
        Objects.requireNonNull(points, "points");
        Objects.requireNonNull(visibility, "visibility");
        if (!visibility.buy() && !visibility.sell()) {
            return Optional.empty();
        }
        var values = new ArrayList<Double>();
        for (var point : points) {
            if (point == null) {
                continue;
            }
            if (visibility.buy()) {
                addFinite(values, point.buy());
                if (visibility.bands()) {
                    addFinite(values, point.minBuy());
                    addFinite(values, point.maxBuy());
                }
            }
            if (visibility.sell()) {
                addFinite(values, point.sell());
                if (visibility.bands()) {
                    addFinite(values, point.minSell());
                    addFinite(values, point.maxSell());
                }
            }
        }
        return ValueProjection.from(values, top, height);
    }

    private static Series series(
        List<BazaarHistoryPoint> points,
        ToDoubleFunction<BazaarHistoryPoint> extractor,
        TimeProjection time,
        ValueProjection values,
        OptionalInt selectedIndex
    ) {
        var projected = new ArrayList<IndexedPoint>();
        var segments = new ArrayList<LineSegment>();
        IndexedPoint previous = null;
        for (int index = 0; index < points.size(); index++) {
            var point = points.get(index);
            if (point == null) {
                previous = null;
                continue;
            }
            double raw = extractor.applyAsDouble(point);
            if (!Double.isFinite(raw)) {
                previous = null;
                continue;
            }
            var current = new IndexedPoint(index, time.x(point.timestamp()), values.y(raw));
            projected.add(current);
            if (previous != null) {
                appendSegment(segments, previous.point(), current.point());
            }
            previous = current;
        }
        var ordinary = new ArrayList<PixelPoint>();
        for (int index = 0; index < projected.size(); index++) {
            var current = projected.get(index);
            int distance = Integer.MAX_VALUE;
            if (index > 0) {
                distance = Math.min(distance, Math.abs(current.x() - projected.get(index - 1).x()));
            }
            if (index + 1 < projected.size()) {
                distance = Math.min(distance, Math.abs(projected.get(index + 1).x() - current.x()));
            }
            if (distance >= ORDINARY_MARKER_MINIMUM_SPACING) {
                ordinary.add(current.point());
            }
        }
        var selected = selectedIndex.isPresent()
            ? projected.stream().filter(point -> point.index() == selectedIndex.getAsInt())
                .map(IndexedPoint::point).findFirst().stream().toList()
            : List.<PixelPoint>of();
        return new Series(projected.stream().map(IndexedPoint::point).toList(),
            List.copyOf(segments), List.copyOf(ordinary), selected);
    }

    private static List<BandSegment> band(
        List<BazaarHistoryPoint> points,
        Function<BazaarHistoryPoint, Double> minimum,
        Function<BazaarHistoryPoint, Double> maximum,
        TimeProjection time,
        ValueProjection values
    ) {
        var result = new ArrayList<BandSegment>();
        BandPoint previous = null;
        for (var point : points) {
            if (point == null) {
                previous = null;
                continue;
            }
            Double lowValue = minimum.apply(point);
            Double highValue = maximum.apply(point);
            if (lowValue == null || highValue == null
                || !Double.isFinite(lowValue)
                || !Double.isFinite(highValue)) {
                previous = null;
                continue;
            }
            var current = new BandPoint(
                time.x(point.timestamp()),
                values.y(Math.max(lowValue, highValue)),
                values.y(Math.min(lowValue, highValue)));
            if (previous != null) {
                result.add(new BandSegment(previous, current));
            } else {
                result.add(new BandSegment(current, current));
            }
            previous = current;
        }
        return List.copyOf(result);
    }

    private static void addFinite(List<Double> values, Double value) {
        if (value != null && Double.isFinite(value)) {
            values.add(value);
        }
    }

    static void appendSegment(List<LineSegment> segments, PixelPoint start, PixelPoint end) {
        Objects.requireNonNull(segments, "segments");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!segments.isEmpty()) {
            int lastIndex = segments.size() - 1;
            var previous = segments.get(lastIndex);
            if (previous.end().equals(start)
                && !previous.start().equals(start)
                && !start.equals(end)) {
                long firstX = (long) start.x() - previous.start().x();
                long firstY = (long) start.y() - previous.start().y();
                long secondX = (long) end.x() - start.x();
                long secondY = (long) end.y() - start.y();
                long cross = firstX * secondY - firstY * secondX;
                long direction = firstX * secondX + firstY * secondY;
                if (cross == 0 && direction >= 0) {
                    segments.set(lastIndex, new LineSegment(previous.start(), end));
                    return;
                }
            }
        }
        segments.add(new LineSegment(start, end));
    }

    public record Visibility(boolean buy, boolean sell, boolean bands) {}

    public record PixelPoint(int x, int y) {}

    public record LineSegment(PixelPoint start, PixelPoint end) {}

    public record BandPoint(int x, int top, int bottom) {}

    public record BandSegment(BandPoint start, BandPoint end) {}

    public record Series(
        List<PixelPoint> points,
        List<LineSegment> segments,
        List<PixelPoint> ordinaryMarkers,
        List<PixelPoint> selectedMarkers
    ) {
        public Series {
            points = List.copyOf(points);
            segments = List.copyOf(segments);
            ordinaryMarkers = List.copyOf(ordinaryMarkers);
            selectedMarkers = List.copyOf(selectedMarkers);
        }

        public static Series empty() {
            return new Series(List.of(), List.of(), List.of(), List.of());
        }
    }

    public record Geometry(
        TimeProjection time,
        ValueProjection values,
        Series buy,
        Series sell,
        List<BandSegment> buyBands,
        List<BandSegment> sellBands
    ) {
        public Geometry {
            time = Objects.requireNonNull(time, "time");
            buy = Objects.requireNonNull(buy, "buy");
            sell = Objects.requireNonNull(sell, "sell");
            buyBands = List.copyOf(buyBands);
            sellBands = List.copyOf(sellBands);
        }

        public boolean isEmpty() {
            return this.values == null;
        }

        public static Geometry empty(TimeProjection time) {
            return new Geometry(time, null, Series.empty(), Series.empty(), List.of(), List.of());
        }
    }

    private record IndexedPoint(int index, int x, int y) {
        PixelPoint point() {
            return new PixelPoint(this.x, this.y);
        }
    }
}
