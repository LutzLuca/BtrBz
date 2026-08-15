package com.github.lutzluca.coflnet;

/** One aggregated order-book level from a Coflnet bazaar snapshot. */
public record BazaarOrder(int amount, double pricePerUnit, int orders) {}
