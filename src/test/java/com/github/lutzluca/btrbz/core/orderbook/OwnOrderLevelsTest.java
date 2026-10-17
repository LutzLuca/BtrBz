package com.github.lutzluca.btrbz.core.orderbook;

import com.github.lutzluca.btrbz.core.trackedorders.TrackedOrderManager.TrackedOrderSnapshot;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrderId;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class OwnOrderLevelsTest {
    private static final ProductIdentity PRODUCT = new ProductIdentity("Product", Optional.of("PRODUCT"), null);

    @Test
    void matchesExactResolvedProductSideAndSupportedPriceWithoutPublicDepth() {
        var differentId = new ProductIdentity("Product", Optional.of("OTHER"), null);
        var levels = OwnOrderLevels.from(PRODUCT, List.of(
            order(PRODUCT, OrderType.Buy, 12.3, 20, 8, 10),
            order(PRODUCT, OrderType.Buy, 12.3, 30, 14, 11),
            order(PRODUCT, OrderType.Sell, 12.3, 40, 1, 12),
            order(differentId, OrderType.Buy, 12.3, 50, 0, 13),
            order(ProductIdentity.fromName("Product"), OrderType.Buy, 12.3, 50, 0, 14),
            order(PRODUCT, OrderType.Buy, 12.31, 60, 0, 15)));
        Assertions.assertEquals(2, levels.at(OrderType.Buy, 12.3).count());
        Assertions.assertEquals(28, levels.at(OrderType.Buy, 12.3).remaining().orElseThrow());
        Assertions.assertEquals(1, levels.at(OrderType.Sell, 12.3).count());
        Assertions.assertEquals(0, levels.at(OrderType.Buy, 12.31).count());
        Assertions.assertEquals(0, levels.at(OrderType.Buy, Math.nextUp(12.3)).count());
        Assertions.assertEquals(0, OwnOrderLevels.from(ProductIdentity.fromName("Product"), List.of(
            order(PRODUCT, OrderType.Buy, 12.3, 20, 0, 10))).at(OrderType.Buy, 12.3).count());
    }

    @Test
    void unknownOrInvalidPersonalObservationNeverProducesAPartialRemainingTotal() {
        for (var unknown : List.of(
            order(PRODUCT, OrderType.Buy, 12.3, 30, 0, -1),
            order(PRODUCT, OrderType.Buy, 12.3, 30, 31, 12),
            order(PRODUCT, OrderType.Buy, 12.3, 30, -1, 12),
            order(PRODUCT, OrderType.Buy, 12.3, 0, 0, 12))) {
            var levels = OwnOrderLevels.from(PRODUCT, List.of(
                order(PRODUCT, OrderType.Buy, 12.3, 20, 8, 10), unknown));
            Assertions.assertEquals(2, levels.at(OrderType.Buy, 12.3).count());
            Assertions.assertTrue(levels.at(OrderType.Buy, 12.3).remaining().isEmpty());
        }
        Assertions.assertEquals(0, OwnOrderLevels.from(PRODUCT, List.of()).at(OrderType.Buy, 12.3).count());
    }

    static TrackedOrderSnapshot order(
        ProductIdentity product,
        OrderType side,
        double price,
        int volume,
        int fill,
        int slot
    ) {
        return new TrackedOrderSnapshot(new TrackedOrderId(UUID.randomUUID()), product, "Product", "Product", side,
            volume, price, new OrderStatus.Unknown(), slot, fill);
    }
}
