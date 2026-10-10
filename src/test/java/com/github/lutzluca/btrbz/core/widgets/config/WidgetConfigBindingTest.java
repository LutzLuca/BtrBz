package com.github.lutzluca.btrbz.core.widgets.config;

import com.github.lutzluca.btrbz.core.widgets.WidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import com.github.lutzluca.btrbz.cache.CacheDependencies;
import com.github.lutzluca.btrbz.core.widgets.cache.WidgetDataSource;
import com.github.lutzluca.btrbz.core.widgets.dailylimit.DailyLimitWidgetConfig;
import com.github.lutzluca.btrbz.core.widgets.orderbook.OrderBookWidgetConfig;
import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.core.widgets.presets.OrderPresetsWidgetConfig;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetDisplayOptions.NumberStyle;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
            var binding = new WidgetConfigBinding<>(handle(
                WidgetId.parse("btrbz:presets"), () -> config,
                OrderPresetsWidgetConfig::new, value -> value.frame,
                OrderPresetsWidgetConfig::resetPreferences), changes::incrementAndGet);

            binding.resetPreferences();

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
            var binding = new WidgetConfigBinding<>(handle(
                WidgetId.parse("btrbz:daily"), () -> config,
                DailyLimitWidgetConfig::new, value -> value.frame,
                DailyLimitWidgetConfig::resetPreferences), () -> {});

            binding.resetPreferences();

            assertEquals(NumberStyle.Compact, config.numberStyle);
            assertEquals(1234, config.usedToday);
            assertEquals(99, config.lastResetEpochDay);
        }

        @Test
        @DisplayName("reset all routes content and frame changes through their owners")
        void resetsFrameAppearanceOverrides() {
            var widgets = new WidgetsConfig();
            var config = widgets.orderBookScreen;
            config.depthMode = OrderBookWidgetConfig.DepthMode.Hidden;
            config.showOrderCount = false;
            config.scrolling = OrderBookWidgetConfig.ScrollMode.Together;
            config.frame.overrideScale = true;
            config.frame.scale = 1.7;
            config.frame.overrideBackground = true;
            config.frame.background = 0xAA102030;
            var id = WidgetId.parse("btrbz:order_book");
            var handle = handle(
                id, () -> config, OrderBookWidgetConfig::new,
                value -> value.frame, OrderBookWidgetConfig::resetPreferences);
            var definition = WidgetDefinition.<Object, OrderBookWidgetConfig, Void>builder(id, "Order Book")
                .config(handle)
                .data(source())
                .preview(() -> null)
                .viewFactory(() -> null)
                .build();
            var store = new WidgetStateStore(() -> widgets, () -> {});

            store.resetAll(definition, false);

            assertEquals(1, handle.contentChanges().revision());
            assertEquals(1, store.frameChanges(id).revision());
            assertFalse(config.frame.overrideScale);
            assertEquals(1.0, config.frame.scale);
            assertFalse(config.frame.overrideBackground);
            assertEquals(WidgetsConfig.DEFAULT_BACKGROUND, config.frame.background);
            assertEquals(UiStyles.palette().panelBackground(), store.backgroundColor(definition));
            assertEquals(OrderBookWidgetConfig.DepthMode.Cumulative, config.depthMode);
            assertTrue(config.showOrderCount);
            assertEquals(OrderBookWidgetConfig.ScrollMode.Independent, config.scrolling);
            assertTrue(config.frame.enabled);
        }
    }

    private static <C> WidgetConfigHandle<C> handle(
        WidgetId id,
        Supplier<C> current,
        Supplier<C> defaults,
        Function<C, WidgetFrameConfig> frame,
        WidgetPreferenceReset<C> reset
    ) {
        return new WidgetConfigHandle<>(id, current, defaults, frame, reset);
    }

    private static WidgetDataSource<Object> source() {
        return new WidgetDataSource<>() {
            @Override
            public CacheDependencies cacheDependencies() {
                return CacheDependencies.none();
            }

            @Override
            public Object snapshot(WidgetSession session) {
                return new Object();
            }
        };
    }
}
