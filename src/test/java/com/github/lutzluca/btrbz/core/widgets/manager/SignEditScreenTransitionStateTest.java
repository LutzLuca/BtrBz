package com.github.lutzluca.btrbz.core.widgets.manager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Sign edit widget-manager transition")
class SignEditScreenTransitionStateTest {
    @Nested
    @DisplayName("one-shot removal suppression")
    class OneShotRemovalSuppression {
        @Test
        @DisplayName("does not suppress a normal removal")
        void leavesNormalRemovalActive() {
            var state = new SignEditScreenTransitionState();

            assertFalse(state.consumeSuspendedRemoval());
        }

        @Test
        @DisplayName("consumes only the manager-triggered removal")
        void consumesOneRemoval() {
            var state = new SignEditScreenTransitionState();
            state.suspendNextRemoval();

            assertTrue(state.consumeSuspendedRemoval());
            assertFalse(state.consumeSuspendedRemoval());
        }
    }
}
