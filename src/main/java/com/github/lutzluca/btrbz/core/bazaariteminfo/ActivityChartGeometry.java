package com.github.lutzluca.btrbz.core.bazaariteminfo;

import com.github.lutzluca.coflnet.BazaarHistoryPoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.ToLongFunction;

/** Pure interval-item activity geometry with one shared Y scale. */
public final class ActivityChartGeometry {
    private ActivityChartGeometry() {}

    public static Geometry layout(
        List<BazaarHistoryPoint> points,
        boolean showBuy,
        boolean showSell,
        TimeProjection time,
        int top,
        int height,
        OptionalInt selectedIndex
    ) {
        Objects.requireNonNull(points, "points");
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(selectedIndex, "selectedIndex");
        var bounds = new ArrayList<Double>();
        for (var point : points) {
            if (point == null) {
                continue;
            }
            if (showBuy && point.buyVolume() >= 0) {
                bounds.add((double) point.buyVolume());
            }
            if (showSell && point.sellVolume() >= 0) {
                bounds.add((double) point.sellVolume());
            }
        }
        var projection = ValueProjection.fromZero(bounds, top, height);
        if (projection.isEmpty()) {
            return new Geometry(time, null, Series.empty(), Series.empty());
        }
        var values = projection.orElseThrow();
        return new Geometry(
            time,
            values,
            showBuy ? series(points, BazaarHistoryPoint::buyVolume, time, values, selectedIndex) : Series.empty(),
            showSell ? series(points, BazaarHistoryPoint::sellVolume, time, values, selectedIndex) : Series.empty());
    }

    private static Series series(
        List<BazaarHistoryPoint> points,
        ToLongFunction<BazaarHistoryPoint> extractor,
        TimeProjection time,
        ValueProjection values,
        OptionalInt selectedIndex
    ) {
        var segments = new ArrayList<PriceChartGeometry.LineSegment>();
        var selected = new ArrayList<PriceChartGeometry.PixelPoint>();
        PriceChartGeometry.PixelPoint previous = null;
        for (int index = 0; index < points.size(); index++) {
            var point = points.get(index);
            if (point == null) {
                previous = null;
                continue;
            }
            long raw = extractor.applyAsLong(point);
            if (raw < 0) {
                previous = null;
                continue;
            }
            var current = new PriceChartGeometry.PixelPoint(time.x(point.timestamp()), values.y(raw));
            if (previous != null) {
                PriceChartGeometry.appendSegment(segments, previous, current);
            }
            if (selectedIndex.isPresent() && selectedIndex.getAsInt() == index) {
                selected.add(current);
            }
            previous = current;
        }
        return new Series(List.copyOf(segments), List.copyOf(selected));
    }

    public record Series(
        List<PriceChartGeometry.LineSegment> segments,
        List<PriceChartGeometry.PixelPoint> selectedMarkers
    ) {
        public Series {
            segments = List.copyOf(segments);
            selectedMarkers = List.copyOf(selectedMarkers);
        }

        public static Series empty() {
            return new Series(List.of(), List.of());
        }
    }

    public record Geometry(TimeProjection time, ValueProjection values, Series buy, Series sell) {
        public Geometry {
            time = Objects.requireNonNull(time, "time");
            buy = Objects.requireNonNull(buy, "buy");
            sell = Objects.requireNonNull(sell, "sell");
        }

        public boolean isEmpty() {
            return this.values == null;
        }
    }
}
