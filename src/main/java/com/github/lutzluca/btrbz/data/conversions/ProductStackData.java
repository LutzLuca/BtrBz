package com.github.lutzluca.btrbz.data.conversions;

/** Modern, registry-decodable item stack data sourced from an NEU item overlay. */
public record ProductStackData(int dataVersion, String stackSnbt) {

    public ProductStackData {
        if (dataVersion < 0) {
            throw new IllegalArgumentException("dataVersion must not be negative");
        }
        if (stackSnbt == null || stackSnbt.isBlank()) {
            throw new IllegalArgumentException("stackSnbt must not be blank");
        }
        stackSnbt = stackSnbt.trim();
    }
}
