package com.github.lutzluca.btrbz.core.widgets.cache;

import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/** Shares one successful immutable source snapshot across definitions and hosts. */
public final class MemoizedWidgetDataSource<D> implements WidgetDataSource<D> {
    private final WidgetDataSource<D> source;
    private long sessionId = Long.MIN_VALUE;
    private long sessionContextRevision = Long.MIN_VALUE;
    private long[] dependencyRevisions = new long[0];
    private @Nullable D cached;

    public MemoizedWidgetDataSource(WidgetDataSource<D> source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    @Override
    public CacheDependencies cacheDependencies() {
        return this.source.cacheDependencies();
    }

    @Override
    public boolean sessionSensitive() {
        return this.source.sessionSensitive();
    }

    @Override
    public D snapshot(WidgetSession session) {
        var dependencies = this.source.cacheDependencies();
        boolean sessionMatches = !this.source.sessionSensitive()
            || this.sessionId == session.id()
                && this.sessionContextRevision == session.contextRevision();
        var current = this.cached;
        if (current != null && sessionMatches
            && CacheRevisions.match(this.dependencyRevisions, dependencies)) {
            return current;
        }

        D computed = Objects.requireNonNull(this.source.snapshot(session), "widget data snapshot");
        long[] revisions = CacheRevisions.capture(dependencies);
        this.sessionId = session.id();
        this.sessionContextRevision = session.contextRevision();
        this.dependencyRevisions = revisions;
        this.cached = computed;
        return computed;
    }
}
