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
    private static final int COLUMN_GAP = 12;
    private final RetainedFlowLayout root = RetainedFlowLayout.vertical(Sizing.fixed(1), Sizing.content());

    PriceDifferenceWidgetView() {
        this.root.gap(2);
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
        var metrics = metrics(data, config);
        var difference = signed(data.perItem(), false, config);
        String inventoryLabel = data.quantity().isPresent()
            ? Utils.formatDecimal(data.quantity().getAsInt(), 0, true) + " items" : "Inventory ?";
        var inventory = signed(data.total(), true, config);
        var differenceLabel = Component.literal("Difference").withStyle(UiStyles.primary());
        var inventoryTitle = Component.literal(inventoryLabel).withStyle(UiStyles.label());
        int resultWidth = lineWidth(differenceLabel, difference);
        if (config.showInventoryTotal) {
            resultWidth = Math.max(resultWidth, lineWidth(inventoryTitle, inventory));
        }
        int parallelWidth = 0;
        for (var metric : metrics) {
            String buyTitle = metric.quote() ? "Buy order" : metric.title();
            String sellTitle = metric.quote() ? "Sell offer" : metric.title();
            parallelWidth = Math.max(parallelWidth,
                Math.max(textWidth(buyTitle) + textWidth(metric.buy()), textWidth(sellTitle) + textWidth(metric.sell()))
                    + 16);
        }
        int width = Math.max(Math.max(180, config.contentWidth), parallelWidth * 2 + COLUMN_GAP);
        width = Math.max(width, config.showInventoryTotal ? resultWidth * 2 + COLUMN_GAP : resultWidth);
        this.root.horizontalSizing(Sizing.fixed(width));
        this.addProduct(data, width);

        boolean noMarket = data.prices().highestBuyOrderPrice().isEmpty()
            && data.prices().lowestSellOfferPrice().isEmpty()
            && data.activity().isEmpty();
        if (noMarket) {
            this.root.child(label(Component.literal("Market unavailable").withStyle(UiStyles.muted())));
        } else {
            for (var metric : metrics) {
                var buyTitle = Component.literal(metric.quote() ? "Buy order" : metric.title())
                    .withStyle(metric.quote() ? UiStyles.color(UiStyles.palette().buy()) : UiStyles.label());
                var sellTitle = Component.literal(metric.quote() ? "Sell offer" : metric.title())
                    .withStyle(metric.quote() ? UiStyles.color(UiStyles.palette().sell()) : UiStyles.label());
                this.root.child(pairedRow(buyTitle, metric.buy(), sellTitle, metric.sell(), width));
            }
        }
        var results = RetainedFlowLayout.vertical(Sizing.fill(100), Sizing.content());
        results.gap(2);
        results.padding(Insets.top(4));
        results.surface((graphics, component) -> graphics.fill(component.x(), component.y(),
            component.x() + component.width(), component.y() + 1, UiStyles.palette().outline()));
        if (config.showInventoryTotal) {
            results.child(pairedRow(differenceLabel, difference, inventoryTitle, inventory, width));
        } else {
            results.child(valueLine(differenceLabel, difference));
        }
        this.root.child(results);
    }

    private void addProduct(PriceDifferenceWidgetData.Snapshot data, int width) {
        var product = data.product().orElseThrow();
        var header = RetainedFlowLayout.horizontal(Sizing.fill(100), Sizing.fixed(16));
        header.verticalAlignment(VerticalAlignment.CENTER);
        header.gap(4);
        var stack = product.itemStack();
        stack.ifPresent(item -> header.child(UiComponents.icon(item)));
        var text = UiStyles.productName(product.displayName(), false);
        var font = Minecraft.getInstance().font;
        int nameWidth = width - (stack.isPresent() ? 20 : 0);
        var fitted = text.copy();
        if (font.width(text) > nameWidth) {
            fitted = Component.empty();
            var target = fitted;
            font.substrByWidth(text, Math.max(0, nameWidth - font.width("\u2026"))).visit((style, part) -> {
                target.append(Component.literal(part).withStyle(style));
                return Optional.empty();
            }, Style.EMPTY);
            fitted.append(Component.literal("\u2026").withStyle(UiStyles.label()));
        }
        var name = label(fitted);
        name.tooltip(WidgetTooltips.wrapped(List.of(text)));
        header.child(name);
        this.root.child(header);
    }

    private static List<Metric> metrics(PriceDifferenceWidgetData.Snapshot data, PriceDifferenceWidgetConfig config) {
        var metrics = new ArrayList<Metric>();
        metrics.add(new Metric("Price", price(data.prices().highestBuyOrderPrice()),
            price(data.prices().lowestSellOfferPrice()), true));
        if (config.mode == PriceDifferenceWidgetConfig.Mode.Detailed && config.showBookTotals) {
            metrics.add(new Metric("Orders", activity(data, config, BazaarData.SideActivity::orders, true),
                activity(data, config, BazaarData.SideActivity::orders, false), false));
            metrics.add(new Metric("Listed", activity(data, config, BazaarData.SideActivity::listedItems, true),
                activity(data, config, BazaarData.SideActivity::listedItems, false), false));
        }
        if (config.mode == PriceDifferenceWidgetConfig.Mode.Detailed && config.showWeeklyActivity) {
            metrics.add(new Metric("7d avg/h",
                activity(data, config, BazaarData.SideActivity::weeklyAverageHourlyMovement, true),
                activity(data, config, BazaarData.SideActivity::weeklyAverageHourlyMovement, false), false));
        }
        return metrics;
    }

    private static FlowLayout pairedRow(
        Component firstTitle,
        Component first,
        Component secondTitle,
        Component second,
        int width
    ) {
        var row = RetainedFlowLayout.horizontal(Sizing.fill(100), Sizing.content());
        row.gap(COLUMN_GAP);
        int half = (width - COLUMN_GAP) / 2;
        var left = valueLine(firstTitle, first);
        left.horizontalSizing(Sizing.fixed(half));
        var right = valueLine(secondTitle, second);
        right.horizontalSizing(Sizing.fixed(width - COLUMN_GAP - half));
        row.child(left);
        row.child(right);
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
        return value.<Component>map(number -> Component.literal((number > 0 ? "+" : "")
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

    private static int lineWidth(Component title, Component value) {
        return textWidth(title) + textWidth(value) + 16;
    }

    private static int textWidth(Component text) {
        return Minecraft.getInstance().font.width(text);
    }

    private static int textWidth(String text) {
        return Minecraft.getInstance().font.width(text);
    }

    private record Metric(String title, Component buy, Component sell, boolean quote) {}
}
