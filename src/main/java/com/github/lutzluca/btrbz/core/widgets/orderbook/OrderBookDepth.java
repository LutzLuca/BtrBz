package com.github.lutzluca.btrbz.core.widgets.orderbook;

import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.orderbook.OrderBookHintConfig.Mode;
import com.github.lutzluca.btrbz.core.orderbook.OrderBookHintConfig;
import com.github.lutzluca.btrbz.core.orderbook.OrderBookMatches.Match;
import com.github.lutzluca.btrbz.core.widgets.orderbook.OrderBookWidgetConfig.DepthMode;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Sorted exact price levels and one shared depth scale for the full order book. */
record OrderBookDepth(
    List<OrderBookDepth.Level> buy,
    List<OrderBookDepth.Level> sell,
    long maximum,
    Optional<BigDecimal> spread,
    DepthMode mode
) {
    OrderBookDepth {
        buy = List.copyOf(buy);
        sell = List.copyOf(sell);
    }

    static OrderBookDepth from(OrderBookWidgetData.Snapshot snapshot, DepthMode mode, OrderBookHintConfig hints) {
        var buy = levels(snapshot.buyOffers(), Comparator.comparingDouble(OrderBookWidgetData.Entry::price)
            .reversed(), mode, hints);
        var sell = levels(snapshot.sellOffers(), Comparator.comparingDouble(OrderBookWidgetData.Entry::price), mode,
            hints);
        long maximum = Math.max(maximum(buy), maximum(sell));
        Optional<BigDecimal> spread = buy.isEmpty() || sell.isEmpty()
            ? Optional.empty()
            : Optional.of(BigDecimal.valueOf(sell.getFirst().entry().price())
                .subtract(BigDecimal.valueOf(buy.getFirst().entry().price())));
        return new OrderBookDepth(buy, sell, maximum, spread, mode);
    }

    private static List<Level> levels(
        List<OrderBookWidgetData.Entry> entries,
        Comparator<OrderBookWidgetData.Entry> order,
        DepthMode mode,
        OrderBookHintConfig hints
    ) {
        var levels = new ArrayList<Level>(entries.size());
        long depth = 0;
        for (var entry : entries.stream().sorted(order).toList()) {
            depth = mode == DepthMode.Cumulative ? depth + entry.quantity() : entry.quantity();
            var hint = hints.mode == Mode.Off || entry.personalOrders().count() == 0
                ? Optional.<Hint>empty()
                : Optional.of(
                    new Hint(entry.personalOrders(), hints.mode == Mode.Detailed, hints.showRemainingInWidgetTooltips));
            levels.add(new Level(entry, depth, hint));
        }
        return List.copyOf(levels);
    }

    private static long maximum(List<Level> levels) {
        return levels.stream().mapToLong(Level::depth).max().orElse(0);
    }

    String spreadText() {
        var format = new DecimalFormat("#,##0.0", DecimalFormatSymbols.getInstance(Locale.US));
        return this.spread.map(value -> format.format(value) + " coins").orElse("Unavailable");
    }

    record Hint(Match orders, boolean detailed, boolean showRemaining) {}

    record Level(OrderBookWidgetData.Entry entry, long depth, Optional<Hint> hint) {
        String ordersColumnText() {
            return this.orderCountText() + this.hint.filter(Hint::detailed)
                .map(value -> " (" + value.orders().count() + ")").orElse("");
        }

        String depthText() {
            return BazaarWidgetViewData.formatInt(this.depth);
        }

        String ordersText() {
            return this.orderCountText()
                + (this.entry.orders() == 1 ? " order" : " orders");
        }

        String orderCountText() {
            return BazaarWidgetViewData.formatInt(this.entry.orders());
        }

        String boundText(DepthMode mode) {
            long quantity = mode == DepthMode.Cumulative ? this.depth : this.entry.quantity();
            return BazaarWidgetViewData.formatInt(quantity) + (quantity == 1 ? " item at " : " items at ")
                + this.entry.priceText() + " coins"
                + (mode != DepthMode.Cumulative
                    ? ""
                    : this.entry.side() == BazaarWidgetViewData.OrderSide.Buy ? " or higher" : " or cheaper");
        }

        double fillFraction(long maximum) {
            return maximum > 0 ? (double) this.depth / maximum : 0;
        }
    }
}
