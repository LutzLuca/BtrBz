package com.github.lutzluca.btrbz.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.github.lutzluca.btrbz.data.BazaarMessageDispatcher.BazaarMessage;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.OrderModels.OutstandingOrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OrderModelsTest {

    @Nested
    @DisplayName("OrderType.tryFrom")
    class OrderTypeTryFrom {

        @Test
        void parsesKnownValues() {
            assertEquals(OrderType.Buy, OrderType.tryFrom("BUY").get());
            assertEquals(OrderType.Sell, OrderType.tryFrom("SELL").get());
        }

        @Test
        void rejectsUnknownValue() {
            assertTrue(OrderType.tryFrom("UNKNOWN").isFailure());
        }

        @Test
        void isCaseSensitive() {
            assertTrue(OrderType.tryFrom("buy").isFailure());
        }
    }

    @Nested
    @DisplayName("OrderStatus.sameVariant")
    class OrderStatusSameVariant {

        @Test
        void returnsTrueForSameClass() {
            assertTrue(new OrderStatus.Unknown().sameVariant(new OrderStatus.Unknown()));
            assertTrue(new OrderStatus.Undercut(5.0).sameVariant(new OrderStatus.Undercut(10.0)));
        }

        @Test
        void returnsFalseForDifferentClasses() {
            assertFalse(new OrderStatus.Unknown().sameVariant(new OrderStatus.Top()));
        }

        @Test
        void returnsFalseForNull() {
            assertFalse(new OrderStatus.Unknown().sameVariant(null));
        }
    }

    @Nested
    @DisplayName("OrderStatus.toString")
    class OrderStatusToString {

        @Test
        void rendersEachVariantName() {
            assertEquals("Unknown", new OrderStatus.Unknown().toString());
            assertEquals("Top", new OrderStatus.Top().toString());
            assertEquals("Matched", new OrderStatus.Matched().toString());
            assertEquals("Undercut", new OrderStatus.Undercut(3.5).toString());
        }
    }

    @Nested
    @DisplayName("TrackedOrder.matches")
    class TrackedOrderMatches {

        private final TrackedOrder trackedOrder = new TrackedOrder(new OrderInfo.UnfilledOrderInfo(
            "Enchanted Hopper",
            OrderType.Buy,
            64,
            1234.5,
            12,
            0,
            3
        ));

        @Test
        void matchesEquivalentOrderInfo() {
            assertTrue(this.trackedOrder.matches(unfilledInfo("Enchanted Hopper", OrderType.Buy, 64, 1234.5)));
        }

        @Test
        void rejectsDifferentProductName() {
            assertFalse(this.trackedOrder.matches(unfilledInfo("Wrong Item", OrderType.Buy, 64, 1234.5)));
        }

        @Test
        void rejectsDifferentOrderType() {
            assertFalse(this.trackedOrder.matches(unfilledInfo("Enchanted Hopper", OrderType.Sell, 64, 1234.5)));
        }

        @Test
        void rejectsDifferentVolume() {
            assertFalse(this.trackedOrder.matches(unfilledInfo("Enchanted Hopper", OrderType.Buy, 63, 1234.5)));
        }

        @Test
        void rejectsDifferentPrice() {
            assertFalse(this.trackedOrder.matches(unfilledInfo("Enchanted Hopper", OrderType.Buy, 64, 1234.5001)));
        }

        @Test
        void treatsPositiveAndNegativeZeroAsDifferentPrices() {
            var zeroTrackedOrder = new TrackedOrder(new OrderInfo.UnfilledOrderInfo(
                "Heat Core",
                OrderType.Sell,
                1,
                0.0,
                0,
                0,
                7
            ));

            assertFalse(zeroTrackedOrder.matches(unfilledInfo("Heat Core", OrderType.Sell, 1, -0.0)));
        }

        private static OrderInfo.UnfilledOrderInfo unfilledInfo(
            String productName,
            OrderType type,
            int volume,
            double pricePerUnit
        ) {
            return new OrderInfo.UnfilledOrderInfo(productName, type, volume, pricePerUnit, 0, 0, 0);
        }
    }

    @Nested
    @DisplayName("OutstandingOrderInfo.matches")
    class OutstandingOrderInfoMatches {

        private final OutstandingOrderInfo outstandingOrder = new OutstandingOrderInfo(
            "Summoning Eye",
            OrderType.Sell,
            12,
            825000.0,
            9_900_000.0
        );

        @Test
        void matchesEquivalentSetupMessage() {
            assertTrue(this.outstandingOrder.matches(setup(OrderType.Sell, 12, "Summoning Eye", 9_900_000.0)));
        }

        @Test
        void rejectsDifferentProductName() {
            assertFalse(this.outstandingOrder.matches(setup(OrderType.Sell, 12, "Heat Core", 9_900_000.0)));
        }

        @Test
        void rejectsDifferentType() {
            assertFalse(this.outstandingOrder.matches(setup(OrderType.Buy, 12, "Summoning Eye", 9_900_000.0)));
        }

        @Test
        void rejectsDifferentVolume() {
            assertFalse(this.outstandingOrder.matches(setup(OrderType.Sell, 11, "Summoning Eye", 9_900_000.0)));
        }

        @Test
        void rejectsDifferentTotal() {
            assertFalse(this.outstandingOrder.matches(setup(OrderType.Sell, 12, "Summoning Eye", 9_900_001.0)));
        }

        private static BazaarMessage.OrderSetup setup(
            OrderType type,
            int volume,
            String productName,
            double total
        ) {
            return new BazaarMessage.OrderSetup(type, volume, productName, total);
        }
    }
}