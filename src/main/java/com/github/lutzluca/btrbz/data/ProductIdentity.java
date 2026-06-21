package com.github.lutzluca.btrbz.data;

import java.util.Optional;

public sealed interface ProductIdentity permits ProductRef, UnresolvedProduct {

    String displayName();

    String identityKey();

    default Optional<ProductRef> resolvedProduct() {
        return this instanceof ProductRef product ? Optional.of(product) : Optional.empty();
    }

    default boolean isResolved() {
        return this instanceof ProductRef;
    }
}
