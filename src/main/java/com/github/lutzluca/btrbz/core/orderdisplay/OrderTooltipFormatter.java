package com.github.lutzluca.btrbz.core.orderdisplay;

import com.github.lutzluca.btrbz.core.appearance.UiColor;
import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.Utils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;

/** Resolves computed sections into current styles when a presentation is prepared. */
public final class OrderTooltipFormatter {
    private OrderTooltipFormatter() {}

    public static List<Component> format(OrderTooltipData data) {
        if (data.unknownProduct() != null) {
            return List.of(Component.literal("Unknown Product: " + data.unknownProduct())
                .withStyle(UiStyles.color(UiColor.Error)));
        }
        var lines = new ArrayList<Component>();
        if (data.status() != null) {
            lines.add(switch (data.status()) {
                case OrderStatus.Top _ -> Component.literal("Best Price!")
                    .withStyle(UiStyles.color(UiColor.Best).withBold(true));
                case OrderStatus.Matched _ -> Component.literal("Matched!")
                    .withStyle(UiStyles.color(UiColor.Matched).withBold(true));
                case OrderStatus.Undercut _ -> Component.literal("Undercut!")
                    .withStyle(UiStyles.color(UiColor.Undercut).withBold(true));
                case OrderStatus.Unknown _ -> Component.literal("Status Unknown")
                    .withStyle(UiStyles.color(UiColor.Unknown));
            });
        }
        if (data.estimatedMinutes() != null) {
            lines.add(Component.literal("Estimated fill time: ").withStyle(UiStyles.label())
                .append(Component.literal(formatDuration(data.estimatedMinutes()))
                    .withStyle(UiStyles.primary())));
        }
        if (data.status() instanceof OrderStatus.Undercut undercut) {
            lines.add(Component.literal("By: ").withStyle(UiStyles.label())
                .append(value(Math.abs(undercut.amount))));
        }
        if (data.queue() != null) {
            var queue = data.queue();
            lines.add(Component.literal("Queue: ").withStyle(UiStyles.label())
                .append(GameUtils.buildQueueComponent(queue.ordersAhead(), queue.itemsAhead(), queue.displayMode())));
        }
        if (data.ownOrder() != null) {
            lines.add(Component.empty());
            lines.add(Component.literal("Your Order").withStyle(UiStyles.heading()));
            lines.add(Component.literal("Price: ").withStyle(UiStyles.label())
                .append(value(data.ownOrder().unitPrice())));
            lines.add(Component.literal("Volume: ").withStyle(UiStyles.label())
                .append(Component.literal(String.valueOf(data.ownOrder().volume())).withStyle(UiStyles.quantity())));
        }
        if (data.prices() != null) {
            lines.add(Component.empty());
            lines.add(Component.literal("Current Prices").withStyle(UiStyles.heading()));
            lines.add(price("Buy Orders: ", data.prices().highestBuyOrderPrice()));
            lines.add(price("Sell Offers: ", data.prices().lowestSellOfferPrice()));
        }
        return List.copyOf(lines);
    }

    private static Component price(String label, Optional<Double> price) {
        return Component.literal(label).withStyle(UiStyles.label())
            .append(price.map(OrderTooltipFormatter::value)
                .orElseGet(() -> Component.literal("N/A").withStyle(UiStyles.muted())));
    }

    private static Component value(double value) {
        return Component.literal(Utils.formatDecimal(value, 1, true)).withStyle(UiStyles.money());
    }

    static String formatDuration(double totalMinutes) {
        if (totalMinutes < 1) {
            return "< 1m";
        }

        long hours = (long) (totalMinutes / 60);
        long minutes = (long) (totalMinutes % 60);

        if (hours > 0) {
            if (minutes > 0) {
                return String.format("%dh %dm", hours, minutes);
            }

            return String.format("%dh", hours);
        }

        return String.format("%dm", minutes);
    }

}
