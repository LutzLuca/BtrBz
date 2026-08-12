package com.github.lutzluca.btrbz.core.widgets.orderbook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.lutzluca.btrbz.core.widgets.config.BazaarWidgetOptions;
import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

class OrderBookWidgetTest {
    @Test
    void appropriateSideOptionRestrictsSellWorkflowsToSellOffers() {
        var options = options(BazaarWidgetOptions.EmbeddedSideDisplay.Relevant);
        var book = book(Optional.of(BazaarWidgetViewData.OrderSide.Sell));

        assertFalse(OrderBookWidget.showsEmbeddedSide(
            options, book, BazaarWidgetViewData.OrderSide.Buy
        ));
        assertTrue(OrderBookWidget.showsEmbeddedSide(
            options, book, BazaarWidgetViewData.OrderSide.Sell
        ));
    }

    @Test
    void configuredSidesRemainVisibleWithoutRestrictionOrWorkflowSide() {
        assertTrue(OrderBookWidget.showsEmbeddedSide(
            options(BazaarWidgetOptions.EmbeddedSideDisplay.Both),
            book(Optional.of(BazaarWidgetViewData.OrderSide.Sell)),
            BazaarWidgetViewData.OrderSide.Buy
        ));
        assertTrue(OrderBookWidget.showsEmbeddedSide(
            options(BazaarWidgetOptions.EmbeddedSideDisplay.Relevant),
            book(Optional.empty()), BazaarWidgetViewData.OrderSide.Buy
        ));
    }

    @Test
    void oneVisibleSideUsesHalfWidthAndTwoSidesUseFullWidth() {
        var sellWorkflow = book(Optional.of(BazaarWidgetViewData.OrderSide.Sell));

        assertEquals(118, OrderBookWidget.embeddedContentWidth(
            options(BazaarWidgetOptions.EmbeddedSideDisplay.Relevant), sellWorkflow
        ));
        assertEquals(1, OrderBookWidget.embeddedVisibleSideCount(
            options(BazaarWidgetOptions.EmbeddedSideDisplay.Relevant), sellWorkflow
        ));
        assertEquals(240, OrderBookWidget.embeddedContentWidth(
            options(BazaarWidgetOptions.EmbeddedSideDisplay.Both), sellWorkflow
        ));
        assertEquals(2, OrderBookWidget.embeddedVisibleSideCount(
            options(BazaarWidgetOptions.EmbeddedSideDisplay.Both), sellWorkflow
        ));
    }

    @Test
    void embeddedMetadataLabelsOrderCountsExplicitly() {
        var entry = new BazaarWidgetViewData.OrderBookEntry(
            BazaarWidgetViewData.OrderSide.Sell, 100, 424, 2
        );

        assertEquals(
            "424 · 2 ord",
            OrderBookWidget.embeddedMetadata(
                entry, options(BazaarWidgetOptions.EmbeddedSideDisplay.Relevant)
            )
        );
    }

    private static BazaarWidgetOptions.EmbeddedOrderBook options(
        BazaarWidgetOptions.EmbeddedSideDisplay sideDisplay
    ) {
        return new BazaarWidgetOptions.EmbeddedOrderBook(
            240,
            3,
            true,
            true,
            true,
            true,
            true,
            true,
            sideDisplay
        );
    }

    private static BazaarWidgetViewData.OrderBookData book(
        Optional<BazaarWidgetViewData.OrderSide> appropriateSide
    ) {
        return new BazaarWidgetViewData.OrderBookData(
            "Product", ItemStack.EMPTY, List.of(), List.of(), appropriateSide
        );
    }
}
