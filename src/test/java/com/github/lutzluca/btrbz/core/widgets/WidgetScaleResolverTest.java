package com.github.lutzluca.btrbz.core.widgets;

import com.github.lutzluca.btrbz.core.widgets.layout.WidgetScaleResolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WidgetScaleResolverTest {
    @Test
    void requestedScaleIsPreservedWhenTheWidgetFits() {
        assertEquals(1.25, WidgetScaleResolver.fitToCanvas(1.25, 800, 600, 350, 142));
    }

    @Test
    void oversizedWidgetFitsDownToTheSupportedFloor() {
        double scale = WidgetScaleResolver.fitToCanvas(1.0, 333, 300, 350, 142);

        assertTrue(scale < 1.0);
        assertTrue(WidgetScaleResolver.fitsCanvas(scale, 333, 300, 350, 142));
        assertEquals(1.0, WidgetScaleResolver.fitToCanvas(1.0, 800, 600, 350, 142));
    }

    @Test
    void automaticFitDoesNotGoBelowTheSupportedMinimumScale() {
        assertEquals(WidgetScaleResolver.MIN_SCALE, WidgetScaleResolver.fitToCanvas(1.0, 120, 80, 350, 142));
    }

    @Test
    void requestedScaleBelowTheReadableFloorIsRaised() {
        assertEquals(1.0, WidgetScaleResolver.fitToCanvas(0.75, 1.0, 800, 600, 350, 142));
    }

    @Test
    void reportsWhenTheReadableWidgetCannotFit() {
        double scale = WidgetScaleResolver.fitToCanvas(1.0, 1.0, 120, 80, 350, 142);

        assertEquals(1.0, scale);
        assertFalse(WidgetScaleResolver.fitsCanvas(scale, 120, 80, 350, 142));
        assertTrue(WidgetScaleResolver.fitsCanvas(1.0, 800, 600, 350, 142));
    }
}
