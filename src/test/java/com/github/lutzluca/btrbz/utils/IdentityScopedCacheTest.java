package com.github.lutzluca.btrbz.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class IdentityScopedCacheTest {

    @Nested
    @DisplayName("cached resolutions")
    class CachedResolutions {

        @Test
        void cachesSuccessfulValuesWithinTheSameScopes() {
            var cache = new IdentityScopedCache<String, Object>();
            var primaryScope = new Object();
            var secondaryScope = new Object();
            var calls = new AtomicInteger();
            var value = new Object();

            var first = cache.getOrResolve(primaryScope, secondaryScope, "product", () -> {
                calls.incrementAndGet();
                return Optional.of(value);
            });
            var second = cache.getOrResolve(primaryScope, secondaryScope, "product", () -> {
                calls.incrementAndGet();
                return Optional.of(new Object());
            });

            assertSame(value, first.orElseThrow());
            assertSame(value, second.orElseThrow());
            assertEquals(1, calls.get());
        }

        @Test
        void cachesAnEmptyResolutionWithinTheSameScopes() {
            var cache = new IdentityScopedCache<String, Object>();
            var primaryScope = new Object();
            var secondaryScope = new Object();
            var calls = new AtomicInteger();

            for (int attempt = 0; attempt < 2; attempt++) {
                assertTrue(cache.getOrResolve(primaryScope, secondaryScope, "product", () -> {
                    calls.incrementAndGet();
                    return Optional.empty();
                }).isEmpty());
            }

            assertEquals(1, calls.get());
        }
    }

    @Nested
    @DisplayName("scope invalidation")
    class ScopeInvalidation {

        @Test
        void invalidatesWhenEitherScopeIdentityChanges() {
            var cache = new IdentityScopedCache<String, Integer>();
            var firstPrimary = new String("index");
            var secondPrimary = new String("index");
            var firstSecondary = new String("registries");
            var secondSecondary = new String("registries");
            var calls = new AtomicInteger();

            assertEquals(1, cache.getOrResolve(
                firstPrimary,
                firstSecondary,
                "product",
                () -> Optional.of(calls.incrementAndGet())
            ).orElseThrow());
            assertEquals(2, cache.getOrResolve(
                secondPrimary,
                firstSecondary,
                "product",
                () -> Optional.of(calls.incrementAndGet())
            ).orElseThrow());
            assertEquals(3, cache.getOrResolve(
                secondPrimary,
                secondSecondary,
                "product",
                () -> Optional.of(calls.incrementAndGet())
            ).orElseThrow());
        }
    }
}
