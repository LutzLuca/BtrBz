package com.github.lutzluca.btrbz.core.config;

import com.github.lutzluca.btrbz.core.widgets.WidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetPlacement;
import com.github.lutzluca.btrbz.core.widgets.WidgetRegistry;
import com.github.lutzluca.btrbz.core.widgets.config.WidgetFrameConfig;
import com.github.lutzluca.btrbz.core.widgets.config.WidgetsConfig;
import dev.isxander.yacl3.api.ButtonOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;


@DisplayName("YACL widget category")
class WidgetConfigCategoryTest {
    @Test
    @DisplayName("provides config-only controls for the widget manager launcher")
    void containsWidgetManagerControls() {
        var options = ConfigScreen.widgetManagerOptions(new WidgetsConfig());

        assertEquals(3, options.size());
        assertFalse(options.getFirst() instanceof ButtonOption);
        assertInstanceOf(ButtonOption.class, options.get(1));
        assertInstanceOf(ButtonOption.class, options.getLast());
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
        return WidgetDefinition.<Object, TestConfig, Void>builder(WidgetId.parse(id), name)
            .config(TestConfig::new, TestConfig::new, value -> value.frame, (current, defaults) -> {})
            .runtimeData(_ -> new Object())
            .preview(() -> null)
            .viewFactory(() -> null)
            .build();
    }

    private static final class TestConfig {
        private final WidgetFrameConfig frame = new WidgetFrameConfig(WidgetPlacement.topLeft(0, 0));
    }
}
