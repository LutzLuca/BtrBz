package com.github.lutzluca.btrbz.core.widgets;

import com.github.lutzluca.btrbz.widgets.framework.WidgetRenderContext;

/** Supplies the presentation-ready snapshots used by the Bazaar widgets. */
public interface BazaarDataProvider {
    BazaarData.OrdersData orders(WidgetRenderContext context);

    BazaarData.OrderValueData orderValue(WidgetRenderContext context);

    BazaarData.OrderBookData orderBook(WidgetRenderContext context);

    BazaarData.BookmarksData bookmarks(WidgetRenderContext context);

    BazaarData.PresetsData presets(WidgetRenderContext context);

    BazaarData.DailyLimitData dailyLimit(WidgetRenderContext context);

    BazaarData.PriceDifferenceData priceDifference(WidgetRenderContext context);
}
