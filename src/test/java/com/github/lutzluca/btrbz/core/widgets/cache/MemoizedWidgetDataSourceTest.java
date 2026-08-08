package com.github.lutzluca.btrbz.core.widgets.cache;

import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Memoized widget data source")
class MemoizedWidgetDataSourceTest {
    @Nested
    @DisplayName("dependency matching")
    class DependencyMatching {
        @Test
        @DisplayName("computes once and returns the identical snapshot until a token changes")
        void tokenDrivenReuse() {
            var source = new TestSource(false);
            var memoized = new MemoizedWidgetDataSource<>(source);
            var session = session(1, 1);

            var first = memoized.snapshot(session);
            var second = memoized.snapshot(session);
            source.token.invalidate(InvalidationReason.of("source changed"));
            var third = memoized.snapshot(session);

            assertSame(first, second);
            assertNotSame(first, third);
            assertEquals(2, source.calls.get());
        }

        @Test
        @DisplayName("session-independent sources remain shared across semantic and context changes")
        void independentAcrossSessions() {
            var source = new TestSource(false);
            var memoized = new MemoizedWidgetDataSource<>(source);

            var first = memoized.snapshot(session(1, 1));
            var second = memoized.snapshot(session(2, 3));

            assertSame(first, second);
            assertEquals(1, source.calls.get());
        }

        @Test
        @DisplayName("session-sensitive sources recompute for semantic or context-only changes")
        void sensitiveToBothSessionStamps() {
            var source = new TestSource(true);
            var memoized = new MemoizedWidgetDataSource<>(source);

            memoized.snapshot(session(1, 1));
            memoized.snapshot(session(2, 1));
            memoized.snapshot(session(2, 2));

            assertEquals(3, source.calls.get());
        }

        @Test
        @DisplayName("a failed recomputation does not stamp current revisions and the next call retries")
        void failureDoesNotBecomeCurrent() {
            var source = new TestSource(false);
            var memoized = new MemoizedWidgetDataSource<>(source);
            var session = session(1, 1);
            var successful = memoized.snapshot(session);
            source.token.invalidate(InvalidationReason.of("source changed"));
            source.fail.set(true);

            assertThrows(IllegalStateException.class, () -> memoized.snapshot(session));
            source.fail.set(false);
            var retried = memoized.snapshot(session);

            assertNotSame(successful, retried);
            assertEquals(3, source.calls.get());
        }
    }

    private static WidgetSession session(long id, long contextRevision) {
        return new WidgetSession(
            id, true, false, false,
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), contextRevision
        );
    }

    private static final class TestSource implements WidgetDataSource<Object> {
        private final CacheToken token = CacheToken.named("test-source");
        private final CacheDependencies dependencies = CacheDependencies.of(this.token);
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicBoolean fail = new AtomicBoolean();
        private final boolean sessionSensitive;

        private TestSource(boolean sessionSensitive) {
            this.sessionSensitive = sessionSensitive;
        }

        @Override public CacheDependencies cacheDependencies() { return this.dependencies; }
        @Override public boolean sessionSensitive() { return this.sessionSensitive; }

        @Override
        public Object snapshot(WidgetSession session) {
            this.calls.incrementAndGet();
            if (this.fail.get()) throw new IllegalStateException("expected failure");
            return new Object();
        }
    }
}
