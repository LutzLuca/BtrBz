package com.github.lutzluca.btrbz.cache;

import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Widget cache primitives")
class CachePrimitivesTest {
    @Nested
    @DisplayName("tokens")
    class Tokens {
        @Test
        @DisplayName("start at zero and retain the latest semantic reason")
        void incrementsAndRetainsReason() {
            var token = CacheToken.named("test");

            Assertions.assertEquals(0, token.revision());
            Assertions.assertNull(token.lastReason());
            token.invalidate("first publication");

            Assertions.assertEquals(1, token.revision());
            Assertions.assertEquals("first publication", token.lastReason());
        }

        @Test
        @DisplayName("are independent by identity")
        void independent() {
            var first = CacheToken.named("same");
            var second = CacheToken.named("same");

            first.invalidate("changed");

            Assertions.assertEquals(1, first.revision());
            Assertions.assertEquals(0, second.revision());
            Assertions.assertNotEquals(first, second);
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

            Assertions.assertEquals(List.of(first, second), dependencies.tokens());
            Assertions.assertThrows(UnsupportedOperationException.class, () -> dependencies.tokens().clear());
        }

        @Test
        @DisplayName("reject null tokens")
        void rejectsNulls() {
            Assertions.assertThrows(NullPointerException.class, () -> CacheDependencies.of((CacheToken) null));
        }

        @Test
        @DisplayName("match compares captured revisions directly")
        void matchesCapturedRevisions() {
            var token = CacheToken.named("direct");
            var dependencies = CacheDependencies.of(token);
            var captured = CacheRevisions.capture(dependencies);

            Assertions.assertTrue(CacheRevisions.match(captured, dependencies));
            token.invalidate("publication");
            Assertions.assertFalse(CacheRevisions.match(captured, dependencies));
            Assertions.assertEquals("direct", CacheRevisions.changes(captured, dependencies).getFirst().tokenName());
        }

        @Test
        @DisplayName("replacement and reordering invalidate equal revisions")
        void detectsChangedIdentities() {
            var first = CacheToken.named("same");
            var second = CacheToken.named("same");
            var captured = CacheRevisions.capture(CacheDependencies.of(first, second));
            var reordered = CacheDependencies.of(second, first);

            Assertions.assertFalse(CacheRevisions.match(captured, reordered));
            Assertions.assertEquals(2, CacheRevisions.changes(captured, reordered).size());

            var replaced = CacheDependencies.of(first, CacheToken.named("same"));
            Assertions.assertFalse(CacheRevisions.match(captured, replaced));
            Assertions.assertEquals(1, CacheRevisions.changes(captured, replaced).size());
        }

        @Test
        @DisplayName("removed dependencies invalidate and explain the miss")
        void detectsRemovedDependencies() {
            var captured = CacheRevisions.capture(CacheDependencies.of(CacheToken.named("removed")));
            var current = CacheDependencies.none();

            Assertions.assertFalse(CacheRevisions.match(captured, current));
            Assertions.assertEquals(
                "dependency removed", CacheRevisions.changes(captured, current).getFirst().reason());
        }
    }
}
