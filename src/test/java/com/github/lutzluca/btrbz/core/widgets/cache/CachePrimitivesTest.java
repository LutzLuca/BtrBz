package com.github.lutzluca.btrbz.core.widgets.cache;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Widget cache primitives")
class CachePrimitivesTest {
    @Nested
    @DisplayName("tokens")
    class Tokens {
        @Test
        @DisplayName("start at zero and retain the latest semantic reason")
        void incrementsAndRetainsReason() {
            var token = CacheToken.named("test");

            assertEquals(0, token.revision());
            assertNull(token.lastReason());
            token.invalidate(InvalidationReason.of("first publication"));

            assertEquals(1, token.revision());
            assertEquals("first publication", token.lastReason().description());
        }

        @Test
        @DisplayName("are independent by identity")
        void independent() {
            var first = CacheToken.named("same");
            var second = CacheToken.named("same");

            first.invalidate(InvalidationReason.of("changed"));

            assertEquals(1, first.revision());
            assertEquals(0, second.revision());
            assertNotEquals(first, second);
        }
    }

    @Nested
    @DisplayName("dependencies")
    class Dependencies {
        @Test
        @DisplayName("deduplicate identity while preserving stable order")
        void deduplicatesInOrder() {
            var first = CacheToken.named("first");
            var second = CacheToken.named("second");

            var dependencies = CacheDependencies.of(first, second, first);

            assertEquals(List.of(first, second), dependencies.tokens());
            assertThrows(UnsupportedOperationException.class, () -> dependencies.tokens().clear());
        }

        @Test
        @DisplayName("reject null tokens")
        void rejectsNulls() {
            assertThrows(NullPointerException.class, () -> CacheDependencies.of((CacheToken) null));
        }

        @Test
        @DisplayName("match compares captured arrays directly")
        void matchesCapturedRevisions() {
            var token = CacheToken.named("direct");
            var dependencies = CacheDependencies.of(token);
            var captured = CacheRevisions.capture(dependencies);

            assertTrue(CacheRevisions.match(captured, dependencies));
            token.invalidate(InvalidationReason.of("publication"));
            assertFalse(CacheRevisions.match(captured, dependencies));
            assertEquals("direct", CacheRevisions.changes(captured, dependencies).getFirst().tokenName());
        }
    }
}
