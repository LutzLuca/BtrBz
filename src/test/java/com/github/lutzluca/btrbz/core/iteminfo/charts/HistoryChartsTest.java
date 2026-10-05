package com.github.lutzluca.btrbz.core.iteminfo.charts;

import com.github.lutzluca.coflnet.HistoryPoint;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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
    }

    private static Instant time(long second) {
        return Instant.EPOCH.plusSeconds(second);
    }

    private static HistoryPoint point(long second, Double buy) {
        return new HistoryPoint(time(second), buy, null, null, null, null, null, 0L, null, null, null);
    }
}
