package com.github.lutzluca.btrbz.core.modules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ModuleDomainSeamsTest {

    @Nested
    @DisplayName("bookmark ordering")
    class BookmarkOrdering {

        @Test
        void movesAndRemovesUsingSemanticIds() {
            var productIds = new ArrayList<>(List.of("APPLE", "CARROT", "POTATO"));

            assertTrue(BookmarkModule.moveMatching(productIds, "APPLE"::equals, 2));
            assertEquals(List.of("CARROT", "POTATO", "APPLE"), productIds);
            assertTrue(BookmarkModule.removeMatching(productIds, "POTATO"::equals));
            assertEquals(List.of("CARROT", "APPLE"), productIds);
            assertFalse(BookmarkModule.removeMatching(productIds, "MISSING"::equals));
        }
    }

    @Nested
    @DisplayName("daily limit reset")
    class DailyLimitReset {

        @Test
        void resetsOnlyWhenTheUtcEpochDayChanges() {
            assertFalse(OrderLimitModule.needsReset(20_000, 20_000));
            assertTrue(OrderLimitModule.needsReset(20_000, 20_001));
        }
    }

    @Nested
    @DisplayName("price calculations")
    class PriceCalculations {

        @Test
        void calculatesSpreadTotalAndSideAdjustment() {
            assertEquals(125.0, PriceDiffModule.calculateTotalDifference(2.5, 50));
            assertEquals(100.1, OrderBookPriceModule.applyUndercut(100.0, OrderType.Buy));
            assertEquals(99.9, OrderBookPriceModule.applyUndercut(100.0, OrderType.Sell));
            assertEquals(0.1, OrderBookPriceModule.applyUndercut(0.05, OrderType.Sell));
        }
    }
}
