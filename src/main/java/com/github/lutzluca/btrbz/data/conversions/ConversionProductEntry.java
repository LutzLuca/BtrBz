package com.github.lutzluca.btrbz.data.conversions;

import com.github.lutzluca.btrbz.utils.Utils;

public record ConversionProductEntry(String displayName, ProductNameSource source) {

    public ConversionProductEntry {
        displayName = Utils.cleanDisplayName(displayName);
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
    }
}
