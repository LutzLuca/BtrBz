package com.github.lutzluca.btrbz.data.conversions;

import java.io.IOException;
import java.util.Map;

record ConversionIndexSnapshot(
    int schemaVersion,
    String generatedAt,
    String neuCommit,
    Map<String, ConversionProductEntry> products
) {

    static ConversionIndexSnapshot fromIndex(ConversionIndex index) {
        return new ConversionIndexSnapshot(
            index.schemaVersion(),
            index.generatedAt(),
            index.neuCommit().orElse(null),
            index.products()
        );
    }

    ConversionIndex toIndex() throws IOException {
        if (this.schemaVersion != ConversionIndex.SCHEMA_VERSION) {
            throw new IOException("Unsupported conversion index schema version: " + this.schemaVersion);
        }
        if (this.products == null || this.products.isEmpty()) {
            throw new IOException("Conversion index contains no products");
        }

        try {
            return new ConversionIndex(
                this.schemaVersion,
                this.generatedAt,
                this.neuCommit,
                this.products
            );
        } catch (IllegalArgumentException err) {
            throw new IOException("Invalid conversion index", err);
        }
    }
}
