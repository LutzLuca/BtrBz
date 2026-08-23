package com.github.lutzluca.btrbz.data;

/** Full-side totals reported by Hypixel quick status. */
public sealed interface Totals permits Totals.Available, Totals.Unavailable {
    record Available(long orders, long items) implements Totals {
        public Available {
            if (orders < 0 || items < 0) {
                throw new IllegalArgumentException("market totals must not be negative");
            }
        }
    }

    record Unavailable() implements Totals {}
}
