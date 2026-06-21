package com.github.lutzluca.btrbz.data.conversions;

public sealed interface ProductNameSource permits ProductNameSource.HypixelItem,
    ProductNameSource.Neu,
    ProductNameSource.Derived {

    record HypixelItem() implements ProductNameSource { }

    record Neu(String neuId) implements ProductNameSource {
        public Neu {
            if (neuId == null || neuId.isBlank()) {
                throw new IllegalArgumentException("neuId must not be blank");
            }
        }
    }

    record Derived() implements ProductNameSource { }
}
