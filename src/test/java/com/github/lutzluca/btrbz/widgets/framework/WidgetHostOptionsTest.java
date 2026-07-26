package com.github.lutzluca.btrbz.widgets.framework;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WidgetHostOptionsTest {
    private static final WidgetId FIRST = WidgetId.of(Identifier.fromNamespaceAndPath("btrbz", "test_first"));
    private static final WidgetId SECOND = WidgetId.of(Identifier.fromNamespaceAndPath("btrbz", "test_second"));

    @Test
    void runtimeRenderingUsesPersistentActiveState() {
        var options = WidgetHostOptions.runtime(false);

        assertTrue(options.shouldRender(FIRST, true));
        assertFalse(options.shouldRender(FIRST, false));
    }

    @Test
    void managerRenderingIgnoresPersistentActiveState() {
        var options = WidgetHostOptions.management(null, Set.of(FIRST), Map.of());

        assertTrue(options.shouldRender(FIRST, false));
        assertFalse(options.shouldRender(SECOND, true));
    }
}
