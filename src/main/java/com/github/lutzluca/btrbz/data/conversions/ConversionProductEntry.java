package com.github.lutzluca.btrbz.data.conversions;

import com.github.lutzluca.btrbz.utils.Utils;
import org.jetbrains.annotations.Nullable;

public record ConversionProductEntry(
    String formattedName,
    ProductNameSource source,
    @Nullable ProductStackData itemStack,
    @Nullable LegacyProductStackData legacyItemStack
) {

    public ConversionProductEntry(String formattedName, ProductNameSource source) {
        this(formattedName, source, null, null);
    }

    public ConversionProductEntry(
        String formattedName,
        ProductNameSource source,
        @Nullable ProductStackData itemStack
    ) {
        this(formattedName, source, itemStack, null);
    }

    public ConversionProductEntry {
        if (formattedName == null || formattedName.isBlank()) {
            throw new IllegalArgumentException("formattedName must not be blank");
        }
        formattedName = formattedName.trim();
        if (Utils.cleanDisplayName(formattedName).isBlank()) {
            throw new IllegalArgumentException("formattedName must contain a visible name");
        }
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
    }

    public String strippedName() {
        return Utils.cleanDisplayName(this.formattedName);
    }
}
