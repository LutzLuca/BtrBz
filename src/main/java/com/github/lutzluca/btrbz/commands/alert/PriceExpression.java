package com.github.lutzluca.btrbz.commands.alert;

import com.github.lutzluca.btrbz.commands.alert.AlertCommandParser.ParseException;
import com.github.lutzluca.btrbz.data.BazaarData;
import io.vavr.control.Try;

public sealed interface PriceExpression
        permits PriceExpression.Literal, PriceExpression.Identifier, PriceExpression.Binary {
    Try<Double> resolve(AlertType type, BazaarData bazaarData);

    public record Literal(double value) implements PriceExpression {
        @Override
        public Try<Double> resolve(AlertType type, BazaarData bazaarData) {
            return Try.success(value);
        }
    }

    public record Identifier(String name) implements PriceExpression {
        @Override
        public Try<Double> resolve(AlertType type, BazaarData bazaarData) {
            var id = bazaarData.nameToId(this.name);
            if (id.isEmpty()) {
                return Try.failure(
                        new IllegalArgumentException("Invalid name " + '"' + this.name + '"'));
            }

            var price = switch (type) {
                case BuyOrder, InstaSell -> bazaarData.highestBuyPrice(id.get());
                case SellOffer, InstaBuy -> bazaarData.lowestSellPrice(id.get());
            };
            if (price.isEmpty()) {
                return Try.failure(new IllegalStateException(
                        "The price of " + '"' + this.name + '"' + " could not be determined"));
            }
            return Try.success(price.get());
        }
    }

    public record Binary(PriceExpression left, BinaryOperator op, PriceExpression right)
            implements PriceExpression {
        @Override
        public Try<Double> resolve(AlertType type, BazaarData bazaarData) {
            return left.resolve(type, bazaarData).flatMap(leftVal -> right.resolve(type, bazaarData)
                    .map(rightVal -> op.apply(leftVal, rightVal)));
        }
    }

    public enum AlertType {
        BuyOrder, SellOffer, InstaBuy, InstaSell;

        public static Try<AlertType> fromIdentifier(String identifier) {
            return switch (identifier) {
                case "buyorder", "buy", "b" -> Try.success(AlertType.BuyOrder);
                case "selloffer", "sell", "s" -> Try.success(AlertType.SellOffer);
                case "instabuy", "ibuy", "ib" -> Try.success(AlertType.InstaBuy);
                case "instasell", "isell", "is" -> Try.success(AlertType.InstaSell);
                default -> Try.failure(new ParseException("Unknown alert type: " + identifier));
            };
        }
    }

    public enum BinaryOperator {
        Add, Subtract, Multiply, Divide;

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


