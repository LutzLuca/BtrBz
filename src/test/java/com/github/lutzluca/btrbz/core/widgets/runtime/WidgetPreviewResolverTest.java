package com.github.lutzluca.btrbz.core.widgets.runtime;

import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import com.github.lutzluca.btrbz.core.widgets.WidgetPreview;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetPreviewSessions;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

@DisplayName("Widget preview resolution")
class WidgetPreviewResolverTest {
    private static final WidgetId WIDGET = WidgetId.parse("test:widget");

    @Nested
    @DisplayName("Captured overrides")
    class CapturedOverrides {
        @Test
        @DisplayName("win without evaluating the sample fallback")
        void capturedPreviewWins() {
            var captured = preview("captured");
            var fallbackCalls = new AtomicInteger();

            var resolved = WidgetPreviewResolver.resolve(
                WIDGET,
                Map.of(WIDGET, captured),
                () -> {
                    fallbackCalls.incrementAndGet();
                    return preview("sample");
                }
            );

            assertSame(captured, resolved);
            assertEquals(0, fallbackCalls.get());
        }
    }

    @Nested
    @DisplayName("Sample fallback")
    class SampleFallback {
        @Test
        @DisplayName("is used when the partial override map lacks the widget")
        void missingOverrideUsesSample() {
            var sample = preview("sample");

            var resolved = WidgetPreviewResolver.resolve(WIDGET, Map.of(), () -> sample);

            assertSame(sample, resolved);
        }

        @Test
        @DisplayName("is used when no contextual override map exists")
        void absentOverrideMapUsesSample() {
            var sample = preview("sample");

            var resolved = WidgetPreviewResolver.resolve(WIDGET, null, () -> sample);

            assertSame(sample, resolved);
        }
    }

    private static WidgetPreview<String> preview(String data) {
        return new WidgetPreview<>(data, WidgetPreviewSessions.hud(), "default");
    }
}
