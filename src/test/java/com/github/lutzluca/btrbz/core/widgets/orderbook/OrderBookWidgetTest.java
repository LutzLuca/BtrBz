package com.github.lutzluca.btrbz.core.widgets.orderbook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.orderbook.OrderBookWidgetConfig.DepthMode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import net.minecraft.network.chat.Component;

class OrderBookWidgetTest {
    @Test
    void appropriateSideOptionRestrictsSellWorkflowsToSellOffers() {
        var options = options(OrderBookPriceWidgetConfig.EmbeddedSideDisplay.Relevant);
        var book = book(Optional.of(BazaarWidgetViewData.OrderSide.Sell));

        assertFalse(OrderBookWidget.showsEmbeddedSide(
            options, book, BazaarWidgetViewData.OrderSide.Buy));
        assertTrue(OrderBookWidget.showsEmbeddedSide(
            options, book, BazaarWidgetViewData.OrderSide.Sell));
    }

    @Test
    void configuredSidesRemainVisibleWithoutRestrictionOrWorkflowSide() {
        assertTrue(OrderBookWidget.showsEmbeddedSide(
            options(OrderBookPriceWidgetConfig.EmbeddedSideDisplay.Both),
            book(Optional.of(BazaarWidgetViewData.OrderSide.Sell)),
            BazaarWidgetViewData.OrderSide.Buy));
        assertTrue(OrderBookWidget.showsEmbeddedSide(
            options(OrderBookPriceWidgetConfig.EmbeddedSideDisplay.Relevant),
            book(Optional.empty()), BazaarWidgetViewData.OrderSide.Buy));
    }

    @Test
    void oneVisibleSideUsesHalfWidthAndTwoSidesUseFullWidth() {
        var sellWorkflow = book(Optional.of(BazaarWidgetViewData.OrderSide.Sell));

        assertEquals(198, OrderBookWidget.embeddedContentWidth(
            options(OrderBookPriceWidgetConfig.EmbeddedSideDisplay.Relevant), sellWorkflow));
        assertEquals(1, OrderBookWidget.embeddedVisibleSideCount(
            options(OrderBookPriceWidgetConfig.EmbeddedSideDisplay.Relevant), sellWorkflow));
        assertEquals(400, OrderBookWidget.embeddedContentWidth(
            options(OrderBookPriceWidgetConfig.EmbeddedSideDisplay.Both), sellWorkflow));
        assertEquals(2, OrderBookWidget.embeddedVisibleSideCount(
            options(OrderBookPriceWidgetConfig.EmbeddedSideDisplay.Both), sellWorkflow));
    }

    @Test
    void embeddedMetadataLabelsOrderCountsExplicitly() {
        var entry = new OrderBookWidgetData.Entry(
            BazaarWidgetViewData.OrderSide.Sell, 100, 424, 2);

        assertEquals(
            "424 items · 2 orders",
            OrderBookWidget.embeddedMetadata(
                entry, options(OrderBookPriceWidgetConfig.EmbeddedSideDisplay.Relevant)));
        var options = options(OrderBookPriceWidgetConfig.EmbeddedSideDisplay.Relevant);
        assertEquals("1 item · 1 order", OrderBookWidget.embeddedMetadata(
            level(BazaarWidgetViewData.OrderSide.Sell, 100, 1, 1), options));
        assertEquals("4,240 items · 1,234 orders", OrderBookWidget.embeddedMetadata(
            level(BazaarWidgetViewData.OrderSide.Sell, 100, 4240, 1234), options));
        options.showOrderCount = false;
        assertEquals("424 items", OrderBookWidget.embeddedMetadata(entry, options));
    }

