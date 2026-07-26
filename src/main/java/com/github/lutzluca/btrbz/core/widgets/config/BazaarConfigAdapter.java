package com.github.lutzluca.btrbz.core.widgets.config;

import com.github.lutzluca.btrbz.core.config.ConfigManager;

/** Maps fixed YACL fields to immutable presentation options. */
public final class BazaarConfigAdapter {
    private BazaarConfigAdapter() {}

    public static BazaarWidgetOptions read() {
        return read(ConfigManager.get().widgets);
    }

    static BazaarWidgetOptions read(WidgetsConfig widgets) {
        var hud = widgets.bazaarOrders;
        var tracked = widgets.trackedOrders;
        var value = widgets.orderValue;
        var book = widgets.orderBookScreen;
        var embedded = widgets.orderBookPrice;
        var bookmarks = widgets.bookmarks;
        var presets = widgets.orderPresets;
        var limit = widgets.orderLimit;
        var diff = widgets.priceDiff;
        return new BazaarWidgetOptions(
            new BazaarWidgetOptions.Hud(
                hud.mode, hud.visibleOrders, hud.contentWidth, hud.abbreviateEnchanted,
                hud.hideWhenEmpty, hud.showItem, hud.showVolume, hud.priceDisplay,
                hud.queueDisplay, hud.undercutDetail
            ),
            new BazaarWidgetOptions.TrackedOrders(
                tracked.contentWidth, tracked.visibleRows, tracked.fitToContent,
                tracked.layout, tracked.sort, tracked.abbreviateEnchanted,
                tracked.hideWhenEmpty, tracked.showStatusSummary, tracked.showItem,
                tracked.showVolume, tracked.priceDisplay, tracked.showMarketInfo,
                tracked.showProgress
            ),
            new BazaarWidgetOptions.OrderValue(
                value.contentWidth, value.display, value.numberStyle, value.showCoinsSuffix,
                value.colorMode, value.buyLocked, value.buyItems, value.sellClaimable,
                value.sellPending
            ),
            new BazaarWidgetOptions.OrderBook(
                book.contentWidth, book.visibleRows, book.layout, book.numberStyle,
                book.showOrderCount, book.showHeader, book.showItem
            ),
            new BazaarWidgetOptions.EmbeddedOrderBook(
                embedded.contentWidth, embedded.visibleRows, embedded.showBuy, embedded.showSell,
                embedded.showAmounts, embedded.showOrderCount,
                embedded.showHeader, embedded.showItem, embedded.sideDisplay
            ),
            new BazaarWidgetOptions.Bookmarks(
                bookmarks.contentWidth, bookmarks.visibleRows, bookmarks.fitToContent,
                bookmarks.sort, bookmarks.hideWhenEmpty, bookmarks.showItems,
                bookmarks.showIndicators, bookmarks.abbreviateEnchanted
            ),
            new BazaarWidgetOptions.Presets(
                presets.contentWidth, presets.maximum, presets.clipboard,
                presets.showDisabled, presets.showTooltips
            ),
            new BazaarWidgetOptions.OrderLimit(
                limit.contentWidth, limit.display, limit.numberStyle, limit.showHeader,
                limit.warningThreshold, limit.criticalThreshold
            ),
            new BazaarWidgetOptions.PriceDiff(
                diff.contentWidth, diff.display, diff.numberStyle, diff.showItems,
                diff.showProduct
            )
        );
    }
}
