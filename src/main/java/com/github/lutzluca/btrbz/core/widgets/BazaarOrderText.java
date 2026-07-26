package com.github.lutzluca.btrbz.core.widgets;

import java.util.ArrayList;
import java.util.List;

/** Shared text grammar for the HUD and Bazaar tracked-order rows. */
final class BazaarOrderText {
    private BazaarOrderText() {}

    static List<String> optionalDetails(
        BazaarData.Order order,
        boolean showVolume,
        BazaarWidgetOptions.PriceDisplay priceDisplay,
        boolean showMarketInfo
    ) {
        var details = new ArrayList<String>();
        if (showVolume) details.add(order.amountText() + "x");
        if (priceDisplay == BazaarWidgetOptions.PriceDisplay.UNIT
            || priceDisplay == BazaarWidgetOptions.PriceDisplay.BOTH) {
            details.add("@ " + order.unitPriceText());
        }
        if (priceDisplay == BazaarWidgetOptions.PriceDisplay.TOTAL
            || priceDisplay == BazaarWidgetOptions.PriceDisplay.BOTH) {
            details.add("total " + order.totalPriceText());
        }
        if (showMarketInfo) order.marketInfo().map(BazaarOrderText::marketText)
            .filter(value -> !value.isBlank())
            .ifPresent(details::add);
        return List.copyOf(details);
    }

    static String joined(List<String> details) {
        return String.join(" · ", details);
    }

    static List<String> hudMarketCandidates(
        BazaarData.Order order,
        BazaarWidgetOptions.QueueDisplay queueDisplay,
        BazaarWidgetOptions.UndercutDetail undercutDetail
    ) {
        if (order.marketInfo().isEmpty()) return List.of();
        var market = order.marketInfo().orElseThrow();
        return switch (order.status()) {
            case TOP, UNKNOWN -> List.of();
            case MATCHED -> queueCandidates(market, queueDisplay);
            case UNDERCUT -> undercutCandidates(market, queueDisplay, undercutDetail);
        };
    }

    private static List<String> undercutCandidates(
        BazaarData.MarketInfo market,
        BazaarWidgetOptions.QueueDisplay queueDisplay,
        BazaarWidgetOptions.UndercutDetail undercutDetail
    ) {
        if (undercutDetail == BazaarWidgetOptions.UndercutDetail.HIDDEN) return List.of();
        String gap = market.priceDifference().isPresent()
            ? "gap " + BazaarData.formatPrice(market.priceDifference().getAsDouble())
            : "";
        var queue = queueCandidates(market, queueDisplay);
        boolean showGap = undercutDetail == BazaarWidgetOptions.UndercutDetail.PRICE_GAP
            || undercutDetail == BazaarWidgetOptions.UndercutDetail.PRICE_GAP_AND_QUEUE;
        boolean showQueue = undercutDetail == BazaarWidgetOptions.UndercutDetail.QUEUE
            || undercutDetail == BazaarWidgetOptions.UndercutDetail.PRICE_GAP_AND_QUEUE;

        var candidates = new ArrayList<String>();
        if (showGap && !gap.isBlank() && showQueue) {
            for (var queueText : queue) candidates.add(gap + " · " + queueText);
        }
        if (showGap && !gap.isBlank()) candidates.add(gap);
        else if (showQueue) candidates.addAll(queue);
        return distinct(candidates);
    }

    private static List<String> queueCandidates(
        BazaarData.MarketInfo market,
        BazaarWidgetOptions.QueueDisplay display
    ) {
        if (display == BazaarWidgetOptions.QueueDisplay.HIDDEN || market.itemsAhead().isEmpty()) {
            return List.of();
        }
        String items = BazaarData.formatCompact(market.itemsAhead().getAsLong());
        var candidates = new ArrayList<String>();
        if (display == BazaarWidgetOptions.QueueDisplay.ORDERS_AND_ITEMS && market.ordersAhead().isPresent()) {
            candidates.add(BazaarData.formatCompact(market.ordersAhead().getAsInt())
                + "o / " + items + "i ahead");
        }
        candidates.add(items + " ahead");
        return distinct(candidates);
    }

    private static List<String> distinct(List<String> values) {
        return values.stream().filter(value -> !value.isBlank()).distinct().toList();
    }

    private static String marketText(BazaarData.MarketInfo market) {
        if (market.bestPrice().isPresent()) {
            String text = "best " + BazaarData.formatPrice(market.bestPrice().getAsDouble());
            if (market.priceDifference().isPresent()) {
                text += " · " + BazaarData.formatPrice(market.priceDifference().getAsDouble()) + " away";
            }
            return text;
        }
        if (market.ordersAhead().isPresent() || market.itemsAhead().isPresent()) {
            var parts = new ArrayList<String>();
            if (market.ordersAhead().isPresent()) {
                int count = market.ordersAhead().getAsInt();
                parts.add(count + (count == 1 ? " order" : " orders"));
            }
            if (market.itemsAhead().isPresent()) {
                parts.add(BazaarData.formatCompact(market.itemsAhead().getAsLong()) + " items ahead");
            }
            return String.join(" · ", parts);
        }
        return "";
    }
}
