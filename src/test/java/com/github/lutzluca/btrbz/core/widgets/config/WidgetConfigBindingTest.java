package com.github.lutzluca.btrbz.core.widgets.config;

import com.github.lutzluca.btrbz.core.widgets.dailylimit.DailyLimitWidgetConfig;
import com.github.lutzluca.btrbz.core.widgets.presets.OrderPresetsWidgetConfig;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetDisplayOptions.NumberStyle;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Widget config binding")
class WidgetConfigBindingTest {
    @Nested
    @DisplayName("preference resets")
    class PreferenceResets {
        @Test
        @DisplayName("preserve durable preset volumes")
        void preservesPresetVolumes() {
            var config = new OrderPresetsWidgetConfig();
            config.contentWidth = 91;
            config.volumes.addAll(List.of(64, 1024));
            var changes = new AtomicInteger();
            var binding = new WidgetConfigBinding<>(
                () -> config, OrderPresetsWidgetConfig::new, value -> value.frame,
                OrderPresetsWidgetConfig::resetPreferences, changes::incrementAndGet
            );

            binding.resetAll();

            assertEquals(50, config.contentWidth);
            assertEquals(5, config.visibleRows);
            assertEquals(List.of(64, 1024), config.volumes);
            assertEquals(1, changes.get());
        }

        @Test
        @DisplayName("preserve daily accounting state")
        void preservesDailyUsage() {
            var config = new DailyLimitWidgetConfig();
            config.numberStyle = NumberStyle.Exact;
            config.usedToday = 1234;
            config.lastResetEpochDay = 99;
            var binding = new WidgetConfigBinding<>(
                () -> config, DailyLimitWidgetConfig::new, value -> value.frame,
                DailyLimitWidgetConfig::resetPreferences, () -> {}
            );

            binding.resetAll();

            assertEquals(NumberStyle.Compact, config.numberStyle);
            assertEquals(1234, config.usedToday);
            assertEquals(99, config.lastResetEpochDay);
        }

        @Test
        @DisplayName("reset all clears frame appearance overrides")
        void resetsFrameAppearanceOverrides() {
            var config = new DailyLimitWidgetConfig();
            config.frame.overrideScale = true;
            config.frame.scale = 1.7;
            config.frame.overrideBackground = true;
            config.frame.background = 0xAA102030;
            var binding = new WidgetConfigBinding<>(
                () -> config, DailyLimitWidgetConfig::new, value -> value.frame,
                DailyLimitWidgetConfig::resetPreferences, () -> {}
            );

            binding.resetAll();

            assertFalse(config.frame.overrideScale);
            assertEquals(1.0, config.frame.scale);
            assertFalse(config.frame.overrideBackground);
            assertEquals(WidgetsConfig.DEFAULT_BACKGROUND, config.frame.background);
            assertFalse(config.frame.enabled);
        }
    }
}
