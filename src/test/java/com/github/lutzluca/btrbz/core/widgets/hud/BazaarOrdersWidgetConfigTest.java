package com.github.lutzluca.btrbz.core.widgets.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Bazaar orders widget config")
class BazaarOrdersWidgetConfigTest {
    @Nested
    @DisplayName("visible order limit")
    class VisibleOrderLimit {
        @Test
        @DisplayName("clamps persisted values to the supported range")
        void clampsPersistedValuesToSupportedRange() {
            var config = new BazaarOrdersWidgetConfig();

            config.visibleOrders = -5;
            assertEquals(BazaarOrdersWidgetConfig.MIN_VISIBLE_ORDERS, config.supportedVisibleOrders());

            config.visibleOrders = 6;
            assertEquals(6, config.supportedVisibleOrders());

            config.visibleOrders = 15;
            assertEquals(BazaarOrdersWidgetConfig.MAX_VISIBLE_ORDERS, config.supportedVisibleOrders());
        }
    }
}
