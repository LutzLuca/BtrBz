package com.github.lutzluca.btrbz.core.config;

import com.github.lutzluca.btrbz.core.widgets.WidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetPlacement;
import com.github.lutzluca.btrbz.core.widgets.WidgetRegistry;
import com.github.lutzluca.btrbz.core.widgets.config.WidgetFrameConfig;
import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigHandle;
import com.github.lutzluca.btrbz.cache.CacheDependencies;
import com.github.lutzluca.btrbz.core.widgets.cache.WidgetDataSource;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.Assertions;

@DisplayName("YACL widget category")
class WidgetConfigCategoryTest {
    @Test
    @DisplayName("derives one linear manager launcher per registry entry without widget bindings")
    void containsOnlyManagerLaunchers() {
        var registry = new WidgetRegistry();
        registry.register(definition("btrbz:first", "First"));
        registry.register(definition("btrbz:second", "Second"));

        var options = ConfigScreen.widgetOptions(registry, (_, _) -> {});

        Assertions.assertEquals(2, options.size());
        Assertions.assertEquals("First", options.getFirst().name().getString());
        Assertions.assertEquals("Second", options.getLast().name().getString());
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
