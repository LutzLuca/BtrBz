package com.github.lutzluca.btrbz.core.widgets.orderbook;

import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import java.util.ArrayList;

/** Compact order-book visibility, sizing and metadata presentation. */
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

    static String embeddedMetadata(
        OrderBookWidgetData.Entry entry,
        OrderBookPriceWidgetConfig options
    ) {
        var parts = new ArrayList<String>();
        parts.add(entry.quantityText() + (entry.quantity() == 1 ? " item" : " items"));

        if (options.showOrderCount) {
            parts.add(BazaarWidgetViewData.formatInt(entry.orders()) + (entry.orders() == 1 ? " order" : " orders"));
        }

        return String.join(" · ", parts);
    }
}
