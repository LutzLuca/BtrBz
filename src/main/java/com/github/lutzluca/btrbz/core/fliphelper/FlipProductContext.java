package com.github.lutzluca.btrbz.core.fliphelper;

import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

public final class FlipProductContext {

    private final BazaarData bazaarData;
    private @Nullable ProductIdentity product;

    public FlipProductContext(BazaarData bazaarData) {
        this.bazaarData = bazaarData;
    }

    void selectProduct(ProductIdentity product) {
        this.product = product;
    }

    void clearProduct() {
        this.product = null;
    }

    public Optional<ProductIdentity> getSelectedProduct() {
        if (this.product != null) {
            this.product = this.bazaarData.resolveIndexedProduct(this.product)
                .map(ProductIdentity::fromIndex).orElse(this.product);
        }
        return Optional.ofNullable(this.product);
    }
}
