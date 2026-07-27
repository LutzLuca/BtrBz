package com.github.lutzluca.btrbz.core.widgets.ui;

import com.github.lutzluca.btrbz.core.widgets.ui.WidgetDisplayOptions.PriceDisplay;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetDisplayOptions.QueueDisplay;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetDisplayOptions.UndercutDetail;
import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import java.util.ArrayList;
import java.util.List;

/** Shared text grammar for the HUD and Bazaar tracked-order rows. */
public final class BazaarOrderText {
    private BazaarOrderText() {}

    public static List<String> optionalDetails(
        BazaarWidgetViewData.Order order,
        boolean showVolume,
        PriceDisplay priceDisplay,
        boolean showMarketInfo
    ) {
        var details = new ArrayList<String>();
        if (showVolume) details.add(order.amountText() + "x");
        if (priceDisplay == PriceDisplay.Unit
            || priceDisplay == PriceDisplay.Both) {
            details.add("@ " + order.unitPriceText());
        }
        if (priceDisplay == PriceDisplay.Total
            || priceDisplay == PriceDisplay.Both) {
            details.add("total " + order.totalPriceText());
        }
        if (showMarketInfo) order.marketInfo().map(BazaarOrderText::marketText)
            .filter(value -> !value.isBlank())
            .ifPresent(details::add);
        return List.copyOf(details);
    }

    public static String joined(List<String> details) {
        return String.join(" · ", details);
    }

    public static List<String> hudMarketCandidates(
        BazaarWidgetViewData.Order order,
        QueueDisplay queueDisplay,
        UndercutDetail undercutDetail
    ) {
        if (order.marketInfo().isEmpty()) return List.of();
        var market = order.marketInfo().orElseThrow();
        return switch (order.status()) {
            case Top, Unknown -> List.of();
            case Matched -> queueCandidates(market, queueDisplay);
            case Undercut -> undercutCandidates(market, queueDisplay, undercutDetail);
        };
    }

    private static List<String> undercutCandidates(
        BazaarWidgetViewData.MarketInfo market,
        QueueDisplay queueDisplay,
        UndercutDetail undercutDetail
    ) {
        if (undercutDetail == UndercutDetail.Hidden) return List.of();
        String gap = market.priceDifference().isPresent()
            ? "gap " + BazaarWidgetViewData.formatPrice(market.priceDifference().getAsDouble())
            : "";
        var queue = queueCandidates(market, queueDisplay);
        boolean showGap = undercutDetail == UndercutDetail.PriceGap
            || undercutDetail == UndercutDetail.PriceGapAndQueue;
        boolean showQueue = undercutDetail == UndercutDetail.Queue
            || undercutDetail == UndercutDetail.PriceGapAndQueue;

        var candidates = new ArrayList<String>();
        if (showGap && !gap.isBlank() && showQueue) {
            for (var queueText : queue) candidates.add(gap + " · " + queueText);
        }
        if (showGap && !gap.isBlank()) candidates.add(gap);
        else if (showQueue) candidates.addAll(queue);
        return distinct(candidates);
    }

    private static List<String> queueCandidates(
        BazaarWidgetViewData.MarketInfo market,
        QueueDisplay display
    ) {
        if (display == QueueDisplay.Hidden || market.itemsAhead().isEmpty()) {
            return List.of();
        }
        String items = BazaarWidgetViewData.formatCompact(market.itemsAhead().getAsLong());
        var candidates = new ArrayList<String>();
        if (display == QueueDisplay.OrdersAndItems && market.ordersAhead().isPresent()) {
            candidates.add(BazaarWidgetViewData.formatCompact(market.ordersAhead().getAsInt())
                + "o / " + items + "i ahead");
        }
        candidates.add(items + " ahead");
        return distinct(candidates);
    }

    private static List<String> distinct(List<String> values) {
        return values.stream().filter(value -> !value.isBlank()).distinct().toList();
    }

    private static String marketText(BazaarWidgetViewData.MarketInfo market) {
        if (market.bestPrice().isPresent()) {
            String text = "best " + BazaarWidgetViewData.formatPrice(market.bestPrice().getAsDouble());
            if (market.priceDifference().isPresent()) {
                text += " · " + BazaarWidgetViewData.formatPrice(market.priceDifference().getAsDouble()) + " away";
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
                parts.add(BazaarWidgetViewData.formatCompact(market.itemsAhead().getAsLong()) + " items ahead");
            }
            return String.join(" · ", parts);
        }
        return "";
    }
}
