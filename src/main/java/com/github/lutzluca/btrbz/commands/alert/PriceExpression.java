package com.github.lutzluca.btrbz.commands.alert;

import com.github.lutzluca.btrbz.commands.alert.AlertCommandParser.ParseException;
import com.github.lutzluca.btrbz.commands.alert.PriceExpression.Binary;
import com.github.lutzluca.btrbz.commands.alert.PriceExpression.Identifier;
import com.github.lutzluca.btrbz.commands.alert.PriceExpression.Literal;
import com.github.lutzluca.btrbz.data.BazaarData;
import io.vavr.control.Try;

public sealed interface PriceExpression permits Literal, Identifier, Binary {

    Try<Double> resolve(String productName, AlertType type, BazaarData bazaarData);

    enum AlertType {
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

        public AlertType getAssociatedInstaType() {
            return switch (this) {
                case BuyOrder, InstaBuy -> InstaBuy;
                case SellOffer, InstaSell -> InstaSell;
            };
        }

        public AlertType getAssociatedOrderType() {
            return switch (this) {
                case BuyOrder, InstaBuy -> BuyOrder;
                case SellOffer, InstaSell -> SellOffer;
            };
        }
    }

    enum BinaryOperator {
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

    record Literal(double value) implements PriceExpression {

        @Override
        public Try<Double> resolve(String productName, AlertType type, BazaarData bazaarData) {
            return Try.success(value);
        }
    }

    record Identifier(String name) implements PriceExpression {

        @Override
        public Try<Double> resolve(String productName, AlertType type, BazaarData bazaarData) {
            assert this.name.equals("order") || this.name.equals("insta");

            var id = bazaarData.nameToId(productName);
            if (id.isEmpty()) {
                return Try.failure(new IllegalArgumentException("Invalid name " + '"' + productName + '"'));
            }

            var lookupType = switch (this.name) {
                case "order" -> type.getAssociatedOrderType();
                case "insta" -> type.getAssociatedInstaType();
                default -> throw new AssertionError("unreachable");
            };

            var price = switch (lookupType) {
                case BuyOrder, InstaSell -> bazaarData.highestBuyPrice(id.get());
                case SellOffer, InstaBuy -> bazaarData.lowestSellPrice(id.get());
            };

            return price
                .map(Try::success)
                .orElseGet(() -> Try.failure(new IllegalStateException("The price of " + '"' + productName + '"' + " could not be determined")));
        }
    }

    record Binary(PriceExpression left, BinaryOperator op, PriceExpression right) implements
        PriceExpression {

        @Override
        public Try<Double> resolve(String productName, AlertType type, BazaarData bazaarData) {
            return left
                .resolve(productName, type, bazaarData)
                .flatMap(leftVal -> right
                    .resolve(productName, type, bazaarData)
                    .map(rightVal -> op.apply(leftVal, rightVal)));
        }
    }
}


