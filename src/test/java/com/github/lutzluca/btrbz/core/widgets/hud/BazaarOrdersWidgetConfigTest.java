package com.github.lutzluca.btrbz.core.widgets.hud;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
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

            Assertions.assertEquals(BazaarOrdersWidgetConfig.ToggleHintState.Unseen, fresh.supportedToggleHintState());
            Assertions.assertEquals(BazaarOrdersWidgetConfig.ToggleHintState.Unseen,
                fieldMissing.supportedToggleHintState());
            Assertions.assertTrue(fresh.showToggleHint());

            fresh.toggleHintState = BazaarOrdersWidgetConfig.ToggleHintState.Shown;
            Assertions.assertTrue(fresh.showToggleHint());
        }

        @Test
        @DisplayName("preference reset preserves dismissal")
        void preferenceResetPreservesDismissal() {
            var config = new BazaarOrdersWidgetConfig();
            config.toggleHintState = BazaarOrdersWidgetConfig.ToggleHintState.Dismissed;

            BazaarOrdersWidgetConfig.resetPreferences(config, new BazaarOrdersWidgetConfig());

            Assertions.assertEquals(BazaarOrdersWidgetConfig.ToggleHintState.Dismissed,
                config.supportedToggleHintState());
            Assertions.assertFalse(config.showToggleHint());
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

            Assertions.assertTrue(config.showQueue);
            Assertions.assertFalse(config.showUndercutGap);
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
            Assertions.assertEquals(BazaarOrdersWidgetConfig.MIN_VISIBLE_ORDERS, config.supportedVisibleOrders());

            config.visibleOrders = 6;
            Assertions.assertEquals(6, config.supportedVisibleOrders());

            config.visibleOrders = 15;
            Assertions.assertEquals(BazaarOrdersWidgetConfig.MAX_VISIBLE_ORDERS, config.supportedVisibleOrders());
        }
    }
}
