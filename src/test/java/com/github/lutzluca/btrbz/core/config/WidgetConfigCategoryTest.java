package com.github.lutzluca.btrbz.core.config;

import com.github.lutzluca.btrbz.core.widgets.WidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import com.github.lutzluca.btrbz.core.widgets.WidgetPlacement;
import com.github.lutzluca.btrbz.core.widgets.WidgetRegistry;
import com.github.lutzluca.btrbz.core.widgets.config.WidgetFrameConfig;
import dev.isxander.yacl3.api.ButtonOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@DisplayName("YACL widget category")
class WidgetConfigCategoryTest {
    @Test
    @DisplayName("derives one manager launcher per registry entry without widget bindings")
    void containsOnlyManagerLaunchers() {
        var registry = new WidgetRegistry();
        registry.register(definition("btrbz:first", "First"));
        registry.register(definition("btrbz:second", "Second"));

        var groups = ConfigScreen.widgetGroups(registry);

        assertEquals(2, groups.size());
        assertEquals("First", groups.getFirst().name().getString());
        assertEquals("Second", groups.getLast().name().getString());
        groups.forEach(group -> {
            assertEquals(1, group.options().size());
            assertInstanceOf(ButtonOption.class, group.options().getFirst());
        });
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
