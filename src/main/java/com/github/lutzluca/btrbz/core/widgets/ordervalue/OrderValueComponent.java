package com.github.lutzluca.btrbz.core.widgets.ordervalue;

import com.github.lutzluca.btrbz.cache.CacheToken;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo;
import java.util.List;
import lombok.Getter;
import lombok.experimental.Accessors;

/** Owns the latest order facts and the value estimate based on their lore. */
public final class OrderValueComponent {
    private List<OrderInfo> orders = List.of();
    @Getter
    @Accessors(fluent = true)
    private final CacheToken dataChanges = CacheToken.named("order-value.data");

    public void sync(List<OrderInfo> orders) {
        this.orders = List.copyOf(orders);
        this.dataChanges.invalidate("order values synchronized");
    }

    public void clear() {
        this.orders = List.of();
        this.dataChanges.invalidate("order values cleared");
    }

    public Breakdown currentBreakdown() {
        return calculateBreakdown(this.orders);
    }

    public boolean hasOrders() {
        return !this.orders.isEmpty();
    }

    public static Breakdown calculateBreakdown(List<OrderInfo> orders) {
        double buyLocked = 0;
        double buyItems = 0;
        double sellClaimable = 0;
        double sellPending = 0;

        for (var order : orders) {
            int remaining = order instanceof OrderInfo.UnfilledOrderInfo
                ? order.volume() - order.filledAmountSnapshot() : 0;
            switch (order.type()) {
                case Buy -> {
                    buyLocked += remaining * order.pricePerUnit();
                    buyItems += order.unclaimed() * order.pricePerUnit();
                }
                case Sell -> {
                    sellPending += remaining * order.pricePerUnit();
                    sellClaimable += order.unclaimed();
                }
            }
        }

        return new Breakdown(buyLocked, buyItems, sellClaimable, sellPending);
    }

    public record Breakdown(double buyLocked, double buyItems, double sellClaimable, double sellPending) {
        public double total() {
            return this.buyLocked + this.buyItems + this.sellClaimable + this.sellPending;
        }
    }
}
