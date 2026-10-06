package com.github.lutzluca.btrbz.core.iteminfo.charts;

import com.github.lutzluca.coflnet.HistoryPoint;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.Duration;
import java.util.List;

class HistoryChartsTest {
    @Test
    void pinSurvivesTimeWindowChangesAndRefreshWithoutSubstitutingAnotherSample() {
        var viewport = new HistoryViewport();
        var pinned = point(40, 120.0);
        viewport.update(List.of(point(0, 100.0), pinned, point(100, 90.0)), time(0), time(100), true);
        viewport.pinAt(time(38));
        viewport.hoverAt(time(95));
        Assertions.assertEquals(time(100), viewport.inspected().orElseThrow().timestamp());
        Assertions.assertEquals(time(40), viewport.pin());
        viewport.clearHover();
        var priceOwner = new Object();
        var quantityOwner = new Object();
        viewport.hoverAt(priceOwner, time(40));
        viewport.hoverAt(quantityOwner, time(40));
        viewport.clearHover(priceOwner);
        Assertions.assertEquals(time(40), viewport.hover());
        viewport.clearHover(quantityOwner);
        Assertions.assertNull(viewport.hover());
        viewport.zoom(2, .5);
        viewport.pan(10);
        Assertions.assertEquals(time(50), viewport.start());
        Assertions.assertEquals(time(100), viewport.end());
        viewport.update(List.of(point(0, 105.0), pinned, point(100, 95.0)), time(0), time(100), false);
        Assertions.assertEquals(pinned, viewport.pinnedPoint().orElseThrow());
        viewport.update(List.of(point(0, 105.0), point(100, 95.0)), time(0), time(100), false);
        Assertions.assertEquals(time(40), viewport.pin());
        Assertions.assertTrue(viewport.pinnedPoint().isEmpty());
        viewport.reset();
        Assertions.assertEquals(time(0), viewport.start());
        Assertions.assertEquals(time(100), viewport.end());
        viewport.update(List.of(point(10, 105.0), point(110, 95.0)), time(10), time(110), false);
        Assertions.assertEquals(time(10), viewport.start());
        Assertions.assertEquals(time(110), viewport.end());
        viewport.zoom(2, .5);
        viewport.update(List.of(point(20, 105.0), point(120, 95.0)), time(20), time(120), false);
        Assertions.assertEquals(time(35), viewport.start());
        Assertions.assertEquals(time(85), viewport.end());
        Assertions.assertEquals(time(40), viewport.pin());
    }

    @Test
    void statisticsUseReturnedSamplesAndIgnoreMissingPricesDespiteIrregularSpacing() {
        var first = new HistoryPoint(time(0), 100.0, null, 80.0, 120.0, null, null, 0L, null, null, null);
        var latest = new HistoryPoint(time(100), 300.0, null, 280.0, 340.0, null, null, 0L, null, null, null);
        var points = List.of(first, point(1, null), point(2, 200.0), latest, point(101, 900.0));
        var stats = HistoryAnalysis.stats(points, time(0), time(100), true).orElseThrow();
        Assertions.assertEquals(3, stats.samples());
        Assertions.assertEquals(200.0, stats.average());
        Assertions.assertEquals(80.0, stats.low());
        Assertions.assertEquals(340.0, stats.high());
        Assertions.assertEquals(200.0, stats.change());
        Assertions.assertEquals(200.0, stats.percent());
        Assertions.assertEquals(0L, QuantityMetric.OpenOrders.value(point(0, null), true));
        Assertions.assertNull(QuantityMetric.MovingWeek.value(point(0, null), true));
        var viewport = new HistoryViewport();
        viewport.update(points, time(0), time(101), true);
        Assertions.assertTrue(viewport.connects(first, points.get(1)));
        Assertions.assertFalse(viewport.connects(points.get(2), latest));
        var single = HistoryAnalysis.stats(List.of(point(10, 75.0)), time(0), time(20), true).orElseThrow();
        Assertions.assertEquals(time(10), single.firstSample());
        Assertions.assertEquals(time(10), single.lastSample());
        Assertions.assertEquals(75, single.average());
        Assertions.assertEquals(75, single.low());
        Assertions.assertEquals(75, single.high());
        Assertions.assertNull(single.change());
        Assertions.assertNull(single.percent());
    }

