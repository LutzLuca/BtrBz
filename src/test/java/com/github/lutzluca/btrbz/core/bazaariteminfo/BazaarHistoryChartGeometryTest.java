package com.github.lutzluca.btrbz.core.bazaariteminfo;

import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarHistoryChartGeometry.Visibility;
import com.github.lutzluca.coflnet.BazaarHistoryPoint;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BazaarHistoryChartGeometryTest {
    @Test
    void sortsChronologicallyAndProjectsEndpoints() {
        var later = point(20, 20, 25d, 15d, 22d, 18d, 20);
        var earlier = point(10, 10, 15d, 5d, 12d, 8d, 10);

        var geometry = BazaarHistoryChartGeometry.layout(
            List.of(later, earlier), new Visibility(true, true, true), 5, 7, 101, 51);

        assertFalse(geometry.isEmpty());
        assertEquals(5, geometry.buy().points().getFirst().x());
        assertEquals(105, geometry.buy().points().getLast().x());
        assertEquals(1, geometry.buy().segments().size());
        assertEquals(1, geometry.buyBand().size());
    }

    @Test
    void padsFlatValueRangeAndCentersSingleTimestamp() {
        var geometry = BazaarHistoryChartGeometry.layout(
            List.of(point(100, 100, null, null, null, null, 5)),
            new Visibility(true, false, false), 10, 20, 80, 40);

        assertFalse(geometry.isEmpty());
        assertTrue(geometry.bounds().minValue() < 100);
        assertTrue(geometry.bounds().maxValue() > 100);
        assertEquals(50, geometry.buy().points().getFirst().x());
        assertEquals(1, geometry.buy().points().size());
        assertTrue(geometry.buy().segments().isEmpty());
    }

    @Test
    void toleratesNullableBandsAndHiddenLines() {
        var geometry = BazaarHistoryChartGeometry.layout(
            List.of(
                point(10, 20, 9d, 11d, null, null, 1),
                point(11, 21, null, null, 20d, 22d, 2)),
            new Visibility(false, false, true), 0, 0, 100, 50);

        assertFalse(geometry.isEmpty());
        assertTrue(geometry.buy().points().isEmpty());
        assertEquals(1, geometry.buyBand().size());
        assertEquals(1, geometry.sellBand().size());
    }

    @Test
    void returnsEmptyWhenEverySeriesIsHidden() {
        var geometry = BazaarHistoryChartGeometry.layout(
            List.of(point(1, 2, null, null, null, null, 1)),
            new Visibility(false, false, false), 0, 0, 100, 50);

        assertTrue(geometry.isEmpty());
    }

    private static BazaarHistoryPoint point(
        double buy,
        double sell,
        Double minBuy,
        Double maxBuy,
        Double minSell,
        Double maxSell,
        long epochSecond
    ) {
        return new BazaarHistoryPoint(
            buy, sell, minBuy, maxBuy, minSell, maxSell,
            0, 0, 0, 0, Instant.ofEpochSecond(epochSecond));
    }
}
