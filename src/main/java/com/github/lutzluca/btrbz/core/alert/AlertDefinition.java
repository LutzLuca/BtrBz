package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.data.IndexedProduct;
import io.vavr.control.Try;

/** The fully resolved values captured when an alert is saved. */
public record AlertDefinition(long timestamp, IndexedProduct product, AlertCondition condition) {

    public Try<AlertDefinition> validate() {
        if (this.timestamp < 0) {
            return Try.failure(new IllegalArgumentException("Alert capture time must not be negative"));
        }
        if (this.product == null) {
            return Try.failure(new IllegalArgumentException("Select a Bazaar item"));
        }
        if (this.condition == null) {
            return Try.failure(new IllegalArgumentException("An alert condition is required"));
        }
        return this.condition.validate().map(_ -> this);
    }
}
