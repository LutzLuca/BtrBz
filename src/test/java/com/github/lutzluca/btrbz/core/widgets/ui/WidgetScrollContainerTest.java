package com.github.lutzluca.btrbz.core.widgets.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WidgetScrollContainerTest {
    @Test
    void wheelEventsEscapeAtBoundariesAndRemainConsumedWhenMoving() {
        assertFalse(ScrollWheelPolicy.wouldMove(0, 100, 1, 15));
        assertTrue(ScrollWheelPolicy.wouldMove(0, 100, -1, 15));
        assertFalse(ScrollWheelPolicy.wouldMove(100, 100, -1, 15));
        assertTrue(ScrollWheelPolicy.wouldMove(100, 100, 1, 15));
        assertFalse(ScrollWheelPolicy.wouldMove(0, 0, -1, 15));
    }
}
