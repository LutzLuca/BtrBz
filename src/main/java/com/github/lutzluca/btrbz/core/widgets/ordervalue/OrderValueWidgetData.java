package com.github.lutzluca.btrbz.core.widgets.ordervalue;

public final class OrderValueWidgetData {
    private final OrderValueComponent component;

    public OrderValueWidgetData(OrderValueComponent component) {
        this.component = component;
    }

    public Snapshot snapshot() {
        var value = this.component.currentBreakdown();
        return new Snapshot(
            Math.round(value.buyLocked()), Math.round(value.buyItems()), Math.round(value.sellClaimable()),
            Math.round(value.sellPending()), Math.round(value.total())
        );
    }

    public static Snapshot preview() {
        return new Snapshot(24_700_000, 8_400_000, 11_200_000, 6_800_000, 51_100_000);
    }

    public record Snapshot(long buyLocked, long buyItems, long sellClaimable, long sellPending, long total) {}
}
