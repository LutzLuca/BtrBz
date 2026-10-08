package com.github.lutzluca.btrbz.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TimedStoreTest {

    @Nested
    @DisplayName("non-expiry behavior")
    class NonExpiryBehavior {

        @Test
        void addAndItemsExposeInsertedItems() {
            var store = new TimedStore<String>(60_000L);
            store.add("first");
            store.add("second");

            assertIterableEquals(List.of("first", "second"), store.items());
        }

        @Test
        void removeFirstMatchRemovesMatchingItem() {
            var store = new TimedStore<String>(60_000L);
            store.add("alpha");
            store.add("beta");
            store.add("gamma");

            var removed = store.removeFirstMatch("beta"::equals);

            assertEquals(Optional.of("beta"), removed);
            assertIterableEquals(List.of("alpha", "gamma"), store.items());
        }

        @Test
        void removeFirstMatchReturnsEmptyWhenNothingMatches() {
            var store = new TimedStore<String>(60_000L);
            store.add("alpha");
            store.add("beta");

            var removed = store.removeFirstMatch("gamma"::equals);

            assertEquals(Optional.empty(), removed);
            assertIterableEquals(List.of("alpha", "beta"), store.items());
        }

        @Test
        void removeFirstMatchOnlyRemovesTheFirstMatch() {
            var store = new TimedStore<String>(60_000L);
            store.add("match");
            store.add("keep");
            store.add("match");

            var removed = store.removeFirstMatch("match"::equals);

            assertEquals(Optional.of("match"), removed);
            assertIterableEquals(List.of("keep", "match"), store.items());
        }

        @Test
        void itemsReturnsSnapshotCopy() {
            var store = new TimedStore<String>(60_000L);
            store.add("first");

            var snapshot = store.items();
            store.add("second");

            assertIterableEquals(List.of("first"), snapshot);
            assertIterableEquals(List.of("first", "second"), store.items());
        }

        @Test
        void rejectsNonPositiveTimeToLive() {
            assertThrows(IllegalArgumentException.class, () -> new TimedStore<String>(0L));
            assertThrows(IllegalArgumentException.class, () -> new TimedStore<String>(-1L));
        }

        @Test
        void rejectsNullClock() {
            assertThrows(NullPointerException.class, () -> new TimedStore<String>(100L, null));
        }
    }

    @Nested
    @DisplayName("expiry behavior")
    class ExpiryBehavior {

        private final AtomicLong now = new AtomicLong();

        @Test
        void itemsStopReturningEntriesAfterExpiry() {
            var store = new TimedStore<String>(100L, this.now::get);
            store.add("alpha");

            assertIterableEquals(List.of("alpha"), store.items());

            this.now.set(101L);

            assertTrue(store.items().isEmpty());
        }

        @Test
        void removeFirstMatchSkipsExpiredEntries() {
            var store = new TimedStore<String>(100L, this.now::get);
            store.add("expired");
            this.now.set(150L);
            store.add("fresh");

            var removed = store.removeFirstMatch(value -> true);

            assertEquals(Optional.of("fresh"), removed);
            assertTrue(store.items().isEmpty());
        }

    }
}
