package com.github.lutzluca.btrbz.core.widgets.data;

import com.github.lutzluca.btrbz.core.widgets.hud.BazaarHudOptions;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrderId;

class BazaarWidgetViewDataTest {
    @Test
    void enchantedAbbreviationIsOptionalAndSpecific() {
        Assertions.assertEquals("Ench. Diamond", BazaarHudOptions.productName("Enchanted Diamond", true));
        Assertions.assertEquals("Enchanted Diamond", BazaarHudOptions.productName("Enchanted Diamond", false));
        Assertions.assertEquals("Booster Cookie", BazaarHudOptions.productName("Booster Cookie", true));
    }

    @Test
    void statusCountsRepresentOrdersRatherThanVolume() {
        var orders = List.of(
            order("top-large", BazaarWidgetViewData.OrderStatus.Top, 71_680),
            order("top-small", BazaarWidgetViewData.OrderStatus.Top, 1),
            order("matched", BazaarWidgetViewData.OrderStatus.Matched, 500),
            order("undercut", BazaarWidgetViewData.OrderStatus.Undercut, 12),
            order("unknown", BazaarWidgetViewData.OrderStatus.Unknown, 99));

        Assertions.assertEquals(new BazaarWidgetViewData.StatusCounts(2, 1, 1, 1),
            BazaarWidgetViewData.StatusCounts.from(orders));
        Assertions.assertEquals(5, new BazaarWidgetViewData.OrdersData(orders, 3).counts().total());
        Assertions.assertEquals(3, new BazaarWidgetViewData.OrdersData(orders, 3).filledOrderCount());
    }

    @Test
    void liveProgressNeverChangesTheStableOrderVolume() {
        var order = new BazaarWidgetViewData.Order(
            id("partially-filled"), BazaarWidgetViewData.OrderSide.Sell, "Product", Component.literal("Product"),
            Optional.empty(), 10, 64, 21, BazaarWidgetViewData.OrderStatus.Matched, List.of());

        Assertions.assertEquals(64, order.amount());
        Assertions.assertEquals(43, order.liveProgress().orElseThrow().remaining());
    }

    private static BazaarWidgetViewData.Order order(String id, BazaarWidgetViewData.OrderStatus status, int volume) {
        return new BazaarWidgetViewData.Order(
            id(id),
            BazaarWidgetViewData.OrderSide.Buy,
            "Product",
            Component.literal("Product"),
            Optional.empty(),
            1,
            volume,
            0,
            status,
            List.of());
    }

    private static TrackedOrderId id(String value) {
        return new TrackedOrderId(UUID.nameUUIDFromBytes(value.getBytes()));
    }

}
