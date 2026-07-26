package com.github.lutzluca.btrbz.widgets.framework.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScrollSafeDiscreteSliderComponentTest {
    @Test
    void scaleValuesSnapToFiveHundredthsWithinBounds() {
        assertEquals(0.5, ScrollSafeDiscreteSliderComponent.snapToStep(0.49, 0.5, 2.0, 0.05));
        assertEquals(1.05, ScrollSafeDiscreteSliderComponent.snapToStep(1.03, 0.5, 2.0, 0.05), 1e-9);
        assertEquals(2.0, ScrollSafeDiscreteSliderComponent.snapToStep(2.1, 0.5, 2.0, 0.05));
    }
}
