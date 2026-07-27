package com.github.lutzluca.btrbz.core.widgets.orderbook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetDisplayOptions;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

class OrderBookWidgetTest {
    @Test
    void appropriateSideOptionRestrictsSellWorkflowsToSellOffers() {
        var options = options(OrderBookPriceWidgetConfig.EmbeddedSideDisplay.Relevant);
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
            options(OrderBookPriceWidgetConfig.EmbeddedSideDisplay.Both),
            book(Optional.of(BazaarWidgetViewData.OrderSide.Sell)),
            BazaarWidgetViewData.OrderSide.Buy
        ));
        assertTrue(OrderBookWidget.showsEmbeddedSide(
            options(OrderBookPriceWidgetConfig.EmbeddedSideDisplay.Relevant),
            book(Optional.empty()), BazaarWidgetViewData.OrderSide.Buy
        ));
    }

    @Test
    void oneVisibleSideUsesHalfWidthAndTwoSidesUseFullWidth() {
        var sellWorkflow = book(Optional.of(BazaarWidgetViewData.OrderSide.Sell));

        assertEquals(118, OrderBookWidget.embeddedContentWidth(
            options(OrderBookPriceWidgetConfig.EmbeddedSideDisplay.Relevant), sellWorkflow
        ));
        assertEquals(1, OrderBookWidget.embeddedVisibleSideCount(
            options(OrderBookPriceWidgetConfig.EmbeddedSideDisplay.Relevant), sellWorkflow
        ));
        assertEquals(240, OrderBookWidget.embeddedContentWidth(
            options(OrderBookPriceWidgetConfig.EmbeddedSideDisplay.Both), sellWorkflow
        ));
        assertEquals(2, OrderBookWidget.embeddedVisibleSideCount(
            options(OrderBookPriceWidgetConfig.EmbeddedSideDisplay.Both), sellWorkflow
        ));
    }

    @Test
    void embeddedMetadataLabelsOrderCountsExplicitly() {
        var entry = new OrderBookWidgetData.Entry(
            BazaarWidgetViewData.OrderSide.Sell, 100, 424, 2
        );

        assertEquals(
            "424 · 2 ord",
            OrderBookWidget.embeddedMetadata(
                entry, options(OrderBookPriceWidgetConfig.EmbeddedSideDisplay.Relevant)
            )
        );
    }

    private static OrderBookPriceWidgetConfig options(
        OrderBookPriceWidgetConfig.EmbeddedSideDisplay sideDisplay
    ) {
        var options = new OrderBookPriceWidgetConfig();
        options.sideDisplay = sideDisplay;
        return options;
    }

    private static OrderBookWidgetData.Snapshot book(
        Optional<BazaarWidgetViewData.OrderSide> appropriateSide
    ) {
        return new OrderBookWidgetData.Snapshot(
            "Product", ItemStack.EMPTY, List.of(), List.of(), appropriateSide
        );
    }
}
