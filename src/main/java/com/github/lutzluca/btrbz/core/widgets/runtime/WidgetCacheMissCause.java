package com.github.lutzluca.btrbz.core.widgets.runtime;

import com.github.lutzluca.btrbz.core.widgets.cache.CacheRevisions.ChangedDependency;
import org.jetbrains.annotations.Nullable;

/** One bounded, miss-only explanation for a prepared-widget recomputation. */
public record WidgetCacheMissCause(String description, @Nullable ChangedDependency dependency) {
    public static WidgetCacheMissCause direct(String description) {
        return new WidgetCacheMissCause(description, null);
    }

    public static WidgetCacheMissCause dependency(ChangedDependency dependency) {
        return new WidgetCacheMissCause("cache token changed", dependency);
    }
}
