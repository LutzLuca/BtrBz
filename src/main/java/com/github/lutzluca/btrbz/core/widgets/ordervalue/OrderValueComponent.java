package com.github.lutzluca.btrbz.core.widgets.ordervalue;

import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo.FilledOrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo.UnfilledOrderInfo;
import java.util.List;

/** Owns the latest order facts and the established exact value calculation. */
public final class OrderValueComponent {
    private List<UnfilledOrderInfo> unfilledOrders = List.of();
    private List<FilledOrderInfo> filledOrders = List.of();
    private long dataRevision;

    public long dataRevision() {
        return this.dataRevision;
    }

    public void sync(List<UnfilledOrderInfo> unfilledOrders, List<FilledOrderInfo> filledOrders) {
        this.unfilledOrders = List.copyOf(unfilledOrders);
        this.filledOrders = List.copyOf(filledOrders);
        this.dataRevision++;
    }

    public void clear() {
        this.unfilledOrders = List.of();
        this.filledOrders = List.of();
        this.dataRevision++;
    }

    public Breakdown currentBreakdown() {
        return calculateBreakdown(this.unfilledOrders, this.filledOrders);
    }

    public static Breakdown calculateBreakdown(
        List<UnfilledOrderInfo> unfilledOrders,
        List<FilledOrderInfo> filledOrders
    ) {
        double buyLocked = 0;
        double buyItems = 0;
        double sellClaimable = 0;
        double sellPending = 0;
        for (var order : unfilledOrders) {
            int remaining = order.volume() - order.filledAmountSnapshot();
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
        for (var order : filledOrders) {
            switch (order.type()) {
                case Buy -> buyItems += order.unclaimed() * order.pricePerUnit();
                case Sell -> sellClaimable += order.unclaimed();
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