    @Test
    void depthModesUseSortedExactPricesAndOneScale() {
        var buys = List.of(
            level(BazaarWidgetViewData.OrderSide.Buy, 46_749_992.9, 1, 1),
            level(BazaarWidgetViewData.OrderSide.Buy, 60_000_000.2, 4, 1),
            level(BazaarWidgetViewData.OrderSide.Buy, 46_749_995.5, 1, 1),
            level(BazaarWidgetViewData.OrderSide.Buy, 60_000_000.3, 1, 1));
        var sells = List.of(
            level(BazaarWidgetViewData.OrderSide.Sell, 69_689_998.5, 1, 1),
            level(BazaarWidgetViewData.OrderSide.Sell, 69_689_998.6, 2, 1),
            level(BazaarWidgetViewData.OrderSide.Sell, 69_689_999.6, 1, 1),
            level(BazaarWidgetViewData.OrderSide.Sell, 75_000_000.0, 1, 1),
            level(BazaarWidgetViewData.OrderSide.Sell, 75_425_921.9, 5, 1),
            level(BazaarWidgetViewData.OrderSide.Sell, 84_059_983.1, 2, 1),
            level(BazaarWidgetViewData.OrderSide.Sell, 93_999_998.2, 6, 1),
            level(BazaarWidgetViewData.OrderSide.Sell, 93_999_987.3, 1, 1));
        var snapshot = new OrderBookWidgetData.Snapshot(
            Component.literal("Flash V"), Optional.empty(), buys, sells, Optional.empty());
        var depth = OrderBookDepth.from(snapshot, DepthMode.Cumulative);

        assertEquals(List.of(1L, 5L, 6L, 7L), depth.buy().stream().map(OrderBookDepth.Level::depth).toList());
        assertEquals(List.of(1L, 3L, 4L, 5L, 10L, 12L, 13L, 19L),
            depth.sell().stream().map(OrderBookDepth.Level::depth).toList());
        assertEquals(60_000_000.3, depth.buy().getFirst().entry().price());
        assertEquals("60,000,000.2", depth.buy().get(1).entry().priceText());
        assertEquals(93_999_987.3, depth.sell().get(6).entry().price());
        assertEquals("9,689,998.2 coins", depth.spreadText());
        assertEquals(19, depth.maximum());
        assertEquals(7.0 / 19, depth.buy().getLast().fillFraction(depth.maximum()), 0.000001);
        assertEquals(1, depth.sell().getLast().fillFraction(depth.maximum()));
        assertEquals(depth.buy().getFirst().fillFraction(depth.maximum()),
            depth.sell().getFirst().fillFraction(depth.maximum()));
        assertEquals(5, depth.sell().get(4).entry().quantity());
        assertEquals("1 order", depth.sell().get(4).ordersText());
        assertEquals("10 items at 75,425,921.9 coins or cheaper", depth.sell().get(4).boundText(depth.mode()));
        assertEquals("5 items at 60,000,000.2 coins or higher", depth.buy().get(1).boundText(depth.mode()));

        var relative = OrderBookDepth.from(snapshot, DepthMode.Relative);
        assertEquals(List.of(1L, 4L, 1L, 1L),
            relative.buy().stream().map(OrderBookDepth.Level::depth).toList());
        assertEquals(List.of(1L, 2L, 1L, 1L, 5L, 2L, 1L, 6L),
            relative.sell().stream().map(OrderBookDepth.Level::depth).toList());
        assertEquals(6, relative.maximum());
        assertEquals(4.0 / 6, relative.buy().get(1).fillFraction(relative.maximum()), 0.000001);
        assertEquals(5.0 / 6, relative.sell().get(4).fillFraction(relative.maximum()), 0.000001);
        assertEquals(relative.buy().getFirst().fillFraction(relative.maximum()),
            relative.sell().getFirst().fillFraction(relative.maximum()));
        assertEquals("5 items at 75,425,921.9 coins", relative.sell().get(4).boundText(relative.mode()));
        assertEquals(depth.spreadText(), relative.spreadText());
    }

    @Test
    void missingSidesAndZeroDepthHaveNoSpreadAndWideCountsRemainExact() {
        var empty = OrderBookDepth.from(book(Optional.empty()), DepthMode.Cumulative);
        assertEquals(0, empty.maximum());
        assertTrue(empty.spread().isEmpty());
        var sell = BazaarWidgetViewData.OrderSide.Sell;
        var depth = OrderBookDepth.from(new OrderBookWidgetData.Snapshot(
            Component.literal("Product"), Optional.empty(), List.of(), List.of(
                level(sell, 101.2, 3_000_000_000L, 9),
                level(sell, 101.1, 0, 0),
                level(sell, 101.3, 3_000_000_000L, 2)),
            Optional.empty()), DepthMode.Cumulative);
        assertTrue(depth.spread().isEmpty());
        assertEquals("Unavailable", depth.spreadText());
        assertEquals(0, depth.sell().getFirst().fillFraction(depth.maximum()));
        assertEquals(0, depth.sell().getFirst().fillFraction(0));
        assertEquals(6_000_000_000L, depth.maximum());
        assertEquals("6,000,000,000", depth.sell().getLast().depthText());
        assertEquals("9 orders", depth.sell().get(1).ordersText());
        assertEquals("3,000,000,000", depth.sell().get(1).entry().quantityText());
    }

    private static OrderBookWidgetData.Entry level(
        BazaarWidgetViewData.OrderSide side,
        double price,
        long quantity,
        long orders
    ) {
        return new OrderBookWidgetData.Entry(side, price, quantity, orders);
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
            Component.literal("Product"), Optional.empty(), List.of(), List.of(), appropriateSide);
    }
}
