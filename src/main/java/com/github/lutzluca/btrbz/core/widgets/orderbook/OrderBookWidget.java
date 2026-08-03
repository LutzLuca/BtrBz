package com.github.lutzluca.btrbz.core.widgets.orderbook;

import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import java.util.ArrayList;

/** Pure order-book presentation decisions shared by the retained full and embedded views. */
public final class OrderBookWidget {
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
        if (showsEmbeddedSide(options, book, BazaarWidgetViewData.OrderSide.Buy)) visibleSides++;
        if (showsEmbeddedSide(options, book, BazaarWidgetViewData.OrderSide.Sell)) visibleSides++;
        return visibleSides;
    }

    public static int contentWidth(OrderBookWidgetConfig options) {
        return options.contentWidth;
    }

    public static int sideWidth(OrderBookWidgetConfig options) {
        return options.layout == OrderBookWidgetConfig.BookLayout.Split
            ? Math.max(1, (contentWidth(options) - 2) / 2)
            : contentWidth(options);
    }

    static String embeddedMetadata(
        OrderBookWidgetData.Entry entry,
        OrderBookPriceWidgetConfig options
    ) {
        var parts = new ArrayList<String>();
        parts.add(entry.quantityText() + " items");
        if (options.showOrderCount) parts.add(entry.orders() + " orders");
        return String.join(" · ", parts);
    }
}
