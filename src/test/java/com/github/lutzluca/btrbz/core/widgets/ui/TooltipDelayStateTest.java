package com.github.lutzluca.btrbz.core.widgets.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TooltipDelayStateTest {
    @Test
    void requiresContinuousHoverOverTheSameTarget() {
        var state = new TooltipDelayState<Object>(200);
        var first = new Object();
        var second = new Object();

        assertFalse(state.ready(first, 1_000_000_000L));
        assertFalse(state.ready(first, 1_199_999_999L));
        assertTrue(state.ready(first, 1_200_000_000L));
        assertFalse(state.ready(second, 1_300_000_000L));
        assertTrue(state.ready(second, 1_500_000_000L));
    }

    @Test
    void leavingAComponentRestartsItsDelay() {
        var state = new TooltipDelayState<Object>(200);
        var target = new Object();

        state.ready(target, 0);
        assertFalse(state.ready(null, 300_000_000L));
        assertFalse(state.ready(target, 400_000_000L));
    }

    @Test
    void explicitResetRestartsTheDelayForTheSameTarget() {
        var state = new TooltipDelayState<Object>(200);
        var target = new Object();

        assertFalse(state.ready(target, 1_000_000_000L));
        assertTrue(state.ready(target, 1_200_000_000L));

        state.reset();

        assertFalse(state.ready(target, 1_300_000_000L));
        assertFalse(state.ready(target, 1_499_999_999L));
        assertTrue(state.ready(target, 1_500_000_000L));
    }
}
