package com.github.lutzluca.btrbz.core.widgets.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.lutzluca.btrbz.core.trackedorders.TrackedOrderManager;
import com.github.lutzluca.btrbz.core.widgets.cache.CacheToken;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo.FilledOrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OrdersWidgetDataTest {

    @Test
    void exposesTheManagerOwnedFilledOrderCount() {
        var market = new BazaarData();
        var manager = new TrackedOrderManager(market);
        manager.syncOrders(List.of(
            new FilledOrderInfo("First", OrderType.Buy, 1, 10.0, 1, 1, 0),
            new FilledOrderInfo("Second", OrderType.Sell, 1, 20.0, 1, 20, 1)
        ));
        var data = new OrdersWidgetData(
            market, manager, null,
            CacheToken.named("test.screen"), CacheToken.named("test.inventory")
        );

        assertEquals(2, data.computeSnapshot().filledOrderCount());
    }

    @Nested
    @DisplayName("observed product validation")
    class ObservedProductValidation {

        @Test
        void acceptsMatchingProductIds() {
            var expected = ProductIdentity.fromRuntime("Enchanted Diamond", "ENCHANTED_DIAMOND", null);
            var observed = ProductIdentity.fromRuntime("Different UI Name", "ENCHANTED_DIAMOND", null);

            assertTrue(OrdersWidgetData.sameProduct(expected, observed));
        }

        @Test
        void rejectsDifferentProductIdsEvenWhenNamesMatch() {
            var expected = ProductIdentity.fromRuntime("Enchanted Diamond", "ENCHANTED_DIAMOND", null);
            var observed = ProductIdentity.fromRuntime("Enchanted Diamond", "DIAMOND", null);

            assertFalse(OrdersWidgetData.sameProduct(expected, observed));
        }

        @Test
        void fallsBackToNormalizedNamesWhenAnIdIsUnavailable() {
            var expected = ProductIdentity.fromRuntime(" Enchanted   Diamond ", "ENCHANTED_DIAMOND", null);
            var observed = ProductIdentity.fromName("enchanted diamond");

            assertTrue(OrdersWidgetData.sameProduct(expected, observed));
        }

        @Test
        void rejectsDifferentProductsWithoutIds() {
            assertFalse(OrdersWidgetData.sameProduct(
                ProductIdentity.fromName("Enchanted Diamond"),
                ProductIdentity.fromName("Enchanted Emerald")
            ));
        }
    }
}
