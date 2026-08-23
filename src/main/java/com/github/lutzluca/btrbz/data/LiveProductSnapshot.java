package com.github.lutzluca.btrbz.data;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Presentation-safe live market data derived from one Hypixel publication. */
public record LiveProductSnapshot(
    ProductIdentity product,
    Optional<Instant> lastUpdated,
    MarketSide buyOrders,
    MarketSide sellOffers,
    boolean marketDataAvailable
) {
    public LiveProductSnapshot {
        product = Objects.requireNonNull(product, "product");
        lastUpdated = Objects.requireNonNull(lastUpdated, "lastUpdated");
        buyOrders = Objects.requireNonNull(buyOrders, "buyOrders");
        sellOffers = Objects.requireNonNull(sellOffers, "sellOffers");
    }

    public LiveProductSnapshot(
        ProductIdentity product,
        Optional<Instant> lastUpdated,
        MarketSide buyOrders,
        MarketSide sellOffers
    ) {
        this(product, lastUpdated, buyOrders, sellOffers,
            !buyOrders.levels().isEmpty() || !sellOffers.levels().isEmpty()
                || buyOrders.totals() instanceof Totals.Available
                || sellOffers.totals() instanceof Totals.Available);
    }

    public Optional<Double> buyPrice() {
        return this.sellOffers.levels().stream().findFirst().map(PriceLevel::price);
    }

    public Optional<Double> sellPrice() {
        return this.buyOrders.levels().stream().findFirst().map(PriceLevel::price);
    }
}
