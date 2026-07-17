package com.github.lutzluca.btrbz.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.lutzluca.btrbz.core.modules.OrderBookPriceModule.OrderBookPriceConfig;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OrderBookPriceConfigTest {

    private final Gson gson = new GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create();

    @Nested
    @DisplayName("Flip Sign Overlay")
    class FlipSignOverlay {

        @Test
        void enabledByDefault() {
            var config = new OrderBookPriceConfig();

            assertTrue(config.showOnFlipSign);
        }

        @Test
        void remainsEnabledWhenLoadingOlderConfig() {
            var config = gson.fromJson("{}", OrderBookPriceConfig.class);

            assertTrue(config.showOnFlipSign);
        }

        @Test
        void deserializesDisabledSetting() {
            var config = gson.fromJson("{\"show_on_flip_sign\": false}", OrderBookPriceConfig.class);

            assertFalse(config.showOnFlipSign);
        }
    }
}
