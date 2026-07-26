package com.github.lutzluca.btrbz.widgets.framework.screen;

import com.github.lutzluca.btrbz.widgets.framework.WidgetId;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WidgetManagementLaunchStateTest {
    @Test
    void normalLaunchStartsWithoutRenderedWidgets() {
        var state = WidgetManagementLaunchState.empty();

        assertNull(state.selectedWidget());
        assertEquals(Set.of(), state.renderedWidgets());
    }

    @Test
    void widgetConfigurationLaunchSelectsAndRendersOnlyThatWidget() {
        var widgetId = WidgetId.parse("test:configured");

        var state = WidgetManagementLaunchState.configure(widgetId);

        assertEquals(widgetId, state.selectedWidget());
        assertEquals(Set.of(widgetId), state.renderedWidgets());
    }
}
