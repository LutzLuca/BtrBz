package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.core.alert.AlertType.PriceSource;
import com.github.lutzluca.btrbz.data.BazaarData.MarketPrices;
import io.vavr.control.Try;

public sealed interface PriceExpression permits PriceExpression.Literal,
    PriceExpression.Reference,
    PriceExpression.Binary {

    Try<Double> evaluate(MarketPrices prices);

    default Try<Double> resolve(MarketPrices prices) {
        return this.evaluate(prices).flatMap(value -> {
            if (!Double.isFinite(value) || value <= 0.0) {
                return Try.failure(new IllegalArgumentException(
                    "Price expression must resolve to a finite positive value"));
            }
            return Try.success(value);
        });
    }

    record Literal(double value) implements PriceExpression {

        @Override
        public Try<Double> evaluate(MarketPrices prices) {
            return Try.success(this.value);
        }
    }

    record Reference(PriceSource source) implements PriceExpression {

        @Override
        public Try<Double> evaluate(MarketPrices prices) {
            if (this.source == null) {
                return Try.failure(new IllegalArgumentException("Price reference is missing"));
            }
            return this.source
                .price(prices)
                .map(Try::success)
                .orElseGet(() -> Try.failure(new IllegalStateException(
                    this.source.label() + " is currently unavailable")));
        }
    }

    record Binary(PriceExpression left, BinaryOperator operator, PriceExpression right)
        implements PriceExpression {

        @Override
        public Try<Double> evaluate(MarketPrices prices) {
            if (this.left == null || this.operator == null || this.right == null) {
                return Try.failure(new IllegalArgumentException("Price expression is incomplete"));
            }
            return this.left
                .evaluate(prices)
                .flatMap(leftValue -> this.right
                    .evaluate(prices)
                    .map(rightValue -> this.operator.apply(leftValue, rightValue)));
        }
    }

    enum BinaryOperator {
        Add,
        Subtract,
        Multiply,
        Divide;

        public double apply(double left, double right) {
            return switch (this) {
                case Add -> left + right;
                case Subtract -> left - right;
                case Multiply -> left * right;
                case Divide -> left / right;
            };
        }
    }
}
