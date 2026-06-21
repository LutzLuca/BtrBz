package com.github.lutzluca.btrbz.data;

import com.github.lutzluca.btrbz.utils.Utils;
import org.jetbrains.annotations.Nullable;

public record UnresolvedProduct(String displayName, @Nullable String rawProductId) implements ProductIdentity {

    public UnresolvedProduct {
        if (displayName == null || displayName.isBlank()) {
            displayName = "Unknown Product";
        }
    }

    @Override
    public String identityKey() {
        return this.rawProductId != null && !this.rawProductId.isBlank()
            ? "raw-id:" + this.rawProductId
            : "name:" + Utils.normalizeDisplayName(this.displayName);
    }
}
