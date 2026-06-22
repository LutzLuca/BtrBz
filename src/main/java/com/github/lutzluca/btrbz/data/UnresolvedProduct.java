package com.github.lutzluca.btrbz.data;

import org.jetbrains.annotations.Nullable;

public record UnresolvedProduct(String displayName, @Nullable String rawProductId) implements ProductIdentity {

    public UnresolvedProduct {
        if (displayName == null || displayName.isBlank()) {
            displayName = "Unknown Product";
        }
    }

}
