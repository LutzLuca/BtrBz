package com.github.lutzluca.btrbz.core.widgets.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Bazaar orders widget config")
class BazaarOrdersWidgetConfigTest {
    private final Gson gson = new Gson();

    @Nested
    @DisplayName("toggle hint")
    class ToggleHint {
        @Test
        @DisplayName("starts unseen for new and field-missing configs")
        void startsUnseen() {
            var fresh = new BazaarOrdersWidgetConfig();
            var fieldMissing = BazaarOrdersWidgetConfigTest.this.gson.fromJson(
                "{}", BazaarOrdersWidgetConfig.class);

            assertEquals(BazaarOrdersWidgetConfig.ToggleHintState.Unseen, fresh.supportedToggleHintState());
            assertEquals(BazaarOrdersWidgetConfig.ToggleHintState.Unseen, fieldMissing.supportedToggleHintState());
            assertTrue(fresh.showToggleHint());

            fresh.toggleHintState = BazaarOrdersWidgetConfig.ToggleHintState.Shown;
            assertTrue(fresh.showToggleHint());
        }

        @Test
        @DisplayName("round trips every persisted state")
        void roundTripsEveryState() {
            for (var state : BazaarOrdersWidgetConfig.ToggleHintState.values()) {
                var config = new BazaarOrdersWidgetConfig();
                config.toggleHintState = state;

                var restored = BazaarOrdersWidgetConfigTest.this.gson.fromJson(
                    BazaarOrdersWidgetConfigTest.this.gson.toJson(config),
                    BazaarOrdersWidgetConfig.class);

                assertEquals(state, restored.supportedToggleHintState());
            }
        }

        @Test
        @DisplayName("preference reset preserves dismissal")
        void preferenceResetPreservesDismissal() {
            var config = new BazaarOrdersWidgetConfig();
            config.toggleHintState = BazaarOrdersWidgetConfig.ToggleHintState.Dismissed;

            BazaarOrdersWidgetConfig.resetPreferences(config, new BazaarOrdersWidgetConfig());

            assertEquals(BazaarOrdersWidgetConfig.ToggleHintState.Dismissed, config.supportedToggleHintState());
            assertFalse(config.showToggleHint());
        }
    }

    @Nested
    @DisplayName("preference reset")
    class PreferenceReset {
        @Test
        @DisplayName("restores the market detail preferences")
        void restoresMarketDetailPreferences() {
            var config = new BazaarOrdersWidgetConfig();
            config.showQueue = false;
            config.showUndercutGap = true;

            BazaarOrdersWidgetConfig.resetPreferences(config, new BazaarOrdersWidgetConfig());

            assertTrue(config.showQueue);
            assertFalse(config.showUndercutGap);
        }
    }

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
