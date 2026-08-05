package com.github.lutzluca.btrbz.core.widgets.ordervalue;

import org.jetbrains.annotations.Nullable;

public final class OrderValueWidgetData {
    private final OrderValueComponent component;
    private long cachedRevision = Long.MIN_VALUE;
    private @Nullable Snapshot cachedSnapshot;

    public OrderValueWidgetData(OrderValueComponent component) {
        this.component = component;
    }

    public Snapshot snapshot() {
        long revision = this.component.dataRevision();
        var cached = this.cachedSnapshot;
        if (cached != null && revision == this.cachedRevision) {
            return cached;
        }

        var value = this.component.currentBreakdown();
        var computed = new Snapshot(
            Math.round(value.buyLocked()), Math.round(value.buyItems()),
            Math.round(value.sellClaimable()), Math.round(value.sellPending()),
            Math.round(value.total())
        );
        this.cachedRevision = revision;
        this.cachedSnapshot = computed;
        return computed;
    }

    public static Snapshot preview() {
        return new Snapshot(24_700_000, 8_400_000, 11_200_000, 6_800_000, 51_100_000);
    }

    public record Snapshot(long buyLocked, long buyItems, long sellClaimable, long sellPending, long total) {}
}
