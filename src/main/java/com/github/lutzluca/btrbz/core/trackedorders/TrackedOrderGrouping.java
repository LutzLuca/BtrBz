package com.github.lutzluca.btrbz.core.trackedorders;

import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrder;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.utils.Utils;

final class TrackedOrderGrouping {

    private TrackedOrderGrouping() { }

    sealed interface ProductGroupKey permits MarketProductKey, NameProductKey { }

    record MarketProductKey(String productId) implements ProductGroupKey { }

    record NameProductKey(String normalizedName) implements ProductGroupKey { }

    record GroupMatchKey(ProductGroupKey product, OrderType type, double pricePerUnit) {

        static GroupMatchKey from(TrackedOrder order) {
            return new GroupMatchKey(
                productKey(order.product, order.uiProductName),
                order.type,
                order.pricePerUnit
            );
        }
    }

    record SelfUndercutMatchKey(ProductGroupKey product, OrderType type) {

        static SelfUndercutMatchKey from(TrackedOrder order) {
            return new SelfUndercutMatchKey(
                productKey(order.product, order.uiProductName),
                order.type
            );
        }
    }

    static ProductGroupKey productKey(ProductIdentity product, String fallbackName) {
        return product
            .bazaarProductId()
            .<ProductGroupKey>map(MarketProductKey::new)
            .orElseGet(() -> new NameProductKey(Utils.normalizeDisplayName(fallbackName)));
    }
}
