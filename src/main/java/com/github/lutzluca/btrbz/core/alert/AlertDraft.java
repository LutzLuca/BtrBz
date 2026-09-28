package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.core.alert.AlertCondition.LiquiditySide;
import com.github.lutzluca.btrbz.data.BazaarData.MarketPrices;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import io.vavr.control.Try;

/** Unresolved input, separate from the fixed condition captured on save. */
public sealed interface AlertDraft {
    Try<AlertDefinition> resolve(MarketPrices prices, long timestamp);

    record Price(IndexedProduct product, AlertType type, String input) implements AlertDraft {
        @Override
        public Try<AlertDefinition> resolve(MarketPrices prices, long timestamp) {
            return PriceExpressionParser.parse(this.input)
                .flatMap(parsed -> parsed.resolve(prices))
                .map(price -> new AlertDefinition(timestamp, this.product, new AlertCondition.Price(this.type, price)))
                .flatMap(AlertDefinition::validate);
        }
    }

    record Liquidity(IndexedProduct product, LiquiditySide side, String quantity, String input) implements AlertDraft {
        @Override
        public Try<AlertDefinition> resolve(MarketPrices prices, long timestamp) {
            return Try.of(() -> parseQuantity(this.quantity))
                .flatMap(required -> PriceExpressionParser.parse(this.input)
                    .flatMap(parsed -> parsed.resolve(prices))
                    .map(price -> new AlertDefinition(timestamp, this.product,
                        new AlertCondition.Liquidity(this.side, required, price))))
                .flatMap(AlertDefinition::validate);
        }
    }

    private static long parseQuantity(String input) {
        if (input == null || !input.trim().matches("[0-9]+")) {
            throw new IllegalArgumentException("Enter a positive whole-number quantity");
        }
        try {
            long value = Long.parseLong(input.trim());
            if (value <= 0) {
                throw new IllegalArgumentException("Enter a positive whole-number quantity");
            }
            return value;
        } catch (NumberFormatException err) {
            throw new IllegalArgumentException("Quantity is too large", err);
        }
    }
}
