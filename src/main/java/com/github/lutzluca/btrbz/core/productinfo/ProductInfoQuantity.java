package com.github.lutzluca.btrbz.core.productinfo;

import com.github.lutzluca.btrbz.data.ProductIdentity;
import java.util.OptionalInt;
import lombok.Getter;
import lombok.experimental.Accessors;

record ProductInfoQuantity(Source source, OptionalInt count) {

    static ProductInfoQuantity stack(int count, ProductIdentity product, boolean singleItemPrice) {
        var single = singleItemPrice
            || product.bazaarProductId().filter(id -> id.startsWith("ENCHANTMENT_")).isPresent();
        return new ProductInfoQuantity(Source.STACK, OptionalInt.of(single ? 1 : count));
    }

    @Getter
    @Accessors(fluent = true)
    enum Source {
        STACK("Stack total", "Stack total unavailable", "items"),
        ORDER("Market total", "Order total unavailable", "items"),
        SACK("Sack total", "Sack total unavailable", "items"),
        STASH("Stash total", "Stash total unavailable", "items"),
        HUNTING_BOX("Owned shard total", "Shard total unavailable", "shards"),
        COMPOSTER("Available compost total", "Compost total unavailable", "compost");

        private final String totalLabel;
        private final String unavailableLabel;
        private final String unit;

        Source(String totalLabel, String unavailableLabel, String unit) {
            this.totalLabel = totalLabel;
            this.unavailableLabel = unavailableLabel;
            this.unit = unit;
        }
    }
}
