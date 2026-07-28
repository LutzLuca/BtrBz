package com.github.lutzluca.btrbz.core.widgets.data;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.lutzluca.btrbz.data.ProductIdentity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OrdersWidgetDataTest {

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
