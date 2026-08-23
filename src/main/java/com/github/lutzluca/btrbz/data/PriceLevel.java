package com.github.lutzluca.btrbz.data;

/** Presentation-safe order-book level from one Hypixel publication. */
public record PriceLevel(double price, long items, int orders) {
    public PriceLevel {
        if (!Double.isFinite(price) || price <= 0) {
            throw new IllegalArgumentException("price must be finite and positive");
        }
        if (items < 0) {
            throw new IllegalArgumentException("items must not be negative");
        }
        if (orders < 0) {
            throw new IllegalArgumentException("orders must not be negative");
        }
    }
}
