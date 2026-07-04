package com.github.lutzluca.btrbz.core.commands.alert;

import com.github.lutzluca.btrbz.core.commands.alert.AlertCommandParser.ParseException;
import com.github.lutzluca.btrbz.core.commands.alert.PriceExpression.Binary;
import com.github.lutzluca.btrbz.core.commands.alert.PriceExpression.Literal;
import com.github.lutzluca.btrbz.core.commands.alert.PriceExpression.Reference;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.ProductRef;
import io.vavr.control.Try;

public sealed interface PriceExpression permits Literal,
    Reference,
    Binary {

    Try<Double> resolve(ProductRef product, AlertType type, BazaarData bazaarData);

    enum AlertType {
        BuyOrder,
        SellOffer,
        InstaBuy,
        InstaSell;

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

        public String format() {
            return switch (this) {
                case BuyOrder -> "buy order";
                case SellOffer -> "sell offer";
                case InstaBuy -> "insta buy";
                case InstaSell -> "insta sell";
            };
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

    enum ReferenceType {
        Order,
        Insta;

        public static Try<ReferenceType> fromIdentifier(String ident) {
            return switch (ident) {
                case "order" -> Try.success(ReferenceType.Order);
                case "insta" -> Try.success(ReferenceType.Insta);
                default ->
                    Try.failure(new IllegalArgumentException("Unrecognized reference type " + '"' + ident + '"'));
            };
        }
    }

    record Literal(double value) implements PriceExpression {

        @Override
        public Try<Double> resolve(ProductRef product, AlertType type, BazaarData bazaarData) {
            return Try.success(value);
        }
    }

    record Reference(ReferenceType reference) implements PriceExpression {

        @Override
        public Try<Double> resolve(ProductRef product, AlertType type, BazaarData bazaarData) {
            var lookupType = switch (this.reference) {
                case ReferenceType.Order -> type.getAssociatedOrderType();
                case ReferenceType.Insta -> type.getAssociatedInstaType();
            };

            var price = switch (lookupType) {
                case BuyOrder, InstaSell -> bazaarData.highestBuyOrderPrice(product);
                case SellOffer, InstaBuy -> bazaarData.lowestSellOfferPrice(product);
            };

            return price
                .map(Try::success)
                .orElseGet(() -> Try.failure(new IllegalStateException("The price of " + '"' + product.strippedName() + '"' + " could not be determined")));
        }
    }

    record Binary(PriceExpression left, BinaryOperator op, PriceExpression right)
        implements PriceExpression {

        @Override
        public Try<Double> resolve(ProductRef product, AlertType type, BazaarData bazaarData) {
            return left
                .resolve(product, type, bazaarData)
                .flatMap(leftVal -> right
                    .resolve(product, type, bazaarData)
                    .map(rightVal -> op.apply(leftVal, rightVal)));
        }
    }
}
