package com.github.lutzluca.btrbz.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OrderTooltipProviderTest {

    @Nested
    @DisplayName("formatDuration")
    class FormatDuration {

        @Test
        void formatsSubMinuteDurations() {
            assertEquals("< 1m", OrderTooltipProvider.formatDuration(0.5));
        }

        @Test
        void formatsExactHours() {
            assertEquals("2h", OrderTooltipProvider.formatDuration(120));
        }

        @Test
        void formatsHoursAndMinutes() {
            assertEquals("2h 5m", OrderTooltipProvider.formatDuration(125));
        }

        @Test
        void formatsZero() {
            assertEquals("< 1m", OrderTooltipProvider.formatDuration(0));
        }
    }
}
