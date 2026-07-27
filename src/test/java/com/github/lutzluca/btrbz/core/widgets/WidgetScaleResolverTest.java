package com.github.lutzluca.btrbz.core.widgets;

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
        assertEquals(WidgetStateStore.MIN_SCALE, WidgetScaleResolver.fitToCanvas(1.0, 120, 80, 350, 142));
    }

    @Test
    void readableFloorKeepsGuiScaleOneAtNativeDensity() {
        assertEquals(WidgetStateStore.MIN_SCALE, WidgetScaleResolver.readableMinimumScale(1));
        assertEquals(WidgetStateStore.MIN_SCALE, WidgetScaleResolver.readableMinimumScale(2));
        assertEquals(WidgetStateStore.MIN_SCALE, WidgetScaleResolver.readableMinimumScale(3));
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

    @Test
    void baseAndPerWidgetScalesMultiplyWithinTheSupportedRange() {
        assertEquals(0.75, WidgetScaleResolver.combineRequestedScale(1.0, 0.75));
        assertEquals(0.6, WidgetScaleResolver.combineRequestedScale(0.8, 0.75), 0.000001);
        assertEquals(0.5, WidgetScaleResolver.combineRequestedScale(0.5, 0.5));
        assertEquals(2.0, WidgetScaleResolver.combineRequestedScale(2.0, 2.0));
    }
}
