package com.github.lutzluca.coflnet;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** The current Coflnet bazaar snapshot response. */
public record BazaarSnapshot(
    String productId,
    double buyPrice,
    long buyVolume,
    long buyMovingWeek,
    int buyOrdersCount,
    double sellPrice,
    long sellVolume,
    long sellMovingWeek,
    int sellOrdersCount,
    Instant timeStamp,
    List<BazaarOrder> buyOrders,
    List<BazaarOrder> sellOrders
) {
    public BazaarSnapshot {
        Objects.requireNonNull(timeStamp, "timeStamp");
        buyOrders = buyOrders == null ? List.of() : List.copyOf(buyOrders);
        sellOrders = sellOrders == null ? List.of() : List.copyOf(sellOrders);
    }
}
