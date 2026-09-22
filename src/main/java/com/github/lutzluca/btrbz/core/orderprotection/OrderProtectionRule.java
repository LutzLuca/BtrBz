package com.github.lutzluca.btrbz.core.orderprotection;

import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * Pure price-safety rule for Bazaar limit orders.
 *
 * <p>The rule deliberately distinguishes crossing the spread from improving the same side of
 * the order book. Crossing the spread turns a limit order into an immediate trade and is therefore
 * checked independently. Exactly one Bazaar price tick is exempt from the same-side percentage
 * check because it is the smallest price improvement the game permits.</p>
 */
public final class OrderProtectionRule {

    private OrderProtectionRule() {}

    public static Result evaluate(
        OrderType type,
        double proposedPrice,
        Optional<Double> highestBuyOrder,
        Optional<Double> lowestSellOffer,
        Settings settings
    ) {
        if (!isValidPrice(proposedPrice)) {
            return Result.blocked(Violation.InvalidPrice, proposedPrice, Double.NaN, 0, 0);
        }

        var bestBuy = validPrice(highestBuyOrder);
        var bestSell = validPrice(lowestSellOffer);

        var opposingPrice = type == OrderType.Buy ? bestSell : bestBuy;
        if (settings.blockOpposingPrices()
            && opposingPrice.isPresent()
            && crossesSpread(type, proposedPrice, opposingPrice.get())) {
            return Result.blocked(
                Violation.OpposingPrice,
                proposedPrice,
                opposingPrice.get(),
                Math.abs(proposedPrice - opposingPrice.get()),
                percentageDifference(proposedPrice, opposingPrice.get()));
        }

        var sameSidePrice = type == OrderType.Buy ? bestBuy : bestSell;
        if (sameSidePrice.isEmpty()) {
            return Result.allowed();
        }

        double referencePrice = sameSidePrice.get();
        long aggressiveTicks = aggressiveTicks(type, proposedPrice, referencePrice);
        if (aggressiveTicks <= 1) {
            return Result.allowed();
        }

        double aggressiveDelta = aggressiveDelta(type, proposedPrice, referencePrice);
        double percentage = aggressiveDelta / referencePrice * 100;
        double percentageLimit = type == OrderType.Buy
            ? settings.maxBuyOrderPercentage()
            : settings.maxSellOfferPercentage();

        if (settings.blockPercentage()
            && thresholdReached(type, proposedPrice, referencePrice, percentageLimit)) {
            return Result.blocked(
                Violation.Percentage,
                proposedPrice,
                referencePrice,
                aggressiveDelta,
                percentage);
        }

        return Result.allowed();
    }

    private static Optional<Double> validPrice(Optional<Double> price) {
        return price.filter(OrderProtectionRule::isValidPrice);
    }

    private static boolean isValidPrice(double price) {
        return Double.isFinite(price) && price > 0;
    }

    private static boolean crossesSpread(OrderType type, double proposedPrice, double opposingPrice) {
        return switch (type) {
            case Buy -> proposedPrice >= opposingPrice;
            case Sell -> proposedPrice <= opposingPrice;
        };
    }

    private static long aggressiveTicks(OrderType type, double proposedPrice, double referencePrice) {
        long proposedTicks = toTicks(proposedPrice);
        long referenceTicks = toTicks(referencePrice);

        return switch (type) {
            case Buy -> proposedTicks - referenceTicks;
            case Sell -> referenceTicks - proposedTicks;
        };
    }

    private static long toTicks(double price) {
        return Math.round(price / 0.1);
    }

    private static double aggressiveDelta(OrderType type, double proposedPrice, double referencePrice) {
        return switch (type) {
            case Buy -> proposedPrice - referencePrice;
            case Sell -> referencePrice - proposedPrice;
        };
    }

    private static double percentageDifference(double proposedPrice, double referencePrice) {
        return Math.abs(proposedPrice - referencePrice) / referencePrice * 100;
    }

    private static boolean thresholdReached(
        OrderType type,
        double proposedPrice,
        double referencePrice,
        double threshold
    ) {
        if (!Double.isFinite(threshold) || threshold < 0) {
            return false;
        }

        var proposed = BigDecimal.valueOf(proposedPrice);
        var reference = BigDecimal.valueOf(referencePrice);
        var improvement = type == OrderType.Buy
            ? proposed.subtract(reference)
            : reference.subtract(proposed);
        return improvement.multiply(BigDecimal.valueOf(100))
            .compareTo(reference.multiply(BigDecimal.valueOf(threshold))) >= 0;
    }

    public enum Violation {
        InvalidPrice,
        OpposingPrice,
        Percentage
    }

    public record Result(
        boolean blocked,
        Optional<Violation> violation,
        double proposedPrice,
        double referencePrice,
        double delta,
        double percentage
    ) {

        private static Result allowed() {
            return new Result(false, Optional.empty(), Double.NaN, Double.NaN, 0, 0);
        }

        private static Result blocked(
            Violation violation,
            double proposedPrice,
            double referencePrice,
            double delta,
            double percentage
        ) {
            return new Result(
                true,
                Optional.of(violation),
                proposedPrice,
                referencePrice,
                delta,
                percentage);
        }
    }

    public record Settings(
        boolean blockOpposingPrices,
        boolean blockPercentage,
        double maxBuyOrderPercentage,
        double maxSellOfferPercentage
    ) {

        public static Settings from(OrderProtectionConfig cfg) {
            return new Settings(
                cfg.blockUndercutOfOpposing,
                cfg.blockUndercutPercentage,
                cfg.maxBuyOrderUndercut,
                cfg.maxSellOfferUndercut);
        }
    }
}
