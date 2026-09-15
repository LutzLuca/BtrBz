package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.data.BazaarData;
import java.util.Optional;
import lombok.Getter;
import lombok.experimental.Accessors;

/** A persisted alert kind, expressed as the intended side and observed market price. */
@Getter
@Accessors(fluent = true)
public enum AlertType {
    BuyOrder(Side.Buy, PriceSource.BuyOrder),
    SellOffer(Side.Sell, PriceSource.SellOffer),
    InstaBuy(Side.Buy, PriceSource.SellOffer),
    InstaSell(Side.Sell, PriceSource.BuyOrder);

    private final Side side;
    private final PriceSource priceSource;

    AlertType(Side side, PriceSource priceSource) {
        this.side = side;
        this.priceSource = priceSource;
    }

    public static AlertType of(Side side, PriceSource priceSource) {
        if (side == null || priceSource == null) {
            throw new IllegalArgumentException("Alert side and price source are required");
        }

        for (var type : values()) {
            if (type.side == side && type.priceSource == priceSource) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported alert side and price source");
    }

    public String format() {
        return this.side.label() + " at " + this.priceSource.label();
    }

    public boolean isReached(double current, double target) {
        if (!Double.isFinite(current) || !Double.isFinite(target)) {
            return false;
        }
        return switch (this.side) {
            case Buy -> current <= target;
            case Sell -> current >= target;
        };
    }

    public enum Side {
        Buy,
        Sell;

        public String label() {
            return this.name();
        }
    }

    public enum PriceSource {
        BuyOrder,
        SellOffer;

        public String label() {
            return switch (this) {
                case BuyOrder -> "Buy Order";
                case SellOffer -> "Sell Offer";
            };
        }

        public String reference() {
            return switch (this) {
                case BuyOrder -> "buy_order";
                case SellOffer -> "sell_offer";
            };
        }

        public Optional<Double> price(BazaarData.MarketPrices prices) {
            if (prices == null) {
                return Optional.empty();
            }
            var price = switch (this) {
                case BuyOrder -> prices.highestBuyOrderPrice();
                case SellOffer -> prices.lowestSellOfferPrice();
            };
            return price.filter(value -> value != null && Double.isFinite(value) && value > 0.0);
        }
    }
}
