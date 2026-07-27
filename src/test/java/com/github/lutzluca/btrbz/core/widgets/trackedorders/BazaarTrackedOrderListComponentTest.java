package com.github.lutzluca.btrbz.core.widgets.trackedorders;

import com.github.lutzluca.btrbz.core.widgets.ui.WidgetDisplayOptions;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BazaarTrackedOrderListComponentTest {
    @Test
    void insertionIndicatorClampsFirstAndLastGapsIntoTheViewport() {
        assertEquals(OptionalInt.of(100),
            BazaarTrackedOrderListComponent.visibleInsertionIndicatorY(99, 100, 199));
        assertEquals(OptionalInt.of(199),
            BazaarTrackedOrderListComponent.visibleInsertionIndicatorY(200, 100, 199));
    }

    @Test
    void insertionIndicatorRejectsGapsOutsideTheVisibleTolerance() {
        assertEquals(OptionalInt.empty(),
            BazaarTrackedOrderListComponent.visibleInsertionIndicatorY(98, 100, 199));
        assertEquals(OptionalInt.empty(),
            BazaarTrackedOrderListComponent.visibleInsertionIndicatorY(201, 100, 199));
    }

    @Test
    void progressFillKeepsTheRowWidthStableAndClampsItsFraction() {
        assertEquals(50, BazaarTrackedOrderRowComponent.progressFillWidth(200, 0.25));
        assertEquals(0, BazaarTrackedOrderRowComponent.progressFillWidth(200, -1));
        assertEquals(200, BazaarTrackedOrderRowComponent.progressFillWidth(200, 2));
    }

    @Test
    void compactRowsUseAThinnerProgressBar() {
        assertEquals(1, BazaarTrackedOrderRowComponent.progressHeight(
            TrackedOrdersWidgetConfig.TrackedLayout.Compact));
        assertEquals(2, BazaarTrackedOrderRowComponent.progressHeight(
            TrackedOrdersWidgetConfig.TrackedLayout.Standard));
    }
}
