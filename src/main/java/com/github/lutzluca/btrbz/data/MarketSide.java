package com.github.lutzluca.btrbz.data;

import java.util.List;
import java.util.Objects;

/** Immutable levels and exact totals for one visible market side. */
public record MarketSide(List<PriceLevel> levels, Totals totals) {
    public MarketSide {
        levels = List.copyOf(Objects.requireNonNull(levels, "levels"));
        totals = Objects.requireNonNull(totals, "totals");
    }
}
