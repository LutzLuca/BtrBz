package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.data.BazaarData.MarketPrices;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import io.vavr.control.Try;

/** Editable alert input which can be previewed repeatedly against live prices. */
public record AlertDraft(IndexedProduct product, AlertType type, String input) {

    public Try<AlertDefinition> resolve(MarketPrices prices, long timestamp) {
        if (this.product == null) {
            return Try.failure(new IllegalArgumentException("Select a Bazaar item"));
        }
        if (this.type == null) {
            return Try.failure(new IllegalArgumentException("Select a price and threshold direction"));
        }

        return PriceExpressionParser
            .parse(this.input)
            .flatMap(parsed -> parsed.resolve(prices))
            .map(price -> new AlertDefinition(timestamp, this.product, this.type, price))
            .flatMap(AlertDefinition::validate);
    }
}
