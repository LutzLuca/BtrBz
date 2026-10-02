package com.github.lutzluca.btrbz.core.widgets.ui;

import com.github.lutzluca.btrbz.core.ui.UiStyles;

import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/** Shared order identity and market-position grammar for Bazaar order widgets. */
public final class BazaarOrderText {
    private BazaarOrderText() {}

    public static Component orderIdentity(BazaarWidgetViewData.Order order) {
        return Component.literal(order.amountText()).withStyle(UiStyles.quantity())
            .append(Component.literal("x @ ").withStyle(UiStyles.label()))
            .append(Component.literal(order.unitPriceText()).withStyle(UiStyles.money()));
    }

    public static List<Component> marketPositionCandidates(
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

    private static List<Component> undercutCandidates(
        BazaarWidgetViewData.MarketInfo market,
        boolean showQueue,
        boolean showGap
    ) {
        var queue = queueCandidates(market, showQueue);

        if (!showGap || market.priceDifference().isEmpty()) {
            return queue;
        }

        var gap = Component.literal("gap ").withStyle(UiStyles.label())
            .append(Component.literal(BazaarWidgetViewData.formatCompact(market.priceDifference().getAsDouble()))
                .withStyle(UiStyles.money()));
        var candidates = new ArrayList<Component>();

        for (var queueText : queue) {
            candidates.add(gap.copy().append(Component.literal(" · ").withStyle(UiStyles.label())).append(queueText));
        }

        candidates.addAll(queue);
        candidates.add(gap);

        return List.copyOf(candidates);
    }

    private static List<Component> queueCandidates(
        BazaarWidgetViewData.MarketInfo market,
        boolean showQueue
    ) {
        if (!showQueue || market.itemsAhead().isEmpty()) {
            return List.of();
        }

        String items = BazaarWidgetViewData.formatCompact(market.itemsAhead().getAsLong());
        var candidates = new ArrayList<Component>();

        if (market.ordersAhead().isPresent()) {
            candidates.add(Component.literal("[").withStyle(UiStyles.label())
                .append(Component.literal(BazaarWidgetViewData.formatCompact(market.ordersAhead().getAsInt()))
                    .withStyle(UiStyles.quantity()))
                .append(Component.literal("/").withStyle(UiStyles.label()))
                .append(Component.literal(items).withStyle(UiStyles.quantity()))
                .append(Component.literal("]").withStyle(UiStyles.label())));
        }

        candidates.add(Component.literal("[").withStyle(UiStyles.label())
            .append(Component.literal(items).withStyle(UiStyles.quantity()))
            .append(Component.literal("]").withStyle(UiStyles.label())));

        return List.copyOf(candidates);
    }
}
