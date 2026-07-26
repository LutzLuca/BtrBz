package com.github.lutzluca.btrbz.core.widgets;

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
                HudMode.DETAILED,
                6,
                200,
                false,
                false,
                true,
                true,
                PriceDisplay.UNIT,
                QueueDisplay.ITEMS,
                UndercutDetail.PRICE_GAP_AND_QUEUE
            ),
            new TrackedOrders(
                218, 6, TrackedLayout.STANDARD, TrackedSort.MANUAL, false,
                true, true, true, PriceDisplay.UNIT, true, true
            ),
            new OrderValue(205, ValueDisplay.DETAILED, NumberStyle.COMPACT, true, ColorMode.SEMANTIC,
                true, true, true, true),
            new OrderBook(330, 5, BookLayout.SPLIT, NumberStyle.EXACT, true, true),
            new EmbeddedOrderBook(240, 3, EmbeddedBookLayout.SPLIT, true, true, true, true, true),
            new Bookmarks(200, 6, BookmarkSort.MANUAL, true, true, false),
            new Presets(100, true, true, true, true),
            new OrderLimit(180, LimitDisplay.USED_LIMIT, NumberStyle.COMPACT, true, 75, 90),
            new PriceDiff(190, DiffDisplay.BOTH, NumberStyle.COMPACT, true, true)
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

    public enum NumberStyle { COMPACT, EXACT }
    public enum PriceDisplay { NONE, UNIT, TOTAL, BOTH }
    public enum QueueDisplay { ITEMS, ORDERS_AND_ITEMS, HIDDEN }
    public enum UndercutDetail { PRICE_GAP, QUEUE, PRICE_GAP_AND_QUEUE, HIDDEN }
    public enum HudMode { DETAILED, STATUS_COUNTS }
    public enum TrackedLayout { STANDARD, COMPACT }
    public enum TrackedSort { MANUAL, NEWEST, OLDEST, STATUS, SIDE, PRODUCT, VALUE }
    public enum ValueDisplay { DETAILED, SUMMARY }
    public enum ColorMode { SEMANTIC, NEUTRAL }
    public enum BookLayout { SPLIT, BUY_ONLY, SELL_ONLY }
    public enum EmbeddedBookLayout { SPLIT, STACKED }
    public enum BookmarkSort { MANUAL, ALPHABETICAL }
    public enum LimitDisplay { USED_LIMIT, REMAINING, PERCENTAGE, COMPACT }
    public enum DiffDisplay { BOTH, PER_ITEM, TOTAL }
}
