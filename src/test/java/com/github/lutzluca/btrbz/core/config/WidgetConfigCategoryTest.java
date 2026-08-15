package com.github.lutzluca.btrbz.core.config;

import com.github.lutzluca.btrbz.core.widgets.WidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetPlacement;
import com.github.lutzluca.btrbz.core.widgets.WidgetRegistry;
import com.github.lutzluca.btrbz.core.widgets.config.WidgetFrameConfig;
import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigHandle;
import com.github.lutzluca.btrbz.core.widgets.cache.CacheDependencies;
import com.github.lutzluca.btrbz.core.widgets.cache.WidgetDataSource;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("YACL widget category")
class WidgetConfigCategoryTest {
    @Test
    @DisplayName("maps config images to their widgets without constructing the UI")
    void mapsWidgetImages() {
        var images = Map.of(
            "btrbz:bazaar_orders", ConfigImages.TrackedOrdersHud,
            "btrbz:tracked_orders_list", ConfigImages.TrackedOrdersBazaar,
            "btrbz:order_value", ConfigImages.OrderValueOverView,
            "btrbz:order_book", ConfigImages.OrderBookScreen,
            "btrbz:order_book_price", ConfigImages.OrderBookSign,
            "btrbz:bookmarks", ConfigImages.Bookmarks,
            "btrbz:order_presets", ConfigImages.OrderPresets,
            "btrbz:order_limit", ConfigImages.OrderLimit,
            "btrbz:price_diff", ConfigImages.PriceDiff);

        images.forEach((id, image) -> assertEquals(image, ConfigImages.forWidget(WidgetId.parse(id))));
    }

    @Test
    @DisplayName("derives one linear manager launcher per registry entry without widget bindings")
    void containsOnlyManagerLaunchers() {
        var registry = new WidgetRegistry();
        registry.register(definition("btrbz:first", "First"));
        registry.register(definition("btrbz:second", "Second"));

        var options = ConfigScreen.widgetOptions(registry);

        assertEquals(2, options.size());
        assertEquals("First", options.getFirst().name().getString());
        assertEquals("Second", options.getLast().name().getString());
    }

    private static WidgetDefinition<Object, TestConfig, Void> definition(String id, String name) {
        var widgetId = WidgetId.parse(id);
        var handle = new WidgetConfigHandle<>(
            widgetId, TestConfig::new, TestConfig::new,
            value -> value.frame, (current, defaults) -> {});
        return WidgetDefinition.<Object, TestConfig, Void>builder(widgetId, name)
            .config(handle)
            .data(source())
            .preview(() -> null)
            .viewFactory(() -> null)
            .build();
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

    private static final class TestConfig {
        private final WidgetFrameConfig frame = new WidgetFrameConfig(WidgetPlacement.topLeft(0, 0));
    }
}
