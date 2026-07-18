package com.github.lutzluca.btrbz.core.fliphelper;

import com.github.lutzluca.btrbz.data.IndexedProduct;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

public final class FlipProductContext {

    private @Nullable IndexedProduct product;

    void selectProduct(IndexedProduct product) {
        this.product = product;
    }

    void clearProduct() {
        this.product = null;
    }

    public Optional<IndexedProduct> getSelectedProduct() {
        return Optional.ofNullable(this.product);
    }
}
