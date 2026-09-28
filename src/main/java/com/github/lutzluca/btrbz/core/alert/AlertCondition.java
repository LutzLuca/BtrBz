package com.github.lutzluca.btrbz.core.alert;

import io.vavr.control.Try;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Fixed conditions shared by editing, monitoring, and persistence. */
public sealed interface AlertCondition {
    record Price(AlertType type, double price) implements AlertCondition {
        public Price {
            price = roundPrice(price);
        }
    }

    record Liquidity(LiquiditySide side, long quantity, double priceBound) implements AlertCondition {
        public Liquidity {
            priceBound = roundPrice(priceBound);
        }
    }

    default Kind kind() {
        return switch (this) {
            case Price _ -> Kind.Price;
            case Liquidity _ -> Kind.Liquidity;
        };
    }

    default Try<AlertCondition> validate() {
        return Try.of(() -> {
            switch (this) {
                case Price price -> {
                    if (price.type() == null) {
                        throw new IllegalArgumentException("Select a price and threshold direction");
                    }
                    validatePrice(price.price());
                }
                case Liquidity liquidity -> {
                    if (liquidity.side() == null) {
                        throw new IllegalArgumentException("Select buy orders or sell offers");
                    }
                    if (liquidity.quantity() <= 0) {
                        throw new IllegalArgumentException("Quantity must be a positive whole number");
                    }
                    validatePrice(liquidity.priceBound());
                }
            }
            return this;
        });
    }

    default boolean isReached(Observation observation) {
        return switch (this) {
            case Price price -> observation instanceof Observation.Price observed
                && price.type().isReached(observed.value(), price.price());
            case Liquidity liquidity -> observation instanceof Observation.Liquidity observed
                && observed.quantity() >= liquidity.quantity();
        };
    }

    sealed interface Observation {
        record Price(double value) implements Observation {
            public Price {
                validatePrice(value);
            }
        }

        record Liquidity(long quantity) implements Observation {
            public Liquidity {
                if (quantity < 0) {
                    throw new IllegalArgumentException("Observed quantity must not be negative");
                }
            }
        }

        default Kind kind() {
            return switch (this) {
                case Price _ -> Kind.Price;
                case Liquidity _ -> Kind.Liquidity;
            };
        }
    }

    enum Kind {
        Price,
        Liquidity
    }

    enum LiquiditySide {
        BuyOrders,
        SellOffers
    }

    private static double roundPrice(double price) {
        return Double.isFinite(price)
            ? BigDecimal.valueOf(price).setScale(1, RoundingMode.HALF_UP).doubleValue() : price;
    }

    private static void validatePrice(double price) {
        if (!Double.isFinite(price) || price <= 0) {
            throw new IllegalArgumentException("Price must be finite and positive");
        }
    }
}
