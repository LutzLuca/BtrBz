package com.github.lutzluca.btrbz.core.widgets.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Retained scroll state")
class RetainedScrollStateTest {
    @Nested
    @DisplayName("absolute offset")
    class AbsoluteOffset {
        @Test
        @DisplayName("stays fixed when content grows")
        void staysFixedWhenContentGrows() {
            var state = new RetainedScrollState();
            state.remember(50.0);

            assertEquals(50.0, state.restore(100.0));
            assertEquals(50.0, state.restore(200.0));
        }

        @Test
        @DisplayName("survives a transient empty layout")
        void survivesTransientEmptyLayout() {
            var state = new RetainedScrollState();
            state.remember(50.0);

            assertEquals(0.0, state.restore(0.0));
            assertEquals(50.0, state.restore(200.0));
        }

        @Test
        @DisplayName("commits the clamped offset after content shrinks")
        void commitsClampedOffsetAfterContentShrinks() {
            var state = new RetainedScrollState();
            state.remember(50.0);

            double clampedOffset = state.restore(25.0);
            state.remember(clampedOffset);

            assertEquals(25.0, state.restore(200.0));
        }
    }
}
