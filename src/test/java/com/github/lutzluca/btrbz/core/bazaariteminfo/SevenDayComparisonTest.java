package com.github.lutzluca.btrbz.core.bazaariteminfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.lutzluca.btrbz.data.LiveProductSnapshot;
import com.github.lutzluca.btrbz.data.MarketSide;
import com.github.lutzluca.btrbz.data.PriceLevel;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.data.Totals;
import com.github.lutzluca.coflnet.BazaarHistoryPoint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SevenDayComparisonTest {
    @Test
    void calculatesSidesIndependently() {
        var points = List.of(
            point(10, 20, 0), point(20, 30, 60), point(30, 40, 120));

        var result = SevenDayComparison.calculate(points, live(24, 36));

        assertEquals(20, result.buy().average().orElseThrow());
        assertEquals(30, result.sell().average().orElseThrow());
        assertEquals(20, result.buy().deltaPercent().orElseThrow());
        assertEquals(20, result.sell().deltaPercent().orElseThrow());
    }

    @Test
    void missingLivePriceKeepsTheAverageWithoutDelta() {
        var empty = new MarketSide(List.of(), new Totals.Unavailable());
        var live = new LiveProductSnapshot(
            ProductIdentity.fromRuntime("Item", "ITEM", null), Optional.empty(), empty, empty);

        var result = SevenDayComparison.calculate(List.of(point(10, 20, 0)), live);

        assertEquals(10, result.buy().average().orElseThrow());
        assertTrue(result.buy().delta().isEmpty());
    }

    @Test
    void cadenceLabelChangesForIrregularSamples() {
        assertTrue(SevenDayComparison.regularCadence(List.of(
            point(1, 2, 0), point(1, 2, 60), point(1, 2, 120))));
        assertFalse(SevenDayComparison.regularCadence(List.of(
            point(1, 2, 0), point(1, 2, 60), point(1, 2, 300))));
        assertEquals("7d sample avg", SevenDayComparison.calculate(
            List.of(point(1, 2, 0), point(1, 2, 60), point(1, 2, 300)), live(1, 2)).label());
    }

    private static LiveProductSnapshot live(double buy, double sell) {
        var product = ProductIdentity.fromRuntime("Item", "ITEM", null);
        return new LiveProductSnapshot(
            product,
            Optional.empty(),
            new MarketSide(List.of(new PriceLevel(sell, 1, 1)), new Totals.Unavailable()),
            new MarketSide(List.of(new PriceLevel(buy, 1, 1)), new Totals.Unavailable()));
    }

    private static BazaarHistoryPoint point(double buy, double sell, long seconds) {
        return new BazaarHistoryPoint(
            buy, sell, null, null, null, null, 0, 0, 0, 0, Instant.ofEpochSecond(seconds));
    }
}
