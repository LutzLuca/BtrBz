package com.github.lutzluca.btrbz.core.widgets.ui;

import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import java.util.ArrayList;
import java.util.List;

/** Shared order identity and market-position grammar for Bazaar order widgets. */
public final class BazaarOrderText {
    private BazaarOrderText() {
    }

    public static String orderIdentity(BazaarWidgetViewData.Order order) {
        return order.amountText() + "x @ " + order.unitPriceText();
    }

    public static List<String> marketPositionCandidates(
        BazaarWidgetViewData.Order order,
        boolean showQueue,
        boolean showUndercutGap
    ) {
        if (order.marketInfo().isEmpty()) {
            return List.of();
        }

        var market = order.marketInfo().orElseThrow();

        return switch (order.status()) {
            case Top, Unknown -> List.of();
            case Matched -> queueCandidates(market, showQueue);
            case Undercut -> undercutCandidates(market, showQueue, showUndercutGap);
        };
    }

    private static List<String> undercutCandidates(
        BazaarWidgetViewData.MarketInfo market,
        boolean showQueue,
        boolean showGap
    ) {
        String gap = market.priceDifference().isPresent()
            ? "gap " + BazaarWidgetViewData.formatCompact(market.priceDifference().getAsDouble())
            : "";
        var queue = queueCandidates(market, showQueue);

        if (!showGap || gap.isBlank()) {
            return queue;
        }

        var candidates = new ArrayList<String>();

        for (var queueText : queue) {
            candidates.add(gap + " · " + queueText);
        }

        candidates.addAll(queue);
        candidates.add(gap);

        return distinct(candidates);
    }

    private static List<String> queueCandidates(
        BazaarWidgetViewData.MarketInfo market,
        boolean showQueue
    ) {
        if (!showQueue || market.itemsAhead().isEmpty()) {
            return List.of();
        }

        String items = BazaarWidgetViewData.formatCompact(market.itemsAhead().getAsLong());
        var candidates = new ArrayList<String>();

        if (market.ordersAhead().isPresent()) {
            candidates.add("["
                + BazaarWidgetViewData.formatCompact(market.ordersAhead().getAsInt())
                + "/" + items + "]");
        }

        candidates.add("[" + items + "]");

        return distinct(candidates);
    }

    private static List<String> distinct(List<String> values) {
        return values.stream().filter(value -> !value.isBlank()).distinct().toList();
    }
}
