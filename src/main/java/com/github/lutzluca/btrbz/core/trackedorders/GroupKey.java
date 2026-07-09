package com.github.lutzluca.btrbz.core.trackedorders;

import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrder;
import com.github.lutzluca.btrbz.data.ProductIdentity;

public record GroupKey(String productName, ProductIdentity product, OrderType type, double pricePerUnit) {

    public static GroupKey from(TrackedOrder order) {
        return new GroupKey(
            order.productName,
            order.product,
            order.type,
            order.pricePerUnit
        );
    }
}
