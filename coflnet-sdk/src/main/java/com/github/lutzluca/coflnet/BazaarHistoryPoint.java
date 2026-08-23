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
        requirePositiveFinite(buy, "buy");
        requirePositiveFinite(sell, "sell");
        minBuy = optionalPositiveFinite(minBuy, "minBuy");
        maxBuy = optionalPositiveFinite(maxBuy, "maxBuy");
        minSell = optionalPositiveFinite(minSell, "minSell");
        maxSell = optionalPositiveFinite(maxSell, "maxSell");
        requireOrdered(minBuy, maxBuy, "buy");
        requireOrdered(minSell, maxSell, "sell");
        requireNonNegative(buyVolume, "buyVolume");
        requireNonNegative(sellVolume, "sellVolume");
        requireNonNegative(buyMovingWeek, "buyMovingWeek");
        requireNonNegative(sellMovingWeek, "sellMovingWeek");
    }

    private static void requirePositiveFinite(double value, String name) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    private static Double optionalPositiveFinite(Double value, String name) {
        if (value != null) {
            requirePositiveFinite(value, name);
        }
        return value;
    }

    private static void requireOrdered(Double minimum, Double maximum, String name) {
        if (minimum != null && maximum != null && minimum > maximum) {
            throw new IllegalArgumentException(name + " range minimum must not exceed its maximum");
        }
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
