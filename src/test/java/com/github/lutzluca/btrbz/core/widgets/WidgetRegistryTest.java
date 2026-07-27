package com.github.lutzluca.btrbz.core.widgets;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetFrameConfig;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Widget registry")
class WidgetRegistryTest {
    @Test
    @DisplayName("rejects duplicate IDs and preserves registration order")
    void rejectsDuplicates() {
        var registry = new WidgetRegistry();
        var first = definition("btrbz:first");
        var second = definition("btrbz:second");
        registry.register(first);
        registry.register(second);
        assertEquals(java.util.List.of(first, second), registry.all());
        assertThrows(IllegalArgumentException.class, () -> registry.register(definition("btrbz:first")));
    }

    @Test
    @DisplayName("rejects late registration after runtime finalization")
    void rejectsLateRegistration() {
        var registry = new WidgetRegistry();
        registry.register(definition("btrbz:first"));

        registry.freeze();

        assertThrows(IllegalStateException.class, () -> registry.register(definition("btrbz:late")));
    }

    private static WidgetDefinition<Object, TestConfig, Void> definition(String id) {
        return WidgetDefinition.<Object, TestConfig, Void>builder(WidgetId.parse(id), id)
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
