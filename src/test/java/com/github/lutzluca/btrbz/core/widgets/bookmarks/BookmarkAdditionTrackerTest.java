package com.github.lutzluca.btrbz.core.widgets.bookmarks;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Bookmark addition tracker")
class BookmarkAdditionTrackerTest {
    @Nested
    @DisplayName("retained updates")
    class RetainedUpdates {
        @Test
        @DisplayName("ignores the initial snapshot")
        void ignoresInitialSnapshot() {
            var tracker = new BookmarkAdditionTracker();

            assertFalse(tracker.update(List.of("A", "B")));
        }

        @Test
        @DisplayName("detects a newly added product")
        void detectsAddition() {
            var tracker = new BookmarkAdditionTracker();
            tracker.update(List.of("A", "B"));

            assertTrue(tracker.update(List.of("A", "B", "C")));
        }

        @Test
        @DisplayName("ignores removal and reordering")
        void ignoresRemovalAndReordering() {
            var tracker = new BookmarkAdditionTracker();
            tracker.update(List.of("A", "B", "C"));

            assertFalse(tracker.update(List.of("C", "A")));
        }
    }
}
