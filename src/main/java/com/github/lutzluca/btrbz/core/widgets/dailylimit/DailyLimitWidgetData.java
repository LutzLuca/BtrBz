package com.github.lutzluca.btrbz.core.widgets.dailylimit;

public final class DailyLimitWidgetData {
    private final DailyLimitComponent component;

    public DailyLimitWidgetData(DailyLimitComponent component) {
        this.component = component;
    }

    public Snapshot snapshot() {
        var usage = this.component.currentUsage();
        return new Snapshot(Math.round(usage.used()), Math.round(usage.limit()));
    }

    public static Snapshot preview() {
        return new Snapshot(11_250_000_000L, 15_000_000_000L);
    }

    public record Snapshot(long used, long limit) {
        public Snapshot {
            if (used < 0 || limit <= 0) throw new IllegalArgumentException("limit values must be positive");
        }
    }
}
