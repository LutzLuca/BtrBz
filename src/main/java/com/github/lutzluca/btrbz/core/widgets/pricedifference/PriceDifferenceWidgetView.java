package com.github.lutzluca.btrbz.core.widgets.pricedifference;

import com.github.lutzluca.btrbz.core.ui.UiComponents;
import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.core.widgets.WidgetView;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.core.widgets.ui.RetainedFlowLayout;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetTooltips;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.utils.Utils;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

final class PriceDifferenceWidgetView
    implements WidgetView<PriceDifferenceWidgetData.Snapshot, PriceDifferenceWidgetConfig, Void> {
    private static final String ACTIVITY_HELP = "API weekly movement divided by 168, for orders on this side. "
        + "The source includes seven-day transacted volume plus live state. This is not recent-hour trading.";
    private static final String TOTAL_HELP = "Sellable inventory times the best-quote spread, before fees. "
        + "This does not account for book depth or guarantee execution at these prices.";
    private final RetainedFlowLayout root = RetainedFlowLayout.vertical(Sizing.fixed(1), Sizing.content());

    PriceDifferenceWidgetView() {
        this.root.gap(3);
    }

    @Override
    public UIComponent root() {
        return this.root;
    }

    @Override
    public void update(
        PriceDifferenceWidgetData.Snapshot data,
        PriceDifferenceWidgetConfig config,
        WidgetSession session,
        Consumer<Void> actions
    ) {
        this.root.clearChildren();
        if (data.product().isEmpty()) {
            return;
        }
        boolean detailed = config.mode == PriceDifferenceWidgetConfig.Mode.Detailed;
        boolean columns = detailed && config.contentWidth >= 260;
        var metrics = this.metrics(data, config);
        var spread = signed(data.perItem(), false, config);
        String inventoryLabel = data.quantity().isPresent()
            ? Utils.formatDecimal(data.quantity().getAsInt(), 0, true) + " items x spread" : "Inventory count";
        var total = data.quantity().isPresent() ? signed(data.total(), true, config) : unavailable();

        int width = Math.max(180, config.contentWidth);
        int sideWidth = Math.max(textWidth("Buy orders"), textWidth("Sell offers"));
        int metricWidth = 0;
        for (var metric : metrics) {
            sideWidth = Math.max(sideWidth, Math.max(textWidth(metric.buy()), textWidth(metric.sell())));
            metricWidth = Math.max(metricWidth, textWidth(metric.label()));
        }
        if (detailed) {
            width = Math.max(width, metricWidth + sideWidth * (columns ? 2 : 1) + (columns ? 24 : 8));
        } else {
            width = Math.max(width, textWidth("Sell offer") + sideWidth + 8);
        }
        width = Math.max(width, textWidth("Spread / item") + textWidth(spread) + 8);
        if (config.showInventoryTotal) {
            width = Math.max(width, textWidth(inventoryLabel) + textWidth(total) + 8);
        }
        this.root.horizontalSizing(Sizing.fixed(width));

        var product = data.product().orElseThrow();
        var header = RetainedFlowLayout.horizontal(Sizing.fill(100), Sizing.content());
        header.verticalAlignment(VerticalAlignment.CENTER);
        header.gap(4);
        product.itemStack().ifPresent(stack -> header.child(UiComponents.icon(stack)));
        var name = label(UiStyles.productName(product.displayName(), false));
        name.horizontalSizing(Sizing.expand(100));
        header.child(name);
        this.root.child(header);

        boolean noMarket = data.prices().highestBuyOrderPrice().isEmpty()
            && data.prices().lowestSellOfferPrice().isEmpty()
            && data.activity().isEmpty();
        this.root.child(label(Component.literal(noMarket ? "Market data unavailable" : "Best quotes in coins / item")
            .withStyle(UiStyles.muted())));

        if (!detailed) {
            this.root
                .child(valueLine(Component.literal("Buy order").withStyle(UiStyles.color(UiStyles.palette().buy())),
                    price(data.prices().highestBuyOrderPrice())));
            this.root
                .child(valueLine(Component.literal("Sell offer").withStyle(UiStyles.color(UiStyles.palette().sell())),
                    price(data.prices().lowestSellOfferPrice())));
        } else if (columns) {
            this.root
                .child(tableRow("", Component.literal("Buy orders").withStyle(UiStyles.color(UiStyles.palette().buy())),
                    Component.literal("Sell offers").withStyle(UiStyles.color(UiStyles.palette().sell())), sideWidth));
            for (var metric : metrics) {
                var row = tableRow(metric.label(), metric.buy(), metric.sell(), sideWidth);
                row.tooltip(WidgetTooltips.wrapped(metric.help()));
                this.root.child(row);
            }
        } else {
            this.addSide("Buy orders", UiStyles.color(UiStyles.palette().buy()), metrics, true);
            this.addSide("Sell offers", UiStyles.color(UiStyles.palette().sell()), metrics, false);
        }

        var differences = RetainedFlowLayout.vertical(Sizing.fill(100), Sizing.content());
        differences.gap(3);
        differences.padding(Insets.top(5));
        differences.surface((graphics, component) -> graphics.fill(component.x(), component.y(),
            component.x() + component.width(), component.y() + 1, UiStyles.palette().outline()));
        differences.child(valueLine(Component.literal("Spread / item").withStyle(UiStyles.primary()), spread));
        if (config.showInventoryTotal) {
            differences.child(valueLine(Component.literal(inventoryLabel).withStyle(UiStyles.label()), total));
        }
        differences.tooltip(WidgetTooltips.wrapped(TOTAL_HELP));
        this.root.child(differences);
        var footnote = label(Component.literal("Quote difference, before fees").withStyle(UiStyles.muted()));
        footnote.horizontalSizing(Sizing.fill(100));
        this.root.child(footnote);
    }

    private List<Metric> metrics(PriceDifferenceWidgetData.Snapshot data, PriceDifferenceWidgetConfig config) {
        var metrics = new ArrayList<Metric>();
        metrics.add(new Metric("Best price", price(data.prices().highestBuyOrderPrice()),
            price(data.prices().lowestSellOfferPrice()), "Best returned quote on each side, in coins per item."));
        if (config.mode == PriceDifferenceWidgetConfig.Mode.Detailed && config.showBookTotals) {
            metrics.add(new Metric("Active orders", activity(data, config, BazaarData.SideActivity::orders, true),
                activity(data, config, BazaarData.SideActivity::orders, false),
                "Active orders across the whole side."));
            metrics.add(new Metric("Listed items", activity(data, config, BazaarData.SideActivity::listedItems, true),
                activity(data, config, BazaarData.SideActivity::listedItems, false),
                "Listed items across the whole side."));
        }
        if (config.mode == PriceDifferenceWidgetConfig.Mode.Detailed && config.showWeeklyActivity) {
            metrics.add(new Metric("Items/h (7d avg)",
                activity(data, config, BazaarData.SideActivity::weeklyAverageHourlyMovement, true),
                activity(data, config, BazaarData.SideActivity::weeklyAverageHourlyMovement, false), ACTIVITY_HELP));
        }
        return metrics;
    }

    private void addSide(String title, Style style, List<Metric> metrics, boolean buy) {
        this.root.child(label(Component.literal(title).withStyle(style)));
        for (var metric : metrics) {
            var row = valueLine(Component.literal(metric.label()).withStyle(UiStyles.label()),
                buy ? metric.buy() : metric.sell());
            row.tooltip(WidgetTooltips.wrapped(metric.help()));
            this.root.child(row);
        }
    }

    private static FlowLayout tableRow(String title, Component buy, Component sell, int sideWidth) {
        var row = RetainedFlowLayout.horizontal(Sizing.fill(100), Sizing.content());
        row.gap(12);
        var titleLabel = label(Component.literal(title).withStyle(UiStyles.label()));
        titleLabel.horizontalSizing(Sizing.expand(100));
        row.child(titleLabel);
        for (var text : List.of(buy, sell)) {
            var value = label(text);
            value.horizontalSizing(Sizing.fixed(sideWidth));
            value.horizontalTextAlignment(HorizontalAlignment.RIGHT);
            row.child(value);
        }
        return row;
    }

    private static FlowLayout valueLine(Component title, Component text) {
        var row = RetainedFlowLayout.horizontal(Sizing.fill(100), Sizing.content());
        row.gap(8);
        row.child(label(title));
        row.child(UiComponents.spacer());
        row.child(label(text));
        return row;
    }

    private static Component activity(
        PriceDifferenceWidgetData.Snapshot data,
        PriceDifferenceWidgetConfig config,
        ToDoubleFunction<BazaarData.SideActivity> value,
        boolean buy
    ) {
        return data.activity().map(activity -> value.applyAsDouble(buy ? activity.buyOrders() : activity.sellOffers()))
            .<Component>map(number -> Component.literal(aggregate(number, config, 0)).withStyle(UiStyles.quantity()))
            .orElseGet(PriceDifferenceWidgetView::unavailable);
    }

    private static Component price(Optional<Double> value) {
        return value
            .<Component>map(
                number -> Component.literal(Utils.formatDecimal(number, 1, true)).withStyle(UiStyles.money()))
            .orElseGet(PriceDifferenceWidgetView::unavailable);
    }

    private static Component signed(Optional<Double> value, boolean aggregate, PriceDifferenceWidgetConfig config) {
        return value.<Component>map(number -> Component.literal((number >= 0 ? "+" : "")
            + (aggregate ? aggregate(number, config, 1) : Utils.formatDecimal(number, 1, true)))
            .withStyle(UiStyles.money())).orElseGet(PriceDifferenceWidgetView::unavailable);
    }

    private static String aggregate(double value, PriceDifferenceWidgetConfig config, int decimals) {
        return config.numberFormat == PriceDifferenceWidgetConfig.NumberFormat.Short
            ? Utils.formatCompact(value) : Utils.formatDecimal(value, decimals, true);
    }

    private static Component unavailable() {
        return Component.literal("N/A").withStyle(UiStyles.muted());
    }

    private static LabelComponent label(Component text) {
        var label = UiComponents.label("", UiStyles.palette().textPrimary());
        label.text(text);
        return label;
    }

    private static int textWidth(Component text) {
        return Minecraft.getInstance().font.width(text);
    }

    private static int textWidth(String text) {
        return Minecraft.getInstance().font.width(text);
    }

    private record Metric(String label, Component buy, Component sell, String help) {}
}
