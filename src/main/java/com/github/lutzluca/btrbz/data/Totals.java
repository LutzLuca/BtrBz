package com.github.lutzluca.btrbz.data;

/** Full-side totals reported by Hypixel quick status. */
public sealed interface Totals permits Totals.Available, Totals.Unavailable {
    record Available(long orders, long items) implements Totals {}

    record Unavailable() implements Totals {}
}
