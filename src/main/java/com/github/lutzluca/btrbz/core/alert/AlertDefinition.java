package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.github.lutzluca.btrbz.utils.Utils;
import io.vavr.control.Try;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** The fully resolved values captured when an alert is saved. */
public record AlertDefinition(long timestamp, IndexedProduct product, AlertType type, double price) {

    public AlertDefinition {
        if (Double.isFinite(price)) {
            price = BigDecimal.valueOf(price).setScale(1, RoundingMode.HALF_UP).doubleValue();
        }
    }

    public Try<AlertDefinition> validate() {
        if (this.timestamp < 0) {
            return Try.failure(new IllegalArgumentException("Alert capture time must not be negative"));
        }
        if (this.product == null) {
            return Try.failure(new IllegalArgumentException("Select a Bazaar item"));
        }
        if (this.type == null) {
            return Try.failure(new IllegalArgumentException("Select a price and threshold direction"));
        }
        if (!Double.isFinite(this.price) || this.price <= 0.0) {
            return Try.failure(new IllegalArgumentException(
                "Price expression evaluates to an invalid price of \""
                    + Utils.formatDecimal(this.price, 1, true)
                    + "\". Expected a finite positive price"));
        }
        return Try.success(this);
    }
}
