package com.github.lutzluca.btrbz.core.widgets.hud;

import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarOrderText;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetDisplayOptions;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BazaarHudOrderRowComponentTest {
    @Test
    void optionalFactsUseStableOriginalVolumeAndConfiguredPrices() {
        var order = order(Optional.empty());

        assertEquals(
            List.of("64x", "@ 12.4M", "total 793.6M"),
            BazaarOrderText.optionalDetails(
                order, true, WidgetDisplayOptions.PriceDisplay.Both, false
            )
        );
    }

    @Test
    void hudIdentityCombinesVolumeAndUnitPriceWithoutASeparator() {
        var order = order(Optional.empty());

        assertEquals(
            "64x @ 12.4M",
            BazaarOrderText.hudOrderIdentity(
                order, true, WidgetDisplayOptions.PriceDisplay.Unit
            )
        );
        assertEquals(
            "64x @ 12.4M · total 793.6M",
            BazaarOrderText.hudOrderIdentity(
                order, true, WidgetDisplayOptions.PriceDisplay.Both
            )
        );
    }

    @Test
    void hudRowCentersIconWithCompactHorizontalInsets() {
        assertEquals(20, BazaarHudOrderRowComponent.HEIGHT);
        assertEquals(18, BazaarHudOrderRowComponent.ICON_CELL_WIDTH);
    }

    @Test
    void marketDifferenceKeepsExactSmallCoinGapInsteadOfPercentage() {
        var order = order(Optional.of(BazaarWidgetViewData.MarketInfo.bestPrice(12_399_999.9, 0.1)));

        assertEquals(
            "64x · @ 12.4M · best 12,399,999.9 · 0.1 away",
            BazaarOrderText.joined(BazaarOrderText.optionalDetails(
                order, true, WidgetDisplayOptions.PriceDisplay.Unit, true
            ))
        );
    }

    @Test
    void hudQueueDefaultsToItemsAndCanExposeCompactOrderAndItemCounts() {
        var order = order(
            BazaarWidgetViewData.OrderStatus.Matched,
            Optional.of(BazaarWidgetViewData.MarketInfo.queue(3, 72))
        );

        assertEquals(
            List.of("72 ahead"),
            BazaarOrderText.hudMarketCandidates(
                order,
                WidgetDisplayOptions.QueueDisplay.Items,
                WidgetDisplayOptions.UndercutDetail.PriceGapAndQueue
            )
        );
        assertEquals(
            List.of("3o / 72i ahead", "72 ahead"),
            BazaarOrderText.hudMarketCandidates(
                order,
                WidgetDisplayOptions.QueueDisplay.OrdersAndItems,
                WidgetDisplayOptions.UndercutDetail.PriceGapAndQueue
            )
        );
    }

    @Test
    void undercutHudDropsQueueBeforeThePriceGap() {
        var order = order(
            BazaarWidgetViewData.OrderStatus.Undercut,
            Optional.of(BazaarWidgetViewData.MarketInfo.bestPriceAndQueue(12_399_999.9, 0.1, 3, 72))
        );

        assertEquals(
            List.of("gap 0.1 · 3o / 72i ahead", "gap 0.1 · 72 ahead", "gap 0.1"),
            BazaarOrderText.hudMarketCandidates(
                order,
                WidgetDisplayOptions.QueueDisplay.OrdersAndItems,
                WidgetDisplayOptions.UndercutDetail.PriceGapAndQueue
            )
        );
    }

    @Test
    void readableHudMarketPositionUsesOptionalGapAndBracketedQueue() {
        var order = order(
            BazaarWidgetViewData.OrderStatus.Undercut,
            Optional.of(BazaarWidgetViewData.MarketInfo.bestPriceAndQueue(12_399_999.9, 0.1, 3, 72))
        );

        assertEquals(
            List.of(
                "gap 0.1 · [3/72]",
                "gap 0.1 · [72 items]",
                "gap 0.1 · [72]",
                "gap 0.1"
            ),
            BazaarOrderText.hudMarketPositionCandidates(
                order,
                WidgetDisplayOptions.QueueDisplay.OrdersAndItems,
                true
            )
        );
        assertEquals(
            List.of("[3/72]", "[72 items]", "[72]"),
            BazaarOrderText.hudMarketPositionCandidates(
                order,
                WidgetDisplayOptions.QueueDisplay.OrdersAndItems,
                false
            )
        );
    }

    private static BazaarWidgetViewData.Order order(Optional<BazaarWidgetViewData.MarketInfo> marketInfo) {
        return order(BazaarWidgetViewData.OrderStatus.Undercut, marketInfo);
    }

    private static BazaarWidgetViewData.Order order(
        BazaarWidgetViewData.OrderStatus status,
        Optional<BazaarWidgetViewData.MarketInfo> marketInfo
    ) {
        return new BazaarWidgetViewData.Order(
            new com.github.lutzluca.btrbz.data.OrderModels.TrackedOrderId(
                java.util.UUID.nameUUIDFromBytes("order".getBytes())
            ), BazaarWidgetViewData.OrderSide.Sell, "Product", Component.literal("Product"),
            Optional.empty(), 12_400_000, 64,
            Optional.of(new BazaarWidgetViewData.FillProgress(21, 64)),
            status, marketInfo, List.of()
        );
    }
}
