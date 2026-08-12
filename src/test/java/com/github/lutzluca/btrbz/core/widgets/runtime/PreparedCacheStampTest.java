package com.github.lutzluca.btrbz.core.widgets.runtime;

import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import com.github.lutzluca.btrbz.core.widgets.cache.CacheDependencies;
import com.github.lutzluca.btrbz.core.widgets.cache.CacheToken;
import com.github.lutzluca.btrbz.core.widgets.cache.InvalidationReason;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetCanvas;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Prepared widget cache stamps")
class PreparedCacheStampTest {
    private final CacheToken relevant = CacheToken.named("relevant");
    private final CacheDependencies dependencies = CacheDependencies.of(this.relevant);
    private final WidgetCanvas canvas = new WidgetCanvas(10, 20, 300, 200);
    private final WidgetHostOptions options = WidgetHostOptions.runtime(true);
    private final WidgetSession session = session(1, 2);

    @Nested
    @DisplayName("matching")
    class Matching {
        @Test
        @DisplayName("identical inputs hit and unrelated tokens remain hits")
        void identicalAndUnrelated() {
            var stamp = PreparedCacheStamp.capture(session, canvas, options, "default", dependencies);
            var unrelated = CacheToken.named("unrelated");
            unrelated.invalidate(InvalidationReason.of("not consumed"));

            assertTrue(stamp.matches(session, canvas, options, "default", dependencies));
        }

        @Test
        @DisplayName("every universal host and session input misses independently")
        void universalInputs() {
            var stamp = PreparedCacheStamp.capture(session, canvas, options, "default", dependencies);

            assertFalse(stamp.matches(session(2, 2), canvas, options, "default", dependencies));
            assertFalse(stamp.matches(session(1, 3), canvas, options, "default", dependencies));
            assertFalse(stamp.matches(session, new WidgetCanvas(11, 20, 300, 200), options, "default", dependencies));
            assertFalse(stamp.matches(session, new WidgetCanvas(10, 20, 301, 200), options, "default", dependencies));
            assertFalse(stamp.matches(session, canvas, WidgetHostOptions.runtime(false), "default", dependencies));
            assertFalse(stamp.matches(session, canvas, options, "sign", dependencies));
        }

        @Test
        @DisplayName("relevant token changes miss and retain owner diagnostics")
        void relevantDependency() {
            var stamp = PreparedCacheStamp.capture(session, canvas, options, "default", dependencies);
            relevant.invalidate(InvalidationReason.of("published value changed"));

            assertFalse(stamp.matches(session, canvas, options, "default", dependencies));
            var cause = stamp.missCauses(session, canvas, options, "default", dependencies).getFirst();
            assertEquals("relevant", cause.dependency().tokenName());
            assertEquals("published value changed", cause.dependency().reason().description());
        }
    }

    @Test
    @DisplayName("direct miss diagnostics name every changed host field")
    void directDiagnostics() {
        var stamp = PreparedCacheStamp.capture(session, canvas, options, "default", dependencies);
        var changedOptions = WidgetHostOptions.management(
            WidgetId.parse("btrbz:test"), Set.of(), Map.of()
        );
        var causes = stamp.missCauses(
            session(2, 3), new WidgetCanvas(11, 21, 301, 201),
            changedOptions, "sign", dependencies
        ).stream().map(WidgetCacheMissCause::description).toList();

        assertTrue(causes.contains("semantic session changed"));
        assertTrue(causes.contains("session context changed"));
        assertTrue(causes.contains("canvas origin changed"));
        assertTrue(causes.contains("canvas size changed"));
        assertTrue(causes.contains("host options changed"));
        assertTrue(causes.contains("placement profile changed"));
    }

    private static WidgetSession session(long id, long contextRevision) {
        return new WidgetSession(
            id, true, false, false,
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), contextRevision
        );
    }
}
