package com.github.lutzluca.btrbz.core.fliphelper;

import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

public final class FlipProductContext {

    private @Nullable ProductIdentity product;

    void selectOrder(OrderInfo order) {
        this.product = order.type() == OrderType.Buy
            && order instanceof OrderInfo.FilledOrderInfo
            && order.product().bazaarProductId().isPresent()
                ? order.product() : null;
    }

    Optional<Double> getFlipPrice(BazaarData bazaarData) {
        return this.getSelectedProduct()
            .flatMap(bazaarData::lowestSellOfferPrice)
            .filter(price -> Double.isFinite(price) && price > 0)
            .map(price -> Math.max(price - 0.1, 0.1));
    }

    void clearProduct() {
        this.product = null;
    }

    public Optional<ProductIdentity> getSelectedProduct() {
        return Optional.ofNullable(this.product);
    }
}
