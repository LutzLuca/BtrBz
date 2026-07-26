package com.github.lutzluca.btrbz.widgets.framework.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WidgetRenderSurfaceTest {
    @Test
    void surfaceDensityTracksFinalFramebufferDensity() {
        assertEquals(1.5, WidgetRenderSurface.renderDensity(2, 0.75));
        assertEquals(2.0, WidgetRenderSurface.renderDensity(2, 1.0));
        assertEquals(1.0, WidgetRenderSurface.renderDensity(1, 0.5));
    }

    @Test
    void surfaceDimensionsRoundOutward() {
        assertEquals(375, WidgetRenderSurface.surfacePixels(250, 1.5));
        assertEquals(188, WidgetRenderSurface.surfacePixels(250, 0.75));
    }
}
