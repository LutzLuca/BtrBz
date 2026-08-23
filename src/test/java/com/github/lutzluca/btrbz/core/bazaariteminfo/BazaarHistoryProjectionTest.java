package com.github.lutzluca.btrbz.core.bazaariteminfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.lutzluca.coflnet.BazaarHistoryPoint;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class BazaarHistoryProjectionTest {
    @Test
    void projectsByElapsedTimeAndHandlesEqualTimestamps() {
        var projection = new TimeProjection(0, 1000, 10, 101);
        assertEquals(10, projection.x(Instant.EPOCH));
        assertEquals(60, projection.x(Instant.ofEpochMilli(500)));
        assertEquals(110, projection.x(Instant.ofEpochMilli(1000)));

        var equal = new TimeProjection(10, 10, 4, 80);
        assertEquals(44, equal.x(Instant.ofEpochMilli(10)));
    }

    @Test
    void priceBoundsFollowVisibilityAndLinesBreakAcrossInvalidValues() {
        var points = List.of(
            point(1, 100, 0, 10),
            point(Double.NaN, 110, 1, 20),
            point(3, 120, 2, 30));
        var time = TimeProjection.from(points.stream().map(BazaarHistoryPoint::timestamp).toList(), 0, 101);
        var buy = PriceChartGeometry.layout(
            points, new PriceChartGeometry.Visibility(true, false, false), time, 0, 50, OptionalInt.empty());
        var sell = PriceChartGeometry.layout(
            points, new PriceChartGeometry.Visibility(false, true, false), time, 0, 50, OptionalInt.empty());

        assertTrue(buy.values().maximum() < sell.values().minimum());
        assertTrue(buy.buy().segments().isEmpty());
        assertEquals(2, sell.sell().segments().size());
    }

    @Test
    void adaptiveMarkersSuppressDenseAndCoincidentSamplesButKeepSelection() {
        var points = List.of(
            point(1, 2, 0, 1), point(2, 3, 0, 2), point(3, 4, 0, 3));
        var time = TimeProjection.from(points.stream().map(BazaarHistoryPoint::timestamp).toList(), 0, 5);
        var geometry = PriceChartGeometry.layout(
            points, new PriceChartGeometry.Visibility(true, false, false), time, 0, 30, OptionalInt.of(1));

        assertTrue(geometry.buy().ordinaryMarkers().isEmpty());
        assertEquals(1, geometry.buy().selectedMarkers().size());
    }

    @Test
    void activityUsesSeparateBoundsWithoutForcingZero() {
        var points = List.of(point(10, 20, 1000, 100), point(11, 21, 1100, 200));
        var time = TimeProjection.from(points.stream().map(BazaarHistoryPoint::timestamp).toList(), 0, 100);
        var activity = ActivityChartGeometry.layout(points, true, false, time, 0, 50, OptionalInt.empty());

        assertTrue(activity.values().minimum() > 0);
        assertTrue(activity.values().minimum() > 900);
        assertEquals(1, activity.buy().segments().size());
        assertTrue(activity.sell().segments().isEmpty());
    }

    @Test
    void collinearPixelSegmentsCollapseWithoutChangingTheirEndpoints() {
        var segments = new java.util.ArrayList<PriceChartGeometry.LineSegment>();
        PriceChartGeometry.appendSegment(segments,
            new PriceChartGeometry.PixelPoint(0, 10), new PriceChartGeometry.PixelPoint(10, 10));
        PriceChartGeometry.appendSegment(segments,
            new PriceChartGeometry.PixelPoint(10, 10), new PriceChartGeometry.PixelPoint(20, 10));
        PriceChartGeometry.appendSegment(segments,
            new PriceChartGeometry.PixelPoint(20, 10), new PriceChartGeometry.PixelPoint(21, 9));

        assertEquals(2, segments.size());
        assertEquals(new PriceChartGeometry.PixelPoint(0, 10), segments.getFirst().start());
        assertEquals(new PriceChartGeometry.PixelPoint(20, 10), segments.getFirst().end());
    }

    @Test
    void selectionAndTicksUseTheSameProjection() {
        var points = List.of(point(1, 2, 0, 0), point(2, 3, 0, 3600));
        var time = TimeProjection.from(points.stream().map(BazaarHistoryPoint::timestamp).toList(), 10, 211);
        var selected = HistorySelection.nearest(points, time, 205).orElseThrow();
        var ticks = TimeAxisTicks.generate(time, BazaarItemInfoRange.Hour, ZoneId.of("UTC"));

        assertEquals(1, selected.index());
        assertTrue(ticks.size() >= 3 && ticks.size() <= 8);
        assertEquals("1 Jan 1970, 01:00", TimeAxisTicks.tooltip(selected.point().timestamp(), ZoneId.of("UTC")));
    }

    @Test
    void dayTicksMarkTheNewDateAfterMidnight() {
        var start = Instant.parse("2026-08-20T23:00:00Z");
        var end = Instant.parse("2026-08-21T01:00:00Z");
        var ticks = TimeAxisTicks.generate(
            new TimeProjection(start.toEpochMilli(), end.toEpochMilli(), 0, 280),
            BazaarItemInfoRange.Day,
            ZoneId.of("UTC"));

        assertFalse(ticks.isEmpty());
        assertTrue(ticks.stream().skip(1).anyMatch(tick -> tick.label().contains("21 Aug")));
    }

    @Test
    void controllerCachesProjectionTicksAndStationarySelectionUntilDataChanges() {
        var points = List.of(point(1, 2, 10, 0), point(2, 3, 20, 60));
        var controller = new BazaarHistoryPanelController();
        controller.update(
            points, BazaarItemInfoRange.Day, true, true, true,
            BazaarItemInfoConfig.ActivityMode.IntervalItems);

        long revision = controller.revision();
        var projection = controller.projection(10, 400);
        var ticks = controller.ticks(10, 400, ZoneId.of("UTC"));
        controller.select(200, 10, 400);
        var selection = controller.selection();

        assertSame(projection, controller.projection(10, 400));
        assertSame(ticks, controller.ticks(10, 400, ZoneId.of("UTC")));
        controller.select(200, 10, 400);
        assertSame(selection, controller.selection());
        controller.update(
            points, BazaarItemInfoRange.Day, true, true, true,
            BazaarItemInfoConfig.ActivityMode.IntervalItems);
        assertEquals(revision, controller.revision());

        controller.update(
            points, BazaarItemInfoRange.Day, true, true, false,
            BazaarItemInfoConfig.ActivityMode.IntervalItems);
        assertTrue(controller.revision() > revision);
        assertNotSame(projection, controller.projection(10, 400));
    }

    @Test
    void tooltipSeparatesPriceRangesAndActivityIntoReadableSections() {
        var point = new BazaarHistoryPoint(
            100, 110, 90.0, 105.0, 100.0, 120.0,
            50, 60, 0, 0, Instant.EPOCH);

        var tooltip = BazaarHistoryTooltip.create(
            point, true, true, true,
            BazaarItemInfoConfig.ActivityMode.IntervalItems,
            ZoneId.of("UTC"));
        var text = tooltip.lines().stream().map(BazaarHistoryTooltip.Line::text).toList();

        assertEquals(List.of(
            "1 Jan 1970, 00:00",
            "Prices",
            "Buy price  100.0",
            "Range  90.0 to 105.0",
            "Sell price  110.0",
            "Range  100.0 to 120.0",
            "Interval activity",
            "Buy items  50",
            "Sell items  60"), text);
        assertEquals(BazaarHistoryTooltip.SECTION_GAP, tooltip.lines().get(1).gapBefore());
        assertEquals(BazaarHistoryTooltip.SECTION_GAP, tooltip.lines().get(6).gapBefore());
    }

    private static BazaarHistoryPoint point(
        double buy,
        double sell,
        long buyVolume,
        long epochSecond
    ) {
        return new BazaarHistoryPoint(
            buy, sell, null, null, null, null, buyVolume, buyVolume + 10,
            0, 0, Instant.ofEpochSecond(epochSecond));
    }
}
