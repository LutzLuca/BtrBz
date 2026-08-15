package com.github.lutzluca.btrbz.core.widgets.cache;

import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;

/** Immutable snapshot producer with explicit cache dependencies. */
public interface WidgetDataSource<D> {
    CacheDependencies cacheDependencies();

    D snapshot(WidgetSession session);

    default boolean sessionSensitive() {
        return true;
    }
}
