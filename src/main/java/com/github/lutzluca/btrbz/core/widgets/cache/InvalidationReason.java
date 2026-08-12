package com.github.lutzluca.btrbz.core.widgets.cache;

/** Human-readable context captured when an owned cache dependency changes. */
public record InvalidationReason(String description) {
    public InvalidationReason {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Invalidation reason must not be blank");
        }
    }

    public static InvalidationReason of(String description) {
        return new InvalidationReason(description);
    }
}
