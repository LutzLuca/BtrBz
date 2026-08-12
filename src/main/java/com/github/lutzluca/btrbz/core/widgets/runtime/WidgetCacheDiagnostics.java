package com.github.lutzluca.btrbz.core.widgets.runtime;

import java.util.List;

/** Lightweight prepared-cache counters and the latest bounded miss explanation. */
public record WidgetCacheDiagnostics(
    long hits,
    long misses,
    long coldMisses,
    List<WidgetCacheMissCause> lastMissCauses
) {
    public WidgetCacheDiagnostics {
        lastMissCauses = List.copyOf(lastMissCauses);
    }
}
