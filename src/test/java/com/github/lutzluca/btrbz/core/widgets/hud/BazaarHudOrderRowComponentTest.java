package com.github.lutzluca.btrbz.core.widgets.hud;

import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrderId;
import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarOrderText;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

class BazaarHudOrderRowComponentTest {
    @Test
    void hudIdentityCombinesVolumeAndUnitPriceWithoutASeparator() {
        var order = order(Optional.empty());

        Assertions.assertEquals("64x @ 12.4M", BazaarOrderText.orderIdentity(order).getString());
    }

    @Test
    void readableHudMarketPositionUsesOptionalGapAndBracketedQueue() {
        var order = order(
            BazaarWidgetViewData.OrderStatus.Undercut,
            Optional.of(BazaarWidgetViewData.MarketInfo.bestPriceAndQueue(12_399_999.9, 0.1, 3, 72)));

        Assertions.assertEquals(
            List.of(
                "gap 0.1 · [3/72]",
                "gap 0.1 · [72]",
                "[3/72]",
                "[72]",
                "gap 0.1"),
            BazaarOrderText.marketPositionCandidates(
                order,
                true,
                true).stream().map(Component::getString).toList());
        Assertions.assertEquals(
            List.of("[3/72]", "[72]"),
            BazaarOrderText.marketPositionCandidates(
                order,
                true,
                false).stream().map(Component::getString).toList());
        Assertions.assertEquals(
            List.of("gap 0.1"),
            BazaarOrderText.marketPositionCandidates(order, false, true).stream().map(Component::getString).toList());
    }

    private static BazaarWidgetViewData.Order order(Optional<BazaarWidgetViewData.MarketInfo> marketInfo) {
        return order(BazaarWidgetViewData.OrderStatus.Undercut, marketInfo);
    }

    private static BazaarWidgetViewData.Order order(
        BazaarWidgetViewData.OrderStatus status,
        Optional<BazaarWidgetViewData.MarketInfo> marketInfo
    ) {
        return new BazaarWidgetViewData.Order(
            new TrackedOrderId(UUID.nameUUIDFromBytes("order".getBytes())),
            BazaarWidgetViewData.OrderSide.Sell, "Product", Component.literal("Product"),
            Optional.empty(), 12_400_000, 64,
            Optional.of(new BazaarWidgetViewData.FillProgress(21, 64)),
            status, marketInfo, List.of());
    }
}
