package com.github.lutzluca.btrbz.core.widgets.trackedorders;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

class BazaarTrackedOrderListComponentTest {
    @Test
    void insertionIndicatorClampsFirstAndLastGapsIntoTheViewport() {
        Assertions.assertEquals(OptionalInt.of(100),
            BazaarTrackedOrderListComponent.visibleInsertionIndicatorY(99, 100, 199));
        Assertions.assertEquals(OptionalInt.of(199),
            BazaarTrackedOrderListComponent.visibleInsertionIndicatorY(200, 100, 199));
    }

    @Test
    void insertionIndicatorRejectsGapsOutsideTheVisibleTolerance() {
        Assertions.assertEquals(OptionalInt.empty(),
            BazaarTrackedOrderListComponent.visibleInsertionIndicatorY(98, 100, 199));
        Assertions.assertEquals(OptionalInt.empty(),
            BazaarTrackedOrderListComponent.visibleInsertionIndicatorY(201, 100, 199));
    }

    @Test
    void progressFillKeepsTheRowWidthStableAndClampsItsFraction() {
        Assertions.assertEquals(50, BazaarTrackedOrderRowComponent.progressFillWidth(200, 0.25));
        Assertions.assertEquals(0, BazaarTrackedOrderRowComponent.progressFillWidth(200, -1));
        Assertions.assertEquals(200, BazaarTrackedOrderRowComponent.progressFillWidth(200, 2));
    }

}
