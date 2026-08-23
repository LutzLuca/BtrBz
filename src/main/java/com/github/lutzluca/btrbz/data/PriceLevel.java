package com.github.lutzluca.btrbz.data;

/** Presentation-safe order-book level from one Hypixel publication. */
public record PriceLevel(double price, long items, int orders) {}
