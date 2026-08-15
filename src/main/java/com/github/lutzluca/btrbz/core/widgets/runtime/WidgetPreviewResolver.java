package com.github.lutzluca.btrbz.core.widgets.runtime;

import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import com.github.lutzluca.btrbz.core.widgets.WidgetPreview;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/** Resolves a partial captured-preview override map with a definition-owned fallback. */
final class WidgetPreviewResolver {
    private WidgetPreviewResolver() {}

    static WidgetPreview<?> resolve(
        WidgetId id,
        @Nullable Map<WidgetId, WidgetPreview<?>> capturedPreviews,
        Supplier<? extends WidgetPreview<?>> fallback
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(fallback, "fallback");
        var captured = capturedPreviews == null ? null : capturedPreviews.get(id);
        return captured == null
            ? Objects.requireNonNull(fallback.get(), "fallback widget preview")
            : captured;
    }
}
