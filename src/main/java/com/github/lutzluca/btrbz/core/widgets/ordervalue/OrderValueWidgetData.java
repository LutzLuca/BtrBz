package com.github.lutzluca.btrbz.core.widgets.ordervalue;

import com.github.lutzluca.btrbz.core.widgets.cache.CacheDependencies;
import com.github.lutzluca.btrbz.core.widgets.cache.WidgetDataSource;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;

public final class OrderValueWidgetData implements WidgetDataSource<OrderValueWidgetData.Snapshot> {
    private final OrderValueComponent component;
    private final CacheDependencies dependencies;

    public OrderValueWidgetData(OrderValueComponent component) {
        this.component = component;
        this.dependencies = CacheDependencies.of(component.dataChanges());
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
        var value = this.component.currentBreakdown();

        return new Snapshot(
            Math.round(value.buyLocked()), Math.round(value.buyItems()),
            Math.round(value.sellClaimable()), Math.round(value.sellPending()),
            Math.round(value.total())
        );
    }

    public static Snapshot preview() {
        return new Snapshot(24_700_000, 8_400_000, 11_200_000, 6_800_000, 51_100_000);
    }

    public record Snapshot(long buyLocked, long buyItems, long sellClaimable, long sellPending, long total) {}
}
