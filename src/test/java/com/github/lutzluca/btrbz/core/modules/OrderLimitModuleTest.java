package com.github.lutzluca.btrbz.core.modules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.lutzluca.btrbz.core.modules.OrderLimitModule.OrderLimitConfig;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OrderLimitModuleTest {

    @Nested
    @DisplayName("daily limit reset")
    class DailyLimitReset {

        @Test
        void resetsOnlyWhenEpochDayChanges() {
            var module = new TestOrderLimitModule();
            var config = new OrderLimitConfig();
            module.applyConfigState(config);

            config.usedToday = 12_345.0;
            assertEquals(-1, config.lastResetEpochDay);
            assertTrue(module.resetOrderLimitForDay(20_000));
            assertEquals(0.0, config.usedToday);
            assertEquals(20_000, config.lastResetEpochDay);

            config.usedToday = 6_789.0;
            assertFalse(module.resetOrderLimitForDay(20_000));
            assertEquals(6_789.0, config.usedToday);
            assertEquals(20_000, config.lastResetEpochDay);

            assertTrue(module.resetOrderLimitForDay(20_001));
            assertEquals(0.0, config.usedToday);
            assertEquals(20_001, config.lastResetEpochDay);
        }
    }

    private static final class TestOrderLimitModule extends OrderLimitModule {

        @Override
        protected void updateConfig(Consumer<OrderLimitConfig> updater) {
            updater.accept(this.configState);
        }
    }
}
