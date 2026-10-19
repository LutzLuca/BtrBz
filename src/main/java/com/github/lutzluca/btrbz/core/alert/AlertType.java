package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.data.BazaarData;
import java.util.Optional;
import java.util.Locale;
import java.util.Objects;

/** The observed price and threshold direction are independent choices. */
public record AlertType(PriceSource source, Direction direction) {
    public AlertType {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(direction, "direction");
    }

    public String format() {
        return this.source.label() + " " + this.direction.symbol();
    }

    public boolean isReached(double current, double target) {
        if (!Double.isFinite(current) || !Double.isFinite(target)) {
            return false;
        }
        return switch (this.direction) {
            case Below -> current <= target;
            case Above -> current >= target;
        };
    }

    public enum Direction {
        Below,
        Above;

        public String symbol() {
            return this == Below ? "<=" : ">=";
        }
    }

    public enum PriceSource {
        Buy,
        Sell;

        public String label() {
            return this.name() + " Price";
        }

        public String reference() {
            return this.name().toLowerCase(Locale.ROOT);
        }

        public Optional<Double> price(BazaarData.MarketPrices prices) {
            if (prices == null) {
                return Optional.empty();
            }
            var price = switch (this) {
                case Buy -> prices.lowestSellOfferPrice();
                case Sell -> prices.highestBuyOrderPrice();
            };
            return price.filter(value -> value != null && Double.isFinite(value) && value > 0.0);
        }
    }
}
