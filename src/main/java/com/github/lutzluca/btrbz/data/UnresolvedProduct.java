package com.github.lutzluca.btrbz.data;

import org.jetbrains.annotations.Nullable;

public record UnresolvedProduct(String strippedName, @Nullable String rawProductId) implements ProductIdentity {

    public UnresolvedProduct {
        if (strippedName == null || strippedName.isBlank()) {
            strippedName = "Unknown Product";
        }
    }

}
