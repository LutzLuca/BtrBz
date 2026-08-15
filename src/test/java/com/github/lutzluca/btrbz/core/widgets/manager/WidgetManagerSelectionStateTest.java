package com.github.lutzluca.btrbz.core.widgets.manager;

import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Widget manager selection state")
class WidgetManagerSelectionStateTest {
    private static final WidgetId FIRST_WIDGET = WidgetId.parse("test:first");
    private static final WidgetId SECOND_WIDGET = WidgetId.parse("test:second");

    @Nested
    @DisplayName("Selection")
    class Selection {
        @Test
        @DisplayName("selecting a widget also renders it")
        void selectingWidgetAlsoRendersIt() {
            var state = new WidgetManagerSelectionState(WidgetManagementLaunchState.empty());

            assertTrue(state.select(FIRST_WIDGET));

            assertEquals(FIRST_WIDGET, state.selectedWidget());
            assertEquals(Set.of(FIRST_WIDGET), state.renderedWidgets());
        }

        @Test
        @DisplayName("selecting another widget keeps earlier widgets rendered")
        void selectingAnotherWidgetKeepsEarlierWidgetsRendered() {
            var state = new WidgetManagerSelectionState(WidgetManagementLaunchState.empty());
            state.select(FIRST_WIDGET);

            assertTrue(state.select(SECOND_WIDGET));

            assertEquals(SECOND_WIDGET, state.selectedWidget());
            assertEquals(Set.of(FIRST_WIDGET, SECOND_WIDGET), state.renderedWidgets());
        }

        @Test
        @DisplayName("reselecting a manually hidden widget renders it again")
        void reselectingManuallyHiddenWidgetRendersItAgain() {
            var state = new WidgetManagerSelectionState(WidgetManagementLaunchState.empty());
            state.select(FIRST_WIDGET);
            state.setRendered(FIRST_WIDGET, false);

            assertTrue(state.select(FIRST_WIDGET));

            assertEquals(FIRST_WIDGET, state.selectedWidget());
            assertEquals(Set.of(FIRST_WIDGET), state.renderedWidgets());
        }
    }

    @Nested
    @DisplayName("Explicit controls")
    class ExplicitControls {
        @Test
        @DisplayName("the render checkbox can still hide a selected widget")
        void renderCheckboxCanStillHideSelectedWidget() {
            var state = new WidgetManagerSelectionState(WidgetManagementLaunchState.empty());
            state.select(FIRST_WIDGET);

            assertTrue(state.setRendered(FIRST_WIDGET, false));

            assertEquals(FIRST_WIDGET, state.selectedWidget());
            assertEquals(Set.of(), state.renderedWidgets());
        }

        @Test
        @DisplayName("clearing selection keeps rendered widgets visible")
        void clearingSelectionKeepsRenderedWidgetsVisible() {
            var state = new WidgetManagerSelectionState(WidgetManagementLaunchState.empty());
            state.select(FIRST_WIDGET);

            assertTrue(state.clearSelection());

            assertNull(state.selectedWidget());
            assertEquals(Set.of(FIRST_WIDGET), state.renderedWidgets());
            assertFalse(state.clearSelection());
        }
    }
}
