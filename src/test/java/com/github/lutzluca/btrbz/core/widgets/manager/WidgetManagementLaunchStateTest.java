package com.github.lutzluca.btrbz.core.widgets.manager;

import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("Widget manager launch state")
class WidgetManagementLaunchStateTest {
    @Nested
    @DisplayName("Ordinary launches")
    class OrdinaryLaunches {
        @Test
        @DisplayName("start without rendered widgets")
        void normalLaunchStartsWithoutRenderedWidgets() {
            var state = WidgetManagementLaunchState.empty();

            assertNull(state.selectedWidget());
            assertEquals(Set.of(), state.renderedWidgets());
        }

        @Test
        @DisplayName("select and render only the configured widget")
        void widgetConfigurationLaunchSelectsAndRendersOnlyThatWidget() {
            var widgetId = WidgetId.parse("test:configured");

            var state = WidgetManagementLaunchState.configure(widgetId);

            assertEquals(widgetId, state.selectedWidget());
            assertEquals(Set.of(widgetId), state.renderedWidgets());
        }
    }

    @Nested
    @DisplayName("Contextual launches")
    class ContextualLaunches {
        @Test
        @DisplayName("preserve every widget rendered in the captured session")
        void preservesInitiallyRenderedWidgets() {
            var first = WidgetId.parse("test:first");
            var second = WidgetId.parse("test:second");

            var state = WidgetManagementLaunchState.contextual(Set.of(first, second));

            assertNull(state.selectedWidget());
            assertEquals(Set.of(first, second), state.renderedWidgets());
        }

        @Test
        @DisplayName("add a selected hidden widget to the rendered set")
        void addsSelectedHiddenWidget() {
            var rendered = WidgetId.parse("test:rendered");
            var selected = WidgetId.parse("test:selected");

            var state = WidgetManagementLaunchState.contextual(Set.of(rendered), selected);

            assertEquals(selected, state.selectedWidget());
            assertEquals(Set.of(rendered, selected), state.renderedWidgets());
        }
    }
}
