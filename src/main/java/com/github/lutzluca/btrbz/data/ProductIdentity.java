package com.github.lutzluca.btrbz.data;

import com.github.lutzluca.btrbz.utils.Utils;
import java.util.Optional;

public sealed interface ProductIdentity permits ProductRef, UnresolvedProduct {

    String displayName();

    default Optional<ProductRef> resolvedProduct() {
        return this instanceof ProductRef product ? Optional.of(product) : Optional.empty();
    }

    default boolean isResolved() {
        return this instanceof ProductRef;
    }

    default String matchKey(String fallbackName) {
        return this.resolvedProduct()
            .map(ref -> "id:" + ref.productId())
            .orElseGet(() -> "name:" + Utils.normalizeDisplayName(fallbackName));
    }

    static boolean matches(
        ProductIdentity first,
        String firstFallbackName,
        ProductIdentity second,
        String secondFallbackName
    ) {
        var firstRef = first.resolvedProduct();
        var secondRef = second.resolvedProduct();
        if (firstRef.isPresent() && secondRef.isPresent()) {
            return firstRef.get().productId().equals(secondRef.get().productId());
        }

        return Utils
            .normalizeDisplayName(firstFallbackName)
            .equals(Utils.normalizeDisplayName(secondFallbackName));
    }
}
