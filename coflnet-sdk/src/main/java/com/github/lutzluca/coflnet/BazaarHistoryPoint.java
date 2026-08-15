package com.github.lutzluca.coflnet;

import java.time.Instant;
import java.util.Objects;

/** One API-shaped point in a Coflnet bazaar history response. */
public record BazaarHistoryPoint(
    double buy,
    double sell,
    Double minBuy,
    Double maxBuy,
    Double minSell,
    Double maxSell,
    long buyVolume,
    long sellVolume,
    long buyMovingWeek,
    long sellMovingWeek,
    Instant timestamp
) {
    public BazaarHistoryPoint {
        Objects.requireNonNull(timestamp, "timestamp");
    }
}
