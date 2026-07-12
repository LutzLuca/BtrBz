package com.github.lutzluca.btrbz.data;

import java.util.Optional;
import org.jetbrains.annotations.Nullable;

/**
 * Runtime product evidence from stacks, chat, and order UI.
 * Use it for Bazaar market lookups. It may carry a Bazaar id before the conversion index has a matching name.
 */
public record ProductIdentity(
    String strippedName,
    Optional<String> bazaarProductId,
    @Nullable String formattedName
) {

    public ProductIdentity {
        if (strippedName == null || strippedName.isBlank()) {
            strippedName = "Unknown Product";
        } else {
            strippedName = strippedName.trim();
        }

        bazaarProductId = Optional
            .ofNullable(bazaarProductId)
            .flatMap(id -> id)
            .map(String::trim)
            .filter(id -> !id.isEmpty())
            .filter(id -> !"ENCHANTED_BOOK".equalsIgnoreCase(id));

        if (formattedName != null) {
            formattedName = formattedName.trim();
            if (formattedName.isEmpty()) {
                formattedName = null;
            }
        }
    }

    public static ProductIdentity fromIndex(IndexedProduct product) {
        return new ProductIdentity(product.strippedName(), Optional.of(product.productId()), product.formattedName());
    }

    public static ProductIdentity fromRuntime(
        String strippedName,
        @Nullable String bazaarProductId,
        @Nullable String formattedName
    ) {
        return new ProductIdentity(strippedName, Optional.ofNullable(bazaarProductId), formattedName);
    }

    public static ProductIdentity fromName(String strippedName) {
        return new ProductIdentity(strippedName, Optional.empty(), null);
    }

    public String visualName() {
        return this.formattedName != null ? this.formattedName : this.strippedName;
    }
}
