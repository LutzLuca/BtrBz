package com.github.lutzluca.btrbz.core.widgets.dailylimit;

import com.github.lutzluca.btrbz.core.widgets.cache.CacheDependencies;
import com.github.lutzluca.btrbz.core.widgets.cache.WidgetDataSource;
import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigHandle;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;

public final class DailyLimitWidgetData implements WidgetDataSource<DailyLimitWidgetData.Snapshot> {
    private final DailyLimitComponent component;
    private final CacheDependencies dependencies;

    public DailyLimitWidgetData(
        DailyLimitComponent component,
        WidgetConfigHandle<DailyLimitWidgetConfig> configHandle
    ) {
        this.component = component;
        this.dependencies = CacheDependencies.of(
            component.dataChanges(), component.utcDayTracker().changes(), configHandle.contentChanges());
    }

    @Override
    public CacheDependencies cacheDependencies() {
        return this.dependencies;
    }

    @Override
    public boolean sessionSensitive() {
        return false;
    }

    @Override
    public Snapshot snapshot(WidgetSession session) {
        var usage = this.component.currentUsage();

        return new Snapshot(Math.round(usage.used()), Math.round(usage.limit()));
    }

    public static Snapshot preview() {
        return new Snapshot(11_250_000_000L, 15_000_000_000L);
    }

    public record Snapshot(long used, long limit) {
        public Snapshot {
            if (used < 0 || limit <= 0) {
                throw new IllegalArgumentException("limit values must be positive");
            }
        }
    }
}
