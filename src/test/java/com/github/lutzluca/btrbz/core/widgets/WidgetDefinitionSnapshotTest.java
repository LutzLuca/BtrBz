package com.github.lutzluca.btrbz.core.widgets;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetFrameConfig;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetPlacement;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Widget definition snapshot boundary")
class WidgetDefinitionSnapshotTest {
    @Nested
    @DisplayName("Capture")
    class Capture {
        @Test
        void usesTheRequiredWidgetSpecificDeepCopy() {
            var sourceValues = new ArrayList<>(List.of("before"));
            var source = new MutableData(sourceValues);
            var definition = WidgetDefinition.<MutableData, TestConfig, Void>builder(
                    WidgetId.parse("btrbz:snapshot_test"), "Snapshot Test")
                .config(TestConfig::new, TestConfig::new, value -> value.frame, (current, defaults) -> {})
                .runtimeData(_ -> source)
                .snapshotCopy(data -> new MutableData(List.copyOf(data.values())))
                .preview(() -> null)
                .viewFactory(() -> null)
                .build();

            var captured = definition.captureRuntimePreview(session());
            sourceValues.add("after");

            assertNotSame(source, captured.data());
            assertEquals(List.of("before"), captured.data().values());
            assertThrows(UnsupportedOperationException.class, () -> captured.data().values().add("mutation"));
        }

        @Test
        void rejectsDefinitionsWithoutAnExplicitSnapshotCopy() {
            var builder = WidgetDefinition.<MutableData, TestConfig, Void>builder(
                    WidgetId.parse("btrbz:missing_snapshot"), "Missing Snapshot")
                .config(TestConfig::new, TestConfig::new, value -> value.frame, (current, defaults) -> {})
                .runtimeData(_ -> new MutableData(List.of()))
                .preview(() -> null)
                .viewFactory(() -> null);

            assertThrows(NullPointerException.class, builder::build);
        }
    }

    private static WidgetSession session() {
        return new WidgetSession(
            1, false, false, false,
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 0
        );
    }

    private record MutableData(List<String> values) {}

    private static final class TestConfig {
        private final WidgetFrameConfig frame = new WidgetFrameConfig(WidgetPlacement.topLeft(0, 0));
    }
}
