package com.github.lutzluca.btrbz.core.widgets.config;

import java.util.Objects;

/** Feature-owned content and behavior options shared by runtime and manager previews. */
public record BazaarWidgetOptions(
    Hud hud,
    TrackedOrders trackedOrders,
    OrderValue orderValue,
    OrderBook orderBook,
    EmbeddedOrderBook embeddedOrderBook,
    Bookmarks bookmarks,
    Presets presets,
    OrderLimit orderLimit,
    PriceDiff priceDiff
) {
    public BazaarWidgetOptions {
        Objects.requireNonNull(hud, "hud");
        Objects.requireNonNull(trackedOrders, "trackedOrders");
        Objects.requireNonNull(orderValue, "orderValue");
        Objects.requireNonNull(orderBook, "orderBook");
        Objects.requireNonNull(embeddedOrderBook, "embeddedOrderBook");
        Objects.requireNonNull(bookmarks, "bookmarks");
        Objects.requireNonNull(presets, "presets");
        Objects.requireNonNull(orderLimit, "orderLimit");
        Objects.requireNonNull(priceDiff, "priceDiff");
    }

    public static BazaarWidgetOptions defaults() {
        return new BazaarWidgetOptions(
            new Hud(
                HudMode.Detailed,
                6,
                200,
                false,
                false,
                true,
                true,
                PriceDisplay.Unit,
                QueueDisplay.Items,
                UndercutDetail.PriceGapAndQueue
            ),
            new TrackedOrders(
                218, 6, TrackedLayout.Standard, TrackedSort.Manual, false,
                true, true, true, PriceDisplay.Unit, true, true
            ),
            new OrderValue(205, ValueDisplay.Detailed, NumberStyle.Compact, true, ColorMode.Semantic,
                true, true, true, true),
            new OrderBook(330, 5, BookLayout.Split, NumberStyle.Exact, true, true),
            new EmbeddedOrderBook(240, 3, EmbeddedBookLayout.Split, true, true, true, true, true),
            new Bookmarks(200, 6, BookmarkSort.Manual, true, true, false),
            new Presets(100, true, true, true, true),
            new OrderLimit(180, LimitDisplay.UsedLimit, NumberStyle.Compact, true, 75, 90),
            new PriceDiff(190, DiffDisplay.Both, NumberStyle.Compact, true, true)
        );
    }

    public record Hud(
        HudMode mode,
        int visibleOrders,
        int contentWidth,
        boolean abbreviateEnchanted,
        boolean hideWhenEmpty,
        boolean showItem,
        boolean showVolume,
        PriceDisplay priceDisplay,
        QueueDisplay queueDisplay,
        UndercutDetail undercutDetail
    ) {}

    public record TrackedOrders(
        int contentWidth,
        int visibleRows,
        TrackedLayout layout,
        TrackedSort sort,
        boolean abbreviateEnchanted,
        boolean showStatusSummary,
        boolean showItem,
        boolean showVolume,
        PriceDisplay priceDisplay,
        boolean showMarketInfo,
        boolean showProgress
    ) {}

    public record OrderValue(
        int contentWidth,
        ValueDisplay display,
        NumberStyle numberStyle,
        boolean showCoinsSuffix,
        ColorMode colorMode,
        boolean buyLocked,
        boolean buyItems,
        boolean sellClaimable,
        boolean sellPending
    ) {}

    public record OrderBook(
        int contentWidth,
        int visibleRows,
        BookLayout layout,
        NumberStyle numberStyle,
        boolean showOrderCount,
        boolean showHeader
    ) {}

    public record EmbeddedOrderBook(
        int contentWidth,
        int visibleRows,
        EmbeddedBookLayout layout,
        boolean showBuy,
        boolean showSell,
        boolean showAmounts,
        boolean showOrderCount,
        boolean showHeader
    ) {}

    public record Bookmarks(
        int contentWidth,
        int visibleRows,
        BookmarkSort sort,
        boolean showItems,
        boolean showIndicators,
        boolean abbreviateEnchanted
    ) {}

    public record Presets(
        int contentWidth,
        boolean maximum,
        boolean clipboard,
        boolean showDisabled,
        boolean showTooltips
    ) {}

    public record OrderLimit(
        int contentWidth,
        LimitDisplay display,
        NumberStyle numberStyle,
        boolean showHeader,
        int warningThreshold,
        int criticalThreshold
    ) {}

    public record PriceDiff(
        int contentWidth,
        DiffDisplay display,
        NumberStyle numberStyle,
        boolean showItems,
        boolean showProduct
    ) {}

    public enum NumberStyle { Compact, Exact }
    public enum PriceDisplay { None, Unit, Total, Both }
    public enum QueueDisplay { Items, OrdersAndItems, Hidden }
    public enum UndercutDetail { PriceGap, Queue, PriceGapAndQueue, Hidden }
    public enum HudMode { Detailed, StatusCounts }
    public enum TrackedLayout { Standard, Compact }
    public enum TrackedSort { Manual, Newest, Oldest, Status, Side, Product, Value }
    public enum ValueDisplay { Detailed, Summary }
    public enum ColorMode { Semantic, Neutral }
    public enum BookLayout { Split, BuyOnly, SellOnly }
    public enum EmbeddedBookLayout { Split, Stacked }
    public enum BookmarkSort { Manual, Alphabetical }
    public enum LimitDisplay { UsedLimit, Remaining, Percentage, Compact }
    public enum DiffDisplay { Both, PerItem, Total }
}
