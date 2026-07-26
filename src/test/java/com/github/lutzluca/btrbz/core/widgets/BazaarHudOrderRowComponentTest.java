package com.github.lutzluca.btrbz.core.widgets;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
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
                order, true, BazaarWidgetOptions.PriceDisplay.BOTH, false
            )
        );
    }

    @Test
    void marketDifferenceKeepsExactSmallCoinDeltaInsteadOfPercentage() {
        var order = order(Optional.of(BazaarData.MarketInfo.bestPrice(12_399_999.9, 0.1)));

        assertEquals(
            "64x · @ 12.4M · best 12,399,999.9 · 0.1 away",
            BazaarOrderText.joined(BazaarOrderText.optionalDetails(
                order, true, BazaarWidgetOptions.PriceDisplay.UNIT, true
            ))
        );
    }

    @Test
    void hudQueueDefaultsToItemsAndCanExposeCompactOrderAndItemCounts() {
        var order = order(
            BazaarData.OrderStatus.MATCHED,
            Optional.of(BazaarData.MarketInfo.queue(3, 72))
        );

        assertEquals(
            List.of("72 ahead"),
            BazaarOrderText.hudMarketCandidates(
                order,
                BazaarWidgetOptions.QueueDisplay.ITEMS,
                BazaarWidgetOptions.UndercutDetail.PRICE_GAP_AND_QUEUE
            )
        );
        assertEquals(
            List.of("3o / 72i ahead", "72 ahead"),
            BazaarOrderText.hudMarketCandidates(
                order,
                BazaarWidgetOptions.QueueDisplay.ORDERS_AND_ITEMS,
                BazaarWidgetOptions.UndercutDetail.PRICE_GAP_AND_QUEUE
            )
        );
    }

    @Test
    void undercutHudDropsQueueBeforeThePriceGap() {
        var order = order(
            BazaarData.OrderStatus.UNDERCUT,
            Optional.of(BazaarData.MarketInfo.bestPriceAndQueue(12_399_999.9, 0.1, 3, 72))
        );

        assertEquals(
            List.of("gap 0.1 · 3o / 72i ahead", "gap 0.1 · 72 ahead", "gap 0.1"),
            BazaarOrderText.hudMarketCandidates(
                order,
                BazaarWidgetOptions.QueueDisplay.ORDERS_AND_ITEMS,
                BazaarWidgetOptions.UndercutDetail.PRICE_GAP_AND_QUEUE
            )
        );
    }

    private static BazaarData.Order order(Optional<BazaarData.MarketInfo> marketInfo) {
        return order(BazaarData.OrderStatus.UNDERCUT, marketInfo);
    }

    private static BazaarData.Order order(
        BazaarData.OrderStatus status,
        Optional<BazaarData.MarketInfo> marketInfo
    ) {
        return new BazaarData.Order(
            new com.github.lutzluca.btrbz.data.OrderModels.TrackedOrderId(
                java.util.UUID.nameUUIDFromBytes("order".getBytes())
            ), BazaarData.OrderSide.SELL, "Product", Component.literal("Product"),
            ItemStack.EMPTY, 12_400_000, 64,
            Optional.of(new BazaarData.FillProgress(21, 64)),
            status, marketInfo, List.of()
        );
    }
}
