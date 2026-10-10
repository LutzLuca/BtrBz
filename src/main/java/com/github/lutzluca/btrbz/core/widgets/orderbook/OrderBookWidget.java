package com.github.lutzluca.btrbz.core.widgets.orderbook;

import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;

/** Compact order-book visibility and preferred sizing. */
final class OrderBookWidget {
    private OrderBookWidget() {}

    static boolean showsEmbeddedSide(
        OrderBookPriceWidgetConfig options,
        OrderBookWidgetData.Snapshot book,
        BazaarWidgetViewData.OrderSide side
    ) {
        return options.sideDisplay == OrderBookPriceWidgetConfig.EmbeddedSideDisplay.Both
            || book.appropriateSide().isEmpty()
            || book.appropriateSide().filter(side::equals).isPresent();
    }

    static int embeddedContentWidth(
        OrderBookPriceWidgetConfig options,
        OrderBookWidgetData.Snapshot book
    ) {
        return embeddedVisibleSideCount(options, book) <= 1
            ? Math.max(1, (options.contentWidth - 4) / 2)
            : options.contentWidth;
    }

    static int embeddedVisibleSideCount(
        OrderBookPriceWidgetConfig options,
        OrderBookWidgetData.Snapshot book
    ) {
        int visibleSides = 0;

        if (showsEmbeddedSide(options, book, BazaarWidgetViewData.OrderSide.Buy)) {
            visibleSides++;
        }

        if (showsEmbeddedSide(options, book, BazaarWidgetViewData.OrderSide.Sell)) {
            visibleSides++;
        }

        return visibleSides;
    }

}
