package com.github.lutzluca.btrbz.data.conversions;

import com.github.lutzluca.btrbz.utils.Utils;

public record ConversionProductEntry(String formattedName, ProductNameSource source) {

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
