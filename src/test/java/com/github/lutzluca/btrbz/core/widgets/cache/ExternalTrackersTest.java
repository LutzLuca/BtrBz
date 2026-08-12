package com.github.lutzluca.btrbz.core.widgets.cache;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("External widget state trackers")
class ExternalTrackersTest {
    @Nested
    @DisplayName("UTC day")
    class UtcDay {
        @Test
        @DisplayName("captures its initial day and refreshes it when read after rollover")
        void rolloverOnly() {
            var day = new AtomicLong(10);
            var tracker = new UtcDayTracker(day::get);

            tracker.initialize();
            assertEquals(10, tracker.currentDay());
            assertEquals(0, tracker.changes().revision());
            assertFalse(tracker.poll());
            day.set(11);
            assertEquals(11, tracker.currentDay());
            assertEquals(1, tracker.changes().revision());
            assertFalse(tracker.poll());
        }
    }

    @Nested
    @DisplayName("clipboard")
    class Clipboard {
        @Test
        @DisplayName("invalidates only when text changes")
        void textChangesOnly() {
            var value = new AtomicReference<>("64");
            var tracker = new ClipboardTracker(value::get);

            tracker.initialize();
            assertEquals(0, tracker.changes().revision());
            assertFalse(tracker.poll());
            value.set("128");
            assertTrue(tracker.poll());
            assertEquals("128", tracker.value());
            assertEquals(1, tracker.changes().revision());
        }

        @Test
        @DisplayName("keeps the last good text when polling fails")
        void failureKeepsValue() {
            var value = new AtomicReference<>("64");
            var tracker = new ClipboardTracker(() -> {
                if (value.get() == null) throw new IllegalStateException("unavailable");
                return value.get();
            });
            tracker.initialize();
            value.set(null);

            assertFalse(tracker.poll());
            assertEquals("64", tracker.value());
            assertEquals(0, tracker.changes().revision());
        }
    }

    @Nested
    @DisplayName("purse")
    class Purse {
        @Test
        @DisplayName("tracks semantic availability and value changes")
        void semanticChanges() {
            var value = new AtomicReference<Optional<Double>>(Optional.empty());
            var tracker = new PurseTracker(value::get);

            tracker.initialize();
            assertEquals(0, tracker.changes().revision());
            assertFalse(tracker.poll());
            value.set(Optional.of(100.0));
            assertTrue(tracker.poll());
            assertFalse(tracker.poll());
            value.set(Optional.empty());
            assertTrue(tracker.poll());
            assertEquals(2, tracker.changes().revision());
        }
    }
}
