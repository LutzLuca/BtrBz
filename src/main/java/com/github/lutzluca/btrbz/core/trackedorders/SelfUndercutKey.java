package com.github.lutzluca.btrbz.core.trackedorders;

import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrder;
import com.github.lutzluca.btrbz.data.ProductIdentity;

public record SelfUndercutKey(String productName, ProductIdentity product, OrderType type) {

    public static SelfUndercutKey from(TrackedOrder order) {
        return new SelfUndercutKey(
            order.productName,
            order.product,
            order.type
        );
    }
}
