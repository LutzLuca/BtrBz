package com.github.lutzluca.btrbz.core.bazaariteminfo;

import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import com.github.lutzluca.coflnet.BazaarHistoryPoint;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Pure, sectioned tooltip content for one selected History sample. */
final class BazaarHistoryTooltip {
    static final int SECTION_GAP = 5;

    private BazaarHistoryTooltip() {}

    static Content create(
        BazaarHistoryPoint point,
        boolean showBuy,
        boolean showSell,
        boolean showBands,
        BazaarItemInfoConfig.ActivityMode activityMode,
        ZoneId zone
    ) {
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(activityMode, "activityMode");
        Objects.requireNonNull(zone, "zone");
        var lines = new ArrayList<Line>();
        lines.add(new Line(TimeAxisTicks.tooltip(point.timestamp(), zone), BazaarStyles.PRIMARY_TEXT, 0));

        var prices = new ArrayList<Line>();
        if (showBuy && Double.isFinite(point.buy())) {
            prices.add(new Line("Buy price  " + BazaarWidgetViewData.formatPrice(point.buy()),
                BazaarStyles.BUY_ACCENT, 1));
            addRange(prices, point.minBuy(), point.maxBuy(), showBands);
        }
        if (showSell && Double.isFinite(point.sell())) {
            prices.add(new Line("Sell price  " + BazaarWidgetViewData.formatPrice(point.sell()),
                BazaarStyles.SELL_ACCENT, 1));
            addRange(prices, point.minSell(), point.maxSell(), showBands);
        }
        if (!prices.isEmpty()) {
            lines.add(new Line("Prices", BazaarStyles.MUTED_TEXT, SECTION_GAP));
            lines.addAll(prices);
        }

        var activity = new ArrayList<Line>();
        if (activityMode == BazaarItemInfoConfig.ActivityMode.IntervalItems) {
            if (showBuy && point.buyVolume() >= 0) {
                activity.add(new Line("Buy items  " + BazaarWidgetViewData.formatInt(point.buyVolume()),
                    BazaarStyles.BUY_ACCENT, 1));
            }
            if (showSell && point.sellVolume() >= 0) {
                activity.add(new Line("Sell items  " + BazaarWidgetViewData.formatInt(point.sellVolume()),
                    BazaarStyles.SELL_ACCENT, 1));
            }
        }
        if (!activity.isEmpty()) {
            lines.add(new Line("Interval activity", BazaarStyles.MUTED_TEXT, SECTION_GAP));
            lines.addAll(activity);
        }
        return new Content(lines);
    }

    private static void addRange(List<Line> lines, Double low, Double high, boolean showBands) {
        if (!showBands || low == null || high == null || !Double.isFinite(low) || !Double.isFinite(high)) {
            return;
        }
        lines.add(new Line(
            "Range  " + BazaarWidgetViewData.formatPrice(Math.min(low, high))
                + " to " + BazaarWidgetViewData.formatPrice(Math.max(low, high)),
            BazaarStyles.SECONDARY_TEXT,
            0));
    }

    record Content(List<Line> lines) {
        Content {
            lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        }
    }

    record Line(String text, int color, int gapBefore) {
        Line {
            text = Objects.requireNonNull(text, "text");
            gapBefore = Math.max(0, gapBefore);
        }
    }
}
