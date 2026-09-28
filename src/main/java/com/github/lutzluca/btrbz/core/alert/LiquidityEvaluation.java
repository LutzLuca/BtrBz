package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.core.alert.AlertCondition.LiquiditySide;

import java.util.List;

/** Pure bounded sum of item amounts, independent of order-book widgets. */
public final class LiquidityEvaluation {

    private LiquidityEvaluation() {}

    public record Level(double pricePerItem, long items) {}

    public static long qualifyingQuantity(LiquiditySide side, double bound, List<Level> levels) {
        long total = 0;
        for (var level : levels) {
            if (level.items() <= 0 || !Double.isFinite(level.pricePerItem())
                || !qualifies(side, level.pricePerItem(), bound)) {
                continue;
            }
            if (Long.MAX_VALUE - total < level.items()) {
                return Long.MAX_VALUE;
            }
            total += level.items();
        }
        return total;
    }

    public static boolean qualifies(LiquiditySide side, double pricePerItem, double bound) {
        return switch (side) {
            case BuyOrders -> pricePerItem >= bound;
            case SellOffers -> pricePerItem <= bound;
        };
    }
}
