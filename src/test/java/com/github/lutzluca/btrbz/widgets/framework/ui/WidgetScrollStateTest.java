package com.github.lutzluca.btrbz.widgets.framework.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WidgetScrollStateTest {
    @Test
    void offsetIsStoredAsClampedProgressForRebuiltContainers() {
        var state = new WidgetScrollState();

        state.rememberOffset(50.0, 100);
        assertEquals(0.5, state.progress());

        state.rememberOffset(125.0, 100);
        assertEquals(1.0, state.progress());

        state.rememberOffset(-10.0, 100);
        assertEquals(0.0, state.progress());
    }

    @Test
    void thumbCaptureSurvivesUntilExplicitRelease() {
        var state = new WidgetScrollState();

        state.captureThumb();
        assertTrue(state.thumbCaptured());
        assertTrue(state.visibleUntil() > 0L);

        state.releaseThumb();
        assertFalse(state.thumbCaptured());
    }
}