    @Test
    void clippedConnectionsAndSelectionRespectBoundaryNeighborsCadenceAndMissingValues() {
        var viewport = new HistoryViewport();
        var before = point(0, 100.0);
        var after = point(20, 200.0);
        viewport.update(List.of(before, after), time(5), time(15), Duration.ofSeconds(20), true);
        Assertions.assertEquals(List.of(before, after), viewport.drawingSamples());
        var clipped = viewport.clippedConnection(before, after, before.buy(), after.buy()).orElseThrow();
        Assertions.assertEquals(time(5), clipped.start());
        Assertions.assertEquals(125.0, clipped.startValue());
        Assertions.assertEquals(time(15), clipped.end());
        Assertions.assertEquals(175.0, clipped.endValue());
        viewport.hoverAt(this, time(8), 1, point -> point.buy() != null);
        Assertions.assertEquals(time(0), viewport.hover());
        Assertions.assertTrue(viewport.clippedConnection(before, after, null, after.buy()).isEmpty());
        var distant = point(120, 500.0);
        viewport.update(List.of(before, after, distant), time(0), time(160), Duration.ofSeconds(20), true);
        viewport.pinAt(time(20), 1, point -> point.buy() != null);
        viewport.hoverAt(this, time(80), 1000, point -> point.buy() != null);
        Assertions.assertNull(viewport.hover());
        viewport.pinAt(time(80), 1000, point -> point.buy() != null);
        Assertions.assertEquals(time(20), viewport.pin());
        viewport.hoverAt(this, time(150), 1000, point -> point.buy() != null);
        Assertions.assertNull(viewport.hover());
        Assertions.assertTrue(viewport.clippedConnection(after, distant, after.buy(), distant.buy()).isEmpty());
        viewport.update(List.of(point(0, 100.0), point(20, null), point(40, 150.0)), time(0), time(60),
            Duration.ofSeconds(20), true);
        viewport.hoverAt(this, time(30), 1000, point -> point.buy() != null);
        Assertions.assertNull(viewport.hover());
        var sellOnly = new HistoryPoint(time(20), null, 110.0, null, null, null, null, null, null, null, null);
        viewport.update(List.of(before, sellOnly), time(0), time(30), Duration.ofSeconds(20), true);
        viewport.hoverAt(this, time(10), 1000, point -> point.buy() != null || point.sell() != null,
            (left, right) -> (left.buy() != null && right.buy() != null)
                || (left.sell() != null && right.sell() != null));
        Assertions.assertNull(viewport.hover());
        viewport.update(List.of(after), time(0), time(100), null, true);
        viewport.hoverAt(this, time(21), 1000, point -> point.buy() != null);
        Assertions.assertEquals(time(20), viewport.hover());
        viewport.hoverAt(this, time(22), 1000, point -> point.buy() != null);
        Assertions.assertNull(viewport.hover());
        viewport.update(List.of(before, distant), time(0), time(160), null, true);
        Assertions.assertFalse(viewport.connects(before, distant));
        var mixed = List.of(point(0, 100.0), point(20, 101.0), point(300, 102.0), point(320, 103.0),
            point(600, 104.0), point(620, 105.0), point(900, 106.0), point(1220, 107.0), point(1520, 108.0),
            point(3320, 109.0));
        viewport.update(mixed, time(0), time(3500), null, true);
        for (int index = 1; index < mixed.size() - 1; index++) {
            Assertions.assertTrue(viewport.connects(mixed.get(index - 1), mixed.get(index)));
        }
        Assertions.assertFalse(viewport.connects(mixed.get(8), mixed.get(9)));
        viewport.update(mixed, time(0), time(3500), Duration.ofSeconds(20), true);
        Assertions.assertFalse(viewport.connects(mixed.get(1), mixed.get(2)));
    }

    private static Instant time(long second) {
        return Instant.EPOCH.plusSeconds(second);
    }

    private static HistoryPoint point(long second, Double buy) {
        return new HistoryPoint(time(second), buy, null, null, null, null, null, 0L, null, null, null);
    }
}
